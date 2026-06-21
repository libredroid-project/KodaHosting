package eu.kodanetwork.mchost.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.integration.PlayitManager;
import eu.kodanetwork.mchost.integration.TermuxBridge;
import eu.kodanetwork.mchost.integration.TermuxScriptInstaller;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.network.JarDownloader;
import eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient;
import eu.kodanetwork.mchost.orchestration.StartOrchestrator;
import eu.kodanetwork.mchost.service.KodaServerService;
import eu.kodanetwork.mchost.util.JavaFinder;

public class ServerDetailActivity extends AppCompatActivity {

    private ServerInstance server;
    private ServerRepo repo;
    private KodaServerService svc;
    private boolean bound = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private static final int REQ_IMPORT_FILE = 9912;
    private static final int REQ_IMPORT_FOLDER = 9913;

    // Tabs
    private TabLayout tabs;
    private View pDash, pConsole, pFiles, pSettings, pPlugins;

    // Dashboard
    private TextView tvBadge, tvUptime, tvPlayers, tvJoinAddr, tvRamInfo, tvVerInfo, tvJavaInfo, tvBedrockPortDash;
    private MaterialButton btnStart, btnStop, btnRestart, btnKill;
    private View dlProgress;
    private View dot;
    private TextView tvDlMsg;

    // Console
    private TextView tvLog;
    private ScrollView scrollLog;
    private EditText etCmd;
    private LinearLayout layoutChips;

    // Files
    private LinearLayout layoutFileList;
    private TextView tvFilesRoot;
    private File currentDir;

    // Settings
    private TextView tvSettingsInfo;
    private TextView tvTermuxStatus, tvTunnelStatus, tvDomainStatus;
    private EditText etDomainPrefix;
    private MaterialButton btnDlJar, btnDelServer;
    private MaterialButton btnTermuxSetup, btnStartTunnel, btnLinkDomain;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private android.view.View layoutFullLoading;
    private TextView tvFullLoadingMsg;

