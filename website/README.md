# KodaHosting Website

Static site (no build step) deployed to GitHub Pages via `.github/workflows/pages.yml`.

## One-time setup (maintainer)

1. **Enable Pages:** Repo → Settings → Pages → Build and deployment → Source: **GitHub Actions**.
2. **App statistics (optional):**
   - Fill `SUPABASE_URL` and `SUPABASE_ANON_KEY` at the top of `app.js`
     (Dashboard → Settings → API → Project URL + anon public key — the anon key is safe to publish).
   - Run this SQL once in the Supabase SQL editor:

```sql
create or replace function public.public_site_stats()
returns table (servers bigint, users bigint)
language sql security definer set search_path = public as $$
  select
    (select count(*) from koda_servers where server_version <> '' or server_version is null),
    (select count(*) from koda_users);
$$;

grant execute on function public.public_site_stats() to anon;
```

   Adjust the WHERE clause if `koda_servers` needs different tombstone filtering.
3. **Icons:** the pages reference `https://libredroid-project.github.io/KodaHosting/icon.png` —
   put your app icon as `website/icon.png` (or adjust the URLs).

## Pages

- `index.html` — landing, features, live statistics
- `docs.html` — documentation & FAQ
- `privacy.html` — Privacy Policy (incl. AI crash analysis section)
- `tos.html` — Terms of Service
- `licenses.html` — licenses & credits
