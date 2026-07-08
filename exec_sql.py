import urllib.request
import json

with open('update_rpc.sql', 'r') as f:
    q = f.read()

url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': 'Bearer sbp_68e3fbc6930c4dfa56b32595a7bbbabb316aa651',
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0'
}
data = json.dumps({'query': q}).encode('utf-8')
req = urllib.request.Request(url, data=data, headers=headers, method='POST')
try:
    res = urllib.request.urlopen(req)
    print("Success")
except Exception as e:
    print(e.read().decode('utf-8'))
