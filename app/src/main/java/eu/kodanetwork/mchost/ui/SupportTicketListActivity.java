package eu.kodanetwork.mchost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.network.supabase.SupportApi;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class SupportTicketListActivity extends AppCompatActivity {

    private RecyclerView rvTickets;
    private TicketAdapter adapter;
    private List<JSONObject> ticketList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.apply(this);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_support_tickets_m3 : R.layout.activity_support_tickets);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvTickets = findViewById(R.id.rv_tickets);
        rvTickets.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TicketAdapter();
        rvTickets.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTickets();
    }

    private void loadTickets() {
        String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", null);
        String sessionToken = eu.kodanetwork.mchost.App.getPrefs(this).getString("koda_session_token", null);
        if (uuid == null) return;

        new Thread(() -> {
            try {
                JSONObject rpcObj = new JSONObject();
                rpcObj.put("p_reporter_uuid", uuid);
                String response = SupportApi.makeSupabaseRequest(
                        "rest/v1/rpc/rpc_get_tickets", "POST", rpcObj.toString(), sessionToken);
                JSONArray arr = new JSONArray(response);
                
                List<JSONObject> newTickets = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    newTickets.add(arr.getJSONObject(i));
                }

                runOnUiThread(() -> {
                    ticketList.clear();
                    ticketList.addAll(newTickets);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to load tickets", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private class TicketAdapter extends RecyclerView.Adapter<TicketAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_support_ticket, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject ticket = ticketList.get(position);
            try {
                String title = ticket.getString("title");
                String status = ticket.optString("status", "OPEN");
                String date = ticket.optString("created_at", "").split("T")[0];
                String id = ticket.getString("id");

                holder.tvTitle.setText(title);
                holder.tvStatus.setText(status);
                holder.tvDate.setText(date);

                if ("RESOLVED".equals(status)) {
                    holder.tvStatus.setTextColor(0xFF4CAF50); // Green
                } else {
                    holder.tvStatus.setTextColor(0xFFFF8C00); // Orange
                }

                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(SupportTicketListActivity.this, SupportChatActivity.class);
                    intent.putExtra("TICKET_ID", id);
                    intent.putExtra("TICKET_TITLE", title);
                    intent.putExtra("TICKET_STATUS", status);
                    startActivity(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return ticketList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvStatus, tvDate;
            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_ticket_title);
                tvStatus = itemView.findViewById(R.id.tv_ticket_status);
                tvDate = itemView.findViewById(R.id.tv_ticket_date);
            }
        }
    }
}
