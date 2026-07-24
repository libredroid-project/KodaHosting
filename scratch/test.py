import json
with open(r'C:\Users\Home\KodaHosting\KodaNetwork\app\src\main\assets\chinese_dict.json', encoding='utf-8') as f:
    d = json.load(f)
with open(r'C:\Users\Home\KodaHosting\KodaNetwork\scratch\out.txt', 'w', encoding='utf-8') as f:
    f.write("我: " + str(d.get('我')) + "\n")
    f.write("国: " + str(d.get('国')) + "\n")