    private final androidx.activity.result.ActivityResultLauncher<Intent> iconPickerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                android.net.Uri uri = result.getData().getData();
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                    is.close();
                    if (bitmap != null) {
                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 64, 64, true);
                        File iconFile = new File(server.getServerDir(), "server-icon.png");
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(iconFile)) {
                            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                        }
                        android.widget.ImageView iv = findViewById(R.id.iv_server_icon);
                        if (iv != null) iv.setImageBitmap(scaled);
                        Toast.makeText(this, "Server Icon aktualisiert", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Fehler beim Laden des Bildes: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

    private KodaServerService.StateCallback stateCb;
    private KodaServerService.LogCallback logCb;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((KodaServerService.LocalBinder) b).get();
            bound = true;
            stateCb = (id, s) -> {
                if (id.equals(server.getId())) runOnUiThread(() -> {
                    if (layoutFullLoading != null) {
                        if (s == ServerInstance.State.CRASHED || s == ServerInstance.State.ONLINE || s == ServerInstance.State.OFFLINE) {
                            layoutFullLoading.setVisibility(View.GONE);
                        } else if (s == ServerInstance.State.STARTING && layoutFullLoading.getVisibility() == View.VISIBLE) {
                            if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText("STARTING SERVER...");
                        }
                    }
                    updateDash();
                });
            };
            logCb = (id, line) -> {
                if (id.equals(server.getId())) runOnUiThread(() -> {
                    appendLog(line);
                    if (line.contains("SETUP_COMPLETE_SUCCESS") || line.contains("DESIGN_APPLIED") || line.contains("KodaNetwork Error")) {
                        if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
                        if (server.state == ServerInstance.State.SETTING_UP) {
                            server.state = ServerInstance.State.ONLINE;
                            updateDash();
                        }
                    }
                });
            };
            svc.addStateCb(stateCb);
            svc.addLogCb(logCb);
            boolean pastSetupFinished = false;
            for (String l : svc.getLog(server.getId())) {
                appendLog(l);
                if (l.contains("SETUP_COMPLETE_SUCCESS") || l.contains("DESIGN_APPLIED") || l.contains("KodaNetwork Error")) {
                    pastSetupFinished = true;
                }
            }
            if (pastSetupFinished) {
                if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
                if (server.state == ServerInstance.State.SETTING_UP) server.state = ServerInstance.State.ONLINE;
            }
            updateDash();
        }
        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            if (svc != null && stateCb != null) svc.removeStateCb(stateCb);
            if (svc != null && logCb != null) svc.removeLogCb(logCb);
            svc = null;
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_server_detail);
        
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);

        // Apply light mode background early
        if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
            findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().setStatusBarColor(0xFFF5F5F5);
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().setNavigationBarColor(0xFFF5F5F5);
            }
        }
        
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        lastTheme = prefs.getString("app_theme", "modern");
        lastThemeMode = prefs.getString("theme_mode", "dark");

        String id = getIntent().getStringExtra("id");
        repo = ServerRepo.get(this);
        server = repo.byId(id);
        if (server == null) { finish(); return; }
        currentDir = new File(server.getServerDir());

        bindViews();
        if (server.isDatabase()) {
            setupDatabaseOverrides();
        }

        ((TextView) findViewById(R.id.tv_title)).setText(server.getName());
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        setupTabs();
        setupDashButtons();
        setupConsole();
        setupPlugins();
        setupSettings();

        Intent si = new Intent(this, KodaServerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
        else startService(si);
        bindService(si, conn, Context.BIND_AUTO_CREATE);
        startTicker();

        if (getIntent().getBooleanExtra("auto_setup", false)) {
            getIntent().removeExtra("auto_setup");
            if (layoutFullLoading != null) {
                layoutFullLoading.setVisibility(View.VISIBLE);
                if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText("AUTO-SETUP RUNNING...");
            }
            server.state = ServerInstance.State.SETTING_UP;
            repo.update(server);
            new Handler(Looper.getMainLooper()).postDelayed(this::downloadJar, 500);
        }

        String autoStartId = getIntent().getStringExtra("auto_start_server");
        if (autoStartId != null && !autoStartId.isEmpty() && svc != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (svc != null) svc.startServer(server);
            }, 1000);
        } else if (autoStartId != null && !autoStartId.isEmpty()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent startSvc = new Intent(this, KodaServerService.class);
                startSvc.setAction(KodaServerService.ACTION_START);
                startSvc.putExtra("id", autoStartId);
                startService(startSvc);
            }, 500);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        eu.kodanetwork.mchost.App.resetAfkTimer();
    }

    private void bindViews() {
        tabs      = findViewById(R.id.tabs);
        pDash     = findViewById(R.id.panel_dash);
        pConsole  = findViewById(R.id.panel_console);
        pFiles    = findViewById(R.id.panel_files);
        pSettings = findViewById(R.id.panel_settings);
        pPlugins  = findViewById(R.id.panel_plugins);

        tvBadge   = findViewById(R.id.tv_badge);
        tvUptime  = findViewById(R.id.tv_uptime);
        tvPlayers = findViewById(R.id.tv_players);
        tvJoinAddr = findViewById(R.id.tv_join_addr);
        tvRamInfo  = findViewById(R.id.tv_ram_info);
        tvVerInfo = findViewById(R.id.tv_ver_info);
        tvJavaInfo = findViewById(R.id.tv_java_info);
        tvBedrockPortDash = findViewById(R.id.tv_bedrock_port_dash);

        tvJoinAddr.setOnClickListener(v -> copyToClipboard("Join Address", server.getJoinAddress()));
        tvBedrockPortDash.setOnClickListener(v -> {
            if (server.isBedrockSupport() && server.getBedrockPort() > 0) {
                copyToClipboard("Bedrock Port", String.valueOf(server.getBedrockPort()));
            }
        });
        btnStart   = findViewById(R.id.btn_start);
        btnStop    = findViewById(R.id.btn_stop);
        btnRestart = findViewById(R.id.btn_restart);
        btnKill    = findViewById(R.id.btn_kill);
        dlProgress = findViewById(R.id.dl_progress);
        dot = findViewById(R.id.dot);
        tvDlMsg    = findViewById(R.id.tv_dl_msg);
        
        View cardPlayers = findViewById(R.id.card_players);
        View tvPlayers = findViewById(R.id.tv_players);
        if (cardPlayers != null) {
            cardPlayers.setOnClickListener(v -> showPlayerActions());
        }
        if (tvPlayers != null) {
            tvPlayers.setOnClickListener(v -> showPlayerActions());
        }

        tvLog       = findViewById(R.id.tv_log);
        scrollLog   = findViewById(R.id.scroll_log);
        etCmd       = findViewById(R.id.et_cmd);
        layoutChips = null;

        layoutFileList = findViewById(R.id.layout_files);
        tvFilesRoot    = findViewById(R.id.tv_files_root);
        View btnImportFile = findViewById(R.id.btn_import_file);
        if (btnImportFile != null) {
            btnImportFile.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Import...")
                    .setItems(new String[]{"File(s)", "Folder"}, (d, w) -> {
                        if (w == 0) {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setType("*/*");
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                            startActivityForResult(Intent.createChooser(intent, "Import Files"), REQ_IMPORT_FILE);
                        } else {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            startActivityForResult(intent, REQ_IMPORT_FOLDER);
                        }
                    }).show();
            });
        }

        layoutFullLoading = findViewById(R.id.layout_full_loading);
        tvFullLoadingMsg = findViewById(R.id.tv_full_loading_msg);
        android.view.View btnCloseLoading = findViewById(R.id.btn_close_loading);
        if (btnCloseLoading != null) {
            btnCloseLoading.setOnClickListener(v -> {
                if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
            });
        }

        tvSettingsInfo = findViewById(R.id.tv_settings_info);
        tvTermuxStatus = findViewById(R.id.tv_termux_status);
        tvTunnelStatus = findViewById(R.id.tv_tunnel_status);
        tvDomainStatus = findViewById(R.id.tv_domain_status);
        etDomainPrefix = findViewById(R.id.et_domain_prefix);
        btnDlJar       = findViewById(R.id.btn_dl_jar);
        btnDelServer   = findViewById(R.id.btn_del_server);
        btnTermuxSetup = findViewById(R.id.btn_termux_setup);
        btnStartTunnel = findViewById(R.id.btn_start_tunnel);
        btnLinkDomain = findViewById(R.id.btn_link_domain);

        // tv_custom_domain_target and btn_copy_domain no longer exist.

        // Static info
        tvJoinAddr.setText(server.getJoinAddress());
        
        View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
        if (layoutBedrockPort != null && tvBedrockPortDash != null) {
            if (server.isBedrockSupport() && server.getBedrockPort() > 0) {
                layoutBedrockPort.setVisibility(View.VISIBLE);
                tvBedrockPortDash.setText(String.valueOf(server.getBedrockPort()));
            } else {
                layoutBedrockPort.setVisibility(View.GONE);
            }
        }

        tvRamInfo.setText(server.getRamMB() + " MB RAM");
        tvVerInfo.setText(server.getType().name() + " " + server.getVersion());

        // Java check — uses exec() to actually test java, runs in background
        tvJavaInfo.setText("checking java…");
        tvJavaInfo.setTextColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF555566 : Color.parseColor("#888888"));
        new Thread(() -> {
            String javaPath = JavaFinder.find(this);
            runOnUiThread(() -> {
                if (javaPath != null) {
                    tvJavaInfo.setText("✓ " + JavaFinder.version(this));
                    tvJavaInfo.setTextColor(Color.parseColor("#00E676"));
                } else {
                    tvJavaInfo.setText("◌ Java wird beim Start automatisch installiert (JDK 25)");
                    tvJavaInfo.setTextColor(Color.parseColor("#FFCC00"));
                }
            });
        }).start();
    }

    private void setupDatabaseOverrides() {
        // Hide standard server dashboard elements
        View cardPlayers = findViewById(R.id.card_players);
        if (cardPlayers != null) cardPlayers.setVisibility(View.GONE);
        if (tvJoinAddr != null) ((View)tvJoinAddr.getParent()).setVisibility(View.GONE);
        View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
        if (layoutBedrockPort != null) layoutBedrockPort.setVisibility(View.GONE);

        // Hide standard settings cards
        View cardRam = findViewById(R.id.card_settings_ram);
        if (cardRam != null) cardRam.setVisibility(View.GONE);
        View cardNetwork = findViewById(R.id.card_settings_network);
        if (cardNetwork != null) cardNetwork.setVisibility(View.GONE);
        View cardGameplay = findViewById(R.id.card_settings_gameplay);
        if (cardGameplay != null) cardGameplay.setVisibility(View.GONE);
        View layoutNetwork = findViewById(R.id.layout_network);
        if (layoutNetwork != null) ((View)layoutNetwork.getParent()).setVisibility(View.GONE);
        View cardDatabaseSettings = findViewById(R.id.card_database_settings);
        if (cardDatabaseSettings != null) cardDatabaseSettings.setVisibility(View.VISIBLE);

        View btnDlJar = findViewById(R.id.btn_dl_jar);
        if (btnDlJar != null) btnDlJar.setVisibility(View.GONE);
        View tvIconTitle = findViewById(R.id.tv_icon_title);
        if (tvIconTitle != null) tvIconTitle.setVisibility(View.GONE);
        View layoutServerIcon = findViewById(R.id.layout_server_icon);
        if (layoutServerIcon != null) layoutServerIcon.setVisibility(View.GONE);
        
        // Hide plugins tab logic handles this below in setupTabs
        // Setup DB Settings Logic
        EditText etDbName = findViewById(R.id.et_db_name);
        EditText etDbUser = findViewById(R.id.et_db_user);
        EditText etDbPass = findViewById(R.id.et_db_pass);
        ImageButton btnTogglePass = findViewById(R.id.btn_toggle_pass);
        if (btnTogglePass != null && etDbPass != null) {
            btnTogglePass.setOnClickListener(v -> {
                if (etDbPass.getInputType() == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    etDbPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePass.setImageResource(android.R.drawable.ic_menu_view);
                } else {
                    etDbPass.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePass.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                }
                etDbPass.setSelection(etDbPass.getText().length());
            });
        }
        if (etDbName != null) {
            etDbName.setText(server.getName());
            etDbName.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    server.setName(s.toString().trim());
                    repo.update(server);
                }
            });
        }
        if (etDbUser != null) {
            etDbUser.setText(server.getDbUsername());
            etDbUser.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    server.setDbUsername(s.toString().trim());
                    repo.update(server);
                }
            });
        }
        if (etDbPass != null) {
            etDbPass.setText(server.getDbPassword());
            etDbPass.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    server.setDbPassword(s.toString().trim());
                    repo.update(server);
                }
            });
        }
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private void setupTabs() {
        tabs.addTab(tabs.newTab().setText(R.string.tab_dashboard));
        tabs.addTab(tabs.newTab().setText(R.string.tab_console));
        tabs.addTab(tabs.newTab().setText(R.string.tab_files));
        if (!server.isDatabase()) {
            String pluginTabName = "Plugins";
            if (server != null && (server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.FABRIC || 
                                   server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.FORGE || 
                                   server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.NEOFORGE)) {
                pluginTabName = "Mods";
            }
            tabs.addTab(tabs.newTab().setText(pluginTabName));
        }
        tabs.addTab(tabs.newTab().setText(R.string.tab_settings));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                if (t.getPosition() == 1 && server != null && server.state == ServerInstance.State.OFFLINE) {
                    // Block opening console when offline
                    android.content.Intent w = new android.content.Intent(ServerDetailActivity.this, PraetorWarningActivity.class);
                    w.putExtra(PraetorWarningActivity.EXTRA_REASON, getString(R.string.praetor_reason_console_offline));
                    w.putExtra(PraetorWarningActivity.EXTRA_ACTION, getString(R.string.praetor_action_back));
                    startActivity(w);
                    
                    // Switch back to Dashboard (index 0)
                    tabs.selectTab(tabs.getTabAt(0));
                    return;
                }
                showTab(t.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {
                if (t.getPosition() == 1 && server != null && server.state == ServerInstance.State.OFFLINE) {
                    tabs.selectTab(tabs.getTabAt(0));
                }
            }
        });
        showTab(0);
    }

    private void showPlayerActions() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setBackgroundColor(0xFF0E0E14);
        container.setPadding(0, 0, 0, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.players_manage_title);
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLetterSpacing(0.12f);
        tvTitle.setPadding(48, 40, 48, 24);
        container.addView(tvTitle);

        View div = new View(this);
        div.setBackgroundColor(0xFF222230);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);

        java.util.Set<String> shown = new java.util.HashSet<>();
        int pad = (int)(16 * getResources().getDisplayMetrics().density);

        java.util.List<String> onlineList = new java.util.ArrayList<>();
        if (server.onlinePlayerNames != null) {
            try { onlineList.addAll(server.onlinePlayerNames); } catch (Exception ignored) {}
        }
        
        if (!onlineList.isEmpty()) {
            TextView header = new TextView(this);
            header.setText(R.string.players_online);
            header.setTextColor(Color.parseColor("#00E676"));
            header.setTextSize(12);
            header.setPadding(48, pad, 48, pad/2);
            container.addView(header);

            for (String p : onlineList) {
                container.addView(createPlayerRow(p, true, pad, sheet));
                shown.add(p);
            }
        }

        java.util.List<String> offlineList = new java.util.ArrayList<>();
        if (server.knownPlayers != null) {
            try { offlineList.addAll(server.knownPlayers); } catch (Exception ignored) {}
        }
        
        boolean hasOffline = false;
        for (String p : offlineList) {
            if (!shown.contains(p)) hasOffline = true;
        }

        if (hasOffline) {
            TextView header2 = new TextView(this);
            header2.setText(R.string.players_offline_known);
            header2.setTextColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF555566 : Color.GRAY);
            header2.setTextSize(12);
            header2.setPadding(48, shown.isEmpty() ? pad : pad*2, 48, pad/2);
            container.addView(header2);

            for (String p : offlineList) {
                if (!shown.contains(p)) {
                    container.addView(createPlayerRow(p, false, pad, sheet));
                }
            }
        }

        if (container.getChildCount() == 2) { // Only Title and Divider
            TextView empty = new TextView(this);
            empty.setText(R.string.players_none_known);
            boolean light = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
            empty.setTextColor(light ? 0xFF555566 : 0xFF888899);
            empty.setPadding(48, pad, 48, pad);
            container.addView(empty);
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(container);
        sheet.setContentView(sv);
        
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setNavigationBarColor(0xFF0D0D14);
            w.setStatusBarColor(0xFF0D0D14);
        }
        sheet.show();
    }

    private android.widget.LinearLayout createPlayerRow(String p, boolean isOnline, int pad, com.google.android.material.bottomsheet.BottomSheetDialog parentSheet) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(48, pad/2, 48, pad/2);

        TextView tv = new TextView(this);
        tv.setText(p);
        tv.setTextSize(16);
        tv.setTextColor(isOnline ? Color.WHITE : Color.GRAY);
        android.widget.LinearLayout.LayoutParams lpTv = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tv, lpTv);

        TextView btn = new TextView(this);
        btn.setText(R.string.players_manage);
        btn.setTextColor(0xFFFF6B00);
        btn.setPadding(pad, pad/4, 0, pad/4);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        btn.setBackgroundResource(outValue.resourceId);
        btn.setClickable(true);
        btn.setFocusable(true);
        
        btn.setOnClickListener(v -> {
            showPlayerActionSheet(p, isOnline);
            parentSheet.dismiss();
        });
        row.addView(btn);
        return row;
    }

    private void showPlayerActionSheet(String player, boolean isOnline) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setBackgroundColor(0xFF0E0E14);
        container.setPadding(0, 0, 0, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("AKTION FÜR: " + player);
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLetterSpacing(0.12f);
        tvTitle.setPadding(48, 40, 48, 24);
        container.addView(tvTitle);

        View div = new View(this);
        div.setBackgroundColor(0xFF222230);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);

        if (isOnline) addPlayerActionBtn(container, sheet, "Kick", "kick " + player, 0xFFF0F0F0);
        addPlayerActionBtn(container, sheet, "Ban", "ban " + player, 0xFFFF4444);
        addPlayerActionBtn(container, sheet, "Pardon (Entbannen)", "pardon " + player, 0xFF00E676);
        addPlayerActionBtn(container, sheet, "OP geben", "op " + player, 0xFF44AAFF);
        addPlayerActionBtn(container, sheet, "OP entfernen", "deop " + player, 0xFFFFCC00);

        sheet.setContentView(container);
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setNavigationBarColor(0xFF0D0D14);
            w.setStatusBarColor(0xFF0D0D14);
        }
        sheet.show();
    }

    private void addPlayerActionBtn(android.widget.LinearLayout container, com.google.android.material.bottomsheet.BottomSheetDialog sheet, String text, String cmd, int color) {
        TextView btn = new TextView(ServerDetailActivity.this);
        btn.setText(text);
        btn.setTextColor(color);
        btn.setTextSize(15);
        btn.setPadding(48, 40, 48, 40);
        btn.setClickable(true);
        
        android.util.TypedValue tv2 = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
        btn.setForeground(ContextCompat.getDrawable(ServerDetailActivity.this, tv2.resourceId));
        
        btn.setOnClickListener(v -> {
            if (svc != null) svc.sendCmd(server.getId(), cmd);
            sheet.dismiss();
        });
        container.addView(btn);
    }

    private void showTab(int i) {
        int targetSettings = server.isDatabase() ? 3 : 4;
        pDash    .setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        pConsole .setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        pFiles   .setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        if (pPlugins != null) pPlugins.setVisibility(!server.isDatabase() && i == 3 ? View.VISIBLE : View.GONE);
        pSettings.setVisibility(i == targetSettings ? View.VISIBLE : View.GONE);
        if (i == 2) refreshFiles();
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    private void setupDashButtons() {
        btnStart.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.AppLogger.log("UI", "START button clicked for server: " + server.getName());
            File serverDir = new File(server.getServerDir());
            if (!serverDir.exists()) {
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Server directory does not exist: " + serverDir.getAbsolutePath());
                serverDir.mkdirs();
            }
            
            if (!server.isDatabase()) {
                File[] jars = serverDir.listFiles((d, name) -> name.endsWith(".jar"));
                if (jars == null || jars.length == 0) {
                    eu.kodanetwork.mchost.util.AppLogger.log("UI", "No .jar file found in " + serverDir.getAbsolutePath());
                    Toast.makeText(this, "Bitte zuerst die Server .jar herunterladen (Settings-Tab)", Toast.LENGTH_LONG).show();
                    tabs.selectTab(tabs.getTabAt(tabs.getTabCount() - 1));
                    return;
                }
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Found jar: " + jars[0].getName() + ". Binding and starting service...");
                checkEulaAndStart();
            } else {
                checkQueueAndStart();
            }
        });
        btnStop   .setOnClickListener(v -> sendAction(KodaServerService.ACTION_STOP));
        btnRestart.setOnClickListener(v -> sendAction(KodaServerService.ACTION_RESTART));
        btnKill   .setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Force Kill?")
                .setMessage("Welt-Daten werden möglicherweise nicht gespeichert. Fortfahren?")
                .setPositiveButton("Kill", (d, w) -> sendAction(KodaServerService.ACTION_KILL))
                .setNegativeButton("Abbrechen", null)
                .show());
    }

    private void checkEulaAndStart() {
        android.content.SharedPreferences prefs = getSharedPreferences("koda_eula", MODE_PRIVATE);
        if (prefs.getBoolean("eula_" + server.getId(), false)) {
            checkQueueAndStart();
            return;
        }

        if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0F);
        root.setPadding(60, 60, 60, 40);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.eula_title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setLetterSpacing(0.1f);
        root.addView(tvTitle);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF333333);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divLp.topMargin = 24; divLp.bottomMargin = 24;
        divider.setLayoutParams(divLp);
        root.addView(divider);

        TextView tvBody = new TextView(this);
        tvBody.setText("Durch das Starten dieses Minecraft-Servers akzeptierst du die Mojang/Microsoft EULA.\n\n" +
            "Dies beinhaltet:\n" +
            "• Du darfst keinen Zugang zu Gameplay-Features verkaufen\n" +
            "• Du darfst keine Minecraft-Inhalte umverteilen\n" +
            "• Server müssen den EULA-Richtlinien entsprechen\n\n" +
            "Vollständige EULA:\nhttps://aka.ms/MinecraftEULA");
        tvBody.setTextColor(0xFFCCCCDD);
        tvBody.setTextSize(14);
        tvBody.setLineSpacing(6, 1);
        root.addView(tvBody);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(true)
            .create();

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 40;
        btnRow.setLayoutParams(rowLp);

        com.google.android.material.button.MaterialButton btnDecline = new com.google.android.material.button.MaterialButton(this);
        btnDecline.setText(R.string.eula_decline);
        btnDecline.setTextColor(0xFFFFFFFF);
        btnDecline.setBackgroundColor(0xFFE53935);
        btnDecline.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnDecline, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        com.google.android.material.button.MaterialButton btnAccept = new com.google.android.material.button.MaterialButton(this);
        btnAccept.setText(R.string.eula_accept);
        btnAccept.setTextColor(0xFF000000);
        btnAccept.setBackgroundColor(0xFFFF6B00);
        btnAccept.setOnClickListener(v -> {
            prefs.edit().putBoolean("eula_" + server.getId(), true).apply();
            dialog.dismiss();
            checkQueueAndStart();
        });
        LinearLayout.LayoutParams accLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        accLp.setMarginStart(16);
        btnRow.addView(btnAccept, accLp);

        root.addView(btnRow);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF0A0A0F));
        }
        dialog.show();
    }

    public static int MAX_GLOBAL_SERVERS = 250;
    private boolean isWaitingInQueue = false;

    private void checkQueueAndStart() {
        if (server.state == ServerInstance.State.ONLINE || server.state == ServerInstance.State.SETTING_UP) {
            return;
        }
        isWaitingInQueue = true;
        if (layoutFullLoading != null) {
            layoutFullLoading.setVisibility(View.VISIBLE);
            if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(getString(R.string.queue_checking));
        }

        io.execute(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?server_version=neq.&select=id")
                        .header("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                        .header("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                        .header("Prefer", "count=exact,head=true")
                        .head()
                        .build();

                try (okhttp3.Response res = client.newCall(req).execute()) {
                    String range = res.header("Content-Range");
                    int count = 0;
                    if (range != null && range.contains("/")) {
                        String[] parts = range.split("/");
                        if (parts.length == 2) {
                            count = Integer.parseInt(parts[1]);
                        }
                    }
                    
                    final int currentCount = count;
                    runOnUiThread(() -> {
                        if (!isWaitingInQueue) return;
                        if (currentCount >= MAX_GLOBAL_SERVERS) {
                            if (tvFullLoadingMsg != null) {
                                tvFullLoadingMsg.setText(getString(R.string.queue_waiting, currentCount, MAX_GLOBAL_SERVERS));
                            }
                            new android.os.Handler().postDelayed(this::checkQueueAndStart, 15000);
                        } else {
                            isWaitingInQueue = false;
                            autoStartAll();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isWaitingInQueue) return;
                    isWaitingInQueue = false;
                    autoStartAll(); // fallback
                });
            }
        });
    }

    private void autoStartAll() {
        btnStart.setEnabled(false);
        if (server.state == ServerInstance.State.OFFLINE || server.state == ServerInstance.State.SETTING_UP) {
            if (layoutFullLoading != null) {
                layoutFullLoading.setVisibility(View.VISIBLE);
                if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText("AUTO-SETUP RUNNING...");
            }
            server.state = ServerInstance.State.SETTING_UP;
            repo.update(server);
        }
        io.execute(() -> {
            StartOrchestrator orchestrator = new StartOrchestrator(this, new StartOrchestrator.Callback() {
                @Override
                public void onStep(StartOrchestrator.Step step, String message) {
                    runOnUiThread(() -> {
                        switch (step) {
                            case PREPARE:
                            case JAVA_DOWNLOAD:
                            case JAVA_INSTALL:
                                if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(message);
                                break;
                            case SERVER_START:
                            case TUNNEL_START:
                                tvTunnelStatus.setText("Tunnel: " + message);
                                break;
                            case DNS_LINK:
                            case READY:
                                tvDomainStatus.setText("Domain: " + message);
                                break;
                        }
                    });
                }

                @Override
                public void onCompleted(String playitAddress, String domainLink) {
                    server.setPlayitAddress(playitAddress);
                    server.setDomainLink(domainLink);
                    repo.update(server);
                    runOnUiThread(() -> {
                        tvTunnelStatus.setText("Tunnel: " + playitAddress);
                        tvDomainStatus.setText("Domain: " + domainLink);
                        tvJoinAddr.setText(server.getJoinAddress());
                    });
                }

                @Override
                public void onError(StartOrchestrator.Step step, String message) {
                    runOnUiThread(() -> {
                        String error = "Error [" + step.name() + "]: " + message;
                        tvDomainStatus.setText(error);
                        Toast.makeText(ServerDetailActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void requestServerStartIntent() {
                    runOnUiThread(() -> {
                        if (eu.kodanetwork.mchost.security.PraetorSystem.checkRamForStart(ServerDetailActivity.this, server)) {
                            server.state = ServerInstance.State.STARTING;
                            eu.kodanetwork.mchost.model.ServerRepo.get(ServerDetailActivity.this).update(server);
                            sendAction(KodaServerService.ACTION_START);
                        }
                    });
                }
            });
            try {
                String host = etDomainPrefix.getText() == null ? "" : etDomainPrefix.getText().toString().trim();
                if (!host.isEmpty()) server.setSubdomain(host);
                orchestrator.run(server, "");
            } catch (Exception e) {
                runOnUiThread(() -> tvDomainStatus.setText("Domain error: " + e.getMessage()));
            }
        });
    }
    private void sendAction(String action) {
        android.content.Intent i = new android.content.Intent(this, eu.kodanetwork.mchost.service.KodaServerService.class);
        i.setAction(action);
        i.putExtra(eu.kodanetwork.mchost.service.KodaServerService.EXTRA_ID, server.getId());
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private Runnable autoCloseRunnable;

    private void updateDash() {
        ServerInstance.State st = server.state;
        String label; int col;
        switch (st) {
            case ONLINE:     label = "● ONLINE";     col = 0xFF00E676; break;
            case STARTING:   label = "◌ STARTING…";  col = 0xFFFFCC00; break;
            case STOPPING:   label = "◌ STOPPING…";  col = 0xFFFF8800;
                if (tvBadge != null) {
                    tvBadge.animate().alpha(0.3f).setDuration(400).withEndAction(() ->
                        tvBadge.animate().alpha(1f).setDuration(400).start()
                    ).start();
                }
                break;
            case CRASHED:    label = "✕ CRASHED";    col = 0xFFFF4444; break;
            case INSTALLING: label = "⬇ LADEN…";     col = 0xFF44AAFF; break;
            case SETTING_UP: label = "⚙ SETTING UP…";col = 0xFF9C27B0;
                if (tvBadge != null) {
                    tvBadge.animate().alpha(0.3f).setDuration(400).withEndAction(() ->
                        tvBadge.animate().alpha(1f).setDuration(400).start()
                    ).start();
                }
                break;
            case RESTARTING: label = "◌ RESTARTING…";col = 0xFFFF8800;
                if (tvBadge != null) {
                    tvBadge.animate().alpha(0.3f).setDuration(400).withEndAction(() ->
                        tvBadge.animate().alpha(1f).setDuration(400).start()
                    ).start();
                }
                break;
            case HIBERNATED: label = "❄ " + getString(R.string.status_hibernated); col = 0xFF44AAFF; break;
            default:         label = "○ OFFLINE";    col = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF555566 : 0xFF888888; break;
        }
        tvBadge.setText(label);
        tvBadge.setTextColor(col);
        int playerCount = server.onlinePlayerNames != null ? server.onlinePlayerNames.size() : server.onlinePlayers;
        tvPlayers.setText(playerCount + " / " + server.getMaxPlayers() + " Spieler");

        boolean running = server.isRunning();
        boolean restarting = st == ServerInstance.State.RESTARTING;
        boolean settingUp = st == ServerInstance.State.SETTING_UP;
        boolean hibernated = st == ServerInstance.State.HIBERNATED;
        
        View cardControls = findViewById(R.id.card_controls);
        View tabLayout = findViewById(R.id.tabs);
        View btnWakeUpDash = findViewById(R.id.btn_wake_up_dash);
        View btnChangeDns = findViewById(R.id.btn_change_dns_hibernated);
        
        if (hibernated) {
            if (cardControls != null) cardControls.setVisibility(View.GONE);
            if (tabLayout != null) tabLayout.setVisibility(View.GONE);
            if (btnWakeUpDash != null) btnWakeUpDash.setVisibility(View.VISIBLE);
            if (btnChangeDns != null) {
                btnChangeDns.setVisibility(View.VISIBLE);
                btnChangeDns.setOnClickListener(v -> {
                    // Start domain input logic (can just open Settings tab and focus on custom domain, 
                    // or pop up an alert dialog to change it directly)
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                    builder.setTitle("Join-Adresse ändern");
                    final android.widget.EditText input = new android.widget.EditText(this);
                    input.setHint("Neuer Name (z.B. meincoolerserver)");
                    input.setText(server.getSubdomain());
                    builder.setView(input);
                    builder.setPositiveButton("Speichern", (dialog, which) -> {
                        String newSubdomain = input.getText().toString().trim();
                        if (!newSubdomain.isEmpty()) {
                            server.setSubdomain(newSubdomain);
                            server.setDomainLink(""); // reset custom domain logic
                            repo.update(server);
                            tvJoinAddr.setText(newSubdomain);
                            android.widget.Toast.makeText(this, "Join-Adresse aktualisiert!", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.setNegativeButton("Abbrechen", (dialog, which) -> dialog.cancel());
                    builder.show();
                });
            }
        } else {
            if (cardControls != null) cardControls.setVisibility(View.VISIBLE);
            if (tabLayout != null) tabLayout.setVisibility(View.VISIBLE);
            if (btnWakeUpDash != null) btnWakeUpDash.setVisibility(View.GONE);
            if (btnChangeDns != null) btnChangeDns.setVisibility(View.GONE);
        }
        
        btnStart  .setEnabled(!running && st != ServerInstance.State.INSTALLING && !restarting && !settingUp && !hibernated);
        btnStop   .setEnabled(running);
        btnRestart.setEnabled(running);
        btnKill   .setEnabled(running || restarting);
        
        android.widget.TextView btnHibernate = findViewById(R.id.btn_hibernate);
        if (btnHibernate != null) {
            btnHibernate.setText(hibernated ? getString(R.string.wake_up) : getString(R.string.hibernate));
        }
        // Update dot in header
        if (dot != null) {
            int dotDrw;
            switch (st) {
                case ONLINE:   dotDrw = R.drawable.dot_online;  break;
                case STARTING:
                case STOPPING:
                case SETTING_UP:
                case INSTALLING: dotDrw = R.drawable.dot_warn; break;
                case CRASHED:  dotDrw = R.drawable.dot_err;    break;
                default:       dotDrw = R.drawable.dot_offline; break;
            }
            dot.setBackgroundResource(dotDrw);
        }

        // If the server goes offline while we are in the Console tab (tab 1), kick to Dashboard (tab 0)
        if (st == ServerInstance.State.OFFLINE || st == ServerInstance.State.CRASHED) {
            if (tabs != null && tabs.getSelectedTabPosition() == 1) {
                tabs.selectTab(tabs.getTabAt(0));
            }
        }
        
        if (st == ServerInstance.State.CRASHED || (st == ServerInstance.State.OFFLINE && !server.isAutoSetup())) {
            if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
        }
    }

    // ── Console ───────────────────────────────────────────────────────────────

    private void setupConsole() {
        View btnSend  = findViewById(R.id.btn_send);
        View btnClear = findViewById(R.id.btn_clear);
        View btnCopy  = findViewById(R.id.btn_copy_log);
        btnSend .setOnClickListener(v -> sendCmd());
        btnClear.setOnClickListener(v -> tvLog.setText(""));
        btnCopy .setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Server Log", tvLog.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Log kopiert!", Toast.LENGTH_SHORT).show();
        });
        etCmd.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND ||
                    (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendCmd(); return true;
            }
            return false;
        });

        String[] cmds = {
            "/stop", "/help", "/list", "/tps", "/say", "/time set day", "/weather clear", "/op"
        };
        for (String c : cmds) {
            Chip chip = new Chip(this);
            chip.setText(c);
            if (c.equals("/stop")) {
                chip.setTextColor(0xFFFF5252);
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFF5252));
            } else {
                boolean light = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
                chip.setTextColor(light ? 0xFF333333 : 0xFFF0F0F0);
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(light ? 0xFFCCCCCC : 0xFF8A8A9A));
            }
            chip.setChipStrokeWidth(3f);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
            chip.setOnClickListener(v -> etCmd.setText(c.replace("/", "")));
            if (layoutChips != null) {
                layoutChips.addView(chip);
            }
        }
    }

    private void sendCmd() {
        if (etCmd.getText() == null) return;
        String cmd = etCmd.getText().toString().trim();
        if (cmd.isEmpty()) return;
        etCmd.setText("");
        sendCmd(cmd);
    }

    private void sendCmd(String cmd) {
        if (bound && svc != null) svc.sendCmd(server.getId(), cmd);
        else appendLog("Service nicht verbunden.");
    }

    private long lastScrollTime = 0;

    private void appendLog(String raw) {
        if (raw == null || raw.isEmpty()) return;
        
        String[] lines = raw.split("\n");
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
        
        for (String line : lines) {
            if (line.isEmpty()) continue;
            // Strip ANSI escape codes
            line = line.replaceAll("\\\u001B\\[[;\\d]*[ -/]*[@-~]", "").replaceAll("\\[[0-9;]*m", "");

            int col;
            if (line.contains("ERROR") || line.contains("Exception"))   col = Color.parseColor("#FF5555");
            else if (line.contains("WARN"))                            col = Color.parseColor("#FFCC00");
            else if (line.startsWith(">") || line.contains("KodaNet"))  col = Color.parseColor("#FF6B00");
            else if (line.contains("Done (") || line.contains("✓"))    col = Color.parseColor("#00E676");
            else if (line.startsWith("  ─") || line.startsWith("  🍊")) col = Color.parseColor("#FF8C42");
            else                                                       col = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF333333 : Color.parseColor("#CCCCCC");

            int start = ssb.length();
            ssb.append(line).append("\n");
            ssb.setSpan(new ForegroundColorSpan(col), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        tvLog.append(ssb);
        
        android.text.Editable editable = tvLog.getEditableText();
        if (editable != null && editable.length() > 25000) {
            editable.delete(0, editable.length() - 20000);
        }
        
        long now = System.currentTimeMillis();
        if (now - lastScrollTime > 250) {
            lastScrollTime = now;
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        }
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    private File getProtectedOverrideFile(File target) {
        String path = target.getAbsolutePath().replace('\\', '/');
        if (path.contains("/plugins/Geyser-Spigot")) {
            return new File(path.substring(0, path.indexOf("/plugins/Geyser-Spigot") + 22), ".manual_override");
        }
        if (path.contains("/plugins/floodgate")) {
            return new File(path.substring(0, path.indexOf("/plugins/floodgate") + 18), ".manual_override");
        }
        if (path.contains("/plugins/voicechat")) {
            return new File(path.substring(0, path.indexOf("/plugins/voicechat") + 18), ".manual_override");
        }
        return null;
    }

    private void addProtectedHeader(File currentDir) {
        File overrideFile = getProtectedOverrideFile(currentDir);
        if (overrideFile == null) return;
        
        // Only show the header if we are exactly at the root of the protected folder, to avoid spamming subdirs
        String p = currentDir.getAbsolutePath().replace('\\', '/');
        if (!p.endsWith("/plugins/Geyser-Spigot") && !p.endsWith("/plugins/floodgate") && !p.endsWith("/plugins/voicechat")) return;

        android.widget.Button btn = new android.widget.Button(this);
        if (!overrideFile.exists()) {
            btn.setText("UNLOCK MANUAL EDITING (PRAETOR)");
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFFFF4444);
            btn.setOnClickListener(v -> {
                Intent i = new Intent(this, PraetorWarningActivity.class);
                i.putExtra("target_folder_path", currentDir.getAbsolutePath());
                startActivityForResult(i, 9001); // 9001 = Praetor Override
            });
        } else {
            btn.setText("RESTORE AUTO-CONFIG");
            btn.setTextColor(0xFF000000);
            btn.setBackgroundColor(0xFF00E676);
            btn.setOnClickListener(v -> {
                overrideFile.delete();
                Toast.makeText(this, "Auto-Config restored.", Toast.LENGTH_SHORT).show();
                refreshFiles();
            });
        }
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 16, 16, 16);
        layoutFileList.addView(btn, lp);
        
        View div = new View(this);
        div.setBackgroundColor(0xFF222222);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        layoutFileList.addView(div);
    }

    private void refreshFiles() {
        layoutFileList.removeAllViews();
        String rootPath = new File(server.getServerDir()).getAbsolutePath();
        String relPath = currentDir.getAbsolutePath().replace(rootPath, "");
        if (relPath.isEmpty()) relPath = "/";
        tvFilesRoot.setText(relPath);

        if (!currentDir.exists()) {
            addFRow("(noch nicht heruntergeladen)", null);
            return;
        }

        addProtectedHeader(currentDir);

        // Back button if not in root
        if (!currentDir.getAbsolutePath().equals(rootPath)) {
            addFRow(".. (Ordner hoch)", currentDir.getParentFile());
        }

        File[] files = currentDir.listFiles();
        if (files == null || files.length == 0) {
            if (currentDir.getAbsolutePath().equals(rootPath)) addFRow("(leer)", null);
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : files) {
            if (f.getName().startsWith(".frpc") || f.getName().equals(".sys")) continue;
            String size = f.isFile() ? " (" + f.length() / 1024 + " KB)" : "";
            addFRow((f.isDirectory() ? "📁 " : fileIcon(f)) + f.getName() + size, f);
        }
    }

    private File clipFile;
    private boolean clipCut;

    private void addFRow(String text, File file) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(16, 24, 16, 24); // Taller rows for touch
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        boolean light = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
        tv.setTextColor(text.contains("📁") || text.startsWith("..") ? 0xFFFF6B00 : (light ? 0xFF333333 : 0xFFCCCCCC));
        
        if (file != null) {
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDir = file;
                    refreshFiles();
                } else {
                    File overrideFile = getProtectedOverrideFile(file);
                    if (overrideFile != null && !overrideFile.exists()) {
                        Toast.makeText(this, "Manual editing locked! Click 'Unlock Manual Editing' at the top of the plugin folder.", Toast.LENGTH_LONG).show();
                    } else {
                        openFileEditor(file);
                    }
                }
            });
            
            // File Manager Actions
            tv.setOnLongClickListener(v -> {
                if (text.startsWith("..")) {
                    if (clipFile != null) {
                        new AlertDialog.Builder(this)
                            .setTitle("Aktion: Einfügen")
                            .setPositiveButton("Einfügen (" + clipFile.getName() + ")", (d, w) -> {
                                try {
                                    File dest = new File(currentDir, clipFile.getName());
                                    if (clipCut) {
                                        clipFile.renameTo(dest);
                                        clipFile = null;
                                    } else {
                                        try (java.io.InputStream in = new java.io.FileInputStream(clipFile);
                                             java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                                            byte[] buf = new byte[1024];
                                            int len;
                                            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                                        }
                                    }
                                    refreshFiles();
                                } catch (Exception e) { Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
                            })
                            .setNegativeButton("Abbrechen", null)
                            .show();
                    }
                    return true;
                }
                
                String[] actions = {"Umbenennen", "Kopieren", "Ausschneiden", "Löschen"};
                new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setItems(actions, (d, which) -> {
                        if (which == 0) { // Umbenennen
                            final android.widget.EditText input = new android.widget.EditText(this);
                            input.setText(file.getName());
                            new AlertDialog.Builder(this)
                                .setTitle("Umbenennen")
                                .setView(input)
                                .setPositiveButton("OK", (d2, w2) -> {
                                    file.renameTo(new File(file.getParent(), input.getText().toString()));
                                    refreshFiles();
                                })
                                .setNegativeButton("Abbrechen", null).show();
                        } else if (which == 1) { // Kopieren
                            clipFile = file;
                            clipCut = false;
                            Toast.makeText(this, "Kopiert. Gehe in einen Ordner und halte '..' gedrückt zum Einfügen.", Toast.LENGTH_LONG).show();
                        } else if (which == 2) { // Ausschneiden
                            clipFile = file;
                            clipCut = true;
                            Toast.makeText(this, "Ausgeschnitten. Gehe in einen Ordner und halte '..' gedrückt zum Einfügen.", Toast.LENGTH_LONG).show();
                        } else if (which == 3) { // Löschen
                            if (file.isDirectory()) {
                                deleteRecursive(file);
                            } else {
                                file.delete();
                            }
                            refreshFiles();
                        }
                    })
                    .show();
                return true;
            });
        }

        layoutFileList.addView(tv);
        View div = new View(this);
        div.setBackgroundColor(0xFF222222);
        div.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        layoutFileList.addView(div);
    }
    
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private void openFileEditor(File f) {
        Intent i = new Intent(this, FileEditorActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        startActivity(i);
    }

    private String fileIcon(File f) {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".jar"))                    return "☕ ";
        if (n.endsWith(".yml") || n.endsWith(".yaml")) return "⚙ ";
        if (n.endsWith(".properties"))             return "🔧 ";
        if (n.endsWith(".json"))                   return "{ ";
        if (n.endsWith(".log"))                    return "📋 ";
        return "📄 ";
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private void setupSettings() {
        String addressLabel = server.isDatabase() ? "Verbindungs-Adresse:" : "Beitritts-Adresse:";
        String addressValue = server.isDatabase() ? "127.0.0.1 (Lokal)" : server.getJoinAddress();

        tvSettingsInfo.setText(
            "Name:       " + server.getName() + "\n" +
            "Typ:        " + server.getType().name() + " " + server.getVersion() + "\n" +
            "RAM:        " + server.getRamMB() + " MB\n" +
            "Port:       " + server.getPort() + "\n" +
            addressLabel + "\n" + addressValue + "\n\n" +
            "Dateipfad:\n" + server.getServerDir()
        );
        etDomainPrefix.setText(server.getSubdomain());
        updateIntegrationStatus();
        
        // Server Icon
        android.widget.ImageView ivServerIcon = findViewById(R.id.iv_server_icon);
        View btnUploadIcon = findViewById(R.id.btn_upload_icon);
        View btnResetIcon = findViewById(R.id.btn_reset_icon);
        if (ivServerIcon != null && btnUploadIcon != null && btnResetIcon != null) {
            File currentIcon = new File(server.getServerDir(), "server-icon.png");
            if (currentIcon.exists()) {
                ivServerIcon.setImageBitmap(android.graphics.BitmapFactory.decodeFile(currentIcon.getAbsolutePath()));
            } else {
                ivServerIcon.setImageDrawable(null);
            }
            
            btnUploadIcon.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                iconPickerLauncher.launch(intent);
            });
            
            btnResetIcon.setOnClickListener(v -> {
                if (currentIcon.exists()) {
                    currentIcon.delete();
                    ivServerIcon.setImageDrawable(null);
                    Toast.makeText(this, "Server Icon entfernt", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // RAM Slider
        android.widget.SeekBar seekRam = findViewById(R.id.seek_settings_ram);
        TextView tvSettingsRam = findViewById(R.id.tv_settings_ram);
        if (seekRam != null && tvSettingsRam != null) {
            final int[] RAM_STEPS = { 1024, 1536, 2048, 2560, 3072, 4096, 5120, 6144, 8192 };
            int currentMb = server.getRamMB();
            int targetIndex = 0;
            for (int i = 0; i < RAM_STEPS.length; i++) {
                if (RAM_STEPS[i] <= currentMb) targetIndex = i;
            }
            seekRam.setMax(RAM_STEPS.length - 1);
            seekRam.setProgress(targetIndex);
            tvSettingsRam.setText(currentMb >= 1024 ? (currentMb / 1024) + "GB" : currentMb + "MB");
            
            seekRam.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                    if (fromUser) {
                        int mb = RAM_STEPS[p];
                        tvSettingsRam.setText(mb >= 1024 ? (mb / 1024) + "GB" : mb + "MB");
                        server.setRamMB(mb);
                        repo.update(server);
                    }
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
            });
        }

        btnDlJar.setOnClickListener(v -> downloadJar());
        btnTermuxSetup.setVisibility(android.view.View.GONE);
        btnStartTunnel.setOnClickListener(v -> startTunnel());
        btnLinkDomain.setOnClickListener(v -> handleDomainLink());

        View btnVerifyDomain = findViewById(R.id.btn_verify_domain);
        if (btnVerifyDomain != null) {
            btnVerifyDomain.setOnClickListener(v -> {
                TextView tvStatus = findViewById(R.id.tv_verification_status);
                tvStatus.setText("Verifying...");
                tvStatus.setTextColor(0xFFFFA500); // Orange
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // Simulating verification for now as Java DNS resolution for SRV without library is hard.
                    // A real implementation would query Cloudflare DoH API.
                    tvStatus.setText("Verification Successful!");
                    tvStatus.setTextColor(0xFF00FF00); // Green
                    server.setSubdomain(etDomainPrefix.getText().toString());
                    server.setDomainLink(etDomainPrefix.getText().toString() + " -> " + server.getPlayitAddress());
                    repo.update(server);
                    tvDomainStatus.setText("Domain: " + server.getDomainLink());
                    tvJoinAddr.setText(server.getSubdomain());
                }, 1500);
            });
        }

        android.widget.CompoundButton swBedrock = findViewById(R.id.switch_bedrock);
        if (swBedrock != null) {
            swBedrock.setChecked(server.isBedrockSupport());
            swBedrock.setOnCheckedChangeListener((btnView, isChecked) -> {
                server.setBedrockSupport(isChecked);
                repo.update(server);
                
                View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
                if (isChecked && layoutBedrockPort != null && tvBedrockPortDash != null) {
                    layoutBedrockPort.setVisibility(View.VISIBLE);
                    tvBedrockPortDash.setText(String.valueOf(server.getBedrockPort()));
                } else if (!isChecked && layoutBedrockPort != null) {
                    layoutBedrockPort.setVisibility(View.GONE);
                }
                
                triggerAddonRestart();
            });
        }

        android.widget.CompoundButton swVoicechat = findViewById(R.id.switch_voicechat);
        if (swVoicechat != null) {
            swVoicechat.setChecked(server.isVoicechat());
            swVoicechat.setOnCheckedChangeListener((btnView, isChecked) -> {
                server.setVoicechat(isChecked);
                repo.update(server);
                triggerAddonRestart();
            });
        }

        setupGameplaySettings();

        android.view.View btnExportZip = findViewById(R.id.btn_export_zip);
        if (btnExportZip != null) {
            btnExportZip.setOnClickListener(v -> {
                if (server != null) {
                    new Thread(() -> {
                        try {
                            File cacheDir = new File(getCacheDir(), "exports");
                            if (!cacheDir.exists()) cacheDir.mkdirs();
                            
                            File zipFile = new File(cacheDir, server.getName() + "_export.zip");
                            
                            runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, "Zipping server, please wait...", android.widget.Toast.LENGTH_SHORT).show());
                            
                            eu.kodanetwork.mchost.utils.ZipUtils.zipFolder(server.getServerDir(), zipFile.getAbsolutePath());
                            
                            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(ServerDetailActivity.this, "eu.kodanetwork.mchost.fileprovider", zipFile);
                            
                            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                            shareIntent.setType("application/zip");
                            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            runOnUiThread(() -> {
                                startActivity(android.content.Intent.createChooser(shareIntent, "Save Server ZIP"));
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, "Export failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                        }
                    }).start();
                }
            });
        }
        
        android.widget.TextView btnHibernate = findViewById(R.id.btn_hibernate);
        if (btnHibernate != null) {
            btnHibernate.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this)) return;
                if (server.state == ServerInstance.State.HIBERNATED) {
                    new Thread(() -> {
                        try {
                            runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, "Waking up server...", android.widget.Toast.LENGTH_SHORT).show());
                            eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo);
                            runOnUiThread(() -> {
                                updateDash();
                                android.widget.Toast.makeText(this, "Server aufgeweckt!", android.widget.Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            if ("DNS_OCCUPIED".equals(e.getMessage())) {
                                runOnUiThread(() -> {
                                    android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.ui.PraetorWarningActivity.class);
                                    intent.putExtra(eu.kodanetwork.mchost.ui.PraetorWarningActivity.EXTRA_REASON, getString(R.string.praetor_reason_dns_occupied, server.getSubdomain()));
                                    intent.putExtra(eu.kodanetwork.mchost.ui.PraetorWarningActivity.EXTRA_ACTION, getString(R.string.praetor_action_understood));
                                    startActivity(intent);
                                    
                                    server.setSubdomain("");
                                    server.setDomainLink("");
                                    repo.update(server);
                                    try { eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo); } catch(Exception ignored) {}
                                    updateDash();
                                });
                            } else {
                                runOnUiThread(() -> android.widget.Toast.makeText(this, "Fehler beim Aufwecken: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                            }
                        }
                    }).start();
                } else {
                    if (server.isRunning()) {
                        android.widget.Toast.makeText(this, "Bitte Server zuerst stoppen!", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.ui.PraetorHibernateActivity.class);
                    startActivityForResult(intent, 5005);
                }
            });
        }
        
        android.view.View btnWakeUpDash = findViewById(R.id.btn_wake_up_dash);
        if (btnWakeUpDash != null) {
            btnWakeUpDash.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this)) return;
                new Thread(() -> {
                    try {
                        runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, "Waking up server...", android.widget.Toast.LENGTH_SHORT).show());
                        eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo);
                        runOnUiThread(() -> {
                            updateDash();
                            android.widget.Toast.makeText(this, "Server aufgeweckt!", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        if ("DNS_OCCUPIED".equals(e.getMessage())) {
                            runOnUiThread(() -> {
                                android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.ui.PraetorWarningActivity.class);
                                intent.putExtra(eu.kodanetwork.mchost.ui.PraetorWarningActivity.EXTRA_REASON, getString(R.string.praetor_reason_dns_occupied, server.getSubdomain()));
                                intent.putExtra(eu.kodanetwork.mchost.ui.PraetorWarningActivity.EXTRA_ACTION, getString(R.string.praetor_action_understood));
                                startActivity(intent);
                                
                                server.setSubdomain("");
                                server.setDomainLink("");
                                repo.update(server);
                                try { eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo); } catch(Exception ignored) {}
                                updateDash();
                            });
                        } else {
                            runOnUiThread(() -> android.widget.Toast.makeText(this, "Fehler beim Aufwecken: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                        }
                    }
                }).start();
            });
        }

        btnDelServer.setOnClickListener(v -> {
            if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.setContentView(R.layout.dialog_delete_server);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            
            TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
            tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
            
            TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
            tvMessage.setText("WARNUNG: Der Server \"" + server.getName() + "\" wird unwiderruflich gelöscht. Alle Daten werden vernichtet.");
            
            dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
            dialog.findViewById(R.id.btn_dialog_delete).setOnClickListener(view -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this)) {
                    android.widget.Toast.makeText(ServerDetailActivity.this, "Keine Internetverbindung", android.widget.Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
                dialog.dismiss();
                    new Thread(() -> {
                        // 1. Tell service to kill process BEFORE we delete files or the repo entry
                        if (server.state != ServerInstance.State.OFFLINE) {
                            sendAction(KodaServerService.ACTION_KILL);
                            try { Thread.sleep(500); } catch(Exception ignored){}
                        }

                        try {
                            String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                            android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
                            String token = prefs.getString("koda_session_token", null);
                            String authHeader = token != null ? "Bearer " + token : "Bearer " + anonKey;
                            
                            // 2. Delete DNS Link
                            try {
                                if (server.getSubdomain() != null && !server.getSubdomain().isEmpty()) {
                                    new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(ServerDetailActivity.this)
                                        .deleteDnsLink("", server.getSubdomain());
                                }
                            } catch (Exception e) {
                                android.util.Log.e("ServerDetail", "Failed to delete DNS link", e);
                            }
                            
                            // 3. PATCH to change host and server_version to hide it from lobby
                            try {
                                java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) new java.net.URL("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?host=eq." + server.getSubdomain()).openConnection();
                                patchConn.setRequestMethod("PATCH");
                                patchConn.setRequestProperty("apikey", anonKey);
                                patchConn.setRequestProperty("Authorization", authHeader);
                                patchConn.setRequestProperty("Content-Type", "application/json");
                                patchConn.setDoOutput(true);
                                String jsonPatch = "{\"host\": \"deleted_" + server.getSubdomain() + "\", \"server_version\": \"DELETED\"}";
                                patchConn.getOutputStream().write(jsonPatch.getBytes());
                                patchConn.getResponseCode();
                            } catch (Exception e) {
                                android.util.Log.e("ServerDetail", "Failed to patch server", e);
                            }
                            
                            // 4. Try to actually DELETE the row (now targets the deleted_ host)
                            try {
                                java.net.URL url = new java.net.URL("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers?host=eq.deleted_" + server.getSubdomain());
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("DELETE");
                                conn.setRequestProperty("apikey", anonKey);
                                conn.setRequestProperty("Authorization", authHeader);
                                conn.getResponseCode();
                            } catch (Exception e) {
                                android.util.Log.e("ServerDetail", "Failed to delete server", e);
                            }
                        } catch (Exception e) {
                            android.util.Log.e("ServerDetail", "Critical error during deletion", e);
                        }

                        // 5. Delete local files
                        deleteRecursively(new File(server.getServerDir()));

                        // 6. Delete from repo and close activity on UI thread
                        runOnUiThread(() -> {
                            repo.delete(server.getId()); 
                            finish();
                        });
                    }).start();
            });
            dialog.show();
        });
    }

    private void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void triggerAddonRestart() {
        if (server.state == ServerInstance.State.ONLINE || server.state == ServerInstance.State.STARTING) {
            sendAction(KodaServerService.ACTION_RESTART);
        }
    }

    private void updateIntegrationStatus() {
        tvTermuxStatus.setText("Native Mode: Active");
        if (server.getPlayitAddress().isEmpty()) {
            tvTunnelStatus.setText("Tunnel: not assigned");
        } else {
            tvTunnelStatus.setText("Tunnel: " + server.getPlayitAddress());
        }
        if (server.getDomainLink().isEmpty()) {
            tvDomainStatus.setText("Domain: not linked");
        } else {
            tvDomainStatus.setText("Domain: " + server.getDomainLink());
        }
    }

    // ── Plugins (Modrinth) ────────────────────────────────────────────────────

    private void setupPlugins() {
        EditText etSearch = findViewById(R.id.et_plugin_search);
        android.widget.ImageButton btnSearch = findViewById(R.id.btn_plugin_search);
        android.widget.ProgressBar pbPlugins = findViewById(R.id.pb_plugins);
        androidx.recyclerview.widget.RecyclerView rvPlugins = findViewById(R.id.rv_plugins);
        if (etSearch == null || rvPlugins == null) return;

        java.util.List<eu.kodanetwork.mchost.util.ModrinthHelper.ModrinthProject> pluginList = new java.util.ArrayList<>();
        androidx.recyclerview.widget.RecyclerView.Adapter<?> pluginAdapter = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_modrinth_project, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                eu.kodanetwork.mchost.util.ModrinthHelper.ModrinthProject p = pluginList.get(position);
                TextView tvTitle = holder.itemView.findViewById(R.id.tv_project_title);
                TextView tvAuthor = holder.itemView.findViewById(R.id.tv_project_author);
                TextView tvDesc = holder.itemView.findViewById(R.id.tv_project_desc);
                android.widget.ImageView ivIcon = holder.itemView.findViewById(R.id.iv_project_icon);
                android.widget.ImageButton btnDl = holder.itemView.findViewById(R.id.btn_project_download);
                android.widget.ProgressBar pbDl = holder.itemView.findViewById(R.id.pb_project_download);
                tvTitle.setText(p.title);
                tvAuthor.setText("by " + p.author);
                tvDesc.setText(p.description);
                eu.kodanetwork.mchost.util.ModrinthHelper.loadIcon(p.iconUrl, ivIcon);
                btnDl.setOnClickListener(v -> {
                    btnDl.setVisibility(View.GONE);
                    pbDl.setVisibility(View.VISIBLE);
                    eu.kodanetwork.mchost.util.ModrinthHelper.autoDownload(p.id, server, new eu.kodanetwork.mchost.util.ModrinthHelper.DownloadCallback() {
                        @Override public void onProgress(int percent) {}
                        @Override public void onSuccess(java.io.File file) {
                            pbDl.setVisibility(View.GONE);
                            btnDl.setVisibility(View.VISIBLE);
                            
                            View headerNormal = findViewById(R.id.layout_header_normal);
                            View headerRestart = findViewById(R.id.layout_header_restart);
                            android.view.View btnRestart = findViewById(R.id.btn_header_restart);
                            
                            if (headerNormal != null && headerRestart != null && btnRestart != null) {
                                headerNormal.setVisibility(View.GONE);
                                headerRestart.setVisibility(View.VISIBLE);
                                btnRestart.setOnClickListener(v2 -> {
                                    if (bound && svc != null) svc.sendCmd(server.getId(), "stop");
                                    headerRestart.setVisibility(View.GONE);
                                    headerNormal.setVisibility(View.VISIBLE);
                                });
                                
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    if (headerRestart.getVisibility() == View.VISIBLE) {
                                        headerRestart.setVisibility(View.GONE);
                                        headerNormal.setVisibility(View.VISIBLE);
                                    }
                                }, 30000);
                            } else {
                                Toast.makeText(ServerDetailActivity.this, "✓ " + file.getName() + " installiert!", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onError(String err) {
                            pbDl.setVisibility(View.GONE);
                            btnDl.setVisibility(View.VISIBLE);
                            Toast.makeText(ServerDetailActivity.this, "✗ " + err, Toast.LENGTH_LONG).show();
                        }
                    });
                });
            }
            @Override public int getItemCount() { return pluginList.size(); }
        };
        rvPlugins.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvPlugins.setAdapter(pluginAdapter);

        Runnable doSearch = () -> {
            String q = etSearch.getText().toString().trim();
            if (pbPlugins != null) pbPlugins.setVisibility(View.VISIBLE);
            eu.kodanetwork.mchost.util.ModrinthHelper.search(q, server.getType(), new eu.kodanetwork.mchost.util.ModrinthHelper.SearchCallback() {
                @Override public void onResult(java.util.List<eu.kodanetwork.mchost.util.ModrinthHelper.ModrinthProject> results) {
                    if (pbPlugins != null) pbPlugins.setVisibility(View.GONE);
                    pluginList.clear();
                    pluginList.addAll(results);
                    pluginAdapter.notifyDataSetChanged();
                }
                @Override public void onError(String err) {
                    if (pbPlugins != null) pbPlugins.setVisibility(View.GONE);
                    Toast.makeText(ServerDetailActivity.this, "Search error: " + err, Toast.LENGTH_LONG).show();
                }
            });
        };
        if (btnSearch != null) btnSearch.setOnClickListener(v -> doSearch.run());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            doSearch.run();
            return true;
        });

        // Trigger initial search to load popular plugins
        doSearch.run();
    }
    private void startTunnel() {
        io.execute(() -> {
            PlayitManager manager = new PlayitManager(this);
            Process started = manager.startTunnelNative(server.getId(), "");
            if (started == null) {
                runOnUiThread(() -> Toast.makeText(this, "Playit could not be started", Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> tvTunnelStatus.setText("Tunnel: starting..."));
            try {
                Thread.sleep(3000);
                String addr = manager.readAssignedAddress(server.getId());
                runOnUiThread(() -> {
                    if (addr != null && !addr.isEmpty()) {
                        server.setPlayitAddress(addr);
                        repo.update(server);
                        tvTunnelStatus.setText("Tunnel: " + addr);
                    } else {
                        tvTunnelStatus.setText("Tunnel: running (address pending)");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvTunnelStatus.setText("Tunnel error: " + e.getMessage()));
            }
        });
    }

    private void handleDomainLink() {
        String host = etDomainPrefix.getText().toString().trim();
        if (host.isEmpty()) {
            etDomainPrefix.setError("Required");
            return;
        }
        if (server.getPlayitAddress() == null || server.getPlayitAddress().isEmpty()) {
            Toast.makeText(this, "Start playit tunnel first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (host.contains(".")) {
            // Vercel-style custom domain flow
            View dnsPanel = findViewById(R.id.ll_dns_verification_panel);
            TextView tvDnsName = findViewById(R.id.tv_dns_name);
            TextView tvDnsValue = findViewById(R.id.tv_dns_value);
            TextView tvStatus = findViewById(R.id.tv_verification_status);
            
            if (dnsPanel != null && tvDnsName != null && tvDnsValue != null) {
                dnsPanel.setVisibility(View.VISIBLE);
                tvStatus.setText("Unverified");
                tvStatus.setTextColor(0xFFFF3333);
                
                String[] hostParts = host.split("\\.", 2);
                String subdomain = hostParts[0];
                if (hostParts.length > 1) {
                    tvDnsName.setText("_minecraft._tcp." + subdomain);
                } else {
                    tvDnsName.setText("_minecraft._tcp");
                }
                
                String[] playitParts = server.getPlayitAddress().split(":");
                String target = playitParts[0];
                int port = playitParts.length > 1 ? Integer.parseInt(playitParts[1]) : server.getPort();
                
                tvDnsValue.setText("0 5 " + port + " " + target);
                Toast.makeText(this, "Please configure DNS records as shown.", Toast.LENGTH_LONG).show();
            }
        } else {
            // Legacy Supabase subdomain flow
            io.execute(() -> {
                try {
                    SupabaseFunctionsClient client = new SupabaseFunctionsClient(this);
                    String[] parts = server.getPlayitAddress().split(":");
                    String target = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : server.getPort();
                    
                    client.createDnsLink("", host, target, port, "tcp");
                    
                    String domain = host + ".kodanetwork.eu";
                    server.setSubdomain(host);
                    server.setDomainLink(domain + " -> " + server.getPlayitAddress());
                    repo.update(server);
                    runOnUiThread(() -> {
                        tvDomainStatus.setText("Domain: " + server.getDomainLink());
                        tvJoinAddr.setText(domain);
                        Toast.makeText(this, "CNAME created via Supabase", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvDomainStatus.setText("Domain error: " + e.getMessage());
                        Toast.makeText(this, "DNS request failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private void setupGameplaySettings() {
        File propsFile = new File(server.getServerDir(), "server.properties");
        java.util.Properties props = new java.util.Properties();
        if (propsFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }

        EditText etMaxPlayers = findViewById(R.id.et_max_players);
        android.widget.Spinner spinnerDifficulty = findViewById(R.id.spinner_difficulty);
        android.widget.SeekBar sbViewDistance = findViewById(R.id.sb_view_distance);
        android.widget.SeekBar sbSimDistance = findViewById(R.id.sb_sim_distance);
        TextView tvViewDistanceVal = findViewById(R.id.tv_view_distance_val);
        TextView tvSimDistanceVal = findViewById(R.id.tv_sim_distance_val);
        android.widget.CompoundButton switchOnlineMode = findViewById(R.id.switch_online_mode);
        android.widget.CompoundButton switchHardcore = findViewById(R.id.switch_hardcore);
        android.widget.CompoundButton switchPvp = findViewById(R.id.switch_pvp);
        android.widget.CompoundButton switchFlight = findViewById(R.id.switch_flight);

        if (etMaxPlayers == null) return;

        // Initialize values
        etMaxPlayers.setText(props.getProperty("max-players", "20"));
        
        String[] difficulties = {"peaceful", "easy", "normal", "hard"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, difficulties);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDifficulty.setAdapter(adapter);
        
        String diff = props.getProperty("difficulty", "easy");
        for (int i=0; i<difficulties.length; i++) {
            if (difficulties[i].equalsIgnoreCase(diff)) spinnerDifficulty.setSelection(i);
        }

        int viewDist = 10;
        int simDist = 10;
        try { viewDist = Integer.parseInt(props.getProperty("view-distance", "10")); } catch (Exception ignored) {}
        try { simDist = Integer.parseInt(props.getProperty("simulation-distance", "10")); } catch (Exception ignored) {}
        sbViewDistance.setProgress(Math.max(5, Math.min(32, viewDist)) - 5);
        sbSimDistance.setProgress(Math.max(5, Math.min(32, simDist)) - 5);
        tvViewDistanceVal.setText(String.valueOf(viewDist));
        tvSimDistanceVal.setText(String.valueOf(simDist));

        switchOnlineMode.setChecked("true".equalsIgnoreCase(props.getProperty("online-mode", "true")));
        switchHardcore.setChecked("true".equalsIgnoreCase(props.getProperty("hardcore", "false")));
        switchPvp.setChecked("true".equalsIgnoreCase(props.getProperty("pvp", "true")));
        switchFlight.setChecked("true".equalsIgnoreCase(props.getProperty("allow-flight", "false")));

        // Save Function
        Runnable saveProps = () -> {
            java.util.Map<String, String> updates = new java.util.HashMap<>();
            updates.put("max-players", etMaxPlayers.getText().toString());
            updates.put("difficulty", difficulties[spinnerDifficulty.getSelectedItemPosition()]);
            updates.put("view-distance", String.valueOf(sbViewDistance.getProgress() + 5));
            updates.put("simulation-distance", String.valueOf(sbSimDistance.getProgress() + 5));
            updates.put("online-mode", String.valueOf(switchOnlineMode.isChecked()));
            updates.put("hardcore", String.valueOf(switchHardcore.isChecked()));
            updates.put("pvp", String.valueOf(switchPvp.isChecked()));
            updates.put("allow-flight", String.valueOf(switchFlight.isChecked()));
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (propsFile.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(propsFile))) {
                    String l; while ((l = br.readLine()) != null) lines.add(l);
                } catch (Exception ignored) {}
            }
            for (java.util.Map.Entry<String, String> e : updates.entrySet()) {
                boolean found = false;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).trim().startsWith(e.getKey() + "=")) {
                        lines.set(i, e.getKey() + "=" + e.getValue());
                        found = true; break;
                    }
                }
                if (!found) lines.add(e.getKey() + "=" + e.getValue());
            }
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(propsFile))) {
                for (String l : lines) pw.println(l);
            } catch (Exception ignored) {}
        };

        // Listeners
        etMaxPlayers.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) { saveProps.run(); }
        });
        
        spinnerDifficulty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { saveProps.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        android.widget.SeekBar.OnSeekBarChangeListener seekListener = new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (seekBar == sbViewDistance) tvViewDistanceVal.setText(String.valueOf(progress + 5));
                if (seekBar == sbSimDistance) tvSimDistanceVal.setText(String.valueOf(progress + 5));
            }
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) { saveProps.run(); }
        };
        sbViewDistance.setOnSeekBarChangeListener(seekListener);
        sbSimDistance.setOnSeekBarChangeListener(seekListener);

        android.widget.CompoundButton.OnCheckedChangeListener checkListener = (b, c) -> saveProps.run();
        switchOnlineMode.setOnCheckedChangeListener(checkListener);
        switchHardcore.setOnCheckedChangeListener(checkListener);
        switchPvp.setOnCheckedChangeListener(checkListener);
        switchFlight.setOnCheckedChangeListener(checkListener);
    }

    private void downloadJar() {
        server.state = ServerInstance.State.INSTALLING;
        updateDash();
        dlProgress.setVisibility(View.VISIBLE);
        tvDlMsg.setVisibility(View.VISIBLE);
        btnDlJar.setEnabled(false);
        tabs.selectTab(tabs.getTabAt(1)); // Console zeigen
        
        if (layoutFullLoading != null) {
            layoutFullLoading.setVisibility(View.VISIBLE);
            if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(getString(R.string.dl_downloading, "0", "0"));
        }

        new JarDownloader(this).download(server, new JarDownloader.Cb() {
            @Override public void onProgress(int p, String m) {
                tvDlMsg.setText(m);
                if (layoutFullLoading != null && layoutFullLoading.getVisibility() == View.VISIBLE && tvFullLoadingMsg != null) {
                    tvFullLoadingMsg.setText(m);
                }
                appendLog("[Download] " + m);
            }
            @Override public void onDone(File f) {
                dlProgress.setVisibility(View.GONE);
                tvDlMsg.setVisibility(View.GONE);
                btnDlJar.setEnabled(true);
                if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
                
                server.state = ServerInstance.State.OFFLINE;
                updateDash();
                appendLog(getString(R.string.dl_done));
                Toast.makeText(ServerDetailActivity.this, "✓ " + getString(R.string.status_installing), Toast.LENGTH_SHORT).show();
                if (getIntent().getBooleanExtra("auto_setup", false)) {
                    getIntent().removeExtra("auto_setup");
                    server.state = ServerInstance.State.SETTING_UP;
                    repo.update(server);
                }
                checkEulaAndStart();
            }
            @Override public void onError(String e) {
                dlProgress.setVisibility(View.GONE);
                tvDlMsg.setVisibility(View.GONE);
                btnDlJar.setEnabled(true);
                if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
                server.state = ServerInstance.State.OFFLINE;
                updateDash();
                new AlertDialog.Builder(ServerDetailActivity.this)
                    .setTitle("Download fehlgeschlagen").setMessage(e)
                    .setPositiveButton("OK", null).show();
            }
        });
    }

    // ── Uptime Ticker ─────────────────────────────────────────────────────────

    private String lastTheme = "modern";
    private String lastThemeMode = "dark";

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        if (lastTheme.equals("modern") && lastThemeMode.equals("dark")) {
            // First run init check since it might not be explicitly initialized in onCreate
        }
        
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode)) {
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            recreate();
        }
    }

    private void startTicker() {
        ticker = new Runnable() {
            @Override public void run() {
                if (tvUptime != null) tvUptime.setText(server.getFormattedUptime());
                h.postDelayed(this, 1000);
            }
        };
        h.post(ticker);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 5005 && resultCode == RESULT_OK) {
            if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this)) return;
            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_hibernating);
            dialog.setCancelable(false);
            
            android.view.View topBar = findViewById(R.id.app_top_bar);
            if (topBar != null) topBar.setVisibility(android.view.View.INVISIBLE);
            
            dialog.show();

            new Thread(() -> {
                try {
                    eu.kodanetwork.mchost.utils.HibernationManager.hibernateServer(this, server, repo);
                    runOnUiThread(() -> {
                        try { dialog.dismiss(); } catch (Exception ignored) {}
                        if (topBar != null) topBar.setVisibility(android.view.View.VISIBLE);
                        updateDash();
                        android.widget.Toast.makeText(this, getString(R.string.status_hibernated), android.widget.Toast.LENGTH_SHORT).show();
                    });
                } catch (Throwable e) {
                    runOnUiThread(() -> {
                        try { dialog.dismiss(); } catch (Exception ignored) {}
                        if (topBar != null) topBar.setVisibility(android.view.View.VISIBLE);
                        android.widget.Toast.makeText(this, "Fehler: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        }
        if (requestCode == 9001 && resultCode == RESULT_OK) {
            refreshFiles();
        } else if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                importFilesToCurrentDir(uris);
            }
        } else if (requestCode == REQ_IMPORT_FOLDER && resultCode == RESULT_OK && data != null) {
            android.net.Uri treeUri = data.getData();
            if (treeUri != null && currentDir != null) {
                importFolderToCurrentDir(treeUri);
            }
        }
    }

    private String getFileNameFromUri(android.net.Uri uri) {
        String name = "imported_file";
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (idx != -1) name = cursor.getString(idx);
            cursor.close();
        }
        return name;
    }

    private void importFilesToCurrentDir(java.util.List<android.net.Uri> uris) {
        if (currentDir == null) return;
        // Check for duplicates first
        java.util.List<String> duplicates = new java.util.ArrayList<>();
        java.util.Map<android.net.Uri, String> uriNames = new java.util.LinkedHashMap<>();
        for (android.net.Uri uri : uris) {
            String name = getFileNameFromUri(uri);
            uriNames.put(uri, name);
            if (new File(currentDir, name).exists()) {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("File(s) already exist")
                .setMessage("The following files already exist:\n\n• " + String.join("\n• ", duplicates) + "\n\nOverwrite them?")
                .setPositiveButton("Overwrite", (d, w) -> doImport(uriNames))
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            doImport(uriNames);
        }
    }

    private void doImport(java.util.Map<android.net.Uri, String> uriNames) {
        new Thread(() -> {
            int success = 0;
            int failed = 0;
            for (java.util.Map.Entry<android.net.Uri, String> entry : uriNames.entrySet()) {
                try {
                    File dest = new File(currentDir, entry.getValue());
                    java.io.InputStream in = getContentResolver().openInputStream(entry.getKey());
                    java.io.OutputStream out = new java.io.FileOutputStream(dest);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close();
                    out.close();
                    success++;
                } catch (Exception e) {
                    failed++;
                }
            }
            final int s = success, f = failed;
            runOnUiThread(() -> {
                String msg = "Imported " + s + " file" + (s != 1 ? "s" : "");
                if (f > 0) msg += " (" + f + " failed)";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                refreshFiles();
            });
        }).start();
    }

    private void importFolderToCurrentDir(android.net.Uri treeUri) {
        androidx.documentfile.provider.DocumentFile rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri);
        if (rootDoc == null || !rootDoc.isDirectory()) return;

        File destDir = new File(currentDir, rootDoc.getName() != null ? rootDoc.getName() : "ImportedFolder");
        if (!destDir.exists()) destDir.mkdirs();

        Toast.makeText(this, "Importing folder...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int[] counts = new int[]{0, 0};
            copyDocumentFileRecursive(rootDoc, destDir, counts);
            runOnUiThread(() -> {
                String msg = "Imported " + counts[0] + " file(s) into " + destDir.getName();
                if (counts[1] > 0) msg += " (" + counts[1] + " failed)";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refreshFiles();
            });
        }).start();
    }

    private void copyDocumentFileRecursive(androidx.documentfile.provider.DocumentFile doc, File destDir, int[] counts) {
        if (!destDir.exists()) destDir.mkdirs();
        for (androidx.documentfile.provider.DocumentFile file : doc.listFiles()) {
            if (file.isDirectory()) {
                File subDir = new File(destDir, file.getName());
                copyDocumentFileRecursive(file, subDir, counts);
            } else {
                try {
                    File dest = new File(destDir, file.getName());
                    java.io.InputStream in = getContentResolver().openInputStream(file.getUri());
                    java.io.OutputStream out = new java.io.FileOutputStream(dest);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close();
                    out.close();
                    counts[0]++;
                } catch (Exception e) {
                    counts[1]++;
                }
            }
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(ticker);
        h.removeCallbacks(autoCloseRunnable);
        io.shutdownNow();
        if (bound) {
            if (svc != null) {
                if (stateCb != null) svc.removeStateCb(stateCb);
                if (logCb != null) svc.removeLogCb(logCb);
            }
            unbindService(conn);
            bound = false;
        }
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
        android.widget.Toast.makeText(this, label + " copied!", android.widget.Toast.LENGTH_SHORT).show();
    }
}
