
import re

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

# Replace the two methods
start_str = "    private void showPlayerActionSheet(String player, boolean isOnline) {"
end_str = "        btn.setClickable(true);\n"
# Find the exact bounds.
start_idx = code.find(start_str)
end_idx = code.find("        return row;\n    }\n", start_idx) # wait, addPlayerActionBtn doesn't return row

# Let's just use regex to replace both methods
pattern = re.compile(r"    private void showPlayerActionSheet.*?private void addPlayerActionBtn.*?\n    }\n", re.DOTALL)

replacement = """
    private void showPlayerActionSheet(String player, boolean isOnline) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_player_manage, null);
        sheet.setContentView(view);
        
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setNavigationBarColor(0xFF1B1613);
            w.setStatusBarColor(0xFF1B1613);
        }
        
        // Setup Header
        TextView tvName = view.findViewById(R.id.tv_pm_player_name);
        tvName.setText(player + (isOnline ? " (Online)" : " (Offline)"));
        
        // Buttons
        Button btnHeal = view.findViewById(R.id.btn_pm_heal);
        Button btnStarve = view.findViewById(R.id.btn_pm_starve);
        Button btnKill = view.findViewById(R.id.btn_pm_kill);
        Button btnDelete = view.findViewById(R.id.btn_pm_delete);
        Switch switchWhitelist = view.findViewById(R.id.switch_pm_whitelist);
        
        btnHeal.setOnClickListener(v -> {
            server.executeCommand("effect give " + player + " instant_health 1 255");
            android.widget.Toast.makeText(this, "Healed " + player, android.widget.Toast.LENGTH_SHORT).show();
        });
        
        btnStarve.setOnClickListener(v -> {
            server.executeCommand("effect give " + player + " hunger 100 255");
            android.widget.Toast.makeText(this, "Starving " + player, android.widget.Toast.LENGTH_SHORT).show();
        });
        
        btnKill.setOnClickListener(v -> {
            server.executeCommand("kill " + player);
            android.widget.Toast.makeText(this, "Killed " + player, android.widget.Toast.LENGTH_SHORT).show();
        });
        
        btnDelete.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Wipe Player Data")
                .setMessage("Are you sure? This will kick the player and delete their inventory and stats.")
                .setPositiveButton("Wipe", (d, w2) -> {
                    server.executeCommand("kick " + player + " Your data is being wiped.");
                    new Thread(() -> {
                        try { Thread.sleep(1000); } catch(Exception ignored){}
                        String uuid = eu.kodanetwork.mchost.util.PlayerStatsParser.getUuidFromName(new java.io.File(server.getServerDir()), player);
                        if (uuid != null) {
                            new java.io.File(server.getServerDir(), "world/playerdata/" + uuid + ".dat").delete();
                            new java.io.File(server.getServerDir(), "world/stats/" + uuid + ".json").delete();
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(this, "Wiped " + player, android.widget.Toast.LENGTH_SHORT).show();
                                sheet.dismiss();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        // Stats
        new Thread(() -> {
            String uuid = eu.kodanetwork.mchost.util.PlayerStatsParser.getUuidFromName(new java.io.File(server.getServerDir()), player);
            if (uuid != null) {
                eu.kodanetwork.mchost.util.PlayerStatsParser.PlayerStats stats = eu.kodanetwork.mchost.util.PlayerStatsParser.getStats(new java.io.File(server.getServerDir()), uuid);
                
                // Read Whitelist
                boolean isWhitelisted = false;
                try {
                    java.io.File wl = new java.io.File(server.getServerDir(), "whitelist.json");
                    if (wl.exists()) {
                        byte[] bytes = new byte[(int) wl.length()];
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(wl)) { fis.read(bytes); }
                        org.json.JSONArray arr = new org.json.JSONArray(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                        for (int i = 0; i < arr.length(); i++) {
                            if (arr.getJSONObject(i).getString("uuid").equalsIgnoreCase(uuid)) {
                                isWhitelisted = true; break;
                            }
                        }
                    }
                } catch(Exception ignored){}
                
                boolean finalIsWhitelisted = isWhitelisted;
                
                // Read Inventory
                java.util.Map<String, Object> dat = eu.kodanetwork.mchost.util.NbtParser.parsePlayerDat(new java.io.File(server.getServerDir(), "world/playerdata/" + uuid + ".dat"));
                
                runOnUiThread(() -> {
                    TextView tvDeaths = view.findViewById(R.id.tv_pm_stat_deaths);
                    TextView tvMined = view.findViewById(R.id.tv_pm_stat_mined);
                    TextView tvHours = view.findViewById(R.id.tv_pm_stat_hours);
                    TextView tvMobs = view.findViewById(R.id.tv_pm_stat_mobs);
                    TextView tvDmg = view.findViewById(R.id.tv_pm_stat_dmg);
                    
                    tvDeaths.setText(String.valueOf(stats.deaths));
                    tvMined.setText(String.valueOf(stats.blocksMined));
                    tvHours.setText(String.valueOf(stats.hoursPlayed));
                    tvMobs.setText(String.valueOf(stats.mobsKilled));
                    tvDmg.setText(String.valueOf(stats.damageTaken));
                    
                    switchWhitelist.setChecked(finalIsWhitelisted);
                    switchWhitelist.setOnCheckedChangeListener((btn, isChecked) -> {
                        server.executeCommand((isChecked ? "whitelist add " : "whitelist remove ") + player);
                    });
                    
                    if (dat != null && dat.containsKey("Inventory")) {
                        populateInventoryUI(view, (java.util.List<Object>) dat.get("Inventory"));
                    }
                });
            }
        }).start();
        
        sheet.show();
    }
    
    private void populateInventoryUI(View view, java.util.List<Object> inventory) {
        android.widget.LinearLayout containerArmor = view.findViewById(R.id.container_armor);
        android.widget.FrameLayout containerOffhand = view.findViewById(R.id.container_offhand);
        android.widget.GridLayout gridMain = view.findViewById(R.id.grid_inventory_main);
        android.widget.GridLayout gridHotbar = view.findViewById(R.id.grid_inventory_hotbar);
        
        // Initialize empty slots
        View[] armorSlots = new View[4];
        for (int i=0; i<4; i++) {
            armorSlots[i] = getLayoutInflater().inflate(R.layout.item_inventory_slot, containerArmor, false);
            containerArmor.addView(armorSlots[i]);
        }
        View offhandSlot = getLayoutInflater().inflate(R.layout.item_inventory_slot, containerOffhand, false);
        containerOffhand.addView(offhandSlot);
        
        View[] mainSlots = new View[27];
        for (int i=0; i<27; i++) {
            mainSlots[i] = getLayoutInflater().inflate(R.layout.item_inventory_slot, gridMain, false);
            gridMain.addView(mainSlots[i]);
        }
        
        View[] hotbarSlots = new View[9];
        for (int i=0; i<9; i++) {
            hotbarSlots[i] = getLayoutInflater().inflate(R.layout.item_inventory_slot, gridHotbar, false);
            gridHotbar.addView(hotbarSlots[i]);
        }
        
        // Populate items
        for (Object itemObj : inventory) {
            if (itemObj instanceof java.util.Map) {
                java.util.Map<String, Object> item = (java.util.Map<String, Object>) itemObj;
                int slotId = -1;
                if (item.containsKey("Slot")) {
                    Object slotVal = item.get("Slot");
                    if (slotVal instanceof Byte) slotId = (Byte) slotVal;
                    else if (slotVal instanceof Number) slotId = ((Number)slotVal).intValue();
                }
                
                String id = (String) item.get("id"); // e.g. minecraft:stone
                int count = 1;
                if (item.containsKey("Count")) count = ((Number)item.get("Count")).intValue();
                
                View targetView = null;
                if (slotId >= 0 && slotId <= 8) targetView = hotbarSlots[slotId];
                else if (slotId >= 9 && slotId <= 35) targetView = mainSlots[slotId - 9];
                else if (slotId >= 100 && slotId <= 103) targetView = armorSlots[103 - slotId]; // 103=helmet, 102=chest, 101=legs, 100=boots
                else if (slotId == 106) targetView = offhandSlot;
                
                if (targetView != null && id != null) {
                    ImageView iv = targetView.findViewById(R.id.iv_item_icon);
                    TextView tvCount = targetView.findViewById(R.id.tv_item_count);
                    
                    String itemName = id.replace("minecraft:", "");
                    String url = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/master/assets/minecraft/textures/item/" + itemName + ".png";
                    
                    com.bumptech.glide.Glide.with(this).load(url).into(iv);
                    
                    if (count > 1) {
                        tvCount.setVisibility(View.VISIBLE);
                        tvCount.setText(String.valueOf(count));
                    }
                }
            }
        }
    }
"""

new_code = pattern.sub(replacement, code)

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "w", encoding="utf-8") as f:
    f.write(new_code)
print("Done patching.")

