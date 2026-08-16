# KodaNetwork – Roadmap & Code-Map

Stand: 2026-08-16, Branch `internal-testing`. Basis ist eine vollständige Exploration der
Codebasis gegen die ursprünglichen 31 Notizen. Jeder Eintrag: Status, Ursache/Fundorte
(Datei:Zeile, grob), Lösungsansatz. Nutzer-Korrekturen sind eingearbeitet.

Legende: ✅ erledigt/funktioniert · 🐛 Bug (Ursache identifiziert) · 🔧 Quick Win · 🚧 Großes Feature · 📋 Rechtliches

---

## ✅ Gruppe A – Bugs, die jetzt gefixt werden

### #7 Server-Rename-Bug (Join-Adresse zeigt falsche Subdomain) 🐛
- **Symptom:** Umbenennen des Servers (rein lokal/visuell) lässt das Dashboard danach
  `neuerName.kodanetwork.eu` anzeigen, obwohl die echte Join-Adresse korrekt bleibt
  (z. B. `survival.kodanetwork.eu`).
- **Ursache:** `app/src/main/java/eu/kodanetwork/mchost/model/ServerInstance.java:201` –
  `setName(String v)` setzt intern auch `subdomain = sanitize(v)`. Das Namensfeld in
  `ServerDetailActivity.java:499` (TextWatcher, schreibt bei jedem Tastendruck) und der
  Rename-Dialog `:3583` überschreiben dadurch die lokale Subdomain.
- **Fix:** `setName()` entkoppeln (nur `name`), Remote-Sync
  (`KodaServerService.java:273/277`) und `CreateDatabaseActivity.java:143` setzen die
  Subdomain dann explizit. Konstruktor-Ableitung (`ServerInstance.java:74`) und
  JSON-Fallback (`:153`) bleiben.

### #14 Player-Manager 🐛
- **Symptom:** Buttons wie „Give OP" führen Commands nicht (richtig) aus; Inventar-Ansicht
  zeigt nur ein Kästchen statt des kompletten Inventars.
- **Ursache 1:** `btn_pm_op`, `btn_pm_kick`, `btn_pm_ban`, `btn_pm_feed` existieren im
  Layout `bottom_sheet_player_manage.xml`, haben aber **keine Listener** in
  `ServerDetailActivity.showPlayerActionSheet()` (Z. 684+; verdrahtet sind nur
  Heal/Starve/Kill/Delete).
- **Fix 1:** Buttons an `KodaServerService.sendCmd()` hängen (`op/kick/ban <name>`,
  Feed via `effect`), konsistent zur Heal/Starve-Implementierung; Offline-Spieler
  berücksichtigen.
- **Ursache 2:** Inventar-Rendering in `populateInventoryUI()` (Z. 812+) +
  `util/NbtParser.parsePlayerDat()`: NBT-List-Parsing des `Inventory`-Compounds und
  Slot-Byte → Grid-Index-Mapping (hotbar 0–8, main 9–35, armor 100–103, offhand −106)
  prüfen/korrigieren.
- **Fix 2:** Slot-Mapping korrigieren, Grids (main/hotbar/armor/offhand) befüllen.

### #9 Dateien im Files-Tab erstellen (Plus-Symbol) 🐛/🔧
- **Status:** In der App existiert **kein** Erstellen-Button im Files-Tab (nur Import
  `btn_import_file`, Layout `activity_server_detail.xml:451`); `FileEditorActivity`
  bricht ab, wenn die Datei nicht existiert (`loadFile()`, Z. 136).
- **Fix:** Plus-Button im Files-Tab; Dialog „Neue Datei / Neuer Ordner" im aktuellen
  Verzeichnis; neue Datei direkt im Editor öffnen. Import/Export über SAF
  (`ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT`), damit **alle Dateisysteme/Provider**
  (Downloads, Drive, USB …) nutzbar sind.
- (KodaDash WebUI kann Erstellen bereits – aber out of scope, siehe unten.)

### #13 Gameplay-Settings 🐛/🔧
- **Status:** `setupGameplaySettings()` (`ServerDetailActivity.java:2908+`) bedient nur
  max-players/difficulty/distances/online-mode/hardcore/pvp/flight;
  `writeProps()` (`KodaServerService.java:2475`) überschreibt `gamemode` u. a. mit
  Fallback `survival`.
