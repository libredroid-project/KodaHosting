import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
SELECT id, host, owner_app_uuid, created_at FROM public.koda_servers ORDER BY created_at DESC LIMIT 5;
"""

req = urllib.request.Request(url, data=json.dumps({'query': sql}).encode('utf-8'), headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as res:
        print(res.read().decode('utf-8'))
except Exception as e:
    if hasattr(e, 'read'):
        print(e.read().decode('utf-8'))
    else:
        print(e)
