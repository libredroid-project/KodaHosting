package eu.kodanetwork.mchost.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.ui.MainActivity;
import eu.kodanetwork.mchost.integration.TermuxServerLauncher;
import eu.kodanetwork.mchost.util.JavaFinder;

public class TermuxServerService extends Service {

    public static final String ACTION_START   = "KODA_START";
    public static final String ACTION_STOP    = "KODA_STOP";
    public static final String ACTION_RESTART = "KODA_RESTART";
    public static final String ACTION_KILL    = "KODA_KILL";

    public static final String EXTRA_ID       = "id";
    public static final String EXTRA_STATE    = "state";
    public static final String EXTRA_LOG      = "log";

    public static final String BCAST_STATE    = "eu.kodanetwork.mchost.STATE_CHANGE";
    public static final String BCAST_LOG      = "eu.kodanetwork.mchost.LOG_LINE";

    // DEIN EIGENER VPS:
    public static final String BORE_HOST      = "85.215.180.87";

    private static final String CHANNEL = "mchost_svc";
    private static final int    NOTIF   = 77;

    private static class RT {
        Process     proc;
        Process     playitProc;
        PrintStream stdin;
        List<String> log = Collections.synchronizedList(new ArrayList<>());
    }

    private final IBinder binder = new LocalBinder();
    private final Map<String, RT> runtimes = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    public interface StateCb { void onChange(String id, ServerInstance.State s); }
    public interface LogCb   { void onLine(String id, String line); }

    private final List<StateCb> stateCbs = new ArrayList<>();
    private final List<LogCb>   logCbs   = new ArrayList<>();

    public class LocalBinder extends Binder {
        public TermuxServerService get() { return TermuxServerService.this; }
    }

