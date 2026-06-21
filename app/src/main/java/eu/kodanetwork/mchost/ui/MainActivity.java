package eu.kodanetwork.mchost.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.service.KodaServerService;
import eu.kodanetwork.mchost.util.LocaleHelper;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "KodaNetwork";

    private RecyclerView rv;
    private View tvEmpty;
    private ServerCardAdapter adapter;
    private ServerRepo repo;
    private KodaServerService svc;
    private boolean bound = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((KodaServerService.LocalBinder) b).get();
            bound = true;
            adapter.setService(svc);
            svc.addStateCb((id, state) -> runOnUiThread(() -> adapter.notifyDataSetChanged()));
            refresh();
        }
        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            svc = null;
        }
    };

    private ActivityResultLauncher<String[]> permLauncher;
    private ActivityResultLauncher<Intent> storageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        lastTheme = prefs.getString("app_theme", "modern");
        lastThemeMode = prefs.getString("theme_mode", "dark");
        boolean isCyber = "cyber".equals(lastTheme);

        if (prefs.getString("app_uuid", null) == null) {
            prefs.edit().putString("app_uuid", java.util.UUID.randomUUID().toString()).apply();
        }

        // Initialize Google Play Integrity API check
        eu.kodanetwork.mchost.security.KodaIntegrityHelper.checkIntegrity(this);

        setContentView(R.layout.activity_main);
        
        eu.kodanetwork.mchost.orchestration.DatabaseOrchestrator.ensureDatabasesExtracted(this);

        // Apply light mode background early
        eu.kodanetwork.mchost.util.NetworkMonitorManager.init(this);
        if (ThemeHelper.isLightMode(this)) {
            findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().setStatusBarColor(0xFFF5F5F5);
                getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().setNavigationBarColor(0xFFF5F5F5);
            }
        }
        
        permLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {});
        storageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {});

        try {
            repo = ServerRepo.get(this);
            rv = findViewById(R.id.rv);
            tvEmpty = findViewById(R.id.tv_empty);

            adapter = new ServerCardAdapter(this, this::openServer);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(adapter);

            ExtendedFloatingActionButton fab = findViewById(R.id.fab_add);
            if (fab != null) {
                fab.setText(R.string.new_server_btn);
                fab.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, CreateServerActivity.class));
                });
            }

            View fabDb = findViewById(R.id.fab_add_db);
            if (fabDb != null) {
                fabDb.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, CreateDatabaseActivity.class));
                });
            }

            View btnSupportMain = findViewById(R.id.btn_support_main);
            if (btnSupportMain != null) {
                btnSupportMain.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    SupportSelectionDialog dialog = new SupportSelectionDialog(this, new SupportSelectionDialog.SupportDialogListener() {
                        @Override
                        public void onReportBugClicked() {
                            // Open create bug ticket UI
                            Intent intent = new Intent(MainActivity.this, CreateSupportTicketActivity.class);
                            intent.putExtra("TICKET_TYPE", "BUG");
                            startActivity(intent);
                        }

                        @Override
                        public void onReportServerClicked() {
                            // Open create server ticket UI
                            Intent intent = new Intent(MainActivity.this, CreateSupportTicketActivity.class);
                            intent.putExtra("TICKET_TYPE", "SERVER_REPORT");
                            startActivity(intent);
                        }

                        @Override
                        public void onMyTicketsClicked() {
                            // Open ticket list
                            startActivity(new Intent(MainActivity.this, SupportTicketListActivity.class));
                        }
                    });
                    dialog.show();
                });
            }

            View btnDebug = findViewById(R.id.btn_debug_log);
            if (btnDebug != null) {
                btnDebug.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, DebugLogActivity.class));
                });
            }

            View btnSettings = findViewById(R.id.btn_settings);
            if (btnSettings != null) {
                btnSettings.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, SettingsActivity.class));
                });
            }



            repo.addListener(this::refresh);
            requestPerms();
            startAndBind();
            refresh();
            startPeriodicRefresh();

            String autoStartId = getIntent().getStringExtra("auto_start_server");
            if (autoStartId != null && !autoStartId.isEmpty()) {
                Intent startSvc = new Intent(this, KodaServerService.class);
                startSvc.setAction(KodaServerService.ACTION_START);
                startSvc.putExtra("id", autoStartId);
                startService(startSvc);
            }

            ThemeHelper.apply(this);
            eu.kodanetwork.mchost.util.HapticUtil.applyHaptics(this);
            checkToS();
            Log.d(TAG, "MainActivity created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void checkToS() {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if (!prefs.getBoolean("tos_accepted", false)) {
            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_tos);
            dialog.setCancelable(false);

            dialog.findViewById(R.id.btn_accept_tos).setOnClickListener(v -> {
                prefs.edit().putBoolean("tos_accepted", true).apply();
                dialog.dismiss();
            });

            dialog.findViewById(R.id.btn_reject_tos).setOnClickListener(v -> {
                finishAffinity();
                System.exit(0);
            });

            TextView tvTosContent = dialog.findViewById(R.id.tv_tos_content);
            String tosHtml = "<b>Terms of Service</b><br><br>" +
                "<font color='#888899'>Last Updated: June 2026</font><br><br>" +
                "By downloading, installing, or using the KodaHosting mobile application and related services, you agree to comply with and be bound by these Terms of Service. If you do not agree to these Terms, you must uninstall the app and cease using our services immediately.<br><br>" +
                "<b>Eligibility & Age Requirements</b><br>" +
                "You must be at least 13 years old (or 16 in certain EU jurisdictions) to use this Service. By agreeing to these Terms, you represent and warrant that you meet these age requirements.<br><br>" +
                "<b>1. Service Overview</b><br>" +
                "KodaHosting provides a mobile environment and networking infrastructure that allows you to host Minecraft servers directly from your Android device. We provide the networking tunnels to proxy incoming player connections securely to your device.<br><br>" +
                "<b>2. Acceptable Use Policy</b><br>" +
                "You agree to use KodaHosting solely for its intended purpose: hosting personal game servers. You are entirely responsible for the content, worlds, and plugins loaded onto your servers.<br><br>" +
                "<font color='#FF4444'><b>Strict Prohibitions</b></font><br>" +
                "The following actions are strictly prohibited and will result in an immediate, permanent ban from KodaHosting services without warning or refund:<br>" +
                "&#8226; <b>Reverse Engineering:</b> You may not decompile, reverse engineer, decrypt, or otherwise attempt to extract the source code of the KodaHosting App, our official plugins, or our proprietary networking binaries.<br>" +
                "&#8226; <b>Unauthorized Tunnels:</b> You may not use the KodaHosting network infrastructure to host unauthorized tunnels, VPNs, proxies, or any traffic that is not standard Minecraft gameplay data.<br>" +
                "&#8226; <b>Malicious Activity & Password Stealing:</b> You may not use our services to host malware, execute phishing campaigns, or attempt to steal passwords, session tokens, or personal data from joining players or other users.<br><br>" +
                "<b>3. Service Reliability & Uptime</b><br>" +
                "Because the servers run on your own hardware, uptime depends directly on your device's power state and network connectivity. KodaHosting does not guarantee 100% uptime for our central proxy routing infrastructure, though we strive for maximum reliability.<br><br>" +
                "<b>4. Account Termination</b><br>" +
                "We reserve the right to suspend or terminate your account and access to the KodaHosting proxy infrastructure at any time, for any reason, particularly if you violate the Acceptable Use Policy.<br><br>" +
                "<b>5. Disclaimer of Warranties</b><br>" +
                "THE SERVICE IS PROVIDED ON AN \"AS IS\" AND \"AS AVAILABLE\" BASIS. WE EXPRESSLY DISCLAIM ALL WARRANTIES OF ANY KIND, WHETHER EXPRESS OR IMPLIED. We do not guarantee that the service will be uninterrupted, secure, or error-free, nor do we guarantee the safety of your device data.<br><br>" +
                "<b>6. Limitation of Liability</b><br>" +
                "TO THE MAXIMUM EXTENT PERMITTED BY LAW, KODAHOSTING SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, OR ANY LOSS OF DATA (INCLUDING SERVER WORLDS OR CONFIGURATIONS), RESULTING FROM YOUR USE OF THE APP.<br><br>" +
                "<b>7. Governing Law</b><br>" +
                "These Terms shall be governed by and construed in accordance with the laws of the European Union and applicable local laws.<br><br>" +
                "<b>8. Changes to Terms</b><br>" +
                "We reserve the right to modify these Terms of Service at any time. Continued use of the app after any such changes shall constitute your consent to such changes.";
            tvTosContent.setText(android.text.Html.fromHtml(tosHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 200);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 300);
            }, 300);

            dialog.show();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        eu.kodanetwork.mchost.App.resetAfkTimer();
    }

    private ServerInstance pendingServerOpen;

    private void openServer(ServerInstance s) {
        if (eu.kodanetwork.mchost.util.BiometricHelper.isBioEnabledFor(this, "bio_on_server_click")) {
            pendingServerOpen = s;
            Intent intent = new Intent(this, eu.kodanetwork.mchost.ui.BiometricAuthActivity.class);
            startActivityForResult(intent, eu.kodanetwork.mchost.util.BiometricHelper.REQ_BIO_AUTH);
        } else {
            proceedOpenServer(s);
        }
    }

    private void proceedOpenServer(ServerInstance s) {
        Intent i = new Intent(this, ServerDetailActivity.class);
        i.putExtra("id", s.getId());
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == eu.kodanetwork.mchost.util.BiometricHelper.REQ_BIO_AUTH) {
            if (resultCode == RESULT_OK && pendingServerOpen != null) {
                proceedOpenServer(pendingServerOpen);
            }
            pendingServerOpen = null;
        }
    }

    private void refresh() {
        runOnUiThread(() -> {
            try {
                List<ServerInstance> list = repo.all();
                adapter.setData(list);
                if (tvEmpty != null) tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                
                TextView tvCount = findViewById(R.id.tv_server_count);
                if (tvCount != null) {
                    String label = list.size() == 1 ? getString(R.string.server_singular) : getString(R.string.server_plural);
                    tvCount.setText(list.size() + label);
                }
            } catch (Exception ignored) {}
        });
    }

    private void startAndBind() {
        try {
            Intent si = new Intent(this, KodaServerService.class);
            bindService(si, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception ignored) {}
    }

    private void startPeriodicRefresh() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (bound) adapter.notifyDataSetChanged();
                handler.postDelayed(this, 10000);
            }
        }, 10000);
    }

    private void requestPerms() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) permLauncher.launch(perms.toArray(new String[0]));

        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                String lang = java.util.Locale.getDefault().getLanguage();
                String title = lang.equals("de") ? "Akku-Optimierung deaktivieren" : "Disable Battery Optimization";
                String msg = lang.equals("de") ? 
                    "Damit dein Server mit maximaler CPU-Leistung läuft (z. B. auf Xiaomi-Geräten 20 TPS hält) und nicht im Hintergrund gedrosselt wird, musst du die Akku-Beschränkungen für diese App aufheben.\n\nBitte wähle im nächsten Menü 'Keine Beschränkungen' (No restrictions)." :
                    "To ensure your server runs at maximum CPU performance and maintains 20 TPS without background throttling, you need to remove battery restrictions for this app.\n\nPlease select 'No restrictions' in the next menu.";
                String btn = lang.equals("de") ? "Einstellungen öffnen" : "Open Settings";

                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton(btn, (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .show();
            }
        } catch (Exception ignored) {}
    }

    private String lastTheme = "modern";
    private String lastThemeMode = "dark";

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode)) {
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            recreate();
            return;
        }
        refresh();
        checkAppStatus();
        checkOfflineHibernations();
    }

    private void checkAppStatus() {
        new Thread(() -> {
            try {
                String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "");
                String apiKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                String baseUrl = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl();
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

                // Check Ban Status
                if (!uuid.isEmpty()) {
                    okhttp3.Request userReq = new okhttp3.Request.Builder()
                        .url(baseUrl + "/rest/v1/koda_users?app_uuid=eq." + uuid + "&select=is_banned")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .build();
                    try (okhttp3.Response response = client.newCall(userReq).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String json = response.body().string();
                            if (json.contains("\"is_banned\":true") || json.contains("\"is_banned\": true")) {
                                runOnUiThread(() -> {
                                    startActivity(new Intent(MainActivity.this, BannedActivity.class));
                                    finish();
                                });
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Check Maintenance Mode
                okhttp3.Request maintReq = new okhttp3.Request.Builder()
                    .url(baseUrl + "/rest/v1/app_settings?key=eq.maintenance_mode&select=value")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
                try (okhttp3.Response response = client.newCall(maintReq).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        if (json.contains("\"active\":true") || json.contains("\"active\": true")) {
                            // extract reason and duration via simple string search or JSON parsing
                            String reason = "System Maintenance";
                            int duration = 60;
                            try {
                                org.json.JSONArray arr = new org.json.JSONArray(json);
                                if (arr.length() > 0) {
                                    org.json.JSONObject val = arr.getJSONObject(0).getJSONObject("value");
                                    reason = val.optString("reason", reason);
                                    duration = val.optInt("duration_minutes", duration);
                                }
                            } catch (Exception ignored) {}

                            final String finalReason = reason;
                            final int finalDuration = duration;
                            runOnUiThread(() -> {
                                Intent intent = new Intent(MainActivity.this, MaintenanceActivity.class);
                                intent.putExtra("reason", finalReason);
                                intent.putExtra("duration", finalDuration);
                                startActivity(intent);
                                finish();
                            });
                        }
                    }
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }).start();
    }

    private void checkOfflineHibernations() {
        new Thread(() -> {
            try {
                List<ServerInstance> list = repo.all();
                for (ServerInstance srv : list) {
                    if (srv.state == ServerInstance.State.OFFLINE && srv.getSubdomain() != null && !srv.getSubdomain().isEmpty()) {
                        okhttp3.Request request = new okhttp3.Request.Builder()
                            .url("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?host=eq." + srv.getSubdomain() + "&select=server_version")
                            .get()
                            .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                            .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                            .build();
                        try (okhttp3.Response response = new okhttp3.OkHttpClient().newCall(request).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                String json = response.body().string();
                                if (json.contains("\"server_version\":\"HIBERNATED\"") || json.contains("\"server_version\": \"HIBERNATED\"")) {
                                    Log.d(TAG, "Server " + srv.getName() + " is offline but hibernated remotely. Zipping...");
                                    eu.kodanetwork.mchost.utils.HibernationManager.hibernateServer(this, srv, repo);
                                    refresh();
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        eu.kodanetwork.mchost.App.resetAfkTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            repo.removeListener(this::refresh);
            if (bound) unbindService(conn);
        } catch (Exception ignored) {}
    }
}
