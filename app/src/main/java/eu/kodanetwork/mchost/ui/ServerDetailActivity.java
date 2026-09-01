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
    private static final int REQ_EXPORT_FILE = 9914;

    // Tabs
    private TabLayout tabs;
    private View pDash, pConsole, pFiles, pSettings, pPlugins;
    private android.app.Dialog propsDriftDialog;

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
    private MaterialButton btnDlJar, btnDelServer;
    private MaterialButton btnTermuxSetup, btnStartTunnel, btnLinkDomain;
    private java.util.List<TiltEffectHelper> tiltHelpers = new java.util.ArrayList<>();
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
                        Toast.makeText(this, getString(R.string.sd_toast_icon_updated), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.sd_toast_error_loading_image) + e.getMessage(), Toast.LENGTH_LONG).show();
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
                        if (s == ServerInstance.State.ONLINE && layoutFullLoading.getVisibility() == View.VISIBLE) {
                            // Server is up: blocks fly into the server name while the overlay fades
                            playSuccessFlyAndDismiss();
                        } else if (s == ServerInstance.State.CRASHED || s == ServerInstance.State.OFFLINE) {
                            layoutFullLoading.setVisibility(View.GONE);
                        } else if (s == ServerInstance.State.STARTING && layoutFullLoading.getVisibility() == View.VISIBLE) {
                            if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(getString(R.string.sd_starting_server));
                        }
                    }
                    updateDash();
                });
            };
            logCb = (id, line) -> {
                if (id.equals(server.getId())) runOnUiThread(() -> {
                    appendLog(line);
                    if (line.contains("SETUP_COMPLETE_SUCCESS") || line.contains("DESIGN_APPLIED")) {
                        playSuccessFlyAndDismiss();
                        if (server.state == ServerInstance.State.SETTING_UP) {
                            server.state = ServerInstance.State.ONLINE;
                            updateDash();
                        }
                    } else if (line.contains("KodaNetwork Error")) {
                        if (layoutFullLoading != null) layoutFullLoading.setVisibility(View.GONE);
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
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.SleekThemeHelper.applyTheme(this);
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        
        // Prevent screenshots & screen recording
        // Screen protection removed

        if (eu.kodanetwork.mchost.App.getPrefs(this).getBoolean("dev_terminal_enabled", false)) {
            setContentView(eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)
                    ? R.layout.activity_server_detail_sleek : R.layout.activity_server_detail_terminal);
        setupSleekDetail();
        } else if (eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)) {
            setContentView(R.layout.activity_server_detail_m3);
        } else {
            setContentView(R.layout.activity_server_detail);
        }
        
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);

        // Apply light mode background early
        if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
            findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
        }
        
        setupWindowDecor(getWindow());
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                
                android.view.View topBar = findViewById(R.id.app_top_bar);
                if (topBar != null) {
                    topBar.setPadding(topBar.getPaddingLeft(), top, topBar.getPaddingRight(), topBar.getPaddingBottom());
                    topBar.getLayoutParams().height = (int)(56 * getResources().getDisplayMetrics().density) + top;
                    topBar.requestLayout();
                }

                int[] panels = {R.id.panel_dash, R.id.panel_console, R.id.panel_plugins, R.id.panel_settings, R.id.panel_files};
                for (int id : panels) {
                    android.view.View panel = findViewById(id);
                    if (panel != null) {
                        panel.setPadding(panel.getPaddingLeft(), panel.getPaddingTop(), panel.getPaddingRight(), bottom);
                    }
                }
                
                return insets.replaceSystemWindowInsets(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), 0);
            });
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
        if (server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.PUMPKIN) {
            setupPumpkinOverrides();
        }

        // Network budget rules: entry button in the settings panel (programmatic)
        // own element directly BELOW the CHANGE SERVER NAME card
        View cardNet = findViewById(R.id.btn_change_name) != null
                ? (View) findViewById(R.id.btn_change_name).getParent() : findViewById(R.id.card_settings_network);
        if (cardNet != null && cardNet.getParent() instanceof android.view.ViewGroup && server != null) {
            com.google.android.material.button.MaterialButton btnNet = eu.kodanetwork.mchost.util.KodaButtons.primary(this,
                    getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de") ? "Netzwerk-Regeln" : "Network rules");
            btnNet.setOnClickListener(v -> showNetworkRulesSheet());
            android.view.ViewGroup parent = (android.view.ViewGroup) cardNet.getParent();
            int idx = parent.indexOfChild(cardNet) + 1;
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            float d = getResources().getDisplayMetrics().density;
            lp.topMargin = (int)(12 * d);
            parent.addView(btnNet, idx, lp);
        }

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(server.getName());
        tvTitle.setClickable(true);
        tvTitle.setFocusable(true);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        tvTitle.setBackgroundResource(outValue.resourceId);
        tvTitle.setOnClickListener(v -> showNameEditDialog());
        
        android.widget.ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
        
        android.view.View btnChangeName = findViewById(R.id.btn_change_name);
        if (btnChangeName != null) {
            btnChangeName.setOnClickListener(v -> showNameEditDialog());
        }

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
        
        checkServerBanStatus();

        if (getIntent().getBooleanExtra("auto_setup", false)) {
            getIntent().removeExtra("auto_setup");
            if (layoutFullLoading != null) {
                layoutFullLoading.setVisibility(View.VISIBLE);
                layoutFullLoading.setAlpha(1f);
                com.airbnb.lottie.LottieAnimationView boot = findViewById(R.id.lottie_full_loading);
                if (boot != null) {
                    boot.setVisibility(View.VISIBLE);
                    boot.setProgress(0f);
                    boot.playAnimation();
                }
                if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(getString(R.string.sd_auto_setup_running));
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

        // Sleek layout uses different IDs — alias them onto the same fields so all
        // existing dashboard/console logic (updateDash, appendLog, sendCmd…) keeps working
        if (tvJoinAddr == null) tvJoinAddr = findViewById(R.id.tv_join_address);

        if (tvJoinAddr != null) tvJoinAddr.setOnClickListener(v -> copyToClipboard("Join Address", server.getJoinAddress()));
        if (tvBedrockPortDash != null) tvBedrockPortDash.setOnClickListener(v -> {
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
        // Sleek console aliases (types match: TextView / ScrollView / EditText)
        if (tvLog == null) tvLog = findViewById(R.id.tv_console);
        if (scrollLog == null) scrollLog = findViewById(R.id.sv_console);
        if (etCmd == null) etCmd = findViewById(R.id.et_console_input);
        layoutChips = null;

        layoutFileList = findViewById(R.id.layout_files);
        tvFilesRoot    = findViewById(R.id.tv_files_root);
        View btnImportFile = findViewById(R.id.btn_import_file);
        if (btnImportFile != null) {
            btnImportFile.setOnClickListener(v -> showFilesActionDialog());
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
        btnDlJar       = findViewById(R.id.btn_dl_jar);
        btnDelServer   = findViewById(R.id.btn_del_server);
        btnTermuxSetup = findViewById(R.id.btn_termux_setup);
        btnStartTunnel = findViewById(R.id.btn_start_tunnel);
        btnLinkDomain = findViewById(R.id.btn_link_domain);
        
        android.view.View btnCustomDnsWizard = findViewById(R.id.btn_custom_dns_wizard);
        if (btnCustomDnsWizard != null) {
            btnCustomDnsWizard.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, CustomDnsWizardActivity.class);
                intent.putExtra("SERVER_ID", server.getId());
                startActivity(intent);
            });
        }
        
        android.view.View btnSettingsUpdate = findViewById(R.id.btn_settings_update);
        if (btnSettingsUpdate != null) {
            btnSettingsUpdate.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, UpdateServerActivity.class);
                intent.putExtra("SERVER_ID", server.getId());
                startActivity(intent);
            });
        }
        
        View layoutBanned = findViewById(R.id.layout_server_banned);
        TextView tvBannedReason = findViewById(R.id.tv_banned_reason);
        View btnBannedDownload = findViewById(R.id.btn_banned_download);
        if (btnBannedDownload != null) {
            btnBannedDownload.setOnClickListener(v -> exportZip());
        }

        // tv_custom_domain_target and btn_copy_domain no longer exist.

        // Static info
        updateJoinAddressDisplay();
        
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

        // Java check — shows the runtime this server will actually use, runs in background
        updateJavaInfoDash();
    }

    private void updateJavaInfoDash() {
        if (tvJavaInfo == null) return;
        // Pumpkin runs a native Rust binary — no JDK involved, keep the pumpkin label
        if (server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.PUMPKIN) {
            tvJavaInfo.setText("🎃 Rust native — kein Java-Runtime nötig");
            tvJavaInfo.setTextColor(0xFFF0762B);
            return;
        }
        tvJavaInfo.setText("checking java…");
        tvJavaInfo.setTextColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF555566 : android.graphics.Color.parseColor("#888888"));
        new Thread(() -> {
            int jv = server.getJavaRuntime() != 0
                    ? server.getJavaRuntime()
                    : eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(server);
            String text;
            int color;
            if (jv == 25) {
                String javaPath = eu.kodanetwork.mchost.util.JavaFinder.find(this, 25);
                if (javaPath != null) {
                    text = "✓ " + eu.kodanetwork.mchost.util.JavaFinder.version(this);
                    color = android.graphics.Color.parseColor("#00E676");
                } else {
                    text = "◌ Java wird beim Start automatisch installiert (JDK 25)";
                    color = android.graphics.Color.parseColor("#FFCC00");
                }
            } else if (eu.kodanetwork.mchost.util.RuntimeManager.isRuntimeInstalled(this, jv)) {
                text = "✓ OpenJDK " + jv;
                color = android.graphics.Color.parseColor("#00E676");
            } else {
                text = "⬇ OpenJDK " + jv + " wird beim Start geladen";
                color = android.graphics.Color.parseColor("#FFCC00");
            }
            final String fText = text;
            final int fColor = color;
            runOnUiThread(() -> {
                tvJavaInfo.setText(fText);
                tvJavaInfo.setTextColor(fColor);
            });
        }).start();
    }

    /**
     * Pumpkin servers are native Rust binaries — hide everything from the Java world
     * that has no effect for them (jar download, JDK picker, -Xmx RAM, server.properties
     * gameplay card). Configuration lives in pumpkin.toml via the Files tab.
     */
    private void setupPumpkinOverrides() {
        View layoutJavaRuntime = findViewById(R.id.layout_java_runtime);
        if (layoutJavaRuntime != null) layoutJavaRuntime.setVisibility(View.GONE);

        View cardGameplay = findViewById(R.id.card_settings_gameplay);
        if (cardGameplay != null) cardGameplay.setVisibility(View.GONE);

        View cardRam = findViewById(R.id.card_settings_ram);
        if (cardRam != null) cardRam.setVisibility(View.GONE);

        View btnJar = findViewById(R.id.btn_dl_jar);
        if (btnJar != null) btnJar.setVisibility(View.GONE);

        // Bedrock crossplay is built into Pumpkin — the legacy Geyser row is meaningless
        View rowBedrock = findViewById(R.id.row_bedrock);
        if (rowBedrock != null) rowBedrock.setVisibility(View.GONE);

        if (tvJavaInfo != null) {
            tvJavaInfo.setText("🎃 Rust native — kein Java-Runtime nötig");
            tvJavaInfo.setTextColor(0xFFF0762B);
        }
        if (tvVerInfo != null) tvVerInfo.setText("Pumpkin 0.1.0 (MC 1.26.40 + Bedrock)");
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
        // In-app coach phase 2: walk through the tabs once the freshly created
        // server finished its setup (waits through INSTALLING/SETTING_UP)
        eu.kodanetwork.mchost.util.TutorialCoach.maybeStartTabTour(this, tabs, repo, server.getId());
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                if (t.getPosition() == 1 && server != null && server.state == ServerInstance.State.OFFLINE) {
                    // Block opening console when offline
                    android.content.Intent w = new android.content.Intent(ServerDetailActivity.this, PraetorWarningActivity.class);
                    w.putExtra(PraetorWarningActivity.EXTRA_REASON, getString(R.string.praetor_reason_console_offline));
                    w.putExtra(PraetorWarningActivity.EXTRA_ACTION, getString(R.string.praetor_action_back));
                    startActivity(w);
                    
                    // com.google.android.material.switchmaterial.SwitchMaterial back to Dashboard (index 0)
                    tabs.selectTab(tabs.getTabAt(0));
                    return;
                }
                showTab(t.getPosition());
                // Re-check server.properties for manual edits whenever the
                // dashboard is (re)entered, not only when the screen opens
                if (t.getPosition() == 0) checkPropsDriftAndWarn();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {
                if (t.getPosition() == 1 && server != null && server.state == ServerInstance.State.OFFLINE) {
                    tabs.selectTab(tabs.getTabAt(0));
                } else if (t.getPosition() == 0) {
                    checkPropsDriftAndWarn();
                }
            }
        });
        showTab(0);
    }

        private void showPlayerActions() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.KodaBottomSheetDialog);
        sheet.setContentView(R.layout.bottom_sheet_player_list);
        setupWindowDecor(sheet.getWindow());

        android.widget.LinearLayout containerOnline = sheet.findViewById(R.id.container_pm_online);
        android.widget.LinearLayout containerOffline = sheet.findViewById(R.id.container_pm_offline);
        android.widget.TextView headerOnline = sheet.findViewById(R.id.tv_pm_online_header);
        android.widget.TextView headerOffline = sheet.findViewById(R.id.tv_pm_offline_header);
        final android.view.View emptyState = sheet.findViewById(R.id.container_pm_empty);

        if (containerOnline != null && containerOffline != null && headerOnline != null && headerOffline != null) {
            containerOnline.removeAllViews();
            containerOffline.removeAllViews();
            
                        
            if (server != null && server.onlinePlayerNames != null && !server.onlinePlayerNames.isEmpty()) {
                for (String playerName : server.onlinePlayerNames) {
                    android.view.View playerRow = getLayoutInflater().inflate(R.layout.item_player_row, containerOnline, false);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(playerRow.getContext(), playerRow);
                    android.widget.TextView tvName = playerRow.findViewById(R.id.tv_row_player_name);
                    tvName.setText(playerName);
                    
                    android.widget.ImageView ivHead = playerRow.findViewById(R.id.iv_row_player_head);
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL("https://minotar.net/helm/" + playerName + "/64.png");
                            java.io.InputStream in = url.openStream();
                            final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                            runOnUiThread(() -> {
                                if (ivHead != null && bmp != null) ivHead.setImageBitmap(bmp);
                            });
                        } catch (Exception e) {}
                    }).start();
                    
                    playerRow.setOnClickListener(v -> {
                        sheet.dismiss();
                        showPlayerActionSheet(playerName, true);
                    });
                    
                    containerOnline.addView(playerRow);
                }
                headerOnline.setVisibility(android.view.View.VISIBLE);
                containerOnline.setVisibility(android.view.View.VISIBLE);
                if (emptyState != null) emptyState.setVisibility(android.view.View.GONE);
            } else {
                // Show empty state or hide
                headerOnline.setVisibility(android.view.View.GONE);
                containerOnline.setVisibility(android.view.View.GONE);
                if (emptyState != null) emptyState.setVisibility(android.view.View.VISIBLE);
            }
            
            new Thread(() -> {
                try {
                    java.io.File cacheFile = new java.io.File(server.getServerDir(), "usercache.json");
                    if (!cacheFile.exists()) cacheFile = new java.io.File(server.getServerDir(), "whitelist.json");
                    
                    if (cacheFile.exists()) {
                        StringBuilder sb = new StringBuilder();
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(cacheFile))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                        org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                        java.util.List<String> offlineNames = new java.util.ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            String name = obj.optString("name");
                            if (name != null && !name.isEmpty() && (server.onlinePlayerNames == null || !server.onlinePlayerNames.contains(name))) {
                                if (!offlineNames.contains(name)) offlineNames.add(name);
                            }
                        }
                        
                        if (!offlineNames.isEmpty()) {
                            runOnUiThread(() -> {
                                for (String offlineName : offlineNames) {
                                    android.view.View playerRow = getLayoutInflater().inflate(R.layout.item_player_row, containerOffline, false);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(playerRow.getContext(), playerRow);
                                    android.widget.TextView tvName = playerRow.findViewById(R.id.tv_row_player_name);
                                    tvName.setText(offlineName);
                                    tvName.setTextColor(0xFF888899); // darker text for offline
                                    
                                    android.widget.ImageView ivHead = playerRow.findViewById(R.id.iv_row_player_head);
                                    // optional grayscale matrix
                                    android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                                    matrix.setSaturation(0);
                                    android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
                                    ivHead.setColorFilter(filter);
                                    
                                    new Thread(() -> {
                                        try {
                                            java.net.URL url = new java.net.URL("https://minotar.net/helm/" + offlineName + "/64.png");
                                            java.io.InputStream in = url.openStream();
                                            final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                                            runOnUiThread(() -> {
                                                if (ivHead != null && bmp != null) ivHead.setImageBitmap(bmp);
                                            });
                                        } catch (Exception e) {}
                                    }).start();
                                    
                                    playerRow.setOnClickListener(v -> {
                                        sheet.dismiss();
                                        showPlayerActionSheet(offlineName, false); // offline player
                                    });
                                    
                                    containerOffline.addView(playerRow);
                                }
                                headerOffline.setVisibility(android.view.View.VISIBLE);
                                containerOffline.setVisibility(android.view.View.VISIBLE);
                                if (emptyState != null) emptyState.setVisibility(android.view.View.GONE);
                            });
                        }
                    }
                } catch (Exception e) {}
            }).start();
        }

        eu.kodanetwork.mchost.util.SheetFix.apply(sheet);
        sheet.show();
    }
    
    private void showPlayerActionSheet(String player, boolean isOnline) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.KodaBottomSheetDialog);
        
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_player_manage, null);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(view.getContext(), view);
        sheet.setContentView(view);
        
        android.view.Window w = sheet.getWindow();
        setupWindowDecor(w);
        
        // Setup Header
        TextView tvName = view.findViewById(R.id.tv_pm_player_name);
        tvName.setText(player + (isOnline ? " (Online)" : " (Offline)"));
        
        // Buttons
        android.widget.Button btnHeal = view.findViewById(R.id.btn_pm_heal);
        android.widget.Button btnStarve = view.findViewById(R.id.btn_pm_starve);
        android.widget.Button btnKill = view.findViewById(R.id.btn_pm_kill);
        android.widget.Button btnDelete = view.findViewById(R.id.btn_pm_delete);
        android.widget.Button btnFeed = view.findViewById(R.id.btn_pm_feed);
        android.widget.Button btnOp = view.findViewById(R.id.btn_pm_op);
        android.widget.Button btnKick = view.findViewById(R.id.btn_pm_kick);
        android.widget.Button btnBan = view.findViewById(R.id.btn_pm_ban);
        com.google.android.material.switchmaterial.SwitchMaterial switchWhitelist = view.findViewById(R.id.switch_pm_whitelist);

        // Player heads, UUID lookups and item icons all need internet — gray out
        // every action while offline; effect/kick commands additionally need the
        // player entity on the server
        applyPmAvailability(view, isOnline,
            eu.kodanetwork.mchost.util.NetworkMonitorManager.isInternetAvailable(this));

        btnHeal.setOnClickListener(v -> {
            sendCmd("effect give " + player + " instant_health 1 255");
            android.widget.Toast.makeText(this, "Healed " + player, android.widget.Toast.LENGTH_SHORT).show();
        });

        btnFeed.setOnClickListener(v -> {
            sendCmd("effect give " + player + " saturation 30 0");
            android.widget.Toast.makeText(this, getString(R.string.pm_fed_toast, player), android.widget.Toast.LENGTH_SHORT).show();
        });

        btnStarve.setOnClickListener(v -> {
            sendCmd("effect give " + player + " hunger 100 255");
            android.widget.Toast.makeText(this, "Starving " + player, android.widget.Toast.LENGTH_SHORT).show();
        });

        btnKill.setOnClickListener(v -> {
            sendCmd("kill " + player);
            android.widget.Toast.makeText(this, "Killed " + player, android.widget.Toast.LENGTH_SHORT).show();
        });

        btnKick.setOnClickListener(v -> {
            sendCmd("kick " + player);
            android.widget.Toast.makeText(this, getString(R.string.pm_kicked_toast, player), android.widget.Toast.LENGTH_SHORT).show();
        });

        // Shared ban state — the refresh loop flips the button between Ban and Pardon
        final boolean[] bannedState = { false };
        btnBan.setOnClickListener(v -> {
            if (bannedState[0]) {
                sendCmd("pardon " + player);
                android.widget.Toast.makeText(this, getString(R.string.pm_pardoned_toast, player), android.widget.Toast.LENGTH_SHORT).show();
                bannedState[0] = false;
                btnBan.setText(getString(R.string.pm_ban));
                btnBan.setBackgroundColor(0xFFAA00FF);
                return;
            }
            android.app.Dialog banDialog = new android.app.Dialog(this);
            banDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            banDialog.setContentView(R.layout.dialog_praetor_delete);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(banDialog);
            if (banDialog.getWindow() != null) {
                banDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                banDialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            }
            TextView banDialogTitle = banDialog.findViewById(R.id.tv_dialog_title);
            if (banDialogTitle != null) {
                String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
                banDialogTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
            }
            ((TextView) banDialog.findViewById(R.id.tv_delete_title)).setText(getString(R.string.pm_ban_title));
            ((TextView) banDialog.findViewById(R.id.tv_delete_body)).setText(getString(R.string.pm_ban_desc, player));
            android.widget.Button btnBanConfirm = banDialog.findViewById(R.id.btn_dialog_delete);
            btnBanConfirm.setText(getString(R.string.pm_ban_confirm));
            btnBanConfirm.setOnClickListener(x -> {
                sendCmd("ban " + player + " Banned via KodaNetwork");
                android.widget.Toast.makeText(this, getString(R.string.pm_banned_toast, player), android.widget.Toast.LENGTH_SHORT).show();
                banDialog.dismiss();
            });
            banDialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(x -> banDialog.dismiss());
            banDialog.show();
        });

        // OP state comes from ops.json so the button reflects reality and also
        // works for offline players
        final boolean[] opped = { isOperator(new java.io.File(server.getServerDir()), player) };
        btnOp.setText(getString(opped[0] ? R.string.pm_deop : R.string.pm_op));
        btnOp.setOnClickListener(v -> {
            opped[0] = !opped[0];
            sendCmd((opped[0] ? "op " : "deop ") + player);
            btnOp.setText(getString(opped[0] ? R.string.pm_deop : R.string.pm_op));
            android.widget.Toast.makeText(this, getString(opped[0] ? R.string.pm_opped_toast : R.string.pm_deopped_toast, player), android.widget.Toast.LENGTH_SHORT).show();
        });

        btnDelete.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Wipe Player Data")
                .setMessage("Are you sure? This will kick the player and delete their inventory and stats.")
                .setPositiveButton("Wipe", (d, w2) -> {
                    sendCmd("kick " + player + " Your data is being wiped.");
                    new Thread(() -> {
                        try { Thread.sleep(1000); } catch(Exception ignored){}
                        String uuid = eu.kodanetwork.mchost.util.PlayerStatsParser.getUuidFromName(new java.io.File(server.getServerDir()), player);
                        if (uuid != null) {
                            java.io.File serverDir = new java.io.File(server.getServerDir());
                            eu.kodanetwork.mchost.util.PlayerStatsParser.getPlayerDataFile(serverDir, uuid).delete();
                            eu.kodanetwork.mchost.util.PlayerStatsParser.getStatsFile(serverDir, uuid).delete();
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(this, "Wiped " + player, android.widget.Toast.LENGTH_SHORT).show();
                                sheet.dismiss();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        // Stats & Live Updates — the loop keeps running (and retrying) while the
        // sheet is open, so a failed or crashed load recovers on its own
        new Thread(() -> {
            java.io.File serverDir = new java.io.File(server.getServerDir());
            final boolean[] statsAnimated = {false};
            final boolean[] invAnimated = {false};
            long lastFlush = 0;
            while (sheet.isShowing()) {
                boolean hasInternet = eu.kodanetwork.mchost.util.NetworkMonitorManager.isInternetAvailable(ServerDetailActivity.this);
                String uuid = null;
                eu.kodanetwork.mchost.util.PlayerStatsParser.PlayerStats stats = null;
                boolean statsAvailable = false;
                java.util.Map<String, Object> dat = null;
                boolean datExists = false;
                try {
                    uuid = eu.kodanetwork.mchost.util.PlayerStatsParser.getUuidFromName(serverDir, player);
                    if (uuid != null) {
                        stats = eu.kodanetwork.mchost.util.PlayerStatsParser.getStats(serverDir, uuid);
                        statsAvailable = eu.kodanetwork.mchost.util.PlayerStatsParser.getStatsFile(serverDir, uuid).exists()
                                || eu.kodanetwork.mchost.util.PlayerStatsParser.getLiveStatsFile(serverDir, uuid).exists();
                        java.io.File datFile = eu.kodanetwork.mchost.util.PlayerStatsParser.getPlayerDataFile(serverDir, uuid);
                        datExists = datFile.exists();
                        if (datExists) {
                            // playerdata being rewritten mid-read fails — retry next tick
                            try { dat = eu.kodanetwork.mchost.util.NbtParser.parsePlayerDat(datFile); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}

                // Read Whitelist
                boolean isWhitelisted = false;
                if (uuid != null) {
                    try {
                        java.io.File wl = new java.io.File(server.getServerDir(), "whitelist.json");
                        if (wl.exists()) {
                            byte[] bytes = new byte[(int) wl.length()];
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(wl)) { fis.read(bytes); }
                            org.json.JSONArray arr = new org.json.JSONArray(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                            for (int i = 0; i < arr.length(); i++) {
                                if (arr.getJSONObject(i).getString("uuid").equalsIgnoreCase(uuid)) {
                                    isWhitelisted = true; break;
                                }
                            }
                        }
                    } catch(Exception ignored){}
                }

                boolean finalIsWhitelisted = isWhitelisted;
                final String fUuid = uuid;
                final eu.kodanetwork.mchost.util.PlayerStatsParser.PlayerStats fStats = stats;
                final boolean fStatsAvailable = statsAvailable;
                final java.util.Map<String, Object> fDat = dat;
                final boolean fDatExists = datExists;
                final boolean fHasInternet = hasInternet;
                runOnUiThread(() -> {
                    if (!sheet.isShowing()) return;
                    applyPmAvailability(view, isOnline, fHasInternet);

                    android.view.View pbStats = view.findViewById(R.id.pb_pm_stats);
                    android.widget.GridLayout gridStats = view.findViewById(R.id.grid_pm_stats);
                    if (fStatsAvailable) {
                        if (fStats != null) {
                            TextView tvDeaths = view.findViewById(R.id.tv_pm_stat_deaths);
                            TextView tvMined = view.findViewById(R.id.tv_pm_stat_mined);
                            TextView tvHours = view.findViewById(R.id.tv_pm_stat_hours);
                            TextView tvMobs = view.findViewById(R.id.tv_pm_stat_mobs);
                            TextView tvDmg = view.findViewById(R.id.tv_pm_stat_dmg);

                            tvDeaths.setText(String.valueOf(fStats.deaths));
                            tvMined.setText(String.valueOf(fStats.blocksMined));
                            tvHours.setText(String.valueOf(fStats.hoursPlayed));
                            tvMobs.setText(String.valueOf(fStats.mobsKilled));
                            tvDmg.setText(String.valueOf(fStats.damageTaken));
                        }
                        showLottie(pbStats, false);
                        if (!statsAnimated[0]) {
                            statsAnimated[0] = true;
                            fadeIn(gridStats);
                        }
                    } else {
                        // an online player gets live stats within seconds; an
                        // offline player without a stats file stays at 0
                        showLottie(pbStats, isOnline);
                        statsAnimated[0] = false;
                    }

                    if (fUuid != null) {
                        switchWhitelist.setOnCheckedChangeListener(null);
                        switchWhitelist.setChecked(finalIsWhitelisted);
                        switchWhitelist.setOnCheckedChangeListener((btn, isChecked) -> {
                            sendCmd((isChecked ? "whitelist add " : "whitelist remove ") + player);
                        });
                    }

                    // Ban/Pardon toggle — banned-players.json is the source of truth
                    boolean nowBanned = isPlayerBanned(serverDir, player);
                    if (nowBanned != bannedState[0]) {
                        bannedState[0] = nowBanned;
                        btnBan.setText(getString(nowBanned ? R.string.pm_pardon : R.string.pm_ban));
                        btnBan.setBackgroundColor(nowBanned ? 0xFF00A854 : 0xFFAA00FF);
                    }

                    // Inventory — rebuild the grid whenever it is incomplete; a
                    // crashed build can otherwise leave a single slot behind
                    android.widget.GridLayout gridMain = view.findViewById(R.id.grid_inventory_main);
                    android.widget.GridLayout gridHotbar = view.findViewById(R.id.grid_inventory_hotbar);
                    android.widget.LinearLayout containerArmor = view.findViewById(R.id.container_armor);
                    android.widget.FrameLayout containerOffhand = view.findViewById(R.id.container_offhand);
                    if (gridMain == null || gridHotbar == null || containerArmor == null || containerOffhand == null) return;
                    if (gridMain.getChildCount() != 27 || gridHotbar.getChildCount() != 9
                            || containerArmor.getChildCount() != 4 || containerOffhand.getChildCount() != 1) {
                        gridMain.removeAllViews();
                        gridHotbar.removeAllViews();
                        containerArmor.removeAllViews();
                        containerOffhand.removeAllViews();
                        buildInventoryGrid(view);
                    }

                    android.view.View pbInv = view.findViewById(R.id.pb_pm_inventory);
                    android.view.View invContainer = view.findViewById(R.id.container_pm_inventory);
                    java.util.List<Object> items = (fDat != null && fDat.get("Inventory") instanceof java.util.List)
                            ? (java.util.List<Object>) fDat.get("Inventory") : java.util.Collections.emptyList();
                    // fillInventoryItems resets all slots first, so an empty
                    // list clears stale icons while (re)loading
                    fillInventoryItems(view, items);
                    if (fDat != null && fDat.containsKey("Inventory")) {
                        showLottie(pbInv, false);
                        if (!invAnimated[0]) {
                            invAnimated[0] = true;
                            fadeIn(invContainer);
                        }
                    } else {
                        // spinner only while data is actually expected: unreadable
                        // playerdata is retried, an online player's file may still
                        // appear — an offline player without data is simply empty
                        boolean waitingForData = fDat == null && (fDatExists || isOnline);
                        showLottie(pbInv, waitingForData);
                        invAnimated[0] = false;
                    }
                });

                // Without KodaTransfer (e.g. Fabric servers) vanilla only writes
                // playerdata/stats on save or quit — flush periodically while the
                // player is online and data is still missing
                if (isOnline && (dat == null || !statsAvailable)
                        && System.currentTimeMillis() - lastFlush > 12000) {
                    lastFlush = System.currentTimeMillis();
                    runOnUiThread(() -> {
                        if (sheet.isShowing()) sendCmd("save-all");
                    });
                }

                try { Thread.sleep(2000); } catch (Exception ignored) {}
            }
        }).start();

        sheet.show();
    }

    /** True when the player has an entry in banned-players.json. */
    private boolean isPlayerBanned(java.io.File serverDir, String playerName) {
        try {
            java.io.File f = new java.io.File(serverDir, "banned-players.json");
            if (!f.exists()) return false;
            byte[] bytes = new byte[(int) f.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                fis.read(bytes);
            }
            org.json.JSONArray arr = new org.json.JSONArray(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o != null && o.optString("name", "").equalsIgnoreCase(playerName)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
    
    private boolean isOperator(java.io.File serverDir, String playerName) {
        try {
            java.io.File ops = new java.io.File(serverDir, "ops.json");
            if (!ops.exists()) return false;
            byte[] bytes = new byte[(int) ops.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(ops)) { fis.read(bytes); }
            org.json.JSONArray arr = new org.json.JSONArray(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("name", "").equalsIgnoreCase(playerName)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Grays out every player-manager action while there is no internet
     * connection; effect/kick commands additionally require the target player
     * to be online. Re-applied on every refresh tick so buttons come back on
     * their own once connectivity returns.
     */
    private void applyPmAvailability(android.view.View view, boolean isOnline, boolean hasInternet) {
        android.widget.Button[] all = {
            view.findViewById(R.id.btn_pm_heal), view.findViewById(R.id.btn_pm_starve),
            view.findViewById(R.id.btn_pm_kill), view.findViewById(R.id.btn_pm_delete),
            view.findViewById(R.id.btn_pm_feed), view.findViewById(R.id.btn_pm_op),
            view.findViewById(R.id.btn_pm_kick), view.findViewById(R.id.btn_pm_ban)
        };
        java.util.List<android.widget.Button> needOnline = java.util.Arrays.asList(
            view.findViewById(R.id.btn_pm_heal), view.findViewById(R.id.btn_pm_starve),
            view.findViewById(R.id.btn_pm_kill), view.findViewById(R.id.btn_pm_feed),
            view.findViewById(R.id.btn_pm_kick));
        for (android.widget.Button b : all) {
            if (b == null) continue;
            boolean enabled = hasInternet && (isOnline || !needOnline.contains(b));
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : 0.4f);
        }
        android.widget.CompoundButton sw = view.findViewById(R.id.switch_pm_whitelist);
        if (sw != null) {
            sw.setEnabled(hasInternet);
            sw.setAlpha(hasInternet ? 1f : 0.4f);
        }
        android.view.View hint = view.findViewById(R.id.tv_pm_offline_hint);
        if (hint != null) hint.setVisibility(hasInternet ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    /** Short fade-in used to animate (re)loaded stats/inventory content. */
    private void fadeIn(android.view.View v) {
        if (v == null) return;
        v.clearAnimation();
        v.animate().cancel();
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(350).start();
    }

    /** Shows/hides a loading view; Lottie views also pause/resume with it. */
    private void showLottie(android.view.View v, boolean show) {
        if (v == null) return;
        v.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (v instanceof com.airbnb.lottie.LottieAnimationView) {
            com.airbnb.lottie.LottieAnimationView lav = (com.airbnb.lottie.LottieAnimationView) v;
            if (show) lav.playAnimation(); else lav.pauseAnimation();
        }
    }

    /** Inflates the empty armor/offhand/main/hotbar slots. Runs once per sheet. */
    private void buildInventoryGrid(android.view.View view) {
        android.widget.LinearLayout containerArmor = view.findViewById(R.id.container_armor);
        android.widget.FrameLayout containerOffhand = view.findViewById(R.id.container_offhand);
        android.widget.GridLayout gridMain = view.findViewById(R.id.grid_inventory_main);
        android.widget.GridLayout gridHotbar = view.findViewById(R.id.grid_inventory_hotbar);
        if (containerArmor == null || containerOffhand == null || gridMain == null || gridHotbar == null) return;

        for (int i = 0; i < 4; i++) {
            containerArmor.addView(getLayoutInflater().inflate(R.layout.item_inventory_slot, containerArmor, false));
        }
        containerOffhand.addView(getLayoutInflater().inflate(R.layout.item_inventory_slot, containerOffhand, false));

        for (int i = 0; i < 27; i++) {
            android.view.View slot = getLayoutInflater().inflate(R.layout.item_inventory_slot, gridMain, false);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(slot.getContext(), slot);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(i / 9), android.widget.GridLayout.spec(i % 9)
            );
            gridMain.addView(slot, params);
        }

        for (int i = 0; i < 9; i++) {
            android.view.View slot = getLayoutInflater().inflate(R.layout.item_inventory_slot, gridHotbar, false);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(slot.getContext(), slot);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(0), android.widget.GridLayout.spec(i)
            );
            gridHotbar.addView(slot, params);
        }
    }

    private void fillInventoryItems(android.view.View view, java.util.List<Object> inventory) {
        android.widget.LinearLayout containerArmor = view.findViewById(R.id.container_armor);
        android.widget.FrameLayout containerOffhand = view.findViewById(R.id.container_offhand);
        android.widget.GridLayout gridMain = view.findViewById(R.id.grid_inventory_main);
        android.widget.GridLayout gridHotbar = view.findViewById(R.id.grid_inventory_hotbar);
        if (containerArmor == null || containerOffhand == null || gridMain == null || gridHotbar == null) return;

        // Reset all slots so moved/removed items disappear on refresh
        resetInventorySlot(containerOffhand.getChildAt(0));
        for (int i = 0; i < containerArmor.getChildCount(); i++) resetInventorySlot(containerArmor.getChildAt(i));
        for (int i = 0; i < gridMain.getChildCount(); i++) resetInventorySlot(gridMain.getChildAt(i));
        for (int i = 0; i < gridHotbar.getChildCount(); i++) resetInventorySlot(gridHotbar.getChildAt(i));

        for (Object itemObj : inventory) {
            if (!(itemObj instanceof java.util.Map)) continue;
            java.util.Map<String, Object> item = (java.util.Map<String, Object>) itemObj;

            int slotId = -1;
            Object slotVal = item.get("Slot");
            if (slotVal instanceof Byte) slotId = (Byte) slotVal; // signed: offhand arrives as -106
            else if (slotVal instanceof Number) slotId = ((Number) slotVal).intValue();

            String id = (String) item.get("id"); // e.g. minecraft:stone
            int count = 1;
            // <=1.20.4 stores "Count" (byte), 1.20.5+ uses "count" (int)
            Object countVal = item.containsKey("count") ? item.get("count") : item.get("Count");
            if (countVal instanceof Number) count = ((Number) countVal).intValue();

            android.view.View targetView = null;
            if (slotId >= 0 && slotId <= 8) targetView = gridHotbar.getChildAt(slotId);
            else if (slotId >= 9 && slotId <= 35) targetView = gridMain.getChildAt(slotId - 9);
            else if (slotId >= 100 && slotId <= 103) targetView = containerArmor.getChildAt(103 - slotId); // 103=helmet, 102=chest, 101=legs, 100=boots
            else if (slotId == -106) targetView = containerOffhand.getChildAt(0);

            if (targetView != null && id != null) {
                android.widget.ImageView iv = targetView.findViewById(R.id.iv_item_icon);
                android.widget.TextView tvCount = targetView.findViewById(R.id.tv_item_count);

                String itemName = id.replace("minecraft:", "");
                String url = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/item/" + itemName + ".png";

                com.bumptech.glide.Glide.with(this).load(url).into(iv);

                if (count > 1) {
                    tvCount.setVisibility(android.view.View.VISIBLE);
                    tvCount.setText(String.valueOf(count));
                }
            }
        }
    }

    private void resetInventorySlot(android.view.View slot) {
        if (slot == null) return;
        android.widget.ImageView iv = slot.findViewById(R.id.iv_item_icon);
        if (iv != null) iv.setImageDrawable(null);
        android.widget.TextView tvCount = slot.findViewById(R.id.tv_item_count);
        if (tvCount != null) {
            tvCount.setVisibility(android.view.View.GONE);
            tvCount.setText("");
        }
    }

    private void showTab(int i) {
        int targetSettings = server.isDatabase() ? 3 : 4;
        animatePanel(pDash, i == 0);
        animatePanel(pConsole, i == 1);
        animatePanel(pFiles, i == 2);
        if (pPlugins != null) animatePanel(pPlugins, !server.isDatabase() && i == 3);
        animatePanel(pSettings, i == targetSettings);
        if (i == 2) refreshFiles();
    }

    /** Toggles dashboard tab visibility without animation. */
    private void animatePanel(android.view.View panel, boolean show) {
        if (panel == null) return;
        panel.animate().cancel();
        panel.setAlpha(1f);
        panel.setTranslationY(0f);
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean successFlyRunning = false;

    /**
     * Success sequence when a setup finishes: the three orange loader blocks
     * fly into the server name and merge with its letters while the loading
     * overlay slowly turns transparent and disappears.
     */
    private void playSuccessFlyAndDismiss() {
        if (layoutFullLoading == null || layoutFullLoading.getVisibility() != View.VISIBLE || successFlyRunning) return;
        successFlyRunning = true;

        com.airbnb.lottie.LottieAnimationView boot = findViewById(R.id.lottie_full_loading);
        TextView title = findViewById(R.id.tv_title);
        android.view.ViewGroup contentRoot = (android.view.ViewGroup) findViewById(android.R.id.content);

        int[] bootPos = new int[2];
        int bootW = 0, bootH = 0;
        if (boot != null) {
            boot.getLocationOnScreen(bootPos);
            bootW = boot.getWidth();
            bootH = boot.getHeight();
            boot.pauseAnimation();
        }
        int[] titlePos = new int[2];
        int titleW = 0, titleH = 0;
        if (title != null) {
            title.getLocationOnScreen(titlePos);
            titleW = title.getWidth();
            titleH = title.getHeight();
        }
        int[] rootPos = new int[2];
        if (contentRoot != null) contentRoot.getLocationOnScreen(rootPos);

        boolean canFly = contentRoot != null && bootW > 0 && bootH > 0 && titleW > 0 && titleH > 0;
        if (canFly) {
            float density = getResources().getDisplayMetrics().density;
            // blocks sit at x 70/120/170 (of 240) and y 40 (of 80) in the boot animation
            float[] relX = {70f, 120f, 170f};
            float targetX = titlePos[0] - rootPos[0] + titleW / 2f;
            float targetY = titlePos[1] - rootPos[1] + titleH / 2f;
            float size = bootW * (28f / 240f);

            for (int i = 0; i < 3; i++) {
                float startX = bootPos[0] - rootPos[0] + bootW * (relX[i] / 240f) - size / 2f;
                float startY = bootPos[1] - rootPos[1] + bootH * (40f / 80f) - size / 2f;

                android.widget.ImageView block = new android.widget.ImageView(this);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                bg.setCornerRadius(3f * density);
                bg.setColor(i == 1 ? 0xFFFF8C38 : 0xFFFF6B00);
                block.setBackground(bg);
                block.setLayoutParams(new android.view.ViewGroup.LayoutParams((int) size, (int) size));
                block.setElevation(200f * density); // above the fading overlay
                contentRoot.addView(block);
                block.setTranslationX(startX);
                block.setTranslationY(startY);

                // fly into the letters, shrink and fade as they merge
                block.animate()
                    .translationX(targetX - size / 2f)
                    .translationY(targetY - size / 2f)
                    .scaleX(0.15f).scaleY(0.15f)
                    .alpha(0f)
                    .setStartDelay(i * 90L)
                    .setDuration(680L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .withEndAction(() -> contentRoot.removeView(block))
                    .start();
            }

            // the name swallows the blocks with a small pulse
            if (title != null) {
                title.animate().cancel();
                title.animate().scaleX(1.14f).scaleY(1.14f)
                    .setStartDelay(240L).setDuration(160L)
                    .withEndAction(() -> title.animate().scaleX(1f).scaleY(1f).setDuration(220L).start())
                    .start();
            }

            if (boot != null) boot.setVisibility(View.INVISIBLE); // blocks took over
        }

        // the overlay turns transparent and vanishes while the blocks fly
        layoutFullLoading.animate()
            .alpha(0f)
            .setDuration(canFly ? 950L : 450L)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(() -> {
                layoutFullLoading.setVisibility(View.GONE);
                layoutFullLoading.setAlpha(1f);
                successFlyRunning = false;
            })
            .start();
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    private void setupDashButtons() {
        if (btnStart != null) btnStart.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.AppLogger.log("UI", "START android.widget.Button clicked for server: " + server.getName());
            File serverDir = new File(server.getServerDir());
            if (!serverDir.exists()) {
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Server directory does not exist: " + serverDir.getAbsolutePath());
                serverDir.mkdirs();
            }

            if (!server.isDatabase()) {
                if (server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.PUMPKIN) {
                    // native Rust binary — no jar in the server dir
                    checkEulaAndStart();
                    return;
                }
                File[] jars = serverDir.listFiles((d, name) -> name.endsWith(".jar"));
                if (jars == null || jars.length == 0) {
                    eu.kodanetwork.mchost.util.AppLogger.log("UI", "No .jar file found in " + serverDir.getAbsolutePath());
                    Toast.makeText(this, "Bitte zuerst die Server .jar herunterladen (Settings-Tab)", Toast.LENGTH_LONG).show();
                    if (tabs != null) tabs.selectTab(tabs.getTabAt(tabs.getTabCount() - 1));
                    return;
                }
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Found jar: " + jars[0].getName() + ". Binding and starting service...");
                checkEulaAndStart();
            } else {
                checkQueueAndStart();
            }
        });
        if (btnStop != null) btnStop.setOnClickListener(v -> sendAction(KodaServerService.ACTION_STOP));
        if (btnRestart != null) btnRestart.setOnClickListener(v -> sendAction(KodaServerService.ACTION_RESTART));
        if (btnKill != null) btnKill.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sd_dialog_force_kill_title))
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

        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_praetor_eula);
        dialog.setCancelable(true);

        android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            String mcHtml = "<font color=\"#55FF55\">Minecraft</font> <font color=\"#AAAAAA\">EULA</font>";
            tvTitle.setText(android.text.Html.fromHtml(mcHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }

        com.google.android.material.button.MaterialButton btnDecline = dialog.findViewById(R.id.btn_dialog_decline);
        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> dialog.dismiss());
        }

        com.google.android.material.button.MaterialButton btnAccept = dialog.findViewById(R.id.btn_dialog_accept);
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                prefs.edit().putBoolean("eula_" + server.getId(), true).apply();
                dialog.dismiss();
                checkQueueAndStart();
            });
        }

        dialog.show();
    }



    private void startServer() {
        if (!bound || svc == null) return;
        
        // Add start command
        Intent i = new Intent(this, KodaServerService.class);
        i.setAction(KodaServerService.ACTION_START);
        i.putExtra(KodaServerService.EXTRA_ID, server.getId());
        startService(i);
        
        // Show loading screen...
        if (layoutFullLoading != null) {
            layoutFullLoading.setVisibility(View.VISIBLE);
        }
    }
    
    private void checkForUpdatesAsync() {
        android.widget.Button btnUpdate = findViewById(R.id.btn_settings_update);
        if (btnUpdate == null) return;
        
        new Thread(() -> {
            boolean hasUpdates = false;
            
            // Check Server Software
            if (server.getType() == ServerInstance.Type.PAPER) {
                try {
                    String buildUrl = "https://api.papermc.io/v2/projects/paper/versions/" + server.getVersion() + "/builds";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(buildUrl).openConnection();
                    if (conn.getResponseCode() == 200) {
                        java.io.InputStream is = conn.getInputStream();
                        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                        String result = s.hasNext() ? s.next() : "";
                        is.close();
                        
                        org.json.JSONObject res = new org.json.JSONObject(result);
                        org.json.JSONArray builds = res.getJSONArray("builds");
                        if (builds.length() > 0) {
                            int latestBuildNum = builds.getJSONObject(builds.length() - 1).getInt("build");
                            
                            // Check local jar
                            File serverJar = new File(server.getServerDir(), "paper-" + server.getVersion() + "-" + latestBuildNum + ".jar");
                            if (!serverJar.exists()) {
                                hasUpdates = true;
                            }
                        }
                    }
                } catch (Exception e) {}
            }
            
            // Check Plugins
            if (!hasUpdates) {
                java.util.List<String> projectIds = new java.util.ArrayList<>(server.pluginVersions.keySet());
                for (String pid : projectIds) {
                    try {
                        String latestVersionId = eu.kodanetwork.mchost.util.ModrinthHelper.getLatestVersionIdSync(pid, server);
                        if (latestVersionId != null && !latestVersionId.equals(server.pluginVersions.get(pid))) {
                            hasUpdates = true;
                            break;
                        }
                    } catch (Exception e) {}
                }
            }
            
            if (hasUpdates) {
                runOnUiThread(() -> {
                    btnUpdate.setText(getString(R.string.sd_updates_available));
                    btnUpdate.setTextColor(android.graphics.Color.parseColor("#00E676"));
                    
                    android.view.View btnDashUpdate = findViewById(R.id.btn_dashboard_update);
                    if (btnDashUpdate != null) {
                        btnDashUpdate.setVisibility(View.VISIBLE);
                        btnDashUpdate.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(ServerDetailActivity.this, UpdateServerActivity.class);
                            intent.putExtra("SERVER_ID", server.getId());
                            startActivity(intent);
                        });
                    }
                });
            } else {
                runOnUiThread(() -> {
                    btnUpdate.setText(getString(R.string.sd_check_updates));
                    btnUpdate.setTextColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF333333 : 0xFFDDDDDD);
                    
                    android.view.View btnDashUpdate = findViewById(R.id.btn_dashboard_update);
                    if (btnDashUpdate != null) {
                        btnDashUpdate.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
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
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        // bounds the WHOLE call incl. DNS/TLS — without this a hung
                        // lookup blocks the single-thread io executor forever and every
                        // later START tap silently queues behind it
                        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers?server_version=neq.&select=id")
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
                if (tvFullLoadingMsg != null) tvFullLoadingMsg.setText(getString(R.string.sd_auto_setup_running));
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
                        updateJoinAddressDisplay();
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
                orchestrator.run(server, "");
            } catch (Exception e) {
                runOnUiThread(() -> tvDomainStatus.setText("Domain error: " + e.getMessage()));
            }
        });
    }
    private void sendAction(String action) {
        if (server == null) return;
        android.content.Intent i = new android.content.Intent(this, eu.kodanetwork.mchost.service.KodaServerService.class);
        i.setAction(action);
        i.putExtra(eu.kodanetwork.mchost.service.KodaServerService.EXTRA_ID, server.getId());
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void handleDnsOccupied() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_join_address);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }

        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_subtitle)).setText(R.string.praetor_subtitle_dns_conflict);
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(R.string.praetor_message_dns_conflict);

        android.widget.EditText input = dialog.findViewById(R.id.et_dialog_input);
        input.setHint(R.string.praetor_hint_change_domain);

        // No domain switch here — hide the toggle, keep the suffix static
        android.view.View domainToggle = dialog.findViewById(R.id.container_join_domain);
        if (domainToggle != null) domainToggle.setVisibility(android.view.View.GONE);
        android.widget.TextView suffix = dialog.findViewById(R.id.tv_dialog_suffix);
        if (suffix != null) suffix.setText("." + server.getBaseDomain());

        final android.widget.TextView[] stepIcons = {
                dialog.findViewById(R.id.step_icon_1), dialog.findViewById(R.id.step_icon_2),
                dialog.findViewById(R.id.step_icon_3), dialog.findViewById(R.id.step_icon_4)};
        final android.widget.TextView[] stepTexts = {
                dialog.findViewById(R.id.step_text_1), dialog.findViewById(R.id.step_text_2),
                dialog.findViewById(R.id.step_text_3), dialog.findViewById(R.id.step_text_4)};
        final android.view.View inputPhase = dialog.findViewById(R.id.layout_join_input_phase);
        final android.view.View stepsPhase = dialog.findViewById(R.id.layout_join_steps_phase);
        final com.google.android.material.button.MaterialButton btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);
        final com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);

        // Steps: 1) DB sync of new host, 2) wake server, 3) done
        stepTexts[0].setText(getString(R.string.join_step_db));
        stepTexts[1].setText(getString(R.string.sd_toast_server_woken));
        stepTexts[2].setText(getString(R.string.join_step_done));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            stepIcons[idx].setText("•");
            stepIcons[idx].setTextColor(0xFF555566);
            stepTexts[idx].setTextColor(0xFF555566);
        }

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        btnConfirm.setOnClickListener(view -> {
            String newSubdomain = input.getText().toString().trim().toLowerCase();
            if (!newSubdomain.matches("^[a-z0-9-]+$") || newSubdomain.length() < 3) {
                android.widget.Toast.makeText(this, getString(R.string.sd_toast_invalid_address), android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String oldSubdomain = server.getSubdomain();
            inputPhase.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                inputPhase.setVisibility(android.view.View.GONE);
                stepsPhase.setAlpha(0f);
                stepsPhase.setVisibility(android.view.View.VISIBLE);
                stepsPhase.animate().alpha(1f).setDuration(200).start();
            }).start();
            btnConfirm.setVisibility(android.view.View.GONE);
            btnCancel.setEnabled(false);

            new Thread(() -> {
                boolean dbPatched = false;
                try {
                    stepIcons[0].post(() -> { stepIcons[0].setTextColor(0xFFFF6B00); stepTexts[0].setTextColor(0xFFF0F0F0); });
                    dbPatched = patchServerHostInSupabase(oldSubdomain, newSubdomain, server.getBaseDomain());
                    if (!dbPatched) throw new IllegalStateException("DB sync failed");
                    stepIcons[0].post(() -> { stepIcons[0].setText("✓"); stepIcons[0].setTextColor(0xFF00E676); });

                    server.setSubdomain(newSubdomain);
                    server.setDomainLink(newSubdomain + "." + server.getBaseDomain());
                    repo.update(server);

                    stepIcons[1].post(() -> { stepIcons[1].setTextColor(0xFFFF6B00); stepTexts[1].setTextColor(0xFFF0F0F0); });
                    eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo);
                    stepIcons[1].post(() -> { stepIcons[1].setText("✓"); stepIcons[1].setTextColor(0xFF00E676); });

                    stepIcons[2].post(() -> { stepIcons[2].setText("✓"); stepIcons[2].setTextColor(0xFF00E676); stepTexts[2].setTextColor(0xFFF0F0F0); });
                    runOnUiThread(() -> {
                        updateDash();
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_server_woken), android.widget.Toast.LENGTH_SHORT).show();
                    });
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(dialog::dismiss, 700);
                } catch (Exception ex) {
                    int failedIdx = dbPatched ? 1 : 0;
                    final int fi = failedIdx;
                    stepIcons[fi].post(() -> { stepIcons[fi].setText("✗"); stepIcons[fi].setTextColor(0xFFFF3344); stepTexts[fi].setTextColor(0xFFFF3344); });
                    if (dbPatched) {
                        stepTexts[fi].post(() -> stepTexts[fi].setText(getString(R.string.join_rollback)));
                        try { patchServerHostInSupabase(newSubdomain, oldSubdomain, server.getBaseDomain()); } catch (Exception ignored) {}
                    }
                    String msg = ex.getMessage() == null ? "unknown" : ex.getMessage();
                    stepTexts[fi].post(() -> stepTexts[fi].setText(getString(R.string.join_failed_rolled_back, 0, msg)));
                    runOnUiThread(() -> btnCancel.setEnabled(true));
                }
            }).start();
        });
        dialog.show();
    }

    private Runnable autoCloseRunnable;

    private void updateDash() {
        ServerInstance.State st = server.state;
        String label; int col;
        switch (st) {
            case ONLINE:     label = "● ONLINE";     col = 0xFF69781D; break;
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
        
        if (server.isUpdating()) {
            label = "⬇ UPDATING…";
            col = 0xFF00E676;
        }
        if (tvBadge != null) {
            tvBadge.setText(label);
            tvBadge.setTextColor(col);
        }
        int playerCount = server.onlinePlayerNames != null ? server.onlinePlayerNames.size() : server.onlinePlayers;
        if (tvPlayers != null) tvPlayers.setText(playerCount + " / " + server.getMaxPlayers() + " Spieler");
        updateSleekDash(st, label, col, playerCount);

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
                    if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
                    
                    long lastChange = eu.kodanetwork.mchost.App.getPrefs(this).getLong("last_join_change_" + server.getId(), 0);
                    if (System.currentTimeMillis() - lastChange < 24 * 60 * 60 * 1000L) {
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_subdomain_limit), android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    android.app.Dialog dialog = new android.app.Dialog(this);
                    dialog.setContentView(R.layout.dialog_praetor_input);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    
                    ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_title)).setText(getString(R.string.sd_join_address));
                    ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_subtitle)).setText(getString(R.string.sd_change_subdomain));
                    ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(getString(R.string.sd_change_subdomain_hint));
                    
                    android.widget.EditText input = dialog.findViewById(R.id.et_dialog_input);
                    input.setHint("Neuer Name (z.B. meincoolerserver)");
                    input.setText(server.getSubdomain());
                    
                    android.view.View suffix = dialog.findViewById(R.id.tv_dialog_suffix);
                    if (suffix != null) suffix.setVisibility(android.view.View.VISIBLE);
                    
                    dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
                    dialog.findViewById(R.id.btn_dialog_confirm).setOnClickListener(view -> {
                        String newSubdomain = input.getText().toString().trim().toLowerCase();
                        if (newSubdomain.matches("^[a-z0-9-]+$") && newSubdomain.length() >= 3) {
                            server.setSubdomain(newSubdomain);
                            server.setDomainLink(""); // reset custom domain logic
                            repo.update(server);
                            updateJoinAddressDisplay();
                            eu.kodanetwork.mchost.App.getPrefs(this).edit().putLong("last_join_change_" + server.getId(), System.currentTimeMillis()).apply();
                            android.widget.Toast.makeText(this, getString(R.string.sd_toast_subdomain_updated_next_boot), android.widget.Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } else {
                            android.widget.Toast.makeText(this, getString(R.string.sd_toast_invalid_address), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                    dialog.show();
                });
            }
        } else {
            if (cardControls != null) cardControls.setVisibility(View.VISIBLE);
            if (tabLayout != null) tabLayout.setVisibility(View.VISIBLE);
            if (btnWakeUpDash != null) btnWakeUpDash.setVisibility(View.GONE);
            if (btnChangeDns != null) btnChangeDns.setVisibility(View.GONE);
        }
        
        boolean updating = server.isUpdating();
        if (btnStart != null) btnStart.setEnabled(!running && st != ServerInstance.State.INSTALLING && !restarting && !settingUp && !hibernated && !updating);
        if (btnStop != null) btnStop.setEnabled(running && !updating);
        if (btnRestart != null) btnRestart.setEnabled(running && !updating);
        if (btnKill != null) btnKill.setEnabled((running || restarting) && !updating);
        
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

        updateCrashBanner(st);
    }

    private void updateCrashBanner(ServerInstance.State st) {
        android.widget.FrameLayout bannerContainer = findViewById(R.id.layout_crash_banner);
        if (bannerContainer == null) return;

        if (st != ServerInstance.State.CRASHED) {
            bannerContainer.setVisibility(View.GONE);
            bannerContainer.removeAllViews();
            return;
        }

        if (bannerContainer.getChildCount() > 0) {
            bannerContainer.setVisibility(View.VISIBLE);
            return;
        }

        // Build the banner programmatically
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF201015);
        bg.setStroke(dp(1), 0xFF4A1520);
        banner.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(16), 0, dp(16), dp(16));
        banner.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("⚠  " + getString(R.string.crash_title));
        title.setTextColor(0xFFFF4444);
        title.setTextSize(14f);
        title.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda));
        title.setPadding(0, 0, 0, dp(8));
        banner.addView(title);

        TextView desc = new TextView(this);
        desc.setText(server.crashReason != null ? server.crashReason : getString(R.string.crash_reason_unknown, server.crashExitCode));
        desc.setTextColor(0xFFF0F0F0);
        desc.setTextSize(12f);
        desc.setPadding(0, 0, 0, dp(12));
        banner.addView(desc);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(android.view.Gravity.END);
        
        com.google.android.material.button.MaterialButton btnDetails = eu.kodanetwork.mchost.util.KodaButtons.dark(this, "DETAILS");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        btnParams.setMargins(0, 0, dp(8), 0);
        btnDetails.setLayoutParams(btnParams);
        btnDetails.setTextSize(11f);
        btnDetails.setOnClickListener(v -> {
            Intent alertIntent = new Intent(this, CrashAlertActivity.class);
            alertIntent.putExtra("id", server.getId());
            alertIntent.putExtra("name", server.getName());
            alertIntent.putExtra("crashReason", server.crashReason);
            alertIntent.putExtra("crashCategory", server.crashCategory);
            alertIntent.putExtra("crashFix", server.crashFix);
            alertIntent.putExtra("crashFixAction", server.crashFixAction);
            alertIntent.putExtra("crashStackTrace", server.crashStackTrace);
            alertIntent.putExtra("crashExitCode", server.crashExitCode);
            alertIntent.putExtra("serverType", server.getType() != null ? server.getType().name() : "PAPER");
            alertIntent.putExtra("serverVersion", server.getVersion());
            alertIntent.putExtra("serverRam", server.getRamMB());
            alertIntent.putExtra("serverPort", server.getPort());
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(alertIntent);
        });
        btnLayout.addView(btnDetails);

        if (server.crashFixAction != null) {
            com.google.android.material.button.MaterialButton btnFix = eu.kodanetwork.mchost.util.KodaButtons.primary(this, "FIX");
            btnFix.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            btnFix.setTextSize(11f);
            btnFix.setOnClickListener(v -> {
                eu.kodanetwork.mchost.util.CrashFixer.FixResult result = eu.kodanetwork.mchost.util.CrashFixer.executeFix(this, server, server.crashFixAction);
                if (result.success) {
                    Toast.makeText(this, getString(R.string.crash_fix_success, result.message), Toast.LENGTH_LONG).show();
                    bannerContainer.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, getString(R.string.crash_fix_failed, result.message), Toast.LENGTH_LONG).show();
                }
            });
            btnLayout.addView(btnFix);
        }
        
        banner.addView(btnLayout);
        bannerContainer.addView(banner);
        bannerContainer.setVisibility(View.VISIBLE);
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    // ── Console ───────────────────────────────────────────────────────────────

    private void setupConsole() {
        View btnSend  = findViewById(R.id.btn_send);
        View btnClear = findViewById(R.id.btn_clear);
        View btnCopy  = findViewById(R.id.btn_copy_log);
        boolean sleek = eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this);
        if (btnSend != null) {
            if (sleek) {
                // Custom sleek press feedback instead of the ripple click
                eu.kodanetwork.mchost.util.SleekTouch.apply(btnSend, this::sendCmd, 40);
            } else {
                btnSend.setOnClickListener(v -> sendCmd());
            }
        }
        if (btnClear != null) btnClear.setOnClickListener(v -> { if (tvLog != null) tvLog.setText(""); });
        if (btnCopy != null) btnCopy.setOnClickListener(v -> {
            if (tvLog == null) return;
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Server Log", tvLog.getText().toString());
                clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.sd_toast_log_copied), Toast.LENGTH_SHORT).show();
        });
        if (etCmd != null) etCmd.setOnEditorActionListener((v, id, e) -> {
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

        android.view.View btnModrinth = findViewById(R.id.btn_search_modrinth);
        if (btnModrinth != null) {
            btnModrinth.setOnClickListener(v -> showModrinthSearch());
        }
        
        android.view.View btnUpload = findViewById(R.id.btn_upload_resource_pack);
        if (btnUpload != null) {
            btnUpload.setOnClickListener(v -> {
                android.content.Intent w = new android.content.Intent(ServerDetailActivity.this, PraetorWarningActivity.class);
                w.putExtra("praetor_mode", "resource_pack_upload");
                startActivity(w);
            });
        }
        
        android.view.View btnRpClear = findViewById(R.id.btn_clear_resource_pack);
        if (btnRpClear != null) {
            btnRpClear.setOnClickListener(v -> {
                java.io.File propsFile = new java.io.File(server.getServerDir(), "server.properties");
                try {
                    java.util.List<String> linesProps = new java.util.ArrayList<>();
                    if (propsFile.exists()) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(propsFile))) {
                            String l; while ((l = br.readLine()) != null) {
                                if (!l.trim().startsWith("resource-pack=") && !l.trim().startsWith("resource-pack-sha1=")) {
                                    linesProps.add(l);
                                }
                            }
                        }
                    }
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(propsFile))) {
                        for (String l : linesProps) pw.println(l);
                    }
                    android.widget.TextView tvUrl = findViewById(R.id.tv_resource_pack_url);
                    if (tvUrl != null) tvUrl.setText("No resource pack active.");
                    android.widget.Toast.makeText(ServerDetailActivity.this, "Resource Pack cleared.", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            });
        }
        
        com.google.android.material.switchmaterial.SwitchMaterial swRequire = findViewById(R.id.switch_require_resource_pack);
        if (swRequire != null) {
            swRequire.setOnCheckedChangeListener((btnView, isChecked) -> {
                java.io.File propsFile = new java.io.File(server.getServerDir(), "server.properties");
                try {
                    java.util.List<String> linesProps = new java.util.ArrayList<>();
                    boolean found = false;
                    if (propsFile.exists()) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(propsFile))) {
                            String l; while ((l = br.readLine()) != null) {
                                if (l.trim().startsWith("require-resource-pack=")) {
                                    linesProps.add("require-resource-pack=" + isChecked);
                                    found = true;
                                } else {
                                    linesProps.add(l);
                                }
                            }
                        }
                    }
                    if (!found) linesProps.add("require-resource-pack=" + isChecked);
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(propsFile))) {
                        for (String l : linesProps) pw.println(l);
                    }
                } catch (Exception ignored) {}
            });
            // Init state
            java.io.File pF = new java.io.File(server.getServerDir(), "server.properties");
            if (pF.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(pF))) {
                    String l; while ((l = br.readLine()) != null) {
                        if (l.trim().startsWith("require-resource-pack=true")) swRequire.setChecked(true);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void sendCmd() {
        if (etCmd == null || etCmd.getText() == null) return;
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
        if (raw == null || raw.isEmpty() || tvLog == null) return;
        
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
            if (scrollLog != null) scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
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
            btn.setText(getString(R.string.sd_unlock_manual_editing));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFFFF4444);
            btn.setOnClickListener(v -> {
                Intent i = new Intent(this, PraetorWarningActivity.class);
                i.putExtra("target_folder_path", currentDir.getAbsolutePath());
                startActivityForResult(i, 9001); // 9001 = Praetor Override
            });
        } else {
            btn.setText(getString(R.string.sd_restore_auto_config));
            btn.setTextColor(0xFF000000);
            btn.setBackgroundColor(0xFF00E676);
            btn.setOnClickListener(v -> {
                overrideFile.delete();
                Toast.makeText(this, getString(R.string.sd_toast_auto_config_restored), Toast.LENGTH_SHORT).show();
                refreshFiles();
            });
        }
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 16, 16, 16);
        layoutFileList.addView(btn, lp);
        
        View div = new View(this);
        div.setBackgroundColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFFE5DECF : 0xFF2A241E);
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

        // Back android.widget.Button if not in root
        if (!currentDir.getAbsolutePath().equals(rootPath)) {
            addFRow(".. (Ordner hoch)", currentDir.getParentFile());
        }

        File[] files = currentDir.listFiles();
        if (files == null || files.length == 0) {
            if (currentDir.getAbsolutePath().equals(rootPath)) addFilesEmptyState();
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
    private File pendingExportFile;

    /** Animated empty state for the Files tab (Lottie cube + hint). */
    private void addFilesEmptyState() {
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, (int) (48 * d), 0, (int) (48 * d));

        int size = (int) (96 * d);
        com.airbnb.lottie.LottieAnimationView lav = new com.airbnb.lottie.LottieAnimationView(this);
        lav.setLayoutParams(new android.view.ViewGroup.LayoutParams(size, size));
        lav.setAnimation(R.raw.koda_empty);
        lav.loop(true);
        lav.playAnimation();
        box.addView(lav);

        TextView tv = new TextView(this);
        tv.setText(getString(R.string.sd_files_empty));
        tv.setTextColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFF8A8075 : 0xFF8A8A9A);
        tv.setTextSize(13);
        tv.setGravity(android.view.Gravity.CENTER);
        box.addView(tv);

        layoutFileList.addView(box);
    }

    private void addFRow(String text, File file) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(16, 24, 16, 24); // Taller rows for touch
        android.graphics.Typeface mono = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_koda_mono);
        if (mono != null) tv.setTypeface(mono);
        boolean light = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
        tv.setTextColor(text.contains("📁") || text.startsWith("..") ? 0xFFFF6B00 : (light ? 0xFF241207 : 0xFFE8E2D6));
        
        if (file != null) {
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDir = file;
                    refreshFiles();
                } else {
                    File overrideFile = getProtectedOverrideFile(file);
                    if (overrideFile != null && !overrideFile.exists()) {
                        Toast.makeText(this, getString(R.string.sd_toast_manual_locked), Toast.LENGTH_LONG).show();
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
                            .setTitle(getString(R.string.sd_dialog_paste_action))
                            .setPositiveButton(getString(R.string.sd_action_paste, clipFile.getName()), (d, w) -> {
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
                                } catch (Exception e) { 
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, getString(R.string.sd_toast_error_prefix) + getFriendlyErrorMsg(e.getMessage()), Toast.LENGTH_LONG).show();
                                    });
                                }
                            })
                            .setNegativeButton(getString(R.string.sd_action_cancel), null)
                            .show();
                    }
                    return true;
                }
                
                String[] actions = {getString(R.string.sd_dialog_rename), getString(R.string.sd_action_copy),
                        getString(R.string.sd_action_cut), getString(R.string.sd_action_delete), getString(R.string.sd_files_export)};
                new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setItems(actions, (d, which) -> {
                        if (which == 0) { // Umbenennen
                            final android.widget.EditText input = new android.widget.EditText(this);
                            input.setText(file.getName());
                            new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.sd_dialog_rename))
                                .setView(input)
                                .setPositiveButton(getString(R.string.sd_action_ok), (d2, w2) -> {
                                    file.renameTo(new File(file.getParent(), input.getText().toString()));
                                    refreshFiles();
                                })
                                .setNegativeButton(getString(R.string.sd_action_cancel), null).show();
                        } else if (which == 1) { // Kopieren
                            clipFile = file;
                            clipCut = false;
                            Toast.makeText(this, getString(R.string.sd_toast_copied_hint), Toast.LENGTH_LONG).show();
                        } else if (which == 2) { // Ausschneiden
                            clipFile = file;
                            clipCut = true;
                            Toast.makeText(this, getString(R.string.sd_toast_cut_hint), Toast.LENGTH_LONG).show();
                        } else if (which == 3) { // Löschen
                            if (file.isDirectory()) {
                                deleteRecursive(file);
                            } else {
                                file.delete();
                            }
                            refreshFiles();
                        } else if (which == 4 && file.isFile()) { // Export via SAF
                            pendingExportFile = file;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            intent.putExtra(Intent.EXTRA_TITLE, file.getName());
                            startActivityForResult(intent, REQ_EXPORT_FILE);
                        }
                    })
                    .show();
                return true;
            });
        }

        layoutFileList.addView(tv);
        View div = new View(this);
        div.setBackgroundColor(eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this) ? 0xFFE5DECF : 0xFF2A241E);
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

    /** Praetor-styled action picker for the files tab plus button. */
    private void showFilesActionDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_praetor_files_action);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView filesActionTitle = dialog.findViewById(R.id.tv_praetor_title);
        if (filesActionTitle != null) {
            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
            filesActionTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }

        dialog.findViewById(R.id.btn_files_new_file).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateEntryDialog(true);
        });
        dialog.findViewById(R.id.btn_files_new_folder).setOnClickListener(v -> {
            dialog.dismiss();
            showCreateEntryDialog(false);
        });
        dialog.findViewById(R.id.btn_files_import_files).setOnClickListener(v -> {
            dialog.dismiss();
            // SAF picker reaches every DocumentsProvider (USB, Drive,
            // Downloads, ...) — no chooser wrapper, it breaks the UI
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQ_IMPORT_FILE);
        });
        dialog.findViewById(R.id.btn_files_import_folder).setOnClickListener(v -> {
            dialog.dismiss();
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_IMPORT_FOLDER);
        });

        dialog.show();
    }

    /** Creates a new file (opened in the editor right away) or folder in the current files-tab directory. */
    private void showCreateEntryDialog(boolean isFile) {
        if (currentDir == null || !currentDir.exists()) return;
        String rel = currentDir.getAbsolutePath().replace(server.getServerDir(), "");
        if (rel.isEmpty()) rel = "/";

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_praetor_input);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        ((TextView) dialog.findViewById(R.id.tv_dialog_title)).setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        ((TextView) dialog.findViewById(R.id.tv_dialog_subtitle)).setText(getString(R.string.sd_files_action_title));
        ((TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(getString(
                isFile ? R.string.sd_files_create_file_msg : R.string.sd_files_create_folder_msg, rel));

        android.widget.EditText input = dialog.findViewById(R.id.et_dialog_input);
        input.setHint(isFile ? "config.yml" : "plugins/myfolder");
        ((android.widget.Button) dialog.findViewById(R.id.btn_dialog_confirm)).setText(getString(R.string.sd_files_create));

        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
        dialog.findViewById(R.id.btn_dialog_confirm).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || name.contains("..")) {
                android.widget.Toast.makeText(this, getString(R.string.sd_files_name_invalid), android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            File target = new File(currentDir, name);
            if (target.exists()) {
                android.widget.Toast.makeText(this, getString(R.string.sd_files_exists), android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            boolean ok = isFile ? false : target.mkdirs();
            if (isFile) {
                try { ok = target.createNewFile(); } catch (Exception e) { ok = false; }
            }
            if (!ok) {
                android.widget.Toast.makeText(this, getString(R.string.sd_files_create_failed, name), android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            refreshFiles();
            if (isFile) openFileEditor(target);
        });

        dialog.show();
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

    private void updateJavaRuntimeLabel(TextView tvJavaRuntime) {
        if (tvJavaRuntime == null) return;
        if (server.getJavaRuntime() == 0) {
            int auto = eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(server);
            tvJavaRuntime.setText(getString(R.string.java_runtime_auto, auto));
        } else {
            tvJavaRuntime.setText(getString(R.string.java_runtime_manual, server.getJavaRuntime()));
        }
    }

    private void showJavaRuntimePicker(TextView tvJavaRuntime) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.KodaBottomSheetDialog);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(0xFF0A0807);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.java_runtime_label));
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLetterSpacing(0.12f);
        tvTitle.setPadding(0, 0, 0, pad);
        container.addView(tvTitle);

        final int[] choices = {0, 8, 17, 21, 25};
        for (int choice : choices) {
            String label;
            if (choice == 0) {
                label = getString(R.string.java_runtime_auto, eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(server));
            } else {
                label = getString(R.string.java_runtime_manual, choice);
            }
            boolean selected = server.getJavaRuntime() == choice;
            MaterialButton btn = selected
                    ? eu.kodanetwork.mchost.util.KodaButtons.primary(this, label)
                    : eu.kodanetwork.mchost.util.KodaButtons.dark(this, label);
            
            if (selected) {
                android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) btn.getBackground();
                bg.setStroke((int)(2 * getResources().getDisplayMetrics().density), android.graphics.Color.WHITE);
                float[] hsv = new float[]{0, 1, 1};
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0, 360);
                anim.setDuration(2500);
                anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                anim.addUpdateListener(a -> {
                    hsv[0] = (float) a.getAnimatedValue();
                    bg.setStroke((int)(2 * getResources().getDisplayMetrics().density), android.graphics.Color.HSVToColor(hsv));
                    btn.invalidate();
                });
                anim.start();
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(52 * getResources().getDisplayMetrics().density));
            lp.bottomMargin = (int)(8 * getResources().getDisplayMetrics().density);
            final int sel = choice;
            btn.setOnClickListener(v -> {
                server.setJavaRuntime(sel);
                repo.update(server);
                updateJavaRuntimeLabel(tvJavaRuntime);
                sheet.dismiss();
                applyJavaRuntimeChange();
            });
            container.addView(btn, lp);
        }
        sheet.setContentView(container);
        eu.kodanetwork.mchost.util.SheetFix.apply(sheet);
        sheet.show();
    }

    /** Applies a runtime change: restarts a running server, pre-downloads the runtime otherwise. */
    private void applyJavaRuntimeChange() {
        int version = server.getJavaRuntime() != 0
                ? server.getJavaRuntime()
                : eu.kodanetwork.mchost.util.RuntimeManager.resolveAutoVersion(server);
        boolean running = server.state == ServerInstance.State.ONLINE
                || server.state == ServerInstance.State.STARTING
                || server.state == ServerInstance.State.RESTARTING;
        if (running) {
            Toast.makeText(this, getString(R.string.java_runtime_restarting, version), Toast.LENGTH_SHORT).show();
            Intent kill = new Intent(this, KodaServerService.class);
            kill.setAction(KodaServerService.ACTION_KILL);
            kill.putExtra(KodaServerService.EXTRA_ID, server.getId());
            startService(kill);
            new Thread(() -> {
                int retries = 0;
                ServerInstance s = server;
                while (retries < 30) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    s = repo.byId(server.getId());
                    if (s == null) return;
                    if (s.state == ServerInstance.State.OFFLINE || s.state == ServerInstance.State.CRASHED) break;
                    retries++;
                }
                Intent start = new Intent(ServerDetailActivity.this, KodaServerService.class);
                start.setAction(KodaServerService.ACTION_START);
                start.putExtra("id", server.getId());
                startService(start);
                
                runOnUiThread(() -> {
                    updateDash();
                    Toast.makeText(ServerDetailActivity.this, getString(R.string.java_runtime_updated,
                            getString(R.string.java_runtime_manual, version)), Toast.LENGTH_SHORT).show();
                });
            }).start();
        } else if (version != 25 && !eu.kodanetwork.mchost.util.RuntimeManager.isRuntimeInstalled(this, version)) {
            Intent w = new Intent(this, eu.kodanetwork.mchost.ui.DownloadJreActivity.class);
            w.putExtra("VERSION", version);
            startActivity(w);
            updateDash();
        } else {
            Toast.makeText(this, getString(R.string.java_runtime_updated,
                    getString(R.string.java_runtime_manual, version)), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSettings() {
        String addressLabel = server.isDatabase() ? "Verbindungs-Adresse:" : "Beitritts-Adresse:";
        String addressValue = server.isDatabase() ? "127.0.0.1 (Lokal)" : server.getJoinAddress();

        String modpackLine = server.getModpackName().isEmpty()
                ? ""
                : "Modpack:   " + server.getModpackName() + "\n";

        tvSettingsInfo.setText(
            "Name:       " + server.getName() + "\n" +
            "Typ:        " + server.getType().name() + " " + server.getVersion() + "\n" +
            modpackLine +
            "RAM:        " + server.getRamMB() + " MB\n" +
            "Port:       " + server.getPort() + "\n" +
            addressLabel + "\n" + addressValue + "\n\n" +
            "Dateipfad:\n" + server.getServerDir()
        );

        // Java runtime selector (per server; Auto = Fabric 21 / sonst 25)
        View javaRuntimeRow = findViewById(R.id.layout_java_runtime);
        TextView tvJavaRuntime = findViewById(R.id.tv_java_runtime);
        if (javaRuntimeRow != null && tvJavaRuntime != null) {
            updateJavaRuntimeLabel(tvJavaRuntime);
            javaRuntimeRow.setOnClickListener(v -> showJavaRuntimePicker(tvJavaRuntime));
        }

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
                    Toast.makeText(this, getString(R.string.sd_toast_icon_removed), Toast.LENGTH_SHORT).show();
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



        android.widget.CompoundButton swBedrock = findViewById(R.id.switch_bedrock);
        View rowBedrock = findViewById(R.id.row_bedrock);
        if (swBedrock != null) {
            swBedrock.setChecked(server.isBedrockSupport());
            swBedrock.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) {
                    swBedrock.setChecked(false); // Revert UI visually
                    return;
                }
                boolean isChecked = swBedrock.isChecked();
                if (isChecked) {
                    if (server.getBedrockPort() > 0) {
                        server.setBedrockSupport(true);
                        repo.update(server);
                        
                        View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
                        if (layoutBedrockPort != null && tvBedrockPortDash != null) {
                            layoutBedrockPort.setVisibility(View.VISIBLE);
                            tvBedrockPortDash.setText(String.valueOf(server.getBedrockPort()));
                        }
                        triggerAddonRestart();
                    } else {
                        // Allocate port
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_allocating_proxy), android.widget.Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            try {
                                int allocatedPort = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).allocatePort("", server.getSubdomain(), "bedrock");
                                runOnUiThread(() -> {
                                    server.setBedrockPort(allocatedPort);
                                    server.setBedrockSupport(true);
                                    repo.update(server);
                                    
                                    View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
                                    if (layoutBedrockPort != null && tvBedrockPortDash != null) {
                                        layoutBedrockPort.setVisibility(View.VISIBLE);
                                        tvBedrockPortDash.setText(String.valueOf(server.getBedrockPort()));
                                    }
                                    triggerAddonRestart();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    swBedrock.setChecked(false);
                                    if (e.getMessage() != null && e.getMessage().contains("ports_exhausted")) {
                                        eu.kodanetwork.mchost.ui.components.PraetorDialog.showApology(ServerDetailActivity.this, "P.R.A.E.T.O.R.", "Alle Bedrock Proxy-Ports sind derzeit belegt. Bitte versuche es später erneut.");
                                    } else {
                                        android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_error_prefix) + getFriendlyErrorMsg(e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }).start();
                    }
                } else {
                    swBedrock.setChecked(true); // REVERT visually!
                    showAddonWarningDialog("bedrock", swBedrock);
                }
            });
        }
        if (rowBedrock != null) {
            rowBedrock.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
                showAddonOptions("bedrock", swBedrock);
            });
        }

        android.widget.CompoundButton swVoicechat = findViewById(R.id.switch_voicechat);
        View rowVoicechat = findViewById(R.id.row_voicechat);
        if (swVoicechat != null) {
            swVoicechat.setChecked(server.isVoicechat());
            swVoicechat.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) {
                    swVoicechat.setChecked(false); // Revert UI visually
                    return;
                }
                boolean isChecked = swVoicechat.isChecked();
                if (isChecked) {
                    if (server.getVoicechatPort() > 0) {
                        server.setVoicechat(true);
                        repo.update(server);
                        triggerAddonRestart();
                    } else {
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_allocating_voice), android.widget.Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            try {
                                int allocatedPort = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).allocatePort("", server.getSubdomain(), "voicechat");
                                runOnUiThread(() -> {
                                    server.setVoicechatPort(allocatedPort);
                                    server.setVoicechat(true);
                                    repo.update(server);
                                    triggerAddonRestart();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    swVoicechat.setChecked(false);
                                    if (e.getMessage() != null && e.getMessage().contains("ports_exhausted")) {
                                        eu.kodanetwork.mchost.ui.components.PraetorDialog.showApology(ServerDetailActivity.this, "P.R.A.E.T.O.R.", "Alle Voicechat Proxy-Ports sind derzeit belegt. Bitte versuche es später erneut.");
                                    } else {
                                        android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_error_prefix) + getFriendlyErrorMsg(e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }).start();
                    }
                } else {
                    swVoicechat.setChecked(true); // REVERT visually!
                    showAddonWarningDialog("voicechat", swVoicechat);
                }
            });
        }
        if (rowVoicechat != null) {
            rowVoicechat.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
                showAddonOptions("voicechat", swVoicechat);
            });
        }

        // KodaDash Web Control Addon (Restricted to darkpocky3@gmail.com and PaperMC/Purpur)
        String accountEmail = eu.kodanetwork.mchost.App.getPrefs(this).getString("account_email", "");
        boolean isPaperOrPurpur = server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR;
        
        android.widget.CompoundButton swKodadash = findViewById(R.id.switch_kodadash);
        View rowKodadash = findViewById(R.id.row_kodadash);
        View dividerKodadash = findViewById(R.id.divider_kodadash);

        if ("darkpocky3@gmail.com".equalsIgnoreCase(accountEmail) && isPaperOrPurpur) {
            if (rowKodadash != null) rowKodadash.setVisibility(View.VISIBLE);
            if (dividerKodadash != null) dividerKodadash.setVisibility(View.VISIBLE);

            if (swKodadash != null) {
                swKodadash.setChecked(server.isKodadashSupport());
                swKodadash.setOnClickListener(v -> {
                    if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) {
                        swKodadash.setChecked(false);
                        return;
                    }
                    boolean isChecked = swKodadash.isChecked();
                    if (isChecked) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("KodaDash Beta Warning")
                            .setMessage("KodaDash Web Control Panel is a Beta feature. It uses ~50MB extra RAM and might be unstable. Are you sure you want to enable it?")
                            .setPositiveButton("Enable", (dialog, which) -> {
                                if (server.getKodadashPort() > 0) {
                                    server.setKodadashSupport(true);
                                    repo.update(server);
                                    
                                    // Patch Supabase with the existing port
                                    new Thread(() -> {
                                        try {
                                            String patchPayload = "{\"kodadash_port\": " + server.getKodadashPort() + "}";
                                            String appUuid = eu.kodanetwork.mchost.App.getPrefs(ServerDetailActivity.this).getString("app_uuid", "");
                                            String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + server.getSubdomain() + "\", \"p_payload\": " + patchPayload + "}";
                                            okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                                            okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                                .url(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server")
                                                .post(body)
                                                .addHeader("Content-Type", "application/json")
                                                .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                                .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                                .build();
                                            new okhttp3.OkHttpClient().newCall(patchReq).execute().close();
                                        } catch (Exception ignored) {}
                                    }).start();

                                    triggerAddonRestart();
                                } else {
                                    android.widget.Toast.makeText(this, "Allocating KodaDash Proxy Port...", android.widget.Toast.LENGTH_SHORT).show();
                                    new Thread(() -> {
                                        try {
                                            int allocatedPort = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).allocatePort("", server.getSubdomain(), "kodadash");
                                            runOnUiThread(() -> {
                                                server.setKodadashPort(allocatedPort);
                                                server.setKodadashSupport(true);
                                                repo.update(server);
                                                
                                                // Patch Supabase with the new port
                                                new Thread(() -> {
                                                    try {
                                                        String patchPayload = "{\"kodadash_port\": " + allocatedPort + "}";
                                                        String appUuid = eu.kodanetwork.mchost.App.getPrefs(ServerDetailActivity.this).getString("app_uuid", "");
                                                        String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + server.getSubdomain() + "\", \"p_payload\": " + patchPayload + "}";
                                                        okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                                                        okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                                            .url(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server")
                                                            .post(body)
                                                            .addHeader("Content-Type", "application/json")
                                                            .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                                            .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                                            .build();
                                                        new okhttp3.OkHttpClient().newCall(patchReq).execute().close();
                                                    } catch (Exception ignored) {}
                                                }).start();
                                                triggerAddonRestart();
                                            });
                                        } catch (Exception e) {
                                            runOnUiThread(() -> {
                                                swKodadash.setChecked(false);
                                                android.widget.Toast.makeText(ServerDetailActivity.this, "Port Allocation Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }).start();
                                }
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                swKodadash.setChecked(false);
                            })
                            .setOnCancelListener(dialog -> swKodadash.setChecked(false))
                            .show();
                    } else {
                        server.setKodadashSupport(false);
                        repo.update(server);

                        // Patch Supabase to remove the port
                        new Thread(() -> {
                            try {
                                String patchPayload = "{\"kodadash_port\": null}";
                                String appUuid = eu.kodanetwork.mchost.App.getPrefs(ServerDetailActivity.this).getString("app_uuid", "");
                                String rpcJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + server.getSubdomain() + "\", \"p_payload\": " + patchPayload + "}";
                                okhttp3.RequestBody body = okhttp3.RequestBody.create(rpcJson, okhttp3.MediaType.parse("application/json"));
                                okhttp3.Request patchReq = new okhttp3.Request.Builder()
                                    .url(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server")
                                    .post(body)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                    .addHeader("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey())
                                    .build();
                                new okhttp3.OkHttpClient().newCall(patchReq).execute().close();
                            } catch (Exception ignored) {}
                        }).start();

                        triggerAddonRestart();
                    }
                });
            }
        }

        setupGameplaySettings();

        android.view.View btnExportZip = findViewById(R.id.btn_export_zip);
        if (btnExportZip != null) {
            btnExportZip.setOnClickListener(v -> exportZip());
        }
        
        android.widget.TextView btnHibernate = findViewById(R.id.btn_hibernate);
        if (btnHibernate != null) {
            btnHibernate.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this)) return;
                if (server.state == ServerInstance.State.HIBERNATED) {
                    new Thread(() -> {
                        try {
                            runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_waking_up), android.widget.Toast.LENGTH_SHORT).show());
                            eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo);
                            runOnUiThread(() -> {
                                updateDash();
                                android.widget.Toast.makeText(this, getString(R.string.sd_toast_server_woken), android.widget.Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            if ("DNS_OCCUPIED".equals(e.getMessage())) {
                                runOnUiThread(() -> handleDnsOccupied());
                            } else {
                                runOnUiThread(() -> android.widget.Toast.makeText(this, getString(R.string.sd_toast_wakeup_error) + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                            }
                        }
                    }).start();
                } else {
                    if (server.isRunning()) {
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_stop_server_first), android.widget.Toast.LENGTH_SHORT).show();
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
                        runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_waking_up), android.widget.Toast.LENGTH_SHORT).show());
                        eu.kodanetwork.mchost.utils.HibernationManager.wakeUpServer(this, server, repo);
                        runOnUiThread(() -> {
                            updateDash();
                            android.widget.Toast.makeText(this, getString(R.string.sd_toast_server_woken), android.widget.Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        if ("DNS_OCCUPIED".equals(e.getMessage())) {
                            runOnUiThread(() -> handleDnsOccupied());
                        } else {
                            runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_wakeup_error) + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                        }
                    }
                }).start();
            });
        }

        btnDelServer.setOnClickListener(v -> {
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_praetor_delete);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
            dialog.setCancelable(false);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
            android.widget.Button btnPos = dialog.findViewById(R.id.btn_dialog_delete);
            android.widget.Button btnNeg = dialog.findViewById(R.id.btn_dialog_cancel);

            if (tvTitle != null) {
                String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
                tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
            }

            btnPos.setEnabled(false);
            btnPos.setEnabled(false);

            android.os.CountDownTimer timer = new android.os.CountDownTimer(10000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    btnPos.setText(String.format(getString(R.string.sd_delete_btn_waiting), (millisUntilFinished / 1000)));
                }

                @Override
                public void onFinish() {
                    btnPos.setText(getString(R.string.sd_delete_btn_ready));
                    btnPos.setEnabled(true);
                }
            };
            timer.start();

            btnNeg.setOnClickListener(v2 -> {
                timer.cancel();
                dialog.dismiss();
            });

            btnPos.setOnClickListener(v2 -> {
                btnPos.setEnabled(false);
                btnPos.setText("...");
                new Thread(() -> {
                    boolean hasNet = eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(ServerDetailActivity.this);
                    runOnUiThread(() -> {
                        if (hasNet) {
                            dialog.dismiss();
                            Intent intent = new Intent(ServerDetailActivity.this, DeleteServerActivity.class);
                            intent.putExtra("SERVER_ID", server.getId());
                            startActivity(intent);
                        } else {
                            btnPos.setEnabled(true);
                            btnPos.setText(getString(R.string.sd_delete_btn_ready));
                            android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_no_internet), android.widget.Toast.LENGTH_SHORT).show();
                        }
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
        tvTermuxStatus.setText(getString(R.string.sd_native_mode_active));
        if (server.getPlayitAddress().isEmpty()) {
            tvTunnelStatus.setText(getString(R.string.sd_tunnel_not_assigned));
        } else {
            tvTunnelStatus.setText("Tunnel: " + server.getPlayitAddress());
        }
        if (server.getDomainLink().isEmpty()) {
            tvDomainStatus.setText(getString(R.string.sd_domain_not_linked));
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
                View v = getLayoutInflater().inflate(R.layout.item_modrinth_project, parent, false);;
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(v.getContext(), v);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                eu.kodanetwork.mchost.util.ModrinthHelper.ModrinthProject p = pluginList.get(position);
                
                android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(ServerDetailActivity.this);
                boolean lightMode = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(ServerDetailActivity.this);
                boolean isCyber = prefs.getBoolean("dev_cyber", false);
                int themeColor = prefs.getInt("app_theme_color", 0xFFFF6B00);
                eu.kodanetwork.mchost.util.ThemeHelper.applyToView(holder.itemView, lightMode, themeColor, isCyber);

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
                            
                            View headerRestart = findViewById(R.id.layout_header_restart);
                            android.view.View btnRestart = findViewById(R.id.btn_header_restart);
                            
                            if (headerRestart != null && btnRestart != null) {
                                headerRestart.setVisibility(View.VISIBLE);
                                btnRestart.setOnClickListener(v2 -> {
                                    triggerAddonRestart();
                                    headerRestart.setVisibility(View.GONE);
                                });
                                
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    if (headerRestart.getVisibility() == View.VISIBLE) {
                                        headerRestart.setVisibility(View.GONE);
                                    }
                                }, 5000);
                            }
                            android.widget.Toast.makeText(ServerDetailActivity.this, "✓ " + file.getName() + getString(R.string.sd_toast_installed_suffix), android.widget.Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String err) {
                            pbDl.setVisibility(View.GONE);
                            btnDl.setVisibility(View.VISIBLE);
                            android.widget.Toast.makeText(ServerDetailActivity.this, "✗ " + err, android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
                });
                
                holder.itemView.setOnClickListener(v -> {
                    // Show a loading dialog instead of pbDl since pbDl is for the download android.widget.Button
                    android.app.ProgressDialog pd = new android.app.ProgressDialog(ServerDetailActivity.this);
                    pd.setMessage(getString(R.string.sd_dialog_loading_versions));
                    pd.setCancelable(false);
                    pd.show();
                    
                    new Thread(() -> {
                        try {
                            String loader = server.getType().name().toLowerCase();
                            if (server.getType() == ServerInstance.Type.PURPUR) loader = "paper";
                            String mcVer = server.getVersion();
                            java.util.List<String> compLoaders = new java.util.ArrayList<>();
                            if (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) {
                                compLoaders.add("paper");
                                compLoaders.add("spigot");
                                compLoaders.add("bukkit");
                            } else {
                                compLoaders.add(loader);
                            }
                            
                            org.json.JSONArray versions = null;
                            for (String l : compLoaders) {
                                String qL = java.net.URLEncoder.encode("[\"" + l + "\"]", "UTF-8");
                                String qG = java.net.URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                                String u = "https://api.modrinth.com/v2/project/" + p.id + "/version?loaders=" + qL + "&game_versions=" + qG;
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                                conn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                                if (conn.getResponseCode() == 200) {
                                    java.io.InputStream is = conn.getInputStream();
                                    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                                    versions = new org.json.JSONArray(s.hasNext() ? s.next() : "");
                                    is.close();
                                    if (versions.length() > 0) break;
                                }
                            }
                            
                            if (versions == null || versions.length() == 0) {
                                runOnUiThread(() -> {
                                    pd.dismiss();
                                    android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_no_compatible_version), android.widget.Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            
                            java.util.List<String> names = new java.util.ArrayList<>();
                            java.util.List<org.json.JSONObject> verObjs = new java.util.ArrayList<>();
                            for (int i = 0; i < versions.length(); i++) {
                                org.json.JSONObject ver = versions.getJSONObject(i);
                                names.add(ver.getString("name") + " (" + ver.getString("version_number") + ")");
                                verObjs.add(ver);
                            }
                            
                            runOnUiThread(() -> {
                                pd.dismiss();
                                
                                com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(ServerDetailActivity.this, R.style.KodaBottomSheetDialog);
                                setupWindowDecor(sheet.getWindow());
                                android.widget.LinearLayout container = new android.widget.LinearLayout(ServerDetailActivity.this);
                                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                                container.setBackgroundColor(0xFF0E0E14);
                                container.setPadding(0, 0, 0, 48);

                                TextView sheetTitle = new TextView(ServerDetailActivity.this);
                                sheetTitle.setText(getString(R.string.select_version));
                                sheetTitle.setTextColor(0xFFFF6B00);
                                sheetTitle.setTextSize(13);
                                sheetTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                                sheetTitle.setLetterSpacing(0.12f);
                                sheetTitle.setPadding(48, 40, 48, 24);
                                container.addView(sheetTitle);

                                View div = new View(ServerDetailActivity.this);
                                div.setBackgroundColor(0xFF222230);
                                div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                                container.addView(div);
                                
                                android.widget.ScrollView sv = new android.widget.ScrollView(ServerDetailActivity.this);
                                android.widget.LinearLayout listContainer = new android.widget.LinearLayout(ServerDetailActivity.this);
                                listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
                                sv.addView(listContainer);
                                container.addView(sv);
                                
                                for (int i = 0; i < names.size(); i++) {
                                    final int which = i;
                                    String name = names.get(i);
                                    
                                    android.widget.LinearLayout row = new android.widget.LinearLayout(ServerDetailActivity.this);
                                    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                                    row.setPadding(48, 32, 48, 32);
                                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                    row.setBackgroundResource(android.R.drawable.list_selector_background);
                                    row.setClickable(true);
                                    
                                    TextView tvName = new TextView(ServerDetailActivity.this);
                                    tvName.setText(name);
                                    tvName.setTextColor(0xFFEEEEEE);
                                    tvName.setTextSize(14);
                                    android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                                    row.addView(tvName, lp);
                                    
                                    TextView btn = new TextView(ServerDetailActivity.this);
                                    btn.setText(R.string.select_version);
                                    btn.setTextColor(0xFFFF6B00);
                                    btn.setTextSize(14);
                                    btn.setPadding(48, 8, 0, 8);
                                    android.util.TypedValue outValue = new android.util.TypedValue();
                                    getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
                                    btn.setBackgroundResource(outValue.resourceId);
                                    row.addView(btn);
                                    
                                    row.setOnClickListener(vRow -> {
                                        sheet.dismiss();
                                        org.json.JSONObject selectedVer = verObjs.get(which);
                                        btnDl.setVisibility(View.GONE);
                                        pbDl.setVisibility(View.VISIBLE);
                                        
                                        new Thread(() -> {
                                            try {
                                                org.json.JSONArray files = selectedVer.getJSONArray("files");
                                                org.json.JSONObject fileObj = files.getJSONObject(0);
                                                for (int j = 0; j < files.length(); j++) {
                                                    if (files.getJSONObject(j).optBoolean("primary", false)) {
                                                        fileObj = files.getJSONObject(j); break;
                                                    }
                                                }
                                                
                                                String dlUrl = fileObj.getString("url");
                                                String fName = fileObj.getString("filename");
                                                
                                                java.io.File targetDir = new java.io.File(server.getServerDir(), (server.getType() == ServerInstance.Type.PAPER || server.getType() == ServerInstance.Type.PURPUR) ? "plugins" : "mods");
                                                targetDir.mkdirs();
                                                java.io.File targetFile = new java.io.File(targetDir, fName);
                                                
                                                String oldVersionId = server.pluginVersions.get(p.id);
                                                eu.kodanetwork.mchost.util.ModrinthHelper.deleteOldVersion(oldVersionId, targetDir, fName);
                                                
                                                java.net.HttpURLConnection dlConn = (java.net.HttpURLConnection) new java.net.URL(dlUrl).openConnection();
                                                dlConn.setRequestProperty("User-Agent", "KodaNetwork/3.0");
                                                dlConn.setInstanceFollowRedirects(true);
                                                
                                                if (dlConn.getResponseCode() < 300) {
                                                    java.io.InputStream dlIs = dlConn.getInputStream();
                                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
                                                    byte[] buf = new byte[8192]; int r;
                                                    while ((r = dlIs.read(buf)) != -1) fos.write(buf, 0, r);
                                                    fos.close(); dlIs.close();
                                                    
                                                    server.pluginVersions.put(p.id, selectedVer.getString("id"));
                                                    ServerRepo.get(ServerDetailActivity.this).update(server);
                                                    
                                                    runOnUiThread(() -> {
                                                        pbDl.setVisibility(View.GONE);
                                                        btnDl.setVisibility(View.VISIBLE);
                                                        if (server.state != ServerInstance.State.OFFLINE) {
                                                            View headerRestart = findViewById(R.id.layout_header_restart);
                                                            android.view.View btnRestartObj = findViewById(R.id.btn_header_restart);
                                                            if (headerRestart != null && btnRestartObj != null) {
                                                                headerRestart.setVisibility(View.VISIBLE);
                                                                btnRestartObj.setOnClickListener(v2 -> {
                                                                    triggerAddonRestart();
                                                                    headerRestart.setVisibility(View.GONE);
                                                                });
                                                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                                                    if (headerRestart.getVisibility() == View.VISIBLE) headerRestart.setVisibility(View.GONE);
                                                                }, 5000);
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    runOnUiThread(() -> {
                                                        pbDl.setVisibility(View.GONE); btnDl.setVisibility(View.VISIBLE);
                                                        android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_download_error), android.widget.Toast.LENGTH_SHORT).show();
                                                    });
                                                }
                                            } catch (Exception e) {
                                                runOnUiThread(() -> {
                                                    pbDl.setVisibility(View.GONE); btnDl.setVisibility(View.VISIBLE);
                                                    android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_error_prefix) + getFriendlyErrorMsg(e.getMessage()), android.widget.Toast.LENGTH_SHORT).show();
                                                    View headerRestart = findViewById(R.id.layout_header_restart);
                                                    if (headerRestart != null) {
                                                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                                            if (headerRestart.getVisibility() == View.VISIBLE) {
                                                                headerRestart.setVisibility(View.GONE);
                                                            }
                                                        }, 5000);
                                                    }
                                                });
                                            }
                                        }).start();
                                    });
                                    
                                    listContainer.addView(row);
                                }
                                
                                sheet.setContentView(container);
                                eu.kodanetwork.mchost.util.SheetFix.apply(sheet);
                                sheet.show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                pd.dismiss();
                                android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_loading_error), android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
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
                    Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_search_error) + err, Toast.LENGTH_LONG).show();
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
    
    private String getFriendlyErrorMsg(String msg) {
        if (msg == null) return getString(R.string.error_unknown);
        if (msg.contains("Connection refused")) return getString(R.string.error_connection_refused);
        if (msg.contains("timeout")) return getString(R.string.error_timeout);
        if (msg.contains("ports_exhausted")) return getString(R.string.error_ports_exhausted);
        if (msg.contains("DNS_OCCUPIED")) return getString(R.string.error_dns_occupied);
        if (msg.contains("profanity_detected")) return getString(R.string.error_profanity_detected);
        if (msg.contains("invalid_characters")) return getString(R.string.error_invalid_characters);
        if (msg.contains("length_invalid")) return getString(R.string.error_length_invalid);
        return msg;
    }

    private void startTunnel() {
        io.execute(() -> {
            PlayitManager manager = new PlayitManager(this);
            Process started = manager.startTunnelNative(server.getId(), "");
            if (started == null) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.sd_toast_playit_error), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> tvTunnelStatus.setText(getString(R.string.sd_tunnel_starting)));
            try {
                Thread.sleep(3000);
                String addr = manager.readAssignedAddress(server.getId());
                runOnUiThread(() -> {
                    if (addr != null && !addr.isEmpty()) {
                        server.setPlayitAddress(addr);
                        repo.update(server);
                        tvTunnelStatus.setText("Tunnel: " + addr);
                    } else {
                        tvTunnelStatus.setText(getString(R.string.sd_tunnel_running_pending));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvTunnelStatus.setText("Tunnel error: " + e.getMessage()));
            }
            repo.update(server);
        });
        
        // Add TiltEffect to controls if LiquidGlass
        if (eu.kodanetwork.mchost.util.ThemeHelper.isLiquidGlass(this)) {
            android.view.View[] controlsToTilt = new android.view.View[] {
                btnStart, btnStop, btnRestart, btnKill, 
                btnDlJar, btnTermuxSetup, btnStartTunnel, btnLinkDomain
            };
            for (android.view.View v : controlsToTilt) {
                if (v != null) {
                    TiltEffectHelper helper = new TiltEffectHelper(this, v, true);
                    tiltHelpers.add(helper);
                    helper.register(); // Ensure it starts working right away
                }
            }
        }
    }

    private void handleDomainLink() {
        if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;

        long lastChange = eu.kodanetwork.mchost.App.getPrefs(this).getLong("last_join_change_" + server.getId(), 0);
        if (System.currentTimeMillis() - lastChange < 24 * 60 * 60 * 1000L) {
            Toast.makeText(this, getString(R.string.sd_toast_subdomain_limit), Toast.LENGTH_LONG).show();
            return;
        }

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_join_address);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_title)).setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_subtitle)).setText(R.string.praetor_subtitle);
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(R.string.praetor_message_change_domain);

        android.widget.EditText input = dialog.findViewById(R.id.et_dialog_input);
        if (server.getSubdomain() != null) input.setText(server.getSubdomain());

        // --- Base domain toggle (same sliding-pill style as the create screen) ---
        final String[] selectedDomain = {server.getBaseDomain()};
        android.widget.TextView btnKn = dialog.findViewById(R.id.btn_join_kodanetwork);
        android.widget.TextView btnKs = dialog.findViewById(R.id.btn_join_kodaserv);
        android.view.View wrapKn = dialog.findViewById(R.id.wrapper_join_kodanetwork);
        android.view.View wrapKs = dialog.findViewById(R.id.wrapper_join_kodaserv);
        android.view.View pill = dialog.findViewById(R.id.pill_join_domain);
        android.widget.TextView suffix = dialog.findViewById(R.id.tv_dialog_suffix);

        final Runnable[] updateDomainUi = {null};
        updateDomainUi[0] = () -> {
            boolean kn = "kodanetwork.eu".equals(selectedDomain[0]);
            btnKn.setTextColor(kn ? 0xFFFFFFFF : 0xFF888899);
            btnKs.setTextColor(kn ? 0xFF888899 : 0xFFFFFFFF);
            suffix.setText("." + selectedDomain[0]);
            android.view.View activeWrapper = kn ? wrapKn : wrapKs;
            activeWrapper.post(() -> {
                pill.animate()
                        .translationX(activeWrapper.getX())
                        .setDuration(200)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                android.view.ViewGroup.LayoutParams params = pill.getLayoutParams();
                params.width = activeWrapper.getWidth();
                pill.requestLayout();
            });
        };
        android.view.View.OnClickListener domainListener = v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            selectedDomain[0] = v.getId() == R.id.wrapper_join_kodaserv || v.getId() == R.id.btn_join_kodaserv
                    ? "kodaserv.eu" : "kodanetwork.eu";
            updateDomainUi[0].run();
        };
        btnKn.setOnClickListener(domainListener);
        btnKs.setOnClickListener(domainListener);
        wrapKn.setOnClickListener(domainListener);
        wrapKs.setOnClickListener(domainListener);
        // Initial pill placement after layout
        wrapKn.post(() -> {
            android.view.View activeWrapper = "kodanetwork.eu".equals(selectedDomain[0]) ? wrapKn : wrapKs;
            pill.setTranslationX(activeWrapper.getX());
            android.view.ViewGroup.LayoutParams params = pill.getLayoutParams();
            params.width = activeWrapper.getWidth();
            pill.requestLayout();
        });

        // --- Step views ---
        final android.widget.TextView[] stepIcons = {
                dialog.findViewById(R.id.step_icon_1), dialog.findViewById(R.id.step_icon_2),
                dialog.findViewById(R.id.step_icon_3), dialog.findViewById(R.id.step_icon_4)};
        final android.widget.TextView[] stepTexts = {
                dialog.findViewById(R.id.step_text_1), dialog.findViewById(R.id.step_text_2),
                dialog.findViewById(R.id.step_text_3), dialog.findViewById(R.id.step_text_4)};
        final android.view.View inputPhase = dialog.findViewById(R.id.layout_join_input_phase);
        final android.view.View stepsPhase = dialog.findViewById(R.id.layout_join_steps_phase);
        final com.google.android.material.button.MaterialButton btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);
        final com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);

        final int ST_ACTIVE = 0, ST_OK = 1, ST_FAIL = 2, ST_PENDING = 3;
        //noinspection Convert2Lambda
        final java.util.function.BiConsumer<Integer, Integer>[] setStep = new java.util.function.BiConsumer[1];
        setStep[0] = (idx, state) -> runOnUiThread(() -> {
            android.widget.TextView icon = stepIcons[idx];
            android.widget.TextView text = stepTexts[idx];
            if (state == ST_ACTIVE) {
                icon.setText("•"); icon.setTextColor(0xFFFF6B00);
                text.setTextColor(0xFFF0F0F0);
            } else if (state == ST_OK) {
                icon.setText("✓"); icon.setTextColor(0xFF00E676);
                text.setTextColor(0xFFF0F0F0);
                icon.setScaleX(0.3f); icon.setScaleY(0.3f);
                icon.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
            } else if (state == ST_FAIL) {
                icon.setText("✗"); icon.setTextColor(0xFFFF3344);
                text.setTextColor(0xFFFF3344);
            } else {
                icon.setText("•"); icon.setTextColor(0xFF555566);
                text.setTextColor(0xFF555566);
            }
        });

        stepTexts[0].setText(getString(R.string.join_step_dns_delete));
        stepTexts[1].setText(getString(R.string.join_step_dns_create));
        stepTexts[2].setText(getString(R.string.join_step_db));
        stepTexts[3].setText(getString(R.string.join_step_done));
        for (int i = 1; i < 4; i++) setStep[0].accept(i, ST_PENDING);

        dialog.setCancelable(true);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String newSubdomain = input.getText().toString().trim().toLowerCase();
            if (!newSubdomain.matches("^[a-z0-9-]+$") || newSubdomain.length() < 3) {
                Toast.makeText(this, getString(R.string.sd_toast_invalid_address_length), Toast.LENGTH_SHORT).show();
                return;
            }
            if (newSubdomain.equals(server.getSubdomain()) && selectedDomain[0].equals(server.getBaseDomain())) {
                Toast.makeText(this, getString(R.string.sd_toast_invalid_address), Toast.LENGTH_SHORT).show();
                return;
            }
            String newBaseDomain = selectedDomain[0];
            // Switch to the steps phase with a small transition
            inputPhase.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                inputPhase.setVisibility(android.view.View.GONE);
                stepsPhase.setAlpha(0f);
                stepsPhase.setVisibility(android.view.View.VISIBLE);
                stepsPhase.animate().alpha(1f).setDuration(200).start();
            }).start();
            btnConfirm.setVisibility(android.view.View.GONE);
            btnCancel.setEnabled(false);

            io.execute(() -> {
                String oldSubdomain = server.getSubdomain();
                String oldBaseDomain = server.getBaseDomain();
                boolean oldDnsDeleted = false;
                boolean newDnsCreated = false;
                String target = null;
                int port = server.getPort();
                if (server.getPlayitAddress() != null && !server.getPlayitAddress().isEmpty()) {
                    String[] parts = server.getPlayitAddress().split(":");
                    target = parts[0];
                    if (parts.length > 1) {
                        try { port = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
                    }
                }
                eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient client =
                        new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this);
                final String finalTarget = target;
                try {
                    // Step 1: delete old DNS (old zone)
                    setStep[0].accept(0, ST_ACTIVE);
                    if (oldSubdomain != null && !oldSubdomain.isEmpty()) {
                        client.deleteDnsLink("", oldSubdomain, oldBaseDomain);
                    }
                    oldDnsDeleted = true;
                    setStep[0].accept(0, ST_OK);

                    // Step 2: create new DNS (new zone) — only when a tunnel exists
                    setStep[0].accept(1, ST_ACTIVE);
                    if (target != null) {
                        client.createDnsLink("", newSubdomain, newBaseDomain, target, port, "tcp");
                        newDnsCreated = true;
                    }
                    setStep[0].accept(1, ST_OK);

                    // Step 3: database sync
                    setStep[0].accept(2, ST_ACTIVE);
                    if (!patchServerHostInSupabase(oldSubdomain, newSubdomain, newBaseDomain)) {
                        throw new IllegalStateException("DB sync failed");
                    }
                    setStep[0].accept(2, ST_OK);

                    // All network steps done — now update local state
                    String domain = newSubdomain + "." + newBaseDomain;
                    if (target != null) {
                        server.setDomainLink(domain + " -> " + server.getPlayitAddress());
                    } else {
                        server.setDomainLink("");
                    }
                    server.setSubdomain(newSubdomain);
                    server.setBaseDomain(newBaseDomain);
                    repo.update(server);
                    eu.kodanetwork.mchost.App.getPrefs(this).edit()
                            .putLong("last_join_change_" + server.getId(), System.currentTimeMillis()).apply();

                    setStep[0].accept(3, ST_OK);
                    runOnUiThread(() -> {
                        if (finalTarget != null) {
                            tvDomainStatus.setText("Domain: " + server.getDomainLink());
                        } else {
                            tvDomainStatus.setText("Domain: " + domain + " (offline)");
                        }
                        updateJoinAddressDisplay();
                        Toast.makeText(this, getString(R.string.sd_toast_subdomain_updated), Toast.LENGTH_SHORT).show();
                    });
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(dialog::dismiss, 900);
                } catch (Exception e) {
                    int failedIdx = !oldDnsDeleted ? 0 : (!newDnsCreated && target != null ? 1 : 2);
                    setStep[0].accept(failedIdx, ST_FAIL);
                    // Roll back everything that already succeeded, in reverse order
                    runOnUiThread(() -> stepTexts[failedIdx].setText(getString(R.string.join_rollback)));
                    try { if (newDnsCreated) client.deleteDnsLink("", newSubdomain, newBaseDomain); } catch (Exception ignored) {}
                    try { if (oldDnsDeleted && target != null) client.createDnsLink("", oldSubdomain, oldBaseDomain, target, port, "tcp"); } catch (Exception ignored) {}
                    String msg = e.getMessage() == null ? "unknown" : e.getMessage();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("HTTP (\\d{3})").matcher(msg);
                    int code = m.find() ? Integer.parseInt(m.group(1)) : 0;
                    String apiMsg = msg.contains(":") ? msg.substring(msg.indexOf(":") + 1).trim() : msg;
                    int fCode = code;
                    runOnUiThread(() -> {
                        stepTexts[failedIdx].setText(getString(R.string.join_failed_rolled_back, fCode, apiMsg));
                        btnCancel.setEnabled(true);
                        tvDomainStatus.setText("Domain: " + server.getSubdomain() + "." + server.getBaseDomain());
                    });
                }
            });
        });
        dialog.show();
    }

    /** Patches host (+ base_domain) of the koda_servers row so DB matches the app. */
    private boolean patchServerHostInSupabase(String oldHost, String newHost, String baseDomain) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                org.json.JSONObject payload = new org.json.JSONObject().put("host", newHost);
                if (baseDomain != null) payload.put("base_domain", baseDomain);
                org.json.JSONObject body = new org.json.JSONObject()
                        .put("p_app_uuid", eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", ""))
                        .put("p_host", oldHost)
                        .put("p_payload", payload);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                        eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("Authorization", "Bearer " +
                        eu.kodanetwork.mchost.network.supabase.SupabaseAuth.getSessionToken(this));
                conn.setRequestProperty("Prefer", "return=minimal");
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.toString().getBytes());
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 401 && attempt == 0) {
                    eu.kodanetwork.mchost.network.supabase.SupabaseAuth.refreshTokenSync(this);
                    continue;
                }
                if (code >= 200 && code < 300) return true;
                android.util.Log.w("ServerDetail", "patchServerHost HTTP " + code);
            } catch (Exception e) {
                android.util.Log.e("ServerDetail", "patchServerHost error", e);
            }
        }
        return false;
    }

    private void showAddonOptions(String addonType, android.widget.CompoundButton toggleSwitch) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_praetor_addon);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        TextView tvTitle = dialog.findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

        TextView tvSubtitle = dialog.findViewById(R.id.tv_addon_title);
        tvSubtitle.setText(addonType.equals("bedrock") ? "BEDROCK SUPPORT" : "VOICECHAT SUPPORT");

        boolean isEnabled = addonType.equals("bedrock") ? server.isBedrockSupport() : server.isVoicechat();
        boolean hasCustomDomain = server.getCustomDomain() != null && !server.getCustomDomain().isEmpty();

        com.google.android.material.button.MaterialButton btnToggle = dialog.findViewById(R.id.btn_addon_toggle);
        com.google.android.material.button.MaterialButton btnChange = dialog.findViewById(R.id.btn_addon_change_domain);
        com.google.android.material.button.MaterialButton btnUnlink = dialog.findViewById(R.id.btn_addon_unlink_domain);

        btnToggle.setText(isEnabled ? "TURN OFF" : "TURN ON");
        btnToggle.setOnClickListener(v -> {
            if (!isEnabled) {
                toggleSwitch.setChecked(true);
                if (addonType.equals("bedrock")) {
                    server.setBedrockSupport(true);
                    View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
                    if (layoutBedrockPort != null && tvBedrockPortDash != null) {
                        layoutBedrockPort.setVisibility(View.VISIBLE);
                        tvBedrockPortDash.setText(String.valueOf(server.getBedrockPort()));
                    }
                } else {
                    server.setVoicechat(true);
                }
                repo.update(server);
                triggerAddonRestart();
                
                android.content.Intent intent = new android.content.Intent(ServerDetailActivity.this, CustomDnsWizardActivity.class);
                intent.putExtra("SERVER_ID", server.getId());
                intent.putExtra("SERVER_PORT", server.getPort());
                intent.putExtra("TARGET_RECORD", addonType);
                startActivity(intent);
                
                dialog.dismiss();
            } else {
                dialog.dismiss();
                showAddonWarningDialog(addonType, toggleSwitch);
            }
        });

        if (isEnabled && hasCustomDomain) {
            btnChange.setVisibility(View.VISIBLE);
            btnUnlink.setVisibility(View.VISIBLE);
            
            btnChange.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(ServerDetailActivity.this, CustomDnsWizardActivity.class);
                intent.putExtra("SERVER_ID", server.getId());
                intent.putExtra("SERVER_PORT", server.getPort());
                intent.putExtra("TARGET_RECORD", addonType);
                startActivity(intent);
                dialog.dismiss();
            });

            btnUnlink.setOnClickListener(v -> {
                android.widget.Toast.makeText(this, getString(R.string.sd_toast_unlinked_domain), android.widget.Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        } else if (isEnabled) {
            btnChange.setVisibility(View.VISIBLE);
            btnUnlink.setVisibility(View.GONE);
            
            btnChange.setText(getString(R.string.sd_link_custom_domain));
            btnChange.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(ServerDetailActivity.this, CustomDnsWizardActivity.class);
                intent.putExtra("SERVER_ID", server.getId());
                intent.putExtra("SERVER_PORT", server.getPort());
                intent.putExtra("TARGET_RECORD", addonType);
                startActivity(intent);
                dialog.dismiss();
            });
        } else {
            btnChange.setVisibility(View.GONE);
            btnUnlink.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private void showAddonWarningDialog(String addonType, android.widget.CompoundButton toggleSwitch) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_praetor_addon);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        TextView tvTitle = dialog.findViewById(R.id.tv_praetor_title);
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));

        TextView tvSubtitle = dialog.findViewById(R.id.tv_addon_title);
        tvSubtitle.setText(addonType.equals("bedrock") ? "BEDROCK SUPPORT" : "VOICECHAT SUPPORT");
        
        boolean hasCustomDomain = server.getCustomDomain() != null && !server.getCustomDomain().trim().isEmpty() && !server.getCustomDomain().contains("koda.network");
        
        dialog.findViewById(R.id.layout_addon_options).setVisibility(View.GONE);
        dialog.findViewById(R.id.layout_addon_warning).setVisibility(View.VISIBLE);
        
        TextView tvWarningText = dialog.findViewById(R.id.tv_addon_warning_text);
        String warningText = "The server will be stopped.\n";
        if (addonType.equals("bedrock")) {
            warningText += "The 'Geyser-Spigot' and 'floodgate' plugins and their folders will be deleted.\n";
        } else {
            warningText += "The 'voicechat' plugin and its folder will be deleted.\n";
        }
        if (hasCustomDomain) {
            warningText += "\n⚠ YOU MUST DELETE THE SRV RECORD ⚠\n";
            if (addonType.equals("bedrock")) {
                warningText += "_minecraft._udp." + server.getCustomDomain();
            } else {
                warningText += "_voicechat._udp." + server.getCustomDomain();
            }
            warningText += "\nfrom your DNS provider manually!";
        }
        tvWarningText.setText(warningText);
        
        TextView tvCountdown = dialog.findViewById(R.id.tv_addon_countdown);
        com.google.android.material.button.MaterialButton btnConfirm = dialog.findViewById(R.id.btn_addon_confirm_off);
        
        new android.os.CountDownTimer(10000, 1000) {
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(millisUntilFinished / 1000));
            }
            public void onFinish() {
                tvCountdown.setVisibility(View.GONE);
                btnConfirm.setEnabled(true);
            }
        }.start();
        
        btnConfirm.setOnClickListener(confirmView -> {
            if (addonType.equals("bedrock")) {
                server.setBedrockSupport(false);
                View layoutBedrockPort = findViewById(R.id.layout_bedrock_port);
                if (layoutBedrockPort != null) layoutBedrockPort.setVisibility(View.GONE);
            } else {
                server.setVoicechat(false);
            }
            repo.update(server);
            toggleSwitch.setChecked(false);
            
            cleanupAddonFiles(addonType);
            dialog.dismiss();
        });
        
        dialog.findViewById(R.id.btn_addon_cancel).setOnClickListener(cancelView -> {
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void cleanupAddonFiles(String addonType) {
        if (server.isRunning()) {
            android.content.Intent intent = new android.content.Intent(this, eu.kodanetwork.mchost.service.KodaServerService.class);
            intent.setAction(eu.kodanetwork.mchost.service.KodaServerService.ACTION_STOP);
            intent.putExtra(eu.kodanetwork.mchost.service.KodaServerService.EXTRA_ID, server.getId());
            startService(intent);
        }
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            File pluginsDir = new File(server.getServerDir(), "plugins");
            if (addonType.equals("bedrock")) {
                File geyserJar = new File(pluginsDir, "Geyser.jar");
                File geyserSpigotJar = new File(pluginsDir, "Geyser-Spigot.jar");
                File floodgateJar = new File(pluginsDir, "Floodgate.jar");
                File floodgateBukkitJar = new File(pluginsDir, "floodgate-bukkit.jar");
                File floodgateOldJar = new File(pluginsDir, "floodgate-spigot.jar");
                if (geyserJar.exists()) geyserJar.delete();
                if (geyserSpigotJar.exists()) geyserSpigotJar.delete();
                if (floodgateJar.exists()) floodgateJar.delete();
                if (floodgateBukkitJar.exists()) floodgateBukkitJar.delete();
                if (floodgateOldJar.exists()) floodgateOldJar.delete();
                deleteRecursive(new File(pluginsDir, "Geyser-Spigot"));
                deleteRecursive(new File(pluginsDir, "floodgate"));
            } else {
                File vcJar = new File(pluginsDir, "Voicechat.jar");
                File vcJarOld = new File(pluginsDir, "voicechat-bukkit.jar");
                if (vcJar.exists()) vcJar.delete();
                if (vcJarOld.exists()) vcJarOld.delete();
                deleteRecursive(new File(pluginsDir, "voicechat"));
            }
            android.widget.Toast.makeText(this, getString(R.string.sd_toast_addon_removed), android.widget.Toast.LENGTH_SHORT).show();
        }, server.isRunning() ? 4000 : 500);
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
        EditText etMotd = findViewById(R.id.et_motd);
        android.widget.Spinner spinnerGamemode = findViewById(R.id.spinner_gamemode);
        android.widget.CompoundButton switchForceGamemode = findViewById(R.id.switch_force_gamemode);

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

        final String[] gamemodes = {"survival", "creative", "adventure", "spectator"};
        if (spinnerGamemode != null) {
            android.widget.ArrayAdapter<String> gmAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, gamemodes);
            gmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerGamemode.setAdapter(gmAdapter);
            String gm = props.getProperty("gamemode", "survival");
            for (int i = 0; i < gamemodes.length; i++) {
                if (gamemodes[i].equalsIgnoreCase(gm)) spinnerGamemode.setSelection(i);
            }
        }
        if (switchForceGamemode != null) {
            switchForceGamemode.setChecked("true".equalsIgnoreCase(props.getProperty("force-gamemode", "false")));
        }
        if (etMotd != null) {
            String motd = props.getProperty("motd", server.getMotd() != null ? server.getMotd() : "");
            etMotd.setText(motd);
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
            if (etMotd != null) updates.put("motd", etMotd.getText().toString());
            if (spinnerGamemode != null && spinnerGamemode.getSelectedItemPosition() >= 0) {
                updates.put("gamemode", gamemodes[spinnerGamemode.getSelectedItemPosition()]);
            }
            if (switchForceGamemode != null) updates.put("force-gamemode", String.valueOf(switchForceGamemode.isChecked()));
            updates.put("view-distance", String.valueOf(sbViewDistance.getProgress() + 5));
            updates.put("simulation-distance", String.valueOf(sbSimDistance.getProgress() + 5));
            updates.put("online-mode", String.valueOf(switchOnlineMode.isChecked()));
            updates.put("hardcore", String.valueOf(switchHardcore.isChecked()));
            updates.put("pvp", String.valueOf(switchPvp.isChecked()));
            updates.put("allow-flight", String.valueOf(switchFlight.isChecked()));

            // Keep the model in sync so KodaServerService.writeProps() rewrites
            // the same values on the next server start instead of stale ones
            try { server.setMaxPlayers(Integer.parseInt(etMaxPlayers.getText().toString())); } catch (Exception ignored) {}
            if (etMotd != null) server.setMotd(etMotd.getText().toString());
            try { server.setDifficulty(ServerInstance.Difficulty.valueOf(difficulties[spinnerDifficulty.getSelectedItemPosition()])); } catch (Exception ignored) {}
            if (spinnerGamemode != null && spinnerGamemode.getSelectedItemPosition() >= 0) {
                try { server.setGamemode(ServerInstance.Gamemode.valueOf(gamemodes[spinnerGamemode.getSelectedItemPosition()])); } catch (Exception ignored) {}
            }
            server.setPvp(switchPvp.isChecked());
            repo.update(server);

            writePropsEntries(propsFile, updates);
        };

        // Listeners
        etMaxPlayers.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) { saveProps.run(); }
        });

        if (etMotd != null) {
            etMotd.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) { saveProps.run(); }
            });
        }

        spinnerDifficulty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { saveProps.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        if (spinnerGamemode != null) {
            spinnerGamemode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { saveProps.run(); }
                public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
        }

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
        if (switchForceGamemode != null) switchForceGamemode.setOnCheckedChangeListener(checkListener);

        // Manual edits of server.properties are allowed, but writeProps() would
        // silently overwrite them from the model on the next start — warn instead
        checkPropsDriftAndWarn();
    }

    /**
     * Re-reads server.properties, restores the strictly app-managed port/ip and
     * warns about manual edits of app-tracked values instead of clobbering them
     * on the next server start. Called on screen setup and whenever the user
     * (re)enters the dashboard tab.
     */
    private void checkPropsDriftAndWarn() {
        File propsFile = new File(server.getServerDir(), "server.properties");
        if (!propsFile.exists()) return;
        if (propsDriftDialog != null && propsDriftDialog.isShowing()) return;

        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(propsFile)) {
            props.load(fis);
        } catch (Exception ignored) { return; }

        // server-port and server-ip are strictly app-managed (tunnel/DNS wiring
        // depends on them) — restore them immediately, never offer them as a choice
        java.util.Map<String, String> enforce = new java.util.HashMap<>();
        String filePort = props.getProperty("server-port");
        if (filePort != null && !filePort.trim().equals(String.valueOf(server.getPort()).trim())) {
            enforce.put("server-port", String.valueOf(server.getPort()));
        }
        String fileIp = props.getProperty("server-ip");
        if (fileIp != null && !fileIp.trim().equals("127.0.0.1")) {
            enforce.put("server-ip", "127.0.0.1");
        }
        if (!enforce.isEmpty()) {
            writePropsEntries(propsFile, enforce);
            android.widget.Toast.makeText(this, getString(R.string.sd_props_port_ip_managed), android.widget.Toast.LENGTH_LONG).show();
        }

        java.util.LinkedHashMap<String, String> fileVals = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> modelVals = new java.util.LinkedHashMap<>();
        collectPropsDrift(props, "motd", server.getMotd(), fileVals, modelVals);
        collectPropsDrift(props, "gamemode", server.getGamemode() == null ? null : server.getGamemode().name(), fileVals, modelVals);
        collectPropsDrift(props, "difficulty", server.getDifficulty() == null ? null : server.getDifficulty().name(), fileVals, modelVals);
        collectPropsDrift(props, "pvp", String.valueOf(server.isPvp()), fileVals, modelVals);
        collectPropsDrift(props, "white-list", String.valueOf(server.isWhitelist()), fileVals, modelVals);
        collectPropsDrift(props, "max-players", String.valueOf(server.getMaxPlayers()), fileVals, modelVals);

        if (fileVals.isEmpty()) return;

        StringBuilder msg = new StringBuilder(getString(R.string.sd_props_changed_prefix) + "\n\n");
        for (java.util.Map.Entry<String, String> e : fileVals.entrySet()) {
            msg.append(e.getKey()).append(": \"").append(e.getValue())
               .append("\"\n").append(getString(R.string.sd_props_app_value)).append(" \"")
               .append(modelVals.get(e.getKey())).append("\"\n\n");
        }
        msg.append(getString(R.string.sd_props_changed_suffix));

        android.app.Dialog driftDialog = new android.app.Dialog(this);
        driftDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        driftDialog.setContentView(R.layout.dialog_praetor_delete);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(driftDialog);
        if (driftDialog.getWindow() != null) {
            driftDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            driftDialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
        TextView driftTitle = driftDialog.findViewById(R.id.tv_dialog_title);
        if (driftTitle != null) {
            String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
            driftTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        }
        ((TextView) driftDialog.findViewById(R.id.tv_delete_title)).setText(getString(R.string.sd_props_changed_title));
        ((TextView) driftDialog.findViewById(R.id.tv_delete_body)).setText(msg.toString());

        android.widget.Button btnUseManual = driftDialog.findViewById(R.id.btn_dialog_delete);
        btnUseManual.setText(getString(R.string.sd_props_use_manual));
        btnUseManual.setOnClickListener(x -> {
            for (java.util.Map.Entry<String, String> e : fileVals.entrySet()) {
                String v = e.getValue();
                switch (e.getKey()) {
                    case "motd": server.setMotd(v); break;
                    case "gamemode":
                        try { server.setGamemode(ServerInstance.Gamemode.valueOf(v.toLowerCase())); } catch (Exception ignored) {} break;
                    case "difficulty":
                        try { server.setDifficulty(ServerInstance.Difficulty.valueOf(v.toLowerCase())); } catch (Exception ignored) {} break;
                    case "pvp": server.setPvp(Boolean.parseBoolean(v)); break;
                    case "white-list": server.setWhitelist(Boolean.parseBoolean(v)); break;
                    case "max-players":
                        try { server.setMaxPlayers(Integer.parseInt(v)); } catch (Exception ignored) {} break;
                }
            }
            repo.update(server);
            driftDialog.dismiss();
        });

        android.widget.Button btnUseApp = driftDialog.findViewById(R.id.btn_dialog_cancel);
        btnUseApp.setText(getString(R.string.sd_props_use_app));
        btnUseApp.setOnClickListener(x -> {
            java.util.Map<String, String> restore = new java.util.HashMap<>();
            if (server.getMotd() != null) restore.put("motd", server.getMotd());
            if (server.getGamemode() != null) restore.put("gamemode", server.getGamemode().name());
            if (server.getDifficulty() != null) restore.put("difficulty", server.getDifficulty().name());
            restore.put("pvp", String.valueOf(server.isPvp()));
            restore.put("white-list", String.valueOf(server.isWhitelist()));
            restore.put("max-players", String.valueOf(server.getMaxPlayers()));

            // Reflect the restored model values in the widgets; every programmatic
            // change re-fires saveProps, so write the model values again last to
            // guarantee the file ends up matching the model
            EditText etMaxPlayers = findViewById(R.id.et_max_players);
            EditText etMotd = findViewById(R.id.et_motd);
            android.widget.Spinner spinnerDifficulty = findViewById(R.id.spinner_difficulty);
            android.widget.Spinner spinnerGamemode = findViewById(R.id.spinner_gamemode);
            android.widget.CompoundButton switchPvp = findViewById(R.id.switch_pvp);
            if (etMaxPlayers != null) etMaxPlayers.setText(String.valueOf(server.getMaxPlayers()));
            if (etMotd != null && server.getMotd() != null) etMotd.setText(server.getMotd());
            if (spinnerGamemode != null && server.getGamemode() != null) {
                String[] gamemodes = {"survival", "creative", "adventure", "spectator"};
                for (int i = 0; i < gamemodes.length; i++) {
                    if (gamemodes[i].equalsIgnoreCase(server.getGamemode().name())) spinnerGamemode.setSelection(i);
                }
            }
            if (spinnerDifficulty != null && server.getDifficulty() != null) {
                String[] difficulties = {"peaceful", "easy", "normal", "hard"};
                for (int i = 0; i < difficulties.length; i++) {
                    if (difficulties[i].equalsIgnoreCase(server.getDifficulty().name())) spinnerDifficulty.setSelection(i);
                }
            }
            if (switchPvp != null) switchPvp.setChecked(server.isPvp());
            writePropsEntries(propsFile, restore);

            driftDialog.dismiss();
        });

        driftDialog.setOnDismissListener(d -> propsDriftDialog = null);
        propsDriftDialog = driftDialog;
        driftDialog.show();
    }

    private void collectPropsDrift(java.util.Properties props, String key, String modelVal,
                                   java.util.Map<String, String> fileVals, java.util.Map<String, String> modelVals) {
        if (modelVal == null) return;
        String fileVal = props.getProperty(key);
        if (fileVal != null && !fileVal.trim().equalsIgnoreCase(modelVal.trim())) {
            fileVals.put(key, fileVal.trim());
            modelVals.put(key, modelVal.trim());
        }
    }

    /** Updates the given keys in a .properties-style file, preserving all other lines. */
    private void writePropsEntries(File propsFile, java.util.Map<String, String> updates) {
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
    }

    
    private void showModrinthSearch() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.KodaBottomSheetDialog);
        dialog.setContentView(R.layout.dialog_modrinth_search);
        
        android.view.Window window = dialog.getWindow();
        setupWindowDecor(window);
        
        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bsd = (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            android.widget.FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                bottomSheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
                behavior.setPeekHeight(android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        
        android.widget.EditText etSearch = dialog.findViewById(R.id.et_modrinth_search);
        android.widget.ImageButton btnSubmit = dialog.findViewById(R.id.btn_modrinth_search_submit);
        androidx.recyclerview.widget.RecyclerView rvResults = dialog.findViewById(R.id.rv_modrinth_results);
        android.widget.ProgressBar pb = dialog.findViewById(R.id.pb_modrinth_search);
        
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        eu.kodanetwork.mchost.ui.adapters.ModrinthSearchAdapter adapter = new eu.kodanetwork.mchost.ui.adapters.ModrinthSearchAdapter(this, (project, pbDownload, btn) -> {
            pbDownload.setVisibility(android.view.View.VISIBLE);
            btn.setVisibility(android.view.View.GONE);
            new Thread(() -> {
                try {
                    String projectId = project.optString("project_id", project.optString("id"));
                    String projectTitle = project.optString("title");
                    String versionUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(versionUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/1.0");
                    java.io.InputStream in = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    in.close();
                    
                    org.json.JSONArray versions = new org.json.JSONArray(sb.toString());
                    if (versions.length() > 0) {
                        org.json.JSONObject latest = versions.getJSONObject(0);
                        org.json.JSONArray files = latest.getJSONArray("files");
                        if (files.length() > 0) {
                            org.json.JSONObject file = files.getJSONObject(0);
                            String dlUrl = file.getString("url");
                            String sha1 = file.getJSONObject("hashes").getString("sha1");
                            
                            runOnUiThread(() -> {
                                pbDownload.setVisibility(android.view.View.GONE);
                                btn.setVisibility(android.view.View.VISIBLE);
                                dialog.dismiss();
                                
                                java.io.File propsFile = new java.io.File(server.getServerDir(), "server.properties");
                                try {
                                    java.util.List<String> linesProps = new java.util.ArrayList<>();
                                    if (propsFile.exists()) {
                                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(propsFile))) {
                                            String l; while ((l = br.readLine()) != null) linesProps.add(l);
                                        }
                                    }
                                    boolean foundUrl = false, foundSha1 = false;
                                    for (int i = 0; i < linesProps.size(); i++) {
                                        if (linesProps.get(i).trim().startsWith("resource-pack=")) {
                                            linesProps.set(i, "resource-pack=" + dlUrl);
                                            foundUrl = true;
                                        } else if (linesProps.get(i).trim().startsWith("resource-pack-sha1=")) {
                                            linesProps.set(i, "resource-pack-sha1=" + sha1);
                                            foundSha1 = true;
                                        }
                                    }
                                    if (!foundUrl) linesProps.add("resource-pack=" + dlUrl);
                                    if (!foundSha1) linesProps.add("resource-pack-sha1=" + sha1);
                                    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(propsFile))) {
                                        for (String l : linesProps) pw.println(l);
                                    }
                                } catch (Exception ignored) {}
                                
                                android.widget.TextView tvUrl = findViewById(R.id.tv_resource_pack_url);
                                if (tvUrl != null) tvUrl.setText(dlUrl);
                                
                                android.widget.Toast.makeText(ServerDetailActivity.this, "Resource Pack applied: " + projectTitle, android.widget.Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }
                    }
                    runOnUiThread(() -> {
                        pbDownload.setVisibility(android.view.View.GONE);
                        btn.setVisibility(android.view.View.VISIBLE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "No valid files found for " + projectTitle, android.widget.Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        pbDownload.setVisibility(android.view.View.GONE);
                        btn.setVisibility(android.view.View.VISIBLE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "Error fetching versions: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        rvResults.setAdapter(adapter);
        
        android.view.View.OnClickListener doSearch = v -> {
            String query = etSearch.getText().toString().trim();
            
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
            pb.setVisibility(android.view.View.VISIBLE);
            
            new Thread(() -> {
                try {
                    String urlStr = "https://api.modrinth.com/v2/search?limit=100&facets=[[%22project_type:resourcepack%22]]";
                    if (!query.isEmpty()) {
                        urlStr += "&query=" + java.net.URLEncoder.encode(query, "UTF-8");
                    }
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/1.0");
                    
                    java.io.InputStream in = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    in.close();
                    
                    org.json.JSONObject result = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray hits = result.getJSONArray("hits");
                    
                    runOnUiThread(() -> {
                        pb.setVisibility(android.view.View.GONE);
                        adapter.setResults(hits);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        pb.setVisibility(android.view.View.GONE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "Search error: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        };
        
        btnSubmit.setOnClickListener(doSearch);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch.onClick(v);
                return true;
            }
            return false;
        });
        
        // Trigger initial search for popular resource packs
        doSearch.onClick(null);
        
        eu.kodanetwork.mchost.util.SheetFix.apply(dialog);
        dialog.show();
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
                    .setTitle(getString(R.string.sd_dialog_download_failed)).setMessage(e)
                    .setPositiveButton("OK", null).show();
            }
        });
    }

    // ── Uptime Ticker ─────────────────────────────────────────────────────────

    private String lastTheme = "modern";
    private String lastThemeMode = "dark";

    private android.net.ConnectivityManager.NetworkCallback netCb;

    /** Slow-internet banner: link bandwidth below these thresholds = warn the user. */
    private void registerNetQualityWatcher() {
        TextView banner = findViewById(R.id.tv_net_warning);
        if (banner == null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (netCb != null) { try { cm.unregisterNetworkCallback(netCb); } catch (Exception ignored) {} }
            netCb = new android.net.ConnectivityManager.NetworkCallback() {
                @Override public void onCapabilitiesChanged(android.net.Network n, android.net.NetworkCapabilities caps) {
                    boolean slow = caps.getLinkDownstreamBandwidthKbps() > 0
                            && caps.getLinkDownstreamBandwidthKbps() < 1500;
                    runOnUiThread(() -> {
                        if (slow) {
                            banner.setText(getString(R.string.net_quality_warning));
                            banner.setBackgroundColor(0x33FFCC00);
                            banner.setTextColor(0xFFFFCC00);
                            banner.setVisibility(android.view.View.VISIBLE);
                        } else {
                            banner.setVisibility(android.view.View.GONE);
                        }
                    });
                }
                @Override public void onLost(android.net.Network n) {
                    runOnUiThread(() -> {
                        banner.setText(getString(R.string.net_offline_warning));
                        banner.setBackgroundColor(0x44FF4444);
                        banner.setTextColor(0xFFFF4444);
                        banner.setVisibility(android.view.View.VISIBLE);
                        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(ServerDetailActivity.this, 250);
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(ServerDetailActivity.this, 400), 450);
                    });
                }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Exception e) {
            android.util.Log.d("ServerDetail", "net watcher failed", e);
        }
    }

    protected void onResume() {
        super.onResume();
        registerNetQualityWatcher();
        startNetworkUsageTicker();
        // Re-theme dynamically built dashboard/tab content for light mode
        eu.kodanetwork.mchost.util.ThemeHelper.reapply(this);
        // Re-sync control buttons: if the service died while we were backgrounded
        // (e.g. MIUI killed the app), no state callback ever re-enables START otherwise
        if (server != null) {
            try { updateDash(); } catch (Exception ignored) {}
        }
        if (tvJoinAddr != null && server != null) {
            updateJoinAddressDisplay();
        }
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
        
        
        checkForUpdatesAsync();
        for (TiltEffectHelper helper : tiltHelpers) {
            helper.register();
        }
    }

    private final android.os.Handler netUsageHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable netUsageTick = new Runnable() {
        @Override public void run() {
            TextView tv = findViewById(R.id.tv_network_usage);
            if (tv != null && server != null) {
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                            getSystemService(Context.CONNECTIVITY_SERVICE);
                    boolean onMobile = cm != null && cm.isActiveNetworkMetered();
                    eu.kodanetwork.mchost.util.NetworkPolicy.Rule worst = null;
                    int worstPct = -1; long used = 0, limit = 0;
                    for (eu.kodanetwork.mchost.util.NetworkPolicy.Rule r :
                            eu.kodanetwork.mchost.util.NetworkPolicy.getRules(ServerDetailActivity.this, server.getId())) {
                        if (!(onMobile ? r.mobile : r.wifi)) continue;
                        eu.kodanetwork.mchost.util.NetworkPolicy.Usage u =
                                eu.kodanetwork.mchost.util.NetworkPolicy.usageFor(ServerDetailActivity.this, r.periodDays);
                        long ru = onMobile ? u.mobileBytes : u.wifiBytes;
                        if (r.limitBytes <= 0) continue;
                        int pct = (int) Math.min(100, ru * 100 / r.limitBytes);
                        if (pct > worstPct) { worstPct = pct; worst = r; used = ru; limit = r.limitBytes; }
                    }
                    if (worst != null) {
                        String type = onMobile ? "Mobile" : "WLAN";
                        tv.setText(getString(R.string.net_usage_label,
                                eu.kodanetwork.mchost.util.NetworkPolicy.humanBytes(used),
                                eu.kodanetwork.mchost.util.NetworkPolicy.humanBytes(limit),
                                worstPct, type));
                        tv.setTextColor(eu.kodanetwork.mchost.util.NetworkPolicy.colorForPercent(worstPct));
                        tv.setVisibility(android.view.View.VISIBLE);
                    } else {
                        tv.setVisibility(android.view.View.GONE);
                    }
                } catch (Exception ignored) {}
            }
            netUsageHandler.postDelayed(this, 3000);
        }
    };

    private void startNetworkUsageTicker() {
        netUsageHandler.removeCallbacks(netUsageTick);
        netUsageHandler.post(netUsageTick);
    }

    protected void onPause() {
        super.onPause();
        netUsageHandler.removeCallbacks(netUsageTick);
        for (TiltEffectHelper helper : tiltHelpers) {
            helper.unregister();
        }
        if (netCb != null) {
            try {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);
                cm.unregisterNetworkCallback(netCb);
            } catch (Exception ignored) {}
            netCb = null;
        }
    }

    private void updateJoinAddressDisplay() {
        if (tvJoinAddr == null || server == null) return;
        String customDomain = server.getCustomDomain();
        String defaultDomain = server.getJoinAddress();
        
        if (customDomain != null && !customDomain.trim().isEmpty() && !customDomain.contains("koda.network")) {
            String html = "<b>" + customDomain + "</b><br><small><font color='#AAAAAA'>" + defaultDomain + "</font></small>";
            tvJoinAddr.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY));
            tvJoinAddr.setOnClickListener(v -> copyToClipboard("Join Address", customDomain));
        } else {
            String displayDomain = (customDomain != null && !customDomain.trim().isEmpty()) ? customDomain : defaultDomain;
            tvJoinAddr.setText(displayDomain);
            tvJoinAddr.setOnClickListener(v -> copyToClipboard("Join Address", displayDomain));
        }
    }

    private void checkServerBanStatus() {
        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        String sessionToken = prefs.getString("koda_session_token", null);
        String cacheKey = "banned_server_" + server.getId();
        String reasonKey = "banned_reason_" + server.getId();
        
        // Check offline cache first
        if (prefs.getBoolean(cacheKey, false)) {
            showBannedOverlay(prefs.getString(reasonKey, "Unknown"));
        }
        
        // Fetch from Supabase
        new Thread(() -> {
            try {
                String responseStr = eu.kodanetwork.mchost.network.supabase.SupportApi.makeSupabaseRequest(
                        "rest/v1/koda_servers?select=is_banned,ban_reason&host=eq." + server.getSubdomain(), "GET", null, sessionToken);
                org.json.JSONArray arr = new org.json.JSONArray(responseStr);
                if (arr.length() > 0) {
                    org.json.JSONObject obj = arr.getJSONObject(0);
                    boolean isBanned = obj.optBoolean("is_banned", false);
                    String banReason = obj.optString("ban_reason", "Verstoß gegen die Nutzungsbedingungen");
                    
                    if (isBanned) {
                        prefs.edit().putBoolean(cacheKey, true).putString(reasonKey, banReason).apply();
                        runOnUiThread(() -> showBannedOverlay(banReason));
                    } else {
                        prefs.edit().remove(cacheKey).remove(reasonKey).apply();
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    private void showBannedOverlay(String reason) {
        View layoutBanned = findViewById(R.id.layout_server_banned);
        TextView tvBannedReason = findViewById(R.id.tv_banned_reason);
        if (layoutBanned != null && tvBannedReason != null) {
            tvBannedReason.setText(reason);
            layoutBanned.setVisibility(View.VISIBLE);
            
            // Hide everything except the top bar
            View tabs = findViewById(R.id.tabs);
            if (tabs != null) tabs.setVisibility(View.GONE);
            View panelDash = findViewById(R.id.panel_dash);
            if (panelDash != null) panelDash.setVisibility(View.GONE);
            View panelConsole = findViewById(R.id.panel_console);
            if (panelConsole != null) panelConsole.setVisibility(View.GONE);
            View panelFiles = findViewById(R.id.panel_files);
            if (panelFiles != null) panelFiles.setVisibility(View.GONE);
            View panelPlugins = findViewById(R.id.panel_plugins);
            if (panelPlugins != null) panelPlugins.setVisibility(View.GONE);
            View panelSettings = findViewById(R.id.panel_settings);
            if (panelSettings != null) panelSettings.setVisibility(View.GONE);
            View restartHeader = findViewById(R.id.layout_header_restart);
            if (restartHeader != null) restartHeader.setVisibility(View.GONE);
        }
    }
    
    private void exportZip() {
        if (server != null) {
            new Thread(() -> {
                try {
                    File cacheDir = new File(getCacheDir(), "exports");
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    
                    File zipFile = new File(cacheDir, server.getName() + "_export.zip");
                    
                    runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_zipping), android.widget.Toast.LENGTH_SHORT).show());
                    
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
                    runOnUiThread(() -> android.widget.Toast.makeText(ServerDetailActivity.this, getString(R.string.sd_toast_export_failed) + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                }
            }).start();
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
                        android.widget.Toast.makeText(this, getString(R.string.sd_toast_error_prefix) + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
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
        } else if (requestCode == REQ_EXPORT_FILE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null && pendingExportFile != null && pendingExportFile.exists()) {
                File src = pendingExportFile;
                pendingExportFile = null;
                new Thread(() -> {
                    boolean ok = false;
                    try (java.io.InputStream in = new java.io.FileInputStream(src);
                         java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        ok = true;
                    } catch (Exception ignored) {}
                    final boolean okF = ok;
                    runOnUiThread(() -> Toast.makeText(this,
                            okF ? getString(R.string.sd_files_exported, src.getName()) : getString(R.string.sd_files_export_failed),
                            Toast.LENGTH_SHORT).show());
                }).start();
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
                .setTitle(getString(R.string.sd_dialog_files_exist))
                .setMessage("The following files already exist:\n\n• " + String.join("\n• ", duplicates) + getString(R.string.sd_dialog_files_exist_msg_2))
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

        Toast.makeText(this, getString(R.string.sd_toast_importing_folder), Toast.LENGTH_SHORT).show();
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

    private void showNameEditDialog() {
        if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_praetor_input);
        eu.kodanetwork.mchost.util.DialogLandFix.apply(dialog);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        
        String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A.E.T.</font><font color=\"#FFFFFF\">O.R.</font>";
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_title)).setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_subtitle)).setText(R.string.praetor_subtitle_change_name);
        ((android.widget.TextView) dialog.findViewById(R.id.tv_dialog_message)).setText(R.string.praetor_message_change_name);
        
        android.widget.EditText input = dialog.findViewById(R.id.et_dialog_input);
        input.setText(server.getName());
        input.setHint(R.string.praetor_hint_change_name);
        
        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
        dialog.findViewById(R.id.btn_dialog_confirm).setOnClickListener(view -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                server.setName(newName);
                repo.update(server);
                TextView tvTitle = findViewById(R.id.tv_title);
                if (tvTitle != null) tvTitle.setText(newName);
                android.widget.Toast.makeText(this, getString(R.string.praetor_change_name_success), android.widget.Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                android.widget.Toast.makeText(this, getString(R.string.praetor_change_name_error), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
        android.widget.Toast.makeText(this, label + getString(R.string.sd_toast_copied_suffix), android.widget.Toast.LENGTH_SHORT).show();
    }

    private void setupWindowDecor(android.view.Window w) {
        if (w == null) return;
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
            
            boolean isLight = eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
            int flags = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (isLight) {
                flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
            
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
            }
        }
    }


    // ── Sleek Server Detail: Tab-Wiring + Press-Animationen ──
    private void setupSleekDetail() {
        if (!eu.kodanetwork.mchost.util.SleekThemeHelper.isSleekEnabled(this)) return;

        // Tab-Werte
        TextView[] tabs = {
            findViewById(R.id.tab_dash), findViewById(R.id.tab_console),
            findViewById(R.id.tab_files), findViewById(R.id.tab_mods), findViewById(R.id.tab_settings)};
        View[] panels = {
            findViewById(R.id.panel_dashboard), findViewById(R.id.panel_console),
            findViewById(R.id.panel_files), findViewById(R.id.panel_mods), findViewById(R.id.panel_settings)};

        java.util.function.BiConsumer<Integer, Runnable>[] switchTab = new java.util.function.BiConsumer[1];
        switchTab[0] = (idx, after) -> {
            for (int i = 0; i < tabs.length; i++) {
                if (tabs[i] == null || panels[i] == null) continue;
                boolean active = i == idx;
                panels[i].setVisibility(active ? View.VISIBLE : View.GONE);
                tabs[i].setTextColor(active ? 0xFFFFFFFF : 0xFF9C968F);
                if (active) tabs[i].setBackgroundResource(R.drawable.sleek_pill_accent); else tabs[i].setBackground(null);
                if (active) tabs[i].setPadding((int)(20 * getResources().getDisplayMetrics().density), 0, (int)(20 * getResources().getDisplayMetrics().density), 0);
            }
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            if (after != null) after.run();
        };

        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            if (tabs[i] != null) {
                eu.kodanetwork.mchost.util.SleekTouch.apply(tabs[i], () -> switchTab[0].accept(idx, null), 40);
            }
        }

        // Deep link from the sleek Files nav: open straight on the Files tab (index 2)
        String openTab = getIntent() != null ? getIntent().getStringExtra("OPEN_TAB") : null;
        if ("files".equals(openTab)) {
            switchTab[0].accept(2, null);
        }

        // Back button
        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
                finish();
            });
        }

        // Header mit Server-Daten füllen
        TextView tvName = findViewById(R.id.tv_server_name);
        if (tvName != null && server != null) tvName.setText(server.getName());

        // Power FAB: start/stop toggle (mirrors the legacy btnStart logic incl. EULA check)
        View fabPower = findViewById(R.id.fab_power);
        if (fabPower != null) {
            eu.kodanetwork.mchost.util.SleekTouch.apply(fabPower, () -> {
                if (server.isRunning()) {
                    sendAction(KodaServerService.ACTION_STOP);
                    return;
                }
                java.io.File serverDir = new java.io.File(server.getServerDir());
                if (!serverDir.exists()) serverDir.mkdirs();
                if (!server.isDatabase()) {
                    if (server.getType() == eu.kodanetwork.mchost.model.ServerInstance.Type.PUMPKIN) {
                        // native Rust binary — no jar check
                        checkEulaAndStart();
                        return;
                    }
                    java.io.File[] jars = serverDir.listFiles((d, name) -> name.endsWith(".jar"));
                    if (jars == null || jars.length == 0) {
                        Toast.makeText(this, "Bitte zuerst die Server .jar herunterladen (Settings-Tab)", Toast.LENGTH_LONG).show();
                        switchTab[0].accept(4, null);
                        return;
                    }
                    checkEulaAndStart();
                } else {
                    checkQueueAndStart();
                }
            }, 60);
        }

        // Restart (dashboard) + Delete
        if (btnRestart != null) {
            eu.kodanetwork.mchost.util.SleekTouch.apply(btnRestart,
                    () -> sendAction(KodaServerService.ACTION_RESTART), 50);
        }
        View btnDelete = findViewById(R.id.btn_delete);
        if (btnDelete != null) {
            eu.kodanetwork.mchost.util.SleekTouch.apply(btnDelete, () -> {
                android.content.Intent i = new android.content.Intent(ServerDetailActivity.this, DeleteServerActivity.class);
                i.putExtra("SERVER_ID", server.getId());
                startActivity(i);
            }, 50);
        }

        // Console: replay log the service already captured (tvLog/etCmd are aliased in bindViews)
        if (bound && svc != null && tvLog != null) {
            for (String l : svc.getLog(server.getId())) appendLog(l);
        }

        // Copy address
        View btnCopy = findViewById(R.id.btn_copy_address);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
                TextView tvAddr = findViewById(R.id.tv_join_address);
                if (tvAddr != null) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("address", tvAddr.getText().toString()));
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                }
            });
            TextView tvAddr = findViewById(R.id.tv_join_address);
            if (tvAddr != null && server != null) tvAddr.setText(server.getJoinAddress());
        }

        updateSleekDash(server != null ? server.state : ServerInstance.State.OFFLINE, null, 0, 0);
    }

    /**
     * Sleek-only dashboard refresh: status text + dot, power FAB icon/tint,
     * RAM usage bar and player count. Called from updateDash().
     */
    private void updateSleekDash(ServerInstance.State st, String label, int col, int playerCount) {
        TextView tvStatus = findViewById(R.id.tv_status);
        View dotStatus = findViewById(R.id.dot_status);
        if (tvStatus == null && dotStatus == null) return;

        if (label == null) {
            switch (st) {
                case ONLINE:     label = "ONLINE";     col = 0xFF69781D; break;
                case STARTING:   label = "STARTING…";  col = 0xFFFFCC00; break;
                case STOPPING:   label = "STOPPING…";  col = 0xFFFF8800; break;
                case RESTARTING: label = "RESTARTING…";col = 0xFFFF8800; break;
                case CRASHED:    label = "CRASHED";    col = 0xFFE8442E; break;
                case INSTALLING: label = "LOADING…";   col = 0xFF44AAFF; break;
                case SETTING_UP: label = "SETTING UP…";col = 0xFF9C27B0; break;
                case HIBERNATED: label = "HIBERNATED"; col = 0xFF44AAFF; break;
                default:         label = "OFFLINE";    col = 0xFF9C968F; break;
            }
        }
        if (tvStatus != null) {
            tvStatus.setText(label);
            tvStatus.setTextColor(col);
        }
        if (dotStatus != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(col);
            dotStatus.setBackground(gd);
        }

        // Power FAB reflects the state: play/green when off, pause/red when running
        com.google.android.material.floatingactionbutton.FloatingActionButton fabPower = findViewById(R.id.fab_power);
        if (fabPower != null && server != null) {
            boolean running = server.isRunning() || st == ServerInstance.State.STARTING;
            fabPower.setImageResource(running
                    ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            fabPower.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    running ? 0xFFE8442E : 0xFF69781D));
        }

        // RAM: live usage vs. allocated
        TextView tvRam = findViewById(R.id.tv_ram_usage);
        android.widget.ProgressBar pbRam = findViewById(R.id.pb_ram);
        if (tvRam != null && server != null) {
            int used = Math.max(0, server.ramUsageMB);
            int max = server.getRamMB();
            tvRam.setText(used + " / " + max + " MB");
            if (pbRam != null && max > 0) {
                pbRam.setMax(max);
                pbRam.setProgress(Math.min(used, max));
            }
        }
    }

    /** Bottom sheet: list + edit + delete network budget rules for this server. */
    private void showNetworkRulesSheet() {
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");
        float d = getResources().getDisplayMetrics().density;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1714);
        int pad = (int)(20*d); root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(de ? "Netzwerk-Regeln" : "Network rules");
        title.setTextColor(0xFFF0F0F0); title.setTextSize(18); title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> { sheet.dismiss(); showNetworkRulesSheet(); };

        java.util.List<eu.kodanetwork.mchost.util.NetworkPolicy.Rule> rules =
                eu.kodanetwork.mchost.util.NetworkPolicy.getRules(this, server.getId());
        if (rules.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(de ? "Noch keine Regeln. Verbrauch wird trotzdem gemessen." : "No rules yet. Usage is still measured.");
            none.setTextColor(0xFF8A8A9A); none.setTextSize(12);
            none.setPadding(0,(int)(12*d),0,(int)(12*d));
            root.addView(none);
        }
        for (eu.kodanetwork.mchost.util.NetworkPolicy.Rule r : rules) {
            TextView row = new TextView(this);
            String types = (r.mobile?"Mobile ":"") + (r.wifi?"WLAN":"");
            row.setText((r.paused ? "⏸ " : "• ") + eu.kodanetwork.mchost.util.NetworkPolicy.humanBytes(r.limitBytes) + " / " + r.periodDays + "d · " + types.trim() + " → " + r.action);
            row.setTextColor(r.paused ? 0xFF8A8A9A : 0xFFE8E2D6); row.setTextSize(13);
            row.setPadding((int)(8*d),(int)(14*d),(int)(8*d),(int)(14*d));
            row.setOnClickListener(v -> showRuleActions(r, refresh[0]));
            root.addView(row);
        }
        com.google.android.material.button.MaterialButton add = eu.kodanetwork.mchost.util.KodaButtons.primary(this,
                de ? "Regel hinzufügen" : "Add rule");
        add.setOnClickListener(v -> editNetworkRule(null, refresh[0]));
        android.widget.LinearLayout.LayoutParams addLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = (int)(16*d);
        root.addView(add, addLp);

        sheet.setContentView(root);
        styleSheetFullscreen(sheet);
        sheet.show();
    }

    /** Fullscreen slide-up sheet (Modrinth-search pattern) with app-warm background. */
    private void styleSheetFullscreen(com.google.android.material.bottomsheet.BottomSheetDialog sheet) {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        // the gray line at the bottom is the dialog WINDOW background showing through
        if (sheet.getWindow() != null) {
            sheet.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            sheet.getWindow().setNavigationBarColor(0xFF1D1714);
        }
        sheet.getBehavior().setPeekHeight((int)(screenH * 0.9f));
        sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        android.view.View sheetBg = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheetBg != null) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1D1714);
            bg.setCornerRadius(24f * getResources().getDisplayMetrics().density);
            sheetBg.setBackground(bg);
        }
    }

    /** Tap on a rule: fullscreen menu with edit / pause / delete. */
    private void showRuleActions(eu.kodanetwork.mchost.util.NetworkPolicy.Rule r, Runnable done) {
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");
        float d = getResources().getDisplayMetrics().density;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1714);
        int pad = (int)(20*d); root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText(eu.kodanetwork.mchost.util.NetworkPolicy.humanBytes(r.limitBytes) + " / " + r.periodDays + "d · " + ((r.mobile?"Mobile ":"")+(r.wifi?"WLAN":"")).trim() + (r.paused ? " · ⏸" : ""));
        t.setTextColor(0xFFF0F0F0); t.setTextSize(18); t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);

        com.google.android.material.button.MaterialButton bEdit = eu.kodanetwork.mchost.util.KodaButtons.primary(this, de ? "Bearbeiten" : "Edit");
        bEdit.setOnClickListener(v -> { sheet.dismiss(); editNetworkRule(r, done); });
        com.google.android.material.button.MaterialButton bPause = eu.kodanetwork.mchost.util.KodaButtons.dark(this, r.paused ? (de?"Fortsetzen":"Resume") : (de?"Anhalten":"Pause"));
        bPause.setOnClickListener(v -> {
            java.util.List<eu.kodanetwork.mchost.util.NetworkPolicy.Rule> rules =
                    eu.kodanetwork.mchost.util.NetworkPolicy.getRules(this, server.getId());
            for (eu.kodanetwork.mchost.util.NetworkPolicy.Rule x : rules) if (x.id.equals(r.id)) x.paused = !x.paused;
            eu.kodanetwork.mchost.util.NetworkPolicy.saveRules(this, server.getId(), rules);
            sheet.dismiss(); done.run();
        });
        com.google.android.material.button.MaterialButton bDel = eu.kodanetwork.mchost.util.KodaButtons.dark(this, de ? "Löschen" : "Delete");
        bDel.setOnClickListener(v -> {
            java.util.List<eu.kodanetwork.mchost.util.NetworkPolicy.Rule> rules =
                    eu.kodanetwork.mchost.util.NetworkPolicy.getRules(this, server.getId());
            rules.removeIf(x -> x.id.equals(r.id));
            eu.kodanetwork.mchost.util.NetworkPolicy.saveRules(this, server.getId(), rules);
            sheet.dismiss(); done.run();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int)(12*d);
        root.addView(bEdit, lp); root.addView(bPause, lp); root.addView(bDel, lp);

        sheet.setContentView(root);
        styleSheetFullscreen(sheet);
        sheet.show();
    }

    /** Create or edit one rule with simple chip options (fullscreen sheet). */
    private void editNetworkRule(eu.kodanetwork.mchost.util.NetworkPolicy.Rule existing, Runnable done) {
        boolean de = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("de");
        float d = getResources().getDisplayMetrics().density;
        eu.kodanetwork.mchost.util.NetworkPolicy.Rule r = existing != null ? existing : new eu.kodanetwork.mchost.util.NetworkPolicy.Rule();
        if (r.id == null) { r.id = java.util.UUID.randomUUID().toString(); r.periodDays = 7; r.mobile = true; r.wifi = true; r.action = eu.kodanetwork.mchost.util.NetworkPolicy.ACTION_WARN; }

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1714);
        int pad = (int)(20*d); root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText(existing == null ? (de?"Neue Regel":"New rule") : (de?"Regel bearbeiten":"Edit rule"));
        t.setTextColor(0xFFF0F0F0); t.setTextSize(20); t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);
        final TextView preview = new TextView(this);
        preview.setTextColor(0xFFB7AE9F); preview.setTextSize(12);
        preview.setPadding(0, (int)(4*d), 0, (int)(8*d));
        java.util.function.BiConsumer<Long, Integer> upd = (lim, per) -> preview.setText(
            (de ? "Vorschau: " : "Preview: ") + eu.kodanetwork.mchost.util.NetworkPolicy.humanBytes(lim == null ? r.limitBytes : lim)
            + " / " + (per == null ? r.periodDays : per) + "d · " + ((r.mobile?"Mobile ":"")+(r.wifi?"WLAN":"")).trim());
        upd.accept(null, null);
        root.addView(preview);

        final long[] limits = {500L*1048576, 1073741824L, 5L*1073741824, 20L*1073741824, -1};
        final String[] limitNames = {"500 MB","1 GB","5 GB","20 GB", de?"Eigenes":"Custom"};
        final int[] pickedLimit = {1};
        for (int i = 0; i < limits.length - 1; i++) if (limits[i] == r.limitBytes) pickedLimit[0] = i;

        final EditText etCustom = new EditText(this);
        etCustom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etCustom.setHint(de ? "Limit in GB" : "Limit in GB");
        etCustom.setVisibility(android.view.View.GONE);

        java.util.function.BiConsumer<Integer, TextView[]> paint = (sel, views) -> {
            for (int i = 0; i < views.length; i++) {
                views[i].setTextColor(sel == i ? 0xFFFF6B00 : 0xFFE8E2D6);
                views[i].setTypeface(null, sel == i ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        };

        android.widget.FrameLayout pillLimit = makePillSwitch(limitNames, pickedLimit[0], sel -> {
            pickedLimit[0] = sel;
            etCustom.setVisibility(sel == limits.length - 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        root.addView(netLabel(de ? "Wie viel Datenvolumen?" : "How much data?", d));
        root.addView(pillLimit); root.addView(etCustom);

        final int[] periods = {1, 7, 30, -1};
        final String[] periodNames = {de?"Tag":"Day", de?"Woche":"Week", de?"Monat":"Month", de?"Eigenes":"Custom"};
        int pickedPeriod = r.periodDays == 1 ? 0 : (r.periodDays == 30 ? 2 : 1);
        if (pickedPeriod == 1 && r.periodDays != 7) pickedPeriod = 3;
        final int[] selPeriod = {pickedPeriod};
        final EditText etDays = new EditText(this);
        android.widget.FrameLayout pillPeriod = makePillSwitch(periodNames, selPeriod[0], sel -> {
            selPeriod[0] = sel;
            etDays.setVisibility(periods[sel] == -1 ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        etDays.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDays.setHint(de ? "Tage" : "Days");
        etDays.setText(r.periodDays == 7 || pickedPeriod != 3 ? "" : String.valueOf(r.periodDays));
        etDays.setVisibility(pickedPeriod == 3 ? android.view.View.VISIBLE : android.view.View.GONE);
        etDays.setVisibility(periods[selPeriod[0]] == -1 ? android.view.View.VISIBLE : android.view.View.GONE);
        root.addView(netLabel(de ? "Pro Zeitraum" : "Per period", d));
        root.addView(pillPeriod); root.addView(etDays);

        final android.widget.CheckBox cbMobile = new android.widget.CheckBox(this);
        cbMobile.setText("Mobile Data"); cbMobile.setChecked(r.mobile); root.addView(cbMobile);
        final android.widget.CheckBox cbWifi = new android.widget.CheckBox(this);
        cbWifi.setText("WLAN"); cbWifi.setChecked(r.wifi); root.addView(cbWifi);

        final String[] actions = {eu.kodanetwork.mchost.util.NetworkPolicy.ACTION_WARN, eu.kodanetwork.mchost.util.NetworkPolicy.ACTION_STOP, eu.kodanetwork.mchost.util.NetworkPolicy.ACTION_BLOCK};
        final String[] actionNames = {de?"Nur warnen":"Warn only", de?"Server stoppen":"Stop server", de?"Start blockieren":"Block start"};
        final String[] actionDesc = {de?"Zeigt eine Warnung":"Shows a warning", de?"Stoppt den Server":"Stops the server", de?"Kein Start bis frei":"No start until free"};
        int actIdx = java.util.Arrays.asList(actions).indexOf(r.action); if (actIdx < 0) actIdx = 0;
        final int[] selAct = {actIdx};
        android.widget.LinearLayout rowAct = new android.widget.LinearLayout(this);
        TextView[] actViews = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int fi = i;
            TextView chip = new TextView(this);
            chip.setText(actionNames[i] + "\n" + actionDesc[i]); chip.setTextSize(12);
            chip.setPadding((int)(10*d), (int)(8*d), (int)(10*d), (int)(8*d));
            chip.setOnClickListener(v -> { selAct[0] = fi; paint.accept(fi, actViews); });
            actViews[i] = chip; rowAct.addView(chip);
        }
        paint.accept(selAct[0], actViews);
        root.addView(netLabel(de ? "Wenn erreicht" : "When reached", d));
        root.addView(rowAct);

        com.google.android.material.button.MaterialButton save = eu.kodanetwork.mchost.util.KodaButtons.primary(this, de ? "Speichern" : "Save");
        save.setOnClickListener(v -> {
            try {
                if (pickedLimit[0] == limits.length-1) {
                    r.limitBytes = (long)(Float.parseFloat(etCustom.getText().toString()) * 1073741824f);
                } else r.limitBytes = limits[pickedLimit[0]];
                r.periodDays = periods[selPeriod[0]] == -1 ? Math.max(1, Integer.parseInt(etDays.getText().toString().isEmpty() ? "7" : etDays.getText().toString())) : periods[selPeriod[0]];
                r.mobile = cbMobile.isChecked(); r.wifi = cbWifi.isChecked();
                r.action = actions[selAct[0]];
                java.util.List<eu.kodanetwork.mchost.util.NetworkPolicy.Rule> rules =
                        eu.kodanetwork.mchost.util.NetworkPolicy.getRules(this, server.getId());
                rules.removeIf(x -> x.id.equals(r.id)); // same rule = replace, never duplicate
                rules.add(r);
                eu.kodanetwork.mchost.util.NetworkPolicy.saveRules(this, server.getId(), rules);
                sheet.dismiss(); done.run();
            } catch (Exception e) {
                android.widget.Toast.makeText(this, de ? "Ungültige Eingabe" : "Invalid input", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        android.widget.LinearLayout.LayoutParams saveLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.topMargin = (int)(20*d);
        root.addView(save, saveLp);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setFillViewport(true); sc.addView(root);
        sheet.setContentView(sc);
        styleSheetFullscreen(sheet);
        sheet.show();
    }

    /** Segmented sliding-pill switch (like the theme dark/light/auto picker). */
    private android.widget.FrameLayout makePillSwitch(String[] options, int selected, java.util.function.IntConsumer onPick) {
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        final android.view.View[] pill = new android.view.View[1];
        pill[0] = new android.view.View(this);
        android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
        pillBg.setCornerRadius(18 * d); pillBg.setColor(0xFFFF6B00);
        pill[0].setBackground(pillBg);
        TextView[] items = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int fi = i;
            TextView tv = new TextView(this);
            tv.setText(options[i]); tv.setTextSize(11); tv.setGravity(android.view.Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setPadding((int)(6*d), (int)(12*d), (int)(6*d), (int)(12*d));
            tv.setOnClickListener(v -> {
                onPick.accept(fi);
                android.view.View sv = (android.view.View) tv.getParent();
                int left = tv.getLeft();
                pill[0].animate().translationX(left).setDuration(200)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                for (int k = 0; k < items.length; k++)
                    items[k].setTextColor(k == fi ? 0xFF1D1714 : 0xFFE8E2D6);
            });
            items[i] = tv; row.addView(tv, new android.widget.LinearLayout.LayoutParams(0,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        android.view.View track = new android.view.View(this);
        android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
        trackBg.setCornerRadius(18 * d); trackBg.setColor(0xFF26221E);
        track.setBackground(trackBg);
        frame.addView(track, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(pill[0], new android.widget.FrameLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(row, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        // initial pill width/position nach Layout
        frame.post(() -> {
            TextView selTv = items[Math.max(0, Math.min(selected, items.length - 1))];
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) pill[0].getLayoutParams();
            lp.width = selTv.getWidth(); lp.leftMargin = 0;
            pill[0].setLayoutParams(lp);
            pill[0].setTranslationX(selTv.getLeft());
            for (int k = 0; k < items.length; k++)
                items[k].setTextColor(k == selected ? 0xFF1D1714 : 0xFFE8E2D6);
        });
        int h = (int)(44 * d);
        android.widget.LinearLayout.LayoutParams flp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, h);
        flp.topMargin = (int)(6 * d);
        frame.setLayoutParams(flp);
        return frame;
    }

    private void etCustom2Sel(View v, boolean show) {
        v.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private TextView netLabel(String txt, float d) {
        TextView tv = new TextView(this);
        tv.setText(txt); tv.setTextColor(0xFFB7AE9F); tv.setTextSize(11);
        tv.setPadding(0,(int)(14*d),0,(int)(4*d));
        return tv;
    }
}
