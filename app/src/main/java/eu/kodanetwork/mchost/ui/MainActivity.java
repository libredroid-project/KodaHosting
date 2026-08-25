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

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
    private static boolean updateDialogShown = false;

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
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        this.lastTheme = prefs.getString("app_theme", "modern");
        this.lastThemeMode = prefs.getString("theme_mode", "dark");
        this.lastM3Enabled = prefs.getBoolean("dev_material3_enabled", false);
        this.lastM3ColorMode = prefs.getString("m3_color_mode", "dynamic");
        this.lastM3CustomColor = prefs.getInt("m3_custom_color", 0xFF6750A4);
        boolean isCyber = "cyber".equals(this.lastTheme);

        String currentAppUuid = prefs.getString("app_uuid", null);
        String hwidUuid = null;
        try {
            String fullHwid = eu.kodanetwork.mchost.security.HWIDManager.getDeviceHWID(this);
            hwidUuid = "KODA-" + fullHwid.substring(0, 16).toUpperCase();
        } catch (Exception e) {
            String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            hwidUuid = "KODA-" + androidId.substring(0, 8).toUpperCase();
        }

        if (currentAppUuid == null || !currentAppUuid.equals(hwidUuid)) {
            prefs.edit().putString("app_uuid", hwidUuid).apply();
            
            // Automatic migration of servers for existing users
            if (currentAppUuid != null && !currentAppUuid.isEmpty()) {
                String finalOld = currentAppUuid;
                String finalNew = hwidUuid;
                new Thread(() -> {
                    try {
                        java.net.URL patchUrl = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_migrate_servers");
                        java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) patchUrl.openConnection();
                        patchConn.setRequestMethod("POST");
                        patchConn.setRequestProperty("Content-Type", "application/json");
                        patchConn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                        patchConn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                        patchConn.setRequestProperty("Prefer", "return=minimal");
                        patchConn.setDoOutput(true);
                        org.json.JSONObject pJson = new org.json.JSONObject()
                                .put("p_old_app_uuid", finalOld)
                                .put("p_new_app_uuid", finalNew);
                        java.io.OutputStream os = patchConn.getOutputStream();
                        os.write(pJson.toString().getBytes());
                        os.flush(); os.close();
                        patchConn.getResponseCode();
                    } catch (Exception ignored) {}
                }).start();
            }
        }
        
        // Prevent screenshots & screen recording
        // Screen protection removed

        // Initialize Google Play Integrity API check
        eu.kodanetwork.mchost.security.KodaIntegrityHelper.checkIntegrity(this);

        if (eu.kodanetwork.mchost.security.AntiTamperSystem.isScaryBannedLocally(this)) {
            android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.ui.ScaryBannedActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return;
        } else if (eu.kodanetwork.mchost.security.AntiTamperSystem.isBannedLocally(this)) {
            android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.ui.BannedActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return;
        }

        // Check for Android 17+ Memory Limiter process terminations
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    java.util.List<android.app.ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(getPackageName(), 0, 1);
                    if (exits != null && !exits.isEmpty()) {
                        android.app.ApplicationExitInfo lastExit = exits.get(0);
                        if (lastExit.getReason() == android.app.ApplicationExitInfo.REASON_OTHER) {
                            String desc = lastExit.getDescription();
                            if (desc != null && desc.contains("MemoryLimiter")) {
                                long lastExitTime = lastExit.getTimestamp();
                                long lastShownTime = prefs.getLong("last_shown_memory_limiter_warning", 0);
                                if (lastExitTime > lastShownTime) {
                                    prefs.edit().putLong("last_shown_memory_limiter_warning", lastExitTime).apply();
                                    Intent memoryIntent = new Intent(this, eu.kodanetwork.mchost.ui.PraetorMemoryLimitActivity.class);
                                    startActivity(memoryIntent);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        eu.kodanetwork.mchost.security.AntiTamperSystem.check(this);
        eu.kodanetwork.mchost.security.TripwireObserver.startWatching(this);

        if (this.lastM3Enabled) {
            setContentView(R.layout.activity_main_m3);
        } else {
            setContentView(R.layout.activity_main);
        }

        showWelcomeSplash();

        eu.kodanetwork.mchost.orchestration.DatabaseOrchestrator.ensureDatabasesExtracted(this);

        eu.kodanetwork.mchost.util.NetworkMonitorManager.init(this);
        View rootLayout = findViewById(R.id.main_root_layout);
        View topBar = findViewById(R.id.main_top_bar);
        View bottomBar = findViewById(R.id.main_bottom_bar);
        
        if (prefs.getBoolean("dev_liquid_glass", false)) {
            // Overdrive Mode LiquidGlass Background
            if (rootLayout != null) rootLayout.setBackgroundResource(R.drawable.bg_liquid_glass);
            if (topBar != null) topBar.setBackgroundColor(0x33000000);
            if (bottomBar != null) bottomBar.setBackgroundColor(0x33000000);
        } else if (ThemeHelper.isLightMode(this)) {
            if (rootLayout != null) rootLayout.setBackgroundColor(0xFFF5F5F5);
            if (topBar != null) topBar.setBackgroundColor(0xFFF5F5F5);
            if (bottomBar != null) bottomBar.setBackgroundColor(0xFFF5F5F5);
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
                if (this.lastM3Enabled) eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(fab);
                fab.setText(R.string.new_server_btn);
                fab.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, CreateServerActivity.class));
                });
                // In-app coach phase 1: explain the NEW SERVER button on first launch
                eu.kodanetwork.mchost.util.TutorialCoach.maybeShowNewServerHint(this, fab);
            }

            View fabDb = findViewById(R.id.fab_add_db);
            if (fabDb != null) {
                if (this.lastM3Enabled) eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(fabDb);
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
            // First app open: cinematic tutorial takes over (includes the ToS step);
            // the plain ToS dialog below stays as fallback for skipped tutorials
            eu.kodanetwork.mchost.util.TutorialCoach.maybeStartTutorial(this);
            checkToS(0);
            Log.d(TAG, "MainActivity created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void checkToS(long newTs) {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if (!prefs.getBoolean("tos_accepted_v3", false) || (newTs > 0 && newTs > prefs.getLong("accepted_tos_version_ts", 0))) {
            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_tos);
            dialog.setCancelable(false);

            dialog.findViewById(R.id.btn_accept_tos).setOnClickListener(v -> {
                prefs.edit().putBoolean("tos_accepted_v3", true).putLong("accepted_tos_version_ts", newTs > 0 ? newTs : System.currentTimeMillis()).apply();
                dialog.dismiss();
            });

            dialog.findViewById(R.id.btn_reject_tos).setOnClickListener(v -> {
                finishAffinity();
                System.exit(0);
            });

            TextView tvTosContent = dialog.findViewById(R.id.tv_tos_content);
            String divider = "<br><br><font color='#FF6B00'>&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;&#9644;</font><br><br>";
            String legalHtml = legalDocToHtml("tos.txt") + divider + legalDocToHtml("privacy.txt") + divider + legalDocToHtml("impressum.txt");
            tvTosContent.setText(android.text.Html.fromHtml(legalHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 200);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 300);
            }, 300);

            dialog.show();
        }
    }

    private String legalDocToHtml(String assetName) {
        String raw = readAssetText("licenses/" + assetName);
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            boolean followedByBlank = i + 1 >= lines.length || lines[i + 1].trim().isEmpty();
            boolean heading = i == 0
                    || line.matches("\\d+\\.\\s.+")
                    || (line.length() < 64 && !line.endsWith(".") && !line.startsWith("-") && followedByBlank);
            sb.append(heading ? "<b>" : "")
              .append(android.text.TextUtils.htmlEncode(line))
              .append(heading ? "</b>" : "")
              .append("<br>");
            if (followedByBlank) sb.append("<br>");
        }
        return sb.toString();
    }

    private String readAssetText(String path) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
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

    private String lastTheme;
    private String lastThemeMode;
    private boolean lastM3Enabled;
    private String lastM3ColorMode;
    private int lastM3CustomColor;

    @Override
    protected void onResume() {
        super.onResume();
        // Coach phase 1 fires here — the tutorial may have finished while
        // MainActivity was paused behind it
        eu.kodanetwork.mchost.util.TutorialCoach.maybeShowNewServerHint(this, findViewById(R.id.fab_add));
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        boolean currentM3Enabled = prefs.getBoolean("dev_material3_enabled", false);
        String currentM3ColorMode = prefs.getString("m3_color_mode", "dynamic");
        int currentM3CustomColor = prefs.getInt("m3_custom_color", 0xFF6750A4);
        
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode) ||
            currentM3Enabled != lastM3Enabled || !currentM3ColorMode.equals(lastM3ColorMode) ||
            currentM3CustomColor != lastM3CustomColor) {
            
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            lastM3Enabled = currentM3Enabled;
            lastM3ColorMode = currentM3ColorMode;
            lastM3CustomColor = currentM3CustomColor;
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
                if (eu.kodanetwork.mchost.App.getPrefs(this).getBoolean("is_banned_user", false)) {
                    runOnUiThread(() -> {
                        startActivity(new Intent(MainActivity.this, BannedActivity.class));
                        finish();
                    });
                    return;
                }
                String uuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "");
                String apiKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                String baseUrl = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl();
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

                // Check Ban Status
                if (!uuid.isEmpty()) {
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(
                            "{\"p_app_uuid\":\"" + uuid + "\"}",
                            okhttp3.MediaType.parse("application/json")
                    );
                    okhttp3.Request userReq = new okhttp3.Request.Builder()
                        .url(baseUrl + "/rest/v1/rpc/rpc_get_is_banned")
                        .post(body)
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .build();
                    try (okhttp3.Response response = client.newCall(userReq).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String json = response.body().string();
                            if (json.contains("true")) {
                                eu.kodanetwork.mchost.App.getPrefs(MainActivity.this).edit().putBoolean("is_banned_user", true).apply();
                                runOnUiThread(() -> {
                                    startActivity(new Intent(MainActivity.this, BannedActivity.class));
                                    finish();
                                });
                                return;
                            } else {
                                eu.kodanetwork.mchost.App.getPrefs(MainActivity.this).edit().putBoolean("is_banned_user", false).apply();
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

                // Check latest ToS
                okhttp3.Request tosReq = new okhttp3.Request.Builder()
                    .url(baseUrl + "/rest/v1/app_settings?key=eq.latest_tos_version&select=value")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
                try (okhttp3.Response response = client.newCall(tosReq).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        long latestTs = 0;
                        try {
                            org.json.JSONArray arr = new org.json.JSONArray(json);
                            if (arr.length() > 0) {
                                String valStr = arr.getJSONObject(0).optString("value", "0");
                                latestTs = Long.parseLong(valStr);
                            }
                        } catch (Exception ignored) {}
                        
                        long acceptedTs = eu.kodanetwork.mchost.App.getPrefs(MainActivity.this).getLong("accepted_tos_version_ts", 0);
                        if (latestTs > acceptedTs) {
                            final long fTs = latestTs;
                            runOnUiThread(() -> {
                                checkToS(fTs);
                            });
                        }
                    }
                } catch (Exception ignored) {}

                // Check latest app version
                okhttp3.Request versionReq = new okhttp3.Request.Builder()
                    .url(baseUrl + "/rest/v1/app_settings?key=eq.latest_app_version&select=value")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
                try (okhttp3.Response response = client.newCall(versionReq).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        int latestVer = 0;
                        try {
                            org.json.JSONArray arr = new org.json.JSONArray(json);
                            if (arr.length() > 0) {
                                String valStr = arr.getJSONObject(0).optString("value", "0");
                                latestVer = Integer.parseInt(valStr);
                            }
                        } catch (Exception ignored) {}
                        
                        if (latestVer > eu.kodanetwork.mchost.BuildConfig.VERSION_CODE) {
                            if (!updateDialogShown) {
                                updateDialogShown = true;
                                runOnUiThread(() -> showUpdateRequiredDialog());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }).start();
    }

    private void showUpdateRequiredDialog() {
        if (isFinishing() || isDestroyed()) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.KodaBottomSheetDialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_praetor_update, null);
        dialog.setContentView(view);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvUpdateTitle);
        String praetorHtml = "<font color='#555555'>P.R.</font><font color='#AAAAAA'>A.E.T.</font><font color='#FFFFFF'>O.R.</font>";
        if (tvTitle != null) tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

        com.google.android.material.button.MaterialButton btnUpdate = view.findViewById(R.id.btnUpdateNow);
        com.google.android.material.button.MaterialButton btnSkip = view.findViewById(R.id.btnUpdateSkip);

        btnUpdate.setOnClickListener(v -> {
            try {
                com.google.android.play.core.appupdate.AppUpdateManager appUpdateManager = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(this);
                com.google.android.gms.tasks.Task<com.google.android.play.core.appupdate.AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
                appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                    if (appUpdateInfo.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
                          && appUpdateInfo.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)) {
                          try {
                              appUpdateManager.startUpdateFlowForResult(appUpdateInfo, com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE, this, 123);
                          } catch (Exception e) {
                              fallbackUpdate();
                          }
                    } else {
                          fallbackUpdate();
                    }
                }).addOnFailureListener(e -> {
                    fallbackUpdate();
                });
            } catch (Exception e) {
                fallbackUpdate();
            }
        });

        new android.os.CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnSkip.setText(String.format(getString(R.string.praetor_update_skip_timer), millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                btnSkip.setEnabled(true);
                btnSkip.setText(getString(R.string.praetor_update_skip));
            }
        }.start();

        btnSkip.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setOnShowListener(d -> {
            // Expand the bottom sheet
            com.google.android.material.bottomsheet.BottomSheetDialog d1 = (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            android.widget.FrameLayout bottomSheet = d1.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        android.view.Window w = dialog.getWindow();
        if (w != null && android.os.Build.VERSION.SDK_INT >= 23) {
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
            }
            int flags = w.getDecorView().getSystemUiVisibility();
            flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // Enforce white icons for dark Praetor background
            w.getDecorView().setSystemUiVisibility(flags);
        }

        dialog.show();
    }

    private void fallbackUpdate() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + getPackageName()));
            intent.setPackage("com.android.vending");
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void checkOfflineHibernations() {
        new Thread(() -> {
            try {
                List<ServerInstance> list = repo.all();
                for (ServerInstance srv : list) {
                    if (srv.state == ServerInstance.State.OFFLINE && srv.getSubdomain() != null && !srv.getSubdomain().isEmpty()) {
                        okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?host=eq." + srv.getSubdomain() + "&select=server_version")
                            .get()
                            .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                            .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                            .build();
                        try (okhttp3.Response response = new okhttp3.OkHttpClient().newCall(request).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                String json = response.body().string();
                                org.json.JSONArray arr = new org.json.JSONArray(json);
                                if (arr.length() > 0 && "HIBERNATED".equals(arr.getJSONObject(0).optString("server_version"))) {
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

    // ── Welcome splash: covers startup for ~3s every launch ────────────────

    private android.widget.FrameLayout splashOverlay;

    private void showWelcomeSplash() {
        if (splashOverlay != null) return;
        android.view.ViewGroup content = findViewById(android.R.id.content);
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xFF000000);
        float d = getResources().getDisplayMetrics().density;

        overlay.addView(new eu.kodanetwork.mchost.ui.FloatingSquaresView(this), new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        android.widget.LinearLayout center = new android.widget.LinearLayout(this);
        center.setOrientation(android.widget.LinearLayout.VERTICAL);
        center.setGravity(android.view.Gravity.CENTER);
        android.widget.FrameLayout.LayoutParams cLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        overlay.addView(center, cLp);

        android.widget.TextView logo = new android.widget.TextView(this);
        logo.setText("KodaHosting");
        logo.setTextColor(0xFFFF6B00);
        logo.setTextSize(30);
        logo.setLetterSpacing(0.06f);
        logo.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, eu.kodanetwork.mchost.R.font.font_koda), android.graphics.Typeface.BOLD);
        center.addView(logo);

        com.airbnb.lottie.LottieAnimationView boot = new com.airbnb.lottie.LottieAnimationView(this);
        android.widget.LinearLayout.LayoutParams bLp = new android.widget.LinearLayout.LayoutParams(
                (int)(180 * d), (int)(64 * d));
        bLp.topMargin = (int)(28 * d);
        boot.setLayoutParams(bLp);
        com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(this, eu.kodanetwork.mchost.R.raw.koda_boot, null);
        boot.setAnimation(eu.kodanetwork.mchost.R.raw.koda_boot);
        boot.loop(true);
        boot.playAnimation();
        center.addView(boot);

        android.widget.TextView version = new android.widget.TextView(this);
        version.setText("v" + eu.kodanetwork.mchost.BuildConfig.VERSION_NAME);
        version.setTextColor(0xFF555566);
        version.setTextSize(12);
        android.widget.FrameLayout.LayoutParams vLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM);
        vLp.bottomMargin = (int)(36 * d);
        overlay.addView(version, vLp);

        content.addView(overlay, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        splashOverlay = overlay;

        // minimum 3 seconds, then fade out
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (splashOverlay == null) return;
            splashOverlay.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> {
                        if (splashOverlay != null) {
                            content.removeView(splashOverlay);
                            splashOverlay = null;
                        }
                    }).start();
        }, 3000);
    }
}
