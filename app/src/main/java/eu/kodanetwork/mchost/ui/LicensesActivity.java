package eu.kodanetwork.mchost.ui;

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
        setContentView(R.layout.activity_licenses);

        android.content.SharedPreferences prefs = eu.kodanetwork.mchost.App.getPrefs(this);
        if ("cyber".equals(prefs.getString("app_theme", "modern"))) {
            findViewById(android.R.id.content).getRootView().setBackgroundResource(R.drawable.bg_cyber_grid);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        LinearLayout topContainer = findViewById(R.id.ll_legal_top_container);
        addLicense(topContainer, "Imprint (Impressum)", "impressum.txt");
        addLicense(topContainer, "Terms of Service", "tos.txt");
        addLicense(topContainer, "Privacy Policy", "privacy.txt");

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
