import re

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'r') as f:
    content = f.read()

# Make corners square
content = re.sub(r'app:cardCornerRadius="[^"]+"', 'app:cardCornerRadius="0dp"', content)
content = re.sub(r'app:cornerRadius="[^"]+"', 'app:cornerRadius="0dp"', content)
content = re.sub(r'app:cardElevation="[^"]+"', 'app:cardElevation="0dp"', content)

# Change common background colors to dark terminal colors
content = re.sub(r'android:background="(?!#000000|#111111|#222222)[^"]+"', 'android:background="#111111"', content)
content = re.sub(r'app:cardBackgroundColor="[^"]+"', 'app:cardBackgroundColor="#111111"', content)
content = re.sub(r'android:backgroundTint="[^"]+"', 'android:backgroundTint="#333333"', content)

# Inject monospace font into all text-bearing elements
def inject_font(match):
    tag = match.group(0)
    if 'android:fontFamily' not in tag:
        # Insert just before the closing > or />
        if tag.endswith('/>'):
            return tag[:-2] + ' android:fontFamily="monospace"/>'
        else:
            return tag[:-1] + ' android:fontFamily="monospace">'
    return tag

content = re.sub(r'<(TextView|EditText|Button|com\.google\.android\.material\.button\.MaterialButton)[\s\S]*?(?=>|/>)(>|/>)', inject_font, content)

# Modify text colors
content = re.sub(r'android:textColor="[^"]+"', 'android:textColor="#FFFFFF"', content)
content = re.sub(r'android:textColorHint="[^"]+"', 'android:textColorHint="#555555"', content)

# Set root background to pure black
content = re.sub(r'<RelativeLayout([\s\S]*?)android:background="[^"]+"', r'<RelativeLayout\1android:background="#000000"', content, count=1)
# Ensure root has background if it was missing
if 'android:background="#000000"' not in content[:300]:
    content = re.sub(r'<RelativeLayout', r'<RelativeLayout\n    android:background="#000000"', content, count=1)

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'w') as f:
    f.write(content)

print("Transformed!")
