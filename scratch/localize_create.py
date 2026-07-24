import os
import re
import xml.etree.ElementTree as ET

strings_map = {
    # DB
    "new_database": {"en": "New Database", "de": "Neue Datenbank", "zh": "新数据库"},
    "database_name": {"en": "DATABASE NAME", "de": "DATENBANK NAME", "zh": "数据库名称"},
    "database_name_hint": {"en": "e.g. LobbyDB", "de": "z.B. LobbyDB", "zh": "例如 LobbyDB"},
    "database_engine": {"en": "DATABASE ENGINE", "de": "DATENBANK ENGINE", "zh": "数据库引擎"},
    "database_port": {"en": "PORT", "de": "PORT", "zh": "端口"},
    "database_port_hint": {"en": "Port (default: 3306 for MariaDB, 6379 for Redis)", "de": "Port (Standard: 3306 für MariaDB, 6379 für Redis)", "zh": "端口 (默认: MariaDB 3306, Redis 6379)"},
    "database_username": {"en": "USERNAME", "de": "BENUTZERNAME", "zh": "用户名"},
    "database_username_default": {"en": "admin", "de": "admin", "zh": "admin"},
    "database_password": {"en": "PASSWORD", "de": "PASSWORT", "zh": "密码"},
    "database_password_default": {"en": "password", "de": "passwort", "zh": "password"},
    "btn_create_database": {"en": "CREATE DATABASE", "de": "DATENBANK ERSTELLEN", "zh": "创建数据库"},
    
    # Server
    "new_server": {"en": "New Server", "de": "Neuer Server", "zh": "新服务器"},
    "import_folder": {"en": "Import Folder", "de": "Ordner importieren", "zh": "导入文件夹"},
    "import_zip": {"en": "Import ZIP", "de": "ZIP importieren", "zh": "导入ZIP"},
    "server_name_label": {"en": "SERVER NAME", "de": "SERVER NAME", "zh": "服务器名称"},
    "server_name_hint": {"en": "e.g. MySurvival", "de": "z.B. MySurvival", "zh": "例如 MySurvival"},
    "connect_address_label": {"en": "CONNECT ADDRESS", "de": "VERBINDUNGSADRESSE", "zh": "连接地址"},
    "server_type_label": {"en": "SERVER TYPE", "de": "SERVER TYP", "zh": "服务器类型"},
    "version_label": {"en": "VERSION", "de": "VERSION", "zh": "版本"},
    "ram_label": {"en": "RAM", "de": "RAM", "zh": "内存"},
    "execution_mode_native": {"en": "Native Mode (Standalone)", "de": "Nativer Modus (Standalone)", "zh": "原生模式 (独立)"},
    "execution_mode_desc": {"en": "Direct in-app hosting (Experimental)", "de": "Direktes In-App-Hosting (Experimentell)", "zh": "直接应用内托管 (实验性)"},
    "setup_design_label": {"en": "SETUP & DESIGN", "de": "SETUP & DESIGN", "zh": "设置与设计"},
    "setup_koda": {"en": "KodaHosting Design (Auto TAB & Theme)", "de": "KodaHosting Design (Auto TAB & Theme)", "zh": "KodaHosting 设计 (自动TAB和主题)"},
    "setup_manual": {"en": "Alles selbst machen (Clean Install)", "de": "Alles selbst machen (Clean Install)", "zh": "全部自己来 (纯净安装)"},
    "setup_ai": {"en": "AI Server Builder (BETA)", "de": "AI Server Builder (BETA)", "zh": "AI 服务器构建器 (测试版)"},
    "ai_chat_label": {"en": "AI SERVER BUILDER CHAT", "de": "AI SERVER BUILDER CHAT", "zh": "AI 服务器构建器聊天"},
    "ai_chat_hint": {"en": "Talk to Gemini...", "de": "Sprich mit Gemini...", "zh": "与Gemini交谈..."},
    "ai_approve_btn": {"en": "Approve Plan & Create Server", "de": "Plan genehmigen & Server erstellen", "zh": "批准计划并创建服务器"},
    "ai_chat_desc": {"en": "Discuss your server idea. When you are happy with Gemini\\'s plugin choices, click Approve.", "de": "Besprich deine Server-Idee. Wenn du mit Geminis Plugin-Auswahl zufrieden bist, klicke auf Genehmigen.", "zh": "讨论您的服务器想法。当您对Gemini的插件选择满意时，请点击批准。"},
    "theme_color_label": {"en": "THEME COLOR", "de": "DESIGN FARBE", "zh": "主题颜色"},
    "tap_to_change": {"en": "Tap to change", "de": "Tippen zum Ändern", "zh": "点击更改"},
    "btn_create_server": {"en": "Create Server", "de": "Server erstellen", "zh": "创建服务器"},
    "preparing_java": {"en": "Preparing Java...", "de": "Java wird vorbereitet...", "zh": "正在准备Java..."}
}

