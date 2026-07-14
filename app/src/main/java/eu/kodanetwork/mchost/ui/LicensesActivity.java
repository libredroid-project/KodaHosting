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
