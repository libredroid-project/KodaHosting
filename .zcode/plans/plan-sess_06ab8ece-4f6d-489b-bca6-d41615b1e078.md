# KodaNetwork: ROADMAP.md + Bugfix-Batch

## Kontext
Die 31 Notizen wurden per Code-Exploration gemappt. Nutzer-Präzisierungen: **#7** = reiner **Anzeige-Bug**: Server-Rename (nur lokal/visuell) lässt das Dashboard danach fälschlich `neuerName.kodanetwork.eu` zeigen, obwohl die echte Join-Adresse korrekt bleibt. **#24** bereits gefixt, **#10/#11 (DNS/Custom Domain)** und **KodaDash WebUI** für jetzt raus. **#14** Player-Manager ist ein Bug-Bundle. Sonst: Bugs zuerst.

## Teil 1: ROADMAP.md (Repo-Root)
Alle 31 Notizen dokumentiert mit: Kurzbeschreibung, Status im Code (existiert / Bug mit Ursache / fehlt), Lösungsansatz, Fundorte (Datei:Zeile), inkl. der Nutzer-Korrekturen. Gruppierung: ✅ Bugs jetzt / 🔧 Quick Wins später / 🚧 Große Features / 📋 Rechtliches & Sonstiges.

## Teil 2: Bugfixes (alle in `app/src/main/java/eu/kodanetwork/mchost/`)

### Fix 1 — #7: Rename überschreibt lokal die Subdomain (Anzeige-Bug) — **Ursache verifiziert**
- **Ursache:** `model/ServerInstance.java:201` — `setName(String v)` setzt `subdomain = sanitize(v)` mit. Das Namensfeld in `ServerDetailActivity.java:499` (TextWatcher, `server.setName(...)` + `repo.update()` bei jedem Tastendruck) und der Rename-Dialog `:3583` ändern so unbeabsichtigt die Subdomain → `getJoinAddress()` (`subdomain + "." + baseDomain`, Z.81) zeigt `neuerName.kodanetwork.eu`, während DNS/Supabase korrekt bleiben.
- **Fix (minimal & gezielt):**
  1. `ServerInstance.setName()` entkoppeln: nur noch `name = v`.
  2. `service/KodaServerService.java:273/277` (Remote-Install-Sync, verlässt sich bisher auf die Kopplung): explizit `s.setSubdomain(host)` ergänzen (setSubdomain sanitized selbst, Z.211).
  3. `ui/CreateDatabaseActivity.java:143`: nach `s.setName(name)` explizit `s.setSubdomain(name)` ergänzen (Default-Konstruktor initialisiert sonst keine Subdomain).
  4. Konstruktor-Ableitung (`ServerInstance.java:74`, subdomain = sanitize(name) bei Erstellung) und JSON-Fallback (`:153`) bleiben unverändert.
  5. Danach zeigt `updateJoinAddressDisplay()` nach Rename automatisch die unveränderte, korrekte Subdomain.

### Fix 2 — #9: Dateien im Files-Tab der App erstellen (Plus-Symbol, alle Dateisysteme)
- Fundorte: `ui/ServerDetailActivity.java` `refreshFiles()` Z.1631, `addFRow()` Z.1659, `btn_import_file` (Layout Z.451); Erstellen-Button existiert nicht.
- Vorgehen: Plus-Button im Files-Tab ergänzen (Layout `activity_server_detail.xml` + `_m3.xml`-Variante). Dialog „Neue Datei / Neuer Ordner" → im aktuellen Server-Verzeichnis anlegen, neue Datei direkt im `FileEditorActivity` öffnen. Import/Export zusätzlich über **SAF** (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`), damit alle Dateisysteme/Provider nutzbar sind.

### Fix 3 — #14: Player-Manager-Bugs
- Fundorte: `showPlayerActionSheet()` Z.684+ — verdrahtet sind nur Heal/Starve/Kill/Delete; `btn_pm_op/kick/ban/feed` (Layout `bottom_sheet_player_manage.xml`) haben **keine Listener** → wirken wie „Command wird nicht richtig ausgeführt".
- Vorgehen: Vier Buttons an die bestehende Command-Pipeline (`KodaServerService.sendCmd`) hängen: `op <name>`, `kick <name>`, `ban <name>`, Feed via `effect` — konsistent mit den bestehenden Heal/Starve-Implementierungen; Online/Offline-Status berücksichtigen.
- **Inventar-Bug** („nur ein Kästchen statt Inventar"): `populateInventoryUI()` Z.812+ + `util/NbtParser.parsePlayerDat()` debuggen — NBT-List-Parsing des `Inventory`-Compounds und Slot-Byte → Grid-Index-Mapping (hotbar 0–8, main 9–35, armor 100–103, offhand -106) korrigieren; Grids richtig befüllen.

### Fix 4 — #13: Gameplay-Settings (Gamemode-Default + server.properties manuell mit Warnung)
- Fundorte: `setupGameplaySettings()` Z.2908+, `KodaServerService.writeProps()` Z.2475 (gamemode-Fallback `survival`).
- Vorgehen: (a) UI zeigt aktuellen Wert aus `server.properties` statt Default. (b) Manuelle Bearbeitung der `server.properties` erlauben (FileEditorActivity vorhanden); **Warnung**, wenn Keys vom Nutzer geändert wurden: vor `writeProps()` geänderte Keys erkennen → Dialog „manuell geändert — übernehmen oder App-Werte behalten?" → Nutzerauswahl respektieren statt blind zu überschreiben.

### Fix 5 — #21: MOTD-Feld verdrahten (Grundlage für MOTD-Creator)
- Fundorte: `et_motd` existiert in beiden Layouts, ist in **keinem** Java-File referenziert; `ServerInstance.motd`, `writeProps()` schreibt motd, Remote-Sync Z.2758.
- Vorgehen: `et_motd` in `setupGameplaySettings()` einbinden (laden + speichern wie max-players/difficulty). Voller Creator mit Farbcodes-Vorschau bleibt Roadmap-Punkt.

## Reihenfolge
1. ROADMAP.md schreiben und committen (sichert die Analyse ab)
2. Fix 1 (Subdomain-Entkopplung) → 3 (Player Manager) → 5 (MOTD) → 4 (Gameplay) → 2 (Files erstellen + SAF, größter UI-Anteil)
3. Nach jedem Fix Build-Check; am Ende `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug`
4. Einzelne Commits pro Fix auf `internal-testing`

## Verifikation
- Build läuft durch (assembleDebug, JDK 21).
- Code-Review der neuen Button-Commands gegen die Syntax der bestehenden Heal/Starve-Buttons.
- Manuelle QA-Punkte in der Zusammenfassung (Rename → Adresse bleibt korrekt, Plus im Files-Tab, OP/Kick/Ban im Player-Sheet, Inventar-Grid, MOTD speichern, properties-Warnung) — echte Laufzeit-Tests mit MC-Server kann ich nicht ausführen.

## Explizit out of scope (nur ROADMAP)
#10/#11 DNS-Join-Adresse, KodaDash WebUI, #24 (fertig), #22/#23 HWID-Redesign, große Features (#2, #6, #16, #18, #19, #20, #26, #28, #31 …).