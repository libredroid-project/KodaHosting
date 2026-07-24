import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
-- 1. rpc_get_is_banned
CREATE OR REPLACE FUNCTION public.rpc_get_is_banned(p_app_uuid text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_banned boolean;
BEGIN
  SELECT is_banned INTO v_banned FROM public.koda_users WHERE app_uuid = p_app_uuid;
  RETURN COALESCE(v_banned, false);
END;
$$;

-- 2. rpc_patch_user
CREATE OR REPLACE FUNCTION public.rpc_patch_user(p_app_uuid text, p_payload jsonb)
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
    app_last_ping = CASE WHEN p_payload ? 'app_last_ping' THEN (p_payload->>'app_last_ping')::timestamp with time zone ELSE app_last_ping END
  WHERE app_uuid = p_app_uuid;
END;
$$;

-- 3. rpc_patch_server
CREATE OR REPLACE FUNCTION public.rpc_patch_server(p_app_uuid text, p_host text, p_payload jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.koda_servers SET 
    host = CASE WHEN p_payload ? 'host' THEN p_payload->>'host' ELSE host END,
    server_version = CASE WHEN p_payload ? 'server_version' THEN p_payload->>'server_version' ELSE server_version END,
    is_deleted = CASE WHEN p_payload ? 'is_deleted' THEN (p_payload->>'is_deleted')::boolean ELSE is_deleted END
  WHERE owner_app_uuid = p_app_uuid AND host = p_host;
END;
$$;

-- 4. rpc_admin_patch_user
CREATE OR REPLACE FUNCTION public.rpc_admin_patch_user(p_admin_app_uuid text, p_target_id uuid, p_payload jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_perms jsonb;
BEGIN
  SELECT permissions INTO v_perms FROM public.koda_users WHERE app_uuid = p_admin_app_uuid;
  IF v_perms IS NOT NULL AND (v_perms->>'praetor_admin')::boolean = true THEN
    UPDATE public.koda_users SET 
      is_banned = CASE WHEN p_payload ? 'is_banned' THEN (p_payload->>'is_banned')::boolean ELSE is_banned END,
      permissions = CASE WHEN p_payload ? 'permissions' THEN p_payload->'permissions' ELSE permissions END
    WHERE id = p_target_id;
  END IF;
END;
$$;

-- 5. rpc_admin_patch_server
CREATE OR REPLACE FUNCTION public.rpc_admin_patch_server(p_admin_app_uuid text, p_target_host text, p_payload jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_perms jsonb;
BEGIN
  SELECT permissions INTO v_perms FROM public.koda_users WHERE app_uuid = p_admin_app_uuid;
  IF v_perms IS NOT NULL AND (v_perms->>'praetor_admin')::boolean = true THEN
    UPDATE public.koda_servers SET 
      host = CASE WHEN p_payload ? 'host' THEN p_payload->>'host' ELSE host END,
      server_version = CASE WHEN p_payload ? 'server_version' THEN p_payload->>'server_version' ELSE server_version END
    WHERE host = p_target_host;
  END IF;
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
