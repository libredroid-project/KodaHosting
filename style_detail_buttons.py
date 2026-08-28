import re

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'r') as f:
    c = f.read()

def inject_bg(match):
    block = match.group(0)
    # Strip existing styles and tints
    block = re.sub(r'style="[^"]+"', '', block)
    block = re.sub(r'app:backgroundTint="[^"]*"', '', block)
    block = re.sub(r'android:backgroundTint="[^"]*"', '', block)
    block = re.sub(r'android:background="[^"]*"', '', block)
    
    bg = "@drawable/bg_mc_button_dark"
    if 'id="@+id/btn_start"' in block: bg = "@drawable/bg_mc_button_green"
    elif 'id="@+id/btn_stop"' in block: bg = "@drawable/bg_mc_button_red"
    elif 'id="@+id/btn_restart"' in block: bg = "@drawable/bg_mc_button_orange"
    elif 'id="@+id/btn_kill"' in block: bg = "@drawable/bg_mc_button_red"
    
    block = block.replace('<com.google.android.material.button.MaterialButton', f'<com.google.android.material.button.MaterialButton\n        android:background="{bg}"\n        app:backgroundTint="@null"')
    return block

c = re.sub(r'<com\.google\.android\.material\.button\.MaterialButton[\s\S]*?(?=>|/>)(>|/>)', inject_bg, c)

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'w') as f:
    f.write(c)
print("Styled detail buttons!")
