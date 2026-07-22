const fs = require('fs');
const path = require('path');

const strings = [
  { key: 'sd_starting_server', en: 'STARTING SERVER...', de: 'STARTE SERVER...', zh: '正在启动服务器...' },
  { key: 'sd_auto_setup_running', en: 'AUTO-SETUP RUNNING...', de: 'AUTO-SETUP LÄUFT...', zh: '自动设置运行中...' },
  { key: 'sd_checking_java', en: 'checking java.', de: 'Prüfe Java...', zh: '正在检查 Java...' },
  { key: 'sd_java_auto_install', en: 'Java will be installed automatically at start (JDK 25)', de: 'Java wird beim Start automatisch installiert (JDK 25)', zh: 'Java 将在启动时自动安装 (JDK 25)' },
  { key: 'sd_updates_available', en: 'Updates Available!', de: 'Updates Verfügbar!', zh: '有可用更新！' },
  { key: 'sd_check_updates', en: 'Check for Updates', de: 'Nach Updates suchen', zh: '检查更新' },
  { key: 'sd_join_address', en: 'JOIN ADDRESS', de: 'JOIN ADRESSE', zh: '加入地址' },
  { key: 'sd_change_subdomain', en: 'CHANGE SUBDOMAIN', de: 'JOIN ADRESSE ÄNDERN', zh: '更改子域名' },
  { key: 'sd_change_subdomain_hint', en: 'Enter a new Join Address (only letters, numbers, and hyphens).', de: 'Gib eine neue Join-Adresse ein (nur Buchstaben, Zahlen und Bindestriche).', zh: '输入新的加入地址（仅限字母、数字和连字符）。' },
  { key: 'sd_unlock_manual_editing', en: 'UNLOCK MANUAL EDITING (PRAETOR)', de: 'UNLOCK MANUAL EDITING (PRAETOR)', zh: '解锁手动编辑 (PRAETOR)' },
  { key: 'sd_restore_auto_config', en: 'RESTORE AUTO-CONFIG', de: 'RESTORE AUTO-CONFIG', zh: '恢复自动配置' },
  { key: 'sd_native_mode_active', en: 'Native Mode: Active', de: 'Native Mode: Active', zh: '原生模式：激活' },
  { key: 'sd_tunnel_not_assigned', en: 'Tunnel: not assigned', de: 'Tunnel: not assigned', zh: '隧道：未分配' },
  { key: 'sd_domain_not_linked', en: 'Domain: not linked', de: 'Domain: not linked', zh: '域名：未关联' },
  { key: 'sd_tunnel_starting', en: 'Tunnel: starting...', de: 'Tunnel: starting...', zh: '隧道：正在启动...' },
  { key: 'sd_tunnel_running_pending', en: 'Tunnel: running (address pending)', de: 'Tunnel: running (address pending)', zh: '隧道：运行中（地址待定）' },
  { key: 'sd_link_custom_domain', en: 'LINK CUSTOM DOMAIN', de: 'EIGENE DOMAIN VERLINKEN', zh: '关联自定义域名' },
  
  { key: 'sd_toast_icon_updated', en: 'Server Icon updated', de: 'Server Icon aktualisiert', zh: '服务器图标已更新' },
  { key: 'sd_toast_error_loading_image', en: 'Error loading image: ', de: 'Fehler beim Laden des Bildes: ', zh: '加载图像时出错：' },
  { key: 'sd_dialog_import_title', en: 'Import...', de: 'Import...', zh: '导入...' },
  { key: 'sd_toast_install_jar_first', en: 'Please install the server .jar first (Settings-Tab)', de: 'Bitte installiere zuerst die Server .jar (Einstellungen-Tab)', zh: '请先安装服务器 .jar（设置选项卡）' },
  { key: 'sd_dialog_force_kill_title', en: 'Force Kill?', de: 'Sofort Beenden?', zh: '强制停止？' },
  { key: 'sd_dialog_force_kill_msg', en: 'The world files may not be saved. Continue?', de: 'Die Weltdaten werden eventuell nicht gespeichert. Fortfahren?', zh: '世界文件可能不会保存。继续吗？' },
  { key: 'sd_toast_server_woken', en: 'Server woken up!', de: 'Server aufgeweckt!', zh: '服务器已唤醒！' },
  { key: 'sd_toast_error_prefix', en: 'Error: ', de: 'Fehler: ', zh: '错误：' },
  { key: 'sd_toast_invalid_address', en: 'Invalid address!', de: 'Ungültige Adresse!', zh: '地址无效！' },
  { key: 'sd_toast_invalid_address_length', en: 'Invalid address! Minimum 3 characters (a-z, 0-9, -).', de: 'Ungültige Adresse! Mindestens 3 Zeichen (a-z, 0-9, -).', zh: '无效地址！至少3个字符（a-z，0-9，-）。' },
  { key: 'sd_toast_subdomain_limit', en: 'You can only change the Join Address once every 24 hours.', de: 'Du kannst die Join-Adresse nur einmal alle 24 Stunden ändern.', zh: '您每 24 小时只能更改一次加入地址。' },
  { key: 'sd_toast_subdomain_updated_next_boot', en: 'Join Address updated! (Will be registered on next wakeup)', de: 'Join-Adresse aktualisiert! (Wird beim nächsten Aufwecken registriert)', zh: '加入地址已更新！（将在下次唤醒时注册）' },
  { key: 'sd_toast_subdomain_updated', en: 'Join Address updated!', de: 'Join-Adresse aktualisiert!', zh: '加入地址已更新！' },
  { key: 'sd_toast_log_copied', en: 'Log copied!', de: 'Log kopiert!', zh: '日志已复制！' },
  { key: 'sd_toast_auto_config_restored', en: 'Auto-Config restored.', de: 'Auto-Config wiederhergestellt.', zh: '自动配置已恢复。' },
  { key: 'sd_toast_manual_locked', en: 'Manual editing locked! Click "Unlock Manual Editing" at the top of the plugin folder.', de: 'Manuelle Bearbeitung gesperrt! Klicke oben im Plugin-Ordner auf "Unlock Manual Editing".', zh: '手动编辑已锁定！点击插件文件夹顶部的“解锁手动编辑”。' },
  { key: 'sd_dialog_paste_action', en: 'Action: Paste', de: 'Aktion: Einfügen', zh: '操作：粘贴' },
  { key: 'sd_dialog_rename', en: 'Rename', de: 'Umbenennen', zh: '重命名' },
  { key: 'sd_toast_copied_hint', en: "Copied. Go to a folder and hold '..' to paste.", de: "Kopiert. Gehe in einen Ordner und halte '..' gedrückt zum Einfügen.", zh: "已复制。转到文件夹并长按 '..' 进行粘贴。" },
  { key: 'sd_toast_cut_hint', en: "Cut. Go to a folder and hold '..' to paste.", de: "Ausgeschnitten. Gehe in einen Ordner und halte '..' gedrückt zum Einfügen.", zh: "已剪切。转到文件夹并长按 '..' 进行粘贴。" },
  { key: 'sd_toast_icon_removed', en: 'Server Icon removed', de: 'Server Icon entfernt', zh: '服务器图标已删除' },
  { key: 'sd_toast_allocating_proxy', en: 'Allocating proxy port...', de: 'Alloziere Proxy-Port...', zh: '正在分配代理端口...' },
  { key: 'sd_toast_allocating_voice', en: 'Allocating voicechat port...', de: 'Alloziere Voicechat-Port...', zh: '正在分配语音聊天端口...' },
  { key: 'sd_toast_zipping', en: 'Zipping server, please wait...', de: 'Zippe Server, bitte warten...', zh: '正在压缩服务器，请稍候...' },
  { key: 'sd_toast_export_failed', en: 'Export failed: ', de: 'Export fehlgeschlagen: ', zh: '导出失败：' },
  { key: 'sd_toast_waking_up', en: 'Waking up server...', de: 'Wecke Server auf...', zh: '正在唤醒服务器...' },
  { key: 'sd_toast_wakeup_error', en: 'Error waking up: ', de: 'Fehler beim Aufwecken: ', zh: '唤醒时出错：' },
  { key: 'sd_toast_stop_server_first', en: 'Please stop the server first!', de: 'Bitte Server zuerst stoppen!', zh: '请先停止服务器！' },
  { key: 'sd_toast_no_internet', en: 'No internet connection', de: 'Keine Internetverbindung', zh: '没有互联网连接' },
  { key: 'sd_toast_installed_suffix', en: ' installed!', de: ' installiert!', zh: '已安装！' },
  { key: 'sd_dialog_loading_versions', en: 'Loading versions...', de: 'Lade Versionen...', zh: '正在加载版本...' },
  { key: 'sd_toast_no_compatible_version', en: 'No compatible version found.', de: 'Keine kompatible Version gefunden.', zh: '未找到兼容版本。' },
  { key: 'sd_toast_download_error', en: 'Download Error', de: 'Download-Fehler', zh: '下载错误' },
  { key: 'sd_toast_loading_error', en: 'Error loading', de: 'Fehler beim Laden', zh: '加载错误' },
  { key: 'sd_toast_search_error', en: 'Search error: ', de: 'Such-Fehler: ', zh: '搜索错误：' },
  { key: 'sd_toast_playit_error', en: 'Playit could not be started', de: 'Playit konnte nicht gestartet werden', zh: 'Playit 无法启动' },
  { key: 'sd_toast_dns_failed', en: 'DNS request failed: ', de: 'DNS-Anfrage fehlgeschlagen: ', zh: 'DNS请求失败：' },
  { key: 'sd_toast_unlinked_domain', en: 'Unlinked from Custom Domain. Reverting to default.', de: 'Eigene Domain entfernt. Standard wiederhergestellt.', zh: '已取消关联自定义域名。恢复默认。' },
  { key: 'sd_toast_addon_removed', en: 'Addon removed and files cleaned up.', de: 'Addon entfernt und Dateien bereinigt.', zh: '插件已删除，文件已清理。' },
  { key: 'sd_dialog_download_failed', en: 'Download failed', de: 'Download fehlgeschlagen', zh: '下载失败' },
  { key: 'sd_dialog_files_exist', en: 'File(s) already exist', de: 'Datei(en) existieren bereits', zh: '文件已存在' },
  { key: 'sd_dialog_files_exist_msg_1', en: 'The following files already exist:\n\n  ', de: 'Die folgenden Dateien existieren bereits:\n\n  ', zh: '以下文件已存在：\n\n  ' },
  { key: 'sd_dialog_files_exist_msg_2', en: '\n\nOverwrite them?', de: '\n\nÜberschreiben?', zh: '\n\n要覆盖它们吗？' },
  { key: 'sd_toast_importing_folder', en: 'Importing folder...', de: 'Importiere Ordner...', zh: '正在导入文件夹...' },
  { key: 'sd_toast_copied_suffix', en: ' copied!', de: ' kopiert!', zh: '已复制！' },
  
  // EULA additions
  { key: 'sd_eula_title', en: 'Minecraft EULA', de: 'Minecraft EULA', zh: 'Minecraft 最终用户许可协议' },
  { key: 'sd_eula_accept', en: 'ACCEPT', de: 'AKZEPTIEREN', zh: '接受' },
  { key: 'sd_eula_decline', en: 'DECLINE', de: 'ABLEHNEN', zh: '拒绝' },
  { key: 'sd_eula_body', 
    en: 'By starting this Minecraft server you accept the Mojang/Microsoft EULA.\n\nThis includes:\n• You may not sell access to gameplay features\n• You may not redistribute Minecraft content\n• Servers must comply with EULA guidelines\n\nFull EULA:\nhttps://aka.ms/MinecraftEULA', 
    de: 'Durch das Starten dieses Minecraft-Servers akzeptierst du die Mojang/Microsoft EULA.\n\nDies beinhaltet:\n• Du darfst keinen Zugang zu Gameplay-Features verkaufen\n• Du darfst keine Minecraft-Inhalte umverteilen\n• Server müssen den EULA-Richtlinien entsprechen\n\nVollständige EULA:\nhttps://aka.ms/MinecraftEULA', 
    zh: '启动此 Minecraft 服务器即表示您接受 Mojang/Microsoft 最终用户许可协议。\n\n这包括：\n• 您不得出售游戏功能的使用权\n• 您不得重新分发 Minecraft 内容\n• 服务器必须遵守 EULA 准则\n\n完整 EULA：\nhttps://aka.ms/MinecraftEULA' }
];

