create extension if not exists "pgcrypto";

create table if not exists public.servers (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    name text not null,
    subdomain text not null,
    version text not null default '1.21.4',
    ram_mb integer not null default 1024,
    port integer not null default 25565,
    state text not null default 'OFFLINE',
    playit_address text not null default '',
    domain_link text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists servers_user_subdomain_idx on public.servers(user_id, subdomain);

create table if not exists public.server_runs (
    id uuid primary key default gen_random_uuid(),
    server_id uuid not null references public.servers(id) on delete cascade,
    status text not null,
    step text not null,
    message text not null default '',
    created_at timestamptz not null default now()
);

create table if not exists public.tunnel_sessions (
    id uuid primary key default gen_random_uuid(),
    server_id uuid not null references public.servers(id) on delete cascade,
    playit_address text not null,
    token_hint text not null default '',
    status text not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.dns_links (
    id uuid primary key default gen_random_uuid(),
    server_id uuid not null references public.servers(id) on delete cascade,
    host text not null,
    target text not null,
    provider text not null default 'ionos',
    status text not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.servers enable row level security;
alter table public.server_runs enable row level security;
alter table public.tunnel_sessions enable row level security;
alter table public.dns_links enable row level security;

create policy "servers_owner_all" on public.servers
for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "server_runs_owner_read" on public.server_runs
for select using (
    exists (
        select 1 from public.servers s where s.id = server_runs.server_id and s.user_id = auth.uid()
    )
);

create policy "tunnel_owner_read" on public.tunnel_sessions
for select using (
    exists (
        select 1 from public.servers s where s.id = tunnel_sessions.server_id and s.user_id = auth.uid()
    )
);

create policy "dns_owner_read" on public.dns_links
for select using (
    exists (
        select 1 from public.servers s where s.id = dns_links.server_id and s.user_id = auth.uid()
    )
);
