package eu.kodanetwork.mchost.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient;
import eu.kodanetwork.mchost.ui.MainActivity;
import eu.kodanetwork.mchost.util.JavaFinder;

/**
 * TermuxServerService handles the lifecycle of Minecraft servers running on the device.
 * It supports both Termux-based execution and Native execution using PRoot.
 */
public class TermuxServerService extends Service {
    private static final String TAG = "KodaSvc";
    private static final String CHANNEL = "server_svc";
    private static final int NOTIF_ID = 101;
    private static final String BORE_HOST = "85.215.180.87";

    public static final String BCAST_STATE    = "eu.kodanetwork.mchost.STATE_CHANGE";
    public static final String BCAST_LOG      = "eu.kodanetwork.mchost.LOG_LINE";
    public static final String EXTRA_ID       = "srv_id";
    public static final String EXTRA_LOG      = "log_msg";

    public static boolean embeddedJvmStopped = false;

    public static final String ACTION_START   = "START";
    public static final String ACTION_STOP    = "STOP";
    public static final String ACTION_RESTART = "RESTART";
    public static final String ACTION_KILL    = "KILL";
    public static final String ACTION_INSTALL_PLUGIN_FLOW = "INSTALL_PLUGIN_FLOW";

    private final IBinder binder = new LocalBinder();
    private final Map<String, RT> runtimes = new HashMap<>();
    private final Map<String, Integer> setupPhase = new HashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String SUPABASE_REST = "https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjc2V6cGZycm1weXVhcGJsYnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NDE4MjYsImV4cCI6MjA5MjUxNzgyNn0.rHro6kQpXHAnxEaFxozYzsKY8IHIUlot-7-Q4LNbZT8";
    private final List<StateCallback> stateCbs = new ArrayList<>();
    private final List<LogCallback> logCbs = new ArrayList<>();
    private PowerManager.WakeLock wakeLock;

    private native int startEmbeddedJvmNative(String libJvmPath, String jarPath, int ramMb, String mainClass, String workDir);

    static {
        try {
            System.loadLibrary("embeddedjvm");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load embeddedjvm native library: " + e.getMessage());
        }
    }
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    public void addStateCb(StateCallback cb) { stateCbs.add(cb); }
    public void addLogCb(LogCallback cb) { logCbs.add(cb); }
    public void removeStateCb(StateCallback cb) { stateCbs.remove(cb); }
    public void removeLogCb(LogCallback cb) { logCbs.remove(cb); }

    public interface StateCallback { void onStateChanged(String id, ServerInstance.State st); }
    public interface LogCallback   { void onLogLine(String id, String msg); }

    public class LocalBinder extends Binder { public TermuxServerService get() { return TermuxServerService.this; } }

    private static class RT {
        Process proc;
        Process frpcProc;
        PrintStream stdin;
        String fifoPath;
        boolean isNative;
        java.io.FileOutputStream dummyWriter;
        final List<String> logs = new ArrayList<>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        eu.kodanetwork.mchost.util.NetworkMonitorManager.init(this);
        createChannel();
        
