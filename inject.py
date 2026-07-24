import re

with open('app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java', 'r', encoding='utf-8') as f:
    code = f.read()

modrinth_code = """
    private void showModrinthSearch() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_modrinth_search);
        
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            window.setStatusBarColor(0xFF0A0807);
            window.getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
        }
        
        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bsd = (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            android.widget.FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                bottomSheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
                behavior.setPeekHeight(android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        
        android.widget.EditText etSearch = dialog.findViewById(R.id.et_modrinth_search);
        android.widget.ImageButton btnSubmit = dialog.findViewById(R.id.btn_modrinth_search_submit);
        androidx.recyclerview.widget.RecyclerView rvResults = dialog.findViewById(R.id.rv_modrinth_results);
        android.widget.ProgressBar pb = dialog.findViewById(R.id.pb_modrinth_search);
        
        rvResults.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        eu.kodanetwork.mchost.ui.adapters.ModrinthSearchAdapter adapter = new eu.kodanetwork.mchost.ui.adapters.ModrinthSearchAdapter(this, (project, pbDownload, btn) -> {
            pbDownload.setVisibility(android.view.View.VISIBLE);
            btn.setVisibility(android.view.View.GONE);
            new Thread(() -> {
                try {
                    String projectId = project.optString("project_id", project.optString("id"));
                    String projectTitle = project.optString("title");
                    String versionUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(versionUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/1.0");
                    java.io.InputStream in = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    in.close();
                    
                    org.json.JSONArray versions = new org.json.JSONArray(sb.toString());
                    if (versions.length() > 0) {
                        org.json.JSONObject latest = versions.getJSONObject(0);
                        org.json.JSONArray files = latest.getJSONArray("files");
                        if (files.length() > 0) {
                            org.json.JSONObject file = files.getJSONObject(0);
                            String dlUrl = file.getString("url");
                            String sha1 = file.getJSONObject("hashes").getString("sha1");
                            
                            runOnUiThread(() -> {
                                pbDownload.setVisibility(android.view.View.GONE);
                                btn.setVisibility(android.view.View.VISIBLE);
                                dialog.dismiss();
                                
                                java.io.File propsFile = new java.io.File(server.getServerDir(), "server.properties");
                                try {
                                    java.util.List<String> linesProps = new java.util.ArrayList<>();
                                    if (propsFile.exists()) {
                                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(propsFile))) {
                                            String l; while ((l = br.readLine()) != null) linesProps.add(l);
                                        }
                                    }
                                    boolean foundUrl = false, foundSha1 = false;
                                    for (int i = 0; i < linesProps.size(); i++) {
                                        if (linesProps.get(i).trim().startsWith("resource-pack=")) {
                                            linesProps.set(i, "resource-pack=" + dlUrl);
                                            foundUrl = true;
                                        } else if (linesProps.get(i).trim().startsWith("resource-pack-sha1=")) {
                                            linesProps.set(i, "resource-pack-sha1=" + sha1);
                                            foundSha1 = true;
                                        }
                                    }
                                    if (!foundUrl) linesProps.add("resource-pack=" + dlUrl);
                                    if (!foundSha1) linesProps.add("resource-pack-sha1=" + sha1);
                                    try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(propsFile))) {
                                        for (String l : linesProps) pw.println(l);
                                    }
                                } catch (Exception ignored) {}
                                
                                android.widget.TextView tvUrl = findViewById(R.id.tv_resource_pack_url);
                                if (tvUrl != null) tvUrl.setText(dlUrl);
                                
                                android.widget.Toast.makeText(ServerDetailActivity.this, "Resource Pack applied: " + projectTitle, android.widget.Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }
                    }
                    runOnUiThread(() -> {
                        pbDownload.setVisibility(android.view.View.GONE);
                        btn.setVisibility(android.view.View.VISIBLE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "No valid files found for " + projectTitle, android.widget.Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        pbDownload.setVisibility(android.view.View.GONE);
                        btn.setVisibility(android.view.View.VISIBLE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "Error fetching versions: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        rvResults.setAdapter(adapter);
        
        android.view.View.OnClickListener doSearch = v -> {
            String query = etSearch.getText().toString().trim();
            if (query.isEmpty()) return;
            
            eu.kodanetwork.mchost.util.HapticUtil.forceVibrate(this, 30);
            pb.setVisibility(android.view.View.VISIBLE);
            
            new Thread(() -> {
                try {
                    String urlStr = "https://api.modrinth.com/v2/search?query=" + java.net.URLEncoder.encode(query, "UTF-8") + "&facets=[[%22project_type:resourcepack%22]]";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "KodaNetwork/1.0");
                    
                    java.io.InputStream in = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    in.close();
                    
                    org.json.JSONObject result = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray hits = result.getJSONArray("hits");
                    
                    runOnUiThread(() -> {
                        pb.setVisibility(android.view.View.GONE);
                        adapter.setResults(hits);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        pb.setVisibility(android.view.View.GONE);
                        android.widget.Toast.makeText(ServerDetailActivity.this, "Search error: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        };
        
        btnSubmit.setOnClickListener(doSearch);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch.onClick(v);
                return true;
            }
            return false;
        });
        
        dialog.show();
    }
"""

if 'private void showModrinthSearch()' not in code:
    code = code.replace('private void downloadJar() {', modrinth_code + '\n    private void downloadJar() {')

with open('app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java', 'w', encoding='utf-8') as f:
    f.write(code)
