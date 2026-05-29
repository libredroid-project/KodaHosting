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
import eu.kodanetwork.mchost.service.TermuxServerService;
import eu.kodanetwork.mchost.util.LocaleHelper;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "KodaNetwork";

    private RecyclerView rv;
    private View tvEmpty;
    private ServerCardAdapter adapter;
    private ServerRepo repo;
    private TermuxServerService svc;
    private boolean bound = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((TermuxServerService.LocalBinder) b).get();
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

        setContentView(R.layout.activity_main);

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
                fab.setText(LocaleHelper.t(this, "[+] NEW SERVER", "[+] NEUER SERVER"));
                fab.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    startActivity(new Intent(this, CreateServerActivity.class));
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
                Intent startSvc = new Intent(this, TermuxServerService.class);
                startSvc.setAction(TermuxServerService.ACTION_START);
                startSvc.putExtra("id", autoStartId);
                startService(startSvc);
            }

            ThemeHelper.apply(this);
            eu.kodanetwork.mchost.util.HapticUtil.applyHaptics(this);
            Log.d(TAG, "MainActivity created");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
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
                    String label = LocaleHelper.t(this, " SERVER", " SERVER");
                    if (list.size() != 1 && " SERVER".equals(label)) label = " SERVERS";
                    tvCount.setText(list.size() + label);
                }
            } catch (Exception ignored) {}
        });
    }

    private void startAndBind() {
        try {
            Intent si = new Intent(this, TermuxServerService.class);
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
