-- 2026-08-17: Auto-Hibernate von 14 auf 180 Tage erhoeht
-- (live angewendet via cron.alter_job am 2026-08-17, siehe supabase/backup-20260817/)
-- Job 4 (aktiv, taeglich 03:00): hiberniert Server >180 Tage offline
-- und loescht deren DNS-Link via Edge Function.

SELECT cron.alter_job(
  4,
  null,
  replace(
    (SELECT command FROM cron.job WHERE jobid = 4),
    'INTERVAL ''14 days''',
    'INTERVAL ''180 days'''
  ),
  null, null, null
);
