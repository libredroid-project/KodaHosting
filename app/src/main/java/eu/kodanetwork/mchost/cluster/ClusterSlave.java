package eu.kodanetwork.mchost.cluster;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.kodanetwork.mchost.security.HWIDManager;
import eu.kodanetwork.mchost.service.KodaServerService;

/**
 * Cluster SLAVE side: this device offers itself to a master over USB.
 * Accessory mode (AOAv2): the master (USB host) opens us as accessory; we get
 * FileStreams — no root, no ADB. Handles: hello/heartbeat emission and
 * START/STOP/RESTART/GETLOG commands via the existing service intents.
 */
public class ClusterSlave {

    private static ClusterSlave instance;

    public static synchronized ClusterSlave get(Context ctx) {
        if (instance == null) instance = new ClusterSlave(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final UsbManager usb;
    private final String deviceId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ParcelFileDescriptor pfd;
    private PrintStream out;
    private Thread reader, hbTimer;
    private static final String ACTION_ACC_PERM = "eu.kodanetwork.mchost.CLUSTER_ACC_PERM";
    private BroadcastReceiver attachReceiver, detachReceiver, permReceiver;
    private Thread pollTimer;
    private volatile boolean permPending = false;
    private volatile boolean linkUp = false;

    private ClusterSlave(Context ctx) {
        this.ctx = ctx;
        this.usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        this.deviceId = HWIDManager.getDeviceHWID(ctx);
    }

    public void start() {
        if (running.getAndSet(true)) return;
        ClusterBus.get().setSlaveRole(true);
        attachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Log.d(ClusterProtocol.TAG, "slave: accessory attached");
                openLink();
            }
        };
        detachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Log.d(ClusterProtocol.TAG, "slave: accessory detached");
                closeLink();
            }
        };
        int flags = Context.RECEIVER_EXPORTED;
        androidx.core.content.ContextCompat.registerReceiver(ctx, attachReceiver, new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED), flags);
        androidx.core.content.ContextCompat.registerReceiver(ctx, detachReceiver, new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED), flags);
        permReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Log.i(ClusterProtocol.TAG, "slave: permission result " + granted);
                permPending = false;
                if (granted) openLink();
            }
        };
        androidx.core.content.ContextCompat.registerReceiver(ctx, permReceiver, new IntentFilter(ACTION_ACC_PERM), flags);
        // robust poll: attach events runtime receivers never see are covered this way
        pollTimer = new Thread(() -> {
            while (running.get()) {
                openLink();
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
            }
        }, "cluster-slave-poll");
        pollTimer.setDaemon(true);
        pollTimer.start();
        Log.i(ClusterProtocol.TAG, "slave role started");
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        send(ClusterProtocol.bye());
        closeLink();
        try { ctx.unregisterReceiver(attachReceiver); } catch (Exception ignored) {}
        try { ctx.unregisterReceiver(detachReceiver); } catch (Exception ignored) {}
        try { ctx.unregisterReceiver(permReceiver); } catch (Exception ignored) {}
        if (pollTimer != null) pollTimer.interrupt();
        ClusterBus.get().setSlaveRole(false);
        Log.i(ClusterProtocol.TAG, "slave role stopped");
    }

    // ── link ─────────────────────────────────────────────────────────

    public synchronized void openLink() {
        if (!running.get() || linkUp) return;
        UsbAccessory[] list = usb.getAccessoryList();
        if (list == null || list.length == 0) return;
        UsbAccessory acc = list[0];
        if (!usb.hasPermission(acc)) {
            // custom action: runtime receivers never receive ACTION_USB_ACCESSORY_ATTACHED
            // (that launch intent only goes to manifest activities), so the grant result
            // must come back through our own broadcast action
            if (permPending) return;
            permPending = true;
            Intent fire = new Intent(ACTION_ACC_PERM).setPackage(ctx.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 2, fire,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                usb.requestPermission(acc, pi);
                Log.i(ClusterProtocol.TAG, "slave: permission dialog requested");
            } catch (Exception e) {
                permPending = false;
                Log.w(ClusterProtocol.TAG, "slave: perm request failed", e);
            }
            return;
        }
        permPending = false;
        pfd = usb.openAccessory(acc);
        if (pfd == null) {
            Log.w(ClusterProtocol.TAG, "slave: openAccessory returned null");
            return;
        }
        out = new PrintStream(new FileOutputStream(pfd.getFileDescriptor()), true);
        linkUp = true;
        Log.i(ClusterProtocol.TAG, "slave: link UP");

        send(ClusterProtocol.hello(ctx, deviceId));

        reader = new Thread(this::readLoop, "cluster-slave-reader");
        reader.setDaemon(true);
        reader.start();

        hbTimer = new Thread(() -> {
            while (running.get() && linkUp) {
                send(ClusterProtocol.heartbeat(ctx, deviceId));
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }, "cluster-slave-hb");
        hbTimer.setDaemon(true);
        hbTimer.start();
    }

    private synchronized void closeLink() {
        linkUp = false;
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        out = null; pfd = null;
        Log.i(ClusterProtocol.TAG, "slave: link DOWN");
    }

    private void readLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
            String line;
            while (running.get() && linkUp && (line = br.readLine()) != null) {
                handleFrame(line.trim());
            }
        } catch (Exception e) {
            if (running.get()) Log.w(ClusterProtocol.TAG, "slave: read loop ended: " + e.getMessage());
        }
        closeLink();
    }

    public void send(String frame) {
        PrintStream o = out;
        if (o != null && linkUp) {
            try { o.println(frame); } catch (Exception e) { closeLink(); }
        }
    }

    // ── commands ─────────────────────────────────────────────────────

    private void handleFrame(String frame) {
        String t = ClusterProtocol.type(frame);
        if (t.isEmpty()) return;
        JSONObject o = ClusterProtocol.parse(frame);
        if (o == null) return;
        switch (t) {
            case "ping":
                send(pongFrame(o));
                break;
            case "bye":
                closeLink();
                break;
            case "cmd":
                execCmd(o);
                break;
            default: // hb/ack/hello not expected on slave
        }
    }

    private String pongFrame(JSONObject ping) {
        JSONObject o = new JSONObject();
        try {
            o.put("v", ClusterProtocol.VERSION);
            o.put("t", "pong");
            o.put("ts", ping.optLong("ts", 0));
        } catch (Exception ignored) {}
        return o.toString();
    }

    private void execCmd(JSONObject cmd) {
        int ref = cmd.optInt("ref", 0);
        String action = cmd.optString("action", "");
        String sid = cmd.optString("sid", "");
        Log.i(ClusterProtocol.TAG, "slave: cmd " + action + " for " + sid);
        try {
            switch (action) {
                case "START":
                case "STOP":
                case "RESTART": {
                    Intent i = new Intent(ctx, KodaServerService.class);
                    i.setAction(action.equals("START") ? KodaServerService.ACTION_START
                            : action.equals("STOP") ? KodaServerService.ACTION_STOP
                            : KodaServerService.ACTION_RESTART);
                    i.putExtra(KodaServerService.EXTRA_ID, sid);
                    ctx.startForegroundService(i);
                    send(ClusterProtocol.ack(ref, true, action + " sent"));
                    break;
                }
                case "GETLOG": {
                    List<String> log = KodaServerService.getRecentLog(sid);
                    StringBuilder sb = new StringBuilder();
                    int from = Math.max(0, log.size() - 100);
                    for (int i = from; i < log.size(); i++) sb.append(log.get(i)).append('\n');
                    send(ClusterProtocol.ack(ref, true, sb.toString()));
                    break;
                }
                default:
                    send(ClusterProtocol.ack(ref, false, "unknown action " + action));
            }
        } catch (Exception e) {
            send(ClusterProtocol.ack(ref, false, e.getMessage() == null ? "error" : e.getMessage()));
        }
    }
}
