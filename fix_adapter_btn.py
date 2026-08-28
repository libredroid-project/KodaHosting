import re

with open('app/src/main/java/eu/kodanetwork/mchost/ui/ServerCardAdapter.java', 'r') as f:
    c = f.read()

# Replace block around line 195
old_block1 = """                    if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                        btnAction.setText(ctx.getString(R.string.stop));
                        btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF5252));
                        btnAction.setTextColor(0xFF111111);
                    } else {
                        btnAction.setText(ctx.getString(R.string.start));
                        btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B00));
                        btnAction.setTextColor(0xFF111111);
                    }"""
new_block1 = """                    if (st == ServerInstance.State.ONLINE || st == ServerInstance.State.STARTING) {
                        btnAction.setText(ctx.getString(R.string.stop));
                        if (eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false)) {
                            btnAction.setBackgroundResource(R.drawable.bg_mc_button_red);
                            btnAction.setBackgroundTintList(null);
                            btnAction.setTextColor(0xFF000000);
                        } else {
                            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF5252));
                            btnAction.setTextColor(0xFF111111);
                        }
                    } else {
                        btnAction.setText(ctx.getString(R.string.start));
                        if (eu.kodanetwork.mchost.App.getPrefs(ctx).getBoolean("dev_terminal_enabled", false)) {
                            btnAction.setBackgroundResource(R.drawable.bg_mc_button_orange);
                            btnAction.setBackgroundTintList(null);
                            btnAction.setTextColor(0xFF000000);
                        } else {
                            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF6B00));
                            btnAction.setTextColor(0xFF111111);
                        }
                    }"""
c = c.replace(old_block1, new_block1)

with open('app/src/main/java/eu/kodanetwork/mchost/ui/ServerCardAdapter.java', 'w') as f:
    f.write(c)

print("Updated Adapter")
