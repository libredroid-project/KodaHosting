-- Supabase SQL Editor: Bitte diesen Code kopieren und in deinem Supabase Dashboard unter "SQL Editor" ausführen!

-- 1. Tabelle für App-weite Einstellungen (Wartungsmodus etc.)
CREATE TABLE IF NOT EXISTS public.app_settings (
    key VARCHAR(255) PRIMARY KEY,
    value JSONB NOT NULL
);

-- Initiale Werte für den Wartungsmodus setzen
INSERT INTO public.app_settings (key, value)
VALUES (
    'maintenance_mode',
    '{"active": false, "reason": "Wir führen gerade Wartungsarbeiten durch. Bitte habe etwas Geduld.", "duration_minutes": 60}'::jsonb
)
ON CONFLICT (key) DO NOTHING;

-- RLS für app_settings konfigurieren
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public can read app_settings" ON public.app_settings;
CREATE POLICY "Public can read app_settings" ON public.app_settings FOR SELECT TO anon USING (true);
-- Update/Insert ist nur für Admins (Service Role Key) erlaubt, daher keine weiteren Policies.

-- 2. Bann-Status zu Nutzern hinzufügen
ALTER TABLE public.koda_users ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT false;

-- 3. Bann-Status zu Servern hinzufügen
ALTER TABLE public.koda_servers ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT false;
