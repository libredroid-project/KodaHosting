-- Supabase SQL Editor: Bitte diesen Code kopieren und in deinem Supabase Dashboard unter "SQL Editor" ausführen!

-- 1. koda_users absichern
ALTER TABLE public.koda_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can insert" ON public.koda_users;
DROP POLICY IF EXISTS "Public can select" ON public.koda_users;
DROP POLICY IF EXISTS "Public can update" ON public.koda_users;
DROP POLICY IF EXISTS "Public can delete" ON public.koda_users;

-- Erlaubt das Erstellen neuer Nutzer
CREATE POLICY "Allow insert for everyone" ON public.koda_users FOR INSERT TO anon WITH CHECK (true);

-- Erlaubt das Lesen der Nutzer (für die Link-Code Überprüfung leider aktuell nötig, da kein Supabase Auth genutzt wird)
CREATE POLICY "Allow select for everyone" ON public.koda_users FOR SELECT TO anon USING (true);

-- Erlaubt Updates (Da wir kein Login-System haben, kann RLS Updates für 'anon' nicht auf bestimmte Nutzer begrenzen. 
-- Die App nutzt die lange 'app_uuid' quasi als Passwort in der Abfrage).
CREATE POLICY "Allow update for everyone" ON public.koda_users FOR UPDATE TO anon USING (true);

-- Verhindert, dass jemand (außer Admin) Nutzer löscht! (Die App nutzt ohnehin PATCH um Accounts zu unlinken)
CREATE POLICY "Deny delete for everyone" ON public.koda_users FOR DELETE TO anon USING (false);

-- 2. koda_servers absichern
ALTER TABLE public.koda_servers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can insert" ON public.koda_servers;
DROP POLICY IF EXISTS "Public can select" ON public.koda_servers;
DROP POLICY IF EXISTS "Public can update" ON public.koda_servers;
DROP POLICY IF EXISTS "Public can delete" ON public.koda_servers;

CREATE POLICY "Allow insert for servers" ON public.koda_servers FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Allow select for servers" ON public.koda_servers FOR SELECT TO anon USING (true);
CREATE POLICY "Allow update for servers" ON public.koda_servers FOR UPDATE TO anon USING (true);

-- Verhindert, dass Server-Einträge durch Anon-Nutzer komplett gelöscht werden (Wir nutzen in der App "host=deleted_..." als Flag)
CREATE POLICY "Deny delete for servers" ON public.koda_servers FOR DELETE TO anon USING (false);
