package eu.kodanetwork.mchost.ui;

import android.content.Context;
import android.graphics.Color;
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
            ver     = v.findViewById(R.id.tv_ver);      // hidden
            ram     = v.findViewById(R.id.tv_ram);
            addr    = v.findViewById(R.id.tv_addr);
            players = v.findViewById(R.id.tv_players);  // hidden
            dot     = v.findViewById(R.id.dot);
        }

        void bind(ServerInstance s) {
            name.setText(s.getName());
            type.setText(s.getType().name());
            ram.setText(s.getRamMB() + "MB");
            addr.setText(s.getJoinAddress());

            ServerInstance.State st = s.state;
            String label; int dotDrw; int textCol;
            switch (st) {
                case ONLINE:
                    label="ONLINE";    dotDrw=R.drawable.dot_online; textCol=Color.parseColor("#00E676"); break;
                case STARTING:
                    label="STARTING";  dotDrw=R.drawable.dot_warn;   textCol=Color.parseColor("#FFCC00"); break;
                case STOPPING:
                    label="STOPPING";  dotDrw=R.drawable.dot_warn;   textCol=Color.parseColor("#FF8800"); break;
                case CRASHED:
                    label="CRASHED";   dotDrw=R.drawable.dot_err;    textCol=Color.parseColor("#FF3333"); break;
                case INSTALLING:
                    label="INSTALL";   dotDrw=R.drawable.dot_warn;   textCol=Color.parseColor("#2277FF"); break;
                default:
                    label="OFFLINE";   dotDrw=R.drawable.dot_offline; textCol=Color.parseColor("#2A2A2A"); break;
            }
            badge.setText(label);
            badge.setTextColor(textCol);
            dot.setBackground(ctx.getDrawable(dotDrw));
            itemView.setOnClickListener(v -> click.on(s));
        }
    }
}
