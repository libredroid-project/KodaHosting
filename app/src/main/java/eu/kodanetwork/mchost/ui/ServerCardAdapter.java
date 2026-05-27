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
import eu.kodanetwork.mchost.service.TermuxServerService;

public class ServerCardAdapter extends RecyclerView.Adapter<ServerCardAdapter.VH> {

    public interface Click { void on(ServerInstance s); }

    private List<ServerInstance> data = new ArrayList<>();
    private final Context ctx;
    private final Click click;
    private TermuxServerService svc;

    public ServerCardAdapter(Context c, Click cl) { ctx = c; click = cl; }
    public void setData(List<ServerInstance> d)   { data = new ArrayList<>(d); notifyDataSetChanged(); }
    public void setService(TermuxServerService s)  { svc = s; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_server, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int i) { h.bind(data.get(i)); }
    @Override public int  getItemCount() { return data.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView name, badge, type, ver, ram, addr, players;
        View dot;
        android.widget.ProgressBar pbRam;
        com.google.android.material.button.MaterialButton btnAction;
        android.widget.ImageView ivServerIcon;
        TextView tvBattery;

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
        }

        void bind(ServerInstance s) {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("koda_settings", Context.MODE_PRIVATE);
            boolean isLight = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(ctx);
            boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));

            // Maintain the new modern dark theme design unless explicitly in light mode
            if (isLight) {
                itemView.setBackgroundColor(0xFFF3F4F6); // light mode fallback
            }

            name.setText(s.getName());
            if (type != null) type.setText(s.getType().name());

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
                    // Quick async load to prevent ANR on Main Thread
                    ivServerIcon.setTag(iconFile.getAbsolutePath());
                    new Thread(() -> {
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (iconFile.getAbsolutePath().equals(ivServerIcon.getTag())) {
                                ivServerIcon.setImageBitmap(bitmap);
                            }
                        });
                    }).start();
                } else {
                    ivServerIcon.setImageResource(R.mipmap.ic_launcher);
                }
            }

            addr.setText(s.getJoinAddress());

            ServerInstance.State st = s.state;
            String label; int dotDrw; int textCol;
            switch (st) {
                case ONLINE: label="ONLINE"; dotDrw=R.drawable.dot_online; textCol=0xFF00E676; break;
                case STARTING: label="STARTING"; dotDrw=R.drawable.dot_warn; textCol=0xFFFFCC00; break;
                case STOPPING: label="STOPPING"; dotDrw=R.drawable.dot_warn; textCol=0xFFFF8800; break;
                case CRASHED: label="CRASHED"; dotDrw=R.drawable.dot_err; textCol=0xFFFF3333; break;
                case INSTALLING: label="INSTALL"; dotDrw=R.drawable.dot_warn; textCol=0xFF2277FF; break;
                default: label="OFFLINE"; dotDrw=R.drawable.dot_offline; textCol=0xFF8A8A9A; break; // dim gray for offline
            }
            badge.setText(label);
            badge.setTextColor(textCol);
            
            String verText = s.getType().name() + " • ";
            if (s.state == ServerInstance.State.ONLINE) {
                verText += s.onlinePlayerNames.size() + " players";
            } else {
                verText += "Offline";
            }
            if (ver != null) ver.setText(verText);

            if (btnAction != null) {
                if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                    btnAction.setText("STOP");
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF5252));
                    btnAction.setTextColor(0xFF111111);
                } else {
                    btnAction.setText("START");
                    btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2B221E));
                    btnAction.setTextColor(0xFFF0F0F0);
                }
                btnAction.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(v.getContext(), 50);
                    if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                        android.content.Intent i = new android.content.Intent(ctx, TermuxServerService.class);
                        i.setAction(TermuxServerService.ACTION_STOP);
                        i.putExtra(TermuxServerService.EXTRA_ID, s.getId());
                        if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
                        else ctx.startService(i);
                    } else {
                        if (eu.kodanetwork.mchost.security.PraetorSystem.checkRamForStart(ctx, s)) {
                            s.state = ServerInstance.State.STARTING;
                            eu.kodanetwork.mchost.model.ServerRepo.get(ctx).update(s);
                            
                            android.content.Intent i = new android.content.Intent(ctx, TermuxServerService.class);
                            i.setAction(TermuxServerService.ACTION_START);
                            i.putExtra(TermuxServerService.EXTRA_ID, s.getId());
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
        }
    }
}
