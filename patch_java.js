const fs = require('fs');

let javaFile = 'app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java';
let javaContent = fs.readFileSync(javaFile, 'utf8');

const replacements = [
    { from: '"STARTING SERVER..."', to: 'getString(R.string.sd_starting_server)' },
    { from: '"AUTO-SETUP RUNNING..."', to: 'getString(R.string.sd_auto_setup_running)' },
    { from: '"checking java."', to: 'getString(R.string.sd_checking_java)' },
    { from: '"Java will be installed automatically at start (JDK 25)"', to: 'getString(R.string.sd_java_auto_install)' },
    { from: '"Updates Available!"', to: 'getString(R.string.sd_updates_available)' },
    { from: '"Check for Updates"', to: 'getString(R.string.sd_check_updates)' },
    { from: '"JOIN ADRESSE"', to: 'getString(R.string.sd_join_address)' },
    { from: '"CHANGE SUBDOMAIN"', to: 'getString(R.string.sd_change_subdomain)' },
    { from: '"Gib eine neue Join-Adresse ein (nur Buchstaben, Zahlen und Bindestriche)."', to: 'getString(R.string.sd_change_subdomain_hint)' },
    { from: '"UNLOCK MANUAL EDITING (PRAETOR)"', to: 'getString(R.string.sd_unlock_manual_editing)' },
    { from: '"RESTORE AUTO-CONFIG"', to: 'getString(R.string.sd_restore_auto_config)' },
    { from: '"Native Mode: Active"', to: 'getString(R.string.sd_native_mode_active)' },
    { from: '"Tunnel: not assigned"', to: 'getString(R.string.sd_tunnel_not_assigned)' },
    { from: '"Domain: not linked"', to: 'getString(R.string.sd_domain_not_linked)' },
    { from: '"Tunnel: starting..."', to: 'getString(R.string.sd_tunnel_starting)' },
    { from: '"Tunnel: running (address pending)"', to: 'getString(R.string.sd_tunnel_running_pending)' },
    { from: '"LINK CUSTOM DOMAIN"', to: 'getString(R.string.sd_link_custom_domain)' },
    
    // Toasts and strings
    { from: '"Server Icon aktualisiert"', to: 'getString(R.string.sd_toast_icon_updated)' },
    { from: '"Fehler beim Laden des Bildes: "', to: 'getString(R.string.sd_toast_error_loading_image)' },
    { from: '"Import..."', to: 'getString(R.string.sd_dialog_import_title)' },
    { from: '"Please install the server .jar first (Settings-Tab)"', to: 'getString(R.string.sd_toast_install_jar_first)' },
    { from: '"Force Kill?"', to: 'getString(R.string.sd_dialog_force_kill_title)' },
    { from: '"The world files may won\'t be saved. Continue?"', to: 'getString(R.string.sd_dialog_force_kill_msg)' },
    { from: '"Server aufgeweckt!"', to: 'getString(R.string.sd_toast_server_woken)' },
    { from: '"Ungültige Adresse!"', to: 'getString(R.string.sd_toast_invalid_address)' },
    { from: '"Du kannst die Join-Adresse nur einmal alle 24 Stunden ändern."', to: 'getString(R.string.sd_toast_subdomain_limit)' },
    { from: '"Join-Adresse aktualisiert! (Wird beim nächsten Aufwecken registriert)"', to: 'getString(R.string.sd_toast_subdomain_updated_next_boot)' },
    { from: '"Join-Adresse aktualisiert!"', to: 'getString(R.string.sd_toast_subdomain_updated)' },
    { from: '"Log kopiert!"', to: 'getString(R.string.sd_toast_log_copied)' },
    { from: '"Auto-Config restored."', to: 'getString(R.string.sd_toast_auto_config_restored)' },
    { from: '"Manual editing locked! Click \\"Unlock Manual Editing\\" at the top of the plugin folder."', to: 'getString(R.string.sd_toast_manual_locked)' },
    { from: '"Aktion: Einfügen"', to: 'getString(R.string.sd_dialog_paste_action)' },
    { from: '"Umbenennen"', to: 'getString(R.string.sd_dialog_rename)' },
    { from: '"Kopiert. Gehe in einen Ordner und halte \'..\' gedrückt zum Einfügen."', to: 'getString(R.string.sd_toast_copied_hint)' },
    { from: '"Ausgeschnitten. Gehe in einen Ordner und halte \'..\' gedrückt zum Einfügen."', to: 'getString(R.string.sd_toast_cut_hint)' },
    { from: '"Server Icon entfernt"', to: 'getString(R.string.sd_toast_icon_removed)' },
    { from: '"Allocating proxy port..."', to: 'getString(R.string.sd_toast_allocating_proxy)' },
    { from: '"Allocating voicechat port..."', to: 'getString(R.string.sd_toast_allocating_voice)' },
    { from: '"Zipping server, please wait..."', to: 'getString(R.string.sd_toast_zipping)' },
    { from: '"Export failed: "', to: 'getString(R.string.sd_toast_export_failed)' },
    { from: '"Waking up server..."', to: 'getString(R.string.sd_toast_waking_up)' },
    { from: '"Fehler beim Aufwecken: "', to: 'getString(R.string.sd_toast_wakeup_error)' },
    { from: '"Bitte Server zuerst stoppen!"', to: 'getString(R.string.sd_toast_stop_server_first)' },
    { from: '"Keine Internetverbindung"', to: 'getString(R.string.sd_toast_no_internet)' },
    { from: '" installiert!"', to: 'getString(R.string.sd_toast_installed_suffix)' },
    { from: '"Lade Versionen..."', to: 'getString(R.string.sd_dialog_loading_versions)' },
    { from: '"Keine kompatible Version gefunden."', to: 'getString(R.string.sd_toast_no_compatible_version)' },
    { from: '"Download-Fehler"', to: 'getString(R.string.sd_toast_download_error)' },
    { from: '"Fehler beim Laden"', to: 'getString(R.string.sd_toast_loading_error)' },
    { from: '"Search error: "', to: 'getString(R.string.sd_toast_search_error)' },
    { from: '"Playit could not be started"', to: 'getString(R.string.sd_toast_playit_error)' },
    { from: '"DNS request failed: "', to: 'getString(R.string.sd_toast_dns_failed)' },
    { from: '"Ungültige Adresse! Mindestens 3 Zeichen (a-z, 0-9, -)."', to: 'getString(R.string.sd_toast_invalid_address_length)' },
    { from: '"Unlinked from Custom Domain. Reverting to default."', to: 'getString(R.string.sd_toast_unlinked_domain)' },
    { from: '"Addon removed and files cleaned up."', to: 'getString(R.string.sd_toast_addon_removed)' },
    { from: '"Download fehlgeschlagen"', to: 'getString(R.string.sd_dialog_download_failed)' },
    { from: '"File(s) already exist"', to: 'getString(R.string.sd_dialog_files_exist)' },
    { from: '"The following files already exist:\\n\\n  "', to: 'getString(R.string.sd_dialog_files_exist_msg_1)' },
    { from: '"\\n\\nOverwrite them?"', to: 'getString(R.string.sd_dialog_files_exist_msg_2)' },
    { from: '"Importing folder..."', to: 'getString(R.string.sd_toast_importing_folder)' },
    { from: '" copied!"', to: 'getString(R.string.sd_toast_copied_suffix)' }
];

for (let r of replacements) {
    javaContent = javaContent.split(r.from).join(r.to);
}

fs.writeFileSync(javaFile, javaContent);
console.log("Java file patched.");
