import re

file_path = "app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java"

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# We need to replace the dummy player generation in showPlayerActions
start_marker = "// Dummy data for now"
end_marker = "sheet.show();"

start_idx = code.find(start_marker)
end_idx = code.find(end_marker, start_idx)

if start_idx != -1 and end_idx != -1:
    new_population = """            
            if (server != null && server.onlinePlayerNames != null && !server.onlinePlayerNames.isEmpty()) {
                for (String playerName : server.onlinePlayerNames) {
                    android.view.View playerRow = getLayoutInflater().inflate(R.layout.item_player_row, containerOnline, false);
                    android.widget.TextView tvName = playerRow.findViewById(R.id.tv_row_player_name);
                    tvName.setText(playerName);
                    
                    android.widget.ImageView ivHead = playerRow.findViewById(R.id.iv_row_player_head);
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL("https://mc-heads.net/avatar/" + playerName + "/64");
                            java.io.InputStream in = url.openStream();
                            final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                            runOnUiThread(() -> {
                                if (ivHead != null && bmp != null) ivHead.setImageBitmap(bmp);
                            });
                        } catch (Exception e) {}
                    }).start();
                    
                    playerRow.setOnClickListener(v -> {
                        sheet.dismiss();
                        showPlayerActionSheet(playerName, true);
                    });
                    
                    containerOnline.addView(playerRow);
                }
                headerOnline.setVisibility(android.view.View.VISIBLE);
                containerOnline.setVisibility(android.view.View.VISIBLE);
            } else {
                // Show empty state or hide
                headerOnline.setVisibility(android.view.View.GONE);
                containerOnline.setVisibility(android.view.View.GONE);
            }
            
            // For offline players, we could read usercache.json or whitelist.json
            // Let's implement reading whitelist.json to show known offline players!
            new Thread(() -> {
                try {
                    java.io.File whitelistFile = new java.io.File(server.getServerDir(), "whitelist.json");
                    if (whitelistFile.exists()) {
                        StringBuilder sb = new StringBuilder();
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(whitelistFile))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                        org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                        java.util.List<String> offlineNames = new java.util.ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            String name = obj.optString("name");
                            if (name != null && !name.isEmpty() && (server.onlinePlayerNames == null || !server.onlinePlayerNames.contains(name))) {
                                offlineNames.add(name);
                            }
                        }
                        
                        if (!offlineNames.isEmpty()) {
                            runOnUiThread(() -> {
                                for (String offlineName : offlineNames) {
                                    android.view.View playerRow = getLayoutInflater().inflate(R.layout.item_player_row, containerOffline, false);
                                    android.widget.TextView tvName = playerRow.findViewById(R.id.tv_row_player_name);
                                    tvName.setText(offlineName);
                                    tvName.setTextColor(0xFF888899); // darker text for offline
                                    
                                    android.widget.ImageView ivHead = playerRow.findViewById(R.id.iv_row_player_head);
                                    // optional grayscale matrix
                                    android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
                                    matrix.setSaturation(0);
                                    android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(matrix);
                                    ivHead.setColorFilter(filter);
                                    
                                    new Thread(() -> {
                                        try {
                                            java.net.URL url = new java.net.URL("https://mc-heads.net/avatar/" + offlineName + "/64");
                                            java.io.InputStream in = url.openStream();
                                            final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                                            runOnUiThread(() -> {
                                                if (ivHead != null && bmp != null) ivHead.setImageBitmap(bmp);
                                            });
                                        } catch (Exception e) {}
                                    }).start();
                                    
                                    playerRow.setOnClickListener(v -> {
                                        sheet.dismiss();
                                        showPlayerActionSheet(offlineName, false); // offline player
                                    });
                                    
                                    containerOffline.addView(playerRow);
                                }
                                headerOffline.setVisibility(android.view.View.VISIBLE);
                                containerOffline.setVisibility(android.view.View.VISIBLE);
                            });
                        }
                    }
                } catch (Exception e) {}
            }).start();
        }

        """
    code = code[:start_idx] + new_population + code[end_idx:]

with open(file_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Added real player population.")
