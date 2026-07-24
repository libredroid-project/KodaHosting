import re

file_path = "app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java"

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Add class variable systemNavHeight
if "private int systemNavHeight = 0;" not in code:
    code = code.replace("private com.google.android.material.tabs.TabLayout tabsServer;", "private com.google.android.material.tabs.TabLayout tabsServer;\n    private int systemNavHeight = 0;")

# 2. Add WindowInsetsListener in onCreate, BEFORE setupTabs()
nav_capture = """
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            systemNavHeight = insets.getSystemWindowInsetBottom();
            showTab(tabsServer.getSelectedTabPosition());
            return insets; // don't consume, let system handle keyboard
        });
"""
if "systemNavHeight =" not in code:
    code = code.replace("        setupTabs();", nav_capture + "\n        setupTabs();")

# 3. Update showTab to dynamically handle nav bar color AND padding
old_show_tab = """    private void showTab(int i) {
        int targetSettings = server.isDatabase() ? 3 : 4;
        pDash    .setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        pConsole .setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        pFiles   .setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        if (pPlugins != null) pPlugins.setVisibility(!server.isDatabase() && i == 3 ? View.VISIBLE : View.GONE);
        pSettings.setVisibility(i == targetSettings ? View.VISIBLE : View.GONE);
        if (i == 2) refreshFiles();
    }"""

new_show_tab = """    private void showTab(int i) {
        int targetSettings = server.isDatabase() ? 3 : 4;
        pDash    .setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        pConsole .setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        pFiles   .setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        if (pPlugins != null) pPlugins.setVisibility(!server.isDatabase() && i == 3 ? View.VISIBLE : View.GONE);
        pSettings.setVisibility(i == targetSettings ? View.VISIBLE : View.GONE);
        if (i == 2) refreshFiles();

        android.view.View vp = findViewById(R.id.vp_server);
        if (i == 2 || i == 3 || i == targetSettings) {
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                getWindow().setNavigationBarContrastEnforced(false);
            }
            if (vp != null) vp.setPadding(0, 0, 0, 0);
            if (pSettings != null) pSettings.setPadding(0, 0, 0, systemNavHeight + 20); // Add extra padding for settings so it's not hidden
        } else {
            getWindow().setNavigationBarColor(0xFF0D0D14);
            if (vp != null) vp.setPadding(0, 0, 0, systemNavHeight);
            if (pSettings != null) pSettings.setPadding(0, 0, 0, 0);
        }
    }"""

if "setNavigationBarColor" not in old_show_tab and old_show_tab in code:
    code = code.replace(old_show_tab, new_show_tab)

# 4. Hook up modrinth button inside setupSettings()
modrinth_hook = """        android.view.View btnModrinth = findViewById(R.id.btn_search_modrinth);
        if (btnModrinth != null) {
            btnModrinth.setOnClickListener(v -> showModrinthSearch());
        }
"""
if "btn_search_modrinth" not in code:
    code = code.replace("private void setupSettings() {", "private void setupSettings() {\n" + modrinth_hook)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Done patching.")
