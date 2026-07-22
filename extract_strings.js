const fs = require('fs');
const path = require('path');

const filePath = 'app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java';
let content = fs.readFileSync(filePath, 'utf8');

// We are going to replace specific known patterns
const replacements = [
    { target: '"STARTING SERVER..."', key: 'sd_starting_server' },
    { target: '"AUTO-SETUP RUNNING..."', key: 'sd_auto_setup_running' },
    { target: '"checking java."', key: 'sd_checking_java' },
    { target: '"Java will be installed automatically at start (JDK 25)"', key: 'sd_java_auto_install' },
    { target: '"Updates Available!"', key: 'sd_updates_available' },
    { target: '"Check for Updates"', key: 'sd_check_updates' },
    { target: '"JOIN ADRESSE"', key: 'sd_join_address' },
    { target: '"CHANGE SUBDOMAIN"', key: 'sd_change_subdomain' },
    { target: '"Gib eine neue Join-Adresse ein (nur Buchstaben, Zahlen und Bindestriche)."', key: 'sd_change_subdomain_hint' },
    { target: '"UNLOCK MANUAL EDITING (PRAETOR)"', key: 'sd_unlock_manual_editing' },
    { target: '"RESTORE AUTO-CONFIG"', key: 'sd_restore_auto_config' },
    { target: '"Native Mode: Active"', key: 'sd_native_mode_active' },
    { target: '"Tunnel: not assigned"', key: 'sd_tunnel_not_assigned' },
    { target: '"Domain: not linked"', key: 'sd_domain_not_linked' },
    { target: '"Tunnel: starting..."', key: 'sd_tunnel_starting' },
    { target: '"Tunnel: running (address pending)"', key: 'sd_tunnel_running_pending' },
    { target: '"LINK CUSTOM DOMAIN"', key: 'sd_link_custom_domain' },
    
    // Toasts and Dialogs
    { target: '"Server Icon aktualisiert"', key: 'sd_toast_icon_updated' },
    { target: '"Fehler beim Laden des Bildes: "', key: 'sd_toast_error_loading_image' },
    { target: '"Import..."', key: 'sd_dialog_import_title' },
    { target: '"Please install the server .jar first (Settings-Tab)"', key: 'sd_toast_install_jar_first' },
    { target: '"Force Kill?"', key: 'sd_dialog_force_kill_title' },
    { target: '"The world files may won\\uFFFDt be saved. Continue?"', key: 'sd_dialog_force_kill_msg' },
    { target: '"The world files may won\'t be saved. Continue?"', key: 'sd_dialog_force_kill_msg' },
    { target: '"The world files may wont be saved. Continue?"', key: 'sd_dialog_force_kill_msg' },
    { target: '"Server aufgeweckt!"', key: 'sd_toast_server_woken' },
    { target: '"Fehler: "', key: 'sd_toast_error_prefix' },
    { target: '"Ung\\uFFFDltige Adresse!"', key: 'sd_toast_invalid_address' },
    { target: '"Ung\\u00fcltige Adresse!"', key: 'sd_toast_invalid_address' },
    { target: '"Ungültige Adresse!"', key: 'sd_toast_invalid_address' },
    { target: '"Ungltige Adresse!"', key: 'sd_toast_invalid_address' },
    { target: '"Du kannst die Join-Adresse nur einmal alle 24 Stunden \\uFFFDndern."', key: 'sd_toast_subdomain_limit' },
    { target: '"Du kannst die Join-Adresse nur einmal alle 24 Stunden ändern."', key: 'sd_toast_subdomain_limit' },
    { target: '"Du kannst die Join-Adresse nur einmal alle 24 Stunden ndern."', key: 'sd_toast_subdomain_limit' },
    { target: '"Join-Adresse aktualisiert! (Wird beim n\\uFFFDchsten Aufwecken registriert)"', key: 'sd_toast_subdomain_updated_next_boot' },
    { target: '"Join-Adresse aktualisiert! (Wird beim nächsten Aufwecken registriert)"', key: 'sd_toast_subdomain_updated_next_boot' },
    { target: '"Join-Adresse aktualisiert! (Wird beim nchsten Aufwecken registriert)"', key: 'sd_toast_subdomain_updated_next_boot' },
    { target: '"Log kopiert!"', key: 'sd_toast_log_copied' },
    { target: '"Auto-Config restored."', key: 'sd_toast_auto_config_restored' },
    { target: '"Manual editing locked! Click \'Unlock Manual Editing\' at the top of the plugin folder."', key: 'sd_toast_manual_locked' },
    { target: '"Aktion: Einf\\uFFFdgen"', key: 'sd_dialog_paste_action' },
    { target: '"Aktion: Einfügen"', key: 'sd_dialog_paste_action' },
    { target: '"Aktion: Einfgen"', key: 'sd_dialog_paste_action' },
    { target: '"Umbenennen"', key: 'sd_dialog_rename' },
    { target: '"Kopiert. Gehe in einen Ordner und halte \'..\' gedr\\uFFFdckt zum Einf\\uFFFdgen."', key: 'sd_toast_copied_hint' },
    { target: '"Kopiert. Gehe in einen Ordner und halte \'..\' gedrückt zum Einfügen."', key: 'sd_toast_copied_hint' },
    { target: '"Kopiert. Gehe in einen Ordner und halte \'..\' gedrckt zum Einfgen."', key: 'sd_toast_copied_hint' },
    { target: '"Ausgeschnitten. Gehe in einen Ordner und halte \'..\' gedr\\uFFFdckt zum Einf\\uFFFdgen."', key: 'sd_toast_cut_hint' },
    { target: '"Ausgeschnitten. Gehe in einen Ordner und halte \'..\' gedrückt zum Einfügen."', key: 'sd_toast_cut_hint' },
    { target: '"Ausgeschnitten. Gehe in einen Ordner und halte \'..\' gedrckt zum Einfgen."', key: 'sd_toast_cut_hint' },
    { target: '"Server Icon entfernt"', key: 'sd_toast_icon_removed' },
    { target: '"Allocating proxy port..."', key: 'sd_toast_allocating_proxy' },
    { target: '"Error: "', key: 'sd_toast_error_en_prefix' },
    { target: '"Allocating voicechat port..."', key: 'sd_toast_allocating_voice' },
    { target: '"Zipping server, please wait..."', key: 'sd_toast_zipping' },
    { target: '"Export failed: "', key: 'sd_toast_export_failed' },
    { target: '"Waking up server..."', key: 'sd_toast_waking_up' },
    { target: '"Fehler beim Aufwecken: "', key: 'sd_toast_wakeup_error' },
    { target: '"Bitte Server zuerst stoppen!"', key: 'sd_toast_stop_server_first' },
    { target: '"Keine Internetverbindung"', key: 'sd_toast_no_internet' },
    { target: '" installiert!"', key: 'sd_toast_installed_suffix' },
    { target: '"Lade Versionen..."', key: 'sd_dialog_loading_versions' },
    { target: '"Keine kompatible Version gefunden."', key: 'sd_toast_no_compatible_version' },
    { target: '"Download-Fehler"', key: 'sd_toast_download_error' },
    { target: '"Fehler beim Laden"', key: 'sd_toast_loading_error' },
    { target: '"Search error: "', key: 'sd_toast_search_error' },
    { target: '"Playit could not be started"', key: 'sd_toast_playit_error' },
    { target: '"Join-Adresse aktualisiert!"', key: 'sd_toast_subdomain_updated' },
    { target: '"DNS request failed: "', key: 'sd_toast_dns_failed' },
    { target: '"Ung\\uFFFDltige Adresse! Mindestens 3 Zeichen (a-z, 0-9, -)."', key: 'sd_toast_invalid_address_length' },
    { target: '"Ungültige Adresse! Mindestens 3 Zeichen (a-z, 0-9, -)."', key: 'sd_toast_invalid_address_length' },
    { target: '"Ungltige Adresse! Mindestens 3 Zeichen (a-z, 0-9, -)."', key: 'sd_toast_invalid_address_length' },
    { target: '"Unlinked from Custom Domain. Reverting to default."', key: 'sd_toast_unlinked_domain' },
    { target: '"Addon removed and files cleaned up."', key: 'sd_toast_addon_removed' },
    { target: '"Download fehlgeschlagen"', key: 'sd_dialog_download_failed' },
    { target: '"File(s) already exist"', key: 'sd_dialog_files_exist' },
    { target: '"The following files already exist:\\n\\n  "', key: 'sd_dialog_files_exist_msg_1' },
    { target: '"\\n\\nOverwrite them?"', key: 'sd_dialog_files_exist_msg_2' },
    { target: '"Importing folder..."', key: 'sd_toast_importing_folder' },
    { target: '" copied!"', key: 'sd_toast_copied_suffix' },
    { target: '"Durch das Starten dieses Minecraft-Servers akzeptierst du die Mojang/Microsoft EULA.\\n\\n" +\n            "Dies beinhaltet:\\n" +\n            "• Du darfst keinen Zugang zu Gameplay-Features verkaufen\\n" +\n            "• Du darfst keine Minecraft-Inhalte umverteilen\\n" +\n            "• Server müssen den EULA-Richtlinien entsprechen\\n\\n" +\n            "Vollständige EULA:\\nhttps://aka.ms/MinecraftEULA"', key: 'sd_eula_body' }
];

