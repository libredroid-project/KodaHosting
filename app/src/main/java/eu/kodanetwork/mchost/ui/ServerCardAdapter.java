package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.service.KodaServerService;

public class ServerCardAdapter extends RecyclerView.Adapter<ServerCardAdapter.VH> {

    public interface Click { void on(ServerInstance s); }

    private static final java.util.Map<String, android.graphics.Bitmap> iconCache = new java.util.concurrent.ConcurrentHashMap<>();

    private List<ServerInstance> data = new ArrayList<>();
    private final Context ctx;
    private final Click click;
    private KodaServerService svc;

    public ServerCardAdapter(Context c, Click cl) { ctx = c; click = cl; }
    public void setData(List<ServerInstance> d)   { data = new ArrayList<>(d); notifyDataSetChanged(); }
    public void setService(KodaServerService s)  { svc = s; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        boolean terminalEnabled = eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false);
        boolean m3Enabled = eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(ctx);
        // Redesigned cards are the LIGHT default; in dark mode they need the
        // "Neue Server-Karten" developer toggle (not part of the release default)
        boolean lightMode = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(ctx);
        boolean v2CardsDark = eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_v2_cards_dark", false);
        int layoutId;
        if (terminalEnabled) {
            layoutId = R.layout.item_server_terminal;
        } else if (m3Enabled) {
            layoutId = R.layout.item_server_m3;
        } else if (lightMode || v2CardsDark) {
            layoutId = R.layout.item_server;
        } else {
            layoutId = R.layout.item_server_legacy;
        }
        View v = LayoutInflater.from(ctx).inflate(layoutId, p, false);
        // Tablet/landscape quick-win: cap the card width and center it instead of
        // stretching full-width across big screens
        int screenDp = Math.round(p.getResources().getDisplayMetrics().widthPixels
                / p.getResources().getDisplayMetrics().density);
        if (screenDp >= 900) {
            int maxPx = Math.round(600 * p.getResources().getDisplayMetrics().density);
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.width = maxPx;
            lp.leftMargin = lp.rightMargin =
                    Math.max(0, (p.getWidth() == 0 ? p.getResources().getDisplayMetrics().widthPixels : p.getWidth()) / 2 - maxPx / 2);
            v.setLayoutParams(lp);
        }
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) { h.bind(data.get(i)); }
    @Override public int  getItemCount() { return data.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView name, badge, type, ver, ram, addr, players;
        View dot, statusLine;
        android.widget.ProgressBar pbRam;
        com.google.android.material.button.MaterialButton btnAction;
        android.widget.ImageView ivServerIcon;
        TextView tvBattery, tvUptime;
        // v2 card elements (item_server.xml — null in terminal/m3 variants)
        TextView chipVersion;
        eu.kodanetwork.mchost.util.MiniChartView chartTps, chartRam;
        android.widget.LinearLayout layoutAvatars;
        View statusPill, cardFace;

        VH(View v) {
            super(v);
            name    = v.findViewById(R.id.tv_name);
            badge   = v.findViewById(R.id.tv_badge);
            type    = v.findViewById(R.id.tv_type);
            ver     = v.findViewById(R.id.tv_ver);
            ram     = v.findViewById(R.id.tv_ram);
            addr    = v.findViewById(R.id.tv_addr);
            players = v.findViewById(R.id.tv_players);
            dot     = v.findViewById(R.id.dot);
            pbRam   = v.findViewById(R.id.pb_ram);
            btnAction = v.findViewById(R.id.btn_action);
            ivServerIcon = v.findViewById(R.id.iv_server_icon);
            tvBattery = v.findViewById(R.id.tv_battery);
            statusLine = v.findViewById(R.id.status_line);
            tvUptime = v.findViewById(R.id.tv_uptime);
            chipVersion = v.findViewById(R.id.chip_version);
            chartTps = v.findViewById(R.id.chart_tps);
            chartRam = v.findViewById(R.id.chart_ram);
            layoutAvatars = v.findViewById(R.id.layout_avatars);
            statusPill = dot != null ? (View) dot.getParent() : null;
            cardFace = v.findViewById(R.id.container_main);
        }

        /** Colored avatar circle with a letter (v2 card footer). */
        private android.view.View makeAvatar(String label, int bgColor, boolean wide) {
            float d = ctx.getResources().getDisplayMetrics().density;
            android.widget.TextView av = new android.widget.TextView(ctx);
            av.setText(label);
            av.setTextColor(0xFFF7F3EB);
            av.setTextSize(10);
            av.setTypeface(null, android.graphics.Typeface.BOLD);
            av.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(bgColor);
            bg.setStroke((int) d, 0xFFF7F3EB); // ring so overlapping circles separate
            av.setBackground(bg);
            int size = (int) (28 * d);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
            lp.rightMargin = (int) (-6 * d); // overlap like the design reference
            av.setLayoutParams(lp);
            if (wide) av.setMinWidth(size);
            return av;
        }

        private void tintCardStroke(int statusColor) {
            if (statusLine == null) return;
            statusLine.setBackgroundColor(statusColor);
        }

        private void bindUptime(ServerInstance s) {
            if (tvUptime == null) return;
            tvUptime.setText(s.getFormattedUptime());
            if (s.state == ServerInstance.State.ONLINE && s.startTime > 0) {
                tvUptime.setTextColor(0xFFAAFFAA); // light green
            } else {
                tvUptime.setTextColor(0xFFF0F0F0); // white-ish, just like the dashboard
            }
        }

        void bind(ServerInstance s) {
            android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ctx);
            boolean isLight = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(ctx);
            boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));

            // Maintain the new modern dark theme design unless explicitly in light mode
            if (isLight) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(0xFFF5E1F2); // Noticeable light purple/pink box for card
                gd.setStroke(3, 0xFFFF8C38); // Orange stroke
                gd.setCornerRadius(24f);
                int pL = itemView.getPaddingLeft(), pT = itemView.getPaddingTop(), pR = itemView.getPaddingRight(), pB = itemView.getPaddingBottom();
                itemView.setBackground(gd);
                itemView.setPadding(pL, pT, pR, pB);

                // Ensure the card has margins so they don't touch
                if (itemView.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams p = (android.view.ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
                    p.setMargins(16, 16, 16, 16);
                    itemView.setLayoutParams(p);
                }
            }

            int themeColor = android.graphics.Color.parseColor(prefs.getString("theme_color", "#FF6B00"));

            name.setText(s.getName());
            if (type != null) type.setText(s.getType().name());

            // === DATABASE-SPECIFIC CARD ===
            if (s.isDatabase()) {
                // Hide MC-specific views
                if (ivServerIcon != null) ivServerIcon.setVisibility(View.GONE);
                if (addr != null) addr.setText("Port: " + s.getPort());
                // Hide address pill parent (the LinearLayout with pill_address_bg)
                View addrPill = addr != null ? (View) addr.getParent() : null;
                if (addrPill != null) {
                    // Replace pill content with port info
                    addr.setText(":" + s.getPort());
                }

                // Hide TPS
                TextView tvTps = itemView.findViewById(R.id.tv_tps);
                if (tvTps != null) {
                    View tpsParent = (View) tvTps.getParent();
                    if (tpsParent != null) tpsParent.setVisibility(View.GONE);
                }

                // Change RAM label to "DB MEMORY"
                // The RAM label is the first child TextView in the stats container
                // Replace ver text with user + masked password
                String masked = "••••••••";
                String dbInfo = s.getType().name() + " • " + s.getDbUsername() + " / " + masked;
                if (ver != null) ver.setText(dbInfo);

                // RAM still useful for databases
                int ramTotal = s.getRamMB();
                int ramUsed = s.ramUsageMB;
                String ramText = String.format(java.util.Locale.US, "%.1fGB / %.1fGB", 0f, ramTotal / 1024f);
                if (s.state == ServerInstance.State.ONLINE && ramUsed > 0) {
                    ramText = String.format(java.util.Locale.US, "%.1fGB / %.1fGB", ramUsed / 1024f, ramTotal / 1024f);
                }
                ram.setText(ramText);

                if (pbRam != null) {
                    pbRam.setMax(ramTotal);
                    pbRam.setProgress((ramUsed > 0 && s.state == ServerInstance.State.ONLINE) ? ramUsed : 0);
                }

                // Battery/CPU still useful
                android.widget.ProgressBar pbBattery = itemView.findViewById(R.id.pb_battery);
                if (tvBattery != null) {
                    if (s.state == ServerInstance.State.ONLINE && ramTotal > 0) {
                        int fakeCpu = Math.min(100, Math.max(5, (ramUsed * 100) / ramTotal));
                        tvBattery.setText(fakeCpu + "%");
                        if (pbBattery != null) pbBattery.setProgress(fakeCpu);
                    } else {
                        tvBattery.setText("0%");
                        if (pbBattery != null) pbBattery.setProgress(0);
                    }
                }

                // Status badge
                ServerInstance.State st = s.state;
                String label; int dotDrw; int textCol;
                switch (st) {
                    case ONLINE: label=ctx.getString(R.string.status_online); dotDrw=R.drawable.dot_online; textCol=0xFF69781D; break;
                    case STARTING: label=ctx.getString(R.string.status_starting); dotDrw=R.drawable.dot_warn; textCol=0xFFFFCC00; break;
                    case STOPPING: label=ctx.getString(R.string.status_stopping); dotDrw=R.drawable.dot_warn; textCol=0xFFFF8800; break;
                    case CRASHED: label=ctx.getString(R.string.status_crashed); dotDrw=R.drawable.dot_err; textCol=0xFFFF3333; break;
                    default: label=ctx.getString(R.string.status_offline); dotDrw=R.drawable.dot_offline; textCol=0xFF555555; break; // Grey for offline
                }
                badge.setText(label);
                badge.setTextColor(textCol);
                dot.setBackground(ctx.getDrawable(dotDrw));
                tintCardStroke(textCol);

                // Start/Stop button
                if (btnAction != null) {
                    btnAction.setTag(R.id.tag_themed, "BLOCKED");
                    if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                        btnAction.setText(ctx.getString(R.string.stop));
                        if (eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false)) {
                            btnAction.setBackgroundResource(R.drawable.bg_mc_button_red);
                            btnAction.setBackgroundTintList(null);
                            btnAction.setTextColor(0xFF000000);
                        } else {
                            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF5252));
                            btnAction.setTextColor(0xFF111111);
                        }
                    } else {
                        btnAction.setText(ctx.getString(R.string.start));
                        if (eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false)) {
                            btnAction.setBackgroundResource(R.drawable.bg_mc_button_orange);
                            btnAction.setBackgroundTintList(null);
                            btnAction.setTextColor(0xFF000000);
                        } else {
                            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B00));
                            btnAction.setTextColor(0xFF111111);
                        }
                    }
                    btnAction.setOnClickListener(v -> {
                        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(v.getContext(), 50);
                        if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                            android.content.Intent intent = new android.content.Intent(ctx, KodaServerService.class);
                            intent.setAction(KodaServerService.ACTION_STOP);
                            intent.putExtra(KodaServerService.EXTRA_ID, s.getId());
                            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent);
                            else ctx.startService(intent);
                        } else {
                            s.state = ServerInstance.State.STARTING;
                            eu.kodanetwork.mchost.model.ServerRepo.get(ctx).update(s);
                            android.content.Intent intent = new android.content.Intent(ctx, KodaServerService.class);
                            intent.setAction(KodaServerService.ACTION_START);
                            intent.putExtra(KodaServerService.EXTRA_ID, s.getId());
                            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent);
                            else ctx.startService(intent);
                        }
                    });
                }

                View.OnClickListener cardClick = v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(v.getContext(), 40);
                    click.on(s);
                };
                itemView.setOnClickListener(cardClick);
                if (eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(ctx)) {
                    eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(itemView);
                    if (btnAction != null) {
                        eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(btnAction);
                    }
                }
                eu.kodanetwork.mchost.util.ThemeHelper.applyToView(itemView, isLight, themeColor, isCyber);
                bindUptime(s);
                return; // Done for databases
            }

            // === NORMAL SERVER CARD (below) ===
            // Reset views that might have been hidden by database card
            if (ivServerIcon != null) ivServerIcon.setVisibility(View.VISIBLE);
            TextView tvTpsReset = itemView.findViewById(R.id.tv_tps);
            if (tvTpsReset != null) {
                View tpsParent = (View) tvTpsReset.getParent();
                if (tpsParent != null) tpsParent.setVisibility(View.VISIBLE);
            }

            // Real RAM info
            int ramTotal = s.getRamMB();
            int ramUsed = s.ramUsageMB;
            String ramText = String.format(java.util.Locale.US, "%.1fGB / %.1fGB", 0f, ramTotal / 1024f);
            if (s.state == ServerInstance.State.ONLINE && ramUsed > 0) {
                ramText = String.format(java.util.Locale.US, "%.1fGB / %.1fGB", ramUsed / 1024f, ramTotal / 1024f);
            }
            ram.setText(ramText);
            
            if (pbRam != null) {
                pbRam.setMax(ramTotal);
                pbRam.setProgress((ramUsed > 0 && s.state == ServerInstance.State.ONLINE) ? ramUsed : 0);
            }

            // Server Battery/Resource info
            android.widget.ProgressBar pbBattery = itemView.findViewById(R.id.pb_battery);
            if (tvBattery != null) {
                if (s.state == ServerInstance.State.ONLINE && ramTotal > 0) {
                    // CPU approximation based on RAM load and some noise
                    int fakeCpu = Math.min(100, Math.max(5, (ramUsed * 100) / ramTotal));
                    tvBattery.setText(fakeCpu + "%");
                    if (pbBattery != null) pbBattery.setProgress(fakeCpu);
                } else {
                    tvBattery.setText("0%");
                    if (pbBattery != null) pbBattery.setProgress(0);
                }
            }

            TextView tvTps = itemView.findViewById(R.id.tv_tps);
            if (tvTps != null) {
                if (s.state == ServerInstance.State.ONLINE) {
                    float tps = s.currentTps;
                    tvTps.setText(String.format(java.util.Locale.US, "%.1f", tps));
                    if (tps >= 18.0f) tvTps.setTextColor(0xFF00E676);
                    else if (tps >= 15.0f) tvTps.setTextColor(0xFFFFCC00);
                    else tvTps.setTextColor(0xFFFF3333);
                } else {
                    tvTps.setText("---");
                    tvTps.setTextColor(0xFF8A8A9A);
                }
            }

            // Server Icon
            if (ivServerIcon != null) {
                java.io.File iconFile = new java.io.File(s.getServerDir(), "server-icon.png");
                if (iconFile.exists()) {
                    String path = iconFile.getAbsolutePath();
                    if (iconCache.containsKey(path)) {
                        ivServerIcon.setImageBitmap(iconCache.get(path));
                        ivServerIcon.setTag(null);
                    } else {
                        ivServerIcon.setTag(path);
                        new Thread(() -> {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
                            if (bitmap != null) iconCache.put(path, bitmap);
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (path.equals(ivServerIcon.getTag())) {
                                    ivServerIcon.setImageBitmap(bitmap);
                                }
                            });
                        }).start();
                    }
                } else {
                    ivServerIcon.setImageResource(R.mipmap.ic_launcher);
                    if (isLight) {
                        // In light mode, the background becomes LIGHT_CELL (pinkish), so we tint the logo to be solid orange to stand out
                        ivServerIcon.setColorFilter(0xFFFF8C38, android.graphics.PorterDuff.Mode.SRC_IN);
                    } else {
                        ivServerIcon.clearColorFilter();
                    }
                }
            }

            addr.setText(s.getJoinAddress());

            // ── v2 card elements: chip_version only exists in the redesigned item_server.xml ──
            if (chipVersion != null) {
            if (chipVersion != null) {
                    String shortVer = s.getVersion() == null ? "" : s.getVersion();
                    chipVersion.setText((s.getType().name() + " " + shortVer).toUpperCase().trim());
                    chipVersion.setTag(R.id.tag_themed, "BLOCKED");
                    chipVersion.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            isLight ? 0xFFE9E2D4 : 0xFF26221E));
                    chipVersion.setTextColor(isLight ? 0xFF5C5344 : 0xFFB7AE9F);
                }
                if (cardFace != null) {
                    // keep the espresso elements outside ThemeHelper's BFS recoloring and
                    // tint the card face per mode ourselves
                    cardFace.setTag(R.id.tag_themed, "BLOCKED");
                    android.graphics.drawable.GradientDrawable face = new android.graphics.drawable.GradientDrawable();
                    face.setCornerRadius(ctx.getResources().getDisplayMetrics().density * 20);
                    face.setColor(isLight ? 0xFFF7F3EB : 0xFF1D1714);
                    face.setStroke((int)(ctx.getResources().getDisplayMetrics().density + 0.5f),
                            isLight ? 0xFFE5DECF : 0xFF2A2A30);
                    cardFace.setBackground(face);
                }
                if (statusPill != null) {
                    statusPill.setTag(R.id.tag_themed, "BLOCKED");
                    statusPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            isLight ? 0xFFE9E2D4 : 0xFF26221E));
                }
                View tileTps = itemView.findViewById(R.id.tile_tps);
                View tileRam = itemView.findViewById(R.id.tile_ram);
                if (tileTps != null) tileTps.setTag(R.id.tag_themed, "BLOCKED");
                if (tileRam != null) tileRam.setTag(R.id.tag_themed, "BLOCKED");
                if (chartTps != null) {
                    chartTps.setTag(R.id.tag_themed, "BLOCKED");
                    chartTps.configure(eu.kodanetwork.mchost.util.MiniChartView.MODE_BARS, 20f,
                            0xFFF2EFE9, isLight ? 0xFF69781D : 0xFFFF6B00);
                    float tps = s.state == ServerInstance.State.ONLINE ? s.currentTps : 0f;
                    chartTps.push(s.getId(), Math.max(0f, Math.min(1f, tps / 20f)));
                }
                if (chartRam != null) {
                    chartRam.setTag(R.id.tag_themed, "BLOCKED");
                    chartRam.configure(eu.kodanetwork.mchost.util.MiniChartView.MODE_LINE, 1f,
                            0xFFF2EFE9, isLight ? 0xFF69781D : 0xFFFF6B00);
                    float used = s.getRamMB() > 0
                            ? (s.state == ServerInstance.State.ONLINE ? s.ramUsageMB : 0) / (float) s.getRamMB()
                            : 0f;
                    chartRam.push(s.getId(), Math.max(0f, Math.min(1f, used)));
                }
                if (layoutAvatars != null) {
                    layoutAvatars.setTag(R.id.tag_themed, "BLOCKED");
                    layoutAvatars.removeAllViews();
                    java.util.List<String> names = s.state == ServerInstance.State.ONLINE && s.onlinePlayerNames != null
                            ? s.onlinePlayerNames : java.util.Collections.emptyList();
                    int[] palette = {0xFFE8913A, 0xFF69781D, 0xFFC96F1E, 0xFF8A9BCE};
                    int shown = Math.min(3, names.size());
                    for (int ai = 0; ai < shown; ai++) {
                        String letter = names.get(ai).isEmpty() ? "?" : names.get(ai).substring(0, 1).toUpperCase();
                        layoutAvatars.addView(makeAvatar(letter, palette[ai % palette.length], false));
                    }
                    if (names.size() > 3) {
                        layoutAvatars.addView(makeAvatar("+" + (names.size() - 3), 0xFF241207, true));
                    }
                    if (names.isEmpty()) {
                        TextView none = new TextView(ctx);
                        none.setText(s.getMaxPlayers() + " " + ctx.getString(R.string.players));
                        none.setTextColor(isLight ? 0xFF8A8075 : 0xFF8A8A9A);
                        none.setTextSize(12);
                        none.setFontFeatureSettings("");
                        none.setTypeface(name.getTypeface());
                        layoutAvatars.addView(none);
                    }
                }


            }

            ServerInstance.State st = s.state;
            String label; int dotDrw; int textCol;
            switch (st) {
                case ONLINE: label=ctx.getString(R.string.status_online); dotDrw=R.drawable.dot_online; textCol=0xFF69781D; break;
                case STARTING: label=ctx.getString(R.string.status_starting); dotDrw=R.drawable.dot_warn; textCol=0xFFFFCC00; break;
                case STOPPING: label=ctx.getString(R.string.status_stopping); dotDrw=R.drawable.dot_warn; textCol=0xFFFF8800; break;
                case RESTARTING: label=ctx.getString(R.string.status_restarting); dotDrw=R.drawable.dot_warn; textCol=0xFFFF8800; break;
                case SETTING_UP: label=ctx.getString(R.string.status_setting_up); dotDrw=R.drawable.dot_warn; textCol=0xFF9C27B0; break;
                case CRASHED: label=ctx.getString(R.string.status_crashed); dotDrw=R.drawable.dot_err; textCol=0xFFFF3333; break;
                case INSTALLING: label=ctx.getString(R.string.status_installing); dotDrw=R.drawable.dot_warn; textCol=0xFF2277FF; break;
                case HIBERNATED: label=ctx.getString(R.string.hibernate); dotDrw=R.drawable.dot_offline; textCol=0xFF44AAFF; break;
                default: label=ctx.getString(R.string.status_offline); dotDrw=R.drawable.dot_offline; textCol=0xFF555555; break; // Grey for offline
            }
            badge.setText(label);
            badge.setTextColor(textCol);
            tintCardStroke(textCol);
            
            String verText = s.getType().name();
            if (!s.isDatabase()) {
                verText += " • ";
                if (s.state == ServerInstance.State.ONLINE) {
                    verText += s.onlinePlayerNames.size() + " " + ctx.getString(R.string.players);
                } else {
                    verText += s.getVersion();
                }
            }
            if (ver != null) ver.setText(verText);

            if (btnAction != null) {
                btnAction.setTag(R.id.tag_themed, "BLOCKED");
                if (st == ServerInstance.State.HIBERNATED) {
                    btnAction.setText(ctx.getString(R.string.wake_up));
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD9701A));
                    btnAction.setTextColor(0xFFF7F3EB);
                } else if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                    btnAction.setText(ctx.getString(R.string.stop));
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF241207));
                    btnAction.setTextColor(0xFFF7F3EB);
                } else {
                    btnAction.setText(ctx.getString(R.string.start));
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD9701A));
                    btnAction.setTextColor(0xFFF7F3EB);
                }
                btnAction.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(v.getContext(), 50);
                    if (st == ServerInstance.State.HIBERNATED) {
                        click.on(s);
                    } else if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                        android.content.Intent i = new android.content.Intent(ctx, KodaServerService.class);
                        i.setAction(KodaServerService.ACTION_STOP);
                        i.putExtra(KodaServerService.EXTRA_ID, s.getId());
                        if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                        else ctx.startService(i);
                    } else {
                        if (eu.kodanetwork.mchost.security.PraetorSystem.checkRamForStart(ctx, s)) {
                            s.state = ServerInstance.State.STARTING;
                            eu.kodanetwork.mchost.model.ServerRepo.get(ctx).update(s);
                            
                            android.content.Intent i = new android.content.Intent(ctx, KodaServerService.class);
                            i.setAction(KodaServerService.ACTION_START);
                            i.putExtra(KodaServerService.EXTRA_ID, s.getId());
                            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                            else ctx.startService(i);
                        }
                    }
                });
            }

            dot.setBackground(ctx.getDrawable(dotDrw));
            
            View.OnClickListener cardClick = v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(v.getContext(), 40);
                click.on(s);
            };
            
            itemView.setOnClickListener(cardClick);
            if (eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(ctx)) {
                if (btnAction != null) {
                    eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(btnAction);
                }
            }

            bindUptime(s);

            // Apply theme LAST so that dynamic colors on btnAction are properly styled for light mode
            eu.kodanetwork.mchost.util.ThemeHelper.applyToView(itemView, isLight, themeColor, isCyber);

            // LiquidGlass Tilt Effect
            if (prefs.getBoolean("dev_liquid_glass", false)) {
                TiltEffectHelper tiltHelper = (TiltEffectHelper) itemView.getTag(R.id.container_main);
                if (tiltHelper == null) {
                    tiltHelper = new TiltEffectHelper(ctx, itemView, true);
                    tiltHelper.register();
                    itemView.setTag(R.id.container_main, tiltHelper);
                }
            } else {
                TiltEffectHelper tiltHelper = (TiltEffectHelper) itemView.getTag(R.id.container_main);
                if (tiltHelper != null) {
                    tiltHelper.unregister();
                    itemView.setTag(R.id.container_main, null);
                    itemView.setRotationX(0f);
                    itemView.setRotationY(0f);
                }
            }
        }
    }
}
