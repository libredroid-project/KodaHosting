const fs = require('fs');

const newStrings = [
  { key: 'sd_delete_dialog_title', en: 'Delete Server?', de: 'Server löschen?', zh: '?除服务??' },
  { key: 'sd_delete_dialog_msg', en: 'Warning: This action will permanently delete the server. Please wait...', de: 'Achtung: Dieser Vorgang wird den Server permanent löschen. Bitte warte...', zh: '警告：此操作将永久?除服务?。请稍候...' },
  { key: 'sd_delete_btn_waiting', en: 'Delete (%ds)', de: 'Löschen (%ds)', zh: '?除 (%ds)' },
  { key: 'sd_delete_btn_ready', en: 'DELETE', de: 'LÖSCHEN', zh: '?除' }
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

function addStrings(langCode, langField) {
    let filePath = 'app/src/main/res/values' + (langCode ? '-' + langCode : '') + '/strings.xml';
    if (!fs.existsSync(filePath)) return;
    
    let content = fs.readFileSync(filePath, 'utf8');
    let insertIndex = content.indexOf('</resources>');
    if (insertIndex === -1) return;
    
    let toInsert = '';
    for (let s of newStrings) {
        if (!content.includes('name="' + s.key + '"')) {
            let val = s[langField];
            val = escapeXml(val);
            toInsert += '    <string name="' + s.key + '">' + val + '</string>\n';
        }
    }
    
    content = content.substring(0, insertIndex) + toInsert + content.substring(insertIndex);
    fs.writeFileSync(filePath, content);
}

addStrings('', 'en');
addStrings('de', 'de');
addStrings('zh-rCN', 'zh');
console.log("Done");
