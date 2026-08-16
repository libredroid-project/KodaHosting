# KodaNetwork: Eigene Font + Lottie-Animationen (ROADMAP #16)

## Status
- ✅ **Lottie** `com.airbnb.android:lottie:6.7.1` in `app/build.gradle` aufgenommen,
  Build grün. Wichtig: mit JDK 21 bauen —
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug`
  (Default-JDK 25 lässt Gradle 8.9 mit „Unsupported class file major version 69" sterben).
- 🔜 Font-Umstellung (Teil 1–2), Lottie-Einsatzstellen (Teil 3)

## Teil 1: Font-Auswahl
Ziel: eigener Font statt Android-Systemfont (Roboto), passend zur Dark-UI + Orange (#FF6B00).

Empfehlung (alle SIL OFL → Bundling erlaubt, LICENSE-Texte mitliefern):
- **UI-Font: „Space Grotesk"** (400/500/700) — techy, eigenständig, gute Ziffern.
  Alternativen: Inter (beste Lesbarkeit in kleinen Größen), Outfit.
- **Mono: „JetBrains Mono"** (400/700) — Console-Tab, Log, FileEditor, Join-Adresse,
  Item-Counts/Statistik-Zahlen.
- **Akzent optional: „Monocraft"** (Minecraft-Stil-Monospace, OFL) — nur Logo/Header,
  nicht als Body-Font (Lesbarkeit).
- `press_start_2p.ttf` bleibt wo er ist (Retro-Akzent in MainActivity).
- ⚠️ Keine originalen Mojang/Minecraft-Fonts bundeln (Lizenz verbietet Redistribution).

### CJK-Falle (App ist en/de/zh lokalisiert)
Space Grotesk & JetBrains Mono enthalten **keine chinesischen Glyphen** → Android fällt
bei zh automatisch auf den Systemfont zurück (kein Bruch, nur zh-UI bleibt „System-Look").
Optionen: (a) akzeptieren **[Empfehlung]**, (b) eigenes `values-zh-rCN/themes.xml` ohne
Font-Override, (c) Noto Sans SC bündeln (+4–8 MB APK).

## Teil 2: Umsetzung Font (ca. 1–2 h)
1. TTFs nach `app/src/main/res/font/` (Namen nur lowercase a-z0-9_):
   `space_grotesk_regular.ttf`, `space_grotesk_medium.ttf`, `space_grotesk_bold.ttf`,
   `jetbrains_mono_regular.ttf`, `jetbrains_mono_bold.ttf`
2. Font-Familien-XML: `res/font/font_koda.xml` + `res/font/font_koda_mono.xml`
   (fontStyle/fontWeight/font → TTF-Mapping).
3. Theme-Override: im Basis-Theme (`values/styles.xml`) `android:fontFamily` +
   `fontFamily` auf `@font/font_koda` setzen → gilt app-weit inkl. Material-Komponenten;
   die 4 Styles mit hartem `sans-serif-medium` (styles.xml Z. 38/50/60/71) umstellen.
4. Hardcoded `fontFamily="sans-serif-medium"` in den Layouts **entfernen** (überschreiben
   sonst den Theme-Font): grep über `res/layout/` (u. a. bottom_sheet_player_manage.xml-Header,
   item_player_row) — Entfernen reicht, View erbt dann den Theme-Font.
5. Mono-Ausnahmen explizit setzen: `tvLog`/`etCmd` (Console), FileEditorActivity-Editor,
   Join-Adresse, `tv_item_count` → `fontFamily="@font/font_koda_mono"`.
6. APK-Größe: ~150 KB × 5 Weights ≈ 0,7 MB — ok; optional später per fonttools subsetten.
7. QA: en+de Screens (ServerListe, Detail in beiden Layout-Varianten `activity_server_detail.xml`
   + `_m3.xml`, BottomSheets, Praetor-Dialoge); Zeilenhöhen in 48dp-Buttons/Player-Rows
   prüfen (Space Grotesk läuft weiter als Roboto); zh-Fallback kurz ansehen.
8. Lizenzen (ROADMAP #4): OFL-Texte nach `app/src/main/assets/licenses/` +
   Eintrag in LicensesActivity.

## Teil 3: Lottie-Einsatzstellen (nach dem Font, je Schritt einzeln machbar)
JSON-Assets nach `res/raw/` (Quelle: lottiefiles.com — Lizenz CC-BY beachten — oder
eigene After-Effects-Exports; <50 KB pro Datei, keine Netzwerk-JSONs ohne
Offline-Fallback wegen App-Offline-Prinzip).

Priorisierung:
1. **Vollbild-Lade-Overlay** (`layout_full_loading`, ServerDetailActivity Z.~131–145):
   Lottie-Loop (z. B. „Server-Boot") statt/parallel zur PhysicsLoadingView.
2. **Player-Sheet-Loader**: die neuen `pb_pm_stats`/`pb_pm_inventory`-Spinner → kleine
   Lottie-Loops in Orange (#FF6B00).
3. **Praetor-Dialoge**: Success/Error-Animation (Haken/Kreuz) nach Bestätigung
   (Ban/Wipe/Props-Warnung).
4. **Empty States**: leerer Files-Tab, Player-Liste ohne Spieler.
5. Start/Stopp-Übergänge im Dashboard (Status-Dot-Puls), Integration in
   `M3AnimationHelper`/`*_m3.xml`-Varianten.

API-Snippet:
```xml
<com.airbnb.android.lottie.LottieAnimationView
    android:layout_width="64dp" android:layout_height="64dp"
    app:lottie_rawRes="@raw/koda_loading"
    app:lottie_autoPlay="true" app:lottie_loop="true"/>
```

## Out of scope
Downloadable Fonts (Google-Fonts-Provider) — braucht Play Services/Netz, widerspricht
Offline-First. Noto-SC-Bundling, solange der zh-Fallback akzeptiert wird.
