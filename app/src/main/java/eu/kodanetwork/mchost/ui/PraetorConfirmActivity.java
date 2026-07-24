package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import java.io.File;
import java.util.List;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;

public class PraetorConfirmActivity extends Activity {

    private String action;
    private Button btnConfirm;
    private TextView tvNetworkStatus;
    private TextView tvCountdown;
    private CountDownTimer timer;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF0A0000);
            getWindow().setNavigationBarColor(0xFF0A0000);
        }

        setContentView(R.layout.activity_praetor_confirm);

        action = getIntent().getStringExtra("action");
        if (action == null) action = "";

        TextView tvExplanation = findViewById(R.id.tv_explanation);
        if ("revoke_tos".equals(action)) {
            tvExplanation.setText("You are about to revoke your acceptance of the Terms of Service. This will permanently delete all your servers and require you to accept the terms again to continue using KodaHosting. This action cannot be undone.");
        } else if ("delete_account".equals(action)) {
            tvExplanation.setText(getString(R.string.praetor_delete_account_explanation));
        } else {
            tvExplanation.setText("You are about to permanently delete all servers from this device. All world data, plugins, and configurations will be completely erased. This action cannot be undone.");
        }

        TextView tvTitle = findViewById(R.id.tv_praetor_title);
        if (tvTitle != null) {
            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
            tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }

        android.view.View warningIcon = findViewById(R.id.tv_warning_icon);
        if (warningIcon != null) {
            android.view.animation.AlphaAnimation blink = new android.view.animation.AlphaAnimation(1.0f, 0.0f);
            blink.setDuration(300);
            blink.setRepeatMode(android.view.animation.Animation.REVERSE);
            blink.setRepeatCount(android.view.animation.Animation.INFINITE);
            warningIcon.startAnimation(blink);
        }

        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 200);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 300);
        }, 300);

        btnConfirm = findViewById(R.id.btn_confirm);
        Button btnCancel = findViewById(R.id.btn_cancel);
        tvNetworkStatus = findViewById(R.id.tv_network_status);
        tvCountdown = findViewById(R.id.tv_countdown);

        btnCancel.setOnClickListener(v -> finish());
        
        btnConfirm.setOnClickListener(v -> {
            if (timer != null) timer.cancel();

            if (!isConnected) {
                Toast.makeText(this, "Internet connection required to proceed.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show loading spinner, hide buttons and countdown
            findViewById(R.id.progress_loading).setVisibility(View.VISIBLE);
            findViewById(R.id.layout_buttons).setVisibility(View.GONE);
            tvCountdown.setVisibility(View.GONE);

            executeDestructiveAction();
        });

        checkNetworkAndStartTimer();
    }

    private void checkNetworkAndStartTimer() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if (isConnected) {
            tvNetworkStatus.setText("CONNECTED");
            tvNetworkStatus.setTextColor(0xFF00FF00); // Green
        } else {
            tvNetworkStatus.setText("DISCONNECTED - WAITING FOR NETWORK");
            tvNetworkStatus.setTextColor(0xFFFF3333); // Red
        }

        timer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(millisUntilFinished / 1000));
                
                // Re-check internet
                NetworkInfo net = cm.getActiveNetworkInfo();
                isConnected = net != null && net.isConnectedOrConnecting();
                if (isConnected) {
                    tvNetworkStatus.setText("CONNECTED");
                    tvNetworkStatus.setTextColor(0xFF00FF00);
                } else {
                    tvNetworkStatus.setText("DISCONNECTED");
                    tvNetworkStatus.setTextColor(0xFFFF3333);
                }
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                if (isConnected) {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("EXECUTE");
                } else {
                    tvCountdown.setText("AWAITING NETWORK");
                    // Check again in 1 second if still waiting
                    new android.os.Handler().postDelayed(() -> checkNetworkAndStartTimer(), 1000);
                }
            }
        }.start();
    }

    private void executeDestructiveAction() {
        ServerRepo repo = ServerRepo.get(this);
        List<ServerInstance> allServers = repo.all();
        
        new Thread(() -> {
            String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
            android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(PraetorConfirmActivity.this);
            String token = prefs.getString("koda_session_token", null);
            String authHeader = token != null ? "Bearer " + token : "Bearer " + anonKey;

            for (ServerInstance srv : new java.util.ArrayList<>(allServers)) {
                // 1. Kill service FIRST to ensure the server process is stopped while it's still in the repo
                if (srv.state != ServerInstance.State.OFFLINE) {
                    android.content.Intent intent = new android.content.Intent(PraetorConfirmActivity.this, eu.kodanetwork.mchost.service.KodaServerService.class);
                    intent.setAction(eu.kodanetwork.mchost.service.KodaServerService.ACTION_KILL);
                    intent.putExtra(eu.kodanetwork.mchost.service.KodaServerService.EXTRA_ID, srv.getId());
                    startService(intent);
                    int retries = 0;
                    while (srv.state != ServerInstance.State.OFFLINE && retries < 20) {
                        try { Thread.sleep(500); } catch (Exception ignored) {}
                        retries++;
                    }
                }

                // 2. Delete DNS Link
                try {
                    if (srv.getSubdomain() != null && !srv.getSubdomain().isEmpty()) {
                        new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(PraetorConfirmActivity.this)
                            .deleteDnsLink("", srv.getSubdomain(), srv.getBaseDomain());
                    }
                } catch (Exception e) {
                    android.util.Log.e("PraetorConfirm", "Failed to delete DNS link", e);
                }
                
                // 3. PATCH to change host and server_version to hide it from lobby
                try {
                    java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_admin_patch_server").openConnection();
                    patchConn.setRequestMethod("POST");
                    patchConn.setRequestProperty("apikey", anonKey);
                    patchConn.setRequestProperty("Authorization", authHeader);
                    patchConn.setRequestProperty("Content-Type", "application/json");
                    patchConn.setDoOutput(true);
                    String adminAppUuid = eu.kodanetwork.mchost.App.getPrefs(PraetorConfirmActivity.this).getString("app_uuid", "");
                    String jsonPatch = "{\"p_admin_app_uuid\": \"" + adminAppUuid + "\", \"p_target_host\": \"" + srv.getSubdomain() + "\", \"p_payload\": {\"host\": \"deleted_" + srv.getSubdomain() + "\", \"server_version\": \"DELETED\"}}";
                    patchConn.getOutputStream().write(jsonPatch.getBytes());
                    patchConn.getResponseCode();
                } catch (Exception e) {
                    android.util.Log.e("PraetorConfirm", "Failed to patch server", e);
                }
                
                // 4. Try to actually DELETE the row (now targets the deleted_ host)
                try {
                    java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?host=eq.deleted_" + srv.getSubdomain());
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("DELETE");
                    conn.setRequestProperty("apikey", anonKey);
                    conn.setRequestProperty("Authorization", authHeader);
                    conn.getResponseCode();
                } catch (Exception e) {
                    android.util.Log.e("PraetorConfirm", "Failed to delete server", e);
                }

                // 5. Delete Local Files
                File dir = new File(srv.getServerDir());
                if (dir.exists()) {
                    deleteRecursive(dir, true);
                }
                repo.delete(srv.getId());
            }

            runOnUiThread(() -> {
                if ("delete_account".equals(action)) {
                    eu.kodanetwork.mchost.network.supabase.SupabaseAuth.deleteUser(PraetorConfirmActivity.this, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                eu.kodanetwork.mchost.network.supabase.SupabaseAuth.logout(PraetorConfirmActivity.this);
                                Toast.makeText(PraetorConfirmActivity.this, "Account deleted.", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(PraetorConfirmActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                        }
                        @Override public void onError(String msg) {
                            runOnUiThread(() -> {
                                Toast.makeText(PraetorConfirmActivity.this, "Error: " + msg, Toast.LENGTH_LONG).show();
                                finish();
                            });
                        }
                    });
                    return;
                }

                Toast.makeText(PraetorConfirmActivity.this, "All servers deleted.", Toast.LENGTH_SHORT).show();

                if ("revoke_tos".equals(action)) {
                    eu.kodanetwork.mchost.App.getPrefs(PraetorConfirmActivity.this).edit().putBoolean("tos_accepted", false).apply();
                    
                    Intent intent = new Intent(PraetorConfirmActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(PraetorConfirmActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
                
                finish();
            });
        }).start();
    }

    private void deleteRecursive(File fileOrDirectory, boolean deleteRoot) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child, true);
                }
            }
        }
        if (deleteRoot) {
            fileOrDirectory.delete();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
