
with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

# Replace server.executeCommand with sendCmd
code = code.replace("server.executeCommand", "sendCmd")

# Add imports for UI elements
imports = """
import android.widget.Button;
import android.widget.Switch;
import android.widget.ImageView;
"""

if "import android.widget.Button;" not in code:
    code = code.replace("import android.widget.TextView;", "import android.widget.TextView;\n" + imports)

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "w", encoding="utf-8") as f:
    f.write(code)
print("Fix done")

