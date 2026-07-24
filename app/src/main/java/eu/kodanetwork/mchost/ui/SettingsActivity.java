package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.App;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvVersion = findViewById(R.id.tv_settings_version);
        if (tvVersion != null) {
            tvVersion.setText(eu.kodanetwork.mchost.BuildConfig.VERSION_NAME);
        }

        prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        boolean isCyber = "cyber".equals(prefs.getString("app_theme", "modern"));
        
        findViewById(R.id.card_legal).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, LicensesActivity.class));
        });

        if (isCyber) {
            findViewById(android.R.id.content).getRootView().setBackgroundResource(R.drawable.bg_cyber_grid);
        }

        TextView tvUuid = findViewById(R.id.tv_settings_app_uuid);
        if (tvUuid != null) {
            String appUuid = prefs.getString("app_uuid", "Unknown");
            tvUuid.setText(appUuid);
        }
        
        View cardUuid = findViewById(R.id.card_app_uuid);
        if (cardUuid != null) {
            String appUuid = prefs.getString("app_uuid", "Unknown");
            cardUuid.setOnClickListener(v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Koda App UUID", appUuid);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "UUID copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }

        // Apply light mode to settings page itself
        applyThemeModeToActivity();

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            finish();
        });

        setupAccountLink();
        setupAccountManagement();
        setupThemeMode();
        setupStyleOptions();
        setupTimeoutOptions();
        setupLanguageOptions();
        setupHapticsOptions();
        setupAnimationOptions();
        setupStorageOptions();
        setupDeviceInfo();
        setupLobbyRemote();
        setupBetaJni();
        setup2FA();
        setupBiometrics();
        setupGeminiApi();
        setupNetworkAlarmOptions();
        setupDeveloperOptions();
        
        styleTrack(findViewById(R.id.container_theme));
        styleTrack(findViewById(R.id.container_style));
        styleTrack(findViewById(R.id.container_timeout));
        
        eu.kodanetwork.mchost.util.ThemeHelper.apply(this);

        // Style all buttons premium
        stylePremiumButtons();

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                
                android.view.View topBar = findViewById(R.id.settings_top_bar);
                if (topBar != null) {
                    topBar.setPadding(topBar.getPaddingLeft(), top, topBar.getPaddingRight(), topBar.getPaddingBottom());
                    topBar.getLayoutParams().height = (int)(56 * getResources().getDisplayMetrics().density) + top;
                    topBar.requestLayout();
                }

                android.view.View scrollView = findViewById(R.id.settings_scroll);
                if (scrollView != null) {
                    scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(), scrollView.getPaddingRight(), bottom);
                }
                
                return insets.replaceSystemWindowInsets(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), 0);
            });
        }
    }

    private boolean isLight() {
        return eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this);
    }

    private int devClicks = 0;
    private long lastDevClickTime = 0;

    private void setupDeveloperOptions() {
        View cardAppInfo = findViewById(R.id.card_app_info);
        View cardDevOptions = findViewById(R.id.card_developer_options);
        com.google.android.material.switchmaterial.SwitchMaterial switchLiquidGlass = findViewById(R.id.switch_dev_liquid_glass);

        if (cardAppInfo == null || cardDevOptions == null || switchLiquidGlass == null) return;

        boolean isDevModeUnlocked = prefs.getBoolean("dev_mode_unlocked", false);
        if (isDevModeUnlocked) {
            cardDevOptions.setVisibility(View.VISIBLE);
        }

        switchLiquidGlass.setChecked(prefs.getBoolean("dev_liquid_glass", false));
        switchLiquidGlass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dev_liquid_glass", isChecked).apply();
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            Toast.makeText(this, "LiquidGlass Mode requires app restart.", Toast.LENGTH_SHORT).show();
        });

        cardAppInfo.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastDevClickTime > 500) {
                devClicks = 0;
            }
            lastDevClickTime = now;
            devClicks++;

            if (devClicks >= 10 && !prefs.getBoolean("dev_mode_unlocked", false)) {
                prefs.edit().putBoolean("dev_mode_unlocked", true).apply();
                cardDevOptions.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Du bist jetzt ein Entwickler!", Toast.LENGTH_LONG).show();
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 100);
            }
        });
    }

    // Colors for light/dark
    private int btnBgNormal()   { return 0x00000000; } // Inactive buttons are 100% transparent!
    private int btnBgActive()   { return 0xFFFF6B00; } // Active button is Tactical Orange!
    private int btnTextNormal() { return isLight() ? 0xFF6B7280 : 0xFF7E7E8F; } // Inactive text gray
    private int btnTextActive() { return 0xFFFFFFFF; } // Active text is White!
    private int cardBg()        { return isLight() ? 0xFFFFFFFF : 0xFF16110D; }
    private int pageBg()        { return isLight() ? 0xFFF3F4F6 : 0xFF0A0807; }
    private int headerBg()      { return isLight() ? 0xFFFFFFFF : 0xFF0A0807; }
    private int textPrimary()   { return isLight() ? 0xFF111827 : 0xFFE6E6FA; } // Lavender white vs Slate dark
    private int textSecondary() { return isLight() ? 0xFF6B7280 : 0xFF7E7E8F; }

    private void styleTrack(View track) {
        if (track == null) return;
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(getResources().getDisplayMetrics().density * 10); // 10dp
        gd.setColor(isLight() ? 0xFFE5E7EB : 0xFF161622); // Track background!
        track.setBackground(gd);
        track.setPadding(
            (int)(2 * getResources().getDisplayMetrics().density),
            (int)(2 * getResources().getDisplayMetrics().density),
            (int)(2 * getResources().getDisplayMetrics().density),
            (int)(2 * getResources().getDisplayMetrics().density)
        );
    }

    private void tintBtn(Button btn, int bgColor, int textColor) {
        if (btn == null) return;
        if (btn instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) btn;
            mb.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
            mb.setStrokeWidth(0); // Remove any border/outline!
            mb.setStrokeColor(android.content.res.ColorStateList.valueOf(0)); // Clear stroke color!
            mb.setRippleColor(android.content.res.ColorStateList.valueOf(0x22FF6B00)); // Smooth subtle ripple
            mb.setElevation(bgColor == btnBgActive() ? 4 : 0); // Elevation only on active button for premium layer effect!
            mb.setCornerRadius((int)(10 * getResources().getDisplayMetrics().density));
        } else {
            btn.setBackgroundColor(bgColor);
        }
        btn.setTextColor(textColor);
        btn.setAllCaps(false);
    }

    private void stylePremiumButtons() {
        eu.kodanetwork.mchost.util.HapticUtil.applyHaptics(this);
    }

    private void setupGeminiApi() {
        View layoutGemini = findViewById(R.id.layout_gemini_settings);
        String email = prefs.getString("account_email", "");
        if (!"karolbrz11212@gmail.com".equalsIgnoreCase(email)) {
            if (layoutGemini != null) layoutGemini.setVisibility(View.GONE);
            return;
        } else {
            if (layoutGemini != null) layoutGemini.setVisibility(View.VISIBLE);
        }

        android.widget.EditText etApiKey = findViewById(R.id.et_gemini_api_key);
        View btnSave = findViewById(R.id.btn_save_gemini_key);
        if (etApiKey != null) {
            String savedKey = prefs.getString("gemini_api_key", "");
            etApiKey.setText(savedKey);
            
            etApiKey.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    prefs.edit().putString("gemini_api_key", s.toString()).apply();
                }
            });
            
            if (btnSave != null) {
                btnSave.setOnClickListener(v -> {
                    String key = etApiKey.getText().toString().trim();
                    if (key.isEmpty()) return;
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    prefs.edit().putString("gemini_api_key", key).apply();
                    Toast.makeText(this, "Pinging Gemini API...", Toast.LENGTH_SHORT).show();
                    
                    new Thread(() -> {
                        try {
                            String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + key;
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setDoOutput(true);
                            String payload = "{\"contents\":[{\"parts\":[{\"text\":\"Ping! Reply with 'Pong!'\"}]}]}";
                            conn.getOutputStream().write(payload.getBytes());
                            
                            int code = conn.getResponseCode();
                            if (code == 200) {
                                runOnUiThread(() -> {
                                    Toast.makeText(SettingsActivity.this, "✅ Gemini API is working!", Toast.LENGTH_LONG).show();
                                    try {
                                        android.os.Vibrator vib = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                        if (vib != null && vib.hasVibrator()) vib.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                                    } catch (Exception ignored) {}
                                });
                            } else {
                                runOnUiThread(() -> {
                                    Toast.makeText(SettingsActivity.this, "❌ API Error (" + code + "). Invalid Key?", Toast.LENGTH_LONG).show();
                                    try {
                                        android.os.Vibrator vib = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                        if (vib != null && vib.hasVibrator()) {
                                            long[] pattern = {0, 50, 100, 50};
                                            vib.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
                                        }
                                    } catch (Exception ignored) {}
                                });
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupAccountLink();
        if (prefs.getBoolean("auto_generate_code", false) && eu.kodanetwork.mchost.network.supabase.SupabaseAuth.isLoggedIn(this)) {
            prefs.edit().putBoolean("auto_generate_code", false).apply();
            Button btnGen = findViewById(R.id.btn_generate_code);
            if (btnGen != null) {
                // Clear cooldown so it works instantly
                prefs.edit().putLong("last_code_gen", 0).apply();
                btnGen.performClick();
            }
        }
    }

    private void setupAccountLink() {
        LinearLayout container = findViewById(R.id.ll_linked_accounts_container);
        if (container == null) return;
        
        // Retain the instructions and generate button
        TextView tvInstructions = findViewById(R.id.tv_link_instructions);
        Button btnGen = findViewById(R.id.btn_generate_code);
        
        // Wait to remove views until we get the result
        
        TextView tvStatus = findViewById(R.id.tv_link_status);
        String currentCode = prefs.getString("link_code", null);
        String appUuid = prefs.getString("app_uuid", null);

        if (appUuid == null) {
            appUuid = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("app_uuid", appUuid).apply();
        }

        final String finalAppUuid = appUuid;
        
        // Setup generate code button (only handles generating code, not unlinking anymore)
        btnGen.setText("Link New Account");
        btnGen.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            if (!eu.kodanetwork.mchost.network.supabase.SupabaseAuth.isLoggedIn(this)) {
                Toast.makeText(this, "Bitte logge dich zuerst ein, um dein Minecraft-Konto zu verknüpfen!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, eu.kodanetwork.mchost.ui.design.LoginPageActivity.class);
                intent.putExtra("from_link_button", true);
                startActivity(intent);
                return;
            }
            long lastGen = prefs.getLong("last_code_gen", 0);
            if (System.currentTimeMillis() - lastGen < 60000) {
                long left = 60 - ((System.currentTimeMillis() - lastGen) / 1000);
                Toast.makeText(this, "Please wait " + left + "s before generating a new code.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putLong("last_code_gen", System.currentTimeMillis()).apply();
            
            String newCode = String.format("%06d", new java.util.Random().nextInt(999999));
            prefs.edit().putString("link_code", newCode).apply();
            setupAccountLink();

            // Count existing accounts by looking at the container
            int count = 0;
            for (int i = 0; i < container.getChildCount(); i++) {
                if ("dynamic_account".equals(container.getChildAt(i).getTag())) count++;
            }
            final boolean isMain = (count == 0);

            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                    conn.setRequestProperty("apikey", anonKey);
                    
                    String token = prefs.getString("koda_session_token", null);
                    conn.setRequestProperty("Authorization", token != null ? "Bearer " + token : "Bearer " + anonKey);
                    
                    conn.setDoOutput(true);
                    
                    String userId = prefs.getString("koda_session_token", null) != null ? finalAppUuid : null;
                    String authIdStr = userId != null ? "\"" + userId + "\"" : "null";
                    
                    String json = "{\"app_uuid\":\"" + finalAppUuid + "\", \"code\":\"" + newCode + "\", \"auth_id\": " + authIdStr + ", \"is_main\": " + isMain + "}";
                    java.io.OutputStream os = conn.getOutputStream();
                    os.write(json.getBytes());
                    os.flush(); os.close();
                    int responseCode = conn.getResponseCode();
                    if (responseCode >= 400) {
                        java.io.InputStreamReader r = new java.io.InputStreamReader(conn.getErrorStream());
                        StringBuilder sb = new StringBuilder();
                        int c; while ((c = r.read()) != -1) sb.append((char) c);
                        r.close();
                        runOnUiThread(() -> android.widget.Toast.makeText(SettingsActivity.this, "Link Error: " + sb.toString(), android.widget.Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(SettingsActivity.this, "Exception: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        if (currentCode != null) {
            tvStatus.setText("Code generated: " + currentCode);
            tvStatus.setTextColor(0xFFFFCC00);
            tvStatus.setVisibility(View.VISIBLE);
            tvInstructions.setText(getString(R.string.link_steps).replace("<code\\>", currentCode).replace("<code>", currentCode).replace("<代码>", currentCode));
            tvInstructions.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
            tvInstructions.setVisibility(View.GONE);
        }

        // Fetch all linked accounts for this appUuid/authId
        new Thread(() -> {
            try {
                // Also update last_active using a PATCH request to all rows belonging to this app_uuid
                java.net.URL patchUrl = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_user");
                java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) patchUrl.openConnection();
                patchConn.setRequestMethod("POST");
                patchConn.setRequestProperty("Content-Type", "application/json");
                String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                patchConn.setRequestProperty("apikey", anonKey);
                
                patchConn.setRequestProperty("Authorization", "Bearer " + anonKey);
                
                patchConn.setDoOutput(true);
                java.io.OutputStream pos = patchConn.getOutputStream();
                pos.write(("{\"p_app_uuid\":\"" + finalAppUuid + "\", \"p_payload\": {\"last_active\":\"now()\"}}").getBytes());
                pos.flush(); pos.close();
                patchConn.getResponseCode();
                
                String userId = prefs.getString("koda_session_token", null) != null ? finalAppUuid : null;
                String queryUrl = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?";
                if (userId != null) {
                    queryUrl += "or=(app_uuid.eq." + finalAppUuid + ",auth_id.eq." + userId + ")&mc_username=not.is.null";
                } else {
                    queryUrl += "app_uuid=eq." + finalAppUuid + "&mc_username=not.is.null";
                }
                
                // Now fetch accounts
                java.net.URL url = new java.net.URL(queryUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                
                if (conn.getResponseCode() == 200) {
                    java.io.InputStreamReader r = new java.io.InputStreamReader(conn.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    int c; while ((c = r.read()) != -1) sb.append((char) c);
                    r.close();
                    
                    String json = sb.toString();
                    if (json.length() > 5) {
                        org.json.JSONArray arr = new org.json.JSONArray(json);
                        runOnUiThread(() -> {
                            // Clear old views just before rendering new ones
                            for (int i = container.getChildCount() - 1; i >= 0; i--) {
                                View child = container.getChildAt(i);
                                if (child.getTag() != null && child.getTag().equals("dynamic_account")) {
                                    container.removeViewAt(i);
                                }
                            }
                            renderAccounts(arr, container);
                        });
                    } else {
                        runOnUiThread(() -> {
                            for (int i = container.getChildCount() - 1; i >= 0; i--) {
                                View child = container.getChildAt(i);
                                if (child.getTag() != null && child.getTag().equals("dynamic_account")) {
                                    container.removeViewAt(i);
                                }
                            }
                        });
                    }
                    if (currentCode != null) {
                        fetchLinkStatus(finalAppUuid, currentCode); // keep polling
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    private void renderAccounts(org.json.JSONArray arr, LinearLayout container) {
        for (int i = 0; i < arr.length(); i++) {
            try {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String mcName = obj.getString("mc_username");
                String rowId = obj.getString("id");
                boolean isMain = obj.optBoolean("is_main", false);
                
                // Container for account
                LinearLayout accLayout = new LinearLayout(this);
                accLayout.setOrientation(LinearLayout.HORIZONTAL);
                accLayout.setTag("dynamic_account");
                accLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, 24);
                accLayout.setLayoutParams(lp);
                
                // Head Image
                android.widget.ImageView ivHead = new android.widget.ImageView(this);
                ivHead.setLayoutParams(new LinearLayout.LayoutParams(96, 96));
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL("https://mc-heads.net/avatar/" + mcName + "/96");
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream());
                        runOnUiThread(() -> ivHead.setImageBitmap(bmp));
                    } catch (Exception ignored) {}
                }).start();
                accLayout.addView(ivHead);
                
                // Texts
                LinearLayout textLayout = new LinearLayout(this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                tLp.setMargins(16, 0, 0, 0);
                textLayout.setLayoutParams(tLp);
                
                TextView tvName = new TextView(this);
                tvName.setText(mcName);
                tvName.setTextColor(0xFF00E676);
                tvName.setTextSize(16);
                if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
                    tvName.setTextColor(0xFF008000);
                }
                textLayout.addView(tvName);
                
                TextView tvRole = new TextView(this);
                tvRole.setText(isMain ? "Main Account" : "Secondary Account");
                tvRole.setTextColor(0xFFAAAAAA);
                tvRole.setTextSize(12);
                if (eu.kodanetwork.mchost.util.ThemeHelper.isLightMode(this)) {
                    tvRole.setTextColor(0xFF555555);
                }
                textLayout.addView(tvRole);
                
                accLayout.addView(textLayout);
                
                Button btnManage = new Button(this);
                btnManage.setText("Manage");
                btnManage.setTextSize(10);
                btnManage.setTextColor(0xFF00E676);
                btnManage.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                btnManage.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    Intent intent = new Intent(this, eu.kodanetwork.mchost.ui.PraetorAccountsActivity.class);
                    intent.putExtra("extra_row_id", rowId);
                    intent.putExtra("extra_mc_name", mcName);
                    startActivity(intent);
                });
                accLayout.addView(btnManage);
                
                // Unlink button
                Button btnUnlink = new Button(this);
                btnUnlink.setText("Unlink");
                btnUnlink.setTextSize(10);
                btnUnlink.setTextColor(0xFFFF4444);
                btnUnlink.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                btnUnlink.setOnClickListener(v -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?id=eq." + rowId);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("DELETE");
                            
                            String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                            String authHeader = "Bearer " + anonKey; // Force anonKey to bypass RLS UPDATE restrictions for authenticated users
                                
                                // 1. PATCH to nullify mc_username (bypasses RLS DELETE restrictions for anon users)
                                int patchCode = -1;
                                try {
                                    java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_user_by_id").openConnection();
                                    patchConn.setRequestMethod("POST");
                                    patchConn.setRequestProperty("apikey", anonKey);
                                    patchConn.setRequestProperty("Authorization", authHeader);
                                    patchConn.setRequestProperty("Content-Type", "application/json");
                                    patchConn.setDoOutput(true);
                                    String myAppUuid = eu.kodanetwork.mchost.App.getPrefs(SettingsActivity.this).getString("app_uuid", "");
                                    patchConn.getOutputStream().write(("{\"p_app_uuid\":\"" + myAppUuid + "\", \"p_id\":\"" + rowId + "\", \"p_payload\": {\"app_uuid\": \"UNLINKED\", \"auth_id\": null, \"mc_username\": null}}").getBytes());
                                    patchCode = patchConn.getResponseCode();
                            } catch (Exception ignored) {}
                            
                            // 2. Try to actually DELETE the row
                            conn.setRequestProperty("apikey", anonKey);
                            conn.setRequestProperty("Authorization", authHeader);
                            
                            int code = -1;
                            try {
                                code = conn.getResponseCode();
                            } catch (Exception ignored) {}
                            
                            final int finalCode = code;
                            final int finalPatchCode = patchCode;
                            if ((finalCode >= 200 && finalCode < 300) || (finalPatchCode >= 200 && finalPatchCode < 300)) {
                                runOnUiThread(() -> {
                                    android.content.SharedPreferences p = eu.kodanetwork.mchost.App.getPrefs(this);
                                    if (mcName.equals(p.getString("mc_username", ""))) {
                                        p.edit().remove("mc_username").apply();
                                    }
                                    Toast.makeText(this, "Account unlinked", Toast.LENGTH_SHORT).show();
                                    container.removeView(accLayout);
                                });
                            } else {
                                runOnUiThread(() -> Toast.makeText(this, "Unlink failed: HTTP " + finalCode + " (Patch: " + finalPatchCode + ")", Toast.LENGTH_LONG).show());
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                });
                accLayout.addView(btnUnlink);
                
                if (!isMain) {
                    org.json.JSONObject permsObj = obj.optJSONObject("permissions");
                    if (permsObj == null) {
                        try {
                            permsObj = new org.json.JSONObject();
                            // default true for start/stop? Optional. We'll just leave it empty if null.
                        } catch (Exception e) {}
                    }
                    final org.json.JSONObject currentPerms = permsObj;
                    accLayout.setClickable(true);
                    
                    android.util.TypedValue outValue = new android.util.TypedValue();
                    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                    accLayout.setBackgroundResource(outValue.resourceId);
                    
                    accLayout.setOnClickListener(v -> {
                        openPermissionsDialog(rowId, mcName, currentPerms);
                    });
                }

                // Add right above the Generate Code button
                container.addView(accLayout, container.getChildCount() - 1);
                
            } catch (Exception ignored) {}
        }
    }

    private void setupAccountManagement() {
        TextView tvEmail = findViewById(R.id.tv_account_email);
        androidx.cardview.widget.CardView cardAccount = findViewById(R.id.card_account);
        
        if (!eu.kodanetwork.mchost.network.supabase.SupabaseAuth.isLoggedIn(this)) {
            tvEmail.setText("Not logged in (Local Mode)");
            cardAccount.setOnClickListener(v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                startActivity(new Intent(this, eu.kodanetwork.mchost.ui.design.LoginPageActivity.class));
            });
            return;
        }

        String email = prefs.getString("account_email", "Unknown Email");
        tvEmail.setText(email);

        cardAccount.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) {
                Toast.makeText(this, "Keine Internetverbindung. Aktion abgebrochen.", Toast.LENGTH_SHORT).show();
                return;
            }

            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            dialog.setContentView(R.layout.dialog_praetor_account);
            dialog.setCancelable(true);

            TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
            if (tvTitle != null) {
                String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
                tvTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
            }

            TextView tvAccEmail = dialog.findViewById(R.id.tv_account_email);
            if (tvAccEmail != null) tvAccEmail.setText(email);

            dialog.findViewById(R.id.btn_dialog_change_password).setOnClickListener(btn -> {
                android.app.Dialog passDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
                passDialog.setContentView(R.layout.dialog_praetor_change_password);
                passDialog.setCancelable(true);

                TextView tvPassTitle = passDialog.findViewById(R.id.tv_dialog_title);
                if (tvPassTitle != null) {
                    String praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>";
                    tvPassTitle.setText(android.text.Html.fromHtml(praetorHtml, android.text.Html.FROM_HTML_MODE_LEGACY));
                }

                android.widget.EditText inputOld = passDialog.findViewById(R.id.et_old_password);
                android.widget.EditText inputNew = passDialog.findViewById(R.id.et_new_password);
                android.widget.EditText inputConfirm = passDialog.findViewById(R.id.et_new_password_confirm);
                
                passDialog.findViewById(R.id.btn_dialog_update_password).setOnClickListener(updateBtn -> {
                    String oldPass = inputOld.getText().toString().trim();
                    String newPass = inputNew.getText().toString().trim();
                    String confirmPass = inputConfirm.getText().toString().trim();
                    
                    if (oldPass.isEmpty()) {
                        Toast.makeText(this, "Please enter your old password.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.length() < 6) {
                        Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Disable buttons and show loading
                    passDialog.findViewById(R.id.btn_dialog_update_password).setEnabled(false);
                    passDialog.findViewById(R.id.btn_dialog_cancel).setEnabled(false);
                    ((android.widget.Button) passDialog.findViewById(R.id.btn_dialog_update_password)).setText("Verifying...");

                    // Verify old password first by signing in
                    eu.kodanetwork.mchost.network.supabase.SupabaseAuth.signInWithEmail(this, email, oldPass, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                ((android.widget.Button) passDialog.findViewById(R.id.btn_dialog_update_password)).setText("Updating...");
                            });
                            // Old password is correct, update to new password
                            eu.kodanetwork.mchost.network.supabase.SupabaseAuth.updatePassword(SettingsActivity.this, newPass, new eu.kodanetwork.mchost.network.supabase.SupabaseAuth.AuthCallback() {
                                @Override public void onSuccess() {
                                    runOnUiThread(() -> {
                                        Toast.makeText(SettingsActivity.this, "Password updated successfully!", Toast.LENGTH_LONG).show();
                                        passDialog.dismiss();
                                    });
                                }
                                @Override public void onError(String msg) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(SettingsActivity.this, "Error updating password: " + msg, Toast.LENGTH_LONG).show();
                                        passDialog.findViewById(R.id.btn_dialog_update_password).setEnabled(true);
                                        passDialog.findViewById(R.id.btn_dialog_cancel).setEnabled(true);
                                        ((android.widget.Button) passDialog.findViewById(R.id.btn_dialog_update_password)).setText("Update Password");
                                    });
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String msg) {
                            runOnUiThread(() -> {
                                Toast.makeText(SettingsActivity.this, "Old password is incorrect.", Toast.LENGTH_LONG).show();
                                passDialog.findViewById(R.id.btn_dialog_update_password).setEnabled(true);
                                passDialog.findViewById(R.id.btn_dialog_cancel).setEnabled(true);
                                ((android.widget.Button) passDialog.findViewById(R.id.btn_dialog_update_password)).setText("Update Password");
                            });
                        }
                    });
                });
                
                passDialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(cancelBtn -> {
                    passDialog.dismiss();
                });
                
                passDialog.show();
                dialog.dismiss();
            });

            dialog.findViewById(R.id.btn_dialog_delete_account).setOnClickListener(btn -> {
                Intent intent = new Intent(this, eu.kodanetwork.mchost.ui.PraetorConfirmActivity.class);
                intent.putExtra("action", "delete_account");
                startActivity(intent);
                dialog.dismiss();
            });

            dialog.findViewById(R.id.btn_dialog_logout).setOnClickListener(btn -> {
                eu.kodanetwork.mchost.network.supabase.SupabaseAuth.logout(this);
                Toast.makeText(this, "Erfolgreich abgemeldet", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                recreate();
            });

            dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(btn -> dialog.dismiss());
            dialog.show();
        });

        setupSupport();
      }

    private void setupSupport() {
        Button btnReportBug = findViewById(R.id.btn_report_bug);
        Button btnReportServer = findViewById(R.id.btn_report_server);
        Button btnMyTickets = findViewById(R.id.btn_my_tickets);
        View tvLoginWarning = findViewById(R.id.tv_support_login_warning);
        View llButtons = findViewById(R.id.ll_support_buttons);

        if (!eu.kodanetwork.mchost.network.supabase.SupabaseAuth.isLoggedIn(this)) {
            if (tvLoginWarning != null) tvLoginWarning.setVisibility(View.VISIBLE);
            if (llButtons != null) llButtons.setVisibility(View.GONE);
            return;
        } else {
            if (tvLoginWarning != null) tvLoginWarning.setVisibility(View.GONE);
            if (llButtons != null) llButtons.setVisibility(View.VISIBLE);
        }

        if (btnReportBug != null) {
            btnReportBug.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, CreateSupportTicketActivity.class);
                intent.putExtra("TICKET_TYPE", "BUG");
                startActivity(intent);
            });
        }
        
        if (btnReportServer != null) {
            btnReportServer.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, CreateSupportTicketActivity.class);
                intent.putExtra("TICKET_TYPE", "SERVER_REPORT");
                startActivity(intent);
            });
        }
        
        if (btnMyTickets != null) {
            btnMyTickets.setOnClickListener(v -> {
                startActivity(new Intent(SettingsActivity.this, SupportTicketListActivity.class));
            });
        }
    }


    private void fetchLinkStatus(String appUuid, String expectedCode) {
        new Thread(() -> {
            try {
                for (int i=0; i<30; i++) {
                    Thread.sleep(2000);
                    String queryUrl = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_users?app_uuid=eq." + appUuid + "&code=eq." + expectedCode;
                    java.net.URL url = new java.net.URL(queryUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("apikey", eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                    conn.setRequestProperty("Authorization", "Bearer " + eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey());
                    int respCode = conn.getResponseCode();
                    if (respCode == 200) {
                        java.io.InputStreamReader r = new java.io.InputStreamReader(conn.getInputStream());
                        StringBuilder sb = new StringBuilder();
                        int c; while ((c = r.read()) != -1) sb.append((char) c);
                        r.close();
                        
                        String json = sb.toString();
                        if (json.equals("[]")) {
                            prefs.edit().remove("link_code").apply();
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Account successfully linked!", Toast.LENGTH_SHORT).show();
                                setupAccountLink();
                                syncServersToSupabase();
                            });
                            break;
                        } else if (json.length() > 5) {
                            org.json.JSONArray arr = new org.json.JSONArray(json);
                            if (arr.length() > 0) {
                                org.json.JSONObject obj = arr.getJSONObject(0);
                                if (obj.has("mc_username") && !obj.isNull("mc_username")) {
                                    prefs.edit().remove("link_code").apply();
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "Account successfully linked!", Toast.LENGTH_SHORT).show();
                                        setupAccountLink();
                                        syncServersToSupabase();
                                    });
                                    break;
                                }
                            }
                        }
                    } else {
                        java.io.InputStreamReader r = new java.io.InputStreamReader(conn.getErrorStream());
                        StringBuilder sb = new StringBuilder();
                        int c; while ((c = r.read()) != -1) sb.append((char) c);
                        r.close();
                        runOnUiThread(() -> Toast.makeText(this, "Poll Error: " + sb.toString(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Poll Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void syncServersToSupabase() {
        new Thread(() -> {
            try {
                String appUuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", "unknown");
                String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                for (eu.kodanetwork.mchost.model.ServerInstance s : eu.kodanetwork.mchost.model.ServerRepo.get(this).all()) {
                    if (s.getSubdomain() == null || s.getSubdomain().isEmpty()) continue;
                    try {
                        java.net.URL patchUrl = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_server");
                        java.net.HttpURLConnection patchConn = (java.net.HttpURLConnection) patchUrl.openConnection();
                        patchConn.setRequestMethod("POST");
                        patchConn.setRequestProperty("Content-Type", "application/json");
                        patchConn.setRequestProperty("apikey", anonKey);
                        patchConn.setRequestProperty("Authorization", "Bearer " + anonKey);
                        patchConn.setDoOutput(true);
                        String patchJson = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_host\":\"" + s.getSubdomain() + "\", \"p_payload\": {\"owner_app_uuid\":\"" + appUuid + "\"}}";
                        patchConn.getOutputStream().write(patchJson.getBytes());
                        patchConn.getOutputStream().flush();
                        patchConn.getOutputStream().close();
                        
                        if (patchConn.getResponseCode() < 200 || patchConn.getResponseCode() >= 300) {
                            // Fallback to POST if row doesn't exist
                            java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/koda_servers");
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
                            conn.setRequestProperty("apikey", anonKey);
                            conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                            conn.setDoOutput(true);
                            String json = "{\"host\":\"" + s.getSubdomain() + "\", \"owner_app_uuid\":\"" + appUuid + "\"}";
                            conn.getOutputStream().write(json.getBytes());
                            conn.getOutputStream().flush();
                            conn.getOutputStream().close();
                            conn.getResponseCode();
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void setupStorageOptions() {
        Button btnDeleteServers = findViewById(R.id.btn_delete_servers);
        Button btnRevokeTos = findViewById(R.id.btn_revoke_tos);

        if (btnDeleteServers != null) {
            btnDeleteServers.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                Intent i = new Intent(this, PraetorConfirmActivity.class);
                i.putExtra("action", "delete_servers");
                startActivity(i);
            });
        }

        if (btnRevokeTos != null) {
            btnRevokeTos.setOnClickListener(v -> {
                if (!eu.kodanetwork.mchost.security.PraetorSystem.checkNetwork(this)) return;
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
                Intent i = new Intent(this, PraetorConfirmActivity.class);
                i.putExtra("action", "revoke_tos");
                startActivity(i);
            });
        }
    }

    private void setupLobbyRemote() {
        Button btn = findViewById(R.id.btn_dev_termux);
        boolean on = prefs.getBoolean("lobby_remote_control", true);
        updateLobbyRemoteBtn(btn, on);
        
        btn.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            boolean current = prefs.getBoolean("lobby_remote_control", true);
            prefs.edit().putBoolean("lobby_remote_control", !current).apply();
            updateLobbyRemoteBtn(btn, !current);
        });
    }

    private void updateLobbyRemoteBtn(Button btn, boolean on) {
        btn.setAllCaps(false);
        btn.setText(on ? "Lobby Remote: ON" : "Lobby Remote: OFF");
        tintBtn(btn, on ? 0xFF00E676 : btnBgNormal(),
                     on ? 0xFF000000 : btnTextNormal());
    }

    private void setupBetaJni() {
        Button btn = findViewById(R.id.btn_beta_jni);
        if (btn == null) return;
        boolean legacyOn = !prefs.getBoolean("beta_jni_embedded", true); // Default is true (Standard JNI)
        updateLegacyModeBtn(btn, legacyOn);
        
        btn.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            boolean currentLegacy = !prefs.getBoolean("beta_jni_embedded", true);
            if (!currentLegacy) {
                Intent i = new Intent(this, LegacyModeWarningActivity.class);
                startActivityForResult(i, 9002);
            } else {
                prefs.edit().putBoolean("beta_jni_embedded", true).apply();
                updateLegacyModeBtn(btn, false);
            }
        });
    }

    private void updateLegacyModeBtn(Button btn, boolean legacyOn) {
        btn.setAllCaps(false);
        btn.setText(legacyOn ? "Legacy Mode: ON" : "Legacy Mode: OFF");
        tintBtn(btn, legacyOn ? 0xFFFF4444 : btnBgNormal(),
                     legacyOn ? 0xFFFFFFFF : btnTextNormal());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9002 && resultCode == RESULT_OK) {
            prefs.edit().putBoolean("beta_jni_embedded", false).apply();
            Button btn = findViewById(R.id.btn_beta_jni);
            if (btn != null) updateLegacyModeBtn(btn, true);
        }
    }

    private void openPermissionsDialog(String rowId, String mcName, org.json.JSONObject currentPerms) {
        String[] permKeys = {"perm_start", "perm_stop", "perm_hibernate", "perm_delete", "perm_players", "perm_plugins", "perm_console", "perm_settings"};
        String[] permNames = {"Start Server", "Stop Server", "Hibernate Server", "Delete Server", "Manage Players", "Manage Plugins", "Send Console Commands", "Server Settings"};
        boolean[] checkedItems = new boolean[permKeys.length];
        
        for (int i = 0; i < permKeys.length; i++) {
            checkedItems[i] = currentPerms.optBoolean(permKeys[i], false);
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("Permissions: " + mcName)
            .setMultiChoiceItems(permNames, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
            })
            .setPositiveButton("Save", (dialog, which) -> {
                org.json.JSONObject newPerms = new org.json.JSONObject();
                try {
                    for (int i = 0; i < permKeys.length; i++) {
                        newPerms.put(permKeys[i], checkedItems[i]);
                    }
                } catch (Exception ignored) {}
                
                String jsonStr = newPerms.toString();
                // Send PATCH request
                new Thread(() -> {
                    try {
                        String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                        java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_user_by_id");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("apikey", anonKey);
                        conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        String myAppUuid = eu.kodanetwork.mchost.App.getPrefs(SettingsActivity.this).getString("app_uuid", "");
                        conn.getOutputStream().write(("{\"p_app_uuid\":\"" + myAppUuid + "\", \"p_id\":\"" + rowId + "\", \"p_payload\": {\"permissions\": " + jsonStr + "}}").getBytes());
                        int code = conn.getResponseCode();
                        if (code >= 200 && code < 300) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Permissions saved", Toast.LENGTH_SHORT).show();
                                setupAccountLink(); // reload UI to show changes
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "Failed to save: HTTP " + code, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    

    private void setup2FA() {
        TextView tvStatus = findViewById(R.id.tv_2fa_status);
        Button btnToggle = findViewById(R.id.btn_2fa_toggle);
        Button btnSetPassword = findViewById(R.id.btn_2fa_set_password);

        boolean is2FAEnabled = prefs.getBoolean("2fa_enabled", false);
        String password = prefs.getString("2fa_password", null);

        if (is2FAEnabled && password != null) {
            tvStatus.setText("2FA Status: Enabled");
            tvStatus.setTextColor(0xFF00E676);
            btnToggle.setText("Disable 2FA");
            btnSetPassword.setVisibility(View.VISIBLE);
            btnSetPassword.setText("Change 2FA Password");
        } else {
            tvStatus.setText("2FA Status: Disabled");
            tvStatus.setTextColor(0xFFFF4444);
            btnToggle.setText("Enable 2FA");
            btnSetPassword.setVisibility(View.GONE);
        }

        btnToggle.setOnClickListener(v -> {
            if (is2FAEnabled) {
                prefs.edit().putBoolean("2fa_enabled", false).apply();
                sync2FAToSupabase(false, prefs.getString("2fa_password", null));
                setup2FA();
            } else {
                if (password == null) {
                    promptFor2FAPassword(true);
                } else {
                    prefs.edit().putBoolean("2fa_enabled", true).apply();
                    sync2FAToSupabase(true, password);
                    setup2FA();
                }
            }
        });

        btnSetPassword.setOnClickListener(v -> {
            promptFor2FAPassword(false);
        });
    }

    private void promptFor2FAPassword(boolean enableAfter) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Enter a secure password");

        new android.app.AlertDialog.Builder(this)
            .setTitle("Set 2FA Password")
            .setMessage("This password will be required in Minecraft when using /myservers.")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String newPass = input.getText().toString().trim();
                if (newPass.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString("2fa_password", newPass).apply();
                if (enableAfter) {
                    prefs.edit().putBoolean("2fa_enabled", true).apply();
                }
                sync2FAToSupabase(prefs.getBoolean("2fa_enabled", false), newPass);
                setup2FA();
                Toast.makeText(this, "2FA Password saved!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupBiometrics() {
        com.google.android.material.switchmaterial.SwitchMaterial swMain = findViewById(R.id.switch_bio_main);
        LinearLayout llOptions = findViewById(R.id.ll_bio_options);
        com.google.android.material.checkbox.MaterialCheckBox cbResume = findViewById(R.id.cb_bio_resume);
        com.google.android.material.checkbox.MaterialCheckBox cbCold = findViewById(R.id.cb_bio_cold_start);
        com.google.android.material.checkbox.MaterialCheckBox cbServer = findViewById(R.id.cb_bio_server_click);
        com.google.android.material.checkbox.MaterialCheckBox cbCreate = findViewById(R.id.cb_bio_create_server);

        boolean enabled = prefs.getBoolean("bio_enabled", false);
        swMain.setChecked(enabled);
        llOptions.setVisibility(enabled ? View.VISIBLE : View.GONE);

        cbResume.setChecked(prefs.getBoolean("bio_on_resume", false));
        cbCold.setChecked(prefs.getBoolean("bio_on_cold_start", false));
        cbServer.setChecked(prefs.getBoolean("bio_on_server_click", false));
        cbCreate.setChecked(prefs.getBoolean("bio_on_create_server", false));

        swMain.setOnCheckedChangeListener((btn, isChecked) -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            if (isChecked) {
                eu.kodanetwork.mchost.util.BiometricHelper.startAuth(this, false);
                prefs.edit().putBoolean("bio_enabled", true).apply();
                llOptions.setVisibility(View.VISIBLE);
            } else {
                prefs.edit().putBoolean("bio_enabled", false).apply();
                llOptions.setVisibility(View.GONE);
            }
        });

        cbResume.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bio_on_resume", isChecked).apply());
        cbCold.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bio_on_cold_start", isChecked).apply());
        cbServer.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bio_on_server_click", isChecked).apply());
        cbCreate.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bio_on_create_server", isChecked).apply());
    }

    private void sync2FAToSupabase(boolean enabled, String password) {
        String appUuid = prefs.getString("app_uuid", null);
        if (appUuid == null) return;
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "/rest/v1/rpc/rpc_patch_user");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                String anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                conn.setDoOutput(true);
                
                String json = "{\"p_app_uuid\":\"" + appUuid + "\", \"p_payload\": {\"two_fa_enabled\": " + enabled + ", \"two_fa_password\": " + (password == null ? "null" : "\"" + password + "\"") + "}}";
                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush(); os.close();
                conn.getResponseCode();
            } catch (Exception e) {}
        }).start();
    }

    private void setupDeviceInfo() {
        View llHeader = findViewById(R.id.ll_info_header);
        View llContent = findViewById(R.id.ll_info_content);
        TextView tvIcon = findViewById(R.id.tv_info_icon);
        TextView tvText = findViewById(R.id.tv_info_text);

        llHeader.setOnClickListener(v -> {
            boolean isVisible = llContent.getVisibility() == View.VISIBLE;
            llContent.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            tvIcon.setText(isVisible ? "▼" : "▲");
            if (!isVisible) updateDeviceInfoText(tvText);
        });
    }

    private void updateDeviceInfoText(TextView tv) {
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long totalRam = mi.totalMem / 1048576L;
        long freeRam = mi.availMem / 1048576L;
        int cores = Runtime.getRuntime().availableProcessors();
        
        long baseServerRam = 1536; // 1.5GB
        long playerRam = 100; // 100MB per player
        long usableRam = totalRam - 2048; // Leave 2GB for OS
        if (usableRam < 0) usableRam = 0;
        
        int estimatedServers = (int) (usableRam / baseServerRam);
        int maxPlayers = estimatedServers > 0 ? (int) ((usableRam - baseServerRam) / playerRam) : 0;
        if (maxPlayers < 0) maxPlayers = 0;
        int maxPlugins = estimatedServers > 0 ? (int) (usableRam / 50) : 0; // ~50MB per plugin

        String appVersion = "Unknown";
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (Exception ignored) {}

        String info = "App Version: " + appVersion + "\n" +
            "Android OS: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n\n" +
            "Hardware Specs:\n" +
            "- CPU Cores: " + cores + "\n" +
            "- Total RAM: " + totalRam + " MB\n" +
            "- Free RAM: " + freeRam + " MB\n\n" +
            "Capacity Estimates:\n" +
            "- Max Servers: " + Math.max(1, estimatedServers) + "\n" +
            "- Max Players (approx): " + (maxPlayers == 0 ? 10 : maxPlayers) + "\n" +
            "- Max Plugins (approx): " + (maxPlugins == 0 ? 20 : maxPlugins);
            
        tv.setText(info);
    }

    private void deleteRecursive(java.io.File fileOrDirectory, boolean deleteRoot) {
        if (fileOrDirectory.isDirectory()) {
            java.io.File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child, true);
                }
            }
        }
        if (deleteRoot) fileOrDirectory.delete();
    }


    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        App.resetAfkTimer();
        return super.dispatchTouchEvent(ev);
    }

    private void applyThemeModeToActivity() {
        boolean light = isLight();

        // Tag the root content and activity content to prevent ThemeHelper from overriding them
        View root = getWindow().getDecorView().getRootView();
        root.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");

        View settingsRoot = findViewById(R.id.settings_root);
        if (settingsRoot != null) {
            settingsRoot.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");
            settingsRoot.setBackgroundColor(pageBg());
        }

        View scrollView = findViewById(R.id.settings_scroll);
        if (scrollView != null) {
            scrollView.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");
            scrollView.setBackgroundColor(pageBg());
        }

        View contentView = findViewById(R.id.settings_content);
        if (contentView != null) {
            contentView.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");
            contentView.setBackgroundColor(pageBg());
        }

        // Top bar custom styling
        if (settingsRoot != null) {
            View btnBack = settingsRoot.findViewById(R.id.btn_back);
            if (btnBack != null && btnBack.getParent() instanceof View) {
                View topBar = (View) btnBack.getParent();
                topBar.setBackgroundColor(headerBg());
                topBar.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");
            }
        }

        // Status bar + nav bar
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (light) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        // Apply to all card backgrounds and text colors
        applyThemeToAllViews(getWindow().getDecorView());

        // Fix hardcoded dark colors for specific buttons in light mode
        if (light) {
            int btnBg = 0xFFE5E7EB;
            int textCol = textPrimary();
            tintBtn((Button)findViewById(R.id.btn_2fa_toggle), btnBg, textCol);
            tintBtn((Button)findViewById(R.id.btn_2fa_set_password), btnBg, textCol);
            tintBtn((Button)findViewById(R.id.btn_network_alarm), btnBg, textCol);
            tintBtn((Button)findViewById(R.id.btn_dev_termux), btnBg, textCol);
            if (findViewById(R.id.btn_beta_jni) != null) {
                if (!prefs.getBoolean("beta_jni_embedded", true)) tintBtn((Button)findViewById(R.id.btn_beta_jni), btnBg, textCol);
            }
            tintBtn((Button)findViewById(R.id.btn_delete_servers), 0xFFFFE5E5, 0xFFFF4444);
            tintBtn((Button)findViewById(R.id.btn_revoke_tos), 0xFFFFE5E5, 0xFFFF4444);

            View geminiInput = findViewById(R.id.ll_gemini_input_container);
            if (geminiInput != null) geminiInput.setBackgroundColor(btnBg);
            TextView tvGeminiInput = findViewById(R.id.et_gemini_api_key);
            if (tvGeminiInput != null) {
                tvGeminiInput.setTextColor(textCol);
                tvGeminiInput.setHintTextColor(textSecondary());
            }
        }
    }

    private void applyThemeToAllViews(View v) {
        if (v == null) return;
        boolean light = isLight();
        
        // Tag to block ThemeHelper override
        v.setTag(eu.kodanetwork.mchost.R.id.tag_themed, "BLOCKED");

        if (v instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) v;
            cv.setCardBackgroundColor(cardBg());
            cv.setCardElevation(light ? 4f : 0f);
        }

        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeToAllViews(vg.getChildAt(i));
            }
        }

        if (v instanceof TextView && !(v instanceof Button)) {
            TextView tv = (TextView) v;
            int color = tv.getTextColors().getDefaultColor();
            
            // Section headers (orange or red) - keep
            if (color == 0xFFFF6B00 || color == 0xFFFF4444) {
                // Keep original color
                return;
            }
            // Status colors (green) - keep
            if (color == 0xFF00E676) return;

            // White/light text -> primary
            if (color == 0xFFFFFFFF || color == 0xFFF0F0F0 || color == 0xFFCCCCDD || color == textPrimary()) {
                tv.setTextColor(textPrimary());
            } else if (color == 0xFF888899 || color == 0xFFAAAAAA || color == 0xFF555566 || color == textSecondary()) {
                tv.setTextColor(textSecondary());
            }
            // Grey/secondary text -> secondary
            if (color == 0xFF555566 || color == 0xFF888899 || color == 0xFF8A8A9A || color == 0xFFAAAABC) {
                tv.setTextColor(textSecondary());
            }
            
            // Use premium clean modern sans-serif typography instead of monospace!
            tv.setTypeface(android.graphics.Typeface.SANS_SERIF, tv.getTypeface() != null ? tv.getTypeface().getStyle() : android.graphics.Typeface.NORMAL);
        }
    }

    private void animatePill(View pill, View target) {
        if (pill == null || target == null) return;
        pill.post(() -> {
            int targetWidth = target.getWidth();
            if (targetWidth == 0) {
                target.post(() -> animatePill(pill, target));
                return;
            }
            if (pill.getWidth() != targetWidth) {
                android.view.ViewGroup.LayoutParams params = pill.getLayoutParams();
                params.width = targetWidth;
                pill.setLayoutParams(params);
            }
            pill.animate()
                .translationX(target.getX())
                .setDuration(250)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
        });
    }

    private void setupThemeMode() {
        TextView btnDark = findViewById(R.id.btn_theme_dark);
        TextView btnLight = findViewById(R.id.btn_theme_light);
        TextView btnAuto = findViewById(R.id.btn_theme_auto);

        View.OnClickListener listener = v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            String mode = "dark";
            if (v.getId() == R.id.btn_theme_light) mode = "light";
            else if (v.getId() == R.id.btn_theme_auto) mode = "auto";

            prefs.edit().putString("theme_mode", mode).apply();
            updateThemeModeUI();
            // Delay recreate so the pill animation can be seen
            v.postDelayed(this::recreate, 250);
        };

        btnDark.setOnClickListener(listener);
        btnLight.setOnClickListener(listener);
        btnAuto.setOnClickListener(listener);
        updateThemeModeUI();
    }

    private void updateThemeModeUI() {
        String mode = prefs.getString("theme_mode", "dark");
        TextView btnDark = findViewById(R.id.btn_theme_dark);
        TextView btnLight = findViewById(R.id.btn_theme_light);
        TextView btnAuto = findViewById(R.id.btn_theme_auto);
        View pill = findViewById(R.id.pill_theme);

        TextView active = "dark".equals(mode) ? btnDark : ("light".equals(mode) ? btnLight : btnAuto);
        
        btnDark.setTextColor(btnTextNormal());
        btnLight.setTextColor(btnTextNormal());
        btnAuto.setTextColor(btnTextNormal());
        active.setTextColor(btnTextActive());

        animatePill(pill, active);
    }

    private void setupStyleOptions() {
        TextView btnSquares = findViewById(R.id.btn_style_squares);
        TextView btnBlack = findViewById(R.id.btn_style_black);
        TextView btnOff = findViewById(R.id.btn_style_off);

        View.OnClickListener listener = v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            String style = "squares";
            if (v.getId() == R.id.btn_style_black) style = "black";
            else if (v.getId() == R.id.btn_style_off) style = "off";

            prefs.edit().putString("afk_style", style).apply();
            App.resetAfkTimer();
            updateStyleUI();
        };

        btnSquares.setOnClickListener(listener);
        btnBlack.setOnClickListener(listener);
        btnOff.setOnClickListener(listener);
        updateStyleUI();
    }

    private void updateStyleUI() {
        String style = prefs.getString("afk_style", "squares");
        TextView btnSquares = findViewById(R.id.btn_style_squares);
        TextView btnBlack = findViewById(R.id.btn_style_black);
        TextView btnOff = findViewById(R.id.btn_style_off);
        View pill = findViewById(R.id.pill_style);

        TextView active = "squares".equals(style) ? btnSquares : ("black".equals(style) ? btnBlack : btnOff);

        btnSquares.setTextColor(btnTextNormal());
        btnBlack.setTextColor(btnTextNormal());
        btnOff.setTextColor(btnTextNormal());
        active.setTextColor(btnTextActive());

        animatePill(pill, active);
    }

    private void setupTimeoutOptions() {
        TextView btn1m = findViewById(R.id.btn_timeout_1m);
        TextView btn2m = findViewById(R.id.btn_timeout_2m);
        TextView btn5m = findViewById(R.id.btn_timeout_5m);

        View.OnClickListener listener = v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            int mins = 1;
            if (v.getId() == R.id.btn_timeout_2m) mins = 2;
            else if (v.getId() == R.id.btn_timeout_5m) mins = 5;

            prefs.edit().putInt("afk_timeout", mins).apply();
            App.resetAfkTimer();
            updateTimeoutUI();
        };

        btn1m.setOnClickListener(listener);
        btn2m.setOnClickListener(listener);
        btn5m.setOnClickListener(listener);
        updateTimeoutUI();
    }

    private void updateTimeoutUI() {
        int mins = prefs.getInt("afk_timeout", 1);
        TextView btn1m = findViewById(R.id.btn_timeout_1m);
        TextView btn2m = findViewById(R.id.btn_timeout_2m);
        TextView btn5m = findViewById(R.id.btn_timeout_5m);
        View pill = findViewById(R.id.pill_timeout);

        TextView active = mins == 1 ? btn1m : (mins == 2 ? btn2m : btn5m);

        btn1m.setTextColor(btnTextNormal());
        btn2m.setTextColor(btnTextNormal());
        btn5m.setTextColor(btnTextNormal());
        active.setTextColor(btnTextActive());

        animatePill(pill, active);
    }

    private void setupLanguageOptions() {
        TextView btnSelector = findViewById(R.id.btn_lang_selector);
        if (btnSelector == null) return;
        
        String[] displayLangs = {getString(R.string.system_default), "English", "Deutsch", "简体中文"};
        final String[] codes = {"system", "en", "de", "zh"};
        
        String currentLang = prefs.getString("language", "system");
        for (int i=0; i<codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                btnSelector.setText(displayLangs[i]);
                break;
            }
        }
        
        btnSelector.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundColor(isLight() ? 0xFFFFFFFF : 0xFF0E0E14);
            container.setPadding(0, 0, 0, 48);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(getString(R.string.select_language));
            tvTitle.setTextColor(0xFFFF6B00);
            tvTitle.setTextSize(13);
            tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvTitle.setLetterSpacing(0.12f);
            tvTitle.setPadding(48, 40, 48, 24);
            container.addView(tvTitle);

            View div = new View(this);
            div.setBackgroundColor(isLight() ? 0xFFE5E7EB : 0xFF222230);
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            container.addView(div);

            for (int i = 0; i < codes.length; i++) {
                final int pos = i;
                TextView tv = new TextView(this);
                tv.setText(displayLangs[i]);
                tv.setTextSize(16);
                tv.setPadding(48, 40, 48, 40);
                
                boolean isSelected = codes[i].equals(prefs.getString("language", "system"));
                if (isSelected) {
                    tv.setTextColor(0xFFFF6B00);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                } else {
                    tv.setTextColor(isLight() ? 0xFF111827 : 0xFFFFFFFF);
                }
                
                android.util.TypedValue outValue = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                tv.setBackgroundResource(outValue.resourceId);
                tv.setClickable(true);
                
                tv.setOnClickListener(item -> {
                    eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
                    prefs.edit().putString("language", codes[pos]).apply();
                    eu.kodanetwork.mchost.App.applyLanguage(SettingsActivity.this, codes[pos]);
                    btnSelector.setText(displayLangs[pos]);
                    sheet.dismiss();
                    Toast.makeText(SettingsActivity.this, "Language updated.", Toast.LENGTH_SHORT).show();
                    recreate();
                });
                container.addView(tv);
            }

            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(container);
            sheet.setContentView(sv);
            
            android.view.Window w = sheet.getWindow();
            if (w != null) {
                int navColor = isLight() ? 0xFFFFFFFF : 0xFF0D0D14;
                w.setNavigationBarColor(navColor);
                w.setStatusBarColor(navColor);
            }
            sheet.show();
        });
    }

    private void updateLanguageUI() {
        // Handled by btn_lang_selector
    }

    private void setupHapticsOptions() {
        Button btnToggle = findViewById(R.id.btn_haptics_toggle);
        if (btnToggle == null) return;

        updateHapticsUI(btnToggle);

        btnToggle.setOnClickListener(v -> {
            boolean current = prefs.getBoolean("haptics_enabled", true);
            prefs.edit().putBoolean("haptics_enabled", !current).apply();
            if (!current) eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            updateHapticsUI(btnToggle);
        });
    }

    private void updateHapticsUI(Button btn) {
        boolean enabled = prefs.getBoolean("haptics_enabled", true);
        btn.setText(enabled ? "Haptic Feedback: ON" : "Haptic Feedback: OFF");
        tintBtn(btn, enabled ? 0xFF00E676 : btnBgNormal(),
                     enabled ? 0xFF000000 : btnTextNormal());
    }

    private void setupAnimationOptions() {
        Button btnToggle = findViewById(R.id.btn_animations_toggle);
        if (btnToggle == null) return;

        updateAnimationUI(btnToggle);

        btnToggle.setOnClickListener(v -> {
            boolean current = prefs.getBoolean("animations_enabled", true);
            prefs.edit().putBoolean("animations_enabled", !current).apply();
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
            updateAnimationUI(btnToggle);
        });
    }

    private void updateAnimationUI(Button btn) {
        boolean enabled = prefs.getBoolean("animations_enabled", true);
        btn.setText(enabled ? "Animations: ON" : "Animations: OFF");
        tintBtn(btn, enabled ? 0xFF00E676 : btnBgNormal(),
                     enabled ? 0xFF000000 : btnTextNormal());
    }

    private void setupNetworkAlarmOptions() {
        Button btnAlarm = findViewById(R.id.btn_network_alarm);
        if (btnAlarm == null) return;

        boolean on = prefs.getBoolean("network_alarm_enabled", false);
        updateNetworkAlarmUI(btnAlarm, on);

        btnAlarm.setOnClickListener(v -> {
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 80);
            boolean current = prefs.getBoolean("network_alarm_enabled", false);
            prefs.edit().putBoolean("network_alarm_enabled", !current).apply();
            updateNetworkAlarmUI(btnAlarm, !current);
        });
    }

    private void updateNetworkAlarmUI(Button btn, boolean on) {
        btn.setAllCaps(false);
        btn.setText(on ? "Network Alarm: ON" : "Network Alarm: OFF");
        tintBtn(btn, on ? 0xFFFF4444 : btnBgNormal(),
                     on ? 0xFFFFFFFF : btnTextNormal());
    }
}