layout_replacements = {
    # Create DB
    r'android:text="New Database"': r'android:text="@string/new_database"',
    r'android:text="DATABASE NAME"': r'android:text="@string/database_name"',
    r'android:hint="e.g. LobbyDB"': r'android:hint="@string/database_name_hint"',
    r'android:text="DATABASE ENGINE"': r'android:text="@string/database_engine"',
    r'android:text="PORT"': r'android:text="@string/database_port"',
    r'android:hint="Port \(default: 3306 for MariaDB, 6379 for Redis\)"': r'android:hint="@string/database_port_hint"',
    r'android:text="USERNAME"': r'android:text="@string/database_username"',
    r'android:text="admin"': r'android:text="@string/database_username_default"',
    r'android:text="PASSWORD"': r'android:text="@string/database_password"',
    r'android:text="password"': r'android:text="@string/database_password_default"',
    r'android:text="CREATE DATABASE"': r'android:text="@string/btn_create_database"',
    
    # Create Server
    r'android:text="New Server"': r'android:text="@string/new_server"',
    r'android:text="Import Folder"': r'android:text="@string/import_folder"',
    r'android:text="Import ZIP"': r'android:text="@string/import_zip"',
    r'android:text="SERVER NAME"': r'android:text="@string/server_name_label"',
    r'android:hint="e.g. MySurvival"': r'android:hint="@string/server_name_hint"',
    r'android:text="CONNECT ADDRESS"': r'android:text="@string/connect_address_label"',
    r'android:text="SERVER TYPE"': r'android:text="@string/server_type_label"',
    r'android:text="VERSION"': r'android:text="@string/version_label"',
    r'android:text="RAM"': r'android:text="@string/ram_label"',
    r'android:text="Native Mode \(Standalone\)"': r'android:text="@string/execution_mode_native"',
    r'android:text="Direct in-app hosting \(Experimental\)"': r'android:text="@string/execution_mode_desc"',
    r'android:text="SETUP &amp; DESIGN"': r'android:text="@string/setup_design_label"',
    r'android:text="KodaHosting Design \(Auto TAB &amp; Theme\)"': r'android:text="@string/setup_koda"',
    r'android:text="Alles selbst machen \(Clean Install\)"': r'android:text="@string/setup_manual"',
    r'android:text="AI Server Builder \(BETA\)"': r'android:text="@string/setup_ai"',
    r'android:text="AI SERVER BUILDER CHAT"': r'android:text="@string/ai_chat_label"',
    r'android:hint="Talk to Gemini\.\.\."': r'android:hint="@string/ai_chat_hint"',
    r'android:text="Approve Plan &amp; Create Server"': r'android:text="@string/ai_approve_btn"',
    r'android:text="Discuss your server idea\. When you are happy with Gemini\'s plugin choices, click Approve\."': r'android:text="@string/ai_chat_desc"',
    r'android:text="THEME COLOR"': r'android:text="@string/theme_color_label"',
    r'android:text="Tap to change"': r'android:text="@string/tap_to_change"',
    r'android:text="Create Server"': r'android:text="@string/btn_create_server"',
    r'android:text="Preparing Java\.\.\."': r'android:text="@string/preparing_java"'
}

app_dir = r"C:\Users\Home\KodaHosting\KodaNetwork\app\src\main"
layouts = [
    os.path.join(app_dir, r"res\layout\activity_create_database.xml"),
    os.path.join(app_dir, r"res\layout\activity_create_server.xml")
]

# Update layouts
for layout in layouts:
    if os.path.exists(layout):
        with open(layout, 'r', encoding='utf-8') as f:
            content = f.read()
        for k, v in layout_replacements.items():
            content = re.sub(k, v, content)
        with open(layout, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {layout}")

def add_strings(file_path, lang):
    if not os.path.exists(file_path):
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write("<?xml version='1.0' encoding='utf-8'?>\n<resources>\n</resources>")
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        
    insertions = []
    for k, v in strings_map.items():
        if f'name="{k}"' not in content:
            insertions.append(f'    <string name="{k}">{v[lang]}</string>')
            
    if insertions:
        # insert before </resources>
        insert_text = "\n".join(insertions) + "\n</resources>"
        content = content.replace("</resources>", insert_text)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Added {len(insertions)} strings to {file_path}")

add_strings(os.path.join(app_dir, r"res\values\strings.xml"), "en")
add_strings(os.path.join(app_dir, r"res\values-de\strings.xml"), "de")
add_strings(os.path.join(app_dir, r"res\values-zh-rCN\strings.xml"), "zh")

print("Done!")
