
with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

new_show_actions = """
    private void showPlayerActions() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_player_list, null);
        sheet.setContentView(view);
        
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setNavigationBarColor(0xFF1B1613);
            w.setStatusBarColor(0xFF1B1613);
        }
        
        android.widget.TextView tvOnlineHeader = view.findViewById(R.id.tv_pm_online_header);
        android.widget.LinearLayout containerOnline = view.findViewById(R.id.container_pm_online);
        android.widget.TextView tvOfflineHeader = view.findViewById(R.id.tv_pm_offline_header);
        android.widget.LinearLayout containerOffline = view.findViewById(R.id.container_pm_offline);

        int pad = (int)(16 * getResources().getDisplayMetrics().density);

        java.util.Set<String> shown = new java.util.HashSet<>();
        
        java.util.List<String> onlineList = new java.util.ArrayList<>();
        if (server.onlinePlayerNames != null) {
            try { onlineList.addAll(server.onlinePlayerNames); } catch (Exception ignored) {}
        }
        
        if (!onlineList.isEmpty()) {
            tvOnlineHeader.setVisibility(android.view.View.VISIBLE);
            containerOnline.setVisibility(android.view.View.VISIBLE);
            for (String p : onlineList) {
                containerOnline.addView(createPlayerRow(p, true, pad, sheet));
                shown.add(p);
            }
        }

        java.util.List<String> offlineList = new java.util.ArrayList<>();
        if (server.knownPlayers != null) {
            try { offlineList.addAll(server.knownPlayers); } catch (Exception ignored) {}
        }
        
        boolean hasOffline = false;
        for (String p : offlineList) {
            if (!shown.contains(p)) {
                if (!hasOffline) {
                    tvOfflineHeader.setVisibility(android.view.View.VISIBLE);
                    containerOffline.setVisibility(android.view.View.VISIBLE);
                    hasOffline = true;
                }
                containerOffline.addView(createPlayerRow(p, false, pad, sheet));
                shown.add(p);
            }
        }

        sheet.show();
    }
"""

# Replace the old showPlayerActions
import re
code = re.sub(r"private void showPlayerActions\(\) \{.*?(?=private void showPlayerActionSheet)", new_show_actions, code, flags=re.DOTALL)

# Replace strings in showPlayerActionSheet
code = code.replace("""btnHeal.setText("Heal");""", """btnHeal.setText(R.string.pm_heal);""")
code = code.replace("""btnStarve.setText("Starve");""", """btnStarve.setText(R.string.pm_starve);""")
code = code.replace("""btnKill.setText("Kill");""", """btnKill.setText(R.string.pm_kill);""")
code = code.replace("""btnDelete.setText("Wipe");""", """btnDelete.setText(R.string.pm_wipe);""")
code = code.replace(""""Wipe Player Data\"""", """getString(R.string.pm_wipe_title)""")
code = code.replace(""""Are you sure? This will kick the player and delete their inventory and stats.\"""", """getString(R.string.pm_wipe_desc)""")
code = code.replace("""tvName.setText(player + (isOnline ? " (Online)" : " (Offline)"));""", """tvName.setText(player + " " + getString(isOnline ? R.string.pm_online : R.string.pm_offline));""")

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "w", encoding="utf-8") as f:
    f.write(code)
print("Activity fixed")

