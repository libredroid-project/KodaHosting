import urllib.request, json
try:
    req = urllib.request.Request('https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query', data=json.dumps({'query': 'SELECT policyname, permissive, roles, cmd, qual, with_check FROM pg_policies WHERE tablename = \'koda_servers\''}).encode('utf-8'), headers={'Authorization': 'Bearer sbp_0d11e075df37156e59a70bcaa1afdbb975502f3e', 'Content-Type': 'application/json', 'User-Agent': 'curl/8.4.0'}, method='POST')
    res = urllib.request.urlopen(req)
    print(res.read().decode('utf-8'))
except Exception as e:
    if hasattr(e, 'read'):
        print(e.read().decode('utf-8'))
    else:
        print(e)
