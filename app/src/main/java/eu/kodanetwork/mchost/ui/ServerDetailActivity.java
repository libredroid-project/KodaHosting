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
import eu.kodanetwork.mchost.service.TermuxServerService;
import eu.kodanetwork.mchost.util.JavaFinder;

public class ServerDetailActivity extends AppCompatActivity {

    private ServerInstance server;
    private ServerRepo repo;
    private TermuxServerService svc;
    private boolean bound = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable ticker;

    // Tabs
    private TabLayout tabs;
    private View pDash, pConsole, pFiles, pSettings;

    // Dashboard
    private TextView tvBadge, tvUptime, tvPlayers, tvJoinAddr, tvRamInfo, tvVerInfo, tvJavaInfo;
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

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((TermuxServerService.LocalBinder) b).get();
            bound = true;
            svc.addStateCb((id, s) -> {
                if (id.equals(server.getId())) runOnUiThread(() -> updateDash());
            });
            svc.addLogCb((id, line) -> {
                if (id.equals(server.getId())) runOnUiThread(() -> appendLog(line));
            });
            for (String l : svc.getLog(server.getId())) appendLog(l);
            updateDash();
        }
        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            svc = null;
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_server_detail);

        String id = getIntent().getStringExtra("id");
        repo = ServerRepo.get(this);
        server = repo.byId(id);
        if (server == null) { finish(); return; }
        currentDir = new File(server.getServerDir());

        bindViews();
        ((TextView) findViewById(R.id.tv_title)).setText(server.getName());
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        setupTabs();
        setupDashButtons();
        setupConsole();
        setupSettings();

        Intent si = new Intent(this, TermuxServerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
        else startService(si);
        bindService(si, conn, Context.BIND_AUTO_CREATE);
        startTicker();

        if (getIntent().getBooleanExtra("auto_setup", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(this::downloadJar, 500);
        }
    }

    private void bindViews() {
        tabs      = findViewById(R.id.tabs);
        pDash     = findViewById(R.id.panel_dash);
        pConsole  = findViewById(R.id.panel_console);
        pFiles    = findViewById(R.id.panel_files);
        pSettings = findViewById(R.id.panel_settings);

        tvBadge   = findViewById(R.id.tv_badge);
        tvUptime  = findViewById(R.id.tv_uptime);
        tvPlayers = findViewById(R.id.tv_players);
        tvJoinAddr = findViewById(R.id.tv_join_addr);
        tvRamInfo  = findViewById(R.id.tv_ram_info);
        tvVerInfo  = findViewById(R.id.tv_ver_info);
        tvJavaInfo = findViewById(R.id.tv_java_info);
        btnStart   = findViewById(R.id.btn_start);
        btnStop    = findViewById(R.id.btn_stop);
        btnRestart = findViewById(R.id.btn_restart);
        btnKill    = findViewById(R.id.btn_kill);
        dlProgress = findViewById(R.id.dl_progress);
        dot = findViewById(R.id.dot);
        tvDlMsg    = findViewById(R.id.tv_dl_msg);

        tvLog       = findViewById(R.id.tv_log);
        scrollLog   = findViewById(R.id.scroll_log);
        etCmd       = findViewById(R.id.et_cmd);
        layoutChips = findViewById(R.id.layout_chips);

        layoutFileList = findViewById(R.id.layout_files);
        tvFilesRoot    = findViewById(R.id.tv_files_root);

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

        // Static info
        tvJoinAddr.setText(server.getJoinAddress());
        tvRamInfo.setText(server.getRamMB() + " MB RAM");
        tvVerInfo.setText(server.getType().name() + " " + server.getVersion());

        // Java check — uses exec() to actually test java, runs in background
        tvJavaInfo.setText("checking java…");
        tvJavaInfo.setTextColor(Color.parseColor("#888888"));
        new Thread(() -> {
            String javaPath = JavaFinder.find(this);
            runOnUiThread(() -> {
                if (javaPath != null) {
                    tvJavaInfo.setText("✓ " + JavaFinder.version(this));
                    tvJavaInfo.setTextColor(Color.parseColor("#00E676"));
                } else {
                    if (TermuxBridge.isTermuxInstalled(this)) {
                        tvJavaInfo.setText("◌ Java wird beim Start automatisch (OpenJDK 17) installiert");
                        tvJavaInfo.setTextColor(Color.parseColor("#FFCC00"));
                    } else {
                        tvJavaInfo.setText("✗ kein Java / kein Termux. Installiere Termux (F-Droid).");
                        tvJavaInfo.setTextColor(Color.parseColor("#FF4444"));
                    }
                }
            });
        }).start();
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private void setupTabs() {
        tabs.addTab(tabs.newTab().setText("Dashboard"));
        tabs.addTab(tabs.newTab().setText("Console"));
        tabs.addTab(tabs.newTab().setText("Files"));
        tabs.addTab(tabs.newTab().setText("Settings"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t)   { showTab(t.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });
        showTab(0);
    }

    private void showTab(int i) {
        pDash    .setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        pConsole .setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        pFiles   .setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        pSettings.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
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
            
            File[] jars = serverDir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars == null || jars.length == 0) {
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "No .jar file found in " + serverDir.getAbsolutePath());
                Toast.makeText(this, "Bitte zuerst die Server .jar herunterladen (Settings-Tab)", Toast.LENGTH_LONG).show();
                tabs.selectTab(tabs.getTabAt(3));
                return;
            }
            eu.kodanetwork.mchost.util.AppLogger.log("UI", "Found jar: " + jars[0].getName() + ". Binding and starting service...");
            autoStartAll();
        });
        btnStop   .setOnClickListener(v -> sendAction(TermuxServerService.ACTION_STOP));
        btnRestart.setOnClickListener(v -> sendAction(TermuxServerService.ACTION_RESTART));
        btnKill   .setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Force Kill?")
                .setMessage("Welt-Daten werden möglicherweise nicht gespeichert. Fortfahren?")
                .setPositiveButton("Kill", (d, w) -> sendAction(TermuxServerService.ACTION_KILL))
                .setNegativeButton("Abbrechen", null)
                .show());
    }

    private void autoStartAll() {
        io.execute(() -> {
            StartOrchestrator orchestrator = new StartOrchestrator(this, new StartOrchestrator.Callback() {
                @Override
                public void onStep(StartOrchestrator.Step step, String message) {
                    runOnUiThread(() -> {
                        switch (step) {
                            case PREPARE:
                            case JAVA_DOWNLOAD:
                            case JAVA_INSTALL:
                                tvTermuxStatus.setText("Termux: " + message);
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
                    runOnUiThread(() -> sendAction(TermuxServerService.ACTION_START));
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
        Intent i = new Intent(this, TermuxServerService.class);
        i.setAction(action);
        i.putExtra(TermuxServerService.EXTRA_ID, server.getId());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void updateDash() {
        ServerInstance.State st = server.state;
        String label; int col;
        switch (st) {
            case ONLINE:     label = "● ONLINE";     col = 0xFF00E676; break;
            case STARTING:   label = "◌ STARTING…";  col = 0xFFFFCC00; break;
            case STOPPING:   label = "◌ STOPPING…";  col = 0xFFFF8800; break;
            case CRASHED:    label = "✕ CRASHED";    col = 0xFFFF4444; break;
            case INSTALLING: label = "⬇ LADEN…";     col = 0xFF44AAFF; break;
            default:         label = "○ OFFLINE";    col = 0xFF888888; break;
        }
        tvBadge.setText(label);
        tvBadge.setTextColor(col);
        tvPlayers.setText(server.onlinePlayers + " / " + server.getMaxPlayers() + " Spieler");

        boolean running = server.isRunning();
        btnStart  .setEnabled(!running && st != ServerInstance.State.INSTALLING);
        btnStop   .setEnabled(running);
        btnRestart.setEnabled(running);
        btnKill   .setEnabled(running);
        // Update dot in header
        if (dot != null) {
            int dotDrw;
            switch (st) {
                case ONLINE:   dotDrw = R.drawable.dot_online;  break;
                case STARTING:
                case STOPPING:
                case INSTALLING: dotDrw = R.drawable.dot_warn; break;
                case CRASHED:  dotDrw = R.drawable.dot_err;    break;
                default:       dotDrw = R.drawable.dot_offline; break;
            }
            dot.setBackground(getDrawable(dotDrw));
        }
    }

    // ── Console ───────────────────────────────────────────────────────────────

    private void setupConsole() {
        MaterialButton btnSend  = findViewById(R.id.btn_send);
        MaterialButton btnClear = findViewById(R.id.btn_clear);
        MaterialButton btnCopy  = findViewById(R.id.btn_copy_log);
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
            "list", "tps", "say Hallo!", "time set day", "weather clear",
            "op <Spieler>", "save-all", "whitelist list", "gamemode creative @a"
        };
        for (String c : cmds) {
            Chip chip = new Chip(this);
            chip.setText(c);
            chip.setTextColor(0xFFFF6B00);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFF101010));
            chip.setOnClickListener(v -> etCmd.setText(c));
            layoutChips.addView(chip);
        }
    }

    private void sendCmd() {
        if (etCmd.getText() == null) return;
        String cmd = etCmd.getText().toString().trim();
        if (cmd.isEmpty()) return;
        if (bound && svc != null) svc.sendCmd(server.getId(), cmd);
        else appendLog("Service nicht verbunden.");
        etCmd.setText("");
    }

    private void appendLog(String raw) {
        if (raw == null || raw.isEmpty()) return;
        
        // Strip ANSI escape codes (e.g., [33;1m)
        raw = raw.replaceAll("\\\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        // Also strip the [m codes and similar
        raw = raw.replaceAll("\\[[0-9;]*m", "");

        int col;
        if (raw.contains("ERROR") || raw.contains("Exception"))   col = Color.parseColor("#FF5555");
        else if (raw.contains("WARN"))                            col = Color.parseColor("#FFCC00");
        else if (raw.startsWith(">") || raw.contains("KodaNet"))  col = Color.parseColor("#FF6B00");
        else if (raw.contains("Done (") || raw.contains("✓"))    col = Color.parseColor("#00E676");
        else if (raw.startsWith("  ─") || raw.startsWith("  🍊")) col = Color.parseColor("#FF8C42");
        else                                                       col = Color.parseColor("#CCCCCC");

        SpannableString sp = new SpannableString(raw + "\n");
        sp.setSpan(new ForegroundColorSpan(col), 0, sp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLog.append(sp);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    // ── Files ─────────────────────────────────────────────────────────────────

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
            String size = f.isFile() ? " (" + f.length() / 1024 + " KB)" : "";
            addFRow((f.isDirectory() ? "📁 " : fileIcon(f)) + f.getName() + size, f);
        }
    }

    private void addFRow(String text, File file) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(16, 24, 16, 24); // Taller rows for touch
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(text.contains("📁") || text.startsWith("..") ? 0xFFFF6B00 : 0xFFCCCCCC);
        
        if (file != null) {
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDir = file;
                    refreshFiles();
                } else {
                    openFileEditor(file);
                }
            });
        }

        layoutFileList.addView(tv);
        View div = new View(this);
        div.setBackgroundColor(0xFF222222);
        div.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        layoutFileList.addView(div);
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
        tvSettingsInfo.setText(
            "Name:       " + server.getName() + "\n" +
            "Typ:        " + server.getType().name() + " " + server.getVersion() + "\n" +
            "RAM:        " + server.getRamMB() + " MB\n" +
            "Port:       " + server.getPort() + "\n" +
            "Beitritts-Adresse:\n" + server.getJoinAddress() + "\n\n" +
            "Dateipfad:\n" + server.getServerDir()
        );
        etDomainPrefix.setText(server.getSubdomain());
        updateIntegrationStatus();

        btnDlJar.setOnClickListener(v -> downloadJar());
        btnTermuxSetup.setOnClickListener(v -> runTermuxSetup());
        btnStartTunnel.setOnClickListener(v -> startTunnel());
        btnLinkDomain.setOnClickListener(v -> linkDomain());
        btnDelServer.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("\"" + server.getName() + "\" löschen?")
                .setMessage("Entfernt den Eintrag. Dateien auf dem Gerät werden NICHT gelöscht.")
                .setPositiveButton("Löschen", (d, w) -> { repo.delete(server.getId()); finish(); })
                .setNegativeButton("Abbrechen", null)
                .show());
    }

    private void updateIntegrationStatus() {
        boolean termuxInstalled = TermuxBridge.isTermuxInstalled(this);
        tvTermuxStatus.setText(termuxInstalled
            ? "Termux: installed"
            : "Termux: missing (install from F-Droid)");
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

    private void runTermuxSetup() {
        io.execute(() -> {
            try {
                java.io.File setup = eu.kodanetwork.mchost.integration.TermuxScriptInstaller.ensureSetupScript(this);
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Triggering Termux Setup via script: " + setup.getAbsolutePath());
                boolean started = eu.kodanetwork.mchost.integration.TermuxBridge.runScript(this, setup);
                eu.kodanetwork.mchost.util.JavaFinder.clearCache();
                runOnUiThread(() -> {
                    if (started) {
                        tvTermuxStatus.setText("Termux: setup script started");
                        Toast.makeText(this, "Termux Setup läuft! Bitte warte, bis es in Termux FERTIG ist, bevor du START drückst.", Toast.LENGTH_LONG).show();
                    } else {
                        tvTermuxStatus.setText("Termux: setup failed to start");
                        Toast.makeText(this, "Konnte Termux nicht erreichen!", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                eu.kodanetwork.mchost.util.AppLogger.log("UI", "Setup Error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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

    private void linkDomain() {
        String host = etDomainPrefix.getText() == null ? "" : etDomainPrefix.getText().toString().trim();
        if (host.isEmpty()) {
            etDomainPrefix.setError("Required");
            return;
        }
        if (server.getPlayitAddress().isEmpty()) {
            Toast.makeText(this, "Start playit tunnel first", Toast.LENGTH_LONG).show();
            return;
        }
        io.execute(() -> {
            try {
                SupabaseFunctionsClient client = new SupabaseFunctionsClient(this);
                String[] parts = server.getPlayitAddress().split(":");
                String target = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : server.getPort();
                
                client.createDnsLink("", host, target, port);
                
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

    private void downloadJar() {
        server.state = ServerInstance.State.INSTALLING;
        updateDash();
        dlProgress.setVisibility(View.VISIBLE);
        tvDlMsg.setVisibility(View.VISIBLE);
        btnDlJar.setEnabled(false);
        tabs.selectTab(tabs.getTabAt(1)); // Console zeigen

        new JarDownloader().download(server, new JarDownloader.Cb() {
            @Override public void onProgress(int p, String m) {
                tvDlMsg.setText(m);
                appendLog("[Download] " + m);
            }
            @Override public void onDone(File f) {
                dlProgress.setVisibility(View.GONE);
                tvDlMsg.setVisibility(View.GONE);
                btnDlJar.setEnabled(true);
                server.state = ServerInstance.State.OFFLINE;
                updateDash();
                appendLog("[KodaNetwork] ✓ Server .jar heruntergeladen! Jetzt START drücken.");
                Toast.makeText(ServerDetailActivity.this, "✓ Download abgeschlossen!", Toast.LENGTH_SHORT).show();
                if (getIntent().getBooleanExtra("auto_setup", false)) {
                    getIntent().removeExtra("auto_setup");
                    runTermuxSetup();
                }
            }
            @Override public void onError(String e) {
                dlProgress.setVisibility(View.GONE);
                tvDlMsg.setVisibility(View.GONE);
                btnDlJar.setEnabled(true);
                server.state = ServerInstance.State.OFFLINE;
                updateDash();
                new AlertDialog.Builder(ServerDetailActivity.this)
                    .setTitle("Download fehlgeschlagen").setMessage(e)
                    .setPositiveButton("OK", null).show();
            }
        });
    }

    // ── Uptime Ticker ─────────────────────────────────────────────────────────

    private void startTicker() {
        ticker = new Runnable() {
            @Override public void run() {
                if (tvUptime != null) tvUptime.setText(server.getFormattedUptime());
                h.postDelayed(this, 1000);
            }
        };
        h.post(ticker);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(ticker);
        io.shutdownNow();
        if (bound) unbindService(conn);
    }
}
 