    @Override public void onCreate() { 
        super.onCreate(); 
        mkChannel(); 
        acquireWake(); 
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(NOTIF, notif("KodaNetwork", "Service active"));
        }
    }
    
    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int f, int s) {
        if (intent == null) return START_STICKY;
        String id  = intent.getStringExtra(EXTRA_ID);
        String act = intent.getAction();
        
        if (id == null || act == null) return START_STICKY;
        eu.kodanetwork.mchost.util.AppLogger.log("Service", "Action: " + act + " for " + id);

        ServerInstance server = ServerRepo.get(this).byId(id);
        if (server == null) return START_STICKY;

        switch (act) {
            case ACTION_START:   exec.submit(() -> startServer(server)); break;
            case ACTION_STOP:    stopServer(server, false); break;
            case ACTION_KILL:    stopServer(server, true);  break;
            case ACTION_RESTART: stopServer(server, false); exec.submit(() -> startServer(server)); break;
        }
        return START_STICKY;
    }

    private void startServer(ServerInstance srv) {
        String id = srv.getId();
        if (runtimes.containsKey(id)) return;
        
        setState(srv, ServerInstance.State.STARTING);
        log(id, "  🍊 Starting " + srv.getName() + "...");
        
        File dir = new File(srv.getServerDir());
        dir.mkdirs();

        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log(id, "  ✗ No .jar found!");
            setState(srv, ServerInstance.State.CRASHED);
            return;
        }
        File jar = jars[0];

        try {
            writeEula(dir);
            writeProps(srv, dir);
        } catch (IOException e) {
            log(id, "  ✗ Config error: " + e.getMessage());
            setState(srv, ServerInstance.State.CRASHED); return;
        }

        File logFile = new File(dir, "server.log");
        if (logFile.exists()) logFile.delete();
        File frpLog = new File(dir, "frp.log");
        if (frpLog.exists()) frpLog.delete();

        String inFifoPath = "$HOME/koda_in_" + id.substring(0, 8);

        // Performance Optimized Bash Command with FRP:
        // 1. NICE -20 for maximum CPU priority
        // 2. Generate frpc.toml for both TCP (Java) and UDP (Bedrock)
        // 3. Java (TCP) uses port (30000-40000)
        // 4. Bedrock (UDP) uses port + 10000 (40000-50000)
        String bashCmd = String.format(
            "mkdir -p '%s'; cd '%s'; " +
            "printf 'serverAddr = \"%s\"\\nserverPort = 7000\\n\\n[[proxies]]\\nname = \"mc-java-%s\"\\ntype = \"tcp\"\\nlocalIP = \"127.0.0.1\"\\nlocalPort = %d\\nremotePort = %d\\n\\n[[proxies]]\\nname = \"mc-bedrock-%s\"\\ntype = \"udp\"\\nlocalIP = \"127.0.0.1\"\\nlocalPort = 19132\\nremotePort = %d' > frpc.toml; " +
            "(sleep 3; am start -n eu.kodanetwork.mchost/eu.kodanetwork.mchost.ui.MainActivity) & " +
            "rm -f world/session.lock; " +
            "rm -f %s && mkfifo %s && exec 3<>%s; " +
            "(frpc -c frpc.toml > frp.log 2>&1) & " +
            "nice -n -20 /data/data/com.termux/files/usr/bin/java -Xmx%dM -Xms%dM " +
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
            "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
            "-Dterminal.jline=false -Dterminal.ansi=true -Dcom.sun.jna.nosys=true " +
            "-jar '%s' --nogui <&3 >> server.log 2>&1",
            dir.getAbsolutePath(), dir.getAbsolutePath(), BORE_HOST, id.substring(0,4), srv.getPort(), srv.getPort(), id.substring(0,4), (srv.getPort() + 10000),
            inFifoPath, inFifoPath, inFifoPath, 
            srv.getRamMB(), srv.getRamMB(), jar.getName()
        );

        if (eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, bashCmd, false)) {
            RT rt = new RT();
            runtimes.put(id, rt);
            updateNotif();
            startFrpMonitor(id, srv, dir);
            startLogMonitor(id, srv, logFile);
        } else {
            log(id, "  ✗ Termux trigger failed!");
            setState(srv, ServerInstance.State.CRASHED);
        }
    }

    private void startFrpMonitor(String id, ServerInstance srv, File dir) {
        exec.submit(() -> {
            eu.kodanetwork.mchost.util.AppLogger.log("DNS", "Starting FRP monitor for " + id);
            log(id, "  ► Opening dual tunnel (TCP+UDP)...");
            File frpLog = new File(dir, "frp.log");
            for (int i = 0; i < 60; i++) {
                sleep(1500);
                if (frpLog.exists()) {
                    String content = readFullFile(frpLog);
                    if (content.contains("command not found") || content.contains("not found")) {
                        log(id, "  ✗ frp not installed in Termux!"); break;
                    }
                    // Parse FRP log for remote port: [mc-java-xxxx] start proxy success, remote_addr [vps_ip:12345]
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("mc-java-.*?remote_addr \\[.*?:([0-9]+)\\]").matcher(content);
                    if (m.find()) {
                        int remotePort = Integer.parseInt(m.group(1));
                        String publicAddr = BORE_HOST + ":" + remotePort;
                        log(id, "  ✓ Tunnel ready (TCP+UDP): " + publicAddr);
                        srv.setPlayitAddress(publicAddr);
                        linkDns(id, srv, remotePort);
                        break;
                    } else if (i % 10 == 0) {
                        eu.kodanetwork.mchost.util.AppLogger.log("DNS", "FRP Log: " + content.replace("\n", " | "));
                    }
                }
            }
        });
    }

    private void linkDns(String id, ServerInstance srv, int port) {
        eu.kodanetwork.mchost.util.AppLogger.log("DNS", "Linking DNS via Supabase for " + srv.getSubdomain() + " to " + BORE_HOST + ":" + port);
        try {
            eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient supabase = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this);
            String host = srv.getSubdomain().isEmpty() ? "server" : srv.getSubdomain();

            log(id, "  ► DNS: Aktualisiere " + host + ".kodanetwork.eu (via Supabase)...");
            supabase.createDnsLink("", host, BORE_HOST, port);

            eu.kodanetwork.mchost.util.AppLogger.log("DNS", "DNS successfully linked via Supabase.");
            log(id, "  ✓ DNS AKTIV! Join via: " + host + ".kodanetwork.eu");
            log(id, "  ► (Localhost: 127.0.0.1:" + srv.getPort() + ")");
        } catch (Exception e) {
            eu.kodanetwork.mchost.util.AppLogger.log("DNS", "Critical Error during linking: " + e.getMessage());
            log(id, "  ⚠ DNS Fehler: " + e.getMessage());
        }
    }

    private void startLogMonitor(String id, ServerInstance srv, File logFile) {
        exec.submit(() -> {
            try {
                for (int i = 0; i < 300; i++) {
                    if (logFile.exists()) break;
                    if (!runtimes.containsKey(id)) return;
                    sleep(100);
                }
                if (!logFile.exists()) {
                    log(id, "  ✗ Termux timeout!");
                    setState(srv, ServerInstance.State.CRASHED);
                    runtimes.remove(id);
                    return;
                }
                RT rt = runtimes.get(id);
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "r");
                long lastPos = 0;
                while (runtimes.containsKey(id)) {
                    long len = raf.length();
                    if (len < lastPos) lastPos = 0;
                    if (len > lastPos) {
                        raf.seek(lastPos);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            line = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                            if (rt != null) {
                                rt.log.add(line);
                                if (rt.log.size() > 1000) rt.log.remove(0);
                            }
                            log(id, line);
                            if (line.contains("Done (") && line.contains("For help")) {
                                setState(srv, ServerInstance.State.ONLINE);
                                srv.startTime = System.currentTimeMillis();
                            }
                            if (line.contains("joined the game"))  srv.onlinePlayers++;
                            if (line.contains("left the game"))    srv.onlinePlayers = Math.max(0, srv.onlinePlayers - 1);
                        }
                        lastPos = raf.getFilePointer();
                    }
                    sleep(500);
                }
                raf.close();
            } catch (Exception e) {
                eu.kodanetwork.mchost.util.AppLogger.log("Service", "Monitor error: " + e.getMessage());
            }
        });
    }

    public void stopServer(ServerInstance srv, boolean force) {
        setState(srv, ServerInstance.State.STOPPING);
        RT rt = runtimes.get(srv.getId());
        if (rt == null) { setState(srv, ServerInstance.State.OFFLINE); return; }
        sendCmd(srv.getId(), "stop");
        sleep(5000);
        String killCmd = String.format("pkill -f '%s'", srv.getServerDir());
        eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, killCmd);
        if (rt.playitProc != null && rt.playitProc.isAlive()) rt.playitProc.destroyForcibly();
        runtimes.remove(srv.getId());
        setState(srv, ServerInstance.State.OFFLINE);
        log(srv.getId(), "  ■ Stopped.");
    }

    public void sendCmd(String id, String cmd) {
        if (runtimes.containsKey(id)) {
            String inFifoPath = "/data/data/com.termux/files/home/koda_in_" + id.substring(0, 8);
            String bashCmd = String.format("echo '%s' > %s", cmd.replace("'", "'\\''"), inFifoPath);
            eu.kodanetwork.mchost.integration.TermuxBridge.runBashCommand(this, bashCmd, true);
            log(id, "> " + cmd);
        }
    }

    public boolean isRunning(String id) { return runtimes.containsKey(id); }
    public List<String> getLog(String id) {
        RT rt = runtimes.get(id);
        return rt != null ? new ArrayList<>(rt.log) : new ArrayList<>();
    }

    public boolean isJavaAvailable() { return JavaFinder.available(this); }
    public String  getJavaPath()     { return JavaFinder.find(this); }

    private void writeEula(File dir) throws IOException { write(new File(dir,"eula.txt"), "eula=true\n"); }
    private void writeProps(ServerInstance s, File dir) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("server-port=").append(s.getPort()).append("\n");
        b.append("server-ip=0.0.0.0\n");
        b.append("online-mode=false\n");
        b.append("motd=").append(s.getMotd()).append("\n");
        b.append("max-players=").append(s.getMaxPlayers()).append("\n");
        b.append("enable-command-block=true\nspawn-protection=0\nlevel-name=world\n");
        write(new File(dir,"server.properties"), b.toString());
    }
    private void write(File f, String content) throws IOException { try (FileWriter fw = new FileWriter(f)) { fw.write(content); } }
    private String readFullFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        } catch (Exception ignored) {}
        return sb.toString();
    }

    public void addStateCb(StateCb c) { stateCbs.add(c); }
    public void addLogCb(LogCb c) { logCbs.add(c); }
    private void setState(ServerInstance srv, ServerInstance.State s) { srv.state = s; mainHandler.post(() -> { for (StateCb c : stateCbs) c.onChange(srv.getId(), s); }); }
    private void log(String id, String line) { mainHandler.post(() -> { for (LogCb c : logCbs) c.onLine(id, line); }); }
    private void mkChannel() { if (Build.VERSION.SDK_INT >= 26) { NotificationChannel ch = new NotificationChannel(CHANNEL,"MC Servers", NotificationManager.IMPORTANCE_LOW); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); } }
    private Notification notif(String title, String text) { PendingIntent pi = PendingIntent.getActivity(this,0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE); return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(text).setContentIntent(pi).setOngoing(true).build(); }
    private void updateNotif() { int n = runtimes.size(); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF,notif("🍊 KodaNetwork", n==0?"No servers running":n+" server"+(n>1?"s":"")+" running")); }
    private void acquireWake() { PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE); if (pm!=null){ wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"KodaNet::Wake"); wakeLock.acquire(); } }
    private void sleep(long ms) { try{Thread.sleep(ms);}catch(InterruptedException ignored){} }
    @Override public void onDestroy() { for (RT rt : runtimes.values()) { if(rt.proc!=null) rt.proc.destroyForcibly(); } if(wakeLock!=null&&wakeLock.isHeld()) wakeLock.release(); exec.shutdownNow(); super.onDestroy(); }
}
