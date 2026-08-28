import re

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'r') as f:
    c = f.read()

# For any tag, if it has more than one app:backgroundTint, remove the duplicates
def dedup_tints(match):
    block = match.group(0)
    tints = re.findall(r'app:backgroundTint="[^"]+"', block)
    if len(tints) > 1:
        # Keep the first, remove the rest
        first = tints[0]
        block = block.replace(first, '@@TEMP@@', 1)
        block = re.sub(r'app:backgroundTint="[^"]+"', '', block)
        block = block.replace('@@TEMP@@', first)
    return block

c = re.sub(r'<com\.google\.android\.material\.button\.MaterialButton[\s\S]*?(?=>|/>)(>|/>)', dedup_tints, c)

with open('app/src/main/res/layout/activity_server_detail_terminal.xml', 'w') as f:
    f.write(c)
print("Fixed!")