let stringsXml = '';

for (const rep of replacements) {
    let cleanVal = rep.target.replace(/^"|"$/g, '').replace(/\\n/g, '\n');
    cleanVal = cleanVal.replace(/\\u00fc|ü/g, 'ü').replace(/\\u00e4|ä/g, 'ä').replace(/\\u00f6|ö/g, 'ö');
    // escape quotes and apostrophes for xml
    cleanVal = cleanVal.replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    if (rep.key === 'sd_eula_body') {
        cleanVal = "Durch das Starten dieses Minecraft-Servers akzeptierst du die Mojang/Microsoft EULA.\\n\\nDies beinhaltet:\\n• Du darfst keinen Zugang zu Gameplay-Features verkaufen\\n• Du darfst keine Minecraft-Inhalte umverteilen\\n• Server m\\'ü\\'ssen den EULA-Richtlinien entsprechen\\n\\nVollständige EULA:\\nhttps://aka.ms/MinecraftEULA".replace(/'/g, "\\'");
    }
    
    stringsXml += `    <string name="${rep.key}">${cleanVal}</string>\n`;
    
    // Replace in java content
    // Careful with regex escaping
    let targetRegex;
    if (rep.key === 'sd_eula_body') {
        content = content.replace(
            /"Durch das Starten dieses Minecraft-Servers akzeptierst du die Mojang\/Microsoft EULA.\\n\\n" \+\s*"Dies beinhaltet:\\n" \+\s*"• Du darfst keinen Zugang zu Gameplay-Features verkaufen\\n" \+\s*"• Du darfst keine Minecraft-Inhalte umverteilen\\n" \+\s*"• Server müssen den EULA-Richtlinien entsprechen\\n\\n" \+\s*"Vollständige EULA:\\nhttps:\/\/aka\.ms\/MinecraftEULA"/g,
            `getString(R.string.${rep.key})`
        );
    } else {
        targetRegex = new RegExp(rep.target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
        content = content.replace(targetRegex, `getString(R.string.${rep.key})`);
    }
}

// Special case for Activity context needing getResources() sometimes if not in Activity
content = content.replace(/getString\(R\.string\./g, 'getString(R.string.'); 

fs.writeFileSync(filePath, content);
fs.writeFileSync('extracted_strings.xml', stringsXml);
console.log('Done!');
