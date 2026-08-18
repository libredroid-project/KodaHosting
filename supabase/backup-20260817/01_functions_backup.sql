-- BACKUP 2026-08-17: alle public-Funktionen (Rollback: Datei direkt ausfuehren)

CREATE OR REPLACE FUNCTION public.check_app_user_exists(p_app_uuid text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  RETURN EXISTS (SELECT 1 FROM public.koda_users WHERE app_uuid = p_app_uuid);
END;
$function$
;

CREATE OR REPLACE FUNCTION public.get_hwid_ban_type(check_hwid text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.handle_deleted_host()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.host LIKE 'deleted_%' AND OLD.host NOT LIKE 'deleted_%' THEN
    NEW.host := NEW.host || '_' || gen_random_uuid()::text;
  END IF;
  RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.is_hwid_banned(check_hwid text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    is_banned boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM banned_hwids WHERE hwid = check_hwid
    ) INTO is_banned;
    RETURN is_banned;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.report_high_risk(p_hwid text, p_reason text, p_user_uuid uuid DEFAULT NULL::uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    INSERT INTO high_risk_hwids (hwid, reason)
    VALUES (p_hwid, p_reason)
    ON CONFLICT (hwid) DO UPDATE SET 
        reason = EXCLUDED.reason;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.report_tamper(p_hwid text, p_reason text, p_user_uuid uuid DEFAULT NULL::uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_admin_patch_server(p_admin_app_uuid text, p_target_host text, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_admin_patch_user(p_admin_app_uuid text, p_target_id uuid, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_create_ticket(p_reporter_uuid text, p_ticket_type text, p_reference_id text, p_title text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    new_ticket_id uuid;
BEGIN
    INSERT INTO public.support_tickets (
        reporter_uuid,
        ticket_type,
        reference_id,
        title,
        status
    )
    VALUES (
        p_reporter_uuid,
        p_ticket_type,
        p_reference_id,
        p_title,
        'OPEN'
    )
    RETURNING id INTO new_ticket_id;
    
    IF p_ticket_type = 'BUG' OR p_ticket_type = 'BUG_REPORT' OR p_ticket_type = 'APP_BUG' THEN
        INSERT INTO public.support_bugs (id, description, reporter_uuid, status)
        VALUES (new_ticket_id, p_title, p_reporter_uuid, 'OPEN');
    ELSIF p_ticket_type = 'SERVER_REPORT' THEN
        INSERT INTO public.support_reports (id, server_host, reason, reporter_uuid, status)
        VALUES (new_ticket_id, p_reference_id, p_title, p_reporter_uuid, 'OPEN');
    END IF;

    RETURN new_ticket_id;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_create_ticket_message(p_ticket_id uuid, p_sender_uuid text, p_message text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  -- Simple validation: Ensure ticket exists
  IF EXISTS (SELECT 1 FROM public.support_tickets WHERE id = p_ticket_id) THEN
      INSERT INTO public.support_ticket_messages (ticket_id, sender_uuid, message)
      VALUES (p_ticket_id, p_sender_uuid, p_message);
  END IF;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_get_is_banned(p_app_uuid text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_get_ticket_messages(p_ticket_id uuid, p_reporter_uuid text)
 RETURNS TABLE(id uuid, ticket_id uuid, created_at timestamp with time zone, sender_uuid text, message text, attachment_url text, is_admin boolean)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
#variable_conflict use_column
BEGIN
    -- Only return messages if the ticket actually belongs to the reporter
    IF EXISTS (SELECT 1 FROM public.support_tickets t WHERE t.id = p_ticket_id AND t.reporter_uuid = p_reporter_uuid) THEN
        RETURN QUERY
        SELECT m.id, m.ticket_id, m.created_at, m.sender_uuid, m.message, m.attachment_url, m.is_admin
        FROM public.support_ticket_messages m
        WHERE m.ticket_id = p_ticket_id
        ORDER BY m.created_at ASC;
    END IF;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_get_tickets(p_reporter_uuid text)
 RETURNS TABLE(id uuid, created_at timestamp with time zone, reporter_uuid text, ticket_type text, reference_id text, title text, status text)
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
#variable_conflict use_column
BEGIN
    RETURN QUERY
    SELECT t.id, t.created_at, t.reporter_uuid, t.ticket_type, t.reference_id, t.title, t.status
    FROM public.support_tickets t
    WHERE t.reporter_uuid = p_reporter_uuid
    ORDER BY t.created_at DESC;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_migrate_servers(p_old_app_uuid text, p_new_app_uuid text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  UPDATE public.koda_servers SET owner_app_uuid = p_new_app_uuid WHERE owner_app_uuid = p_old_app_uuid;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_patch_server(p_app_uuid text, p_host text, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
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
    host = CASE WHEN p_payload ? 'host' THEN p_payload->>'host' ELSE host END,
    server_version = CASE WHEN p_payload ? 'server_version' THEN p_payload->>'server_version' ELSE server_version END,
    owner_app_uuid = CASE WHEN p_payload ? 'owner_app_uuid' THEN p_payload->>'owner_app_uuid' ELSE owner_app_uuid END,
    online_players = CASE WHEN p_payload ? 'online_players' THEN (p_payload->>'online_players')::integer ELSE online_players END,
    last_online = CASE WHEN p_payload ? 'last_online' THEN (p_payload->>'last_online')::timestamp with time zone ELSE last_online END,
    gamemode = CASE WHEN p_payload ? 'gamemode' THEN p_payload->>'gamemode' ELSE gamemode END,
    difficulty = CASE WHEN p_payload ? 'difficulty' THEN p_payload->>'difficulty' ELSE difficulty END,
    pvp = CASE WHEN p_payload ? 'pvp' THEN (p_payload->>'pvp')::boolean ELSE pvp END,
    whitelist = CASE WHEN p_payload ? 'whitelist' THEN (p_payload->>'whitelist')::boolean ELSE whitelist END,
    motd = CASE WHEN p_payload ? 'motd' THEN p_payload->>'motd' ELSE motd END,
    max_players = CASE WHEN p_payload ? 'max_players' THEN (p_payload->>'max_players')::integer ELSE max_players END,
    kodadash_port = CASE WHEN p_payload ? 'kodadash_port' THEN (p_payload->>'kodadash_port')::integer ELSE kodadash_port END,
    kodadash_token = CASE WHEN p_payload ? 'kodadash_token' THEN p_payload->>'kodadash_token' ELSE kodadash_token END
  WHERE host = p_host;
END;$function$
;

CREATE OR REPLACE FUNCTION public.rpc_patch_server_by_id(p_app_uuid text, p_id uuid, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
  v_owner text;
BEGIN
  SELECT owner_app_uuid INTO v_owner FROM public.koda_servers WHERE id = p_id;
  IF v_owner IS NULL THEN
    RAISE EXCEPTION 'Server not found';
  END IF;
  
  IF v_owner != p_app_uuid THEN
    RAISE EXCEPTION 'Not authorized';
  END IF;
  
  UPDATE public.koda_servers
  SET 
    host = CASE WHEN p_payload ? 'host' THEN p_payload->>'host' ELSE host END,
    server_version = CASE WHEN p_payload ? 'server_version' THEN p_payload->>'server_version' ELSE server_version END,
    owner_app_uuid = CASE WHEN p_payload ? 'owner_app_uuid' THEN p_payload->>'owner_app_uuid' ELSE owner_app_uuid END,
    online_players = CASE WHEN p_payload ? 'online_players' THEN (p_payload->>'online_players')::integer ELSE online_players END,
    last_online = CASE WHEN p_payload ? 'last_online' THEN (p_payload->>'last_online')::timestamp with time zone ELSE last_online END,
    gamemode = CASE WHEN p_payload ? 'gamemode' THEN p_payload->>'gamemode' ELSE gamemode END,
    difficulty = CASE WHEN p_payload ? 'difficulty' THEN p_payload->>'difficulty' ELSE difficulty END,
    pvp = CASE WHEN p_payload ? 'pvp' THEN (p_payload->>'pvp')::boolean ELSE pvp END,
    whitelist = CASE WHEN p_payload ? 'whitelist' THEN (p_payload->>'whitelist')::boolean ELSE whitelist END,
    motd = CASE WHEN p_payload ? 'motd' THEN p_payload->>'motd' ELSE motd END,
    max_players = CASE WHEN p_payload ? 'max_players' THEN (p_payload->>'max_players')::integer ELSE max_players END
  WHERE id = p_id;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_patch_user(p_app_uuid text, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  UPDATE public.koda_users SET 
    mc_username = CASE WHEN p_payload ? 'mc_username' THEN p_payload->>'mc_username' ELSE mc_username END,
    two_fa_enabled = CASE WHEN p_payload ? 'two_fa_enabled' THEN (p_payload->>'two_fa_enabled')::boolean ELSE two_fa_enabled END,
    two_fa_password = CASE WHEN p_payload ? 'two_fa_password' THEN p_payload->>'two_fa_password' ELSE two_fa_password END,
    device_ram_mb = CASE WHEN p_payload ? 'device_ram_mb' THEN (p_payload->>'device_ram_mb')::integer ELSE device_ram_mb END,
    is_main = CASE WHEN p_payload ? 'is_main' THEN (p_payload->>'is_main')::boolean ELSE is_main END,
    code = CASE WHEN p_payload ? 'code' THEN p_payload->>'code' ELSE code END,
    app_state = CASE WHEN p_payload ? 'app_state' THEN p_payload->>'app_state' ELSE app_state END,
    app_last_ping = CASE WHEN p_payload ? 'app_last_ping' THEN (p_payload->>'app_last_ping')::timestamp with time zone ELSE app_last_ping END,
    
    -- HIER SIND DIE FEHLENDEN SPALTEN:
    device_model = CASE WHEN p_payload ? 'device_model' THEN p_payload->>'device_model' ELSE device_model END,
    os_version = CASE WHEN p_payload ? 'os_version' THEN p_payload->>'os_version' ELSE os_version END,
    app_version = CASE WHEN p_payload ? 'app_version' THEN p_payload->>'app_version' ELSE app_version END,
    total_ram_mb = CASE WHEN p_payload ? 'total_ram_mb' THEN (p_payload->>'total_ram_mb')::integer ELSE total_ram_mb END,
    free_ram_mb = CASE WHEN p_payload ? 'free_ram_mb' THEN (p_payload->>'free_ram_mb')::integer ELSE free_ram_mb END,
    cpu_cores = CASE WHEN p_payload ? 'cpu_cores' THEN (p_payload->>'cpu_cores')::integer ELSE cpu_cores END,
    screen_resolution = CASE WHEN p_payload ? 'screen_resolution' THEN p_payload->>'screen_resolution' ELSE screen_resolution END,
    battery_level = CASE WHEN p_payload ? 'battery_level' THEN (p_payload->>'battery_level')::integer ELSE battery_level END,
    is_charging = CASE WHEN p_payload ? 'is_charging' THEN (p_payload->>'is_charging')::boolean ELSE is_charging END,
    network_type = CASE WHEN p_payload ? 'network_type' THEN p_payload->>'network_type' ELSE network_type END

  WHERE app_uuid = p_app_uuid;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_patch_user_by_id(p_app_uuid text, p_id uuid, p_payload jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
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
$function$
;

CREATE OR REPLACE FUNCTION public.rpc_sync_auth_id(p_app_uuid text, p_auth_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
  -- Wir updaten alle existierenden Einträge dieser app_uuid mit der neuen auth_id
  UPDATE public.koda_users 
  SET auth_id = p_auth_id 
  WHERE app_uuid = p_app_uuid;
  
  -- Falls noch gar kein Eintrag existiert (z.B. App frisch installiert), erstellen wir einen unsichtbaren Hintergrund-Eintrag
  IF NOT FOUND THEN
    INSERT INTO public.koda_users (app_uuid, auth_id, code) 
    VALUES (p_app_uuid, p_auth_id, 'LINKED');
  END IF;
END;
$function$
;

