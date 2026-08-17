package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.security.PraetorSecurity;
import eu.kodanetwork.mchost.util.HapticUtil;

public class PraetorAccountsActivity extends Activity {

    private String rowId;
    private String mcName;
    private final Map<String, CheckBox> permCheckboxes = new HashMap<>();
    
    // The 8 permissions
    private static final String[] PERMS = {
        "perm_start",
        "perm_stop",
        "perm_restart",
        "perm_settings",
        "perm_players",
        "perm_plugins",
        "perm_console",
        "perm_delete"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_praetor_accounts_m3 : R.layout.activity_praetor_accounts);

        rowId = getIntent().getStringExtra("extra_row_id");
        mcName = getIntent().getStringExtra("extra_mc_name");

        TextView tvAccountName = findViewById(R.id.tv_account_name);
        tvAccountName.setText("User: " + (mcName != null ? mcName : "Unknown"));

        LinearLayout llContainer = findViewById(R.id.ll_permissions_container);
        
        // Setup checkboxes
        for (String perm : PERMS) {
            CheckBox cb = new CheckBox(this);
            cb.setText(perm.toUpperCase().replace("PERM_", ""));
            cb.setTextColor(0xFFFFFFFF);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFF0000));
            android.graphics.Typeface kodaMono = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda_mono);
            if (kodaMono != null) cb.setTypeface(kodaMono);
            cb.setTextSize(16);
            cb.setPadding(0, 16, 0, 16);
            
            // Add a separator line
            View separator = new View(this);
            separator.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
            separator.setBackgroundColor(0xFF333333);
            
            llContainer.addView(cb);
            llContainer.addView(separator);
            
            permCheckboxes.put(perm, cb);
        }

        findViewById(R.id.btn_praetor_cancel).setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 80);
            finish();
        });

        findViewById(R.id.btn_praetor_save).setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 120);
            savePermissions();
        });

        loadPermissions();
    }

    private void loadPermissions() {
        if (rowId == null) return;
        new Thread(() -> {
            try {
                URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?select=permissions&id=eq." + rowId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey());

                if (conn.getResponseCode() == 200) {
                    java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
                    String resp = s.hasNext() ? s.next() : "";
                    org.json.JSONArray arr = new org.json.JSONArray(resp);
                    if (arr.length() > 0) {
                        JSONObject permsObj = arr.getJSONObject(0).optJSONObject("permissions");
                        if (permsObj != null) {
                            runOnUiThread(() -> {
                                for (String perm : PERMS) {
                                    if (permCheckboxes.containsKey(perm)) {
                                        permCheckboxes.get(perm).setChecked(permsObj.optBoolean(perm, false));
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void savePermissions() {
        if (rowId == null) return;
        
        Button btnSave = findViewById(R.id.btn_praetor_save);
        btnSave.setEnabled(false);
        btnSave.setText("UPLOADING...");

        new Thread(() -> {
            try {
                JSONObject permsObj = new JSONObject();
                for (String perm : PERMS) {
                    permsObj.put(perm, permCheckboxes.get(perm).isChecked());
                }
                
                JSONObject payload = new JSONObject();
                payload.put("permissions", permsObj);

                URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_admin_patch_user");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey()); // using anon key to bypass auth RLS because user might not be the owner
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String adminAppUuid = eu.kodanetwork.mchost.App.getPrefs(PraetorAccountsActivity.this).getString("app_uuid", "");
                String jsonBody = "{\"p_admin_app_uuid\": \"" + adminAppUuid + "\", \"p_target_id\": \"" + rowId + "\", \"p_payload\": " + payload.toString() + "}";
                conn.getOutputStream().write(jsonBody.getBytes());
                
                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200 || code == 204) {
                        Toast.makeText(this, "Permissions updated successfully.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to update permissions (Code " + code + ")", Toast.LENGTH_LONG).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("APPLY PROTOCOLS");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("APPLY PROTOCOLS");
                });
            }
        }).start();
    }
}
