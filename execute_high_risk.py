import urllib.request
import json

url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': 'Bearer sbp_68e3fbc6930c4dfa56b32595a7bbbabb316aa651',
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
}

sql = """
CREATE TABLE IF NOT EXISTS high_risk_hwids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hwid TEXT UNIQUE NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION report_high_risk(p_hwid text, p_reason text, p_user_uuid uuid DEFAULT null)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO high_risk_hwids (hwid, reason)
    VALUES (p_hwid, p_reason)
    ON CONFLICT (hwid) DO UPDATE SET 
        reason = EXCLUDED.reason;
END;
$$;
"""

data = json.dumps({'query': sql}).encode('utf-8')
req = urllib.request.Request(url, data=data, headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as response:
        print("Success:", response.read().decode('utf-8'))
except Exception as e:
    print(e)
    if hasattr(e, 'read'):
        print(e.read().decode('utf-8'))
