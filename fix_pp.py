import re
with open('../website/privacy.html', 'r', encoding='utf-8') as f: html = f.read()
match = re.search(r'<div[^>]*class="privacy-content"[^>]*>(.*?)</div>', html, re.DOTALL)
if match:
    content = match.group(1)
    content = re.sub(r'<h2>(.*?)</h2>', r'\n\n\1\n', content)
    content = re.sub(r'<h3>(.*?)</h3>', r'\n\n\1\n', content)
    content = re.sub(r'<p[^>]*>(.*?)</p>', r'\1\n\n', content, flags=re.DOTALL)
    content = re.sub(r'<li[^>]*>(.*?)</li>', r'- \1\n', content, flags=re.DOTALL)
    content = re.sub(r'<[^>]+>', '', content)
    content = '\n'.join([line.strip() for line in content.split('\n')])
    content = re.sub(r'\n{3,}', '\n\n', content).strip()
    with open('app/src/main/assets/licenses/privacy.txt', 'w', encoding='utf-8') as f:
        f.write(content)
