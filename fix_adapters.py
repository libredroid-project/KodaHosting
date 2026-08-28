import re
import os

def process_file(file):
    if not os.path.exists(file): return
    with open(file, 'r') as f:
        c = f.read()

    def repl(m):
        full = m.group(0)
        var_name = m.group(2)
        return f"{full}\n        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(parent.getContext(), {var_name});"
        
    c = re.sub(r'(View)\s+([a-zA-Z0-9_]+)\s*=\s*LayoutInflater\.from\([^)]+\)\.inflate\([^;]+;', repl, c)

    with open(file, 'w') as f:
        f.write(c)

process_file('app/src/main/java/eu/kodanetwork/mchost/ui/adapters/ModrinthSearchAdapter.java')
process_file('app/src/main/java/eu/kodanetwork/mchost/ui/adapters/PluginAdapter.java') # if it exists
process_file('app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java')
