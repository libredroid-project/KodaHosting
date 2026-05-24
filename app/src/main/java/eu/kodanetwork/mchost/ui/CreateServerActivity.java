package eu.kodanetwork.mchost.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.model.ServerRepo;

public class CreateServerActivity extends AppCompatActivity {

    private static final String[] FABRIC_VERSIONS  = {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1","1.19.4"};
    private static final String[] VANILLA_VERSIONS = {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1","1.19.4","1.18.2","1.17.1","1.16.5"};
    private static final String[] FORGE_VERSIONS   = {"1.21.1","1.20.1","1.19.2","1.18.2","1.16.5","1.12.2"};
    private static final String[] NEOFORGE_VERSIONS= {"1.21.4","1.21.3","1.21.1","1.20.6","1.20.4","1.20.1"};
    private static final String[] VELOCITY_VERSIONS = {"3.4.0","3.3.0","3.2.0","3.1.2","3.1.1","3.1.0"};

    private static final int[] RAM_STEPS = {512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192};
    private static final String[] TYPE_NAMES = {"Paper", "Purpur", "Folia", "Forge", "Fabric", "Vanilla", "NeoForge", "Velocity"};
    private static final ServerInstance.Type[] TYPE_VALS = {
        ServerInstance.Type.PAPER, ServerInstance.Type.PURPUR, ServerInstance.Type.FOLIA,
        ServerInstance.Type.FORGE, ServerInstance.Type.FABRIC, ServerInstance.Type.VANILLA,
        ServerInstance.Type.NEOFORGE, ServerInstance.Type.VELOCITY
    };
    // Types that support Auto Design / KodaHosting Setup
    private static final java.util.Set<ServerInstance.Type> AUTO_DESIGN_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
        ServerInstance.Type.PAPER, ServerInstance.Type.PURPUR, ServerInstance.Type.FOLIA
    ));

    private EditText etName;
    private SeekBar seekRam;
    private TextView tvRamValue, tvAddressPreview, tvVersionLoading;
    private TextView tvVersionSelected;
    private String selectedVersion = "1.21.4";
    private List<String> currentVersions = new ArrayList<>();
    private LinearLayout layoutThemeColor;
    private android.widget.NumberPicker npServerType;
    private View layoutNameSection, layoutTypeSection, layoutVersionSection, layoutSetupSection;
    private MaterialButton btnCreate, btnImport, btnImportZip;
    private ImageButton btnBack;
    private com.google.android.material.switchmaterial.SwitchMaterial swUseNative;
    private android.widget.RadioGroup rgSetupType;
    private String selectedColor = "#FF6B00";
    private android.net.Uri sourceUri = null;
    private boolean isZipImport = false;

