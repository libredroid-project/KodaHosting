-- Supabase SQL Editor: Bitte ausführen, um den Datenbank-Blocker zu entfernen!

-- 1. Wir löschen alle alten (vielleicht defekten) Lese-Rechte
DROP POLICY IF EXISTS "Public can select" ON public.koda_users;
DROP POLICY IF EXISTS "Allow select for everyone" ON public.koda_users;

DROP POLICY IF EXISTS "Public can select" ON public.koda_servers;
DROP POLICY IF EXISTS "Allow select for servers" ON public.koda_servers;
DROP POLICY IF EXISTS "Allow select for servers public" ON public.koda_servers;

-- 2. Wir erstellen neue, saubere Rechte, die das Lesen für die Website GARANTIERT erlauben
CREATE POLICY "Public can select" ON public.koda_users FOR SELECT USING (true);
CREATE POLICY "Public can select" ON public.koda_servers FOR SELECT USING (true);