- **Fix:** (a) UI zeigt aktuelle Werte aus `server.properties` statt Defaults.
  (b) Manuelles Editieren der `server.properties` erlauben + **Warnung**, wenn Keys vom
  Nutzer geändert wurden (vor `writeProps()` erkennen → Dialog „übernehmen/behalten?").

### #21 MOTD-Feld verdrahten 🔧
- **Status:** `et_motd` existiert in beiden Layouts (`activity_server_detail.xml:861`,
  `_m3.xml:864`), ist aber in **keinem** Java-File referenziert; `ServerInstance.motd`
  und `writeProps()` (motd) existieren bereits.
- **Fix:** In `setupGameplaySettings()` laden/speichern wie max-players. Voller
  MOTD-Creator (Farbcodes-Vorschau) = später.

---

## 🔧 Gruppe B – Quick Wins / zurückgestellt

### #10 Join-Adresse fixen *(zurückgestellt auf Wunsch)*
Bekannte Ursachen aus der Exploration:
- `KodaServerService.java:1546–1551`: `createDnsLink(..., "bore.pub", port, "tcp")` →
  Edge Function `create-dns-link/index.ts:61–71` baut daraus einen **A-Record mit
  Hostname als Inhalt** (ungültig) statt einer IP.
- Voicechat-Port-Range-Mismatch: lokaler Fallback `allocatePortSync(...,55000,65000)`
  vs. Supabase-Range 50000–59999 → Ports, die Supabase nicht kennt.
- `updateJoinAddressDisplay()` (Z. 3276): hardcodede Legacy-Checks
  (`customDomain.contains("koda.network")`).

### #11 Custom Domain für kodaserv.eu fixen *(zurückgestellt)*
- Domain-Auswahl kodaserv.eu/kodanetwork.eu existiert
  (`CreateServerActivity.java:265–272`, `selectedBaseDomain`).
- `CustomDnsWizardActivity.java:421` erwartet hardcoded `85.215.180.87` im
  SRV-Target → schlägt fehl, wenn das Tunnel-Target ein Hostname ist.
- IONOS-Verkabelung: `create-dns-link`/`delete-dns-link` mit A+SRV und Rollback.

### #12 Voice-Chat-Adresse ändern/löschen 🔧
- Voicechat-Support existiert (Port-Allokation, Plugin-Install,
  `voicechat-server.properties`, frpc-UDP-Proxy, SRV `_voicechat._udp`), aber es gibt
  **keine UI**, die die Adresse anzeigt/ändert (`activity_server_detail.xml:800–815`).
- Fix: Adress-Anzeige + Ändern/Löschen im Voicechat-Row-Dialog.

### #8 Konsole Vollbild 🔧
- Konsole ist Panel in `ServerDetailActivity` (`setupConsole()` Z. 1405–1521) ohne
  Vollbild-Modus. Fix: Fullscreen-Toggle (immersive mode + ausgeblendete Tabs).

### #17 Plugin-Beschreibung + Modrinth-Tabs in der App 🔧
- Modrinth-Panel existiert (`setupPlugins()` Z. 2274+, Beschreibung/Icon/Download,
  Versions-BottomSheet, `ModrinthHelper`). Fehlt: Detail-Ansicht mit voller Beschreibung
  + Modrinth-Tabs (Kategorien/Loader/Versions) rein in der App.

### #5 Internet-Warnung ✅ (größtenteils vorhanden)
- `PraetorSystem.checkNetwork()` (`security/PraetorSystem.java:21–33`) +
  `PraetorWarningActivity` vor kritischen Aktionen; `NetworkMonitorManager` mit
  Dauer-Überwachung + Alarm. Ggf. polieren/vereinheitlichen.

### #3 TPS verbessern 🔧
- TPS wird nur für Paper-Familie gemessen (Konsole `tps`, Parsing
  `KodaServerService.java:1597–1607`, Anzeige `ServerCardAdapter.java:246–257`).
- Ansatz: Spark-Plugin für präzisere Messung, Anzeige auch in ServerDetail, bei
  Modloadern/Velocity andere Metriken (MSPT).

---

## 🚧 Gruppe C – Große Features

### #1 Forge zum Laufen bringen
- Typ existiert in `CreateServerActivity` (TYPE_VALS inkl. Forge/NeoForge), Downloads in
  `network/JarDownloader.java:68–98`, Start-Flow kennt Forge-Installer +
  `user_jvm_args.txt` (`KodaServerService.java:1201–1212`). Muss praktisch getestet und
  repariert werden (Installer-Run, Java-Version,_RAM).

### #2 Modpack-Installation
- `ModrinthHelper` kann nur Project-Search + Einzel-Downloads (plugins//mods/).
  Fehlt: Modrinth-Modpack-API (`.mrpack`-Format: downloads+overrides entpacken),
  Server-Setup (Forge/Fabric-Loader-Version aus mrpack), UI.

### #6 Weitere Java-Versionen (21, 17)
- Aktuell nur JRE 25 (Asset `jre25-android-arm64.tar.xz`, `StartOrchestrator.java:44–159`);
  x86_64 nutzt Pojav JRE 21 von GitHub. `provision-java` Edge Function (openjdk17 aus
  Supabase-Storage) ist **toter Code** (`SupabaseFunctionsClient.provisionJava()` hat
  keinen Aufrufer).
- Ansatz: JRE 17/21-Bundles (Assets oder Supabase-Storage), Zuordnungstabelle
  MC-Version → Java-Version (z. B. ≤1.16 → 8/11, 1.17 → 16/17, 1.18–1.20.4 → 17/21,
  ab 1.20.5 → 21), Auswahl-UI + automatische Wahl, `JavaFinder` erweitern.

### #18 Custom JAR Support
- ⚠️ Falle: `JarDownloader.download()` löscht **alle** `.jar` im Server-Verzeichnis
  (`JarDownloader.java:53–57`) – Custom-JAR wird beim „Jar herunterladen"-Button
  entfernt. `findServerJar()` (`KodaServerService.java:770–806`) nähme „any jar".
- Ansatz: Eigener Typ CUSTOM_JAR + Datei-Import (SAF), JarDownloader löscht nur
  bekannte Server-Jars.

### #19 Bedrock-Server (eigenständig)
- Heute nur Java+Geyser/Floodgate-Brücke (`bedrockSupport`/`bedrockPort`,
  `geyser_config.yml`, `floodgate_config.yml`, Supabase-Download der Jars). Ein
  echter Bedrock-Server bräuchte Nukkit/Cuberite-Integration (eigener Prozess-Typ,
  eigene Configs, UDP-Port-Handling – Port-Ranges sind vorbereitet: 40000–49999).

### #15 Server-Updates + Versions-Upgrade
- `UpdateServerActivity.java`: Plugin-Updates via Modrinth; Server nur
  Paper-Build-Update **innerhalb derselben MC-Version**
  (`PaperMCDownloader.downloadLatestPaperSync`). Fehlt: MC-Versions-Upgrade
  (welt-kompatibel prüfen, `ServerInstance.version` aktualisieren, Purpur/Folia/etc.).

### #25 Welttyp & Seed bei Erstellung
- `writeProps()` schreibt nie `level-seed`/`level-type`; keine UI-Felder. Fix: Felder in
  CreateServerActivity + writeProps (`level-type=bukkit:normal|flat|large_biomes`,
  `level-seed`), nur beim ersten Start (Welt existiert noch nicht).

### #31 Chunky-Prewall bei Erstellung
- Nichts vorhanden. Ansatz: Nach erstem Start Chunky-Plugin installieren +
  `chunky start/continue`-Commands per Fortschritts-UI; oder `pregen` über
  Start-Argumente.

### #26 Backups (lokal, Google Drive, Auto)
- Fast nichts: nur `lastBackupTime` (ungenutzt) und Zip bei Hibernation
  (`utils/HibernationManager.java`, `ZipUtils`). Ansatz: Backup-Manager
  (Server-Dir zippen, Welt-sicherer Stop vorher), Restore-UI, Google Drive via
  Drive REST API / Android Picker, Scheduler (WorkManager) mit Zeitplänen.

### #28 Crash-Erkennung mit Lösungsvorschlägen (+ KI)
- Heute: `CrashAlertActivity` (Vollbild-Alarm, letzte 50 Log-Zeilen, manuelles
  DISMISS/RESTART), Exit-Code-/JNI-Fehler-Erkennung in `KodaServerService`.
  Keine Analyse, keine Auto-Fixes, kein App-weiter `UncaughtExceptionHandler`.
- Ansatz: (a) Regelwerk für bekannte Fehler (Port belegt → freigeben/Vorschlag;
  fehlende Lib → Hinweis Gerät-Kompatibilität; falsche Java-Version → Zuweisung
  korrigieren; Welt-inkompatible Version → Ratgeber). (b) Fallback: Gemini-Analyse
  (App hat bereits Gemini-Integration mit User-API-Key,
  `CreateServerActivity.java:1028–1149`) mit Confidence-Angabe + Ein-Klick-Fix.

### #29/#30 App-Robustheit & Selbstverständlichkeit 🚧 (übergreifend)
- Keine Retry-Loops in `StartOrchestrator`/`JarDownloader`/`PaperMCDownloader`; kein
  globaler Crash-Handler; Reparatur nur extern (`fix_vps.py` – enthält hardcodierte
  VPS-Credentials, aus dem Repo entfernen!). Ansatz: schrittweise Start-Pipeline mit
  Checkpoints + Selbst-Reparatur, globaler Crash-Handler mit kontextiertem Reporting.

### #22/#23 HWID-Banning & stabile IDs 🚧
- Heute: `HWIDManager` = `SHA-256(ANDROID_ID + Build-Felder)` – ändert sich bei
  Factory-Reset / ist pro App-Signing-Key anders; `AntiTamperSystem` + native
  `PraetorSecurity.cpp` (Anti-Debug, Honeypots), `banned_hwids`/`high_risk_hwids`
  nur in Ad-hoc-SQL am Repo-Root definiert (**nicht in supabase/migrations**, keine
  RLS-Policies im Repo).
- Ansatz: nie-ändernde ID = server-ausgestellte UUID (nach Erst-Registrierung,
  in Keystore-verschlüsselt + Restore-Fallback über Supabase-Gerätetoken),
  mehrere Signale (Installations-ID, Account-Verknüpfung) für Ban-Umgehung-Erkennung;
  Tabellen + RLS in echte Migrationen überführen.

### #16 Eigene Font + Animationen (GitHub-Lib)
- Nur ein Font (`res/font/press_start_2p.ttf`, nur MainActivity); Material3
  dev-gegated mit 6/58 Layouts (`Material3ThemeHelper`, `M3AnimationHelper`,
  `*_m3.xml`); keine Lottie. Ansatz: Font-Familie einsetzen, Lottie
  (`com.airbnb.android:lottie`) für Empty-States/Übergänge.

---

## 📋 Gruppe D – Rechtliches & Sonstiges

### #4 Alle Lizenzen hinzufügen ✅/🔧
- Vorhanden: `app/src/main/assets/licenses/` (openjdk, mariadb, redis, openssl, zlib,
  ncurses, pcre2, frp, press_start_2p, tab) + `LicensesActivity`. Fehlen u. a.:
  RootBeer, Glide, BlurView, Pojav-JRE, proot/bootstrap, Termux-Pakete.
  Repo selbst hat **keine** LICENSE-Datei.

### #20 Rechtssicherheit, TOS & Privacy
- Vorhanden: `assets/licenses/tos.txt`, `privacy.txt`, `impressum.txt` (Controller:
  Karol Brzostowski, Paderborn), Mojang-EULA-Dialog. Web-Design hat nur Platzhalter-
  Links (`design/src/components/Footer.tsx` `href="#"`).
- To do: TOS an neue Features anpassen (Backups/Drive, KI-Crash-Analyse: Daten an
  Gemini!), Website-TOS/Privacy-Seiten veröffentlichen, Lizenztexte vervollständigen.

---

## Bereits erledigt / verworfen
- **#24 Server während Login löschen** – bereits gefixt (Nutzerbestätigung).
- **KodaDash WebUI** – auf Wunsch aus dem aktuellen Scope genommen. Bekannte Punkte für
  später: Players-/Plugins-Tab fehlen in `index.html`, Ordner-Erstellung ruft POST auf,
  Route akzeptiert nur PUT (`FilesRoute.java:125–127`).
