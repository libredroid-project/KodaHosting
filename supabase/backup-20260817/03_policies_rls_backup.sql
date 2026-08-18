-- BACKUP 2026-08-17: RLS-Status + Policies (Rollback: Datei direkt ausfuehren)

-- RLS-Status:
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.banned_hwids ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.high_risk_hwids DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.koda_file_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.koda_ports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.koda_servers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.koda_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_bugs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_ticket_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;

-- Policies:
DROP POLICY IF EXISTS "Public can read app_settings" ON public.app_settings;
CREATE POLICY "Public can read app_settings" ON public.app_settings AS PERMISSIVE FOR SELECT TO anon
  USING (true);

DROP POLICY IF EXISTS "Allow public read access" ON public.high_risk_hwids;
CREATE POLICY "Allow public read access" ON public.high_risk_hwids AS PERMISSIVE FOR SELECT TO public
  USING (true);

DROP POLICY IF EXISTS "Allow file transfer inserts" ON public.koda_file_transfers;
CREATE POLICY "Allow file transfer inserts" ON public.koda_file_transfers AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow file transfer selects" ON public.koda_file_transfers;
CREATE POLICY "Allow file transfer selects" ON public.koda_file_transfers AS PERMISSIVE FOR SELECT TO public
  USING (true);

DROP POLICY IF EXISTS "Allow public read access" ON public.koda_ports;
CREATE POLICY "Allow public read access" ON public.koda_ports AS PERMISSIVE FOR SELECT TO public
  USING (true);

DROP POLICY IF EXISTS "Allow insert for app users" ON public.koda_servers;
CREATE POLICY "Allow insert for app users" ON public.koda_servers AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK (check_app_user_exists((owner_app_uuid)::text));

DROP POLICY IF EXISTS "Allow select for servers public" ON public.koda_servers;
CREATE POLICY "Allow select for servers public" ON public.koda_servers AS PERMISSIVE FOR SELECT TO public
  USING (true);

DROP POLICY IF EXISTS "Deny delete for servers" ON public.koda_servers;
CREATE POLICY "Deny delete for servers" ON public.koda_servers AS PERMISSIVE FOR DELETE TO public
  USING (false);

DROP POLICY IF EXISTS "Deny direct update for servers" ON public.koda_servers;
CREATE POLICY "Deny direct update for servers" ON public.koda_servers AS PERMISSIVE FOR UPDATE TO public
  USING (false) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow insert for public" ON public.koda_users;
CREATE POLICY "Allow insert for public" ON public.koda_users AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Deny delete for everyone" ON public.koda_users;
CREATE POLICY "Deny delete for everyone" ON public.koda_users AS PERMISSIVE FOR DELETE TO anon
  USING (false);

DROP POLICY IF EXISTS "Deny direct update for users" ON public.koda_users;
CREATE POLICY "Deny direct update for users" ON public.koda_users AS PERMISSIVE FOR UPDATE TO public
  USING (false) WITH CHECK (true);

DROP POLICY IF EXISTS "Users can select own profile" ON public.koda_users;
CREATE POLICY "Users can select own profile" ON public.koda_users AS PERMISSIVE FOR SELECT TO public
  USING (((auth_id)::text = (auth.jwt() ->> 'sub'::text)));

DROP POLICY IF EXISTS "Users can update own profile" ON public.koda_users;
CREATE POLICY "Users can update own profile" ON public.koda_users AS PERMISSIVE FOR UPDATE TO public
  USING (((id)::text = (auth.jwt() ->> 'sub'::text))) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow authenticated insert bugs" ON public.support_bugs;
CREATE POLICY "Allow authenticated insert bugs" ON public.support_bugs AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK ((auth.uid() IS NOT NULL));

DROP POLICY IF EXISTS "Allow anonymous insert reports" ON public.support_reports;
CREATE POLICY "Allow anonymous insert reports" ON public.support_reports AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Users can insert own ticket messages" ON public.support_ticket_messages;
CREATE POLICY "Users can insert own ticket messages" ON public.support_ticket_messages AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK (((ticket_id IN ( SELECT support_tickets.id
   FROM support_tickets
  WHERE (support_tickets.reporter_uuid = (auth.uid())::text))) AND (sender_uuid = (auth.uid())::text)));

DROP POLICY IF EXISTS "Users can read own ticket messages" ON public.support_ticket_messages;
CREATE POLICY "Users can read own ticket messages" ON public.support_ticket_messages AS PERMISSIVE FOR SELECT TO public
  USING ((ticket_id IN ( SELECT support_tickets.id
   FROM support_tickets
  WHERE (support_tickets.reporter_uuid = (auth.uid())::text))));

DROP POLICY IF EXISTS "Users can create tickets" ON public.support_tickets;
CREATE POLICY "Users can create tickets" ON public.support_tickets AS PERMISSIVE FOR INSERT TO public
  USING (true) WITH CHECK ((reporter_uuid = (auth.uid())::text));

DROP POLICY IF EXISTS "Users can read own tickets" ON public.support_tickets;
CREATE POLICY "Users can read own tickets" ON public.support_tickets AS PERMISSIVE FOR SELECT TO public
  USING ((reporter_uuid = (auth.uid())::text));

