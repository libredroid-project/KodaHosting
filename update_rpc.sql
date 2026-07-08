DROP FUNCTION IF EXISTS public.report_tamper(text, text, uuid);
CREATE OR REPLACE FUNCTION public.report_tamper(p_hwid text, p_reason text, p_user_uuid uuid DEFAULT NULL::uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    INSERT INTO banned_hwids (hwid, reason)
    VALUES (p_hwid, p_reason)
    ON CONFLICT (hwid) DO NOTHING;
    
    IF p_user_uuid IS NOT NULL THEN
        UPDATE koda_users SET is_banned = true WHERE id = p_user_uuid;
    END IF;
END;
$function$
