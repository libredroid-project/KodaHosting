-- 1. Create table for user accounts (linking app to Minecraft)
CREATE TABLE public.koda_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(6) NOT NULL UNIQUE,
    app_uuid VARCHAR(255) NOT NULL UNIQUE,
    mc_username VARCHAR(16) NULL,
    mc_uuid VARCHAR(36) NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Allow public inserts and updates to koda_users (since we only use anon key in app and plugin)
ALTER TABLE public.koda_users ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public can insert" ON public.koda_users FOR INSERT WITH CHECK (true);
CREATE POLICY "Public can select" ON public.koda_users FOR SELECT USING (true);
CREATE POLICY "Public can update" ON public.koda_users FOR UPDATE USING (true);

-- 2. Create table for server ownership
CREATE TABLE public.koda_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host VARCHAR(255) NOT NULL UNIQUE,
    owner_app_uuid VARCHAR(255) NOT NULL,
    online_players INT DEFAULT 0,
    server_version VARCHAR(32) DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.koda_servers ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public can insert" ON public.koda_servers FOR INSERT WITH CHECK (true);
CREATE POLICY "Public can select" ON public.koda_servers FOR SELECT USING (true);
CREATE POLICY "Public can update" ON public.koda_servers FOR UPDATE USING (true);
CREATE POLICY "Public can delete" ON public.koda_servers FOR DELETE USING (true);
