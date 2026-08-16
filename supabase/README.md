# KodaHosting Supabase Setup

## Buckets

- `artifacts`
  - `openjdk17/openjdk17-aarch64.tar.gz`
  - `openjdk17/openjdk17-armv7.tar.gz` (optional)

## Function Secrets

Set in Supabase project secrets:

- `SUPABASE_SERVICE_ROLE_KEY`
- `JDK_STORAGE_BUCKET=artifacts`
- `OPENJDK17_SHA256_AARCH64=<sha256>`
- `OPENJDK17_SHA256_ARMV7=<sha256_optional>`
- `PLAYIT_AGENT_TOKEN=<token>`
- `IONOS_API_PREFIX=<prefix>`
- `IONOS_API_SECRET=<secret>`
- `IONOS_ZONE_ID=<zone id for kodanetwork.eu>`

## Deploy

```bash
supabase functions deploy provision-java
supabase functions deploy bootstrap-playit
supabase functions deploy create-dns-link
```
