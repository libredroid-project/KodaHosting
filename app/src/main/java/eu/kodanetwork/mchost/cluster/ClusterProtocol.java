package eu.kodanetwork.mchost.cluster;

import android.content.Context;
import android.os.BatteryManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;

/**
 * KodaCluster wire protocol: newline-delimited JSON frames over the USB link.
 *
 * hello   slave→master  once after link is up (device identity + specs)
 * hb      slave→master  every 5s (free RAM, battery, server list)
 * cmd     master→slave  remote action: START | STOP | RESTART | GETLOG
 * ack     slave→master  result for a cmd (matched by ref)
 * ping    both          link RTT measurement
 * pong    both          reply to ping
 * bye     both          graceful disconnect
 */
public final class ClusterProtocol {

    public static final int VERSION = 1;
    public static final String TAG = "KodaCluster";

    private ClusterProtocol() {}

    // ── frame builders ───────────────────────────────────────────────

    public static String hello(Context ctx, String deviceId) {
        JSONObject o = base("hello");
        try {
            o.put("id", deviceId);
            o.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            o.put("android", android.os.Build.VERSION.RELEASE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            o.put("ram", mi.totalMem / 1048576);
            o.put("cores", Runtime.getRuntime().availableProcessors());
            o.put("app", eu.kodanetwork.mchost.BuildConfig.VERSION_NAME);
        } catch (Exception ignored) {}
        return o.toString();
    }

    public static String heartbeat(Context ctx, String deviceId) {
        JSONObject o = base("hb");
        try {
            o.put("id", deviceId);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            o.put("free", mi.availMem / 1048576);
            android.content.Intent sticky = ctx.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (sticky != null) {
                int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) o.put("batt", level * 100 / scale);
                int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                o.put("chg", status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL);
            }
            JSONArray arr = new JSONArray();
            for (ServerInstance s : ServerRepo.get(ctx).all()) {
                JSONObject so = new JSONObject();
                so.put("id", s.getId());
                so.put("name", s.getName());
                so.put("type", s.getType().name());
                so.put("state", s.state.name());
                so.put("players", s.onlinePlayers);
                arr.put(so);
            }
            o.put("servers", arr);
        } catch (Exception ignored) {}
        return o.toString();
    }

    public static String cmd(int ref, String action, String serverId) {
        JSONObject o = base("cmd");
        try {
            o.put("ref", ref);
            o.put("action", action);
            o.put("sid", serverId == null ? "" : serverId);
        } catch (Exception ignored) {}
        return o.toString();
    }

    public static String ack(int ref, boolean ok, String data) {
        JSONObject o = base("ack");
        try {
            o.put("ref", ref);
            o.put("ok", ok);
            if (data != null) o.put("data", data.length() > 8000 ? data.substring(data.length() - 8000) : data);
        } catch (Exception ignored) {}
        return o.toString();
    }

    public static String ping() {
        return base("ping").toString();
    }

    public static String bye() {
        return base("bye").toString();
    }

    private static JSONObject base(String type) {
        JSONObject o = new JSONObject();
        try {
            o.put("v", VERSION);
            o.put("t", type);
            o.put("ts", System.currentTimeMillis());
        } catch (Exception ignored) {}
        return o;
    }

    // ── parsing helpers ──────────────────────────────────────────────

    public static String type(String frame) {
        try { return new JSONObject(frame).optString("t", ""); } catch (Exception e) { return ""; }
    }

    public static JSONObject parse(String frame) {
        try { return new JSONObject(frame); } catch (Exception e) { return null; }
    }
}
