-- FIX 3: Prevent Privilege Escalation
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
    code = CASE WHEN p_payload ? 'code' THEN p_payload->>'code' ELSE code END,
    app_state = CASE WHEN p_payload ? 'app_state' THEN p_payload->>'app_state' ELSE app_state END,
    app_last_ping = CASE WHEN p_payload ? 'app_last_ping' THEN (p_payload->>'app_last_ping')::timestamp with time zone ELSE app_last_ping END
  WHERE app_uuid = p_app_uuid;
END;
$$;

-- FIX 6: Restrict direct updates to koda_servers
DROP POLICY IF EXISTS "Allow update for servers public" ON public.koda_servers;
CREATE POLICY "Deny direct update for servers" ON public.koda_servers 
  FOR UPDATE TO public USING (false);

-- FIX 7: Restrict direct updates to koda_users
DROP POLICY IF EXISTS "Allow update for everyone" ON public.koda_users;
CREATE POLICY "Deny direct update for users" ON public.koda_users 
  FOR UPDATE TO public USING (false);