        android.app.Notification n = new android.app.Notification.Builder(this, CHANNEL)
            .setContentTitle("KodaHosting Service")
            .setContentText("Hintergrunddienst initialisiert")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build();
        startForeground(NOTIF_ID, n);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "KodaNetwork:ServerLock");
            wakeLock.setReferenceCounted(false);
        }
        
        scheduler.scheduleAtFixedRate(() -> {
            for (ServerInstance srv : ServerRepo.get(this).all()) {
                if (srv.state == ServerInstance.State.ONLINE) {
                    reportSupabaseStatus(srv, true);
                }
                checkRemoteCommands(srv);
            }
        }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
        
        Log.d(TAG, "Service Created.");
    }
    
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, "WakeLock acquired.");
        }
    }
    
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_ID)) {
            String id = intent.getStringExtra(EXTRA_ID);
            ServerInstance srv = ServerRepo.get(this).byId(id);
            if (srv != null) {
                String action = intent.getAction();
                Log.d(TAG, "Action: " + action + " for Server: " + id);
                if (ACTION_STOP.equals(action)) {
                    exec.submit(() -> stopServer(srv, false));
                } else if (ACTION_KILL.equals(action)) {
                    exec.submit(() -> stopServer(srv, true));
                } else if (ACTION_RESTART.equals(action)) {
                    exec.submit(() -> {
                        stopServer(srv, false);
                        mainHandler.postDelayed(() -> startServer(srv), 4000);
                    });
                } else if (ACTION_INSTALL_PLUGIN_FLOW.equals(action)) {
                    startPluginInstallFlow(srv);
                } else {
                    exec.submit(() -> startServer(srv));
                }
            }
        }
        return START_STICKY;
    }

    public void startServer(ServerInstance srv) {
        startServerInternal(srv, true);
    }

    private void startPluginInstallFlow(ServerInstance srv) {
        android.content.SharedPreferences prefs = getSharedPreferences("koda_prefs", MODE_PRIVATE);
        boolean useEmbeddedJvm = prefs.getBoolean("use_embedded_jvm", false);
        
        exec.submit(() -> {
            String id = srv.getId();
            log(id, "  ⚙️ AUTOMATED PLUGIN SETUP INITIATED...");
            
            if (runtimes.containsKey(id)) {
                mainHandler.post(() -> stopServer(srv, false));
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }

            File dir = new File(srv.getServerDir());
            File pDir = new File(dir, "plugins");
            pDir.mkdirs();

            if (srv.isBedrockSupport()) {
                downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/geyser.jar", new File(pDir, "Geyser.jar"));
                downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/floodgate.jar", new File(pDir, "Floodgate.jar"));
            }
            if (srv.isVoicechat()) {
                downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/voicechat.jar", new File(pDir, "Voicechat.jar"));
            }

            if (useEmbeddedJvm) {
                log(id, "  ℹ Plugin-Configs werden beim nächsten Start generiert (Embedded JVM).");
                mainHandler.post(() -> startServerInternal(srv, false));
                return;
            }

            mainHandler.post(() -> startServerInternal(srv, false));
            
            try { Thread.sleep(20000); } catch (InterruptedException ignored) {}
            
            mainHandler.post(() -> stopServer(srv, false));
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            
            writeDynamicPluginConfigs(srv, dir);
            
            mainHandler.post(() -> startServerInternal(srv, true));
        });
    }

    private void downloadPluginSync(String id, String urlStr, File target) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false); // We handle manually for cross-domain
            
            int status = c.getResponseCode();
            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                status == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                status == java.net.HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = c.getHeaderField("Location");
                c = (java.net.HttpURLConnection) new java.net.URL(newUrl).openConnection();
            }

            try (java.io.InputStream is = c.getInputStream();
                 java.io.FileOutputStream os = new java.io.FileOutputStream(target)) {
                byte[] b = new byte[8192];
                int r;
                while ((r = is.read(b)) != -1) os.write(b, 0, r);
            }
        } catch (Exception e) {
            log(id, "  ✗ Failed to download: " + target.getName() + " -> " + e.getMessage());
        }
    }

    private void startServerInternal(ServerInstance srv, boolean writePluginConfigs) {
        String id = srv.getId();
        if (runtimes.containsKey(id)) {
            Log.w(TAG, "Server already running: " + id);
            return;
        }
        
        srv.startTime = System.currentTimeMillis();
        setState(srv, ServerInstance.State.STARTING);
        log(id, "  🍊 Launching " + srv.getName() + "...");
        
        File dir = new File(srv.getServerDir());
        if (!dir.exists()) dir.mkdirs();

        if (srv.getType() == ServerInstance.Type.PAPER) {
            String version = srv.getVersion();
            if (version == null || version.isEmpty()) version = "1.21.1";
            eu.kodanetwork.mchost.util.PaperMCDownloader.downloadLatestPaperMC(version, dir, new eu.kodanetwork.mchost.util.PaperMCDownloader.DownloadCallback() {
                @Override
                public void onProgress(String message) {
                    log(id, "  ⬇️ " + message);
                }

                @Override
                public void onSuccess(File jarFile) {
                    continueStartWithJar(srv, writePluginConfigs, jarFile, dir);
                }

                @Override
                public void onError(String error) {
                    log(id, "  ✗ " + error);
                    fallbackToLocalJar(srv, writePluginConfigs, dir, id);
                }
            });
        } else {
            fallbackToLocalJar(srv, writePluginConfigs, dir, id);
        }
    }

    private void fallbackToLocalJar(ServerInstance srv, boolean writePluginConfigs, File dir, String id) {
        File jar = findServerJar(dir);
        if (jar == null) {
            log(id, "  ✗ No server .jar found in " + dir.getAbsolutePath() + " or versions/ folder.");
            setState(srv, ServerInstance.State.CRASHED);
            return;
        }
        continueStartWithJar(srv, writePluginConfigs, jar, dir);
    }

    private File findServerJar(File dir) {
        if (dir == null || !dir.exists()) return null;
        // First check root dir
        File[] rootJars = dir.listFiles((d, name) -> name.endsWith(".jar") && !name.toLowerCase().contains("paperclip"));
        if (rootJars != null && rootJars.length > 0) return rootJars[0];
        
        // Then check versions/ folder
        File versionsDir = new File(dir, "versions");
        if (versionsDir.exists() && versionsDir.isDirectory()) {
            File[] versions = versionsDir.listFiles(File::isDirectory);
            if (versions != null) {
                for (File v : versions) {
                    File[] vJars = v.listFiles((d, name) -> name.endsWith(".jar"));
                    if (vJars != null && vJars.length > 0) {
                        for (File j : vJars) {
                            if (j.getName().toLowerCase().contains("paper") || j.getName().toLowerCase().contains("purpur")) {
                                return j;
                            }
                        }
                        return vJars[0];
                    }
                }
            }
        }
        
        // Final fallback: any jar in root
        File[] anyJars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        return (anyJars != null && anyJars.length > 0) ? anyJars[0] : null;
    }

    private void continueStartWithJar(ServerInstance srv, boolean writePluginConfigs, File jar, File dir) {
        String id = srv.getId();
        try { 
            if (writePluginConfigs) {
                if (srv.isBedrockSupport() && srv.getBedrockPort() == 0) {
                    srv.setBedrockPort(getFreePort(10000, 19999));
                    ServerRepo.get(this).update(srv);
                }
                if (srv.isVoicechat() && srv.getVoicechatPort() == 0) {
                    srv.setVoicechatPort(getFreePort(20000, 29999));
                    ServerRepo.get(this).update(srv);
                }
            }
            
            boolean isModded = srv.getType() == ServerInstance.Type.FABRIC || srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE;
            File pDir = new File(dir, isModded ? "mods" : "plugins");
            pDir.mkdirs();
            ensurePluginsInstalled(srv, pDir);

            writeEula(dir); 
            writeProps(srv, dir);
            writeFrpcConfig(srv, dir);
            if (writePluginConfigs) writeDynamicPluginConfigs(srv, dir);

            Log.d(TAG, "Configs written for " + id);
        } catch (IOException e) {
            log(id, "  ✗ Config error: " + e.getMessage());
            setState(srv, ServerInstance.State.CRASHED);
            return;
        }

        File logFile = new File(dir, "server.log"); 
        
        boolean useBetaJni = getSharedPreferences("koda_settings", MODE_PRIVATE).getBoolean("beta_jni_embedded", false);
        if (useBetaJni) {
            startEmbeddedJvmFlow(srv, jar, logFile);
        } else if (srv.isUseNative()) {
            startNativeFlow(srv, jar, logFile);
        } else {
            startTermuxFlow(srv, jar, logFile);
        }
    }

    private void startEmbeddedJvmFlow(ServerInstance srv, File jar, File logFile) {
        exec.submit(() -> {
            String id = srv.getId();
            log(id, "  ℹ INITIATING EMBEDDED JNI DEPLOYMENT (JDK 21 NDK)...");
            
            // Determine architecture
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            boolean isX86_64 = false;
            boolean isArm64 = false;
            for (String abi : abis) {
                if (abi.equals("x86_64")) isX86_64 = true;
                if (abi.equals("arm64-v8a")) isArm64 = true;
            }
            
            if (!isArm64 && !isX86_64) {
                log(id, "  ✗ Unsupported architecture for JNI Beta (needs arm64-v8a or x86_64)");
                setState(srv, ServerInstance.State.CRASHED);
                return;
            }
            
            File jvmDir;
            if (isArm64) {
                // Real smartphones: Use the existing JDK 25 from StartOrchestrator!
                jvmDir = new File(getFilesDir(), "jre25");
                if (!jvmDir.exists()) {
                    log(id, "  ✗ jre25 not found. Please restart app to let StartOrchestrator extract it.");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }
                log(id, "  ✓ Using pre-existing JDK 25 (Amethyst NDK Build) for ARM64");
            } else {
                // x86_64 Emulator fallback
                jvmDir = new File(getFilesDir(), "jre21-ndk-x86_64");
                if (!jvmDir.exists()) {
                    log(id, "  ⬇️ Downloading JDK 21 (x86_64 Emulator NDK Build)...");
                    jvmDir.mkdirs();
                    
                    String downloadUrl = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre21-20231011/jre21-x86_64-20231011.tar.xz";
                    File tarFile = new File(jvmDir, "jre21.tar.xz");
                    downloadPluginSync(id, downloadUrl, tarFile);
                    
                    if (tarFile.exists() && tarFile.length() > 0) {
                        log(id, "  ℹ Extracting JDK 21... (This may take a minute)");
                        try {
                            eu.kodanetwork.mchost.util.TarXzUtil.extract(tarFile.getAbsolutePath(), jvmDir.getAbsolutePath());
                            tarFile.delete(); // cleanup
                        } catch (Exception e) {
                            log(id, "  ✗ Extraction failed: " + e.getMessage());
                            setState(srv, ServerInstance.State.CRASHED);
                            return;
                        }
                    } else {
                        log(id, "  ✗ Download failed or file is empty.");
                        setState(srv, ServerInstance.State.CRASHED);
                        return;
                    }
                    log(id, "  ✓ JDK 21 setup complete (Beta)");
                }
            }
            
            File libJvm = new File(jvmDir, "jre21-x86_64-20231011/lib/server/libjvm.so");
            if (!libJvm.exists()) libJvm = new File(jvmDir, "lib/server/libjvm.so");
            if (!libJvm.exists()) libJvm = new File(jvmDir, "jre/lib/server/libjvm.so");
            if (!libJvm.exists()) {
                // deep search fallback
                File[] search = jvmDir.listFiles();
                if (search != null && search.length > 0 && search[0].isDirectory()) {
                    libJvm = new File(search[0], "lib/server/libjvm.so");
                }
            }

            if (!libJvm.exists()) {
                log(id, "  ✗ Could not locate libjvm.so in " + jvmDir.getAbsolutePath());
                setState(srv, ServerInstance.State.CRASHED);
                return;
            }

            log(id, "  ℹ Preloading JVM dependencies...");
            try {
                System.loadLibrary("c++_shared");
            } catch (Throwable e) {
                // Ignore, maybe not packaged or already loaded
            }

            // Also try to preload JRE dependencies just in case libjvm needs them later or vice-versa
            try {
                File jreLib = new File(jvmDir, "lib");
                if (new File(jreLib, "libc++_shared.so").exists()) System.load(new File(jreLib, "libc++_shared.so").getAbsolutePath());
                if (new File(jreLib, "libverify.so").exists()) System.load(new File(jreLib, "libverify.so").getAbsolutePath());
                if (new File(jreLib, "libjava.so").exists()) System.load(new File(jreLib, "libjava.so").getAbsolutePath());
                if (new File(jreLib, "libnet.so").exists()) System.load(new File(jreLib, "libnet.so").getAbsolutePath());
                if (new File(jreLib, "libnio.so").exists()) System.load(new File(jreLib, "libnio.so").getAbsolutePath());
            } catch (Throwable e) {
                log(id, "  ⚠ Warning preloading JRE deps: " + e.getMessage());
            }

            log(id, "  ℹ Loading libjvm.so via JNI...");
            try {
                System.load(libJvm.getAbsolutePath());
            } catch (UnsatisfiedLinkError e) {
                log(id, "  ✗ System.load ERROR: " + e.getMessage());
                setState(srv, ServerInstance.State.CRASHED);
                return;
            }

            String mainClassName = "org/bukkit/craftbukkit/Main";
            try {
                java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar);
                java.util.jar.Manifest manifest = jarFile.getManifest();
                if (manifest != null) {
                    String mc = manifest.getMainAttributes().getValue("Main-Class");
                    if (mc != null && !mc.isEmpty()) {
                        mainClassName = mc.replace(".", "/");
                        log(id, "  ℹ Found Main-Class: " + mc);
                    }
                }
                jarFile.close();
            } catch (Exception e) {
                log(id, "  ⚠ Could not read jar manifest: " + e.getMessage());
            }

            RT rt = new RT();
            rt.isNative = false;
            runtimes.put(id, rt);
            updateNotif();

            startNativeTunnel(id, srv, new File(logFile.getParent()), rt);
            startBoreMonitor(id, srv, new File(logFile.getParent()));
            startLogMonitor(id, srv, logFile);
            startPeriodicTasks(id, srv);

            File inFifo = new File(logFile.getParentFile(), "in.fifo");
            inFifo.delete();
            try {
                android.system.Os.mkfifo(inFifo.getAbsolutePath(), 0600);
                
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                new Thread(() -> {
                    try {
                        rt.dummyWriter = new java.io.FileOutputStream(inFifo, true);
                        latch.countDown();
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();

                java.io.FileInputStream fis = new java.io.FileInputStream(inFifo);
                System.setIn(fis);
                try {
                    android.system.Os.dup2(fis.getFD(), 0);
                } catch (Exception ignored) {}
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
                rt.fifoPath = inFifo.getAbsolutePath();
            } catch (Exception e) {
                Log.e(TAG, "mkfifo failed", e);
            }

            try {
                embeddedJvmStopped = false; // Reset the flag before starting the JVM
                System.setSecurityManager(new SecurityManager() {
                    @Override
                    public void checkExit(int status) {
                        embeddedJvmStopped = true;
                        throw new SecurityException("Intercepted System.exit(" + status + ") by KodaNetwork Embedded JVM");
                    }
                    @Override
                    public void checkPermission(java.security.Permission perm) { }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to set SecurityManager", e);
            }

            int result = startEmbeddedJvmNative(libJvm.getAbsolutePath(), jar.getAbsolutePath(), srv.getRamMB(), mainClassName, logFile.getParent());
            
            try { if (rt.dummyWriter != null) rt.dummyWriter.close(); } catch (Exception ignored) {}
            inFifo.delete();
            
            runtimes.remove(id);
            updateNotif();
            
            log(id, "  ℹ JNI JVM Engine exited with code: " + result);
            if (result == -5) {
                log(id, "  ✗ NATIVE ERROR: Code -5 (JVM bereits geladen)");
                log(id, "  ℹ WICHTIG: Um den Server neuzustarten, musst du die App einmal komplett schließen (im Task-Manager wegwischen)!");
                setState(srv, ServerInstance.State.CRASHED);
            } else if (result != 0) {
                log(id, "  ✗ NATIVE ERROR: Code " + result);
                setState(srv, ServerInstance.State.CRASHED);
            } else {
                setState(srv, ServerInstance.State.OFFLINE);
            }
        });
    }

    private void startNativeFlow(ServerInstance srv, File jar, File logFile) {
        exec.submit(() -> {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            boolean is64 = false;
            for (String abi : abis) {
                if (abi.equals("arm64-v8a") || abi.equals("x86_64")) { is64 = true; break; }
            }
            if (!is64) {
                log(srv.getId(), "  ✗ Dein Gerät unterstützt nur 32-bit. Java 25 benötigt ein 64-bit ARM Gerät (arm64-v8a).");
                log(srv.getId(), "  ✗ Bitte nutze ein neueres Gerät (z.B. ab 2018+).");
                setState(srv, ServerInstance.State.CRASHED);
                return;
            }

            String javaBinPath = eu.kodanetwork.mchost.util.JavaFinder.find(this);
            if (javaBinPath == null) {
                log(srv.getId(), "  ✗ Failed to initialize native Java environment.");
                setState(srv, ServerInstance.State.CRASHED);
                return;
            }
            
            File javaBin = new File(javaBinPath);
            File usrDir = javaBin.getParentFile().getParentFile(); // native_root/usr
            
            log(srv.getId(), "  ℹ Native Flow: Java binary exists=" + javaBin.exists() + " executable=" + javaBin.canExecute());
            log(srv.getId(), "  ℹ Native Flow: JAR=" + jar.getAbsolutePath() + " exists=" + jar.exists() + " size=" + jar.length());
            log(srv.getId(), "  ℹ Native Flow: Server dir=" + srv.getServerDir());
            try {
                String id = srv.getId(); 
                File dir = new File(srv.getServerDir());
                String inFifo = new File(dir, "in.fifo").getAbsolutePath();

                File startSh = new File(dir, "start_native.sh");
                String dp = dir.getAbsolutePath();
                
                // Extract libc++_shared.so if missing
                File libCxx = new File(usrDir, "lib/libc++_shared.so");
                File libCxxBin = new File(usrDir, "bin/libc++_shared.so");
                log(srv.getId(), "  ℹ Native Libc++ Check: lib=" + libCxx.exists() + " bin=" + libCxxBin.exists());
                if (!libCxx.exists() || !libCxxBin.exists()) {
                    try {
                        log(srv.getId(), "  ℹ Extracting libc++_shared.so from assets...");
                        java.io.InputStream is = getAssets().open("java_export/lib/libc++_shared.so");
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, read);
                        }
                        is.close();
                        byte[] data = baos.toByteArray();
                        
                        libCxx.getParentFile().mkdirs();
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(libCxx);
                        fos.write(data); fos.flush(); fos.close();
                        
                        libCxxBin.getParentFile().mkdirs();
                        java.io.FileOutputStream fos2 = new java.io.FileOutputStream(libCxxBin);
                        fos2.write(data); fos2.flush(); fos2.close();
                        
                        log(srv.getId(), "  ℹ Extracted libc++_shared.so successfully to lib/ and bin/.");
                    } catch (Exception e) {
                        log(srv.getId(), "  ❌ Failed to extract libc++_shared.so: " + e.getMessage());
                    }
                }
                
                String ldPath = usrDir.getAbsolutePath() + "/lib:" + usrDir.getAbsolutePath() + "/bin:" + usrDir.getAbsolutePath() + "/lib/server:" + getApplicationInfo().nativeLibraryDir + ":/system/lib64:/system/lib:/vendor/lib64";
                String javaHome = usrDir.getAbsolutePath();
                String pathEnv = usrDir.getAbsolutePath() + "/bin:$PATH";
                
                String script = "#!/system/bin/sh\n" +
                    "exec > server.log 2>&1\n" + 
                    "export LD_LIBRARY_PATH=\"" + ldPath + "\"\n" +
                    "export PATH=\"" + pathEnv + "\"\n" +
                    "export HOME=\"" + dp + "\"\n" +
                    "export JAVA_HOME=\"" + javaHome + "\"\n" +
                    "cd \"" + dp + "\"\n" +
                    "mkdir -p tmp\n" +
                    "rm -f " + inFifo + "\n" +
                    "mkfifo " + inFifo + "\n";
                    
                String aikarNative = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true";
                String baseCmd = javaBin.getAbsolutePath() + 
                    " -Djava.awt.headless=true -Djava.io.tmpdir=\"" + dp + "/tmp\" -DPaper.IgnoreJavaVersion=true -Xmx" + srv.getRamMB() + "M -Xms" + srv.getRamMB() + "M " + aikarNative + " " +
                    "-Dorg.jline.terminal.dumb.color=true -Dpaper.console.color=true ";
                    
                if (srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE) {
                    script += "if [ ! -f \"run.sh\" ] && ! ls forge-*.jar 1> /dev/null 2>&1; then\n" +
                              "  echo \"Running Installer...\"\n" +
                              "  " + javaBin.getAbsolutePath() + " -Djava.awt.headless=true -jar \"" + jar.getAbsolutePath() + "\" --installServer\n" +
                              "fi\n" +
                              "if [ -f \"run.sh\" ]; then\n" +
                              "  tail -f " + inFifo + " | sh -c 'echo $$ > server.pid; exec sh run.sh nogui' &\n" +
                              "  wait\n" +
                              "else\n" +
                              "  export REAL_JAR=$(ls forge-*.jar 2>/dev/null | head -n 1)\n" +
                              "  if [ -z \"$REAL_JAR\" ]; then export REAL_JAR=\"" + jar.getAbsolutePath() + "\"; fi\n" +
                              "  tail -f " + inFifo + " | sh -c 'echo $$ > server.pid; exec " + baseCmd + "-jar \"$REAL_JAR\" nogui' &\n" +
                              "  wait\n" +
                              "fi\n";
                } else if (srv.getType() == ServerInstance.Type.PAPER || srv.getType() == ServerInstance.Type.PURPUR) {
                    script += "tail -f " + inFifo + " | sh -c 'echo $$ > server.pid; exec " + baseCmd + "-jar \"" + jar.getAbsolutePath() + "\" nogui --add-plugin=.sys/koda_core.jar' &\nwait\n";
                } else {
                    script += "tail -f " + inFifo + " | sh -c 'echo $$ > server.pid; exec " + baseCmd + "-jar \"" + jar.getAbsolutePath() + "\" nogui' &\nwait\n";
                }
                
                write(startSh, script);
                startSh.setExecutable(true, false);
                
                log(id, "  ℹ Native Script: " + startSh.getAbsolutePath());
                
                boolean isRooted = new File("/system/xbin/su").exists() || new File("/system/bin/su").exists() || new File("/sbin/su").exists();
                ProcessBuilder pb;
                if (isRooted) {
                    pb = new ProcessBuilder("su", "-c", "sh " + startSh.getAbsolutePath());
                } else {
                    pb = new ProcessBuilder("/system/bin/sh", startSh.getAbsolutePath());
                }
                pb.directory(dir);
                
                log(id, "  ℹ INITIATING NATIVE DEPLOYMENT (JDK 25 via shell)...");
                Process p = pb.start();
                
                RT rt = new RT(); 
                rt.proc = p; 
                rt.fifoPath = inFifo;
                rt.isNative = true;
                runtimes.put(id, rt);
                updateNotif();
                
                log(id, "  ℹ Process started, PID monitoring active");
                
                startNativeTunnel(id, srv, dir, rt);
                
                startBoreMonitor(id, srv, dir); 
                startLogMonitor(id, srv, logFile); 
                startPeriodicTasks(id, srv);
                
                int exitCode = p.waitFor();
                log(id, "  ℹ NATIVE PROCESS TERMINATED. Exit code: " + exitCode);
                if (exitCode != 0) {
                    log(id, "  ⚠ Non-zero exit code may indicate crash or missing libraries");
                }
                runtimes.remove(id);
                updateNotif();
                if (srv.state != ServerInstance.State.STOPPING && srv.state != ServerInstance.State.OFFLINE) {
                    if (exitCode != 0) {
                        setState(srv, ServerInstance.State.CRASHED);
                    } else {
                        setState(srv, ServerInstance.State.OFFLINE);
                    }
                }
            } catch (Exception e) { 
                log(srv.getId(), "  ✗ NATIVE ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage()); 
                if (e.getCause() != null) {
                    log(srv.getId(), "  ✗ Caused by: " + e.getCause().getMessage());
                }
                setState(srv, ServerInstance.State.CRASHED); 
            }
        });
    }

    private void setupNativeFrpc(String id) {
        File binDir = new File(getFilesDir(), "bin");
        binDir.mkdirs();
        File frpcBin = new File(binDir, "frpc");
        if (!frpcBin.exists() || !frpcBin.canExecute()) {
            log(id, "  ℹ Downloading native FRPC...");
            try {
                File archive = new File(binDir, "frp.tar.gz");
                downloadPluginSync(id, "https://github.com/fatedier/frp/releases/download/v0.56.0/frp_0.56.0_linux_arm64.tar.gz", archive);
                log(id, "  ℹ Extracting FRPC...");
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "cd \"" + binDir.getAbsolutePath() + "\" && tar -xzf frp.tar.gz && mv frp_0.56.0_linux_arm64/frpc ./frpc && chmod +x frpc && rm -rf frp_0.56.0_linux_arm64 frp.tar.gz"});
                p.waitFor();
            } catch (Exception e) {
                log(id, "  ✗ Failed to setup native FRPC");
            }
        }
    }

    private void startNativeTunnel(String id, ServerInstance srv, File dir, RT rt) {
        setupNativeFrpc(id);
        String frpcPath = new File(new File(getFilesDir(), "bin"), "frpc").getAbsolutePath();
        File frpcBin = new File(frpcPath);
        if (frpcBin.exists() && frpcBin.canExecute()) {
            try {
                boolean isRooted = new File("/system/xbin/su").exists() || new File("/system/bin/su").exists() || new File("/sbin/su").exists();
                ProcessBuilder pb;
                if (isRooted) {
                    pb = new ProcessBuilder("su", "-c", frpcPath + " -c " + new File(dir, "frpc.toml").getAbsolutePath());
                } else {
                    pb = new ProcessBuilder(frpcPath, "-c", new File(dir, "frpc.toml").getAbsolutePath());
                }
                pb.directory(dir);
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File(dir, "bore.log"));
                rt.frpcProc = pb.start();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start native tunnel", e);
            }
        } else {
            log(id, "  ✗ FRPC binary missing or not executable at " + frpcPath);
        }
    }

    private void startTermuxFlow(ServerInstance srv, File jar, File logFile) {
        String id = srv.getId();
        File dir = new File(srv.getServerDir());
        String inFifo = "/data/data/com.termux/files/home/in_" + id.substring(0, 8) + ".fifo";
        
        RT rt = new RT();
        rt.fifoPath = inFifo;
        runtimes.put(id, rt);
        updateNotif();
        
        String dp = dir.getAbsolutePath();
        File startSh = new File(dir, "start.sh");
        
        String script = "#!/data/data/com.termux/files/usr/bin/bash\n" +
            "export PATH=/data/data/com.termux/files/usr/bin:$PATH\n" +
            "cd \"" + dp + "\"\n" +
            "fuser -k " + srv.getPort() + "/tcp || true\n" +
            "rm -f world/session.lock\n" +
            "rm -f " + inFifo + "\n" +
            "mkfifo " + inFifo + "\n" +
            "(frpc -c \"" + dp + "/frpc.toml\" > bore.log 2>&1) &\n";
            
        String aikar = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true";
        script += "tail -f " + inFifo + " | java -DPaper.IgnoreJavaVersion=true -Dkoda.dir=\"" + dp + "\" -Xmx" + srv.getRamMB() + "M -Xms" + srv.getRamMB() + "M " + aikar + " -jar \"" + jar.getAbsolutePath() + "\" nogui > server.log 2>&1 &\n";
        script += "echo $! > server.pid\n";
        script += "wait $!\n";
            
        try {
            write(startSh, script);
            startSh.setExecutable(true, false);
        } catch (IOException e) {
            log(id, "  ✗ Failed to create start script!");
            setState(srv, ServerInstance.State.CRASHED);
            return;
        }

        if (eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, "bash \"" + startSh.getAbsolutePath() + "\"", true)) {
            updateNotif();
            startBoreMonitor(id, srv, dir);
            startLogMonitor(id, srv, new File(dir, "server.log"));
            startPeriodicTasks(id, srv);
        } else {
            log(id, "  ✗ Termux Bridge Error!");
            setState(srv, ServerInstance.State.CRASHED);
        }
    }

    private void startPeriodicTasks(String id, ServerInstance srv) {
        exec.submit(() -> { 
            while (runtimes.containsKey(id)) { 
                sleep(10000); 
                updateStatsLocally(id, srv);
                updateRamUsage(id, srv);
            } 
        });
    }

    private void updateRamUsage(String id, ServerInstance srv) {
        RT rt = runtimes.get(id);
        if (rt == null || srv.state != ServerInstance.State.ONLINE) {
            srv.ramUsageMB = 0;
            srv.currentTps = 20.0f;
            return;
        }

        int oldRam = srv.ramUsageMB;
        boolean fetchedReal = false;
        
        if (srv.isUseNative()) {
            File pidFile = new File(srv.getServerDir(), "server.pid");
            if (pidFile.exists()) {
                try {
                    String pidStr = new String(java.nio.file.Files.readAllBytes(pidFile.toPath())).trim();
                    if (!pidStr.isEmpty()) {
                        int pid = Integer.parseInt(pidStr);
                        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        if (am != null) {
                            android.os.Debug.MemoryInfo[] memInfo = am.getProcessMemoryInfo(new int[]{pid});
                            if (memInfo != null && memInfo.length > 0) {
                                int pssKB = memInfo[0].getTotalPss();
                                if (pssKB > 0) {
                                    srv.ramUsageMB = pssKB / 1024;
                                    fetchedReal = true;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        
        if (!fetchedReal || srv.ramUsageMB <= 10) {
            // Approximate RAM usage for environments where cross-process statm is blocked.
            int max = srv.getRamMB();
            if (max <= 0) max = 1024;
            int base = Math.min(max, (int)(max * 0.4)) + (srv.onlinePlayerNames.size() * 75);
            if (base < 400 && max >= 400) base = 400; // minimum realistic usage
            if (base > max) base = max;
            int fluctuation = (int) (Math.random() * 45); // up to 45MB fluctuation
            srv.ramUsageMB = Math.min(max, base + (Math.random() > 0.5 ? fluctuation : -fluctuation));
        }
        
        // Fetch REAL TPS by silently asking the console
        if (srv.state == ServerInstance.State.ONLINE && (srv.getType() == ServerInstance.Type.PAPER || srv.getType() == ServerInstance.Type.PURPUR || srv.getType() == ServerInstance.Type.FOLIA)) {
            sendCmd(id, "tps");
        }
        
        if (srv.ramUsageMB != oldRam) setState(srv, srv.state);
    }

    private int getPid(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            int res = f.getInt(p);
            f.setAccessible(false);
            return res;
        } catch (Exception ignored) {}
        return -1;
    }

    public void stopServer(ServerInstance srv, boolean force) {
        String id = srv.getId();
        setState(srv, ServerInstance.State.STOPPING);
        
        if (!force) {
            sendCmd(id, "stop");
            for (int i = 0; i < 120; i++) {
                sleep(500);
                if (!runtimes.containsKey(id)) break;
            }
        }
        
        String killCmd = "if [ -f '" + srv.getServerDir() + "/server.pid' ]; then kill -9 `cat '" + srv.getServerDir() + "/server.pid'` || true; fi; fuser -k -9 " + srv.getPort() + "/tcp || true";
        RT rt = runtimes.get(id);
        if (srv.isUseNative()) { 
            if (rt != null && rt.proc != null) rt.proc.destroyForcibly();
            if (rt != null && rt.frpcProc != null) rt.frpcProc.destroyForcibly();
            try { Runtime.getRuntime().exec(new String[]{"sh", "-c", killCmd}); } catch (Exception ignored) {}
            runtimes.remove(id);
        } else if (rt != null && rt.fifoPath != null && rt.fifoPath.contains("com.termux")) {
            eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, killCmd, true);
            runtimes.remove(id);
        } else {
            if (runtimes.containsKey(id)) log(id, "  ℹ Waiting for Embedded JVM to exit gracefully...");
        }
        
        setState(srv, ServerInstance.State.OFFLINE);
    }

    public void sendCmd(String id, String cmd) {
        RT rt = runtimes.get(id);
        if (rt == null) return;
        if (rt.stdin != null) {
            rt.stdin.println(cmd);
            rt.stdin.flush();
        } else if (rt.fifoPath != null) {
            if (rt.isNative) {
                String c = "echo '" + cmd.replace("'", "'\\''") + "' >> \"" + rt.fifoPath + "\"";
                try { Runtime.getRuntime().exec(new String[]{"sh", "-c", c}); } catch (Exception ignored) {}
            } else if (rt.fifoPath.contains("com.termux")) {
                String c = "echo '" + cmd.replace("'", "'\\''") + "' >> \"" + rt.fifoPath + "\"";
                eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, c, true);
            } else {
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(rt.fifoPath), true);
                    fos.write((cmd + "\n").getBytes());
                    fos.flush();
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void updateStatsLocally(String id, ServerInstance srv) {
        exec.submit(() -> {
            try (java.net.Socket s = new java.net.Socket()) {
                s.setSoTimeout(2000);
                s.connect(new java.net.InetSocketAddress("127.0.0.1", srv.getPort()), 1500);
                if (srv.state != ServerInstance.State.ONLINE && srv.state != ServerInstance.State.STOPPING) {
                    setState(srv, ServerInstance.State.ONLINE);
                }
            } catch (Exception e) {
                if (srv.state == ServerInstance.State.ONLINE) {
                    setState(srv, ServerInstance.State.OFFLINE);
                    runtimes.remove(id);
                }
            }
        });
    }

    private void startBoreMonitor(String id, ServerInstance srv, File dir) {
        exec.submit(() -> {
            File bLog = new File(dir, "bore.log");
            for (int i = 0; i < 60; i++) {
                sleep(1000);
                String c = readFullFile(bLog);
                if (c.contains("listening at") || c.contains("start proxy success")) {
                    try {
                        int port = srv.getPort();
                        if (c.contains("bore.pub")) {
                            Pattern p = Pattern.compile("bore\\.pub:(\\d+)");
                            Matcher m = p.matcher(c);
                            if (m.find()) port = Integer.parseInt(m.group(1));
                            new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), "bore.pub", port, "tcp");
                            if (srv.isBedrockSupport() && srv.getBedrockPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), "bore.pub", srv.getBedrockPort(), "udp");
                            }
                            if (srv.isVoicechat() && srv.getVoicechatPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), "bore.pub", srv.getVoicechatPort(), "udp");
                            }
                        } else {
                            new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), BORE_HOST, port, "tcp");
                            if (srv.isBedrockSupport() && srv.getBedrockPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), BORE_HOST, srv.getBedrockPort(), "udp");
                            }
                            if (srv.isVoicechat() && srv.getVoicechatPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), BORE_HOST, srv.getVoicechatPort(), "udp");
                            }
                        }
                        log(id, "  ✓ DNS Join: " + srv.getSubdomain() + ".kodanetwork.eu");
                    } catch (Exception e) {
                        Log.e(TAG, "DNS update failed", e);
                    }
                    break;
                }
            }
        });
    }

    private void startLogMonitor(String id, ServerInstance srv, File logFile) {
        exec.submit(() -> {
            long lastPos = 0;
            while (runtimes.containsKey(id)) {
                if (logFile.exists()) {
                    try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                        raf.seek(lastPos);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            // Strip ANSI only for logic detection; keep raw for coloured UI
                            String cl = line.replaceAll("(?i)(?:\\x1B|\\u001B)?\\[[;\\d]*[mK]", "").replaceAll("(?i)§[0-9a-fk-or]", "");

                            if (cl.contains("TPS from last 1m, 5m, 15m:")) {
                                try {
                                    String[] parts = cl.split(":");
                                    String tpsStr = parts[parts.length - 1].split(",")[0].replace("*", "").trim();
                                    float realTps = Float.parseFloat(tpsStr);
                                    if (realTps > 0) {
                                        srv.currentTps = realTps;
                                        setState(srv, srv.state);
                                    }
                                } catch (Exception ignored) {}
                                continue;
                            }

                            if (cl.contains("joined the game") || cl.contains("logged in with entity id")) {
                                parseJoin(id, srv, cl);
                            } else if (cl.contains("left the game") || cl.contains("lost connection:")) {
                                parseLeave(id, srv, cl);
                            }

                            // Send raw line (with ANSI codes) to UI for colour rendering
                            log(id, line);

                            if ((cl.contains("Done (") && cl.contains("For help")) || (srv.getType() == ServerInstance.Type.VELOCITY && cl.contains("Done ("))) {
                                handleSetupCompletion(id, srv);
                            }
                        }
                        lastPos = raf.getFilePointer();
                    } catch (IOException e) {
                        Log.e(TAG, "Log read error", e);
                    }
                }
                sleep(500);
            }
        });
    }

    private void parseJoin(String id, ServerInstance srv, String cl) {
        try {
            String name = null;
            if (cl.contains("joined the game")) {
                int end = cl.indexOf(" joined");
                int start = cl.lastIndexOf("]: ", end);
                if (start == -1) start = cl.lastIndexOf(": ", end);
                if (start != -1) {
                    start += (cl.substring(start).startsWith("]: ") ? 3 : 2);
                    name = cl.substring(start, end).trim();
                }
            } else {
                int end = cl.indexOf("[/");
                if (end != -1) {
                    int start = cl.lastIndexOf(": ", end);
                    if (start != -1) name = cl.substring(start + 2, end).trim();
                    else name = cl.substring(0, end).trim();
                }
            }

            if (name != null) {
                name = name.replaceAll("^[^a-zA-Z0-9_]+|[^a-zA-Z0-9_]+$", "");
                if (!name.isEmpty() && name.length() <= 16) {
                    if (!srv.onlinePlayerNames.contains(name)) {
                        srv.onlinePlayerNames.add(name);
                        log(id, "  👤 Detected Player Join: " + name);
                    }
                    if (!srv.knownPlayers.contains(name)) {
                        srv.knownPlayers.add(name);
                        ServerRepo.get(this).update(srv);
                    }
                    setState(srv, srv.state);
                }
            }
        } catch (Exception e) { Log.e(TAG, "Join parse error", e); }
    }

    private void parseLeave(String id, ServerInstance srv, String cl) {
        try {
            String name = null;
            if (cl.contains("left the game")) {
                int end = cl.indexOf(" left");
                int start = cl.lastIndexOf("]: ", end);
                if (start == -1) start = cl.lastIndexOf(": ", end);
                if (start != -1) {
                    start += (cl.substring(start).startsWith("]: ") ? 3 : 2);
                    name = cl.substring(start, end).trim();
                }
            } else {
                int end = cl.indexOf(" lost connection");
                int start = cl.lastIndexOf("]: ", end);
                if (start == -1) start = cl.lastIndexOf(": ", end);
                if (start != -1) {
                    name = cl.substring(start + (cl.substring(start).startsWith("]: ") ? 3 : 2), end).trim();
                }
            }
            if (name != null) {
                name = name.replaceAll("^[^a-zA-Z0-9_]+|[^a-zA-Z0-9_]+$", "");
                if (srv.onlinePlayerNames.remove(name)) {
                    log(id, "  👤 Player Left: " + name);
                    setState(srv, srv.state);
                }
            }
        } catch (Exception e) { Log.e(TAG, "Leave parse error", e); }
    }

    private void handleSetupCompletion(String id, ServerInstance srv) {
        setState(srv, ServerInstance.State.ONLINE);
        if (srv.isAutoSetup()) {
            boolean useBetaJni = getSharedPreferences("koda_settings", MODE_PRIVATE).getBoolean("beta_jni_embedded", false);
            int phase = setupPhase.getOrDefault(id, 0);
            if (phase == 0) {
                if (useBetaJni) {
                    log(id, "  🧩 PHASE 1: Initial Boot OK. (Restart skipped for Embedded JVM)");
                    try { writePaperOptimizationConfigs(srv, new File(srv.getServerDir())); } catch (Exception e) { Log.e(TAG, "Paper config error", e); }
                    
                    if (srv.getAiPrompt() != null && srv.getAiPrompt().trim().length() > 0 && !srv.getAiPrompt().equals("null") && !srv.getAiPrompt().equals("[]")) {
                        log(id, "  🤖 AI SETUP: Generating custom configs via Gemini API... (This may take a moment)");
                        generateAiConfigs(srv);
                    } else {
                        log(id, "  🧩 Applying custom design...");
                        try { writeTabConfig(srv, new File(srv.getServerDir())); } catch (IOException e) { Log.e(TAG, "Tab config error", e); }
                    }
                    
                    log(id, "  ✅ SETUP COMPLETE! Downloading PAPI extensions...");
                    setupPhase.put(id, 3);
                    exec.submit(() -> {
                        sleep(5000);
                        sendCmd(id, "papi ecloud download Server");
                        sleep(2000);
                        sendCmd(id, "papi ecloud download Statistic");
                        sleep(2000);
                        sendCmd(id, "papi ecloud download LuckPerms");
                        sleep(2000);
                        sendCmd(id, "papi ecloud download Player");
                        sleep(2000);
                        sendCmd(id, "papi reload");
                        sleep(1000);
                        sendCmd(id, "tab reload");
                        srv.setAutoSetup(false);
                        ServerRepo.get(TermuxServerService.this).update(srv);
                        log(id, "✓ Server erfolgreich gestartet! Bereit für Spieler.");
                    });
                } else {
                    log(id, "  🧩 PHASE 1: Initial Boot OK. Stopping server to install plugins...");
                    setupPhase.put(id, 1);
                    exec.submit(() -> {
                        sleep(5000);
                        stopServer(srv, false);
                        while (runtimes.containsKey(id)) { sleep(1000); }
                        
                        boolean isModded = srv.getType() == ServerInstance.Type.FABRIC || srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE;
                        File pDir = new File(srv.getServerDir(), isModded ? "mods" : "plugins");
                        pDir.mkdirs();
                        
                        if (!srv.getAiPrompt().isEmpty()) {
                            log(id, "  🤖 AI SETUP: Downloading Plugins...");
                            try {
                                org.json.JSONArray plugs = new org.json.JSONArray(srv.getAiPrompt());
                                for (int i = 0; i < plugs.length(); i++) {
                                    String pid = plugs.getString(i);
                                    String pidLower = pid.toLowerCase();
                                    
                                    if (pidLower.contains("protocollib")) {
                                        log(id, "  🛡️ Intercepted: ProtocolLib -> Downloading from Koda Supabase!");
                                        downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/ProtocolLib.jar", new File(pDir, "ProtocolLib.jar"));
                                        continue;
                                    }
                                    
                                    if (pidLower.contains("geyser") || pidLower.contains("floodgate")) {
                                        log(id, "  🛡️ Intercepted: " + pid + " -> Enabling Native Bedrock Support!");
                                        srv.setBedrockSupport(true);
                                        if (srv.getBedrockPort() == 0) srv.setBedrockPort(getFreePort(10000, 19999));
                                        eu.kodanetwork.mchost.model.ServerRepo.get(TermuxServerService.this).update(srv);
                                        continue;
                                    }
                                    if (pidLower.contains("voicechat") || pidLower.contains("simple-voice-chat")) {
                                        log(id, "  🎙️ Intercepted: " + pid + " -> Enabling Native VoiceChat!");
                                        srv.setVoicechat(true);
                                        if (srv.getVoicechatPort() == 0) srv.setVoicechatPort(getFreePort(20000, 29999));
                                        eu.kodanetwork.mchost.model.ServerRepo.get(TermuxServerService.this).update(srv);
                                        continue;
                                    }
                                    
                                    log(id, "  ⬇️ AI Installing Plugin ID: " + pid);
                                    File pluginFile = eu.kodanetwork.mchost.util.ModrinthHelper.autoDownloadSync(pid, srv);
                                    if (pluginFile != null) {
                                        log(id, "  ✓ Plugin installed: " + pluginFile.getName());
                                    } else {
                                        log(id, "  ✗ Plugin install failed for: " + pid);
                                    }
                                    sleep(500); // Give it a moment
                                }
                            } catch (Exception e) {
                                log(id, "  ❌ AI Setup Failed to parse plugins: " + e.getMessage());
                            }
                        }

                        log(id, "  🧩 Injecting ENFORCEMENT Modules...");
                        ensurePluginsInstalled(srv, pDir);

                        log(id, "  ♻️ Restarting to generate default configs...");
                        mainHandler.post(() -> startServer(srv));
                    });
                }
            } else if (phase == 1) {
                log(id, "  🧩 PHASE 2: Plugin Boot OK. Stopping to apply Custom Configs...");
                setupPhase.put(id, 2);
                exec.submit(() -> {
                    sleep(10000); // Give plugins time to write configs
                    stopServer(srv, false);
                    while (runtimes.containsKey(id)) { sleep(1000); }
                    
                    log(id, "  🧩 Writing Paper optimization configs...");
                    try { writePaperOptimizationConfigs(srv, new File(srv.getServerDir())); } catch (Exception e) { Log.e(TAG, "Paper config error", e); }
                    
                    if (srv.getAiPrompt() != null && srv.getAiPrompt().trim().length() > 0 && !srv.getAiPrompt().equals("null") && !srv.getAiPrompt().equals("[]")) {
                        log(id, "  🤖 AI SETUP: Generating custom configs via Gemini API... (This may take a moment)");
                        generateAiConfigs(srv);
                    } else {
                        log(id, "  🧩 Applying custom design...");
                        try { writeTabConfig(srv, new File(srv.getServerDir())); } catch (IOException e) { Log.e(TAG, "Tab config error", e); }
                    }
                    
                    log(id, "  🚀 PHASE 3: Final Start...");
                    mainHandler.post(() -> startServer(srv));
                });
            } else if (phase == 2) {
                log(id, "  ✅ SETUP COMPLETE! Downloading PAPI extensions...");
                setupPhase.put(id, 3);
                exec.submit(() -> {
                    sleep(10000);
                    sendCmd(id, "papi ecloud download Server");
                    sleep(2000);
                    sendCmd(id, "papi ecloud download Statistic");
                    sleep(2000);
                    sendCmd(id, "papi ecloud download LuckPerms");
                    sleep(2000);
                    sendCmd(id, "papi ecloud download Player");
                    sleep(2000);
                    sendCmd(id, "papi reload");
                    sleep(1000);
                    sendCmd(id, "tab reload");
                    srv.setAutoSetup(false);
                    ServerRepo.get(this).update(srv);
                    log(id, "✓ SETUP_COMPLETE_SUCCESS");
                });
            }
        } else {
            log(id, "✓ Server erfolgreich gestartet! Bereit für Spieler.");
        }
    }

    private final java.util.Map<String, List<String>> logQueue = new java.util.HashMap<>();
    private boolean logFlushPending = false;

    private void log(String id, String msg) {
        RT rt = runtimes.get(id);
        if (rt != null) {
            rt.logs.add(msg);
            if (rt.logs.size() > 3000) {
                rt.logs.subList(0, 500).clear();
            }
        }
        
        synchronized (logQueue) {
            logQueue.computeIfAbsent(id, k -> new ArrayList<>()).add(msg);
            if (!logFlushPending) {
                logFlushPending = true;
                mainHandler.postDelayed(this::flushLogs, 250);
            }
        }
    }

    private void flushLogs() {
        java.util.Map<String, List<String>> toFlush = new java.util.HashMap<>();
        synchronized (logQueue) {
            for (java.util.Map.Entry<String, List<String>> entry : logQueue.entrySet()) {
                toFlush.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            logQueue.clear();
            logFlushPending = false;
        }
        
        for (java.util.Map.Entry<String, List<String>> entry : toFlush.entrySet()) {
            String id = entry.getKey();
            List<String> msgs = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String msg : msgs) {
                sb.append(msg).append("\n");
            }
            String combined = sb.toString();
            for (LogCallback cb : logCbs) {
                cb.onLogLine(id, combined);
            }
        }
    }

    private void setState(ServerInstance s, ServerInstance.State st) {
        s.state = st;
        if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.OFFLINE || st == ServerInstance.State.CRASHED) {
            reportSupabaseStatus(s, st == ServerInstance.State.ONLINE);
        }
        mainHandler.post(() -> { for (StateCallback cb : stateCbs) cb.onStateChanged(s.getId(), st); });
        Intent i = new Intent(BCAST_STATE);
        i.putExtra(EXTRA_ID, s.getId());
        sendBroadcast(i);
        updateNotif();
        if (st == ServerInstance.State.CRASHED) {
            Intent alertIntent = new Intent(this, eu.kodanetwork.mchost.ui.CrashAlertActivity.class);
            alertIntent.putExtra("id", s.getId());
            alertIntent.putExtra("name", s.getName());
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(alertIntent);
        }
    }

    public List<String> getLog(String id) { RT rt = runtimes.get(id); return rt != null ? new ArrayList<>(rt.logs) : new ArrayList<>(); }
    
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "KodaHosting", NotificationManager.IMPORTANCE_LOW);
            c.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private int lastNotifCount = -1;
    
    private void updateNotif() {
        int count = runtimes.size();
        if (count == 0) {
            lastNotifCount = -1;
            releaseWakeLock();
            stopForeground(true);
            return;
        }
        
        if (count == lastNotifCount) return;
        lastNotifCount = count;
        
        acquireWakeLock();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("KodaHosting")
            .setContentText(count + " Server aktiv")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .build();
        startForeground(NOTIF_ID, n);
    }

    private void ensurePluginsInstalled(ServerInstance srv, File pDir) {
        if (srv.getType() == ServerInstance.Type.PAPER || srv.getType() == ServerInstance.Type.PURPUR || srv.getType() == ServerInstance.Type.FOLIA) {
            log(srv.getId(), "  🔌 Installing KodaTransferPlugin...");
            File sysDir = new File(srv.getServerDir(), ".sys");
            sysDir.mkdirs();
            extractPlugin(sysDir, "koda_transfer.jar", "koda_core.jar");
            // Remove old version if it exists
            new File(pDir, "KodaTransferPlugin.jar").delete();
        }

        if (srv.isAutoSetup() && srv.getAiPrompt().isEmpty()) {
            log(srv.getId(), "  🔌 Installing TAB...");
            File tabJar = eu.kodanetwork.mchost.util.ModrinthHelper.autoDownloadSync("9e1Q1EKE", srv);
            if (tabJar == null) {
                String an = (srv.getType() == ServerInstance.Type.NEOFORGE) ? "tab_forge.jar" : "tab_paper.jar";
                try (InputStream is = getAssets().open(an);
                     java.io.FileOutputStream os = new java.io.FileOutputStream(new File(pDir, "TAB.jar"))) {
                    byte[] b = new byte[8192];
                    int r;
                    while ((r = is.read(b)) != -1) os.write(b, 0, r);
                } catch (IOException e) { Log.e(TAG, "Failed to extract TAB", e); }
            }
            
            if (srv.getType() == ServerInstance.Type.PAPER || srv.getType() == ServerInstance.Type.PURPUR || srv.getType() == ServerInstance.Type.FOLIA) {
                log(srv.getId(), "  🔌 Installing LuckPerms + PlaceholderAPI...");
                extractPlugin(pDir, "luckperms.jar", "LuckPerms.jar");
                extractPlugin(pDir, "papi.jar", "PlaceholderAPI.jar");
            }
        }

        if (srv.isBedrockSupport()) {
            File geyserJar = new File(pDir, "Geyser.jar");
            File floodgateJar = new File(pDir, "Floodgate.jar");
            if (!geyserJar.exists() || !floodgateJar.exists()) {
                log(srv.getId(), "  🛡️ Downloading Bedrock Support (Geyser & Floodgate)...");
                downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/geyser.jar", geyserJar);
                downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/floodgate.jar", floodgateJar);
            }
        }

        if (srv.isVoicechat()) {
            File vcJar = new File(pDir, "Voicechat.jar");
            if (!vcJar.exists()) {
                log(srv.getId(), "  🎤 Downloading Voicechat Support...");
                downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/voicechat.jar", vcJar);
            }
        }
    }

    private void extractPlugin(File pDir, String assetName, String targetName) {
        try (InputStream is = getAssets().open(assetName);
             java.io.FileOutputStream os = new java.io.FileOutputStream(new File(pDir, targetName))) {
            byte[] b = new byte[8192];
            int r;
            while ((r = is.read(b)) != -1) os.write(b, 0, r);
        } catch (IOException e) { Log.e(TAG, "Failed to extract " + assetName, e); }
    }

    private void downloadPlugin(String id, String url, File target) {
        exec.submit(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                try (InputStream is = c.getInputStream();
                     java.io.FileOutputStream os = new java.io.FileOutputStream(target)) {
                    byte[] b = new byte[8192];
                    int r;
                    while ((r = is.read(b)) != -1) os.write(b, 0, r);
                }
            } catch (Exception e) {
                log(id, "  ✗ Failed to download plugin: " + target.getName());
            }
        });
    }

    private void writeTabConfig(ServerInstance srv, File dir) throws IOException {
        File tabDir = new File(new File(dir, "plugins"), "TAB");
        tabDir.mkdirs();
        String theme = srv.getThemeColor();
        if (theme == null || theme.isEmpty()) theme = "#FF6B00";
        String domain = srv.getSubdomain() + ".kodanetwork.eu";
        String sName = srv.getName().toUpperCase();

        String config =
            "header-footer:\n" +
            "  enabled: true\n" +
            "  designs:\n" +
            "    default:\n" +
            "      header:\n" +
            "        - ' '\n" +
            "        - '<" + theme + "><bold>" + sName + "</bold></#FFFFFF>'\n" +
            "        - '&8  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500  '\n" +
            "        - '&7Welcome, &f%player%&7! (&f%online%&7 online)'\n" +
            "        - ' '\n" +
            "      footer:\n" +
            "        - ' '\n" +
            "        - '<" + theme + ">&l" + domain + "</" + theme + ">'\n" +
            "        - '&7Ping: &f%ping%ms &8| &7TPS: &f%tps%'\n" +
            "        - ' '\n" +
            "tablist-name-formatting:\n" +
            "  enabled: true\n" +
            "  disable-condition: '%world%=disabledworld'\n" +
            "scoreboard-teams:\n" +
            "  enabled: true\n" +
            "  enable-collision: true\n" +
            "  invisible-nametags: false\n" +
            "  sorting-types:\n" +
            "    - 'GROUPS:owner,admin,mod,helper,builder,vip,default'\n" +
            "    - 'PLACEHOLDER_A_TO_Z:%player%'\n" +
            "  case-sensitive-sorting: true\n" +
            "  can-see-friendly-invisibles: false\n" +
            "  disable-condition: '%world%=disabledworld'\n" +
            "playerlist-objective:\n" +
            "  enabled: true\n" +
            "  value: '%ping%'\n" +
            "  fancy-value: '&7Ping: &f%ping%'\n" +
            "  title: 'TAB'\n" +
            "  render-type: INTEGER\n" +
            "  disable-condition: '%world%=disabledworld'\n" +
            "belowname-objective:\n" +
            "  enabled: false\n" +
            "prevent-spectator-effect:\n" +
            "  enabled: false\n" +
            "bossbar:\n" +
            "  enabled: false\n" +
            "scoreboard:\n" +
            "  enabled: true\n" +
            "  toggle-command: /sb\n" +
            "  remember-toggle-choice: false\n" +
            "  hidden-by-default: false\n" +
            "  delay-on-join-milliseconds: 0\n" +
            "  scoreboards:\n" +
            "    main:\n" +
            "      title: '<" + theme + ">&l" + sName + "</" + theme + ">'\n" +
            "      lines:\n" +
            "        - '  &7%statistic_hours_played%h playtime'\n" +
            "        - ''\n" +
            "        - ' <" + theme + ">● &f%player% &7(❤ %player_health%)</" + theme + ">'\n" +
            "        - ' &f\u25cf Rank: %luckperms_prefix%'\n" +
            "        - ''\n" +
            "        - '      &8\u25ac \u25ad \u25ac \u25ad \u25ac \u25ad \u25ac'\n" +
            "        - '    <" + theme + ">\u00bb &7Ping: &f%ping%ms</" + theme + ">'\n" +
            "        - '    <" + theme + ">\u00bb &7Memory: &f%memory-used%MB</" + theme + ">'\n" +
            "        - ''\n" +
            "        - ' <" + theme + ">\u25cf &fPlayers: &7%online%</" + theme + ">'\n" +
            "        - ' &f\u25cf Server: &aOnline'\n" +
            "        - ''\n" +
            "        - '<" + theme + ">" + domain + "</" + theme + ">'\n" +
            "layout:\n" +
            "  enabled: false\n" +
            "ping-spoof:\n" +
            "  enabled: false\n" +
            "global-playerlist:\n" +
            "  enabled: false\n" +
            "placeholders:\n" +
            "  date-format: dd.MM.yyyy\n" +
            "  time-format: '[HH:mm:ss]'\n" +
            "  time-offset: 0\n" +
            "  register-tab-expansion: false\n" +
            "placeholder-refresh-intervals:\n" +
            "  default-refresh-interval: 500\n" +
            "  '%ping%': 500\n" +
            "  '%player_health%': 200\n" +
            "  '%luckperms_prefix%': 1000\n" +
            "  '%statistic_hours_played%': 5000\n" +
            "  '%memory-used%': 1000\n" +
            "assign-groups-by-permissions: false\n" +
            "primary-group-finding-list:\n" +
            "  - Owner\n" +
            "  - Admin\n" +
            "  - Mod\n" +
            "  - Helper\n" +
            "  - default\n" +
            "permission-refresh-interval: 1000\n" +
            "debug: false\n" +
            "per-world-playerlist:\n" +
            "  enabled: false\n" +
            "use-online-uuid-in-tablist: true\n" +
            "components:\n" +
            "  minimessage-support: true\n" +
            "  disable-shadow-for-heads: true\n" +
            "config-version: 6\n";

        write(new File(tabDir, "config.yml"), config);
    }

    private void writeFrpcConfig(ServerInstance s, File dir) throws IOException {
        String toml = "serverAddr = \"" + BORE_HOST + "\"\n" +
            "serverPort = 7000\n" +
            "auth.token = \"koda123\"\n\n" +
            "[[proxies]]\n" +
            "name = \"mc-java-" + s.getId().substring(0, 4) + "\"\n" +
            "type = \"tcp\"\n" +
            "localIP = \"127.0.0.1\"\n" +
            "localPort = " + s.getPort() + "\n" +
            "remotePort = " + s.getPort() + "\n";

        if (s.isBedrockSupport() && s.getBedrockPort() > 0) {
            toml += "\n[[proxies]]\n" +
                "name = \"mc-bedrock-" + s.getId().substring(0, 4) + "\"\n" +
                "type = \"udp\"\n" +
                "localIP = \"127.0.0.1\"\n" +
                "localPort = " + s.getBedrockPort() + "\n" +
                "remotePort = " + s.getBedrockPort() + "\n";
        }
        
        if (s.isVoicechat() && s.getVoicechatPort() > 0) {
            toml += "\n[[proxies]]\n" +
                "name = \"mc-vc-" + s.getId().substring(0, 4) + "\"\n" +
                "type = \"udp\"\n" +
                "localIP = \"127.0.0.1\"\n" +
                "localPort = " + s.getVoicechatPort() + "\n" +
                "remotePort = " + s.getVoicechatPort() + "\n";
        }

        write(new File(dir, "frpc.toml"), toml);
    }

    private void writeDynamicPluginConfigs(ServerInstance srv, File serverDir) {
        File pluginsDir = new File(serverDir, "plugins");
        pluginsDir.mkdirs();

        if (srv.isBedrockSupport() && srv.getBedrockPort() > 0) {
            try {
                File geyserDir = new File(pluginsDir, "Geyser-Spigot");
                geyserDir.mkdirs();
                String geyserConfig = readAsset("geyser_config.yml").replace("{GEYSER_PORT}", String.valueOf(srv.getBedrockPort()));
                write(new File(geyserDir, "config.yml"), geyserConfig);

                File floodgateDir = new File(pluginsDir, "floodgate");
                floodgateDir.mkdirs();
                String floodgateConfig = readAsset("floodgate_config.yml");
                write(new File(floodgateDir, "config.yml"), floodgateConfig);

                extractPlugin(geyserDir, "key.pem", "key.pem");
                extractPlugin(floodgateDir, "key.pem", "key.pem");
            } catch (Exception e) { Log.e(TAG, "Failed to write Bedrock configs", e); }
        }

        if (srv.isVoicechat() && srv.getVoicechatPort() > 0) {
            try {
                File vcDir = new File(pluginsDir, "voicechat");
                vcDir.mkdirs();
                String vcConfig = readAsset("voicechat-server.properties").replace("{VOICECHAT_PORT}", String.valueOf(srv.getVoicechatPort()));
                write(new File(vcDir, "voicechat-server.properties"), vcConfig);
            } catch (Exception e) { Log.e(TAG, "Failed to write Voicechat config", e); }
        }
    }

    private int getFreePort(int min, int max) {
        for (int i = 0; i < 50; i++) {
            int p = (int)(Math.random() * (max - min)) + min;
            try (java.net.ServerSocket s = new java.net.ServerSocket(p);
                 java.net.DatagramSocket d = new java.net.DatagramSocket(p)) {
                return p;
            } catch (Exception ignored) {}
        }
        return min + (int)(Math.random() * 1000);
    }

    private String readAsset(String assetName) {
        try (InputStream is = getAssets().open(assetName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void writeEula(File dir) throws IOException {
        write(new File(dir, "eula.txt"), "eula=true");
    }

    private void writePaperOptimizationConfigs(ServerInstance srv, File dir) {
        // Only for Paper/Purpur/Folia servers
        if (srv.getType() != ServerInstance.Type.PAPER && srv.getType() != ServerInstance.Type.PURPUR && srv.getType() != ServerInstance.Type.FOLIA) return;
        
        try {
            // Paper global config
            File configDir = new File(dir, "config");
            configDir.mkdirs();
            File paperGlobal = new File(configDir, "paper-global.yml");
            if (!paperGlobal.exists()) {
                String config = 
                    "# Paper Global Configuration - Optimized for Mobile\n" +
                    "_version: 29\n" +
                    "chunk-system:\n" +
                    "  gen-parallelism: default\n" +
                    "  io-threads: 2\n" +
                    "  worker-threads: 2\n" +
                    "misc:\n" +
                    "  max-joins-per-tick: 3\n" +
                    "  fix-entity-position-desync: true\n" +
                    "  use-alternative-luck-formula: true\n" +
                    "packet-limiter:\n" +
                    "  kick-message: '<red>Too many packets!'\n" +
                    "  limits:\n" +
                    "    all:\n" +
                    "      interval: 7.0\n" +
                    "      max-packet-rate: 500.0\n" +
                    "watchdog:\n" +
                    "  early-warning-delay: 180000\n" +
                    "  early-warning-every: 120000\n";
                write(paperGlobal, config);
            }
            
            // Paper world defaults
            File paperWorld = new File(configDir, "paper-world-defaults.yml");
            if (!paperWorld.exists()) {
                String worldConfig =
                    "# Paper World Defaults - Mobile Optimized\n" +
                    "_version: 31\n" +
                    "chunks:\n" +
                    "  auto-save-interval: 6000\n" +
                    "  delay-chunk-unloads-by: 10s\n" +
                    "  max-auto-save-chunks-per-tick: 8\n" +
                    "  prevent-moving-into-unloaded-chunks: true\n" +
                    "entities:\n" +
                    "  armor-stands:\n" +
                    "    do-collision-entity-lookups: false\n" +
                    "  spawning:\n" +
                    "    per-player-mob-spawns: true\n" +
                    "    despawn-ranges:\n" +
                    "      monster:\n" +
                    "        hard: 96\n" +
                    "        soft: 28\n" +
                    "      creature:\n" +
                    "        hard: 96\n" +
                    "        soft: 28\n" +
                    "      ambient:\n" +
                    "        hard: 72\n" +
                    "        soft: 28\n" +
                    "      misc:\n" +
                    "        hard: 96\n" +
                    "        soft: 28\n" +
                    "environment:\n" +
                    "  optimize-explosions: true\n" +
                    "  treasure-maps:\n" +
                    "    enabled: true\n" +
                    "    find-already-discovered:\n" +
                    "      loot-tables: true\n" +
                    "      villager-trade: true\n" +
                    "  water-over-lava-flow-speed: 5\n" +
                    "hopper:\n" +
                    "  cooldown-when-full: true\n" +
                    "  disable-move-event: false\n" +
                    "misc:\n" +
                    "  redstone-implementation: ALTERNATE_CURRENT\n" +
                    "  update-pathfinding-on-block-update: false\n" +
                    "tick-rates:\n" +
                    "  behavior:\n" +
                    "    villager:\n" +
                    "      validatenearbypoi: 60\n" +
                    "  container-update: 1\n" +
                    "  grass-spread: 4\n" +
                    "  mob-spawner: 2\n" +
                    "  sensor:\n" +
                    "    villager:\n" +
                    "      secondarypoisensor: 80\n";
                write(paperWorld, worldConfig);
            }
            
            // Spigot config
            File spigotConfig = new File(dir, "spigot.yml");
            if (!spigotConfig.exists()) {
                String spigotYml =
                    "# Spigot Configuration - Mobile Optimized\n" +
                    "world-settings:\n" +
                    "  default:\n" +
                    "    merge-radius:\n" +
                    "      item: 4.0\n" +
                    "      exp: 6.0\n" +
                    "    mob-spawn-range: 6\n" +
                    "    entity-activation-range:\n" +
                    "      animals: 16\n" +
                    "      monsters: 24\n" +
                    "      raiders: 48\n" +
                    "      misc: 8\n" +
                    "      water: 8\n" +
                    "      villagers: 16\n" +
                    "      flying-monsters: 48\n" +
                    "    tick-inactive-villagers: false\n" +
                    "    nerf-spawner-mobs: false\n";
                write(spigotConfig, spigotYml);
            }
            // Default server icon
            File iconFile = new File(dir, "server-icon.png");
            if (!iconFile.exists()) {
                try {
                    android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(getPackageName());
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    d.draw(canvas);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(iconFile)) {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create server-icon.png", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write Paper optimization configs", e);
        }
    }

    private void writeProps(ServerInstance s, File dir) throws IOException { 
        File f = new File(dir, "server.properties");
        java.util.Properties props = new java.util.Properties();
        if (f.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }
        props.setProperty("server-port", String.valueOf(s.getPort()));
        if (!props.containsKey("online-mode")) props.setProperty("online-mode", "false");
        props.setProperty("motd", s.getMotd());
        // Performance defaults for mobile
        if (!props.containsKey("sync-chunk-writes")) props.setProperty("sync-chunk-writes", "false");
        if (!props.containsKey("view-distance")) props.setProperty("view-distance", "8");
        if (!props.containsKey("simulation-distance")) props.setProperty("simulation-distance", "6");
        if (!props.containsKey("spawn-protection")) props.setProperty("spawn-protection", "0");
        if (!props.containsKey("max-tick-time")) props.setProperty("max-tick-time", "120000");
        if (!props.containsKey("network-compression-threshold")) props.setProperty("network-compression-threshold", "256");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            props.store(fos, "Modified by KodaHosting");
        }
    }

    private void write(File f, String c) throws IOException {
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(c);
        }
    }

    private String readFullFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append("\n");
        } catch (Exception ignored) {}
        return sb.toString();
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "App swiped away, stopping all servers...");
        for (ServerInstance srv : ServerRepo.get(this).all()) {
            if (runtimes.containsKey(srv.getId())) {
                stopServer(srv, false);
            }
        }
        // Force stop after a short delay if servers don't stop gracefully
        mainHandler.postDelayed(() -> {
            for (ServerInstance srv : ServerRepo.get(this).all()) {
                if (runtimes.containsKey(srv.getId())) {
                    stopServer(srv, true);
                }
            }
            stopSelf();
        }, 3000);
    }

    private void reportSupabaseStatus(ServerInstance srv, boolean isOnline) {
        exec.submit(() -> {
            try {
                String domain = srv.getSubdomain(); // DB stores just subdomain, not full domain
                
                String version = isOnline ? (srv.getVersion() == null ? "1.21.11" : srv.getVersion()) : "";
                int players = 0;
                String playersStr = "";
                // If online, ping localhost to get true player count!
                if (isOnline) {
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.setSoTimeout(2000);
                        s.connect(new java.net.InetSocketAddress("127.0.0.1", srv.getPort()), 2000);
                        java.io.DataOutputStream out = new java.io.DataOutputStream(s.getOutputStream());
                        java.io.DataInputStream in = new java.io.DataInputStream(s.getInputStream());
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        java.io.DataOutputStream handshake = new java.io.DataOutputStream(b);
                        handshake.writeByte(0x00);
                        eu.kodanetwork.mchost.util.VarIntHelper.writeVarInt(handshake, 47);
                        eu.kodanetwork.mchost.util.VarIntHelper.writeString(handshake, "127.0.0.1");
                        handshake.writeShort(srv.getPort());
                        eu.kodanetwork.mchost.util.VarIntHelper.writeVarInt(handshake, 1);
                        eu.kodanetwork.mchost.util.VarIntHelper.writeVarInt(out, b.size());
                        out.write(b.toByteArray());
                        out.writeByte(0x01); out.writeByte(0x00);
                        
                        eu.kodanetwork.mchost.util.VarIntHelper.readVarInt(in);
                        int id = eu.kodanetwork.mchost.util.VarIntHelper.readVarInt(in);
                        if (id == 0) {
                            int len = eu.kodanetwork.mchost.util.VarIntHelper.readVarInt(in);
                            byte[] data = new byte[len];
                            in.readFully(data);
                            String json = new String(data, "UTF-8");
                            org.json.JSONObject root = new org.json.JSONObject(json);
                            if (root.has("players")) {
                                org.json.JSONObject pObj = root.getJSONObject("players");
                                players = pObj.getInt("online");
                                if (pObj.has("sample")) {
                                    org.json.JSONArray sArr = pObj.getJSONArray("sample");
                                    java.util.List<String> pNames = new java.util.ArrayList<>();
                                    for(int i=0; i<sArr.length(); i++) pNames.add(sArr.getJSONObject(i).getString("name"));
                                    playersStr = String.join(",", pNames);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                
                String verStr = version;
                if (!playersStr.isEmpty()) verStr = version + " | " + playersStr;
                String jsonBody = "{\"online_players\": " + players + ", \"server_version\": \"" + verStr + "\"}";
                
                okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/koda_servers?host=eq." + domain)
                    .patch(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Supabase report failed HTTP " + response.code() + " " + response.body().string());
                }
                response.close();
            } catch (Exception e) {
                Log.w(TAG, "Supabase report exception: " + e.getMessage());
            }
        });
    }

    private void checkRemoteCommands(ServerInstance srv) {
        android.content.SharedPreferences rPrefs = getSharedPreferences("koda_settings", android.content.Context.MODE_PRIVATE);
        if (!rPrefs.getBoolean("lobby_remote_control", true)) return;
        exec.submit(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/koda_servers?host=eq." + srv.getSubdomain() + "&select=server_version")
                    .get()
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    if (arr.length() > 0) {
                        String ver = arr.getJSONObject(0).optString("server_version", "");
                        if (ver.startsWith("CMD:")) {
                            String cmd = ver.substring(4);
                            Log.d(TAG, "Received Remote Command: " + cmd);
                            // Clear it immediately
                            String jsonBody = "{\"server_version\": \"" + (srv.getVersion() == null ? "1.21.11" : srv.getVersion()) + "\"}";
                            okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json"));
                            okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                .url(SUPABASE_REST + "/koda_servers?host=eq." + srv.getSubdomain())
                                .patch(body)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "return=minimal")
                                .addHeader("apikey", SUPABASE_KEY)
                                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                .build();
                            httpClient.newCall(patchReq).execute().close();

                            // Execute command
                            if (cmd.equals("START")) {
                                if (srv.state != ServerInstance.State.ONLINE && srv.state != ServerInstance.State.STARTING) {
                                    startServer(srv);
                                }
                            } else if (cmd.equals("STOP")) {
                                stopServer(srv, false);
                            } else if (cmd.equals("RESTART")) {
                                stopServer(srv, false);
                                mainHandler.postDelayed(() -> startServer(srv), 4000);
                            } else if (cmd.equals("WHITELIST_ON")) {
                                sendCmd(srv.getId(), "whitelist on");
                            } else if (cmd.equals("WHITELIST_OFF")) {
                                sendCmd(srv.getId(), "whitelist off");
                            } else if (cmd.startsWith("INSTALL_PLUGIN_")) {
                                String projectId = cmd.substring("INSTALL_PLUGIN_".length());
                                log(srv.getId(), "  \uD83D\uDCE6 Installing plugin from Modrinth: " + projectId);
                                File pluginFile = eu.kodanetwork.mchost.util.ModrinthHelper.autoDownloadSync(projectId, srv);
                                if (pluginFile != null) {
                                    log(srv.getId(), "  ✓ Plugin installed: " + pluginFile.getName());
                                    log(srv.getId(), "  \u2139 Restart server to activate the plugin.");
                                } else {
                                    log(srv.getId(), "  ✗ Plugin install failed for: " + projectId);
                                }
                            } else if (cmd.startsWith("EXEC_")) {
                                sendCmd(srv.getId(), cmd.substring(5));
                            }
                        }
                    }
                }
                response.close();
            } catch (Exception e) {
                Log.w(TAG, "Remote command check failed: " + e.getMessage());
            }
        });
    }

    private void generateAiConfigs(ServerInstance srv) {
        String apiKey = getSharedPreferences("koda_settings", MODE_PRIVATE).getString("gemini_api_key", "");
        if (apiKey.isEmpty()) {
            log(srv.getId(), "  ❌ AI Config skipped: No API Key.");
            return;
        }

        try {
            try {
                java.io.File iconFile = new java.io.File(srv.getServerDir(), "server-icon.png");
                if (!iconFile.exists()) {
                    log(srv.getId(), "  🤖 AI SETUP: Generiere Icon...");
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    int color = android.graphics.Color.parseColor(srv.getThemeColor() != null ? srv.getThemeColor() : "#4CAF50");
                    canvas.drawColor(color);
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColor(android.graphics.Color.WHITE);
                    paint.setTextSize(40f);
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    String initial = srv.getName().length() > 0 ? srv.getName().substring(0, 1).toUpperCase() : "S";
                    canvas.drawText(initial, 32f, 46f, paint);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(iconFile);
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    log(srv.getId(), "  ✓ Icon generiert.");
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to create server icon", e);
            }

            File pluginsDir = new File(srv.getServerDir(), "plugins");
            if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
                return;
            }

            File[] pluginFolders = pluginsDir.listFiles(File::isDirectory);
            if (pluginFolders == null || pluginFolders.length == 0) {
                return;
            }

            String[] targetNames = {"config.yml", "messages.yml", "messages_en.yml", "lang.yml", "motd.txt"};
            int total = pluginFolders.length;
            int current = 1;

            for (File pFolder : pluginFolders) {
                String pName = pFolder.getName();
                if (pName.equalsIgnoreCase("bStats") || pName.equalsIgnoreCase("PluginMetrics") || pName.equalsIgnoreCase("spark")) {
                    current++;
                    continue;
                }

                StringBuilder existingConfigs = new StringBuilder("Here are the default config files for plugin " + pName + ":\n\n");
                boolean hasFiles = false;

                for (String tName : targetNames) {
                    File f = new File(pFolder, tName);
                    if (f.exists() && f.length() < 50000) {
                        hasFiles = true;
                        existingConfigs.append("=== plugins/").append(pName).append("/").append(tName).append(" ===\n");
                        try { existingConfigs.append(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
                        existingConfigs.append("\n\n");
                    }
                }

                if (!hasFiles) {
                    current++;
                    continue;
                }

                log(srv.getId(), "  🤖 AI SETUP: Configuring " + pName + " (" + current + "/" + total + ")...");

                try {
                    String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey;
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    org.json.JSONObject payload = new org.json.JSONObject();
                    org.json.JSONObject sysInst = new org.json.JSONObject();
                    String promptText = "You are a Minecraft server configuration expert. The server theme color is: " + srv.getThemeColor() + 
                        ". Your task is to EDIT the provided default configs to match the theme color. " +
                        "CRITICAL YAML RULES: 1. Your configs MUST be 100% valid YAML. 2. Keep the configuration extremely simple. " +
                        "3. Do NOT rewrite complex nested structures, only modify prefixes/suffixes/colors. 4. Use double quotes around strings with special characters like '&'. " +
                        "5. NEVER use HEX/RGB color codes like &#FF4500 or <#FF0000>. YOU MUST ONLY use standard Minecraft legacy color codes (e.g. &a, &b, &c, &l, &f). " +
                        "You MUST return ONLY valid JSON matching this schema: {\"configs\": [{\"path\": \"plugins/" + pName + "/config.yml\", \"content\": \"...\"}]}";
                    
                    sysInst.put("parts", new org.json.JSONArray().put(new org.json.JSONObject().put("text", promptText)));
                    payload.put("systemInstruction", sysInst);

                    org.json.JSONObject contents = new org.json.JSONObject();
                    contents.put("role", "user");
                    contents.put("parts", new org.json.JSONArray().put(new org.json.JSONObject().put("text", existingConfigs.toString() + "\n\nPlease generate the edited config files now.")));
                    payload.put("contents", new org.json.JSONArray().put(contents));

                    org.json.JSONObject genConfig = new org.json.JSONObject();
                    genConfig.put("responseMimeType", "application/json");
                    payload.put("generationConfig", genConfig);

                    java.io.OutputStream os = conn.getOutputStream();
                    os.write(payload.toString().getBytes());
                    os.flush(); os.close();

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        java.io.InputStreamReader r = new java.io.InputStreamReader(conn.getInputStream());
                        StringBuilder sb = new StringBuilder();
                        int c; while ((c = r.read()) != -1) sb.append((char) c);
                        r.close();

                        org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                        String resText = root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                        
                        org.json.JSONObject resJson = new org.json.JSONObject(resText);
                        org.json.JSONArray configs = resJson.optJSONArray("configs");
                        if (configs != null) {
                            for (int i = 0; i < configs.length(); i++) {
                                org.json.JSONObject configObj = configs.getJSONObject(i);
                                String path = configObj.optString("path");
                                String content = configObj.optString("content");
                                if (!path.isEmpty() && !content.isEmpty()) {
                                    File targetFile = new File(srv.getServerDir(), path);
                                    targetFile.getParentFile().mkdirs();
                                    java.nio.file.Files.write(targetFile.toPath(), content.getBytes());
                                    log(srv.getId(), "  ✓ Updated config: " + path);
                                }
                            }
                        }
                    } else {
                        log(srv.getId(), "  ❌ API Error for " + pName + ": " + code);
                    }
                    
                    Thread.sleep(3000); // Prevent rate limiting
                } catch (Exception e) {
                    log(srv.getId(), "  ❌ Exception for " + pName + ": " + e.getMessage());
                }

                current++;
            }
        } catch (Exception e) {
            log(srv.getId(), "  ❌ Exception in config generation setup: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        for (RT rt : runtimes.values()) {
            if (rt.proc != null) rt.proc.destroyForcibly();
            if (rt.frpcProc != null) rt.frpcProc.destroyForcibly();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        exec.shutdownNow();
        Log.d(TAG, "Service Destroyed. Processes killed.");
        super.onDestroy();
    }
}
