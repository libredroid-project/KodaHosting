import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
CREATE OR REPLACE FUNCTION public.rpc_patch_user_by_id(p_app_uuid text, p_id uuid, p_payload jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.koda_users SET 
    mc_username = CASE WHEN p_payload ? 'mc_username' THEN p_payload->>'mc_username' ELSE mc_username END,
    two_fa_enabled = CASE WHEN p_payload ? 'two_fa_enabled' THEN (p_payload->>'two_fa_enabled')::boolean ELSE two_fa_enabled END,
    two_fa_password = CASE WHEN p_payload ? 'two_fa_password' THEN p_payload->>'two_fa_password' ELSE two_fa_password END,
    device_ram_mb = CASE WHEN p_payload ? 'device_ram_mb' THEN (p_payload->>'device_ram_mb')::integer ELSE device_ram_mb END,
    is_main = CASE WHEN p_payload ? 'is_main' THEN (p_payload->>'is_main')::boolean ELSE is_main END,
    permissions = CASE WHEN p_payload ? 'permissions' THEN p_payload->'permissions' ELSE permissions END,
    code = CASE WHEN p_payload ? 'code' THEN p_payload->>'code' ELSE code END,
    app_state = CASE WHEN p_payload ? 'app_state' THEN p_payload->>'app_state' ELSE app_state END,
    app_last_ping = CASE WHEN p_payload ? 'app_last_ping' THEN (p_payload->>'app_last_ping')::timestamp with time zone ELSE app_last_ping END,
    app_uuid = CASE WHEN p_payload ? 'app_uuid' THEN p_payload->>'app_uuid' ELSE app_uuid END,
    auth_id = CASE WHEN p_payload ? 'auth_id' THEN (p_payload->>'auth_id')::uuid ELSE auth_id END
  WHERE id = p_id AND app_uuid = p_app_uuid;
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
