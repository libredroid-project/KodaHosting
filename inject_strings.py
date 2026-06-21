import xml.etree.ElementTree as ET
import os

keys = {
    "minecraft_account": "MINECRAFT ACCOUNT",
    "link_minecraft_instructions": "Link your Minecraft account to enable /join",
    "not_linked": "Not linked",
    "link_steps": "1. Click 'Link Account'\n2. Join lobby.kodanetwork.eu\n3. Type /link <code>",
    "link_new_account": "Link New Account",
    "appearance": "APPEARANCE",
    "theme_mode": "Theme Mode",
    "theme_dark": "Dark",
    "theme_light": "Light",
    "theme_auto": "Auto",
    "haptic_feedback": "Haptic Feedback",
    "on": "ON",
    "off": "OFF",
    "screensaver": "SCREENSAVER",
    "style": "Style",
    "squares": "Squares",
    "black": "Black",
    "afk_timeout": "AFK Timeout",
    "min_1": "1 Min",
    "min_2": "2 Min",
    "min_5": "5 Min",
    "network_security": "NETWORK & SECURITY",
    "network_alarm_off": "Network Alarm: OFF",
    "lobby_remote_on": "Lobby Remote: ON",
    "beta_jni_off": "Beta: JNI Embedded JVM: OFF",
    "twofa_disabled": "2FA Status: Disabled",
    "enable_2fa": "Enable 2FA",
    "set_2fa_password": "Set 2FA Password",
    "biometrics": "BIOMETRICS",
    "enable_biometric": "Enable Biometric Unlock",
    "bio_resume": "On App Resume / Screen Unlock",
    "bio_cold_start": "On App Cold Start",
    "bio_server_click": "Before Opening a Server",
    "bio_create_server": "Before Creating a Server",
    "account": "ACCOUNT",
    "loading": "Loading..."
}

def add_keys_to_xml(file_path):
    if not os.path.exists(file_path):
        return
    tree = ET.parse(file_path)
    root = tree.getroot()
    existing_keys = set(child.attrib.get('name') for child in root)
    
    added = False
    for k, v in keys.items():
        if k not in existing_keys:
            el = ET.SubElement(root, 'string')
            el.set('name', k)
            el.text = v
            added = True
            
    if added:
        tree.write(file_path, encoding='utf-8', xml_declaration=True)
        print(f"Updated {file_path}")

add_keys_to_xml("app/src/main/res/values/strings.xml")
