import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check 
FROM pg_policies 
WHERE schemaname = 'public';
"""

req = urllib.request.Request(url, data=json.dumps({'query': sql}).encode('utf-8'), headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as res:
        policies = json.loads(res.read().decode('utf-8'))
        for p in policies:
            print(f"Table: {p['tablename']} | Cmd: {p['cmd']} | Policy: {p['policyname']} | Roles: {p['roles']} | Qual: {p['qual']} | Check: {p['with_check']}")
except Exception as e:
    if hasattr(e, 'read'):
        print("Error:", e.read().decode('utf-8'))
    else:
        print("Error:", e)
