import urllib.request
import json

url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': 'Bearer sbp_68e3fbc6930c4dfa56b32595a7bbbabb316aa651',
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
}

sql = """
CREATE OR REPLACE FUNCTION is_hwid_banned(check_hwid text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    is_banned boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM banned_hwids WHERE hwid = check_hwid
    ) INTO is_banned;
    RETURN is_banned;
END;
$$;
"""

data = json.dumps({'query': sql}).encode('utf-8')

req = urllib.request.Request(url, data=data, headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as response:
        result = response.read().decode('utf-8')
        print(result)
except Exception as e:
    print(e)
    if hasattr(e, 'read'):
        print(e.read().decode('utf-8'))
