import re
import os

files_to_check = [
    'app/src/main/res/layout/activity_server_detail_terminal.xml',
    'app/src/main/res/layout/item_server_terminal.xml',
    'app/src/main/res/layout/dialog_praetor_files_action.xml',
    'app/src/main/res/layout/activity_file_editor.xml',
    'app/src/main/res/layout/bottom_sheet_player_list.xml',
    'app/src/main/res/layout/bottom_sheet_player_manage.xml',
    'app/src/main/res/layout/dialog_modrinth_search.xml',
    'app/src/main/res/layout/item_modrinth_project.xml',
    'app/src/main/res/layout/item_player_row.xml'
]

def inject_bg_xml(xml_content):
    def repl_btn(match):
        block = match.group(0)
        
        # Determine background based on button id/text
        bg = "@drawable/bg_mc_button_dark"
        if re.search(r'id="@+id/.*?(start|save|apply|add|install|create|confirm|yes).*?"', block, re.IGNORECASE) or re.search(r'text=".*?(start|save|apply|add|install|create|confirm|yes).*?"', block, re.IGNORECASE):
            bg = "@drawable/bg_mc_button_green"
        elif re.search(r'id="@+id/.*?(stop|kill|delete|remove|cancel|no|ban).*?"', block, re.IGNORECASE) or re.search(r'text=".*?(stop|kill|delete|remove|cancel|no|ban).*?"', block, re.IGNORECASE):
            bg = "@drawable/bg_mc_button_red"
        elif re.search(r'id="@+id/.*?(restart|edit|update|change).*?"', block, re.IGNORECASE) or re.search(r'text=".*?(restart|edit|update|change).*?"', block, re.IGNORECASE):
            bg = "@drawable/bg_mc_button_orange"

        # Apply only if not already having bg_mc_button
        if 'bg_mc_button' not in block:
            block = re.sub(r'style="[^"]+"', '', block)
            block = re.sub(r'app:backgroundTint="[^"]*"', '', block)
            block = re.sub(r'android:backgroundTint="[^"]*"', '', block)
            block = re.sub(r'android:background="[^"]*"', '', block)
            block = block.replace('<com.google.android.material.button.MaterialButton', f'<com.google.android.material.button.MaterialButton\n        android:background="{bg}"\n        app:backgroundTint="@null"')
            block = block.replace('<Button', f'<Button\n        android:background="{bg}"\n        app:backgroundTint="@null"')
        return block
    
    xml_content = re.sub(r'<com\.google\.android\.material\.button\.MaterialButton[\s\S]*?(?=>|/>)(>|/>)', repl_btn, xml_content)
    xml_content = re.sub(r'<Button[\s\S]*?(?=>|/>)(>|/>)', repl_btn, xml_content)
    return xml_content

for file_path in files_to_check:
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            content = f.read()
        
        new_content = inject_bg_xml(content)
        
        # Additionally, if we want to change panel backgrounds to dark 3D:
        # e.g., in item_server_terminal.xml ll_terminal_border
        if "item_server_terminal.xml" in file_path:
            new_content = re.sub(r'android:id="@+id/ll_terminal_border"\s*android:layout_width="match_parent"\s*android:layout_height="wrap_content"\s*android:orientation="vertical"\s*android:background="#111111"',
                                 r'android:id="@+id/ll_terminal_border"\n        android:layout_width="match_parent"\n        android:layout_height="wrap_content"\n        android:orientation="vertical"\n        android:background="@drawable/bg_mc_button_dark"', new_content)
        
        with open(file_path, 'w') as f:
            f.write(new_content)

print("Applied 3D colorful buttons universally!")
