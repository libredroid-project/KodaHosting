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
 * KodaServerService handles the lifecycle of Minecraft servers running on the device.
 * It supports both Termux-based execution and Native execution using PRoot.
 */
public class KodaServerService extends Service {
    private static final String TAG = "KodaSvc";
    private static final String CHANNEL = "server_svc";
    private static final int NOTIF_ID = 101;
    private static final String BORE_HOST = eu.kodanetwork.mchost.security.PraetorSecurity.getBoreHost();

    public static final String BCAST_STATE    = "eu.kodanetwork.mchost.STATE_CHANGE";
    public static final String BCAST_LOG      = "eu.kodanetwork.mchost.LOG_LINE";
    public static final String EXTRA_ID       = "srv_id";
    public static final String EXTRA_LOG      = "log_msg";

    public static boolean embeddedJvmStopped = false;
    public static boolean appIsForeground = false;

    public static final String ACTION_START   = "START";
    public static final String ACTION_STOP    = "STOP";
    public static final String ACTION_RESTART = "RESTART";
    public static final String ACTION_KILL    = "KILL";
    public static final String ACTION_INSTALL_PLUGIN_FLOW = "INSTALL_PLUGIN_FLOW";

    private final IBinder binder = new LocalBinder();
    private final Map<String, RT> runtimes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> setupPhase = new HashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String SUPABASE_REST = "https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1";
    private static final String SUPABASE_KEY = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
    private final List<StateCallback> stateCbs = new ArrayList<>();
    private final List<LogCallback> logCbs = new ArrayList<>();
    private PowerManager.WakeLock wakeLock;

    // Removed startEmbeddedJvmNative

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

    public class LocalBinder extends Binder { public KodaServerService get() { return KodaServerService.this; } }

