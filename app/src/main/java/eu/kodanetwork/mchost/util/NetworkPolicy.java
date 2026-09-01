package eu.kodanetwork.mchost.util;

import android.content.Context;
import eu.kodanetwork.mchost.App;
import android.net.TrafficStats;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Network budget system. Rules are configured per server, usage is measured
 * APP-WIDE (all servers share one UID — TrafficStats cannot split per server),
 * so a rule effectively says: "when the app has used X on mobile/wifi in
 * period Y, do Z to THIS server".
 *
 * Usage attribution: every meter tick reads the system UID counters and books
 * the delta onto the currently active network type (mobile vs wifi). Daily
 * snapshots persist across reboots (UID counters reset at boot).
 */
public class NetworkPolicy {

    public static final String ACTION_WARN = "warn";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_BLOCK = "block";

    public static class Rule {
        public String id;
        public long limitBytes;
        public int periodDays;          // 1 = day, 7 = week, 30 = month
        public boolean mobile, wifi;
        public String action;           // warn | stop | block
        public boolean paused;
    }

    public static class Usage {
        public long mobileBytes, wifiBytes;   // this period so far
        public long periodStartMs;
    }

    // ── rules persistence (per server) ──────────────────────────────

    public static List<Rule> getRules(Context ctx, String serverId) {
        List<Rule> out = new ArrayList<>();
        try {
            String raw = App.getPrefs(ctx).getString("net_rules_" + serverId, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Rule r = new Rule();
                r.id = o.optString("id", UUID.randomUUID().toString());
                r.limitBytes = o.getLong("limit");
                r.periodDays = o.optInt("period", 7);
                r.mobile = o.optBoolean("mobile", true);
                r.wifi = o.optBoolean("wifi", true);
                r.action = o.optString("action", ACTION_WARN);
                r.paused = o.optBoolean("paused", false);
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveRules(Context ctx, String serverId, List<Rule> rules) {
        try {
            JSONArray arr = new JSONArray();
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("limit", r.limitBytes);
                o.put("period", r.periodDays);
                o.put("mobile", r.mobile);
                o.put("wifi", r.wifi);
                o.put("action", r.action);
                o.put("paused", r.paused);
                arr.put(o);
            }
            App.getPrefs(ctx).edit().putString("net_rules_" + serverId, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ── usage metering ───────────────────────────────────────────────

    private static long lastUidBytes = -1;
    private static boolean lastWasMobile = false;

    /**
     * Call every few seconds while servers run. Books the UID traffic delta
     * onto the active network type. Period windows auto-roll.
     */
    public static void meterTick(Context ctx, boolean onMobile) {
        try {
            long now = System.currentTimeMillis();
            long uid = TrafficStats.getUidRxBytes(android.os.Process.myUid())
                    + TrafficStats.getUidTxBytes(android.os.Process.myUid());
            if (lastUidBytes < 0) { lastUidBytes = uid; lastWasMobile = onMobile; return; }
            long delta = uid - lastUidBytes;
            lastUidBytes = uid;
            if (delta <= 0) return;

            long prev = App.getPrefs(ctx).getLong("net_acc_bytes", 0);
            App.getPrefs(ctx).edit().putLong("net_acc_bytes", prev + delta).apply();

            String key = onMobile ? "net_acc_mobile" : "net_acc_wifi";
            // attribute delta: if network type switched mid-tick, book on the new type
            long prevType = App.getPrefs(ctx).getLong(key, 0);
            App.getPrefs(ctx).edit().putLong(key, prevType + delta)
                    .putLong("net_last_tick", now).apply();
            lastWasMobile = onMobile;
        } catch (Exception ignored) {}
    }

    /** Period-scoped usage: rolls the counters when the window expired. */
    public static Usage usageFor(Context ctx, int periodDays) {
        Usage u = new Usage();
        long now = System.currentTimeMillis();
        long winMs = periodDays * 24L * 3600L * 1000L;
        long start = App.getPrefs(ctx).getLong("net_period_start_" + periodDays, 0);
        if (start == 0 || now - start >= winMs) {
            // window (re)starts: snapshot current totals as the baseline
            start = now;
            App.getPrefs(ctx).edit()
                    .putLong("net_period_start_" + periodDays, now)
                    .putLong("net_base_mobile_" + periodDays, App.getPrefs(ctx).getLong("net_acc_mobile", 0))
                    .putLong("net_base_wifi_" + periodDays, App.getPrefs(ctx).getLong("net_acc_wifi", 0))
                    .apply();
        }
        u.periodStartMs = start;
        u.mobileBytes = App.getPrefs(ctx).getLong("net_acc_mobile", 0)
                - App.getPrefs(ctx).getLong("net_base_mobile_" + periodDays, 0);
        u.wifiBytes = App.getPrefs(ctx).getLong("net_acc_wifi", 0)
                - App.getPrefs(ctx).getLong("net_base_wifi_" + periodDays, 0);
        return u;
    }

    /**
     * Highest-severity decision across all rules of a server for the CURRENT
     * network type. Returns null when everything is fine.
     */
    public static Rule violatedRule(Context ctx, String serverId, boolean onMobile) {
        Rule worst = null;
        for (Rule r : getRules(ctx, serverId)) {
            if (r.paused) continue;
            if (!(onMobile ? r.mobile : r.wifi)) continue;
            Usage u = usageFor(ctx, r.periodDays);
            long used = onMobile ? u.mobileBytes : u.wifiBytes;
            if (used >= r.limitBytes) {
                if (worst == null || severity(r.action) > severity(worst.action)) worst = r;
            }
        }
        return worst;
    }

    /** 0..100 progress of the most-consumed matching rule (for the dashboard tile). */
    public static int progressPercent(Context ctx, String serverId, boolean onMobile) {
        int max = 0;
        for (Rule r : getRules(ctx, serverId)) {
            if (r.paused) continue;
            if (r.paused) continue;
            if (!(onMobile ? r.mobile : r.wifi)) continue;
            Usage u = usageFor(ctx, r.periodDays);
            long used = onMobile ? u.mobileBytes : u.wifiBytes;
            if (r.limitBytes <= 0) continue;
            max = Math.max(max, (int) Math.min(100, used * 100 / r.limitBytes));
        }
        return max;
    }

    public static int severity(String action) {
        if (ACTION_BLOCK.equals(action)) return 3;
        if (ACTION_STOP.equals(action)) return 2;
        if (ACTION_WARN.equals(action)) return 1;
        return 0;
    }

    /** Traffic-light color for a progress percent: green <70, yellow <85, orange <90, red >=90. */
    public static int colorForPercent(int pct) {
        if (pct >= 90) return 0xFFFF4444;
        if (pct >= 85) return 0xFFFF8C38;
        if (pct >= 70) return 0xFFFFCC00;
        return 0xFF3DBE3D;
    }

    public static String humanBytes(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f GB", b / 1073741824f);
        if (b >= 1024L * 1024) return String.format(java.util.Locale.US, "%.0f MB", b / 1048576f);
        return (b / 1024) + " KB";
    }
}
