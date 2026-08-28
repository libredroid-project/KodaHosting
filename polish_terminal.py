import re

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'r') as f:
    c = f.read()

# Replace CardView with LinearLayout and bg_mc_card
c = re.sub(r'<androidx\.cardview\.widget\.CardView', r'<LinearLayout\n        android:background="@drawable/bg_mc_card"', c)
c = re.sub(r'</androidx\.cardview\.widget\.CardView>', r'</LinearLayout>', c)

# Fix padding for LinearLayouts that used to be CardViews
c = re.sub(r'app:contentPadding(Bottom|Top|Start|End|Left|Right)?="([^"]+)"', r'android:padding\1="\2"', c)
c = re.sub(r'app:cardBackgroundColor="[^"]*"', '', c)

# Use bg_mc_panel for EditTexts
c = re.sub(r'<EditText([\s\S]*?)android:background="[^"]*"', r'<EditText\1android:background="@drawable/bg_mc_panel"', c)
c = re.sub(r'<ScrollView([\s\S]*?)android:background="[^"]*"', r'<ScrollView\1android:background="@drawable/bg_mc_panel"', c)

# Force MaterialButtons to use bg_mc_button_dark (except action button which is colored dynamically)
def fix_button(match):
    block = match.group(0)
    if 'id="@+id/btn_action"' in block or 'id="@+id/btn_start"' in block or 'id="@+id/btn_stop"' in block:
        # Keep dynamic ones flat but square
        return block
    # For others, use dark 3D button
    if 'app:backgroundTint' in block:
        block = re.sub(r'app:backgroundTint="[^"]*"', 'app:backgroundTint="@null"', block)
    block = re.sub(r'android:backgroundTint="[^"]*"', 'app:backgroundTint="@null"', block)
    
    if 'android:background=' in block:
        block = re.sub(r'android:background="[^"]*"', 'android:background="@drawable/bg_mc_button_dark"', block)
    else:
        block = block.replace('<com.google.android.material.button.MaterialButton', '<com.google.android.material.button.MaterialButton\n        android:background="@drawable/bg_mc_button_dark"\n        app:backgroundTint="@null"')
    return block

c = re.sub(r'<com\.google\.android\.material\.button\.MaterialButton[\s\S]*?(?=>|/>)(>|/>)', fix_button, c)

# Set root to very dark grey instead of black, for contrast with cards
c = re.sub(r'<RelativeLayout([\s\S]*?)android:background="#000000"', r'<RelativeLayout\1android:background="#141414"', c, count=1)

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'w') as f:
    f.write(c)
print("Polished!")