function escapeXml(unsafe) {
    return unsafe.replace(/[<>&'"]/g, function (c) {
        switch (c) {
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '&': return '&amp;';
            case "'": return "\\'";
            case '"': return '\\"';
        }
    });
}

function updateStringsXml(langCode, langField) {
    let filePath = 'app/src/main/res/values' + (langCode ? '-' + langCode : '') + '/strings.xml';
    if (!fs.existsSync(filePath)) {
        console.log("File not found: " + filePath);
        return;
    }
    let content = fs.readFileSync(filePath, 'utf8');
    
    let insertIndex = content.indexOf('</resources>');
    if (insertIndex === -1) return;
    
    let toInsert = '';
    for (let s of strings) {
        if (!content.includes('name="' + s.key + '"')) {
            let val = s[langField];
            val = escapeXml(val);
            // newlines need to be \n for xml, but string may contain actual \n.
            val = val.replace(/\n/g, '\\n');
            toInsert += '    <string name="' + s.key + '">' + val + '</string>\n';
        }
    }
    
    content = content.substring(0, insertIndex) + toInsert + content.substring(insertIndex);
    fs.writeFileSync(filePath, content);
}

updateStringsXml('', 'en');
updateStringsXml('de', 'de');
updateStringsXml('zh-rCN', 'zh');
console.log("Done updating xml.");
