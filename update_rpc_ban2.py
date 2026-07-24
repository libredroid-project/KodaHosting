import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
CREATE OR REPLACE FUNCTION public.rpc_get_is_banned(p_app_uuid text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_banned boolean;
BEGIN
  SELECT is_banned INTO v_banned FROM public.koda_users WHERE app_uuid = p_app_uuid;
  
  IF NOT FOUND THEN
    INSERT INTO public.koda_users (app_uuid, code) 
    VALUES (p_app_uuid, substr(md5(random()::text), 1, 4)) 
    ON CONFLICT DO NOTHING;
    RETURN false;
  END IF;
  
  RETURN COALESCE(v_banned, false);
END;
$$;
"""

req = urllib.request.Request(url, data=json.dumps({'query': sql}).encode('utf-8'), headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as res:
        print("Success:", res.read().decode('utf-8'))
except Exception as e:
    if hasattr(e, 'read'):
        print("Error:", e.read().decode('utf-8'))
    else:
        print("Error:", e)
