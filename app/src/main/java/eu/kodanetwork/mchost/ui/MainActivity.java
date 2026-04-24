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
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.service.TermuxServerService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "KodaNetwork";

    private RecyclerView rv;
    private View tvEmpty;          // LinearLayout in XML — use View, not TextView
    private ServerCardAdapter adapter;
    private ServerRepo repo;
    private TermuxServerService svc;
    private boolean bound = false;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            try {
                svc = ((TermuxServerService.LocalBinder) b).get();
                bound = true;
                adapter.setService(svc);
                svc.addStateCb((id, state) -> runOnUiThread(() -> adapter.notifyDataSetChanged()));
                refresh();
                Log.d(TAG, "Service connected");
            } catch (Exception e) {
                Log.e(TAG, "onServiceConnected error: " + e.getMessage(), e);
            }
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

        try {
            setContentView(R.layout.activity_main);

            repo    = ServerRepo.get(this);
            rv      = findViewById(R.id.rv);
            tvEmpty = findViewById(R.id.tv_empty);

            adapter = new ServerCardAdapter(this, this::openServer);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(adapter);

            ExtendedFloatingActionButton fab = findViewById(R.id.fab_add);
            fab.setOnClickListener(v ->
                startActivity(new Intent(this, CreateServerActivity.class)));

            View btnDebug = findViewById(R.id.btn_debug_log);
            if (btnDebug != null) {
                btnDebug.setOnClickListener(v -> 
                    startActivity(new Intent(this, DebugLogActivity.class)));
            }

            repo.addListener(this::refresh);
            requestPerms();
            startAndBind();
            refresh();

            Log.d(TAG, "MainActivity created successfully");

        } catch (Exception e) {
            Log.e(TAG, "CRASH in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Fehler beim Starten: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openServer(ServerInstance s) {
        Intent i = new Intent(this, ServerDetailActivity.class);
        i.putExtra("id", s.getId());
        startActivity(i);
    }

    private void refresh() {
        runOnUiThread(() -> {
            try {
                java.util.List<eu.kodanetwork.mchost.model.ServerInstance> list = repo.all();
                adapter.setData(list);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                }
                // Update server count in header
                android.widget.TextView tvCount = findViewById(R.id.tv_server_count);
                if (tvCount != null) tvCount.setText(list.size() + " SERVER" + (list.size() != 1 ? "S" : ""));
            } catch (Exception e) {
                Log.e(TAG, "refresh error: " + e.getMessage(), e);
            }
        });
    }

    private void startAndBind() {
        try {
            Intent si = new Intent(this, TermuxServerService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
            else startService(si);
            bindService(si, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "startAndBind error: " + e.getMessage(), e);
        }
    }

    private void requestPerms() {
        try {
            permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), r -> {});
            storageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), r -> {});

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && !Environment.isExternalStorageManager()) {
                try {
                    Intent i = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    storageLauncher.launch(i);
                } catch (Exception e) {
                    // Some devices don't support this intent — ignore
                    Log.w(TAG, "Storage permission intent not supported: " + e.getMessage());
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                permLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                });
            }

            java.util.List<String> permsToRequest = new java.util.ArrayList<>();
            
            if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            
            if (ContextCompat.checkSelfPermission(this, "com.termux.permission.RUN_COMMAND") 
                    != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add("com.termux.permission.RUN_COMMAND");
            }
            
            if (!permsToRequest.isEmpty()) {
                permLauncher.launch(permsToRequest.toArray(new String[0]));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "requestPerms error: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            repo.removeListener(this::refresh);
            if (bound) unbindService(conn);
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error: " + e.getMessage(), e);
        }
    }
}
