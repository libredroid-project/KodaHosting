package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.UUID;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;
import eu.kodanetwork.mchost.util.HapticUtil;

public class CreateDatabaseActivity extends AppCompatActivity {

    private EditText etName, etPort, etUsername, etPassword;
    private RadioGroup rgEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_database);
        
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);
        
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                );
            }

            findViewById(R.id.root_layout).setOnApplyWindowInsetsListener((v, insets) -> {
                android.view.View topBar = findViewById(R.id.top_bar_container);
                if (topBar != null) topBar.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
                
                android.view.View scroll = findViewById(R.id.main_scroll);
                if (scroll != null) scroll.setPadding(scroll.getPaddingLeft(), 0, scroll.getPaddingRight(), insets.getSystemWindowInsetBottom() + 120);
                
                return insets.consumeSystemWindowInsets();
            });
            
            if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
                findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
                findViewById(R.id.top_bar_container).setBackgroundColor(0xFFFFFFFF);
            }

            // Handle ThemeHelper overriding it later
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
                getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            }, 500);
        }

        etName = findViewById(R.id.et_name);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        rgEngine = findViewById(R.id.rg_engine);
        Button btnCreate = findViewById(R.id.btn_create);
        
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Auto-assign default port on load
        etPort.setText(String.valueOf(findFreePort(3306)));

        rgEngine.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_redis) {
                etPort.setHint("Port (default: 6379)");
                etUsername.setEnabled(false);
                etPort.setText(String.valueOf(findFreePort(6379)));
            } else {
                etPort.setHint("Port (default: 3306)");
                etUsername.setEnabled(true);
                etPort.setText(String.valueOf(findFreePort(3306)));
            }
        });

        btnCreate.setOnClickListener(v -> {
            HapticUtil.forceVibrate(this, 50);
            createDatabase();
        });
    }

    private void createDatabase() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isRedis = rgEngine.getCheckedRadioButtonId() == R.id.rb_redis;
        ServerInstance.Type type = isRedis ? ServerInstance.Type.REDIS : ServerInstance.Type.MARIADB;

        int port;
        try {
            String pStr = etPort.getText().toString();
            port = pStr.isEmpty() ? (isRedis ? 6379 : 3306) : Integer.parseInt(pStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = etUsername.getText().toString().trim();
        if (username.isEmpty()) username = "admin";

        String password = etPassword.getText().toString().trim();
        if (password.isEmpty()) password = "password";

        ServerInstance s = new ServerInstance();
        s.setId(UUID.randomUUID().toString());
        s.setName(name);
        // We reuse serverDir as dbDir
        s.setServerDir(new File(getFilesDir(), "dbs/" + s.getId()).getAbsolutePath());
        
        // These fields are repurposed for Databases
        s.setType(type);
        s.setPort(port);
        s.setRamMB(512); // Default
        s.setDbUsername(username);
        s.setDbPassword(password);
        s.setUseNative(true);

        ServerRepo.get(this).add(s);
        Toast.makeText(this, "Database created!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int findFreePort(int startPort) {
        int port = startPort;
        java.util.List<ServerInstance> all = ServerRepo.get(this).all();
        while (true) {
            boolean used = false;
            for (ServerInstance s : all) {
                if (s.getPort() == port || s.getBedrockPort() == port) {
                    used = true;
                    break;
                }
            }
            if (!used) return port;
            port++;
        }
    }
}
