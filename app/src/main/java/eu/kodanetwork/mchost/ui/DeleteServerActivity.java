package eu.kodanetwork.mchost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.service.KodaServerService;
import eu.kodanetwork.mchost.util.ZipUtil;

public class DeleteServerActivity extends AppCompatActivity {
    
    private String serverId;
    private ServerInstance server;
    private ServerRepo repo;
    
    private TextView tvMsg;
    private TextView tvSubMsg;
    private ProgressBar pbDelete;
    private Button btnRetry;
    private LinearLayout llActions;
    private Button btnExport;
    private Button btnPermanent;
    private Handler handler;
    private android.animation.ObjectAnimator blobAnimator;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_server);
        
        tvMsg = findViewById(R.id.tv_delete_msg);
        tvSubMsg = findViewById(R.id.tv_delete_submsg);
        pbDelete = findViewById(R.id.pb_delete);
        btnRetry = findViewById(R.id.btn_retry_delete);
        llActions = findViewById(R.id.ll_delete_actions);
        btnExport = findViewById(R.id.btn_export_delete);
        btnPermanent = findViewById(R.id.btn_permanent_delete);
        
        handler = new Handler(Looper.getMainLooper());
        
        android.view.View btnClose = findViewById(R.id.btn_close_update);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }
        
        android.view.View blob = findViewById(R.id.update_light_blob);
        if (blob != null) {
            blobAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                    blob,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f, 1.0f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f, 1.0f)
            );
            blobAnimator.setDuration(2500);
            blobAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            blobAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            blobAnimator.start();
        }
        
        serverId = getIntent().getStringExtra("SERVER_ID");
        if (serverId == null) {
            finish();
            return;
        }
        
        repo = ServerRepo.get(this);
        server = repo.byId(serverId);
        if (server == null) {
            finish();
            return;
        }
        
        btnRetry.setOnClickListener(v -> {
            btnRetry.setVisibility(View.GONE);
            pbDelete.setVisibility(View.VISIBLE);
            startDeletionProcess();
        });
        
        btnExport.setOnClickListener(v -> exportAndDelete());
        btnPermanent.setOnClickListener(v -> permanentlyDelete());
        
        startDeletionProcess();
    }
    
    private void setMsg(String msg, String sub) {
        handler.post(() -> {
            tvMsg.setText(msg);
            if (sub != null) tvSubMsg.setText(sub);
        });
    }
    
    private void showError(String msg, String sub) {
        handler.post(() -> {
            tvMsg.setText(msg);
            if (sub != null) tvSubMsg.setText(sub);
            pbDelete.setVisibility(View.GONE);
            btnRetry.setVisibility(View.VISIBLE);
        });
    }
    
    private void showFinalActions() {
        handler.post(() -> {
            tvMsg.setText("Server disconnected");
            tvSubMsg.setText("DNS and Database records removed. What do you want to do with the local files?");
            pbDelete.setVisibility(View.GONE);
            llActions.setVisibility(View.VISIBLE);
        });
    }
    
    private void startDeletionProcess() {
        new Thread(() -> {
            try {
                // 1. Kill server if running
                if (server.state != ServerInstance.State.OFFLINE && server.state != ServerInstance.State.CRASHED && server.state != ServerInstance.State.HIBERNATED) {
                    setMsg(getString(R.string.delete_server_stopping), getString(R.string.delete_server_stopping_sub));
                    Intent intent = new Intent(this, KodaServerService.class);
                    intent.setAction(KodaServerService.ACTION_KILL);
                    intent.putExtra(KodaServerService.EXTRA_ID, server.getId());
                    startService(intent);
                    
                    int retries = 0;
                    while (server.state != ServerInstance.State.OFFLINE && server.state != ServerInstance.State.CRASHED && retries < 20) {
                        Thread.sleep(500);
                        server = repo.byId(serverId);
                        retries++;
                    }
                }
                
                String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
                String token = prefs.getString("koda_session_token", null);
                if (token != null && token.trim().isEmpty()) token = null;
                String authHeader = token != null ? "Bearer " + token : "Bearer " + anonKey;
                
                // 2. Delete DNS Link
                if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
                    setMsg(getString(R.string.delete_server_dns), getString(R.string.delete_server_dns_sub));
                    try {
                        new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this)
                            .deleteDnsLink("", server.getSubdomain(), server.getBaseDomain());
                    } catch (Exception e) {
                        String errMsg = e.getMessage();
                        if (errMsg != null && errMsg.contains("429")) {
                            showError("DNS Deletion Failed", "Rate Limit reached. Please try again later.");
                        } else {
                            showError("DNS Deletion Failed", errMsg != null ? errMsg : "Unknown error");
                        }
                        return; // Stop here and wait for retry
                    }
                }
                
                // 3. Mark as deleted in Supabase (Do NOT delete row)
                setMsg(getString(R.string.delete_server_db), getString(R.string.delete_server_db_sub));
                try {
                    java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server").openConnection();
                    patchConn.setRequestMethod("POST");
                    patchConn.setRequestProperty("apikey", anonKey);
                    patchConn.setRequestProperty("Authorization", authHeader);
                    patchConn.setRequestProperty("Content-Type", "application/json");
                    patchConn.setDoOutput(true);
                    
                    String appUuid = prefs.getString("app_uuid", "unknown");
                    String jsonPatch = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + server.getSubdomain() + "\", \"p_payload\": {\"host\": \"deleted_" + server.getSubdomain() + "\", \"server_version\": \"DELETED\"}}";
                    patchConn.getOutputStream().write(jsonPatch.getBytes());
                    int responseCode = patchConn.getResponseCode();
                    
                    if (responseCode == 401) {
                        // Token might be expired, try to refresh
                        if (eu.kodanetwork.mchost.network.supabase.SupabaseAuth.refreshTokenSync(this)) {
                            // Token refreshed successfully, try again
                            token = prefs.getString("koda_session_token", null);
                            authHeader = token != null ? "Bearer " + token : "Bearer " + anonKey;
                            
                            patchConn = (java.net.HttpURLConnection) new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server").openConnection();
                            patchConn.setRequestMethod("POST");
                            patchConn.setRequestProperty("apikey", anonKey);
                            patchConn.setRequestProperty("Authorization", authHeader);
                            patchConn.setRequestProperty("Content-Type", "application/json");
                            patchConn.setDoOutput(true);
                            patchConn.getOutputStream().write(jsonPatch.getBytes());
                            responseCode = patchConn.getResponseCode();
                        }
                    }
                    
                    if (responseCode >= 400) {
                        showError(getString(R.string.delete_server_db_failed), "Failed to update server status (Code " + responseCode + ")");
                        return;
                    }
                } catch (Exception e) {
                    showError(getString(R.string.delete_server_db_failed), e.getMessage());
                    return;
                }
                
                // All network operations succeeded
                showFinalActions();
                
            } catch (Exception e) {
                e.printStackTrace();
                showError("Error", e.getMessage());
            }
        }).start();
    }
    
    private void exportAndDelete() {
        llActions.setVisibility(View.GONE);
        pbDelete.setVisibility(View.VISIBLE);
        setMsg(getString(R.string.delete_server_exporting), getString(R.string.delete_server_exporting_sub));
        
        new Thread(() -> {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File exportZip = new File(downloadsDir, server.getName() + "_export.zip");
                
                ZipUtil.zipDirectory(new File(server.getServerDir()), exportZip);
                
                handler.post(() -> Toast.makeText(this, "Exported to Downloads: " + exportZip.getName(), Toast.LENGTH_LONG).show());
                
                setMsg(getString(R.string.delete_server_cleaning), getString(R.string.delete_server_cleaning_sub));
                deleteRecursively(new File(server.getServerDir()));
                
                handler.post(() -> {
                    repo.delete(server.getId());
                    if (blobAnimator != null) blobAnimator.cancel();
                    Intent homeIntent = new Intent(DeleteServerActivity.this, MainActivity.class);
                    homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(homeIntent);
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                showError("Export Failed", e.getMessage());
            }
        }).start();
    }
    
    private void permanentlyDelete() {
        llActions.setVisibility(View.GONE);
        pbDelete.setVisibility(View.VISIBLE);
        setMsg(getString(R.string.delete_server_permanent), getString(R.string.delete_server_permanent_sub));
        
        new Thread(() -> {
            deleteRecursively(new File(server.getServerDir()));
            handler.post(() -> {
                repo.delete(server.getId());
                if (blobAnimator != null) blobAnimator.cancel();
                Intent homeIntent = new Intent(DeleteServerActivity.this, MainActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
                finish();
            });
        }).start();
    }
    
    private long lastUpdate = 0;
    
    private void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        
        long now = System.currentTimeMillis();
        if (now - lastUpdate > 30) {
            lastUpdate = now;
            final String fileName = fileOrDirectory.getName();
            handler.post(() -> {
                if (tvSubMsg != null) {
                    tvSubMsg.setText("..." + fileName);
                }
            });
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {}
        }
        
        fileOrDirectory.delete();
    }
}
