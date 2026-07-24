import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
CREATE OR REPLACE FUNCTION public.rpc_patch_server(p_app_uuid text, p_host text, p_payload jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_owner text;
BEGIN
  SELECT owner_app_uuid INTO v_owner FROM public.koda_servers WHERE host = p_host;
  IF v_owner IS NULL THEN
    RAISE EXCEPTION 'Server not found';
  END IF;
  
  IF v_owner != p_app_uuid THEN
    RAISE EXCEPTION 'Not authorized';
  END IF;
  
  UPDATE public.koda_servers
  SET 
    host = COALESCE(p_payload->>'host', host),
    server_version = COALESCE(p_payload->>'server_version', server_version),
    owner_app_uuid = COALESCE(p_payload->>'owner_app_uuid', owner_app_uuid)
  WHERE host = p_host;
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