    private int ramMB = 1024;
    private int selectedTypeIndex = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<android.net.Uri> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    isZipImport = false;
                    handleImportUri(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> zipLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    isZipImport = true;
                    handleImportZipUri(uri);
                }
            }
    );

    private String lastTheme = "modern";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences prefs = getSharedPreferences("koda_settings", MODE_PRIVATE);
        lastTheme = prefs.getString("app_theme", "modern");
        boolean isCyber = "cyber".equals(lastTheme);

        setContentView(R.layout.activity_create_server);
        if (isCyber) {
            findViewById(android.R.id.content).getRootView().setBackgroundResource(R.drawable.bg_cyber_grid);
        }

        // Apply light mode background early
        if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
            findViewById(android.R.id.content).setBackgroundColor(0xFFF5F5F5);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().setStatusBarColor(0xFFF5F5F5);
                getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                getWindow().setNavigationBarColor(0xFFF5F5F5);
            }
        }
        
        lastThemeMode = prefs.getString("theme_mode", "dark");

        etName          = findViewById(R.id.et_name);
        seekRam         = findViewById(R.id.seek_ram);
        tvRamValue      = findViewById(R.id.tv_ram_value);
        tvAddressPreview= findViewById(R.id.tv_address_preview);
        tvVersionLoading= findViewById(R.id.tv_version_loading);
        tvVersionSelected = findViewById(R.id.tv_version_selected);
        // Setup version picker click
        View layoutVersionPicker = findViewById(R.id.layout_version_picker);
        if (layoutVersionPicker != null) layoutVersionPicker.setOnClickListener(v -> showVersionPicker());
        npServerType    = findViewById(R.id.np_server_type);
        btnCreate       = findViewById(R.id.btn_create);
        btnImport       = findViewById(R.id.btn_import);
        btnImportZip    = findViewById(R.id.btn_import_zip);
        btnBack         = findViewById(R.id.btn_back);
        swUseNative     = findViewById(R.id.sw_use_native);
        rgSetupType     = findViewById(R.id.rg_setup_type);
        layoutThemeColor= findViewById(R.id.layout_theme_color);

        layoutNameSection    = findViewById(R.id.layout_name_section);
        layoutTypeSection    = findViewById(R.id.layout_type_section);
        layoutVersionSection = findViewById(R.id.layout_version_section);
        layoutSetupSection   = findViewById(R.id.layout_setup_section);

        btnBack.setOnClickListener(v -> finish());
        btnImport.setOnClickListener(v -> importLauncher.launch(null));
        btnImportZip.setOnClickListener(v -> zipLauncher.launch(new String[]{"application/zip"}));
        
        boolean devTermux = getSharedPreferences("koda_settings", MODE_PRIVATE).getBoolean("dev_termux_fallback", false);
        if (!devTermux) {
            swUseNative.setVisibility(View.GONE);
            swUseNative.setChecked(true); // force native
        }
        
        setupNumberPicker();
        setupRam();
        
        setupNameWatcher();
        setupThemeColors();
        
        rgSetupType.setOnCheckedChangeListener((g, id) -> {
            layoutThemeColor.setVisibility(id == R.id.rb_setup_koda ? View.VISIBLE : View.GONE);
        });

        loadVersionsForType(0);
        btnCreate.setOnClickListener(v -> createServer());
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this, selectedColor);
        
        // Start Java extraction with animation if missing
        File javaBin = new File(getFilesDir(), "jre25/bin/java");
        if (!javaBin.exists()) {
            View overlay = findViewById(R.id.layout_java_extract);
            TextView tvMsg = findViewById(R.id.tv_extract_msg);
            if (overlay != null) {
                overlay.setAlpha(0f);
                overlay.setVisibility(View.VISIBLE);
                overlay.animate().alpha(1f).setDuration(400).start();
            }
            
            new Thread(() -> {
                try {
                    eu.kodanetwork.mchost.orchestration.StartOrchestrator.extractJavaIfMissing(CreateServerActivity.this, new eu.kodanetwork.mchost.orchestration.StartOrchestrator.Callback() {
                        @Override
                        public void onStep(eu.kodanetwork.mchost.orchestration.StartOrchestrator.Step step, String message) {
                            runOnUiThread(() -> {
                                if (tvMsg != null) tvMsg.setText(message);
                            });
                        }
                        @Override public void onCompleted(String playitAddress, String domainLink) {}
                        @Override public void requestServerStartIntent() {}
                        @Override
                        public void onError(eu.kodanetwork.mchost.orchestration.StartOrchestrator.Step step, String message) {
                            runOnUiThread(() -> {
                                if (tvMsg != null) tvMsg.setText("Error: " + message);
                            });
                        }
                    });
                } catch (Exception e) {
                    eu.kodanetwork.mchost.util.AppLogger.log("CreateServer", "Pre-extraction failed: " + e.getMessage());
                } finally {
                    runOnUiThread(() -> {
                        if (overlay != null) {
                            overlay.animate().alpha(0f).setDuration(400).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
                        }
                    });
                }
            }).start();
        }
    }

    private void enterImportMode() {
        layoutTypeSection.setVisibility(View.VISIBLE);
        layoutSetupSection.setVisibility(View.GONE);
        layoutThemeColor.setVisibility(View.GONE);
        findViewById(R.id.tv_address_preview_label).setVisibility(View.GONE);
        findViewById(R.id.tv_address_preview).setVisibility(View.GONE);
        findViewById(R.id.tv_velocity_label).setVisibility(View.GONE);
        btnCreate.setText("[  FINISH IMPORT  ]");

        if (layoutTypeSection instanceof LinearLayout) {
            View label = ((LinearLayout)layoutTypeSection).getChildAt(0);
            if (label instanceof TextView) ((TextView)label).setText("DETECTED TYPE (CONFIRM OR CHANGE)");
        }
    }

    private void handleImportZipUri(android.net.Uri uri) {
        sourceUri = uri;
        getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        isZipImport = true;
        
        executor.submit(() -> {
            String detectedName = "ImportedServer";
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx != -1) {
                        detectedName = cursor.getString(nameIdx).replace(".zip", "");
                    }
                }
            } catch (Exception ignored) {}

            String finalDetectedName = detectedName;
            mainHandler.post(() -> {
                etName.setText(finalDetectedName);
                rgSetupType.check(R.id.rb_setup_manual);
                enterImportMode();
                Toast.makeText(this, "Zip selected: " + finalDetectedName, Toast.LENGTH_SHORT).show();
            });
            
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                String detectedVersion = null;
                ServerInstance.Type detectedType = ServerInstance.Type.PAPER;
                
                while ((entry = zis.getNextEntry()) != null) {
                    String n = entry.getName().toLowerCase();
                    if (n.contains("purpur.yml")) detectedType = ServerInstance.Type.PURPUR;

                    if (n.endsWith(".jar")) {
                        if (n.contains("paper")) detectedType = ServerInstance.Type.PAPER;
                        else if (n.contains("purpur")) detectedType = ServerInstance.Type.PURPUR;
                        else if (n.contains("forge") && !n.contains("neoforge")) detectedType = ServerInstance.Type.FORGE;
                        else if (n.contains("neoforge")) detectedType = ServerInstance.Type.NEOFORGE;
                        else if (n.contains("fabric")) detectedType = ServerInstance.Type.FABRIC;
                        else if (n.contains("server") || n.contains("vanilla")) detectedType = ServerInstance.Type.VANILLA;

                        Pattern p = Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)");
                        Matcher m = p.matcher(n);
                        if (m.find()) detectedVersion = m.group(1);
                    }
                }
                
                final String fVersion = detectedVersion;
                final ServerInstance.Type fType = detectedType;
                mainHandler.post(() -> {
                    int typeIdx = 0;
                    for(int i=0; i<TYPE_VALS.length; i++) if (TYPE_VALS[i] == fType) { typeIdx = i; break; }
                    npServerType.setValue(typeIdx);
                    selectType(typeIdx);
                    if (fVersion != null) {
                        mainHandler.postDelayed(() -> {
                            for (String ver : currentVersions) {
                                if (ver.contains(fVersion)) {
                                    selectedVersion = ver;
                                    if (tvVersionSelected != null) tvVersionSelected.setText(ver);
                                    break;
                                }
                            }
                        }, 500);
                    }
                });
            } catch (IOException ignored) {}
        });
    }

    private void handleImportUri(android.net.Uri uri) {
        sourceUri = uri;
        getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        isZipImport = false;
        DocumentFile root = DocumentFile.fromTreeUri(this, uri);
        if (root == null || !root.isDirectory()) return;

        executor.submit(() -> {
            String detectedName = root.getName();
            String detectedVersion = null;
            ServerInstance.Type detectedType = ServerInstance.Type.PAPER;
            int detectedPort = 25565;

            DocumentFile propFile = root.findFile("server.properties");
            if (propFile != null) {
                try (InputStream is = getContentResolver().openInputStream(propFile.getUri());
                     BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("server-port=")) {
                            try { detectedPort = Integer.parseInt(line.substring(12).trim()); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            DocumentFile[] files = root.listFiles();
            for (DocumentFile f : files) {
                if (f.getName() == null) continue;
                String n = f.getName().toLowerCase();
                if (n.contains("purpur.yml")) detectedType = ServerInstance.Type.PURPUR;

                if (n.endsWith(".jar")) {
                    if (n.contains("paper")) detectedType = ServerInstance.Type.PAPER;
                    else if (n.contains("purpur")) detectedType = ServerInstance.Type.PURPUR;
                    else if (n.contains("forge") && !n.contains("neoforge")) detectedType = ServerInstance.Type.FORGE;
                    else if (n.contains("neoforge")) detectedType = ServerInstance.Type.NEOFORGE;
                    else if (n.contains("fabric")) detectedType = ServerInstance.Type.FABRIC;
                    else if (n.contains("server") || n.contains("vanilla")) detectedType = ServerInstance.Type.VANILLA;

                    Pattern p = Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)");
                    Matcher m = p.matcher(n);
                    if (m.find()) {
                        detectedVersion = m.group(1);
                    }
                }
            }

            final String fName = detectedName;
            final String fVersion = detectedVersion;
            final ServerInstance.Type fType = detectedType;
            final int fPort = detectedPort;

            mainHandler.post(() -> {
                if (fName != null) etName.setText(fName);
                // Port is always auto-assigned, no UI input needed
                
                int typeIdx = 0;
                for(int i=0; i<TYPE_VALS.length; i++) {
                    if (TYPE_VALS[i] == fType) { typeIdx = i; break; }
                }
                npServerType.setValue(typeIdx);
                selectType(typeIdx);
                
                if (fVersion != null) {
                    mainHandler.postDelayed(() -> {
                        for (String ver : currentVersions) {
                            if (ver.contains(fVersion)) {
                                selectedVersion = ver;
                                if (tvVersionSelected != null) tvVersionSelected.setText(ver);
                                break;
                            }
                        }
                    }, 500);
                }
                
                rgSetupType.check(R.id.rb_setup_manual);
                enterImportMode();
                Toast.makeText(this, "Detected: " + fType + " " + (fVersion!=null?fVersion:"?"), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void setupThemeColors() {
        View btnColorPicker = findViewById(R.id.btn_color_picker);
        View colorPreview = findViewById(R.id.color_preview);
        TextView tvColorHex = findViewById(R.id.tv_color_hex);

        selectedColor = "#FF6B00"; // default Koda orange

        btnColorPicker.setOnClickListener(v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Pick Theme Color");

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 40);

            final View preview = new View(this);
            preview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150));
            preview.setBackgroundColor(android.graphics.Color.parseColor(selectedColor));
            layout.addView(preview);

            final android.widget.EditText hexInput = new android.widget.EditText(this);
            hexInput.setText(selectedColor);
            hexInput.setTypeface(android.graphics.Typeface.MONOSPACE);
            hexInput.setGravity(android.view.Gravity.CENTER);
            layout.addView(hexInput);

            android.widget.SeekBar[] bars = new android.widget.SeekBar[3];
            int[] currentRgb = {
                Integer.valueOf(selectedColor.substring(1, 3), 16),
                Integer.valueOf(selectedColor.substring(3, 5), 16),
                Integer.valueOf(selectedColor.substring(5, 7), 16)
            };

            String[] labels = {"Red", "Green", "Blue"};
            for (int i = 0; i < 3; i++) {
                TextView tv = new TextView(this); tv.setText(labels[i]);
                layout.addView(tv);
                bars[i] = new android.widget.SeekBar(this);
                bars[i].setMax(255);
                bars[i].setProgress(currentRgb[i]);
                final int idx = i;
                bars[i].setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                        if (fromUser) {
                            currentRgb[idx] = p;
                            String hex = String.format("#%02X%02X%02X", currentRgb[0], currentRgb[1], currentRgb[2]);
                            preview.setBackgroundColor(android.graphics.Color.parseColor(hex));
                            hexInput.setText(hex);
                        }
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
                });
                layout.addView(bars[i]);
            }

            hexInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    if (s.length() == 7 && s.toString().startsWith("#")) {
                        try {
                            int c = android.graphics.Color.parseColor(s.toString());
                            preview.setBackgroundColor(c);
                            bars[0].setProgress((c >> 16) & 0xFF);
                            bars[1].setProgress((c >> 8) & 0xFF);
                            bars[2].setProgress(c & 0xFF);
                        } catch (Exception ignored) {}
                    }
                }
            });

            builder.setView(layout);
            builder.setPositiveButton("OK", (dialog, which) -> {
                try {
                    String hex = hexInput.getText().toString();
                    android.graphics.Color.parseColor(hex); // validate
                    selectedColor = hex;
                    colorPreview.setBackgroundColor(android.graphics.Color.parseColor(hex));
                    tvColorHex.setText(hex);
                } catch (Exception ignored) {}
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }
    private void setupNumberPicker() {
        if (npServerType != null) {
            npServerType.setMinValue(0);
            npServerType.setMaxValue(TYPE_NAMES.length - 1);
            npServerType.setDisplayedValues(TYPE_NAMES);
            npServerType.setValue(0);
            npServerType.setWrapSelectorWheel(true);
            npServerType.setOnValueChangedListener((picker, oldVal, newVal) -> selectType(newVal));
        }
    }

    private void selectType(int idx) {
        selectedTypeIndex = idx;
        // Show/hide Auto Design option depending on type
        ServerInstance.Type type = TYPE_VALS[idx];
        boolean supportsAutoDesign = AUTO_DESIGN_TYPES.contains(type);
        if (layoutSetupSection != null) {
            layoutSetupSection.setVisibility(supportsAutoDesign ? View.VISIBLE : View.GONE);
            if (!supportsAutoDesign) rgSetupType.check(R.id.rb_setup_manual);
        }
        if (layoutThemeColor != null && !supportsAutoDesign) {
            layoutThemeColor.setVisibility(View.GONE);
        }
        // Velocity has no version picker in the usual sense — hide version section for now and prefill
        boolean isVelocity = type == ServerInstance.Type.VELOCITY;
        if (layoutVersionSection != null) layoutVersionSection.setVisibility(View.VISIBLE);
        loadVersionsForType(idx);
    }

    private void loadVersionsForType(int typeIdx) {
        ServerInstance.Type type = TYPE_VALS[typeIdx];
        if (tvVersionLoading != null) { tvVersionLoading.setText("Lädt…"); tvVersionLoading.setVisibility(View.VISIBLE); }
        if (type == ServerInstance.Type.PAPER || type == ServerInstance.Type.PURPUR || type == ServerInstance.Type.FOLIA) {
            executor.submit(() -> {
                List<String> versions;
                if (type == ServerInstance.Type.PAPER) versions = fetchPaperMcVersions("paper");
                else if (type == ServerInstance.Type.FOLIA) versions = fetchPaperMcVersions("folia");
                else versions = fetchPurpurVersions();
                mainHandler.post(() -> {
                    if (tvVersionLoading != null) tvVersionLoading.setVisibility(View.GONE);
                    if (versions.isEmpty()) { versions.add("1.21.4"); versions.add("1.20.1"); }
                    setVersionList(versions);
                });
            });
        } else {
            List<String> versions;
            if (type == ServerInstance.Type.NEOFORGE) versions = listOf(NEOFORGE_VERSIONS);
            else if (type == ServerInstance.Type.FORGE) versions = listOf(FORGE_VERSIONS);
            else if (type == ServerInstance.Type.FABRIC) versions = listOf(FABRIC_VERSIONS);
            else if (type == ServerInstance.Type.VELOCITY) versions = listOf(VELOCITY_VERSIONS);
            else versions = listOf(VANILLA_VERSIONS);
            if (tvVersionLoading != null) tvVersionLoading.setVisibility(View.GONE);
            setVersionList(versions);
        }
    }

    private void setVersionList(List<String> versions) {
        currentVersions = versions;
        if (!versions.isEmpty()) {
            selectedVersion = versions.get(0);
            if (tvVersionSelected != null) tvVersionSelected.setText(selectedVersion);
        }
    }

    private List<String> fetchPaperMcVersions(String project) {
        try {
            String json = get("https://api.papermc.io/v2/projects/" + project);
            org.json.JSONObject obj = new org.json.JSONObject(json);
            org.json.JSONArray arr = obj.getJSONArray("versions");
            List<String> res = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String ver = arr.getString(i).trim();
                if (!ver.isEmpty()) res.add(ver);
            }
            java.util.Collections.reverse(res); return res;
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private void showVersionPicker() {
        if (currentVersions.isEmpty()) { Toast.makeText(this, "Versionen werden noch geladen...", Toast.LENGTH_SHORT).show(); return; }

        // Descriptions per type for context
        ServerInstance.Type type = TYPE_VALS[selectedTypeIndex];
        java.util.Map<String, String> descriptions = new java.util.LinkedHashMap<>();
        if (type == ServerInstance.Type.PAPER || type == ServerInstance.Type.PURPUR || type == ServerInstance.Type.FOLIA) {
            if (!currentVersions.isEmpty()) descriptions.put(currentVersions.get(0), "⭐ Neueste Version – empfohlen");
            if (currentVersions.size() > 1) descriptions.put(currentVersions.get(1), "Stabil, viele Plugins verfügbar");
        }
        // Known descriptions for specific versions
        descriptions.put("1.21.4", "Neueste stabile Version");
        descriptions.put("1.21.1", "Sehr beliebt, viele Plugins");
        descriptions.put("1.20.1", "Sehr stabil, beste Mod-Unterstützung");
        descriptions.put("1.19.4", "Letzte 1.19er Version");
        descriptions.put("1.18.2", "Cave Update – sehr beliebt");
        descriptions.put("1.16.5", "Nether Update – viele Mods");
        descriptions.put("1.12.2", "Älteste stabile Version, größte Mod-Auswahl");
        descriptions.put("3.4.0", "Neueste Velocity Version");
        descriptions.put("3.3.0", "Stabile Velocity Version");

        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(0xFF0E0E14);
        container.setPadding(0, 0, 0, 48);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("VERSION WÄHLEN");
        tvTitle.setTextColor(0xFFFF6B00);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLetterSpacing(0.12f);
        tvTitle.setPadding(48, 40, 48, 24);
        container.addView(tvTitle);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFF222230);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);

        // NumberPicker (Dreh-Rad)
        android.widget.NumberPicker picker = new android.widget.NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(currentVersions.size() - 1);
        String[] displayVals = currentVersions.toArray(new String[0]);
        picker.setDisplayedValues(displayVals);
        picker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            picker.setTextColor(0xFFF0F0F0);
        }

        LinearLayout.LayoutParams pickerLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            (int)(200 * getResources().getDisplayMetrics().density));
        pickerLp.setMargins(48, 24, 48, 24);
        container.addView(picker, pickerLp);

        // Description Text
        TextView tvDesc = new TextView(this);
        tvDesc.setTextColor(0xFF8A8A9A);
        tvDesc.setTextSize(14);
        tvDesc.setGravity(android.view.Gravity.CENTER);
        tvDesc.setPadding(48, 0, 48, 32);
        container.addView(tvDesc);

        // Initial selection
        int initialIdx = 0;
        if (selectedVersion != null) {
            initialIdx = currentVersions.indexOf(selectedVersion);
            if (initialIdx < 0) initialIdx = 0;
        }
        picker.setValue(initialIdx);
        tvDesc.setText(descriptions.getOrDefault(currentVersions.get(initialIdx), "Standard Version ohne spezifische Beschreibung. Bietet allgemeine Stabilität und Kompatibilität für deinen Server."));

        // Update description on scroll
        picker.setOnValueChangedListener((p, oldVal, newVal) -> {
            String ver = currentVersions.get(newVal);
            String d = descriptions.getOrDefault(ver, "Standard Version ohne spezifische Beschreibung. Bietet allgemeine Stabilität und Kompatibilität für deinen Server.");
            tvDesc.setText(d);
        });

        // Confirm Button
        com.google.android.material.button.MaterialButton btnConfirm = new com.google.android.material.button.MaterialButton(this);
        btnConfirm.setText("VERSION ÜBERNEHMEN");
        btnConfirm.setTextColor(0xFF000000);
        btnConfirm.setBackgroundColor(0xFFFF6B00);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(48, 16, 48, 16);
        btnConfirm.setOnClickListener(v -> {
            selectedVersion = currentVersions.get(picker.getValue());
            if (tvVersionSelected != null) tvVersionSelected.setText(selectedVersion);
            sheet.dismiss();
        });
        container.addView(btnConfirm, btnLp);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(container);
        sheet.setContentView(scrollView);
        
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setNavigationBarColor(0xFF0D0D14);
            w.setStatusBarColor(0xFF0D0D14);
        }
        sheet.show();
    }

    private List<String> fetchPurpurVersions() {
        try {
            String json = get("https://api.purpurmc.org/v2/purpur");
            org.json.JSONObject obj = new org.json.JSONObject(json);
            org.json.JSONArray arr = obj.getJSONArray("versions");
            List<String> res = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String ver = arr.getString(i).trim();
                if (!ver.isEmpty()) res.add(ver);
            }
            Collections.reverse(res); return res;
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private String get(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(8000); c.setRequestProperty("User-Agent", "KodaNetwork/3.0");
        try (InputStream is = c.getInputStream(); BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l); return sb.toString();
        } finally { c.disconnect(); }
    }

    // Legacy setVersions kept for import detection compatibility
    private void setVersions(List<String> versions) { setVersionList(versions); }

    private List<String> listOf(String[] arr) { List<String> l = new ArrayList<>(); for (String s : arr) l.add(s); return l; }

    private void setupRam() {
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long freeMegs = mi.availMem / 1048576L;
        
        int maxIndex = 0;
        for (int i = 0; i < RAM_STEPS.length; i++) {
            if (RAM_STEPS[i] <= freeMegs) maxIndex = i;
        }
        if (maxIndex == 0 && freeMegs < RAM_STEPS[0]) maxIndex = 0; // At least allow minimum
        
        long targetRam = (long) (freeMegs * 0.75);
        int targetIndex = 0;
        for (int i = 0; i <= maxIndex; i++) {
            if (RAM_STEPS[i] <= targetRam) targetIndex = i;
        }

        seekRam.setMax(maxIndex); 
        seekRam.setProgress(targetIndex); 
        updateRam(RAM_STEPS[targetIndex]);

        seekRam.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { updateRam(RAM_STEPS[p]); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void updateRam(int mb) { ramMB = mb; tvRamValue.setText(mb >= 1024 ? (mb / 1024) + "GB" : mb + "MB"); }

    private void setupNameWatcher() {
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){ updatePreview(); }
        };
        etName.addTextChangedListener(w); updatePreview();
    }

    private void updatePreview() {
        String name = etName.getText().toString(); String sub = name.toLowerCase().trim().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (sub.isEmpty()) sub = "yourserver";
        tvAddressPreview.setText(sub + ".kodanetwork.eu");
    }

    private void createServer() {
        String name = etName.getText().toString().trim(); if (name.isEmpty()) { etName.setError("Required"); return; }
        int port = 30000 + new java.util.Random().nextInt(10000);
        String version = selectedVersion != null && !selectedVersion.isEmpty() ? selectedVersion : "1.21.4";
        ServerInstance.Type type = TYPE_VALS[selectedTypeIndex];
        String id  = UUID.randomUUID().toString();
        boolean useNative = swUseNative.isChecked();
        String dir = useNative ? new File(getFilesDir(), "servers/" + id).getAbsolutePath() : android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS) + "/KodaNetwork/servers/" + id;

        ServerInstance s = new ServerInstance(id, name, type, version, ramMB, port, dir);
        s.setUseNative(useNative); s.setThemeColor(selectedColor); s.setAutoSetup(rgSetupType.getCheckedRadioButtonId() == R.id.rb_setup_koda);
        
        android.widget.FrameLayout loadingOverlay = findViewById(R.id.layout_java_extract);
        android.widget.TextView loadingText = findViewById(R.id.tv_extract_msg);

        if (loadingOverlay != null && loadingText != null) {
            loadingText.setText("Prüfe Servername...");
            loadingOverlay.setVisibility(android.view.View.VISIBLE);
        }

        executor.submit(() -> {
            try {
                boolean isTaken = false;
                try {
                    isTaken = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).checkServerName("", s.getSubdomain());
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
                        android.widget.Toast.makeText(CreateServerActivity.this, "Fehler bei der Namensprüfung: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (isTaken) {
                    mainHandler.post(() -> {
                        if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
                        android.widget.Toast.makeText(CreateServerActivity.this, "Fehler: Servername / Subdomain ist bereits vergeben!", android.widget.Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (sourceUri != null) {
                    mainHandler.post(() -> {
                        if (loadingText != null) loadingText.setText(isZipImport ? "Extracting ZIP..." : "Importing files...");
                    });
                    if (isZipImport) {
                        extractZip(sourceUri, new File(dir));
                    } else {
                        DocumentFile sourceDir = DocumentFile.fromTreeUri(this, sourceUri);
                        if (sourceDir != null) copyRecursive(sourceDir, new File(dir));
                    }
                }
                
                mainHandler.post(() -> {
                    ServerRepo.get(this).add(s);
                    // DnsLink creation runs asynchronously because we already secured the name availability check
                    new Thread(() -> {
                        try {
                            new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).createDnsLink("", s.getSubdomain(), "85.215.180.87", s.getPort());
                        } catch (Exception ignored) {}
                        try {
                            String appUuid = getSharedPreferences("koda_settings", MODE_PRIVATE).getString("app_uuid", "unknown");
                            java.net.URL url = new java.net.URL("https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/koda_servers");
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
                            String anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjc2V6cGZycm1weXVhcGJsYnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NDE4MjYsImV4cCI6MjA5MjUxNzgyNn0.rHro6kQpXHAnxEaFxozYzsKY8IHIUlot-7-Q4LNbZT8";
                            conn.setRequestProperty("apikey", anonKey);
                            conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                            conn.setDoOutput(true);
                            String json = "{\"host\":\"" + s.getSubdomain() + "\", \"owner_app_uuid\":\"" + appUuid + "\"}";
                            java.io.OutputStream os = conn.getOutputStream();
                            os.write(json.getBytes());
                            os.flush(); os.close();
                            conn.getResponseCode();
                        } catch (Exception ignored) {}
                    }).start();
                    if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
                    android.content.Intent i = new android.content.Intent(this, ServerDetailActivity.class);
                    i.putExtra("id", id); i.putExtra("auto_setup", sourceUri == null && s.isAutoSetup()); 
                    startActivity(i);
                    finish();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
                    android.widget.Toast.makeText(CreateServerActivity.this, "Fehler: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void extractZip(android.net.Uri zipUri, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) throw new IOException("Could not create " + destDir);
        try (InputStream is = getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!newFile.exists() && !newFile.mkdirs()) throw new IOException("Could not create dir " + newFile);
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create dir " + parent);
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void copyRecursive(DocumentFile source, File dest) throws IOException {
        if (source.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) throw new IOException("Could not create " + dest);
            for (DocumentFile f : source.listFiles()) {
                copyRecursive(f, new File(dest, f.getName()));
            }
        } else {
            try (InputStream is = getContentResolver().openInputStream(source.getUri());
                 OutputStream os = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            }
        }
    }

    private String lastThemeMode = "dark";

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences("koda_settings", MODE_PRIVATE);
        String currentTheme = prefs.getString("app_theme", "modern");
        String currentMode = prefs.getString("theme_mode", "dark");
        if (lastThemeMode.equals("dark") && lastTheme.equals("modern")) {
            // First run check
        }
        if (!currentTheme.equals(lastTheme) || !currentMode.equals(lastThemeMode)) {
            lastTheme = currentTheme;
            lastThemeMode = currentMode;
            recreate();
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        eu.kodanetwork.mchost.App.resetAfkTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }
}
