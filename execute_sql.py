import urllib.request
import json

url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': 'Bearer sbp_68e3fbc6930c4dfa56b32595a7bbbabb316aa651',
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
}

sql = """
CREATE TABLE IF NOT EXISTS banned_hwids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hwid TEXT UNIQUE NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION report_tamper(hwid text, reason text, user_uuid uuid DEFAULT null)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO banned_hwids (hwid, reason)
    VALUES (hwid, reason)
    ON CONFLICT (hwid) DO NOTHING;
    
    IF user_uuid IS NOT NULL THEN
        UPDATE koda_users SET is_banned = true WHERE id = user_uuid;
    END IF;
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
