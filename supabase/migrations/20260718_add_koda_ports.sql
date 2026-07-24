CREATE TABLE IF NOT EXISTS public.koda_ports (
    port integer PRIMARY KEY,
    host varchar NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.koda_ports ENABLE ROW LEVEL SECURITY;

-- Allow read access for everyone
CREATE POLICY "Allow public read access" ON public.koda_ports FOR SELECT USING (true);
