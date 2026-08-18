-- BACKUP 2026-08-17: pg_cron Jobs (Rollback via UPDATE cron.job bzw. cron.schedule)

-- jobid=1 name=hibernate_dead_servers schedule=0 0 * * * active=False
UPDATE cron.job SET schedule = '0 0 * * *', command = $cmd$UPDATE koda_servers SET server_version = 'HIBERNATED' WHERE last_online < (now() - interval '14 days') AND server_version != 'HIBERNATED';$cmd$, active = false WHERE jobid = 1;

-- jobid=4 name=hibernate-inactive-servers schedule=0 3 * * * active=True
UPDATE cron.job SET schedule = '0 3 * * *', command = $cmd$WITH hibernated AS (
    UPDATE koda_servers
    SET server_version = 'HIBERNATED'
    WHERE last_online < NOW() - INTERVAL '14 days'
      AND server_version != 'HIBERNATED'
    RETURNING host
)
SELECT net.http_post(
    url := 'https://scsezpfrrmpyuapblbxk.supabase.co/functions/v1/delete-dns-link',
    headers := '{"Content-Type": "application/json", "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjc2V6cGZycm1weXVhcGJsYnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NDE4MjYsImV4cCI6MjA5MjUxNzgyNn0.rHro6kQpXHAnxEaFxozYzsKY8IHIUlot-7-Q4LNbZT8"}'::jsonb,
    body := json_build_object('host', host)::jsonb
)
FROM hibernated
WHERE host IS NOT NULL;$cmd$, active = true WHERE jobid = 4;

