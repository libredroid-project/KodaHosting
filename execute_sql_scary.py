import urllib.request
import json

url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': 'Bearer sbp_68e3fbc6930c4dfa56b32595a7bbbabb316aa651',
    'Content-Type': 'application/json',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
}

sql = """
-- Add is_scary column to banned_hwids
ALTER TABLE banned_hwids ADD COLUMN IF NOT EXISTS is_scary BOOLEAN DEFAULT false;

-- Update existing report_tamper to support scary flag
CREATE OR REPLACE FUNCTION report_tamper(p_hwid text, p_reason text, p_user_uuid uuid DEFAULT null)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO banned_hwids (hwid, reason, is_scary)
    VALUES (p_hwid, p_reason, 
        CASE WHEN p_reason LIKE 'PERMANENT_BAN_%' THEN true ELSE false END
    )
    ON CONFLICT (hwid) DO UPDATE SET 
        is_scary = CASE 
            WHEN p_reason LIKE 'PERMANENT_BAN_%' THEN true 
            ELSE banned_hwids.is_scary 
        END,
        reason = EXCLUDED.reason;
    
    IF p_user_uuid IS NOT NULL THEN
        UPDATE koda_users SET is_banned = true WHERE id = p_user_uuid;
    END IF;
END;
$$;

-- New RPC: check if HWID is scary-banned (returns 'none', 'normal', 'scary')
CREATE OR REPLACE FUNCTION get_hwid_ban_type(check_hwid text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    ban_record RECORD;
BEGIN
    SELECT hwid, is_scary INTO ban_record 
    FROM banned_hwids WHERE hwid = check_hwid;
    
    IF NOT FOUND THEN
        RETURN 'none';
    ELSIF ban_record.is_scary = true THEN
        RETURN 'scary';
    ELSE
        RETURN 'normal';
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
