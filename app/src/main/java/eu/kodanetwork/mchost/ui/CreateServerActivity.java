package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;

public class CreateServerActivity extends AppCompatActivity {

    // Static versions for non-Paper types
    private static final String[] FABRIC_VERSIONS  = {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1","1.19.4"};
    private static final String[] VANILLA_VERSIONS = {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1","1.19.4","1.18.2","1.17.1","1.16.5"};
    private static final String[] PURPUR_VERSIONS  = {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1","1.19.4"};

    private static final int[] RAM_STEPS = {512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192};

    // Server type buttons
    private static final String[] TYPE_NAMES = {"Paper", "Purpur", "Fabric", "Vanilla"};
    private static final ServerInstance.Type[] TYPE_VALS = {
        ServerInstance.Type.PAPER, ServerInstance.Type.PURPUR,
        ServerInstance.Type.FABRIC, ServerInstance.Type.VANILLA
    };

    private EditText etName, etPort;
    private Spinner spinVersion;
    private SeekBar seekRam;
    private TextView tvRamValue, tvAddressPreview, tvVersionLoading;
    private LinearLayout layoutTypeBtns;
    private MaterialButton btnCreate;
    private ImageButton btnBack;

    private int ramMB = 1024;
    private int selectedTypeIndex = 0; // Paper by default
    private final MaterialButton[] typeBtns = new MaterialButton[4];

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_server);

        etName          = findViewById(R.id.et_name);
        etPort          = findViewById(R.id.et_port);
        spinVersion     = findViewById(R.id.spin_version);
        seekRam         = findViewById(R.id.seek_ram);
        tvRamValue      = findViewById(R.id.tv_ram_value);
        tvAddressPreview= findViewById(R.id.tv_address_preview);
        tvVersionLoading= findViewById(R.id.tv_version_loading);
        layoutTypeBtns  = findViewById(R.id.layout_type_btns);
        btnCreate       = findViewById(R.id.btn_create);
        btnBack         = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        buildTypeButtons();
        setupRam();
        setupNameWatcher();
        loadVersionsForType(0); // load Paper versions from API on start

        btnCreate.setOnClickListener(v -> createServer());
    }

    // ── Type buttons ──────────────────────────────────────────────────────────

    private void buildTypeButtons() {
        layoutTypeBtns.removeAllViews();
        for (int i = 0; i < TYPE_NAMES.length; i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(TYPE_NAMES[i]);
            btn.setTextSize(11);
            btn.setTypeface(android.graphics.Typeface.MONOSPACE);
            btn.setLetterSpacing(0.05f);
            btn.setPadding(16, 8, 16, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i < TYPE_NAMES.length - 1 ? 6 : 0);
            btn.setLayoutParams(lp);
            styleTypeBtn(btn, i == 0);
            btn.setOnClickListener(v -> selectType(idx));
            typeBtns[i] = btn;
            layoutTypeBtns.addView(btn);
        }
    }

    private void styleTypeBtn(MaterialButton btn, boolean active) {
        if (active) {
            btn.setBackgroundColor(0xFFFF6B00);
            btn.setTextColor(0xFF000000);
        } else {
            btn.setBackgroundColor(0xFF111111);
            btn.setTextColor(0xFF444444);
        }
    }

    private void selectType(int idx) {
        selectedTypeIndex = idx;
        for (int i = 0; i < typeBtns.length; i++) {
            styleTypeBtn(typeBtns[i], i == idx);
        }
        loadVersionsForType(idx);
    }

    // ── Version loading ───────────────────────────────────────────────────────

    private void loadVersionsForType(int typeIdx) {
        ServerInstance.Type type = TYPE_VALS[typeIdx];
        tvVersionLoading.setText("loading…");
        tvVersionLoading.setVisibility(View.VISIBLE);
        spinVersion.setEnabled(false);

        if (type == ServerInstance.Type.PAPER) {
            // Load from PaperMC API
            executor.submit(() -> {
                List<String> versions = fetchPaperVersions();
                mainHandler.post(() -> {
                    tvVersionLoading.setVisibility(View.GONE);
                    spinVersion.setEnabled(true);
                    if (versions.isEmpty()) {
                        // Fallback
                        versions.add("1.21.4"); versions.add("1.21.3");
                        versions.add("1.20.6"); versions.add("1.20.4"); versions.add("1.20.1");
                        tvVersionLoading.setText("(offline fallback)");
                        tvVersionLoading.setVisibility(View.VISIBLE);
                    } else {
                        tvVersionLoading.setText(versions.size() + " versions");
                        tvVersionLoading.setVisibility(View.VISIBLE);
                    }
                    setVersions(versions);
                });
            });
        } else if (type == ServerInstance.Type.PURPUR) {
            executor.submit(() -> {
                List<String> versions = fetchPurpurVersions();
                mainHandler.post(() -> {
                    spinVersion.setEnabled(true);
                    tvVersionLoading.setText(versions.size() + " versions");
                    setVersions(versions.isEmpty() ? listOf(PURPUR_VERSIONS) : versions);
                });
            });
        } else if (type == ServerInstance.Type.FABRIC) {
            mainHandler.post(() -> {
                spinVersion.setEnabled(true);
                tvVersionLoading.setText("stable releases");
                setVersions(listOf(FABRIC_VERSIONS));
            });
        } else {
            mainHandler.post(() -> {
                spinVersion.setEnabled(true);
                tvVersionLoading.setText("releases");
                setVersions(listOf(VANILLA_VERSIONS));
            });
        }
    }

    private List<String> fetchPaperVersions() {
        try {
            String json = get("https://api.papermc.io/v2/projects/paper");
            // Parse "versions":["1.8","1.8.1",...,"1.21.4"]
            Pattern p = Pattern.compile("\"versions\":\\[([^\\]]+)\\]");
            Matcher m = p.matcher(json);
            if (m.find()) {
                String raw = m.group(1);
                List<String> result = new ArrayList<>();
                for (String v : raw.split(",")) {
                    String ver = v.trim().replace("\"", "");
                    if (!ver.isEmpty()) result.add(ver);
                }
                Collections.reverse(result); // newest first
                return result;
            }
        } catch (Exception e) {
            android.util.Log.e("KodaVersion", "Paper API error: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private List<String> fetchPurpurVersions() {
        try {
            String json = get("https://api.purpurmc.org/v2/purpur");
            Pattern p = Pattern.compile("\"versions\":\\[([^\\]]+)\\]");
            Matcher m = p.matcher(json);
            if (m.find()) {
                String raw = m.group(1);
                List<String> result = new ArrayList<>();
                for (String v : raw.split(",")) {
                    String ver = v.trim().replace("\"", "");
                    if (!ver.isEmpty()) result.add(ver);
                }
                Collections.reverse(result);
                return result;
            }
        } catch (Exception e) {
            android.util.Log.e("KodaVersion", "Purpur API error: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private String get(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "KodaNetwork/3.0");
        c.connect();
        try (InputStream is = c.getInputStream()) {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private void setVersions(List<String> versions) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, versions);
        spinVersion.setAdapter(adapter);
    }

    private List<String> listOf(String[] arr) {
        List<String> l = new ArrayList<>();
        for (String s : arr) l.add(s);
        return l;
    }

    // ── RAM slider ────────────────────────────────────────────────────────────

    private void setupRam() {
        seekRam.setMax(RAM_STEPS.length - 1);
        seekRam.setProgress(2); // 1024MB default
        updateRam(RAM_STEPS[2]);
        seekRam.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { updateRam(RAM_STEPS[p]); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void updateRam(int mb) {
        ramMB = mb;
        String label = mb >= 1024 ? (mb / 1024) + "GB" : mb + "MB";
        tvRamValue.setText(label);
    }

    // ── Name watcher → address preview ────────────────────────────────────────

    private void setupNameWatcher() {
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){ updatePreview(); }
        };
        etName.addTextChangedListener(w);
        etPort.addTextChangedListener(w);
        updatePreview();
    }

    private void updatePreview() {
        String name = etName.getText() != null ? etName.getText().toString() : "";
        String port = etPort.getText() != null ? etPort.getText().toString() : "";
        String sub  = toSubdomain(name);
        if (sub.isEmpty()) sub = "yourserver";
        String portSuffix = (port.isEmpty() || port.equals("25565")) ? "" : ":" + port;
        tvAddressPreview.setText(sub + ".kodanetwork.eu" + portSuffix);
    }

    private String toSubdomain(String name) {
        return name.toLowerCase().trim()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    // ── Create ────────────────────────────────────────────────────────────────

    private void createServer() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) { etName.setError("Required"); return; }

        // Updated for user's VPS Range: 30000 - 40000
        int port = 30000 + new java.util.Random().nextInt(10000);

        String version = spinVersion.getSelectedItem() != null
            ? spinVersion.getSelectedItem().toString() : "1.21.4";

        ServerInstance.Type type = TYPE_VALS[selectedTypeIndex];
        String id  = UUID.randomUUID().toString();
        String dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS) + "/KodaNetwork/servers/" + id;

        ServerInstance s = new ServerInstance(id, name, type, version, ramMB, port, dir);

        ServerRepo.get(this).add(s);

        Toast.makeText(this, "Server \"" + name + "\" wird vorbereitet...", Toast.LENGTH_SHORT).show();

        android.content.Intent i = new android.content.Intent(this, ServerDetailActivity.class);
        i.putExtra("id", id);
        i.putExtra("auto_setup", true);
        startActivity(i);

        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
