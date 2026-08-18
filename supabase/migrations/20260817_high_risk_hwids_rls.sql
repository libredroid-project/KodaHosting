-- 2026-08-17: RLS auf high_risk_hwids aktivieren
-- Vorher war RLS deaktiviert -> die Tabelle war mit dem anon-Key
-- voll les- UND schreibbar (jeder konnte Risk-Einträge manipulieren/löschen).
-- Jetzt: public darf nur lesen; Schreiben läuft ausschließlich über die
-- SECURITY DEFINER RPC report_high_risk().
-- Live angewendet am 2026-08-17 (Rollback: supabase/backup-20260817/03_policies_rls_backup.sql).

ALTER TABLE public.high_risk_hwids ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read access" ON public.high_risk_hwids;
CREATE POLICY "Allow public read access" ON public.high_risk_hwids
  FOR SELECT TO public USING (true);
