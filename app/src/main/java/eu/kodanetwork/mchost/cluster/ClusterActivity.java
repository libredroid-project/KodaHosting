package eu.kodanetwork.mchost.cluster;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fleet dashboard (master side): live cards for every slave device with
 * remote START/STOP/RESTART and a console tail sheet. Programmatic UI in the
 * app's card style; refreshes from ClusterBus heartbeats.
 */
public class ClusterActivity extends AppCompatActivity implements ClusterBus.Listener {

    private LinearLayout list;
    private TextView tvHeader;
    private final Map<Integer, Runnable> ackWaiters = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1714);
        int pad = dp(16);
        root.setPadding(pad, dp(24), pad, pad);

        TextView title = new TextView(this);
        title.setText(de ? "GERÄTE-CLUSTER" : "DEVICE CLUSTER");
        title.setTextColor(0xFFFF6B00);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.06f);
        root.addView(title);

        tvHeader = new TextView(this);
        tvHeader.setTextColor(0xFFB7AE9F);
        tvHeader.setTextSize(12);
        tvHeader.setPadding(0, dp(4), 0, dp(12));
        root.addView(tvHeader);

        Button btnScan = new Button(this);
        btnScan.setText(de ? "Geräte suchen" : "Scan devices");
        btnScan.setTextColor(0xFFF0F0F0);
        btnScan.setBackgroundColor(0xFF26221E);
        btnScan.setOnClickListener(v -> {
            ClusterMaster.get(this).scan();
            Toast.makeText(this, de ? "Suche…" : "Scanning…", Toast.LENGTH_SHORT).show();
        });
        root.addView(btnScan, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ClusterBus.get().addListener(this);
        rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ClusterBus.get().removeListener(this);
        synchronized (ackWaiters) { ackWaiters.clear(); }
    }

    @Override
    public void onDevicesChanged() {
        runOnUiThread(this::rebuild);
    }

    @Override
    public void onFrame(JSONObject frame) {
        if (frame == null || !"ack".equals(frame.optString("t"))) return;
        int ref = frame.optInt("ref", -1);
        Runnable r;
        synchronized (ackWaiters) { r = ackWaiters.remove(ref); }
        if (r != null) runOnUiThread(r);
        // console ack carries data — store for the dialog
        if (frame.has("data")) {
            consoleData = frame.optString("data", "");
            Runnable cr;
            synchronized (ackWaiters) { cr = ackWaiters.remove(ref); }
            if (cr != null) runOnUiThread(cr);
        }
    }

    private String consoleData = null;

    // ── UI build ─────────────────────────────────────────────────────

    private void rebuild() {
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");
        List<ClusterBus.SlaveState> slaves = ClusterBus.get().slaves();
        list.removeAllViews();

        int totalFree = 0, serversOnline = 0;
        for (ClusterBus.SlaveState s : slaves) {
            totalFree += s.freeRamMb;
            for (JSONObject o : s.servers) if ("ONLINE".equals(o.optString("state"))) serversOnline++;
        }
        tvHeader.setText((de ? "Geräte: " : "Devices: ") + slaves.size()
                + " · " + (de ? "frei: " : "free: ") + (totalFree / 1024) + " GB"
                + " · " + (de ? "Server online: " : "servers online: ") + serversOnline);

        if (slaves.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(de ? "Keine Geräte verbunden. Auf dem anderen Handy \"KodaCluster Slave anbieten\" aktivieren und per USB-Kabel (OTG) verbinden, dann suchen."
                    : "No devices connected. Enable \"offer as cluster slave\" on the other phone, connect via USB (OTG), then scan.");
            empty.setTextColor(0xFF8A8A9A);
            empty.setTextSize(13);
            empty.setPadding(0, dp(20), 0, dp(20));
            list.addView(empty);
            return;
        }

        for (ClusterBus.SlaveState s : slaves) {
            list.addView(deviceCard(s, de));
        }
    }

    private View deviceCard(ClusterBus.SlaveState s, boolean de) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF241C18);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, s.linkUp ? 0xFF69781D : 0xFFE8442E);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        // header row: model + link state
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvModel = new TextView(this);
        tvModel.setText(s.model + "  ·  Android " + s.android);
        tvModel.setTextColor(0xFFF0F0F0);
        tvModel.setTextSize(15);
        tvModel.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(tvModel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvLink = new TextView(this);
        tvLink.setText(s.linkUp ? (de ? "VERBUNDEN" : "CONNECTED") + (s.rttMs >= 0 ? " · " + s.rttMs + "ms" : "")
                : (de ? "OFFLINE" : "OFFLINE"));
        tvLink.setTextColor(s.linkUp ? 0xFF69781D : 0xFFE8442E);
        tvLink.setTextSize(11);
        tvLink.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(tvLink);
        card.addView(head);

        // specs row
        int ramPct = s.totalRamMb > 0 ? (s.freeRamMb * 100 / s.totalRamMb) : 0;
        TextView tvSpecs = new TextView(this);
        tvSpecs.setText("RAM " + (s.freeRamMb / 1024) + "/" + (s.totalRamMb / 1024) + " GB " + (de ? "frei" : "free")
                + " (" + ramPct + "%)" + (s.battery >= 0 ? " · 🔋 " + s.battery + "%" + (s.charging ? " ⚡" : "") : "")
                + " · " + s.cores + (de ? " Kerne" : " cores"));
        tvSpecs.setTextColor(0xFFB7AE9F);
        tvSpecs.setTextSize(12);
        tvSpecs.setPadding(0, dp(6), 0, dp(4));
        card.addView(tvSpecs);

        // RAM bar
        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(ramPct);
        bar.getProgressDrawable().setColorFilter(
                ramPct > 40 ? 0xFF3DBE3D : ramPct > 15 ? 0xFFFFCC00 : 0xFFE8442E,
                android.graphics.PorterDuff.Mode.SRC_IN);
        card.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        // servers
        if (!s.servers.isEmpty()) {
            TextView tvSrvTitle = new TextView(this);
            tvSrvTitle.setText(de ? "SERVER AUF DIESEM GERÄT" : "SERVERS ON THIS DEVICE");
            tvSrvTitle.setTextColor(0xFFFF6B00);
            tvSrvTitle.setTextSize(10);
            tvSrvTitle.setLetterSpacing(0.08f);
            tvSrvTitle.setPadding(0, dp(12), 0, dp(4));
            card.addView(tvSrvTitle);

            for (JSONObject o : s.servers) {
                card.addView(serverRow(s, o, de));
            }
        }
        return card;
    }

    private View serverRow(ClusterBus.SlaveState s, JSONObject o, boolean de) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));

        String state = o.optString("state", "?");
        int stateCol = "ONLINE".equals(state) ? 0xFF69781D
                : "CRASHED".equals(state) ? 0xFFE8442E : 0xFF8A8A9A;
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(stateCol);
        dot.setTextSize(12);
        row.addView(dot);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(8), 0, dp(8), 0);
        TextView tvName = new TextView(this);
        tvName.setText(o.optString("name", "?"));
        tvName.setTextColor(0xFFF0F0F0);
        tvName.setTextSize(14);
        info.addView(tvName);
        TextView tvMeta = new TextView(this);
        tvMeta.setText(o.optString("type", "") + " · " + state + " · "
                + o.optInt("players", 0) + (de ? " Spieler" : " players"));
        tvMeta.setTextColor(0xFF8A8A9A);
        tvMeta.setTextSize(11);
        info.addView(tvMeta);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean online = "ONLINE".equals(state) || "STARTING".equals(state);
        TextView btnPower = new TextView(this);
        btnPower.setText(online ? "STOP" : "START");
        btnPower.setTextColor(0xFF111111);
        btnPower.setTextSize(11);
        btnPower.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable pb = new android.graphics.drawable.GradientDrawable();
        pb.setColor(online ? 0xFFE8442E : 0xFFFF6B00);
        pb.setCornerRadius(dp(8));
        btnPower.setBackground(pb);
        btnPower.setPadding(dp(12), dp(8), dp(12), dp(8));
        final String sid = o.optString("id", "");
        btnPower.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
            boolean ok = ClusterMaster.get(this).sendCmd(s.deviceId, online ? "STOP" : "START", sid);
            Toast.makeText(this, ok ? (online ? "Stop gesendet" : "Start gesendet")
                    : (de ? "Gerät nicht verbunden" : "device not connected"), Toast.LENGTH_SHORT).show();
        });
        row.addView(btnPower);

        TextView btnLog = new TextView(this);
        btnLog.setText(de ? "LOG" : "LOG");
        btnLog.setTextColor(0xFFF0F0F0);
        btnLog.setTextSize(11);
        btnLog.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable lb = new android.graphics.drawable.GradientDrawable();
        lb.setColor(0xFF26221E);
        lb.setCornerRadius(dp(8));
        btnLog.setBackground(lb);
        btnLog.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnLog.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
            requestConsole(s.deviceId, sid);
        });
        row.addView(btnLog);

        return row;
    }

    // ── console sheet ────────────────────────────────────────────────

    private void requestConsole(String deviceId, String sid) {
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");
        // one ref for the log data (handled in onFrame)
        int ref = System.currentTimeMillis() > 0 ? (int) (System.currentTimeMillis() % 100000) : 1;
        consoleData = null;
        Dialog d = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1714);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView tvTitle = new TextView(this);
        tvTitle.setText((de ? "KONSOLE — " : "CONSOLE — ") + sid.substring(0, Math.min(8, sid.length())));
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);
        TextView tvLog = new TextView(this);
        tvLog.setTextColor(0xFFCCCCCC);
        tvLog.setTextSize(10);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sc = new ScrollView(this);
        sc.addView(tvLog);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new Button(this);
        close.setText("CLOSE");
        close.setTextColor(0xFFF0F0F0);
        close.setBackgroundColor(0xFF2A2A33);
        close.setOnClickListener(v -> d.dismiss());
        root.addView(close);
        d.setContentView(root);
        d.getWindow().setLayout(-1, -2);
        tvLog.setText(de ? "Lade Log…" : "Loading log…");

        synchronized (ackWaiters) { ackWaiters.put(ref, () -> tvLog.setText(consoleData != null ? consoleData : "—")); }
        if (!ClusterMaster.get(this).sendCmd(deviceId, "GETLOG", sid, ref)) {
            tvLog.setText(de ? "Gerät nicht verbunden" : "device not connected");
        }
        d.show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
