package eu.kodanetwork.mchost.cluster;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cluster MASTER side: this device is the USB host. Scans for Android devices,
 * switches them into accessory mode (AOAv2) if needed, claims the bulk
 * endpoints and speaks the frame protocol. Keeps one Link per slave.
 */
public class ClusterMaster {

    private static ClusterMaster instance;

    public static synchronized ClusterMaster get(Context ctx) {
        if (instance == null) instance = new ClusterMaster(ctx.getApplicationContext());
        return instance;
    }

    /** One connected slave: connection + io threads + link state. */
    private class Link {
        final UsbDevice device;
        UsbDeviceConnection conn;
        UsbInterface iface;
        UsbEndpoint epIn, epOut;
        Thread reader;
        final StringBuilder lineBuf = new StringBuilder(4096);
        volatile boolean up = false;
        String deviceId = null; // from hello

        Link(UsbDevice device) { this.device = device; }

        boolean open() {
            conn = usb.openDevice(device);
            if (conn == null) { Log.w(ClusterProtocol.TAG, "master: openDevice failed"); return false; }
            outer:
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface itf = device.getInterface(i);
                if (itf.getInterfaceClass() == 255 && itf.getInterfaceSubclass() == 6) {
                    if (conn.claimInterface(itf, true)) {
                        iface = itf;
                        for (int e = 0; e < itf.getEndpointCount(); e++) {
                            UsbEndpoint ep = itf.getEndpoint(e);
                            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                if (ep.getDirection() == UsbConstants.USB_DIR_IN) epIn = ep; else epOut = ep;
                            }
                        }
                        break outer;
                    }
                }
            }
            if (iface == null || epIn == null || epOut == null) {
                Log.w(ClusterProtocol.TAG, "master: no AOA bulk interface on " + device.getDeviceName());
                if (conn != null) conn.close();
                conn = null;
                return false;
            }
            up = true;
            reader = new Thread(() -> readLoop(this), "cluster-master-read");
            reader.setDaemon(true);
            reader.start();
            Log.i(ClusterProtocol.TAG, "master: link open to " + device.getDeviceName());
            return true;
        }

        void close() {
            up = false;
            try { if (conn != null && iface != null) conn.releaseInterface(iface); } catch (Exception ignored) {}
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
            conn = null;
            if (deviceId != null) ClusterBus.get().linkUp(deviceId, false);
        }

        synchronized void send(String frame) {
            if (!up || conn == null || epOut == null) return;
            byte[] data = (frame + "\n").getBytes(StandardCharsets.UTF_8);
            int sent = 0;
            while (sent < data.length) {
                int n = conn.bulkTransfer(epOut, data, sent, data.length - sent, 3000);
                if (n < 0) { close(); return; }
                sent += n;
            }
        }
    }

    private final Context ctx;
    private final UsbManager usb;
    private final Map<String, Link> links = new HashMap<>(); // keyed by usb device name
    private final AtomicBoolean running = new AtomicBoolean(false);
    private BroadcastReceiver permReceiver, attachReceiver, detachReceiver;
    private static final String ACTION_USB_PERMISSION = "eu.kodanetwork.mchost.CLUSTER_USB_PERM";
    private int pingCounter = 0;

    private ClusterMaster(Context ctx) {
        this.ctx = ctx;
        this.usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
    }

    public void start() {
        if (running.getAndSet(true)) return;
        ClusterBus.get().setMasterRole(true);
        permReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                UsbDevice dev = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && dev != null) {
                    openOrSwitch(dev);
                }
            }
        };
        attachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                UsbDevice dev = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (dev != null) openOrSwitch(dev);
            }
        };
        detachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                UsbDevice dev = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (dev != null) {
                    Link l = links.remove(dev.getDeviceName());
                    if (l != null) l.close();
                    ClusterBus.get().notifyDevicesChanged();
                }
            }
        };
        int flags = Context.RECEIVER_EXPORTED;
        androidx.core.content.ContextCompat.registerReceiver(ctx, permReceiver, new IntentFilter(ACTION_USB_PERMISSION), flags);
        androidx.core.content.ContextCompat.registerReceiver(ctx, attachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), flags);
        androidx.core.content.ContextCompat.registerReceiver(ctx, detachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), flags);
        scan();
        Log.i(ClusterProtocol.TAG, "master role started");
        // periodic ping for RTT
        new Thread(() -> {
            while (running.get()) {
                for (Link l : links.values()) if (l.up) l.send(pingFrame(++pingCounter));
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }, "cluster-master-ping").start();
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        for (Link l : links.values()) { l.send(ClusterProtocol.bye()); l.close(); }
        links.clear();
        try { ctx.unregisterReceiver(permReceiver); } catch (Exception ignored) {}
        try { ctx.unregisterReceiver(attachReceiver); } catch (Exception ignored) {}
        try { ctx.unregisterReceiver(detachReceiver); } catch (Exception ignored) {}
        ClusterBus.get().setMasterRole(false);
        Log.i(ClusterProtocol.TAG, "master role stopped");
    }

    private String pingFrame(int n) {
        JSONObject o = new JSONObject();
        try { o.put("v", ClusterProtocol.VERSION); o.put("t", "ping"); o.put("n", n); o.put("ts", System.currentTimeMillis()); } catch (Exception ignored) {}
        return o.toString();
    }

    /** Scan now; can also be called from a UI refresh button. */
    public void scan() {
        if (!running.get()) return;
        for (UsbDevice dev : usb.getDeviceList().values()) {
            Log.d(ClusterProtocol.TAG, "usb device: " + dev.getDeviceName()
                    + " vid=" + String.format("0x%04x", dev.getVendorId())
                    + " pid=" + String.format("0x%04x", dev.getProductId())
                    + " cls=" + dev.getDeviceClass()
                    + " ifaces=" + ifaceSummary(dev));
            if (links.containsKey(dev.getDeviceName())) continue;
            openOrSwitch(dev);
        }
    }

    private String ifaceSummary(UsbDevice dev) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dev.getInterfaceCount(); i++) {
            if (i > 0) sb.append(',');
            sb.append(dev.getInterface(i).getInterfaceClass())
              .append('/').append(dev.getInterface(i).getInterfaceSubclass());
        }
        return sb.toString();
    }

    private void openOrSwitch(UsbDevice dev) {
        if (hasAoaInterface(dev)) {
            if (usb.hasPermission(dev)) {
                Link l = new Link(dev);
                if (l.open()) links.put(dev.getDeviceName(), l);
            } else {
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                        new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usb.requestPermission(dev, pi);
            }
        } else if (looksLikeAndroid(dev)) {
            // control transfers need USB permission FIRST — openDevice silently
            // returns null otherwise and the accessory switch never happens
            if (!usb.hasPermission(dev)) {
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 1,
                        new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usb.requestPermission(dev, pi);
                Log.i(ClusterProtocol.TAG, "master: requesting USB permission for " + dev.getDeviceName());
            } else {
                switchIntoAccessoryMode(dev);
            }
        }
    }

    private boolean hasAoaInterface(UsbDevice dev) {
        for (int i = 0; i < dev.getInterfaceCount(); i++) {
            UsbInterface itf = dev.getInterface(i);
            if (itf.getInterfaceClass() == 255 && itf.getInterfaceSubclass() == 6) return true;
        }
        return false;
    }

    /** Heuristic: Androids in normal (MTP/none) mode — try the AOA switch; harmless if it fails. */
    private boolean looksLikeAndroid(UsbDevice dev) {
        // AOA-capable devices expose the accessory strings via vendor requests; simplest
        // heuristic for MVP: try switching any device that isn't already AOA and isn't a hub.
        return dev.getDeviceClass() != UsbConstants.USB_CLASS_HUB;
    }

    /** AOAv2: send accessory identification strings, then START. The device re-enumerates. */
    private void switchIntoAccessoryMode(UsbDevice dev) {
        UsbDeviceConnection c = usb.openDevice(dev);
        if (c == null) return;
        try {
            int reqType = 0x40; // dir=host→device, type=vendor, recipient=device
            sendString(c, reqType, 0, "KodaHosting");
            sendString(c, reqType, 1, "KodaCluster");
            sendString(c, reqType, 2, "1.0");
            int r = c.controlTransfer(reqType, 53, 0, 0, null, 0, 1000); // ACCESSORY_START
            Log.i(ClusterProtocol.TAG, "master: accessory START → " + r + " (device re-enumerates)");
        } finally {
            c.close();
        }
        // after re-enumeration the ATTACH broadcast fires; also re-scan as safety net
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(this::scan, 1200);
        h.postDelayed(this::scan, 3000);
    }

    private void sendString(UsbDeviceConnection c, int reqType, int index, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        // ACCESSORY_SEND_STRING = 52
        c.controlTransfer(reqType, 52, 0, index, data, data.length, 1000);
    }

    // ── frame handling ───────────────────────────────────────────────

    private void readLoop(Link l) {
        byte[] buf = new byte[4096];
        while (l.up && l.conn != null) {
            int n = l.conn.bulkTransfer(l.epIn, buf, buf.length, 3000);
            if (n < 0) continue; // timeout, retry — link state checked via up/close
            if (n == 0) continue;
            String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
            synchronized (l.lineBuf) {
                l.lineBuf.append(chunk);
                int nl;
                while ((nl = l.lineBuf.indexOf("\n")) >= 0) {
                    String frame = l.lineBuf.substring(0, nl).trim();
                    l.lineBuf.delete(0, nl + 1);
                    if (!frame.isEmpty()) handleFrame(l, frame);
                }
                if (l.lineBuf.length() > 65536) l.lineBuf.setLength(0); // garbage guard
            }
        }
    }

    private void handleFrame(Link l, String frame) {
        String t = ClusterProtocol.type(frame);
        JSONObject o = ClusterProtocol.parse(frame);
        if (o == null || t.isEmpty()) return;
        switch (t) {
            case "hello": {
                l.deviceId = o.optString("id", l.device.getDeviceName());
                ClusterBus.SlaveState s = ClusterBus.get().slave(l.deviceId);
                s.model = o.optString("model", "?");
                s.android = o.optString("android", "?");
                s.totalRamMb = o.optInt("ram", 0);
                s.cores = o.optInt("cores", 0);
                s.linkUp = true;
                s.lastSeenMs = System.currentTimeMillis();
                Log.i(ClusterProtocol.TAG, "master: HELLO from " + s.model + " (" + l.deviceId + ")");
                ClusterBus.get().notifyDevicesChanged();
                break;
            }
            case "hb": {
                String id = o.optString("id", l.deviceId);
                if (id == null) break;
                ClusterBus.SlaveState s = ClusterBus.get().slave(id);
                s.freeRamMb = o.optInt("free", 0);
                s.battery = o.optInt("batt", -1);
                s.charging = o.optBoolean("chg", false);
                s.servers.clear();
                JSONArray arr = o.optJSONArray("servers");
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject so = arr.optJSONObject(i);
                    if (so != null) s.servers.add(so);
                }
                s.lastHeartbeatMs = s.lastSeenMs = System.currentTimeMillis();
                if (!s.linkUp) { s.linkUp = true; l.deviceId = id; }
                ClusterBus.get().notifyDevicesChanged();
                break;
            }
            case "pong": {
                long rtt = System.currentTimeMillis() - o.optLong("ts", System.currentTimeMillis());
                if (l.deviceId != null) {
                    ClusterBus.get().slave(l.deviceId).rttMs = rtt;
                    ClusterBus.get().notifyDevicesChanged();
                }
                break;
            }
            case "ack":
            default:
                ClusterBus.get().notifyFrame(o);
        }
    }

    /** Send a command to a slave (found by deviceId). Returns false if not connected. */
    public boolean sendCmd(String deviceId, String action, String serverId) {
        return sendCmd(deviceId, action, serverId, ++pingCounter);
    }

    /** Same with a caller-chosen ref (e.g. for console ack correlation). */
    public boolean sendCmd(String deviceId, String action, String serverId, int ref) {
        for (Link l : links.values()) {
            if (l.up && deviceId != null && deviceId.equals(l.deviceId)) {
                l.send(ClusterProtocol.cmd(ref, action, serverId));
                return true;
            }
        }
        return false;
    }
}
