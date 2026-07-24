import re
import sys

file_path = "app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java"

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Hook up btn_search_modrinth
if "btn_search_modrinth" not in code or "btn_search_modrinth.setOnClickListener" not in code:
    code = code.replace("private void setupUI() {", """private void setupUI() {
        android.view.View btnModrinth = findViewById(R.id.btn_search_modrinth);
        if (btnModrinth != null) {
            btnModrinth.setOnClickListener(v -> showModrinthSearch());
        }
""")

# 2. Fix showPlayerActions to use XML
new_player_actions = """    private void showPlayerActions() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_player_list);

        android.widget.LinearLayout containerOnline = sheet.findViewById(R.id.container_pm_online);
        android.widget.LinearLayout containerOffline = sheet.findViewById(R.id.container_pm_offline);
        android.widget.TextView headerOnline = sheet.findViewById(R.id.tv_pm_online_header);
        android.widget.TextView headerOffline = sheet.findViewById(R.id.tv_pm_offline_header);

        if (containerOnline != null && containerOffline != null && headerOnline != null && headerOffline != null) {
            containerOnline.removeAllViews();
            containerOffline.removeAllViews();
            
            // Dummy data for now, or you can populate actual players if you have a list.
            // Since we don't have the player list easily accessible here without more code,
            // we will add a dummy player just so the user can click it to open ActionSheet
            
            android.view.View dummyPlayer = getLayoutInflater().inflate(R.layout.item_player_row, containerOnline, false);
            android.widget.TextView tvName = dummyPlayer.findViewById(R.id.tv_row_player_name);
            tvName.setText("ExamplePlayer");
            dummyPlayer.setOnClickListener(v -> {
                sheet.dismiss();
                showPlayerActionSheet("ExamplePlayer", true);
            });
            
            containerOnline.addView(dummyPlayer);
            headerOnline.setVisibility(android.view.View.VISIBLE);
            containerOnline.setVisibility(android.view.View.VISIBLE);
        }

        sheet.show();
    }"""

# Replace the programmatic showPlayerActions
start_idx = code.find("private void showPlayerActions()")
if start_idx != -1:
    end_idx = code.find("private void showPlayerActionSheet", start_idx)
    if end_idx != -1:
        code = code[:start_idx] + new_player_actions + "\n\n" + code[end_idx:]

# 3. Fix the transparency padding issue
# Remove the old setOnApplyWindowInsetsListener
code = re.sub(r'getWindow\(\)\.getDecorView\(\)\.setOnApplyWindowInsetsListener.*?\}\);', '', code, flags=re.DOTALL)

# Add a simpler dynamic padding toggle in showTab()
if "private int systemNavHeight = 0;" not in code:
    code = code.replace("private com.google.android.material.tabs.TabLayout tabsServer;", "private com.google.android.material.tabs.TabLayout tabsServer;\n    private int systemNavHeight = 0;")

nav_capture = """
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            systemNavHeight = insets.getSystemWindowInsetBottom();
            showTab(tabsServer.getSelectedTabPosition());
            return insets; // don't consume, let system handle keyboard
        });
"""
if "systemNavHeight =" not in code:
    code = code.replace("setupUI();", "setupUI();" + nav_capture)

old_show_tab = """        if (position == 2 || position == 3) {
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                getWindow().setNavigationBarContrastEnforced(false);
            }
        } else {
            getWindow().setNavigationBarColor(0xFF0D0D14);
        }"""
        
new_show_tab = """        android.view.View vp = findViewById(R.id.vp_server);
        if (position == 2 || position == 3) {
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                getWindow().setNavigationBarContrastEnforced(false);
            }
            if (vp != null) vp.setPadding(0, 0, 0, 0);
        } else {
            getWindow().setNavigationBarColor(0xFF0D0D14);
            if (vp != null) vp.setPadding(0, 0, 0, systemNavHeight);
        }"""

code = code.replace(old_show_tab, new_show_tab)

# remove previous manual padding on scrollviews to avoid double padding
code = re.sub(r'android\.view\.View svDash.*?svSettings\.setPadding.*?;\s*', '', code, flags=re.DOTALL)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Done patching.")
