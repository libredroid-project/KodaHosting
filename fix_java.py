import re

with open("app/src/main/java/eu/kodanetwork/mchost/ui/CreateServerActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

# 1. Add class variable
code = re.sub(r"(private String aiSuggestedName = null;)", r"\1\n    private String selectedBaseDomain = \"kodanetwork.eu\";", code)

# 2. Add domain selector setup in onCreate
setup_domain_code = """
        android.widget.TextView btnKodaNetwork = findViewById(R.id.btn_domain_kodanetwork);
        android.widget.TextView btnKodaServ = findViewById(R.id.btn_domain_kodaserv);
        android.view.View wrapperKodaNetwork = findViewById(R.id.wrapper_domain_kodanetwork);
        android.view.View wrapperKodaServ = findViewById(R.id.wrapper_domain_kodaserv);

        if (btnKodaNetwork != null && btnKodaServ != null && wrapperKodaNetwork != null && wrapperKodaServ != null) {
            android.view.View.OnClickListener listener = v -> {
                eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 40);
                selectedBaseDomain = (v.getId() == R.id.wrapper_domain_kodaserv || v.getId() == R.id.btn_domain_kodaserv) ? "kodaserv.eu" : "kodanetwork.eu";
                updateDomainUI();
                updatePreview();
            };
            btnKodaNetwork.setOnClickListener(listener);
            btnKodaServ.setOnClickListener(listener);
            wrapperKodaNetwork.setOnClickListener(listener);
            wrapperKodaServ.setOnClickListener(listener);
            
            // Initial UI state
            btnKodaNetwork.setTextColor(android.graphics.Color.parseColor("#888899"));
            btnKodaServ.setTextColor(android.graphics.Color.parseColor("#888899"));
            android.widget.TextView activeText = "kodanetwork.eu".equals(selectedBaseDomain) ? btnKodaNetwork : btnKodaServ;
            android.view.View activeWrapper = "kodanetwork.eu".equals(selectedBaseDomain) ? wrapperKodaNetwork : wrapperKodaServ;
            
            activeText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            android.view.View pill = findViewById(R.id.pill_domain);
            if (pill != null) {
                activeWrapper.post(() -> {
                    pill.setTranslationX(activeWrapper.getX());
                    android.view.ViewGroup.LayoutParams params = pill.getLayoutParams();
                    params.width = activeWrapper.getWidth();
                    pill.requestLayout();
                });
            }
        }
"""
code = code.replace("setupNameWatcher();", "setupNameWatcher();\n" + setup_domain_code)

# 3. Add updateDomainUI method
update_ui_code = """
    private void updateDomainUI() {
        android.widget.TextView btnKodaNetwork = findViewById(R.id.btn_domain_kodanetwork);
        android.widget.TextView btnKodaServ = findViewById(R.id.btn_domain_kodaserv);
        android.view.View wrapperKodaNetwork = findViewById(R.id.wrapper_domain_kodanetwork);
        android.view.View wrapperKodaServ = findViewById(R.id.wrapper_domain_kodaserv);
        android.view.View pill = findViewById(R.id.pill_domain);
        
        if (btnKodaNetwork == null || btnKodaServ == null || wrapperKodaNetwork == null || wrapperKodaServ == null || pill == null) return;
        
        android.widget.TextView activeText = "kodanetwork.eu".equals(selectedBaseDomain) ? btnKodaNetwork : btnKodaServ;
        android.view.View activeWrapper = "kodanetwork.eu".equals(selectedBaseDomain) ? wrapperKodaNetwork : wrapperKodaServ;
        
        btnKodaNetwork.setTextColor(android.graphics.Color.parseColor("#888899"));
        btnKodaServ.setTextColor(android.graphics.Color.parseColor("#888899"));
        activeText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));

        activeWrapper.post(() -> {
            pill.animate()
                .translationX(activeWrapper.getX())
                .setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
            android.view.ViewGroup.LayoutParams params = pill.getLayoutParams();
            params.width = activeWrapper.getWidth();
            pill.requestLayout();
        });
    }
"""
code = code.replace("private void updatePreview() {", update_ui_code + "\n    private void updatePreview() {")

# 4. Fix updatePreview to use selectedBaseDomain
code = code.replace("tvAddressPreview.setText(sub + \".kodanetwork.eu\");", "tvAddressPreview.setText(sub + \".\" + getSelectedBaseDomain());")

# 5. Fix getSelectedBaseDomain helper
get_base_domain_code = """
    private String getSelectedBaseDomain() {
        return selectedBaseDomain != null ? selectedBaseDomain : "kodanetwork.eu";
    }
"""
code = code.replace("private void createServer() {", get_base_domain_code + "\n    private void createServer() {")

# 6. Fix createServer() logic
code = re.sub(r"String generatedIp = \(\(EditText\) findViewById\(R\.id\.et_name\)\)\.getText\(\)\.toString\(\)\.trim\(\)\.replaceAll\(\"\[\^a-zA-Z0-9-\]\", \"\"\)\.toLowerCase\(\) \+ \"\.kodanetwork\.eu\";\s*if \(generatedIp\.equals\(\"\.kodanetwork\.eu\"\)\) generatedIp = \"play\.kodanetwork\.eu\";", 
    "String generatedIp = ((EditText) findViewById(R.id.et_name)).getText().toString().trim().replaceAll(\"[^a-zA-Z0-9-]\", \"\").toLowerCase() + \".\" + getSelectedBaseDomain();\\n                    if (generatedIp.equals(\".\" + getSelectedBaseDomain())) generatedIp = \"play.\" + getSelectedBaseDomain();", code)

code = re.sub(r"(ServerInstance s = new ServerInstance\(id, name, type, version, ramMB, port, dir\);)", r"\1\n                    s.setBaseDomain(getSelectedBaseDomain());", code)

code = re.sub(r"isTaken = new eu\.kodanetwork\.mchost\.network\.supabase\.SupabaseFunctionsClient\(this\)\.checkServerName\(\"\", s\.getSubdomain\(\)\);", 
    "isTaken = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).checkServerName(\"\", s.getSubdomain(), s.getBaseDomain());", code)

port_alloc_code = """
                try {
                    int allocatedPort = new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(CreateServerActivity.this).allocatePort("", s.getSubdomain(), "main");
                    s.setPort(allocatedPort);
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (loadingOverlay != null) loadingOverlay.setVisibility(android.view.View.GONE);
                        if (e.getMessage() != null && e.getMessage().contains("ports_exhausted")) {
                            eu.kodanetwork.mchost.ui.components.PraetorDialog.showApology(CreateServerActivity.this, "P.R.A.E.T.O.R.", "Alle KodaNetwork Proxy-Ports sind derzeit belegt. Bitte versuche es später erneut.");
                        } else {
                            android.widget.Toast.makeText(CreateServerActivity.this, "Fehler bei der Port-Zuweisung: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
"""
code = re.sub(r"(android\.widget\.Toast\.makeText\(CreateServerActivity\.this, \"Fehler: Servername / Subdomain ist bereits vergeben!\", android\.widget\.Toast\.LENGTH_LONG\)\.show\(\);\n                    \}\);\n                    return;\n                \})", 
    r"\1\n" + port_alloc_code, code)

code = re.sub(r"new eu\.kodanetwork\.mchost\.network\.supabase\.SupabaseFunctionsClient\(this\)\.createDnsLink\(\"\", s\.getSubdomain\(\), \"85\.215\.180\.87\", s\.getPort\(\), \"tcp\"\);", 
    "new eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient(this).createDnsLink(\"\", s.getSubdomain(), s.getBaseDomain(), \"85.215.180.87\", s.getPort(), \"tcp\");", code)

code = re.sub(r"String json = \"\{\\\"host\\\":\\\"\" \+ s\.getSubdomain\(\) \+ \"\\\", \\\"owner_app_uuid\\\":\\\"\" \+ appUuid \+ \"\\\"\}\";", 
    "String json = \"{\\\"host\\\":\\\"\" + s.getSubdomain() + \"\\\", \\\"owner_app_uuid\\\":\\\"\" + appUuid + \"\\\", \\\"base_domain\\\":\\\"\" + s.getBaseDomain() + \"\\\"}\";", code)

with open("app/src/main/java/eu/kodanetwork/mchost/ui/CreateServerActivity.java", "w", encoding="utf-8") as f:
    f.write(code)

print("Done")
