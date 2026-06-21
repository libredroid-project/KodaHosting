# Activity Log - KodaHosting Refactoring & Redesign

## Session: 2026-05-20

### 1. Native JDK 25 Migration
- Added `org.tukaani:xz:1.9` and `org.apache.commons:commons-compress:1.21` to `build.gradle`
- Created `PaperMCDownloader.java` for auto-downloading latest PaperMC builds via API
- Updated `StartOrchestrator.java`:
  - Added `deleteRecursive()` to clean old PRoot/native_root/fake_root files
  - Added `installNativeJRE()` to extract `jre25-android-arm64.tar.xz` from assets
  - Added `setExecutableRecursive()` for bin/lib permissions
- Replaced old PRoot-based `startNativeFlow` in `TermuxServerService.java` with clean `ProcessBuilder`:
  - Direct JDK 25 execution without PRoot wrapper
  - Proper env vars: `LD_LIBRARY_PATH`, `PATH`, `HOME`, `JAVA_HOME`
  - Native frpc tunnel launch
- Added `onTaskRemoved()` to gracefully stop servers when app is swiped away
- Added PaperMC auto-download integration in `startServerInternal()`
- Fixed `ServerRepo.getAll()` → `ServerRepo.all()` compilation error
- Fixed `ServerDetailActivity.java`: Replaced `JavaFinder.find()` call with direct JRE25 check
- Termux fallback kept intact for compatibility

### 2. Full App Redesign (Orange Theme, Phantom.pub inspired)
- Overwrote `colors.xml`: Purple (#7A4E8C) → Orange (#FF6B00) palette
- Overwrote `styles.xml`: New orange theme, rounded corners (12dp), sans-serif-medium fonts
- Overwrote `design_tokens.xml`: Added radius tokens, updated branding
- Overwrote 22 drawable XML files with orange-themed rounded shapes
- Redesigned all layout XML files:
  - `activity_main.xml`: Premium home screen with gradient header
  - `item_server.xml`: Dark cards with orange glow borders
  - `activity_create_server.xml`: Premium form with rounded inputs
  - `activity_settings.xml`: Grouped settings with orange section headers
  - `activity_server_detail.xml`: Full premium redesign (dashboard, console, files, settings tabs)
- Fixed hardcoded purple colors in Java files:
  - `ServerInstance.java`: Default theme #7A4E8C → #FF6B00
  - `CreateServerActivity.java`: Default selected color → #FF6B00

### 3. Bug Fixes & Feature Additions (Round 2)
- **Console Text Readability**: Increased font size (11px→14px), brighter text (#CCC→#E8E8E8), 
  brighter ANSI colors, matching dark background (#08080E)
- **EULA Dialog**: Added first-start EULA acceptance dialog (per-server, stored in SharedPreferences `koda_eula`)
- **Crash → Termux Fallback**: When native mode crashes, dialog offers to switch to Termux mode
- **Enhanced Debug Logging (StartOrchestrator)**:
  - Logs server ID, dir, RAM, type at startup
  - Lists available assets for debugging missing JDK files
  - Tracks extraction progress (file count, MB, elapsed time)
  - Separate FileNotFoundException handler for missing asset
  - Post-extraction verification (exists, executable, size)
- **Enhanced Debug Logging (TermuxServerService)**:
  - Detailed native flow entry logging (paths, existence, sizes)
  - Full command logging before ProcessBuilder start
  - Environment variable logging (LD_LIBRARY_PATH, JAVA_HOME)
  - Non-zero exit code → CRASHED state (was OFFLINE before)
  - Cause chain logging on exceptions