<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="KodaHosting logo">
</p>

<h1 align="center">KodaHosting</h1>

<p align="center">
  <b>Run a Minecraft server directly on your Android phone — your hardware, your world, your rules.</b><br>
  Java &amp; Bedrock crossplay · one-tap modpacks · live console · AI crash assistant
</p>

<p align="center">
  <a href="https://github.com/libredroid-project/KodaHosting/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/libredroid-project/KodaHosting?include_prereleases&color=F0762B"></a>
  <a href="https://github.com/libredroid-project/KodaHosting/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/libredroid-project/KodaHosting/total?color=F0762B"></a>
  <a href="https://github.com/libredroid-project/KodaHosting/blob/master/LICENSE"><img alt="License" src="https://img.shields.io/github/license/libredroid-project/KodaHosting?color=F0762B"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208%2B%20(arm64)-241C18">
</p>

---

## What is KodaHosting?

KodaHosting is an Android app that hosts real Minecraft servers **on your own phone or tablet** — no PC, no rented VPS, no monthly costs. Your worlds and files stay on your device, friends join through a built-in tunnel so your home IP stays private, and the app handles everything that normally requires Linux knowledge: server downloads, Java runtimes, port handling, plugin/mod installation and crash diagnosis.

Think of it as a full server control panel in your pocket: create a server, press START, share the join address.

## Features

- **All major server software** — Paper, Purpur, Folia, Vanilla, Forge, Fabric, NeoForge, plus **PumpkinMC** (native Rust server that boots in under a second with built-in Bedrock support)
- **Bedrock crossplay** — Geyser/Floodgate auto-installed for Java servers, built into Pumpkin
- **Modpacks made easy** — search Modrinth in-app and install Fabric modpacks with dependency + checksum handling
- **Per-server Java** — JRE 8/17/21/25 downloaded on demand, auto-selected per software
- **Live console** — colored log output, real command input, quick commands
- **File manager** — browse/edit/import/export every server file, with protected managed keys
- **Network budgets** — rules like "1 GB mobile data per week" with warn / stop / block actions and a live traffic dashboard
- **Crash diagnosis** — 40+ known crash causes detected with suggested fixes, plus an optional AI assistant that reads the last 1000 log lines and explains the fix in your language
- **Player tools** — live player list, heal/feed/kick/ban, stats & inventory viewer
- **Multi-language** — English, German, Chinese

## Requirements

| | |
|---|---|
| Device | Android 8.0+ (API 26), **arm64** CPU |
| RAM | 3 GB+ recommended (2 GB hostable servers) |
| Internet | Required for tunnel & player connections |

## Download

Grab the latest APK from [**Releases**](https://github.com/libredroid-project/KodaHosting/releases/latest) and install it (allow "install unknown apps" for your browser). Documentation is available in the app and on [kodanetwork.eu](https://kodanetwork.eu).

## Building from source

```bash
# requires JDK 17–21 (Gradle 8.9) and Android SDK with NDK r27
./gradlew :app:assembleDebug
# signed release bundle (store credentials required):
./gradlew :app:bundleRelease -PKEYSTORE_PW=... -PKEY_PW=...
```

## Project structure

```
app/src/main/java/eu/kodanetwork/mchost/
├── ui/        Activities & dialogs (server list, detail, console, tutorials…)
├── service/   KodaServerService — runs & monitors the actual server processes
├── util/      Runtimes, tunnel, crash analysis, network policies, theming
├── security/  Anti-tamper, Praetor guard layer
└── network/   Supabase APIs
app/src/main/cpp/    Native security layer (PraetorSecurity) + frpc
```

## Legal

- **License:** [GNU GPL v3](LICENSE)
- Privacy Policy, Terms of Service and Imprint are bundled in the app (Licenses tab) and available at [kodanetwork.eu](https://kodanetwork.eu)
- KodaHosting is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.
- The optional "Ask AI" feature sends log excerpts to OpenRouter (Google Gemma) only when explicitly tapped; see the in-app Privacy Policy.

## Support

Found a bug or a crash the analyzer doesn't recognize? Open an [issue](https://github.com/libredroid-project/KodaHosting/issues) — include the console log (Files → logs/latest.log).
