import re

files = [
    'app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java',
    'app/src/main/java/eu/kodanetwork/mchost/ui/FileEditorActivity.java'
]

for file in files:
    with open(file, 'r') as f:
        c = f.read()

    # Find the faulty injections
    c = c.replace('eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(this,', 
                  'eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(ServerDetailActivity.this,')

    # Wait, in FileEditorActivity it should be FileEditorActivity.this
    c = c.replace('ServerDetailActivity.this, getWindow().getDecorView());', 'this, getWindow().getDecorView());')
    
    # Actually, a safer replace for `applyThemeToView(this, xxx)` where xxx is a View is to replace `this` with `xxx.getContext()`
    
    # Let's just fix it globally by doing regex replace
    def repl_this(m):
        return f"eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView({m.group(1)}.getContext(), {m.group(1)});"
        
    c = re.sub(r'eu\.kodanetwork\.mchost\.util\.TerminalThemeHelper\.applyThemeToView\((?:this|ServerDetailActivity\.this),\s*([a-zA-Z0-9_]+)\);', repl_this, c)

    with open(file, 'w') as f:
        f.write(c)

