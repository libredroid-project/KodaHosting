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
    private boolean tutorialRunning = false;
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
        eu.kodanetwork.mchost.util.SleekThemeHelper.applyTheme(this);
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

        if (eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) {
            setContentView(R.layout.activity_main_sleek);
        } else if (this.lastM3Enabled) {
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
        } else if (ThemeHelper.isLightMode(this)
                && !eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) {
            // Sleek is always warm-dark; the light-mode overrides would wash out the nav pill
            if (rootLayout != null) rootLayout.setBackgroundColor(0xFFF4EFE7);
            if (topBar != null) topBar.setBackgroundColor(0xFFF4EFE7);
            if (bottomBar != null) bottomBar.setBackgroundColor(0xFFF4EFE7);
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

            // Sleek uses a plain FloatingActionButton, legacy/M3 an ExtendedFloatingActionButton.
            // A hard cast here throws ClassCastException in sleek mode and silently kills
            // every wiring below (caught by the surrounding try/catch) — hence View + instanceof.
            View fabAdd = findViewById(R.id.fab_add);
            if (fabAdd instanceof ExtendedFloatingActionButton) {
                ExtendedFloatingActionButton fab = (ExtendedFloatingActionButton) fabAdd;
                if (this.lastM3Enabled) eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(fab);
                fab.setText(R.string.new_server_btn);
                // In-app coach phase 1: explain the NEW SERVER button on first launch
                eu.kodanetwork.mchost.util.TutorialCoach.maybeShowNewServerHint(this, fab);
            }
            if (fabAdd != null) {
                if (eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) {
                    eu.kodanetwork.mchost.util.SleekTouch.apply(fabAdd, () ->
                        startActivity(new Intent(MainActivity.this, CreateServerActivity.class)), 80);
                } else {
                    fabAdd.setOnClickListener(v -> {
                        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                        startActivity(new Intent(MainActivity.this, CreateServerActivity.class));
                    });
                }
            }

            // ── Sleek layout: nav bar buttons + header icon ──
            if (eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) {
                // Custom Sleek touch feedback (no Android ripple)
                java.util.function.BiConsumer<View, Runnable> wire = (view, action) -> {
                    if (view == null) return;
                    eu.kodanetwork.mchost.util.SleekTouch.button(view, action);
                };

                wire.accept(findViewById(R.id.nav_log), () ->
                        startActivity(new android.content.Intent(MainActivity.this, eu.kodanetwork.mchost.ui.DebugLogActivity.class)));

                // Right nav button: Files — pick a server, jump straight to its Files tab
                wire.accept(findViewById(R.id.nav_files), this::showSleekFilesPicker);

                // Header settings button
                View btnSleekSettings = findViewById(R.id.btn_settings);
                if (btnSleekSettings != null) {
                    btnSleekSettings.setOnClickListener(v -> {
                        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
                        startActivity(new android.content.Intent(MainActivity.this, eu.kodanetwork.mchost.ui.SettingsActivity.class));
                    });
                }

                // Quick stats update
                updateSleekStats();
            }

            View fabDb = findViewById(R.id.fab_add_db);
            if (fabDb != null) {
                if (this.lastM3Enabled) eu.kodanetwork.mchost.util.M3AnimationHelper.applySpringTouch(fabDb);
                fabDb.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, CreateDatabaseActivity.class));
                });
            }

            
            View btnDonateMain = findViewById(R.id.btn_donate_main);
            if (btnDonateMain != null) {
                btnDonateMain.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    showSupportDialog();
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
            tutorialRunning = eu.kodanetwork.mchost.util.TutorialCoach.maybeStartTutorial(this);
            if (!tutorialRunning) {
                checkToS(0);
            }
            Log.d(TAG, "MainActivity created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
        }
    }

    private void checkToS(long newTs) {
        if (tutorialRunning) return;
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if (!prefs.getBoolean("tos_accepted_v3", false) || (newTs > 0 && newTs > prefs.getLong("accepted_tos_version_ts", 0))) {
            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_tos);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
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
                handler.postDelayed(this, 1000);
            }
        }, 1000);
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
        // Re-theme dynamically created views (server cards) so light mode
        // works here like it does in Settings
        eu.kodanetwork.mchost.util.ThemeHelper.reapply(this);
        updateSleekStats();
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

        eu.kodanetwork.mchost.util.SheetFix.apply(dialog);
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
    // once per app process: returning from settings must not replay the splash
    private static boolean splashShownThisProcess = false;

    private void showWelcomeSplash() {
        if (splashOverlay != null || splashShownThisProcess) return;
        splashShownThisProcess = true;
        android.view.ViewGroup content = findViewById(android.R.id.content);
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xFF000000);
        // the real AFK screensaver background (floating Koda squares)
        overlay.addView(new eu.kodanetwork.mchost.ui.FloatingSquaresView(this), new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        // ── left block at VERTICAL CENTER: logo + typed title ──
        // soft radial scrim so logo/text are gently separated from the squares
        android.view.View scrim = new android.view.View(this);
        android.graphics.drawable.GradientDrawable scrimBg = new android.graphics.drawable.GradientDrawable();
        scrimBg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.BL_TR);
        scrimBg.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        scrimBg.setColors(new int[]{0x88000000, 0x22000000, 0x00000000});
        scrim.setBackground(scrimBg);
        android.widget.FrameLayout.LayoutParams scrimLp = new android.widget.FrameLayout.LayoutParams(
                (int)(420 * d), (int)(420 * d), android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        scrimLp.leftMargin = (int)(-90 * d);
        overlay.addView(scrim, scrimLp);

        android.widget.LinearLayout topLeft = new android.widget.LinearLayout(this);
        topLeft.setOrientation(android.widget.LinearLayout.VERTICAL);
        topLeft.setGravity(android.view.Gravity.START);
        android.widget.FrameLayout.LayoutParams tlLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        tlLp.leftMargin = (int)(40 * d);
        tlLp.rightMargin = (int)(40 * d);
        overlay.addView(topLeft, tlLp);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(eu.kodanetwork.mchost.R.mipmap.ic_launcher);
        android.widget.LinearLayout.LayoutParams lLp = new android.widget.LinearLayout.LayoutParams(
                (int)(88 * d), (int)(88 * d));
        logo.setLayoutParams(lLp);
        logo.setAlpha(0f);
        logo.animate().alpha(1f).setDuration(400).start(); // no pulse at start
        topLeft.addView(logo);

        // KodaHosting types itself live in the app's pixel font, left-aligned
        android.widget.TextView typed = new android.widget.TextView(this);
        typed.setTextColor(0xFFFF8C00);
        typed.setTextSize(22);
        typed.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, eu.kodanetwork.mchost.R.font.press_start_2p));
        android.widget.LinearLayout.LayoutParams tLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = (int)(20 * d);
        typed.setLayoutParams(tLp);
        topLeft.addView(typed);

        // ── bottom-center block: three dots, below them version + source ──
        android.widget.LinearLayout bottom = new android.widget.LinearLayout(this);
        bottom.setOrientation(android.widget.LinearLayout.VERTICAL);
        bottom.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        android.widget.FrameLayout.LayoutParams btLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        btLp.bottomMargin = (int)(34 * d);
        overlay.addView(bottom, btLp);

        com.airbnb.lottie.LottieAnimationView boot = new com.airbnb.lottie.LottieAnimationView(this);
        android.widget.LinearLayout.LayoutParams bLp = new android.widget.LinearLayout.LayoutParams(
                (int)(140 * d), (int)(48 * d));
        boot.setLayoutParams(bLp);
        boot.setAnimation(eu.kodanetwork.mchost.R.raw.koda_boot);
        boot.loop(true);
        boot.playAnimation();
        bottom.addView(boot);

        android.widget.TextView version = new android.widget.TextView(this);
        String vname = eu.kodanetwork.mchost.BuildConfig.VERSION_NAME;
        if (vname != null && !vname.startsWith("v")) vname = "v" + vname;
        version.setText(vname);
        version.setTextColor(0xFF8A8A9A);
        version.setTextSize(12);
        android.widget.LinearLayout.LayoutParams vLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        vLp.topMargin = (int)(8 * d);
        bottom.addView(version, vLp);

        android.widget.TextView source = new android.widget.TextView(this);
        source.setText("kodanetwork.eu");
        source.setTextColor(0xFF555566);
        source.setTextSize(10);
        android.widget.LinearLayout.LayoutParams sLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = (int)(3 * d);
        bottom.addView(source, sLp);

        content.addView(overlay, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        splashOverlay = overlay;

        // type "KodaHosting" live with a blinking terminal cursor
        final String word = "KodaHosting";
        final int[] i = {0};
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override public void run() {
                if (splashOverlay == null) return;
                if (i[0] <= word.length()) {
                    typed.setText(word.substring(0, i[0]++));
                    h.postDelayed(tick[0], 90);
                }
            }
        };
        h.postDelayed(tick[0], 350);
        final Runnable[] blink = new Runnable[1];
        blink[0] = () -> {
            if (splashOverlay == null) return;
            CharSequence cur = typed.getText();
            boolean hasCursor = cur != null && cur.toString().endsWith("▌");
            String base = hasCursor && cur.length() > 0
                    ? cur.toString().substring(0, cur.length() - 1)
                    : (cur == null ? "" : cur.toString());
            if (base.length() <= word.length()) {
                typed.setText(base + (hasCursor ? "" : "▌"));
            }
            h.postDelayed(blink[0], 420);
        };
        h.postDelayed(blink[0], 350);

        // fade out after 3s: the typed word glides onto the real corner title
        h.postDelayed(() -> {
            if (splashOverlay == null) return;
            TextView title = findTitleView();
            if (title != null) {
                int[] tLoc = new int[2];
                int[] sLoc = new int[2];
                title.getLocationOnScreen(tLoc);
                typed.getLocationOnScreen(sLoc);
                float dx = tLoc[0] - sLoc[0];
                float dy = (tLoc[1] + title.getHeight() / 2f) - (sLoc[1] + typed.getHeight() / 2f);
                overlay.setBackgroundColor(0x000A0807);
                logo.animate().alpha(0f).setDuration(350).start();
                boot.animate().alpha(0f).setDuration(350).start();
                bottom.animate().alpha(0f).setDuration(300).start();
                typed.animate()
                        .translationX(dx).translationY(dy)
                        .scaleX(0.55f).scaleY(0.55f)
                        .setDuration(500)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                        .withEndAction(() -> {
                            if (splashOverlay != null) {
                                content.removeView(splashOverlay);
                                splashOverlay = null;
                            }
                        }).start();
            } else {
                overlay.animate().alpha(0f).setDuration(400)
                        .withEndAction(() -> {
                            if (splashOverlay != null) {
                                content.removeView(splashOverlay);
                                splashOverlay = null;
                            }
                        }).start();
            }
        }, 3000);
    }

    /** The app title is the borderless TextView showing app_name at the top left. */
    private TextView findTitleView() {
        return findTitleViewTraverse(findViewById(android.R.id.content));
    }

    private TextView findTitleViewTraverse(android.view.View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            CharSequence t = tv.getText();
            if (t != null && t.toString().equals(getString(eu.kodanetwork.mchost.R.string.app_name))) {
                return tv;
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findTitleViewTraverse(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private void showSupportDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_praetor_support);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        String praetorHtml = "<font color=\"#4CAF50\">DONATE</font>";
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_title)).setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        android.widget.TextView subtitle = dialog.findViewById(R.id.tv_dialog_subtitle);
        subtitle.setVisibility(android.view.View.GONE);
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(android.text.Html.fromHtml("KodaHosting is built and maintained by a <font color=\"#FF8C00\">single developer</font>.<br><br>By supporting me on Ko-fi, you directly help me pay for the <font color=\"#FFFFFF\">expensive server costs</font> and keep this project <font color=\"#AAAAAA\">completely ad-free</font>.<br><br>Every coffee means the world to me and keeps KodaHosting alive!", android.text.Html.FROM_HTML_MODE_LEGACY));

        android.widget.Button btnKofi = dialog.findViewById(R.id.btn_open_kofi);
        if (btnKofi != null) {
            btnKofi.setOnClickListener(v -> {
                dialog.dismiss();
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/kodahosting")));
            });
        }
        
        android.widget.Button btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }


    /**
     * Sleek nav "Files": bottom sheet listing all servers; picking one opens the
     * server detail straight on its Files tab.
     */
    private void showSleekFilesPicker() {
        java.util.List<ServerInstance> all = repo != null ? repo.all() : java.util.Collections.emptyList();
        if (all.isEmpty()) {
            android.widget.Toast.makeText(this, "Create a server first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int)(20 * d);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Files");
        title.setTextColor(0xFFF2EFE9);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, (int)(14 * d));
        content.addView(title);

        for (ServerInstance s : all) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int)(12 * d), (int)(12 * d), (int)(12 * d), (int)(12 * d));
            android.graphics.drawable.GradientDrawable rowBg = new android.graphics.drawable.GradientDrawable();
            rowBg.setCornerRadius(16 * d);
            rowBg.setColor(0xFF2E2A26);
            row.setBackground(rowBg);

            View dotView = new View(this);
            int dotCol;
            switch (s.state) {
                case ONLINE: dotCol = 0xFF69781D; break;
                case STARTING: case RESTARTING: dotCol = 0xFFF5A623; break;
                case CRASHED: dotCol = 0xFFE8442E; break;
                default: dotCol = 0xFF5C5852; break;
            }
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(dotCol);
            dotView.setBackground(dot);
            android.widget.LinearLayout.LayoutParams dotLp = new android.widget.LinearLayout.LayoutParams((int)(10 * d), (int)(10 * d));
            row.addView(dotView, dotLp);

            TextView name = new TextView(this);
            name.setText(s.getName());
            name.setTextColor(0xFFF2EFE9);
            name.setTextSize(16);
            android.widget.LinearLayout.LayoutParams nameLp = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameLp.leftMargin = (int)(12 * d);
            row.addView(name, nameLp);

            TextView addr = new TextView(this);
            addr.setText(s.getJoinAddress());
            addr.setTextColor(0xFFF0762B);
            addr.setTextSize(12);
            row.addView(addr);

            android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = (int)(10 * d);
            content.addView(row, rowLp);

            eu.kodanetwork.mchost.util.SleekTouch.apply(row, () -> {
                sheet.dismiss();
                android.content.Intent i = new android.content.Intent(MainActivity.this, ServerDetailActivity.class);
                i.putExtra("id", s.getId());
                i.putExtra("OPEN_TAB", "files");
                startActivity(i);
            }, 50);
        }

        sheet.setContentView(content);
        sheet.getBehavior().setPeekHeight((int)(380 * d));
        eu.kodanetwork.mchost.util.SheetFix.apply(sheet);
        sheet.show();
    }

    private void updateSleekStats() {
        // Custom (sleek) mode only — the legacy list keeps its own adapter/cards
        if (!eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) return;
        try {
            java.util.List<ServerInstance> all = repo != null ? repo.all() : java.util.Collections.emptyList();

            // Sleek adapter: set once, update list on changes
            View rvView = findViewById(R.id.rv);
            if (rvView instanceof androidx.recyclerview.widget.RecyclerView && rvView.getVisibility() == View.VISIBLE) {
                androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) rvView;
                if (rv.getAdapter() == null || !(rv.getAdapter() instanceof eu.kodanetwork.mchost.ui.SleekServerAdapter)) {
                    rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                    rv.setAdapter(new eu.kodanetwork.mchost.ui.SleekServerAdapter(all, server -> {
                        android.content.Intent i = new android.content.Intent(MainActivity.this, ServerDetailActivity.class);
                        i.putExtra("id", server.getId());
                        startActivity(i);
                    }));
                } else {
                    ((eu.kodanetwork.mchost.ui.SleekServerAdapter) rv.getAdapter()).update(all);
                }
            }
            int online = 0;
            long totalRam = 0;
            for (ServerInstance s : all) {
                if (s.state == ServerInstance.State.ONLINE) online++;
                totalRam += s.getRamMB();
            }
            TextView tvTotal = findViewById(R.id.tv_stat_total);
            TextView tvOnline = findViewById(R.id.tv_stat_online);
            TextView tvRam = findViewById(R.id.tv_stat_ram);
            if (tvTotal != null) tvTotal.setText(String.valueOf(all.size()));
            if (tvOnline != null) tvOnline.setText(String.valueOf(online));
            if (tvRam != null) tvRam.setText((totalRam / 1024) + "GB");

            TextView tvCount = findViewById(R.id.tv_server_count);
            if (tvCount != null) tvCount.setText(all.size() + " SERVERS");

            View empty = findViewById(R.id.tv_empty);
            View rv = findViewById(R.id.rv);
            if (empty != null && rv != null) {
                boolean hasServers = !all.isEmpty();
                empty.setVisibility(hasServers ? View.GONE : View.VISIBLE);
                rv.setVisibility(hasServers ? View.VISIBLE : View.GONE);
            }
        } catch (Exception ignored) {}
    }
}
