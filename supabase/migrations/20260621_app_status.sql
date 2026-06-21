ALTER TABLE public.koda_users ADD COLUMN IF NOT EXISTS app_state text DEFAULT 'OFFLINE'; ALTER TABLE public.koda_users ADD COLUMN IF NOT EXISTS app_last_ping timestamptz DEFAULT NOW();
