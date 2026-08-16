
import xml.etree.ElementTree as ET

def update_xml(path, strings_dict):
    tree = ET.parse(path)
    root = tree.getroot()
    existing = {child.attrib["name"] for child in root if "name" in child.attrib}
    
    for k, v in strings_dict.items():
        if k not in existing:
            el = ET.Element("string", {"name": k})
            el.text = v
            root.append(el)
            
    tree.write(path, encoding="utf-8", xml_declaration=True)

en_strings = {
    "pm_heal": "Heal",
    "pm_starve": "Starve",
    "pm_kill": "Kill",
    "pm_wipe": "Wipe",
    "pm_wipe_title": "Wipe Player Data",
    "pm_wipe_desc": "Are you sure? This will kick the player and delete their inventory and stats.",
    "pm_whitelisted": "Whitelisted",
    "pm_inventory": "Inventory",
    "pm_deaths": "Deaths:",
    "pm_blocks_mined": "Blocks Mined:",
    "pm_hours_played": "Hours Played:",
    "pm_mobs_killed": "Mobs Killed:",
    "pm_damage_taken": "Damage Taken:",
    "pm_online": "(Online)",
    "pm_offline": "(Offline)",
    "pm_wiped_toast": "Player data wiped",
    "pm_action_toast": "Executed action on player"
}

zh_strings = {
    "pm_heal": "?? (Heal)",
    "pm_starve": "?? (Starve)",
    "pm_kill": "?? (Kill)",
    "pm_wipe": "???? (Wipe)",
    "pm_wipe_title": "??????",
    "pm_wipe_desc": "??????????????????????????",
    "pm_whitelisted": "??? (Whitelisted)",
    "pm_inventory": "??? (Inventory)",
    "pm_deaths": "????:",
    "pm_blocks_mined": "?????:",
    "pm_hours_played": "???? (??):",
    "pm_mobs_killed": "?????:",
    "pm_damage_taken": "????:",
    "pm_online": "(??)",
    "pm_offline": "(??)",
    "pm_wiped_toast": "???????",
    "pm_action_toast": "????????"
}

update_xml("app/src/main/res/values/strings.xml", en_strings)
try:
    update_xml("app/src/main/res/values-zh/strings.xml", zh_strings)
except Exception as e:
    print(e)
    # create the file if it doesn"t exist
    import os
    os.makedirs("app/src/main/res/values-zh", exist_ok=True)
    with open("app/src/main/res/values-zh/strings.xml", "w", encoding="utf-8") as f:
        f.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        for k, v in zh_strings.items():
            f.write(f"    <string name=\"{k}\">{v}</string>\n")
        f.write("</resources>")

print("Strings updated")

