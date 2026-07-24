ALTER TABLE public.koda_servers ADD COLUMN IF NOT EXISTS base_domain VARCHAR DEFAULT 'kodanetwork.eu';
UPDATE public.koda_servers SET base_domain = 'kodanetwork.eu' WHERE base_domain IS NULL;
