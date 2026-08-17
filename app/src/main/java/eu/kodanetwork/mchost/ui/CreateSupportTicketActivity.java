package eu.kodanetwork.mchost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.network.supabase.SupportApi;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class CreateSupportTicketActivity extends AppCompatActivity {

    private String ticketType; // "BUG" or "SERVER_REPORT"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.apply(this);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_create_support_ticket_m3 : R.layout.activity_create_support_ticket);

        ticketType = getIntent().getStringExtra("TICKET_TYPE");
        if (ticketType == null) ticketType = "BUG";

        TextView tvTitle = findViewById(R.id.tv_title);
        EditText etReference = findViewById(R.id.et_reference);
        if (ticketType.equals("SERVER_REPORT")) {
            tvTitle.setText("Report Server");
            etReference.setHint("Name of the server");
        } else {
            tvTitle.setText("Report Bug");
            etReference.setHint("Short Summary of the bug");
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_create_ticket).setOnClickListener(v -> {
            String reference = etReference.getText().toString().trim();
            String message = ((EditText) findViewById(R.id.et_message)).getText().toString().trim();

            if (reference.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            createTicket(reference, message);
        });
    }

    private void createTicket(String reference, String message) {
        String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", null);
        String sessionToken = eu.kodanetwork.mchost.App.getPrefs(this).getString("koda_session_token", null);
        if (uuid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                if (ticketType.equals("SERVER_REPORT")) {
                    String checkResponse = SupportApi.makeSupabaseRequest(
                            "rest/v1/koda_servers?host=eq." + reference + "&select=id", "GET", null, sessionToken);
                    if (checkResponse == null || checkResponse.equals("[]")) {
                        runOnUiThread(() -> Toast.makeText(this, "Error: Server does not exist on KodaNetwork", Toast.LENGTH_SHORT).show());
                        return;
                    }
                }

                // 1. Create the ticket securely via RPC
                JSONObject rpcObj = new JSONObject();
                rpcObj.put("p_reporter_uuid", uuid);
                rpcObj.put("p_ticket_type", ticketType);
                if (ticketType.equals("SERVER_REPORT")) {
                    rpcObj.put("p_reference_id", reference);
                    rpcObj.put("p_title", "Server Report: " + reference);
                } else {
                    rpcObj.put("p_reference_id", "");
                    rpcObj.put("p_title", reference);
                }

                String ticketResponseStr = SupportApi.makeSupabaseRequest(
                        "rest/v1/rpc/rpc_create_ticket", "POST", rpcObj.toString(), sessionToken);
                
                // PostgREST returns a JSON string, e.g. "uuid"
                String ticketId = ticketResponseStr.replace("\"", "").trim();

                if (ticketId != null && !ticketId.isEmpty() && !ticketId.startsWith("{")) {
                    // 2. Create the first message
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("p_ticket_id", ticketId);
                    msgObj.put("p_sender_uuid", uuid);
                    msgObj.put("p_message", message);

                    SupportApi.makeSupabaseRequest(
                            "rest/v1/rpc/rpc_create_ticket_message", "POST", msgObj.toString(), sessionToken);

                    // 2b. Append Diagnostic Telemetry
                    String telemetry = getDiagnosticTelemetry(uuid, sessionToken);
                    JSONObject sysObj = new JSONObject();
                    sysObj.put("p_ticket_id", ticketId);
                    sysObj.put("p_sender_uuid", "system");
                    sysObj.put("p_message", telemetry);
                    SupportApi.makeSupabaseRequest(
                            "rest/v1/rpc/rpc_create_ticket_message", "POST", sysObj.toString(), sessionToken);

                    // 3. Open the chat activity
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Ticket created!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, SupportChatActivity.class);
                        intent.putExtra("TICKET_ID", ticketId);
                        intent.putExtra("TICKET_TITLE", ticketType.equals("SERVER_REPORT") ? "Server Report: " + reference : reference);
                        intent.putExtra("TICKET_STATUS", "OPEN");
                        startActivity(intent);
                        finish();
                    });
                } else {
                    throw new Exception("Failed to retrieve created ticket");
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error creating ticket", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String getDiagnosticTelemetry(String uuid, String sessionToken) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- SYSTEM TELEMETRY ---\n");
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        sb.append("OS Version: Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("App Version: ").append(eu.kodanetwork.mchost.BuildConfig.VERSION_NAME).append("\n");
        
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        sb.append("Language: ").append(prefs.getString("language", "system")).append("\n");
        sb.append("Theme Mode: ").append(prefs.getString("theme_mode", "dark")).append("\n");
        sb.append("Design Name: ").append(prefs.getString("app_theme", "modern")).append("\n");
        sb.append("Animations: ").append(prefs.getBoolean("animations_enabled", true)).append("\n");

        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMegs = 0;
        try {
            android.app.ActivityManager actManager = (android.app.ActivityManager) getSystemService(android.content.Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            totalMegs = memInfo.totalMem / 1048576L;
        } catch (Exception ignored) {}
        
        sb.append("Total System RAM: ").append(totalMegs).append(" MB\n");
        sb.append("App Max Memory: ").append(maxMem).append(" MB\n");
        sb.append("CPU Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        
        android.content.Intent batteryStatus = registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            sb.append("Battery: ").append((int)(level * 100 / (float)scale)).append("% (").append(isCharging ? "Charging" : "Discharging").append(")\n");
        }
        
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        String networkType = "Unknown";
        if (cm != null) {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                if (activeNetwork.getType() == android.net.ConnectivityManager.TYPE_WIFI) networkType = "WIFI";
                else if (activeNetwork.getType() == android.net.ConnectivityManager.TYPE_MOBILE) networkType = "MOBILE";
            }
        }
        sb.append("Network Type: ").append(networkType).append("\n");
        sb.append("Storage Free: ").append(getFilesDir().getFreeSpace() / (1024 * 1024)).append(" MB\n");

        try {
            String serversResponse = SupportApi.makeSupabaseRequest(
                "rest/v1/koda_servers?owner_app_uuid=eq." + uuid + "&select=host,server_version,is_banned", "GET", null, sessionToken);
            if (serversResponse != null && !serversResponse.equals("[]")) {
                sb.append("\nOwned Servers:\n");
                JSONArray arr = new JSONArray(serversResponse);
                for (int i=0; i<arr.length(); i++) {
                    JSONObject srv = arr.getJSONObject(i);
                    sb.append("- ").append(srv.optString("host", "Unknown"));
                    sb.append(" (Version: ").append(srv.optString("server_version", "N/A")).append(")");
                    if (srv.optBoolean("is_banned", false)) sb.append(" [BANNED]");
                    sb.append("\n");
                }
            } else {
                sb.append("\nOwned Servers: None");
            }
        } catch (Exception e) {
            sb.append("\nOwned Servers: Failed to fetch (" + e.getMessage() + ")");
        }
        
        return sb.toString();
    }
}
