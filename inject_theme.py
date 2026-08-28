import re

files = [
    'app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java',
    'app/src/main/java/eu/kodanetwork/mchost/ui/FileEditorActivity.java'
]

for file in files:
    with open(file, 'r') as f:
        c = f.read()

    # Find patterns like View view = getLayoutInflater().inflate(...)
    # or View sheetView = getLayoutInflater().inflate(...)
    # We can inject eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(this, view);
    
    # It's better to just use regex to find Dialog and BottomSheetDialog inflation
    
    def repl(m):
        var_type = m.group(1)
        var_name = m.group(2)
        inflate_call = m.group(3)
        return f"{var_type} {var_name} = {inflate_call};\n        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(this, {var_name});"
        
    c = re.sub(r'(View)\s+([a-zA-Z0-9_]+)\s*=\s*(getLayoutInflater\(\)\.inflate\([^;]+;)', repl, c)
    c = re.sub(r'(View)\s+([a-zA-Z0-9_]+)\s*=\s*(LayoutInflater\.from\([^;]+;)', repl, c)

    # For FileEditorActivity, it's setContentView
    if "FileEditorActivity" in file:
        c = c.replace('setContentView(R.layout.activity_file_editor);', 
                      'setContentView(R.layout.activity_file_editor);\n        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(this, getWindow().getDecorView());')

    with open(file, 'w') as f:
        f.write(c)

print("Injected dynamic theming!")
