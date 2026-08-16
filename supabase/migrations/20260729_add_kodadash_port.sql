-- Supabase SQL Editor: Bitte ausführen, um die KodaDash Spalte hinzuzufügen!

-- Fügt die fehlende Spalte 'kodadash_port' zur Tabelle 'koda_servers' hinzu,
-- damit die Android-App den Web-Port an die Datenbank senden kann.
ALTER TABLE public.koda_servers ADD COLUMN IF NOT EXISTS kodadash_port INT DEFAULT 0;
