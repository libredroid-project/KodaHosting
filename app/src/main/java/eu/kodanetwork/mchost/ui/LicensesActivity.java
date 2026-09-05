package eu.kodanetwork.mchost.ui;

/*
 * Copyright (c) 2026 Karol Brzostowski / KodaHosting
 *
 * Triple-Licensed under:
 *   - GNU General Public License v3 (GPL-3.0) — see LICENSE
 *   - Libre Open Project License v1.0 PREVIEW — see LOPL_v1.0_PREVIEW.md
 *   - Commercial License — see COMMERCIAL-LICENSE.md
 *
 * For commercial inquiries: licence@kodaserv.eu
 */


import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import eu.kodanetwork.mchost.R;

public class LicensesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(eu.kodanetwork.mchost.util.Material3ThemeHelper.isM3Enabled(this)
                ? R.layout.activity_licenses_m3 : R.layout.activity_licenses);

        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if ("cyber".equals(prefs.getString("app_theme", "modern"))) {
            findViewById(android.R.id.content).getRootView().setBackgroundResource(R.drawable.bg_cyber_grid);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        LinearLayout topContainer = findViewById(R.id.ll_legal_top_container);
        addLicense(topContainer, "App License (GPL v3)", "gpl_v3.txt");
        addPdfLicense(topContainer, "LOPL v1.0 PREVIEW", "lopl_preview.pdf");
        addPdfLicense(topContainer, "Commercial License", "commercial_license.pdf");
        addLicense(topContainer, "Imprint (Impressum)", "impressum.txt");
        addLicense(topContainer, "Terms of Service", "tos.txt");
        addLicense(topContainer, "Privacy Policy", "privacy.txt");
        addLicense(topContainer, "AI Crash Analysis (Google Gemma via OpenRouter)", "gemma_ai.txt");

        LinearLayout container = findViewById(R.id.ll_licenses_container);
        addLicense(container, "OpenJDK", "openjdk.txt");
        addLicense(container, "MariaDB", "mariadb.txt");
        addLicense(container, "Redis", "redis.txt");
        addLicense(container, "OpenSSL", "openssl.txt");
        addLicense(container, "zlib", "zlib.txt");
        addLicense(container, "ncurses", "ncurses.txt");
        addLicense(container, "PCRE2", "pcre2.txt");
        addLicense(container, "Fast Reverse Proxy (FRP)", "frp.txt");
        addLicense(container, "Press Start 2P Font", "press_start_2p.txt");
        addLicense(container, "Golos Text Font (SIL OFL 1.1)", "golos_text.txt");
        addLicense(container, "Noto Sans Font (SIL OFL 1.1)", "noto_sans.txt");
        addLicense(container, "Changa One Font (SIL OFL 1.1)", "changa_one.txt");
        addLicense(container, "Space Grotesk Font (SIL OFL 1.1)", "space_grotesk.txt");
        addLicense(container, "JetBrains Mono Font (SIL OFL 1.1)", "jetbrains_mono.txt");
        addLicense(container, "Lottie by Airbnb", "lottie.txt");
        addLicense(container, "TAB Plugin", "tab.txt");
        addLicense(container, "LuckPerms", "luckperms.txt");
        addLicense(container, "PlaceholderAPI", "placeholderapi.txt");
        addLicense(container, "ProtocolLib", "protocollib.txt");
        addLicense(container, "Simple Voice Chat", "voicechat.txt");
        addLicense(container, "Geyser", "geyser.txt");
        addLicense(container, "Floodgate", "floodgate.txt");
        addLicense(container, "Pojav JRE (PojavLauncher)", "pojav.txt");
        addLicense(container, "Paper, Folia & Velocity (PaperMC)", "papermc.txt");
        addLicense(container, "Purpur", "purpur.txt");
        addLicense(container, "Fabric Loader", "fabric_loader.txt");
        addLicense(container, "Minecraft Forge & NeoForge", "forge.txt");
        addLicense(container, "Minecraft Server (Mojang EULA)", "mojang_eula.txt");
        addLicense(container, "Termux Bootstrap & Packages", "termux.txt");
        addLicense(container, "proot", "proot.txt");
        addLicense(container, "LLVM libc++_shared", "llvm_libcxx.txt");
        addLicense(container, "Glide", "glide.txt");
        addLicense(container, "AndroidX & Material Components (Apache 2.0)", "androidx.txt");
        addLicense(container, "Kotlin Standard Library", "kotlin.txt");
        addLicense(container, "Retrofit", "retrofit.txt");
        addLicense(container, "OkHttp", "okhttp.txt");
        addLicense(container, "Gson", "gson.txt");
        addLicense(container, "org.json", "orgjson.txt");
        addLicense(container, "XZ for Java", "xz_java.txt");
        addLicense(container, "Apache Commons Compress", "commons_compress.txt");
        addLicense(container, "Google Play Services (Auth, App Update, Integrity)", "play_services.txt");
        addLicense(container, "RootBeer", "rootbeer.txt");
        addLicense(container, "BlurView", "blurview.txt");
    }

    
    private void addPdfLicense(LinearLayout container, String title, String filename) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 32);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(0xFF241C18);
        card.setRadius(24f);
        card.setCardElevation(0f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(32, 32, 32, 32);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📄 " + title);
        tvTitle.setTextColor(0xFF4CA1AF); // A different color for PDFs
        tvTitle.setTextSize(16);
        tvTitle.setClickable(true);
        tvTitle.setFocusable(true);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        tvTitle.setBackgroundResource(outValue.resourceId);
        
        tvTitle.setOnClickListener(v -> {
            try {
                // Copy asset to cache
                java.io.File cacheDir = new java.io.File(getCacheDir(), "licenses");
                cacheDir.mkdirs();
                java.io.File pdfFile = new java.io.File(cacheDir, filename);
                
                if (!pdfFile.exists()) {
                    java.io.InputStream in = getAssets().open("licenses/" + filename);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(pdfFile);
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    out.flush();
                    out.close();
                }
                
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "eu.kodanetwork.mchost.fileprovider", pdfFile);
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Failed to open PDF", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        row.addView(tvTitle);
        card.addView(row);
        container.addView(card);
    }

    private void addLicense(LinearLayout container, String title, String filename) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 32);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(0xFF241C18);
        card.setRadius(24f);
        card.setCardElevation(0f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(32, 32, 32, 32);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title + " ▼");
        tvTitle.setTextColor(0xFFFF6B00); // Orange
        tvTitle.setTextSize(16);
        tvTitle.setClickable(true);
        tvTitle.setFocusable(true);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        tvTitle.setBackgroundResource(outValue.resourceId);
        row.addView(tvTitle);

        TextView tvContent = new TextView(this);
        tvContent.setTextColor(0xFFAAAAAA);
        tvContent.setTextSize(11);
        tvContent.setPadding(0, 24, 0, 0);
        tvContent.setText(readAssetFile("licenses/" + filename));
        tvContent.setVisibility(android.view.View.GONE);
        row.addView(tvContent);

        tvTitle.setOnClickListener(v -> {
            if (tvContent.getVisibility() == android.view.View.GONE) {
                tvContent.setVisibility(android.view.View.VISIBLE);
                tvTitle.setText(title + " ▲");
            } else {
                tvContent.setVisibility(android.view.View.GONE);
                tvTitle.setText(title + " ▼");
            }
        });

        card.addView(row);
        container.addView(card);
    }

    private String readAssetFile(String path) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            return "Failed to load license.";
        }
        return sb.toString();
    }
}
