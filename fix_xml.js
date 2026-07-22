const fs = require('fs');
const files = [
  'app/src/main/res/values/strings.xml',
  'app/src/main/res/values-de/strings.xml',
  'app/src/main/res/values-zh-rCN/strings.xml'
];

for (let file of files) {
  let content = fs.readFileSync(file, 'utf8');
  // replace literal \n with actual newlines between tags
  content = content.replace(/<\/string>\\n/g, '</string>\n');
  content = content.replace(/\\'/g, "'"); // replace \\' with ' (AAPT2 handles ' fine if not inside quotes, actually wait! AAPT2 requires \' for single quotes! So we should use \')
  content = content.replace(/\\\\'|\\\\'/g, "\\'"); // fix broken escaping
  // Ensure we just have \'
  content = content.replace(/\\\\'/g, "\\'");
  
  // Let's just fix the specific bad ones:
  content = content.replace(/hold \\'..\\'/g, "hold \\'..\\'");
  content = content.replace(/halte \\'..\\'/g, "halte \\'..\\'");
  content = content.replace(/长按 \\'..\\'/g, "长按 \\'..\\'");
  
  // To be absolutely safe, let's just make them normal quotes or backticks
  content = content.replace(/\\'..\\'/g, "`..`");
  
  fs.writeFileSync(file, content);
}
console.log("Fixed.");
