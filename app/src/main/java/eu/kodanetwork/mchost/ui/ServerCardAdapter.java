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
        }

        void bind(ServerInstance s) {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("koda_settings", Context.MODE_PRIVATE);
            boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));

            if (isCyber) {
                itemView.setBackgroundResource(R.drawable.cell_cyber_bg);
                name.setTypeface(Typeface.MONOSPACE);
                name.setLetterSpacing(0.1f);
                int themeColor = 0xFFFF6A00;
                try {
                    if (s.getThemeColor() != null && !s.getThemeColor().isEmpty()) {
                        themeColor = Color.parseColor(s.getThemeColor());
                    }
                } catch (Exception ignored) {}
                name.setTextColor(themeColor);
                addr.setTypeface(Typeface.MONOSPACE);
                badge.setTypeface(Typeface.MONOSPACE);
                badge.setLetterSpacing(0.2f);
                type.setTypeface(Typeface.MONOSPACE);
                type.setTextColor(themeColor);
            } else {
                itemView.setBackgroundResource(R.drawable.cell_bg);
                name.setTextColor(Color.WHITE);
                type.setTextColor(0xFF444444);
                name.setTypeface(Typeface.DEFAULT);
                type.setTypeface(Typeface.DEFAULT);
            }

            name.setText(s.getName());
            type.setText(s.getType().name());

            String ramText = s.getRamMB() + "MB";
            if (s.state == ServerInstance.State.ONLINE && s.ramUsageMB > 0) {
                ramText = s.ramUsageMB + "MB / " + s.getRamMB() + "MB";
            }
            ram.setText(ramText);
            addr.setText(s.getJoinAddress());

            ServerInstance.State st = s.state;
            String label; int dotDrw; int textCol;
            switch (st) {
                case ONLINE: label="ONLINE"; dotDrw=R.drawable.dot_online; textCol=0xFF00E676; break;
                case STARTING: label="STARTING"; dotDrw=R.drawable.dot_warn; textCol=0xFFFFCC00; break;
                case STOPPING: label="STOPPING"; dotDrw=R.drawable.dot_warn; textCol=0xFFFF8800; break;
                case CRASHED: label="CRASHED"; dotDrw=R.drawable.dot_err; textCol=0xFFFF3333; break;
                case INSTALLING: label="INSTALL"; dotDrw=R.drawable.dot_warn; textCol=0xFF2277FF; break;
                default: label="OFFLINE"; dotDrw=R.drawable.dot_offline; textCol=0xFF2A2A2A; break;
            }
            badge.setText(label);
            badge.setTextColor(textCol);
            
            if (s.state == ServerInstance.State.ONLINE) {
                players.setText(s.onlinePlayerNames.size() + " / " + s.getMaxPlayers());
                players.setVisibility(View.VISIBLE);
                if (isCyber) players.setTypeface(Typeface.MONOSPACE);
            } else {
                players.setVisibility(View.GONE);
            }

            dot.setBackground(ctx.getDrawable(dotDrw));
            itemView.setOnClickListener(v -> click.on(s));
        }
    }
}
