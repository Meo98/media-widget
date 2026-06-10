# Media Widget

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![minSdk 31](https://img.shields.io/badge/minSdk-31-brightgreen.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blueviolet.svg)](https://kotlinlang.org)

Android-Homescreen-Widget für die aktuell laufende Wiedergabe **beliebiger Media-Apps** (Tidal, Spotify, YouTube Music, KDE Connect, Podcasts …). Auto-updating, adaptive Größen, Material You / Glass / AMOLED Themes, App-spezifische Actions, und Quellen-App-Switcher direkt im Widget.

## Features

- **Adaptiv:** HxW 1x3 (mini bar) bis 4x5 (volle Karte) — 5 Layout-Buckets passen sich automatisch der gewählten Größe an
- **3 Themes:** Material You (tonal aus Wallpaper) · Glass (blurred backdrop) · AMOLED (pure black)
- **Auto-Update:** Track-Wechsel in einer Media-App aktualisiert das Widget innerhalb ~100ms ohne User-Interaktion
- **App-Switcher:** bis zu 3 aktive Media-Sessions als Icons in der Ecke — Tap wechselt die Quelle, Play/Pause steuert die gewählte App
- **App-spezifische Actions:** Like in Tidal, Thumbs-up in YouTube Music, Heart in Spotify; generisch für andere Apps
- **Per-Widget-Settings:** jedes Widget hat eigenen Style/Mode (global Defaults mit per-Widget Overrides via INHERIT-Sentinel)
- **Cover-Tap öffnet App:** Tipp auf Album-Cover bringt die Source-App in den Vordergrund

## Requirements

- Android 12+ (API 31)
- Eine Media-App, die `MediaSession` korrekt registriert (Tidal, Spotify, YouTube Music, VLC, KDE Connect, etc.)
- Notification-Listener-Permission (einmalig zu erteilen)

## Build & Install

```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK wird als `app/build/outputs/apk/debug/app-debug.apk` ausgegeben (~14 MB).

## Setup

1. App starten → „Berechtigung erteilen" tippen
2. In den System-Settings „Media Widget Listener" aktivieren
3. Zurück zum Homescreen → langes Drücken → Widgets → Media Widget platzieren
4. ConfigureActivity erscheint → Globale Defaults verwenden oder pro Widget anpassen

## Architektur

Siehe [`docs/specs/2026-06-09-widget-v2-design.md`](docs/specs/2026-06-09-widget-v2-design.md) für die volle Design-Spec und [`docs/plans/2026-06-09-widget-v2.md`](docs/plans/2026-06-09-widget-v2.md) für den schrittweisen Implementations-Plan.

Kurz-Übersicht:

- `MediaSessionTracker` (Singleton im NotifListener-Process) hört auf `OnActiveSessionsChangedListener` und per-Controller `MediaController.Callback`; debounced 100ms pusht Widget-Updates
- `MediaWidgetProvider` rendert pro Widget den passenden Bucket-Layout, holt `OPTION_APPWIDGET_MIN_*` aus dem Options-Bundle und mapped es via `Bucket.pickForSize` direkt — keine SizeF-Map-Gaps
- Settings via `androidx.datastore:datastore-preferences` mit `global.*` und per-Widget `w.<id>.*` Keys, `INHERIT`-Sentinel für String-Resolutionen

## Bekannte Quirks

- **OPlus-Launcher (OnePlus/OPPO/Realme):** `android:configure` muss als reiner Klassenname stehen (`com.meo.mediawidget.ConfigureActivity`), NICHT als `package/class` — der Launcher prefix-t Package selbst, beim `/`-Format crasht Resolution mit START_CLASS_NOT_FOUND
- **`<Space>` und `<View>` sind NICHT in der RemoteViews-Whitelist** — nur `@RemoteView`-annotierte Klassen sind erlaubt. Spacer per `<FrameLayout layout_weight=1/>`
- **`goAsync()` ist nur 1× pro `onReceive` erlaubt** — `onUpdate(ids)` mit Schleife kann das schnell sprengen; wir nutzen synchronen `runBlocking` für DataStore-Read

## License

MIT — siehe [LICENSE](LICENSE).