    private static class RT {
        Process proc;
        Process frpcProc;
        java.io.PrintStream stdin;
        String fifoPath;
        boolean isNative;
        Process process;
        Thread loggerThread;
        Thread boreThread;
        java.io.FileOutputStream dummyWriter;
        eu.kodanetwork.mchost.IJvmService jvmService;
        String jvmClassName;
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
                checkFileTransfers(srv);
            }
            selfHealPendingRows();
        }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
        
        // Capacity Watchdog: Checks every 30s if we bypassed the queue while offline
        scheduler.scheduleAtFixedRate(this::checkCapacityWatchdog, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
        
        scheduler.scheduleAtFixedRate(this::pollNewRemoteServers, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        
        scheduler.scheduleAtFixedRate(this::pingAppStatus, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        
        Log.d(TAG, "Service Created.");
    }
    
    private void pingAppStatus() {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String uuid = prefs.getString("app_uuid", "");
        if (uuid.isEmpty()) return;

        exec.submit(() -> {
            try {
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(mi);
                long freeMegs = mi.availMem / 1048576L;
                long totalMegs = mi.totalMem / 1048576L;

                android.content.IntentFilter ifilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale = batteryStatus != null ? batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) : -1;
                float batteryPct = level * 100 / (float)scale;
                int status = batteryStatus != null ? batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) : -1;
                boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                String resolution = metrics.widthPixels + "x" + metrics.heightPixels;

                String networkType = "UNKNOWN";
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    if (activeNetwork != null) {
                        networkType = activeNetwork.getTypeName();
                    }
                }

                String appVersion = "Unknown";
                try {
                    android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    appVersion = pInfo.versionName;
                } catch (Exception ignored) {}

                String state = appIsForeground ? "FOREGROUND" : "BACKGROUND";
                boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));
                
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("app_state", state);
                payload.put("app_last_ping", "now()");
                payload.put("device_ram_mb", freeMegs);
                payload.put("device_model", android.os.Build.MODEL);
                payload.put("os_version", "Android " + android.os.Build.VERSION.RELEASE);
                payload.put("app_version", appVersion);
                payload.put("total_ram_mb", totalMegs);
                payload.put("free_ram_mb", freeMegs);
                payload.put("cpu_cores", Runtime.getRuntime().availableProcessors());
                payload.put("screen_resolution", resolution);
                payload.put("battery_level", (int) batteryPct);
                payload.put("is_charging", isCharging);
                payload.put("network_type", networkType);

                org.json.JSONObject rpcBody = new org.json.JSONObject();
                rpcBody.put("p_app_uuid", uuid);
                rpcBody.put("p_payload", payload);

                okhttp3.Request requestRpc = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/rpc/rpc_patch_user")
                    .post(okhttp3.RequestBody.create(rpcBody.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8")))
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                httpClient.newCall(requestRpc).execute().close();
            } catch (Exception e) {
                // Ignore, table might not be updated yet
            }
        });
    }

    /** Re-inserts missing koda_servers rows when a creation INSERT failed (pending_row_sync flag). */
    private void selfHealPendingRows() {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if (!prefs.getBoolean("pending_row_sync", false)) return;
        String appUuid = prefs.getString("app_uuid", "");
        if (appUuid.isEmpty()) return;
        exec.submit(() -> {
            try {
                boolean allOk = true;
                for (ServerInstance srv : ServerRepo.get(this).all()) {
                    if (srv.getSubdomain() == null || srv.getSubdomain().isEmpty()) continue;
                    org.json.JSONObject row = new org.json.JSONObject()
                            .put("host", srv.getSubdomain())
                            .put("owner_app_uuid", appUuid)
                            .put("base_domain", srv.getBaseDomain());
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(
                            row.toString(), okhttp3.MediaType.parse("application/json"));
                    okhttp3.Request req = new okhttp3.Request.Builder()
                            .url(SUPABASE_REST + "/koda_servers")
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .addHeader("apikey", SUPABASE_KEY)
                            .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                            .build();
                    try (okhttp3.Response resp = httpClient.newCall(req).execute()) {
                        if (!resp.isSuccessful()) allOk = false;
                    }
                }
                if (allOk) {
                    prefs.edit().putBoolean("pending_row_sync", false).apply();
                    Log.i(TAG, "Self-heal: missing server rows re-synced");
                }
            } catch (Exception e) {
                Log.w(TAG, "Self-heal row sync failed: " + e.getMessage());
            }
        });
    }

    private void pollNewRemoteServers() {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String uuid = prefs.getString("app_uuid", "");
        if (uuid.isEmpty()) return;

        exec.submit(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/koda_servers?owner_app_uuid=eq." + uuid + "&select=id,host,ram_mb,server_version,base_domain")
                    .get()
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    // Empty local repo = fresh install after reinstall -> restore cloud rows as placeholders
                    boolean freshInstall = ServerRepo.get(this).all().isEmpty();
                    int restored = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        String remoteId = obj.optString("id");
                        String host = obj.optString("host");
                        int ram = obj.optInt("ram_mb", 1024);
                        String ver = obj.optString("server_version", "");

                        if (host.startsWith("deleted_")) continue;

                        boolean exists = false;
                        for (ServerInstance srv : ServerRepo.get(this).all()) {
                            if (remoteId.equals(srv.getId()) || host.equals(srv.getSubdomain())) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists && ver.startsWith("CMD:INSTALL_")) {
                            Log.i(TAG, "Found new remote server request: " + host);
                            ServerInstance s = new ServerInstance();
                            s.setId(remoteId);
                            if (host.startsWith("db_")) {
                                s.setName(host.substring(3));
                                s.setSubdomain(host);
                                s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.MARIADB);
                                s.setUseNative(true);
                            } else {
                                s.setName(host);
                                s.setSubdomain(host);
                                
                                // Parse CMD:INSTALL_{ENGINE}_{VERSION}[_SETUP_{COLOR}]
                                String[] parts = ver.split("_");
                                if (parts.length >= 2) {
                                    String engineStr = parts[1]; // PAPER, PURPUR, FABRIC...
                                    try {
                                        s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.valueOf(engineStr));
                                    } catch (Exception e) {
                                        s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.VANILLA);
                                    }
                                } else {
                                    s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.VANILLA);
                                }
                                
                                if (parts.length >= 3 && !parts[2].equals("SETUP")) {
                                    s.setVersion(parts[2]);
                                } else {
                                    s.setVersion("1.21.4");
                                }
                                
                                // Handle SETUP color
                                if (ver.contains("_SETUP_")) {
                                    int setupIdx = ver.indexOf("_SETUP_") + 7;
                                    if (setupIdx < ver.length()) {
                                        String colorHex = "#" + ver.substring(setupIdx);
                                        // Save a minimal setup.json so the orchestrator runs the Auto Design
                                        java.io.File serverDir = new java.io.File(getFilesDir(), "servers/" + s.getId());
                                        serverDir.mkdirs();
                                        try {
                                            java.io.File setupFile = new java.io.File(serverDir, "setup.json");
                                            String setupJson = "{\"current_phase\": 2, \"theme_color\": \"" + colorHex + "\", \"plugins\": []}";
                                            java.io.FileOutputStream fos = new java.io.FileOutputStream(setupFile);
                                            fos.write(setupJson.getBytes());
                                            fos.close();
                                        } catch (Exception e) {
                                            Log.e(TAG, "Failed to create remote setup.json", e);
                                        }
                                    }
                                }
                            }
                            s.setRamMB(ram);
                            ServerRepo.get(this).add(s);

                            // Automatically start it so it installs
                            mainHandler.post(() -> startServer(s));
                        } else if (freshInstall && !ver.equals("DELETED") && !ver.startsWith("CMD:")) {
                            // Reinstall recovery: local repo is empty but the cloud still has rows.
                            // Restore them as HIBERNATED placeholders so name/settings/dashboard stay consistent.
                            Log.i(TAG, "Restoring server from cloud (hibernated): " + host);
                            ServerInstance s = new ServerInstance();
                            s.setId(remoteId);
                            if (host.startsWith("db_")) {
                                s.setName(host.substring(3));
                                s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.MARIADB);
                                s.setUseNative(true);
                            } else {
                                s.setName(host);
                                s.setType(eu.kodanetwork.mchost.model.ServerInstance.Type.VANILLA);
                            }
                            s.setSubdomain(host);
                            String baseDomain = obj.optString("base_domain", "");
                            if (!baseDomain.isEmpty()) s.setBaseDomain(baseDomain);
                            String realVer = ver;
                            if (realVer.startsWith("OFFLINE|")) realVer = realVer.substring("OFFLINE|".length());
                            if (realVer.contains(" | ")) realVer = realVer.substring(0, realVer.indexOf(" | "));
                            if (realVer.isEmpty() || realVer.equals("HIBERNATED")) realVer = "1.21.11";
                            s.setVersion(realVer);
                            s.setRamMB(ram);
                            s.state = ServerInstance.State.HIBERNATED;
                            ServerRepo.get(this).add(s);
                            restored++;
                        }
                    }
                    if (restored > 0) {
                        final int count = restored;
                        mainHandler.post(() -> android.widget.Toast.makeText(this,
                                getString(R.string.cloud_servers_restored, count),
                                android.widget.Toast.LENGTH_LONG).show());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error polling remote servers", e);
            }
        });
    }
    
    private void checkCapacityWatchdog() {
        boolean hasRunningServer = false;
        for (ServerInstance srv : ServerRepo.get(this).all()) {
            if (srv.state == ServerInstance.State.ONLINE || srv.state == ServerInstance.State.SETTING_UP || srv.state == ServerInstance.State.STARTING) {
                hasRunningServer = true;
                break;
            }
        }
        if (!hasRunningServer || !eu.kodanetwork.mchost.util.NetworkMonitorManager.isInternetAvailable(this)) return;

        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?server_version=neq.&server_version=neq.HIBERNATED")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Prefer", "count=exact,head=true")
                .head()
                .build();

            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String countStr = response.header("Content-Range");
                    if (countStr != null && countStr.contains("/")) {
                        int currentCount = Integer.parseInt(countStr.split("/")[1]);
                        if (currentCount >= eu.kodanetwork.mchost.ui.ServerDetailActivity.MAX_GLOBAL_SERVERS) {
                            Log.w(TAG, "Watchdog triggered: Capacity reached (" + currentCount + " >= " + eu.kodanetwork.mchost.ui.ServerDetailActivity.MAX_GLOBAL_SERVERS + "). Stopping servers...");
                            for (ServerInstance srv : ServerRepo.get(this).all()) {
                                if (srv.state == ServerInstance.State.ONLINE || srv.state == ServerInstance.State.SETTING_UP || srv.state == ServerInstance.State.STARTING) {
                                    log(srv.getId(), "🚨 " + getString(R.string.praetor_security_violation));
                                    log(srv.getId(), getString(R.string.watchdog_limit_reached, eu.kodanetwork.mchost.ui.ServerDetailActivity.MAX_GLOBAL_SERVERS));
                                    stopServer(srv, true);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Watchdog error", e);
        }
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
        android.app.Notification n = new android.app.Notification.Builder(this, CHANNEL)
            .setContentTitle("KodaHosting Service")
            .setContentText("Hintergrunddienst aktiv")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build();
        startForeground(NOTIF_ID, n);
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
                        setState(srv, ServerInstance.State.RESTARTING);
                        mainHandler.postDelayed(() -> startServer(srv), 30000);
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
                stopServer(srv, false);
            }

            File dir = new File(srv.getServerDir());
            boolean usesPlugins = (srv.getType() == ServerInstance.Type.PAPER || srv.getType() == ServerInstance.Type.PURPUR || srv.getType() == ServerInstance.Type.FOLIA || srv.getType() == ServerInstance.Type.VELOCITY);

            if (usesPlugins) {
                File pDir = new File(dir, "plugins");
                pDir.mkdirs();

                if (srv.isBedrockSupport()) {
                    downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/geyser.jar", new File(pDir, "Geyser.jar"));
                    downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/floodgate.jar", new File(pDir, "Floodgate.jar"));
                } else {
                    new File(pDir, "Geyser.jar").delete();
                    new File(pDir, "Geyser-Spigot.jar").delete();
                    new File(pDir, "Floodgate.jar").delete();
                    new File(pDir, "floodgate-bukkit.jar").delete();
                    deleteRecursive(new File(pDir, "Geyser-Spigot"), true);
                    deleteRecursive(new File(pDir, "floodgate"), true);
                }
                if (srv.isVoicechat()) {
                    downloadPluginSync(id, "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/voicechat.jar", new File(pDir, "Voicechat.jar"));
                } else {
                    new File(pDir, "Voicechat.jar").delete();
                    new File(pDir, "voicechat-bukkit.jar").delete();
                    deleteRecursive(new File(pDir, "voicechat"), true);
                }

                writeDynamicPluginConfigs(srv, dir);
            }
            
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

        if (srv.isDatabase()) {
            startDatabaseFlow(srv);
            return;
        }

        // PumpkinMC: native Rust binary, no Java jar involved
        if (srv.getType() == ServerInstance.Type.PUMPKIN) {
            startPumpkin(srv);
            return;
        }

        srv.startTime = System.currentTimeMillis();
        setState(srv, srv.isAutoSetup() ? ServerInstance.State.SETTING_UP : ServerInstance.State.STARTING);
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
        File jar = findServerJar(srv, dir);
        if (jar == null) {
            log(id, "  ✗ No server .jar found in " + dir.getAbsolutePath() + " or versions/ folder.");
            setState(srv, ServerInstance.State.CRASHED);
            return;
        }
        continueStartWithJar(srv, writePluginConfigs, jar, dir);
    }

    private void setExecutableRecursive(File dir) {
        if (dir == null || !dir.exists()) return;
        dir.setExecutable(true, false);
        dir.setReadable(true, false);
        dir.setWritable(true, false);
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    setExecutableRecursive(child);
                }
            }
        }
    }

    private void linkNativeBinaries(File binDir) {
        if (!binDir.exists()) binDir.mkdirs();
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        File[] libs = nativeDir.listFiles();
        if (libs != null) {
            for (File lib : libs) {
                String name = lib.getName();
                if (name.startsWith("lib") && name.endsWith(".so")) {
                    String originalName = name.substring(3, name.length() - 3);
                    File symlink = new File(binDir, originalName);
                    if (symlink.exists() || java.nio.file.Files.isSymbolicLink(symlink.toPath())) {
                        symlink.delete();
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            android.system.Os.symlink(lib.getAbsolutePath(), symlink.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    // Specific mapping for mysql to mariadb as we deduplicated them
                    if (originalName.startsWith("mariadb")) {
                        String mysqlName = originalName.replace("mariadb", "mysql");
                        File mysqlSymlink = new File(binDir, mysqlName);
                        if (mysqlSymlink.exists() || java.nio.file.Files.isSymbolicLink(mysqlSymlink.toPath())) {
                            mysqlSymlink.delete();
                        }
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                android.system.Os.symlink(lib.getAbsolutePath(), mysqlSymlink.getAbsolutePath());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void startDatabaseFlow(ServerInstance srv) {
        String id = srv.getId();
        File dir = new File(srv.getServerDir());
        if (!dir.exists()) dir.mkdirs();

        log(id, "  🗄️ Launching Database " + srv.getName() + "...");
        
        exec.submit(() -> {
            try {
                boolean isRedis = srv.getType() == ServerInstance.Type.REDIS;
                String dbEngine = isRedis ? "redis" : "mariadb";
                File engineDir = new File(getFilesDir(), dbEngine);
                File usrDir = new File(engineDir, "usr");
                
                if (!new File(usrDir, "bin").exists()) {
                    log(id, "  ✗ Database engine missing. Did it fail to unpack?");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }
                
                // Ensure everything in usrDir is executable just in case Tar extraction lost permissions
                setExecutableRecursive(usrDir);
                
                // Link native libraries to bin directory to bypass targetSdk 35 W^X restrictions
                linkNativeBinaries(new File(usrDir, "bin"));
                linkNativeBinaries(new File(usrDir, "libexec"));
                
                // Symlink all native libraries into usr/lib to satisfy Termux DT_RUNPATH linker requirements
                File usrLibDir = new File(usrDir, "lib");
                if (!usrLibDir.exists()) usrLibDir.mkdirs();
                File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
                File[] nativeLibs = nativeDir.listFiles();
                if (nativeLibs != null) {
                    for (File lib : nativeLibs) {
                        if (lib.getName().endsWith(".so")) {
                            File targetLib = new File(usrLibDir, lib.getName());
                            File targetLib1 = new File(usrLibDir, lib.getName() + ".1");
                            File targetLib2 = new File(usrLibDir, lib.getName() + ".2");
                            File targetLib3 = new File(usrLibDir, lib.getName() + ".3");
                            
                            File[] targets = {targetLib, targetLib1, targetLib2, targetLib3};
                            for (File t : targets) {
                                if (t.exists() || java.nio.file.Files.isSymbolicLink(t.toPath())) {
                                    t.delete();
                                }
                                try {
                                    java.nio.file.Files.createSymbolicLink(t.toPath(), lib.toPath());
                                } catch (Exception ignored) {
                                    try { java.nio.file.Files.copy(lib.toPath(), t.toPath()); } catch (Exception ignored2) {}
                                }
                            }
                        }
                    }
                }
                
                String ldPath = usrDir.getAbsolutePath() + "/lib:" + getApplicationInfo().nativeLibraryDir + ":/system/lib64:/system/lib:/vendor/lib64";
                String inFifo = new File(dir, "in.fifo").getAbsolutePath();
                File startSh = new File(dir, "start_native.sh");
                String dp = dir.getAbsolutePath();
                
                String script = "#!/system/bin/sh\n" +
                    "exec > server.log 2>&1\n" +
                    "export LD_LIBRARY_PATH=\"" + ldPath + "\"\n" +
                    "export PATH=\"" + usrDir.getAbsolutePath() + "/bin:$PATH\"\n" +
                    "export HOME=\"" + dp + "\"\n" +
                    "cd \"" + dp + "\"\n" +
                    "rm -f " + inFifo + "\n" +
                    "mkfifo " + inFifo + "\n";
                
                if (isRedis) {
                    File confFile = new File(dir, "redis.conf");
                    if (!confFile.exists()) {
                        log(id, "  ℹ Generating redis.conf...");
                        java.io.FileWriter fw = new java.io.FileWriter(confFile);
                        fw.write("port " + srv.getPort() + "\n");
                        fw.write("requirepass " + srv.getDbPassword() + "\n");
                        fw.write("dir " + dp + "\n");
                        fw.write("daemonize no\n");
                        fw.close();
                    }
                    script += "echo \"Starting Redis on port " + srv.getPort() + "...\"\n";
                    script += "exec redis-server redis.conf < " + inFifo + "\n";
                } else {
                    File dataDir = new File(dir, "data");
                    if (!dataDir.exists()) {
                        log(id, "  ℹ Patching Termux paths in MariaDB scripts...");
                        log(id, "  ℹ Patching Termux paths in MariaDB scripts safely...");
                        script += "sed -e 's|\\$dirname0//data/data/com.termux/files/usr|\\$basedir|g' " +
                                       "-e 's|\\$basedir//data/data/com.termux/files/usr|\\$basedir|g' " +
                                       "-e 's|/data/data/com.termux/files/usr|" + usrDir.getAbsolutePath() + "|g' " +
                                       "\"" + usrDir.getAbsolutePath() + "/bin/mariadb-install-db\" > \"" + dir.getAbsolutePath() + "/mariadb-install-db.sh\"\n";
                        script += "chmod +x \"" + dir.getAbsolutePath() + "/mariadb-install-db.sh\"\n";

                        log(id, "  ? Initializing MariaDB data directory...");
                        script += "chmod -R +x \"" + usrDir.getAbsolutePath() + "/bin\"\n";
                        if (new File(usrDir, "libexec").exists()) {
                            script += "chmod -R +x \"" + usrDir.getAbsolutePath() + "/libexec\"\n";
                        }
                        script += "sh \"" + dir.getAbsolutePath() + "/mariadb-install-db.sh\" --datadir=\"" + dataDir.getAbsolutePath() + "\" --basedir=\"" + usrDir.getAbsolutePath() + "\" --auth-root-authentication-method=normal\n";
                        script += "echo \"CREATE USER IF NOT EXISTS '" + srv.getDbUsername() + "'@'%' IDENTIFIED BY '" + srv.getDbPassword() + "';\" > init.sql\n";
                        script += "echo \"GRANT ALL PRIVILEGES ON *.* TO '" + srv.getDbUsername() + "'@'%' WITH GRANT OPTION;\" >> init.sql\n";
                        script += "echo \"FLUSH PRIVILEGES;\" >> init.sql\n";
                    }

                    log(id, "  ℹ Fixing execution permissions...");
                    makeExecutable(new File(usrDir, "bin"));
                    if (new File(usrDir, "libexec").exists()) {
                        makeExecutable(new File(usrDir, "libexec"));
                    }

                    script += "echo \"Starting MariaDB on port " + srv.getPort() + "...\"\n";
                    script += "chmod -R +x \"" + usrDir.getAbsolutePath() + "/bin\"\n";
                    if (new File(usrDir, "libexec").exists()) {
                        script += "chmod -R +x \"" + usrDir.getAbsolutePath() + "/libexec\"\n";
                    }
                    script += "exec mariadbd --datadir=\"" + dataDir.getAbsolutePath() + "\" " +
                              "--basedir=\"" + usrDir.getAbsolutePath() + "\" " +
                              "--lc-messages-dir=\"" + usrDir.getAbsolutePath() + "/share/mariadb\" " +
                              "--plugin-dir=\"" + getApplicationInfo().nativeLibraryDir + "\" " +
                              "--tmpdir=\"" + dp + "\" " +
                              "--socket=\"" + dp + "/mysqld.sock\" " +
                              "--pid-file=\"" + dp + "/mariadbd.pid\" " +
                              "--port=" + srv.getPort() + " " +
                              "--bind-address=0.0.0.0 " +
                              "--console " +
                              (dataDir.exists() ? "" : "--init-file=\"" + dp + "/init.sql\" ") +
                              "< " + inFifo + "\n";
                }
                
                java.io.FileWriter fw = new java.io.FileWriter(startSh);
                fw.write(script);
                fw.close();
                startSh.setExecutable(true, false);

                ProcessBuilder pb = new ProcessBuilder("sh", startSh.getAbsolutePath());
                pb.directory(dir);
                pb.environment().put("LD_LIBRARY_PATH", ldPath);
                
                RT rt = new RT();
                rt.process = pb.start();
                rt.dummyWriter = new java.io.FileOutputStream(inFifo);
                
                runtimes.put(id, rt);
                setState(srv, ServerInstance.State.ONLINE);
                
                startLogMonitor(id, srv, new File(dir, "server.log"));
                startPeriodicTasks(id, srv);
                
                int exitCode = rt.process.waitFor();
                log(id, "  ℹ Database exited with code " + exitCode);
            } catch (Exception e) {
                log(id, "  ✗ Database Native Flow crashed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                RT r = runtimes.remove(id);
                if (r != null) {
                    try { if (r.dummyWriter != null) r.dummyWriter.close(); } catch(Exception ignored){}
                    try { if (r.process != null) r.process.destroy(); } catch(Exception ignored){}
                }
                setState(srv, ServerInstance.State.OFFLINE);
            }
        });
    }

    private File findServerJar(ServerInstance srv, File dir) {
        if (dir == null || !dir.exists()) return null;
        // First check root dir
        File[] rootJars = dir.listFiles((d, name) -> name.endsWith(".jar") && !name.toLowerCase().contains("paperclip"));
        if (rootJars != null && rootJars.length > 0) {
            if (srv.getType() == ServerInstance.Type.FABRIC) {
                for (File j : rootJars) if (j.getName().toLowerCase().contains("fabric")) return j;
            }
            if (srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE) {
                for (File j : rootJars) if (j.getName().toLowerCase().contains("forge") && !j.getName().toLowerCase().contains("installer")) return j;
            }
            return rootJars[0];
        }
        
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
                    srv.setBedrockPort(allocatePortSync(srv, "bedrock", 40000, 50000));
                    ServerRepo.get(this).update(srv);
                }
                if (srv.isVoicechat() && srv.getVoicechatPort() == 0) {
                    srv.setVoicechatPort(allocatePortSync(srv, "voicechat", 55000, 65000));
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
        if (logFile.exists()) logFile.delete();
        
        boolean useBetaJni = eu.kodanetwork.mchost.App.getPrefs(this).getBoolean("beta_jni_embedded", true);
        if (useBetaJni) {
            startEmbeddedJvmFlow(srv, jar, logFile);
        } else if (srv.isUseNative()) {
            startNativeFlow(srv, jar, logFile);
        } else {
            startTermuxFlow(srv, jar, logFile);
        }
    }

    private void startEmbeddedJvmFlow(ServerInstance srv, File jar, File logFile) {
        new Thread(() -> {
            String id = srv.getId();
            log(id, "  ℹ INITIATING EMBEDDED JNI DEPLOYMENT...");
            
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
                // Per-server Java runtime: explicit setting wins, otherwise auto-resolved
                int javaVersion = srv.getJavaRuntime() != 0
                        ? srv.getJavaRuntime()
                        : eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(srv);
                
                if (javaVersion != 25) {
                    // Non-default Java version: ensure it's installed
                    final int dlVer = javaVersion;
                    if (!eu.kodanetwork.mchost.util.RuntimeManager.isRuntimeInstalled(this, javaVersion)) {
                        log(id, "  ⬇ Downloading Java " + javaVersion + " runtime...");
                        eu.kodanetwork.mchost.util.RuntimeManager.Result res = 
                            eu.kodanetwork.mchost.util.RuntimeManager.ensureRuntimeSync(this, javaVersion,
                                (pct, msg) -> log(id, "  ⬇ Java " + dlVer + ": " + pct + "% (" + msg + ")"));
                        if (!res.success) {
                            log(id, "  ⚠ Java " + javaVersion + " download failed: " + res.failReason + " — Fallback to Java 25");
                            javaVersion = 25;
                        }
                    }
                }
                
                jvmDir = new File(getFilesDir(), "jre" + javaVersion);
                if (!jvmDir.exists()) {
                    log(id, "  ✗ jre" + javaVersion + " not found. Please restart app to extract it.");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }
                log(id, "  ✓ Using JDK " + javaVersion + " for ARM64");
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

            File serverDir = logFile.getParentFile();
            File jvmArgsFile = new File(serverDir, "koda_jvm_args.txt");
            File progArgsFile = new File(serverDir, "koda_program_args.txt");
            jvmArgsFile.delete();
            progArgsFile.delete();

            boolean isForgeFamily = srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE;
            boolean needsInstall = isForgeFamily && !new File(serverDir, "libraries").exists();
            final boolean installing = needsInstall;

            String mainClassName = "org/bukkit/craftbukkit/Main";
            
            if (installing) {
                log(id, "  ℹ Running headless installer for " + srv.getType().name() + "...");
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(progArgsFile);
                    fos.write("--installServer\n".getBytes());
                    fos.close();
                    
                    java.io.FileOutputStream fosJvm = new java.io.FileOutputStream(jvmArgsFile, true);
                    fosJvm.write("-Dsun.net.client.defaultReadTimeout=120000\n".getBytes());
                    fosJvm.write("-Dsun.net.client.defaultConnectTimeout=30000\n".getBytes());
                    fosJvm.close();
                } catch (Exception e) {}
            }
            
            try {
                java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar);
                java.util.jar.Manifest manifest = jarFile.getManifest();
                if (manifest != null) {
                    String mc = manifest.getMainAttributes().getValue("Main-Class");
                    if (mc != null && !mc.isEmpty()) {
                        mc = mc.trim();
                        mainClassName = mc.replace(".", "/");
                        log(id, "  ℹ Found Main-Class: " + mc);
                    }
                }
                jarFile.close();
            } catch (Exception e) {
                log(id, "  ⚠ Could not read jar manifest: " + e.getMessage());
            }

            if (isForgeFamily && !installing) {
                // Find unix_args.txt
                File libs = new File(serverDir, "libraries");
                File[] search = null;
                if (srv.getType() == ServerInstance.Type.FORGE) {
                    File forgeDir = new File(libs, "net/minecraftforge/forge");
                    if (forgeDir.exists()) search = forgeDir.listFiles();
                } else if (srv.getType() == ServerInstance.Type.NEOFORGE) {
                    File neoforgeDir = new File(libs, "net/neoforged/neoforge");
                    if (neoforgeDir.exists()) search = neoforgeDir.listFiles();
                }
                
                File unixArgs = null;
                if (search != null) {
                    for (File f : search) {
                        if (f.isDirectory()) {
                            File ua = new File(f, "unix_args.txt");
                            if (ua.exists()) unixArgs = ua;
                        }
                    }
                }
                
                if (unixArgs != null && unixArgs.exists()) {
                    log(id, "  ℹ Parsing " + unixArgs.getName() + " for JNI args...");
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(unixArgs));
                        java.io.FileWriter jvmOut = new java.io.FileWriter(jvmArgsFile);
                        java.io.FileWriter progOut = new java.io.FileWriter(progArgsFile);
                        
                        String line;
                        boolean foundMain = false;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            
                            if (!foundMain) {
                                if (line.equals("-p")) {
                                    jvmOut.write("--module-path=" + br.readLine().trim() + "\n");
                                } else if (line.equals("--add-modules") || line.equals("--add-opens") || line.equals("--add-exports")) {
                                    jvmOut.write(line + "=" + br.readLine().trim() + "\n");
                                } else if (line.equals("-cp") || line.equals("-classpath")) {
                                    jvmOut.write("-Djava.class.path=" + br.readLine().trim() + "\n");
                                } else if (!line.startsWith("-")) {
                                    mainClassName = line.replace(".", "/");
                                    foundMain = true;
                                    log(id, "  ℹ Bootstrapper: " + mainClassName);
                                } else {
                                    // other jvm arg
                                    jvmOut.write(line + "\n");
                                }
                            } else {
                                // Program arg
                                progOut.write(line + "\n");
                            }
                        }
                        
                        progOut.write("--nogui\n"); // Always append nogui
                        
                        br.close();
                        jvmOut.close();
                        progOut.close();
                    } catch (Exception e) {
                        log(id, "  ✗ Failed to parse unix_args: " + e.getMessage());
                    }
                } else {
                    log(id, "  ⚠ Could not find unix_args.txt! Launch may fail.");
                }
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
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
                rt.fifoPath = inFifo.getAbsolutePath();
            } catch (Exception e) {
                Log.e(TAG, "mkfifo failed", e);
            }

            final android.content.ServiceConnection[] jvmConnRef = new android.content.ServiceConnection[1];
            try {
                java.util.concurrent.CountDownLatch bindLatch = new java.util.concurrent.CountDownLatch(1);
                final eu.kodanetwork.mchost.IJvmService[] jvmSvc = new eu.kodanetwork.mchost.IJvmService[1];
                android.content.ServiceConnection jvmConn = new android.content.ServiceConnection() {
                    @Override
                    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                        log(id, "  ℹ ServiceConnection: onServiceConnected");
                        jvmSvc[0] = eu.kodanetwork.mchost.IJvmService.Stub.asInterface(service);
                        bindLatch.countDown();
                    }
                    @Override
                    public void onServiceDisconnected(android.content.ComponentName name) {
                        log(id, "  ✗ ServiceConnection: onServiceDisconnected");
                        jvmSvc[0] = null;
                        bindLatch.countDown();
                    }
                    @Override
                    public void onBindingDied(android.content.ComponentName name) {
                        log(id, "  ✗ ServiceConnection: onBindingDied");
                        bindLatch.countDown();
                    }
                    @Override
                    public void onNullBinding(android.content.ComponentName name) {
                        log(id, "  ✗ ServiceConnection: onNullBinding");
                        bindLatch.countDown();
                    }
                };
                jvmConnRef[0] = jvmConn;
                
                Class<?>[] availableServices = new Class<?>[]{
                    IsolatedJvmService1.class, IsolatedJvmService2.class, IsolatedJvmService3.class
                };
                java.util.Set<String> usedServiceNames = new java.util.HashSet<>();
                for (RT r : runtimes.values()) {
                    if (r.jvmClassName != null) usedServiceNames.add(r.jvmClassName);
                }
                Class<?> targetService = null;
                for (Class<?> svc : availableServices) {
                    if (!usedServiceNames.contains(svc.getName())) {
                        targetService = svc;
                        break;
                    }
                }
                if (targetService == null) {
                    log(id, "  ✗ No free JVM processes available! Maximum 3 concurrent embedded servers.");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }
                rt.jvmClassName = targetService.getName();
                
                android.content.Intent jvmIntent = new android.content.Intent(KodaServerService.this, targetService);
                jvmIntent.setAction(java.util.UUID.randomUUID().toString());
                
                // Call bindService on the main thread to ensure ServiceConnection callbacks are handled correctly
                final boolean[] boundResult = new boolean[1];
                java.util.concurrent.CountDownLatch bindCallLatch = new java.util.concurrent.CountDownLatch(1);
                
                mainHandler.post(() -> {
                    try {
                        boundResult[0] = bindService(jvmIntent, jvmConn, android.content.Context.BIND_AUTO_CREATE | android.content.Context.BIND_IMPORTANT);
                    } catch (Exception e) {
                        Log.e(TAG, "Error binding service: " + e.getMessage());
                        boundResult[0] = false;
                    }
                    bindCallLatch.countDown();
                });
                
                bindCallLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                boolean bound = boundResult[0];
                
                if (!bound) {
                    log(id, "  ✗ bindService() returned false! Service could not be found or started.");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }

                    if (bindLatch.await(30, java.util.concurrent.TimeUnit.SECONDS) && jvmSvc[0] != null) {
                    rt.jvmService = jvmSvc[0];
                    log(id, "  ℹ Verbunden mit isoliertem JVM-Prozess.");
                    

                    
                    int result = jvmSvc[0].startJvm(libJvm.getAbsolutePath(), jar.getAbsolutePath(), srv.getRamMB(), mainClassName, logFile.getParent());
                    log(id, "  ℹ JNI JVM Engine exited with code: " + result);
                    
                    try { if (rt.dummyWriter != null) rt.dummyWriter.close(); } catch (Exception ignored) {}
                    if (rt.frpcProc != null) {
                        try { rt.frpcProc.destroyForcibly(); } catch (Exception ignored) {}
                    }
                    inFifo.delete();
                    
                    // MUST kill the isolated process because JNI_CreateJavaVM cannot be used twice.
                    try { jvmSvc[0].killJvm(); } catch (Exception ignored) {}
                    
                    runtimes.remove(id);
                    updateNotif();
                    try { unbindService(jvmConn); } catch (Exception ignored) {}
                    
                    if (installing && result == 0) {
                        log(id, "  ✓ Installation complete. Rebooting into server...");
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            startEmbeddedJvmFlow(srv, jar, logFile);
                        }, 2000);
                        return;
                    }
                    
                    if (result == -99 || result == -98 || (jvmSvc[0] == null)) {
                        String errMsg = "Isolated JVM process crashed unexpectedly";
                        try {
                            if (jvmSvc[0] != null) errMsg = jvmSvc[0].getInitError();
                            log(id, "  ✗ NATIVE ERROR: " + errMsg);
                        } catch (Exception e) {
                            log(id, "  ✗ NATIVE ERROR: Code " + result);
                        }
                        srv.crashExitCode = result;
                        srv.crashCategory = "NATIVE_LIB";
                        
                        if (errMsg.contains("libjvm.so") || errMsg.contains("crashed")) {
                            srv.crashReason = "Missing Java Library (libjvm.so)";
                            srv.crashFixAction = "REDOWNLOAD_JRE";
                            srv.crashFix = "The selected Java version is incomplete or unsupported. Try re-downloading it or use another Java version.";
                        } else {
                            srv.crashReason = "Native library error (code " + result + ")";
                            srv.crashFixAction = "REDOWNLOAD_JRE";
                            srv.crashFix = "Re-download Java Runtime";
                        }
                        
                        srv.crashStackTrace = errMsg;
                        setState(srv, ServerInstance.State.CRASHED);
                    } else if (result != 0) {
                        log(id, "  ✗ NATIVE ERROR: Code " + result);
                        srv.crashExitCode = result;
                        eu.kodanetwork.mchost.util.CrashAnalyzer.Result cr = eu.kodanetwork.mchost.util.CrashAnalyzer.analyze(srv, result);
                        if (cr != null) {
                            srv.crashCategory = cr.category;
                            srv.crashReason = cr.reason;
                            srv.crashFix = cr.fixDescription;
                            srv.crashFixAction = cr.fixAction;
                            srv.crashStackTrace = cr.stackTrace;
                        }
                        setState(srv, ServerInstance.State.CRASHED);
                    } else {
                        setState(srv, ServerInstance.State.OFFLINE);
                    }
                } else {
                    log(id, "  ✗ Konnte nicht mit dem isolierten JVM-Prozess verbinden.");
                    setState(srv, ServerInstance.State.CRASHED);
                    runtimes.remove(id);
                    updateNotif();
                }
            } catch (Exception e) {
                // If server was being stopped or is already offline, this is NOT a crash.
                // stopServer() kills the JVM process which causes the blocking startJvm()
                // call to throw DeadObjectException — that's expected behavior.
                if (srv.state == ServerInstance.State.STOPPING || srv.state == ServerInstance.State.OFFLINE) {
                    log(id, "  ℹ JVM Prozess normal beendet (Server wurde gestoppt).");
                    // Don't change state — stopServer() already set it to OFFLINE
                } else {
                    log(id, "  ℹ JVM Prozess beendet/abgestürzt: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    eu.kodanetwork.mchost.util.CrashAnalyzer.Result cr = eu.kodanetwork.mchost.util.CrashAnalyzer.analyze(srv, -1);
                    if (cr != null && cr.isCrash) {
                        srv.crashExitCode = -1;
                        srv.crashCategory = cr.category;
                        srv.crashReason = cr.reason;
                        srv.crashFix = cr.fixDescription;
                        srv.crashFixAction = cr.fixAction;
                        srv.crashStackTrace = cr.stackTrace;
                        setState(srv, ServerInstance.State.CRASHED);
                    } else if (e instanceof android.os.DeadObjectException) {
                        // DeadObjectException without a detected crash pattern = normal stop
                        if (installing) {
                            log(id, "  ✓ Installation complete. Rebooting into server...");
                            if (rt.frpcProc != null) {
                                try { rt.frpcProc.destroyForcibly(); } catch (Exception ignored) {}
                            }
                            runtimes.remove(id);
                            updateNotif();
                            if (jvmConnRef[0] != null) {
                                try { unbindService(jvmConnRef[0]); } catch (Exception ignored) {}
                            }
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                startEmbeddedJvmFlow(srv, jar, logFile);
                            }, 2000);
                            return;
                        } else {
                            setState(srv, ServerInstance.State.OFFLINE);
                        }
                    } else {
                        setState(srv, ServerInstance.State.CRASHED);
                    }
                }
                if (rt.frpcProc != null) {
                    try { rt.frpcProc.destroyForcibly(); } catch (Exception ignored) {}
                }
                runtimes.remove(id);
                updateNotif();
                if (jvmConnRef[0] != null) {
                    try { unbindService(jvmConnRef[0]); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void startNativeFlow(ServerInstance srv, File jar, File logFile) {
        new Thread(() -> {
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

            // Per-server Java runtime: explicit setting wins, otherwise auto-resolved
            // (Fabric -> 21 because many mods break on newer Java; everything else -> 25)
            int javaVersion = srv.getJavaRuntime() != 0
                    ? srv.getJavaRuntime()
                    : eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(srv);
            String javaBinPath = null;
            if (javaVersion != 25) {
                if (!eu.kodanetwork.mchost.util.RuntimeManager.isRuntimeInstalled(this, javaVersion)) {
                    log(srv.getId(), "  ⬇ Lade Java-" + javaVersion + "-Runtime (~30 MB)...");
                    eu.kodanetwork.mchost.util.RuntimeManager.Result res = eu.kodanetwork.mchost.util.RuntimeManager.ensureRuntimeSync(this, javaVersion,
                            (pct, msg) -> log(srv.getId(), "  ⬇ Java " + javaVersion + ": " + pct + "% (" + msg + ")"));
                    if (res.success) {
                        javaBinPath = eu.kodanetwork.mchost.util.RuntimeManager.getJavaBin(this, javaVersion);
                    } else {
                        log(srv.getId(), "  ✗ Java Download Error: " + res.failReason);
                    }
                } else {
                    javaBinPath = eu.kodanetwork.mchost.util.RuntimeManager.getJavaBin(this, javaVersion);
                }
            }
            if (javaBinPath != null) {
                log(srv.getId(), "  ℹ Java-" + javaVersion + "-Runtime aktiv: " + javaBinPath);
            } else {
                if (javaVersion != 25) {
                    log(srv.getId(), "  ⚠ Java " + javaVersion + " nicht verfügbar — Fallback auf Java 25");
                }
                javaBinPath = eu.kodanetwork.mchost.util.JavaFinder.find(this, 25);
            }
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
                        java.io.InputStream is = getAssets().open("libc++_shared.so");
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
                    "export PATH=\"$JAVA_HOME/bin:$PATH\"\n" +
                    "cd \"" + dp + "\"\n" +
                    "mkdir -p tmp\n" +
                    "rm -f " + inFifo + "\n" +
                    "mkfifo " + inFifo + "\n";
                    
                String aikarNative = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true";
                String baseCmd = javaBin.getAbsolutePath() + 
                    " -Djava.awt.headless=true -Djava.io.tmpdir=\"" + dp + "/tmp\" -DPaper.IgnoreJavaVersion=true -Xmx" + srv.getRamMB() + "M -Xms" + srv.getRamMB() + "M " + aikarNative + " " +
                    "-Dorg.jline.terminal.dumb.color=true -Dpaper.console.color=true ";
                    
                if (srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE) {
                    script += "if [ ! -f \"run.sh\" ] && ! ls *forge-*.jar 1> /dev/null 2>&1; then\n" +
                              "  echo \"Running Installer...\"\n" +
                              "  " + javaBin.getAbsolutePath() + " -Djava.awt.headless=true -jar \"" + jar.getAbsolutePath() + "\" --installServer\n" +
                              "fi\n" +
                              "if [ -f \"run.sh\" ]; then\n" +
                              "  echo \"-Djava.awt.headless=true -Djava.io.tmpdir=" + dp + "/tmp -Dorg.jline.terminal.dumb.color=true -Xmx" + srv.getRamMB() + "M -Xms" + srv.getRamMB() + "M " + aikarNative + "\" > user_jvm_args.txt\n" +
                              "  tail -f " + inFifo + " | sh -c 'echo $$ > server.pid; exec sh run.sh nogui' &\n" +
                              "  wait\n" +
                              "else\n" +
                              "  export REAL_JAR=$(ls *forge-*.jar 2>/dev/null | grep -v installer | head -n 1)\n" +
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
                startPeriodicTasks(id, srv);
                
                startLogMonitor(id, srv, logFile); 
                
                int exitCode = p.waitFor();
                log(id, "  ℹ NATIVE PROCESS TERMINATED. Exit code: " + exitCode);
                if (exitCode != 0) {
                    log(id, "  ⚠ Non-zero exit code may indicate crash or missing libraries");
                }
                if (rt.frpcProc != null) {
                    try { rt.frpcProc.destroyForcibly(); } catch (Exception ignored) {}
                }
                runtimes.remove(id);
                updateNotif();
                if (srv.state != ServerInstance.State.STOPPING && srv.state != ServerInstance.State.OFFLINE) {
                    if (exitCode != 0) {
                        srv.crashExitCode = exitCode;
                        eu.kodanetwork.mchost.util.CrashAnalyzer.Result cr = eu.kodanetwork.mchost.util.CrashAnalyzer.analyze(srv, exitCode);
                        if (cr != null) {
                            srv.crashCategory = cr.category;
                            srv.crashReason = cr.reason;
                            srv.crashFix = cr.fixDescription;
                            srv.crashFixAction = cr.fixAction;
                            srv.crashStackTrace = cr.stackTrace;
                        }
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

    private void startNativeTunnel(String id, ServerInstance srv, File dir, RT rt) {
        String frpcPath = new File(getApplicationInfo().nativeLibraryDir, "libfrpc.so").getAbsolutePath();
        File frpcBin = new File(frpcPath);
        new File(dir, ".frpc.toml").setReadable(true, false);
        if (frpcBin.exists()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(frpcPath, "-c", new File(dir, ".frpc.toml").getAbsolutePath());
                pb.directory(dir);
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File(dir, "bore.log"));
                rt.frpcProc = pb.start();
                
                // Root Protection: Delete the config file from disk immediately after it is loaded into memory
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    File frpcConfig = new File(dir, ".frpc.toml");
                    if (frpcConfig.exists()) {
                        frpcConfig.delete();
                        Log.d(TAG, "Ghost-File: deleted .frpc.toml for " + srv.getId());
                    }
                }, 1000);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start native tunnel", e);
            }
        } else {
            log(id, "  ✗ FRPC binary missing at " + frpcPath + " - Did you rebuild the app after adding jniLibs?");
        }
    }

    private void startTermuxFlow(ServerInstance srv, File jar, File logFile) {
        String id = srv.getId();
        File dir = new File(srv.getServerDir());
        String inFifo = "/data/data/com.termux/files/home/in_" + id.substring(0, 8) + ".fifo";
        
        File nativeFrpc = new File(getApplicationInfo().nativeLibraryDir, "libfrpc.so");
        File termuxFrpc = new File(dir, ".frpc");
        if (nativeFrpc.exists()) {
            try {
                java.nio.file.Files.copy(nativeFrpc.toPath(), termuxFrpc.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                termuxFrpc.setExecutable(true, false);
            } catch (IOException ignored) {}
        }
        
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
            "if [ -f frpc.pid ]; then kill -9 $(cat frpc.pid) || true; rm -f frpc.pid; fi\n" +
            "pkill -f .frpc || true\n" +
            "nohup ./.frpc -c .frpc.toml > bore.log 2>&1 & echo $! > frpc.pid\n" +
            "(sleep 1 && rm -f .frpc.toml) &\n" +
            "rm -f world/session.lock\n" +
            "rm -f " + inFifo + "\n" +
            "mkfifo " + inFifo + "\n";
            
        String aikar = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true";
        String extraArgs = "";
        if (srv.getType() == ServerInstance.Type.FABRIC) {
            extraArgs = "-Djna.nosys=true -Djna.nounpack=true -Dterminal.jline=false -Dterminal.ansi=true ";
        }
        script += "tail -f " + inFifo + " | java " + extraArgs + "-DPaper.IgnoreJavaVersion=true -Dkoda.dir=\"" + dp + "\" -Xmx" + srv.getRamMB() + "M -Xms" + srv.getRamMB() + "M " + aikar + " -jar \"" + jar.getAbsolutePath() + "\" nogui > server.log 2>&1 &\n";
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
        
        String killCmd = "if [ -f '" + srv.getServerDir() + "/server.pid' ]; then kill -9 `cat '" + srv.getServerDir() + "/server.pid'` || true; fi; if [ -f '" + srv.getServerDir() + "/frpc.pid' ]; then kill -9 `cat '" + srv.getServerDir() + "/frpc.pid'` || true; fi; pkill -f .frpc || true; fuser -k -9 " + srv.getPort() + "/tcp || true";
        RT rt = runtimes.get(id);
        
        if (rt != null && rt.frpcProc != null) {
            try { rt.frpcProc.destroyForcibly(); } catch (Exception ignored) {}
        }

        if (srv.isUseNative()) { 
            if (rt != null && rt.proc != null) rt.proc.destroyForcibly();
            try { Runtime.getRuntime().exec(new String[]{"sh", "-c", killCmd}); } catch (Exception ignored) {}
            runtimes.remove(id);
        } else if (rt != null && rt.fifoPath != null && rt.fifoPath.contains("com.termux")) {
            eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, killCmd, true);
            runtimes.remove(id);
        } else {
            if (force && rt != null && rt.jvmService != null) {
                try {
                    log(id, "  ℹ Force killing isolated JVM process...");
                    rt.jvmService.killJvm();
                } catch (Exception e) {}
                runtimes.remove(id);
            } else if (runtimes.containsKey(id)) {
                log(id, "  ⚠ Waiting for Embedded JVM to exit gracefully timed out. Force killing!");
                if (rt != null && rt.jvmService != null) {
                    try { rt.jvmService.killJvm(); } catch (Exception e) {}
                }
                runtimes.remove(id);
            }
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
                if (srv.state != ServerInstance.State.ONLINE && srv.state != ServerInstance.State.STOPPING && srv.state != ServerInstance.State.SETTING_UP) {
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
            boolean success = false;
            for (int i = 0; i < 60; i++) {
                sleep(1000);
                String c = readFullFile(bLog);
                if (c.contains("listening at") || c.contains("start proxy success")) {
                    success = true;
                    try {
                        int port = srv.getPort();
                        if (c.contains("bore.pub")) {
                            Pattern p = Pattern.compile("bore\\.pub:(\\d+)");
                            Matcher m = p.matcher(c);
                            if (m.find()) port = Integer.parseInt(m.group(1));
                            new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), "bore.pub", port, "tcp");
                            if (srv.isBedrockSupport() && srv.getBedrockPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), "bore.pub", srv.getBedrockPort(), "udp");
                            }
                            if (srv.isVoicechat() && srv.getVoicechatPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), "bore.pub", srv.getVoicechatPort(), "udp", "_voicechat");
                            }
                            if (srv.isKodadashSupport() && srv.getKodadashPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), "bore.pub", srv.getKodadashPort(), "tcp", "_dash");
                            }
                        } else {
                            new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), BORE_HOST, port, "tcp");
                            if (srv.isBedrockSupport() && srv.getBedrockPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), BORE_HOST, srv.getBedrockPort(), "udp");
                            }
                            if (srv.isVoicechat() && srv.getVoicechatPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), BORE_HOST, srv.getVoicechatPort(), "udp", "_voicechat");
                            }
                            if (srv.isKodadashSupport() && srv.getKodadashPort() > 0) {
                                new SupabaseFunctionsClient(this).createDnsLink("", srv.getSubdomain(), srv.getBaseDomain(), BORE_HOST, srv.getKodadashPort(), "tcp", "_dash");
                            }
                        }
                        log(id, "  ✓ DNS Join: " + srv.getJoinAddress());
                    } catch (Exception e) {
                        Log.e(TAG, "DNS update failed", e);
                    }
                    break;
                }
            }
            if (!success) {
                log(id, "  ✗ TUNNEL FAILED! bore.log says:");
                String c = readFullFile(bLog);
                for (String line : c.split("\n")) {
                    if (!line.trim().isEmpty()) log(id, "    " + line);
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
            boolean useBetaJni = eu.kodanetwork.mchost.App.getPrefs(this).getBoolean("beta_jni_embedded", true);
            int phase = setupPhase.getOrDefault(id, 0);
            if (phase == 0) {
                if (useBetaJni && (srv.getAiPrompt() == null || srv.getAiPrompt().trim().isEmpty() || srv.getAiPrompt().equals("null") || srv.getAiPrompt().equals("[]"))) {
                    log(id, "  🧩 PHASE 1: Initial Boot OK. (Restart skipped for Embedded JVM)");
                    try { writePaperOptimizationConfigs(srv, new File(srv.getServerDir())); } catch (Exception e) { Log.e(TAG, "Paper config error", e); }
                    
                    log(id, "  🧩 Applying custom design...");
                    try { writeTabConfig(srv, new File(srv.getServerDir())); } catch (IOException e) { Log.e(TAG, "Tab config error", e); }
                    
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
                        ServerRepo.get(KodaServerService.this).update(srv);
                        log(id, "✓ Server erfolgreich gestartet! Bereit für Spieler.");
                    });
                } else {
                    log(id, "  🧩 PHASE 1: Initial Boot OK. Stopping server to install plugins...");
                    setupPhase.put(id, 1);
                    exec.submit(() -> {
                        sleep(5000);
                        stopServer(srv, false);
                        while (runtimes.containsKey(id)) { sleep(1000); }
                        setState(srv, ServerInstance.State.SETTING_UP);
                        
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
                                        if (srv.getBedrockPort() == 0) srv.setBedrockPort(allocatePortSync(srv, "bedrock", 40000, 50000));
                                        eu.kodanetwork.mchost.model.ServerRepo.get(KodaServerService.this).update(srv);
                                        continue;
                                    }
                                    if (pidLower.contains("voicechat") || pidLower.contains("simple-voice-chat")) {
                                        log(id, "  🎙️ Intercepted: " + pid + " -> Enabling Native VoiceChat!");
                                        srv.setVoicechat(true);
                                        if (srv.getVoicechatPort() == 0) srv.setVoicechatPort(allocatePortSync(srv, "voicechat", 55000, 65000));
                                        eu.kodanetwork.mchost.model.ServerRepo.get(KodaServerService.this).update(srv);
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
                    setState(srv, ServerInstance.State.SETTING_UP);
                    
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
                    setState(srv, ServerInstance.State.ONLINE);
                });
            }
        } else {
            log(id, "✓ Server erfolgreich gestartet! Bereit für Spieler.");
        }
    }

    private final java.util.Map<String, List<String>> logQueue = new java.util.HashMap<>();
    private boolean logFlushPending = false;

    private void makeExecutable(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            file.setExecutable(true, false);
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    makeExecutable(child);
                }
            }
        } else {
            file.setExecutable(true, false);
        }
    }

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
    
    private boolean isAnyServerSettingUp() {
        for (Integer phase : setupPhase.values()) {
            if (phase == 1 || phase == 2) return true;
        }
        return false;
    }

    private void updateNotif() {
        int count = runtimes.size();
        boolean settingUp = isAnyServerSettingUp();
        
        if (count == 0 && !settingUp) {
            lastNotifCount = -1;
            releaseWakeLock();
            stopForeground(true);
            return;
        }
        
        // Use a special lastNotifCount value for "0 servers but setting up" to ensure it updates when entering setup phase
        int virtualCount = count == 0 && settingUp ? -2 : count;
        if (virtualCount == lastNotifCount) return;
        lastNotifCount = virtualCount;
        
        acquireWakeLock();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("KodaHosting")
            .setContentText((count == 0 && settingUp) ? "AI Setup läuft..." : (count + " Server aktiv"))
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

        boolean isModded = srv.getType() == ServerInstance.Type.FABRIC || srv.getType() == ServerInstance.Type.FORGE || srv.getType() == ServerInstance.Type.NEOFORGE;

        if (!isModded) {
            if (srv.isBedrockSupport()) {
                File geyserJar = new File(pDir, "Geyser.jar");
                File floodgateJar = new File(pDir, "Floodgate.jar");
                if (!geyserJar.exists() || !floodgateJar.exists()) {
                    log(srv.getId(), "  🛡️ Downloading Bedrock Support (Geyser & Floodgate)...");
                    downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/geyser.jar", geyserJar);
                    stripNativeLibsFromJar(geyserJar);
                    downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/floodgate.jar", floodgateJar);
                    stripNativeLibsFromJar(floodgateJar);
                    log(srv.getId(), "  🛡️ Download Complete!");
                }
            }

            if (srv.isVoicechat()) {
                File vcJar = new File(pDir, "Voicechat.jar");
                if (!vcJar.exists()) {
                    log(srv.getId(), "  🎤 Downloading Voicechat Support...");
                    downloadPluginSync(srv.getId(), "https://scsezpfrrmpyuapblbxk.supabase.co/storage/v1/object/public/plugins/voicechat.jar", vcJar);
                    stripNativeLibsFromJar(vcJar);
                    log(srv.getId(), "  🎤 Download Complete!");
                }
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

    private void stripNativeLibsFromJar(File jarFile) {
        if (!jarFile.exists()) return;
        File tempFile = new File(jarFile.getAbsolutePath() + ".tmp");
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(jarFile));
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(tempFile))) {
            
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".so") || entry.getName().endsWith(".dll") || entry.getName().endsWith(".dylib")) {
                    zis.closeEntry();
                    continue; // Skip native libraries
                }
                zos.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        } catch (Exception e) {
            android.util.Log.e("KodaServerService", "Failed to strip native libs from " + jarFile.getName(), e);
            tempFile.delete();
            return;
        }
        if (tempFile.exists()) {
            jarFile.delete();
            tempFile.renameTo(jarFile);
        }
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
        String domain = srv.getSubdomain() + "." + srv.getBaseDomain();
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
        String resolvedHost = BORE_HOST;
        try {
            resolvedHost = java.net.InetAddress.getByName(BORE_HOST).getHostAddress();
        } catch (Exception e) {}

        String randSuffix = Integer.toHexString((int)(Math.random() * 0xFFFFF));
        String toml = "serverAddr = \"" + resolvedHost + "\"\n" +
            "serverPort = 7000\n" +
            "auth.method = \"token\"\n" +
            "auth.token = \"" + eu.kodanetwork.mchost.security.PraetorSecurity.getFrpcToken() + "\"\n\n" +
            "[[proxies]]\n" +
            "name = \"mc-java-" + s.getId().substring(0, 4) + "-" + randSuffix + "\"\n" +
            "type = \"tcp\"\n" +
            "localIP = \"127.0.0.1\"\n" +
            "localPort = " + s.getPort() + "\n" +
            "remotePort = " + s.getPort() + "\n";

        if (s.isBedrockSupport() && s.getBedrockPort() > 0) {
            toml += "\n[[proxies]]\n" +
                "name = \"mc-bedrock-" + s.getId().substring(0, 4) + "-" + randSuffix + "\"\n" +
                "type = \"udp\"\n" +
                "localIP = \"127.0.0.1\"\n" +
                "localPort = " + s.getBedrockPort() + "\n" +
                "remotePort = " + s.getBedrockPort() + "\n";
        }
        
        if (s.isVoicechat() && s.getVoicechatPort() > 0) {
            toml += "\n[[proxies]]\n" +
                "name = \"mc-vc-" + s.getId().substring(0, 4) + "-" + randSuffix + "\"\n" +
                "type = \"udp\"\n" +
                "localIP = \"127.0.0.1\"\n" +
                "localPort = " + s.getVoicechatPort() + "\n" +
                "remotePort = " + s.getVoicechatPort() + "\n";
        }

        if (s.isKodadashSupport() && s.getKodadashPort() > 0) {
            toml += "\n[[proxies]]\n" +
                "name = \"mc-dash-" + s.getId().substring(0, 4) + "-" + randSuffix + "\"\n" +
                "type = \"tcp\"\n" +
                "localIP = \"127.0.0.1\"\n" +
                "localPort = 7867\n" +
                "remotePort = " + s.getKodadashPort() + "\n";
        }

        write(new File(dir, ".frpc.toml"), toml);
    }

    private void writeDynamicPluginConfigs(ServerInstance srv, File serverDir) {
        File pluginsDir = new File(serverDir, "plugins");
        pluginsDir.mkdirs();

        boolean hasGeyser = srv.isBedrockSupport() || new File(pluginsDir, "Geyser.jar").exists() || new File(pluginsDir, "Geyser-Spigot.jar").exists();
        boolean hasFloodgate = srv.isBedrockSupport() || new File(pluginsDir, "Floodgate.jar").exists() || new File(pluginsDir, "floodgate-bukkit.jar").exists();

        if (hasGeyser && srv.getBedrockPort() > 0) {
            File geyserDir = new File(pluginsDir, "Geyser-Spigot");
            if (!new File(geyserDir, ".manual_override").exists()) {
                try {
                    geyserDir.mkdirs();
                    String geyserConfig = readAsset("geyser_config.yml")
                        .replace("{GEYSER_PORT}", String.valueOf(srv.getBedrockPort()))
                        .replace("{SERVER_PORT}", String.valueOf(srv.getPort()));
                    write(new File(geyserDir, "config.yml"), geyserConfig);
                    extractPlugin(geyserDir, "key.pem", "key.pem");
                } catch (Exception e) { Log.e(TAG, "Failed to write Geyser config", e); }
            }
        }

        if (hasFloodgate) {
            File floodgateDir = new File(pluginsDir, "floodgate");
            if (!new File(floodgateDir, ".manual_override").exists()) {
                try {
                    floodgateDir.mkdirs();
                    String floodgateConfig = readAsset("floodgate_config.yml");
                    write(new File(floodgateDir, "config.yml"), floodgateConfig);
                    extractPlugin(floodgateDir, "key.pem", "key.pem");
                } catch (Exception e) { Log.e(TAG, "Failed to write Floodgate config", e); }
            }
        }

        boolean hasVc = srv.isVoicechat() || new File(pluginsDir, "Voicechat.jar").exists() || new File(pluginsDir, "voicechat-bukkit.jar").exists();
        if (hasVc && srv.getVoicechatPort() > 0) {
            File vcDir = new File(pluginsDir, "voicechat");
            if (!new File(vcDir, ".manual_override").exists()) {
                try {
                    vcDir.mkdirs();
                    String vcConfig = readAsset("voicechat-server.properties").replace("{VOICECHAT_PORT}", String.valueOf(srv.getVoicechatPort()));
                    write(new File(vcDir, "voicechat-server.properties"), vcConfig);
                } catch (Exception e) { Log.e(TAG, "Failed to write Voicechat config", e); }
            }
        }

        if (srv.isKodadashSupport()) {
            extractPlugin(pluginsDir, "kodadash.jar", "KodaDash.jar");
        }
    }

    private int allocatePortSync(ServerInstance srv, String type, int min, int max) {
        try {
            return new SupabaseFunctionsClient(this).allocatePort("", srv.getSubdomain(), type);
        } catch (Exception e) {
            log(srv.getId(), "❌ Proxy Port Allocation failed: " + e.getMessage() + ". Falling back to local port.");
            return getFreePort(min, max);
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

    private void deleteRecursive(File fileOrDirectory, boolean deleteRoot) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child, true);
                }
            }
        }
        if (deleteRoot) fileOrDirectory.delete();
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
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (f.exists()) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {}
        }
        
        java.util.Map<String, String> updates = new java.util.LinkedHashMap<>();
        updates.put("server-port", String.valueOf(s.getPort()));
        updates.put("server-ip", "127.0.0.1");
        updates.put("motd", s.getMotd() != null ? s.getMotd() : "");
        updates.put("accepts-transfers", "true");
        updates.put("gamemode", s.getGamemode() != null ? s.getGamemode().name().toLowerCase() : "survival");
        updates.put("difficulty", s.getDifficulty() != null ? s.getDifficulty().name().toLowerCase() : "normal");
        updates.put("pvp", String.valueOf(s.isPvp()));
        updates.put("white-list", String.valueOf(s.isWhitelist()));
        updates.put("max-players", String.valueOf(s.getMaxPlayers()));
        
        java.util.Map<String, String> defaults = new java.util.HashMap<>();
        defaults.put("online-mode", "false");
        defaults.put("sync-chunk-writes", "false");
        defaults.put("view-distance", "8");
        defaults.put("simulation-distance", "6");
        defaults.put("spawn-protection", "0");
        defaults.put("max-tick-time", "120000");
        defaults.put("network-compression-threshold", "256");

        for (java.util.Map.Entry<String, String> entry : updates.entrySet()) {
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith(entry.getKey() + "=")) {
                    lines.set(i, entry.getKey() + "=" + entry.getValue());
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(entry.getKey() + "=" + entry.getValue());
        }

        for (java.util.Map.Entry<String, String> entry : defaults.entrySet()) {
            boolean found = false;
            for (String line : lines) {
                if (line.trim().startsWith(entry.getKey() + "=")) {
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(entry.getKey() + "=" + entry.getValue());
        }

        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f))) {
            for (String line : lines) pw.println(line);
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
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                String nowStr = sdf.format(new java.util.Date());
                
                String jsonBody;
                try {
                    org.json.JSONObject jObj = new org.json.JSONObject();
                    jObj.put("online_players", isOnline ? players : 0);
                    jObj.put("server_version", isOnline ? verStr : ("OFFLINE|" + verStr));
                    if (isOnline) {
                        jObj.put("last_online", nowStr);
                    }
                    jObj.put("gamemode", srv.getGamemode() != null ? srv.getGamemode().name() : "survival");
                    jObj.put("difficulty", srv.getDifficulty() != null ? srv.getDifficulty().name() : "normal");
                    jObj.put("pvp", srv.isPvp());
                    jObj.put("whitelist", srv.isWhitelist());
                    jObj.put("motd", srv.getMotd() != null ? srv.getMotd() : "");
                    jObj.put("max_players", srv.getMaxPlayers());

                    if (srv.isKodadashSupport()) {
                        File configFile = new File(srv.getServerDir(), "plugins/KodaDash/config.yml");
                        if (configFile.exists()) {
                            try (java.util.Scanner scanner = new java.util.Scanner(configFile)) {
                                while (scanner.hasNextLine()) {
                                    String line = scanner.nextLine().trim();
                                    if (line.startsWith("api-token:")) {
                                        String token = line.substring(line.indexOf(':') + 1).trim();
                                        token = token.replace("'", "").replace("\"", "");
                                        jObj.put("kodadash_token", token);
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    jsonBody = jObj.toString();
                } catch (Exception e) {
                    if (isOnline) {
                        jsonBody = "{\"online_players\": " + players + ", \"server_version\": \"" + verStr + "\", \"last_online\": \"" + nowStr + "\"}";
                    } else {
                        jsonBody = "{\"online_players\": 0, \"server_version\": \"OFFLINE|" + verStr + "\"}";
                    }
                }                
                String appUuid = eu.kodanetwork.mchost.App.getPrefs(KodaServerService.this).getString("app_uuid", "");
                String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + domain + "\", \"p_payload\": " + jsonBody + "}";
                okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                    .post(body)
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
        android.content.SharedPreferences rPrefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if (!rPrefs.getBoolean("lobby_remote_control", true)) return;
        exec.submit(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/koda_servers?host=eq." + srv.getSubdomain() + "&select=server_version,is_banned,host")
                    .get()
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    if (arr.length() > 0) {
                        org.json.JSONObject obj = arr.getJSONObject(0);
                        String ver = obj.optString("server_version", "");
                        boolean isBanned = obj.optBoolean("is_banned", false);
                        String host = obj.optString("host", "");

                        if (isBanned || host.startsWith("deleted_")) {
                            Log.w(TAG, "Server has been banned or deleted by Admin! Terminating...");
                            try { stopServer(srv, true); } catch (Exception e) {}
                            if (host.startsWith("deleted_")) {
                                ServerRepo.get(KodaServerService.this).delete(srv.getId());
                                return; // Stop processing further commands only if deleted
                            }
                        }

                        if (ver.equals("HIBERNATED")) {
                            if (srv.state != ServerInstance.State.HIBERNATED) {
                                try {
                                    stopServer(srv, true);
                                    eu.kodanetwork.mchost.utils.HibernationManager.hibernateServer(KodaServerService.this, srv, ServerRepo.get(KodaServerService.this));
                                    setState(srv, ServerInstance.State.HIBERNATED);
                                    Log.d(TAG, "Server remotely hibernated due to inactivity.");
                                } catch(Exception e) {
                                    Log.e(TAG, "Remote hibernate failed", e);
                                }
                            }
                        } else if (ver.startsWith("CMD:")) {
                            String cmd = ver.substring(4);
                            Log.d(TAG, "Received Remote Command: " + cmd);
                            
                            if (cmd.startsWith("SETPROP_")) {
                                String[] propParts = cmd.substring(8).split("_", 2);
                                if (propParts.length == 2) {
                                    String key = propParts[0];
                                    String val = propParts[1];
                                    if (key.equals("gamemode")) {
                                        try { srv.setGamemode(eu.kodanetwork.mchost.model.ServerInstance.Gamemode.valueOf(val)); } catch (Exception e){}
                                    } else if (key.equals("difficulty")) {
                                        try { srv.setDifficulty(eu.kodanetwork.mchost.model.ServerInstance.Difficulty.valueOf(val)); } catch (Exception e){}
                                    } else if (key.equals("pvp")) {
                                        srv.setPvp(Boolean.parseBoolean(val));
                                    } else if (key.equals("whitelist")) {
                                        srv.setWhitelist(Boolean.parseBoolean(val));
                                    } else if (key.equals("maxPlayers")) {
                                        try { srv.setMaxPlayers(Integer.parseInt(val)); } catch (Exception e){}
                                    } else if (key.equals("motd")) {
                                        srv.setMotd(new String(android.util.Base64.decode(val, android.util.Base64.DEFAULT)));
                                    } else if (key.equals("ram")) {
                                        try { srv.setRamMB(Integer.parseInt(val)); } catch (Exception e){}
                                    }
                                    ServerRepo.get(KodaServerService.this).update(srv);
                                }
                            } else if (cmd.equals("DELETE")) {
                                try { stopServer(srv, true); } catch (Exception e) {}
                                ServerRepo.get(KodaServerService.this).delete(srv.getId());
                                // Tombstone via RPC (direct DELETE is blocked by RLS policy)
                                try {
                                    org.json.JSONObject delPayload = new org.json.JSONObject()
                                            .put("host", "deleted_" + srv.getSubdomain())
                                            .put("server_version", "DELETED");
                                    org.json.JSONObject delBody = new org.json.JSONObject()
                                            .put("p_app_uuid", eu.kodanetwork.mchost.App.getPrefs(KodaServerService.this).getString("app_uuid", ""))
                                            .put("p_host", srv.getSubdomain())
                                            .put("p_payload", delPayload);
                                    okhttp3.RequestBody delReqBody = okhttp3.RequestBody.create(
                                            delBody.toString(), okhttp3.MediaType.parse("application/json"));
                                    okhttp3.Request delReq = new okhttp3.Request.Builder()
                                        .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                                        .post(delReqBody)
                                        .addHeader("Content-Type", "application/json")
                                        .addHeader("apikey", SUPABASE_KEY)
                                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                        .build();
                                    httpClient.newCall(delReq).execute().close();
                                } catch (Exception e) {}
                                return;
                            } else if (cmd.equals("WIPE")) {
                                try { stopServer(srv, true); } catch (Exception e) {}
                                java.io.File dir = new java.io.File(srv.getServerDir());
                                if (dir.exists()) {
                                    deleteRecursively(dir);
                                    dir.mkdirs();
                                }
                            } else if (cmd.startsWith("EXEC_")) {
                                String toExec = cmd.substring(5);
                                sendCmd(srv.getId(), toExec);
                            } else if (cmd.equals("INSTALL_KODADASH")) {
                                srv.setKodadashSupport(true);
                                if (srv.getKodadashPort() <= 0) {
                                    int dPort = allocatePortSync(srv, "kodadash", 7800, 7900);
                                    srv.setKodadashPort(dPort);
                                }
                                ServerRepo.get(KodaServerService.this).update(srv);
                                
                                File pDir = new File(new File(srv.getServerDir()), "plugins");
                                pDir.mkdirs();
                                extractPlugin(pDir, "kodadash.jar", "KodaDash.jar");

                                String appUuid = eu.kodanetwork.mchost.App.getPrefs(KodaServerService.this).getString("app_uuid", "");
                                String patchPayload = "{\"kodadash_port\": " + srv.getKodadashPort() + ", \"server_version\": \"" + (srv.getVersion() == null ? "1.21.11" : srv.getVersion()) + "\"}";
                                okhttp3.RequestBody body1 = okhttp3.RequestBody.create(patchPayload, okhttp3.MediaType.parse("application/json"));
                                okhttp3.Request directPatchReq = new okhttp3.Request.Builder()
                                    .url(SUPABASE_REST + "/koda_servers?host=eq." + srv.getSubdomain())
                                    .patch(body1)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .addHeader("apikey", SUPABASE_KEY)
                                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                    .build();
                                try { httpClient.newCall(directPatchReq).execute().close(); } catch(Exception e){}

                                String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + srv.getSubdomain() + "\", \"p_payload\": " + patchPayload + "}";
                                okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                                okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                    .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                                    .post(body)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .addHeader("apikey", SUPABASE_KEY)
                                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                    .build();
                                try { httpClient.newCall(patchReq).execute().close(); } catch(Exception e){}

                                if (srv.isRunning()) {
                                    stopServer(srv, false);
                                    mainHandler.postDelayed(() -> startServer(srv), 4000);
                                }
                                return;
                            } else if (cmd.equals("DISABLE_KODADASH")) {
                                srv.setKodadashSupport(false);
                                ServerRepo.get(KodaServerService.this).update(srv);
                                
                                File dashPlugin = new File(new File(srv.getServerDir()), "plugins/KodaDash.jar");
                                if (dashPlugin.exists()) dashPlugin.delete();

                                String appUuid = eu.kodanetwork.mchost.App.getPrefs(KodaServerService.this).getString("app_uuid", "");
                                String patchPayload = "{\"kodadash_port\": null, \"server_version\": \"" + (srv.getVersion() == null ? "1.21.11" : srv.getVersion()) + "\"}";
                                okhttp3.RequestBody body1 = okhttp3.RequestBody.create(patchPayload, okhttp3.MediaType.parse("application/json"));
                                okhttp3.Request directPatchReq = new okhttp3.Request.Builder()
                                    .url(SUPABASE_REST + "/koda_servers?host=eq." + srv.getSubdomain())
                                    .patch(body1)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .addHeader("apikey", SUPABASE_KEY)
                                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                    .build();
                                try { httpClient.newCall(directPatchReq).execute().close(); } catch(Exception e){}

                                String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + srv.getSubdomain() + "\", \"p_payload\": " + patchPayload + "}";
                                okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                                okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                    .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                                    .post(body)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .addHeader("apikey", SUPABASE_KEY)
                                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                    .build();
                                try { httpClient.newCall(patchReq).execute().close(); } catch(Exception e){}

                                if (srv.isRunning()) {
                                    stopServer(srv, false);
                                    mainHandler.postDelayed(() -> startServer(srv), 4000);
                                }
                                return;
                            }
                            // Clear it immediately
                            String jsonBody = "{\"server_version\": \"" + (srv.getVersion() == null ? "1.21.11" : srv.getVersion()) + "\"}";
                            String appUuid = eu.kodanetwork.mchost.App.getPrefs(KodaServerService.this).getString("app_uuid", "");
                            String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + srv.getSubdomain() + "\", \"p_payload\": " + jsonBody + "}";
                            okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                            okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                                .post(body)
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
                            } else if (cmd.equals("HIBERNATE")) {
                                if (srv.state != ServerInstance.State.HIBERNATED) {
                                    try {
                                        stopServer(srv, true);
                                        eu.kodanetwork.mchost.utils.HibernationManager.hibernateServer(KodaServerService.this, srv, ServerRepo.get(KodaServerService.this));
                                        setState(srv, ServerInstance.State.HIBERNATED);
                                        Log.d(TAG, "Server remotely hibernated via Admin Command.");
                                    } catch(Exception e) {
                                        Log.e(TAG, "Remote hibernate failed", e);
                                    }
                                }
                            } else if (cmd.equals("DELETE")) {
                                Log.w(TAG, "Server remotely deleted via Admin Command.");
                                try { stopServer(srv, true); } catch (Exception e) {}
                                try { deleteRecursively(new java.io.File(srv.getServerDir())); } catch (Exception e) {}
                                ServerRepo.get(KodaServerService.this).delete(srv.getId());
                                
                                // Update database to formally mark it as deleted_
                                try {
                                    String delJson = "{\"host\": \"deleted_" + srv.getSubdomain() + "\", \"server_version\": \"DELETED\"}";
                                    String delRpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + srv.getSubdomain() + "\", \"p_payload\": " + delJson + "}";
                                    okhttp3.RequestBody delBody = okhttp3.RequestBody.create(delRpcJson, okhttp3.MediaType.parse("application/json"));
                                    okhttp3.Request delReq = new okhttp3.Request.Builder()
                                        .url(SUPABASE_REST + "/rpc/rpc_patch_server")
                                        .post(delBody)
                                        .addHeader("Content-Type", "application/json")
                                        .addHeader("apikey", SUPABASE_KEY)
                                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                                        .build();
                                    httpClient.newCall(delReq).execute().close();
                                } catch (Exception e) {}
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

    private void checkFileTransfers(ServerInstance srv) {
        exec.submit(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(SUPABASE_REST + "/koda_file_transfers?server_id=eq." + srv.getId() + "&select=id,file_path,status,content_b64")
                    .get()
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .build();
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        String id = obj.getString("id");
                        String status = obj.getString("status");
                        String filePath = obj.getString("file_path");
                        String contentB64 = obj.optString("content_b64", "");
                        
                        java.io.File target = new java.io.File(srv.getServerDir(), filePath);
                        
                        if (status.equals("REQUEST_DIR")) {
                            org.json.JSONArray filesArr = new org.json.JSONArray();
                            if (target.exists() && target.isDirectory()) {
                                java.io.File[] children = target.listFiles();
                                if (children != null) {
                                    for (java.io.File c : children) {
                                        org.json.JSONObject cObj = new org.json.JSONObject();
                                        cObj.put("name", c.getName());
                                        cObj.put("is_dir", c.isDirectory());
                                        filesArr.put(cObj);
                                    }
                                }
                            }
                            String resB64 = android.util.Base64.encodeToString(filesArr.toString().getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                            updateFileTransfer(id, "UPLOADED_DIR", resB64);
                        } else if (status.equals("REQUEST_FILE")) {
                            String resB64 = "";
                            if (target.exists() && target.isFile()) {
                                byte[] bytes = java.nio.file.Files.readAllBytes(target.toPath());
                                resB64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                            }
                            updateFileTransfer(id, "UPLOADED_FILE", resB64);
                        } else if (status.equals("SAVING_FILE")) {
                            if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                            byte[] bytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT);
                            java.nio.file.Files.write(target.toPath(), bytes);
                            deleteFileTransfer(id);
                        }
                    }
                }
                response.close();
            } catch (Exception e) {
                Log.w(TAG, "File transfer check failed: " + e.getMessage());
            }
        });
    }

    private void updateFileTransfer(String id, String status, String contentB64) {
        try {
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("status", status);
            payload.put("content_b64", contentB64);
            
            okhttp3.RequestBody body = okhttp3.RequestBody.create(payload.toString(), okhttp3.MediaType.parse("application/json"));
            okhttp3.Request req = new okhttp3.Request.Builder()
                .url(SUPABASE_REST + "/koda_file_transfers?id=eq." + id)
                .patch(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .build();
            httpClient.newCall(req).execute().close();
        } catch(Exception e){}
    }

    private void deleteFileTransfer(String id) {
        try {
            okhttp3.Request req = new okhttp3.Request.Builder()
                .url(SUPABASE_REST + "/koda_file_transfers?id=eq." + id)
                .delete()
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .build();
            httpClient.newCall(req).execute().close();
        } catch(Exception e){}
    }

    private void generateAiConfigs(ServerInstance srv) {
        String apiKey = eu.kodanetwork.mchost.App.getPrefs(this).getString("gemini_api_key", "");
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
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
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
                        "6. REWRITE ALL chat messages, prefixes, join/quit messages, and feedback messages to be completely CUSTOM, unique, and stylized for an AI server. Do NOT leave them as default. " +
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
        // Shut the schedulers down FIRST — otherwise their tasks keep the
        // service alive past the FGS stop timeout and Android kills the app
        // (ForegroundServiceDidNotStopInTimeException)
        if (scheduler != null) scheduler.shutdownNow();
        try {
            stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {}
        for (RT rt : runtimes.values()) {
            if (rt.proc != null) rt.proc.destroyForcibly();
            if (rt.frpcProc != null) rt.frpcProc.destroyForcibly();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        exec.shutdownNow();
        Log.d(TAG, "Service Destroyed. Processes killed.");
        super.onDestroy();
    }
    private void deleteRecursively(java.io.File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            java.io.File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    // ── PumpkinMC (native Rust binary) ──────────────────────────────────────

    private void startPumpkin(ServerInstance srv) {
        String id = srv.getId();
        srv.startTime = System.currentTimeMillis();
        setState(srv, ServerInstance.State.STARTING);
        log(id, "  🎃 Pumpkin " + srv.getVersion() + " (Rust, native)...");

        exec.submit(() -> {
            if (!eu.kodanetwork.mchost.util.PumpkinRuntime.isInstalled(this)) {
                log(id, "  ⬇ Lade Pumpkin-Binary (~10 MB)...");
                boolean ok = eu.kodanetwork.mchost.util.PumpkinRuntime.ensureBinarySync(this,
                        (pct, msg) -> log(id, "  ⬇ Pumpkin: " + pct + "% (" + msg + ")"));
                if (!ok) {
                    log(id, "  ✗ Pumpkin-Binary nicht verfuegbar. Bitte erneut versuchen.");
                    setState(srv, ServerInstance.State.CRASHED);
                    return;
                }
            }

            File binary = eu.kodanetwork.mchost.util.PumpkinRuntime.getBinaryFile(this);
            File dir = new File(srv.getServerDir());
            if (!dir.exists()) dir.mkdirs();

            try {
                java.io.File logFile = new java.io.File(dir, "pumpkin.log");
                RT rt = new RT();
                rt.isNative = true;

                ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath());
                pb.directory(dir);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                rt.proc = proc;
                rt.stdin = new java.io.PrintStream(proc.getOutputStream(), true);
                rt.logs.add("🎃 Pumpkin started");

                runtimes.put(id, rt);

                // log reader thread -> console
                Thread reader = new Thread(() -> {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            final String l = line;
                            mainHandler.post(() -> log(id, l));
                        }
                    } catch (Exception ignored) {}
                    mainHandler.post(() -> {
                        runtimes.remove(id);
                        setState(srv, ServerInstance.State.OFFLINE);
                        log(id, "  ⏹ Pumpkin exited.");
                    });
                });
                reader.setDaemon(true);
                reader.start();
                rt.loggerThread = reader;

                setState(srv, ServerInstance.State.ONLINE);
                log(id, "  ✓ Pumpkin online. Console: 'help' fuer Befehle.");
            } catch (Exception e) {
                log(id, "  ✗ Pumpkin start failed: " + e.getMessage());
                setState(srv, ServerInstance.State.CRASHED);
            }
        });
    }
}
