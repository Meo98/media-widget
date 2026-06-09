# Media Widget

Android-Homescreen-Widget für **beliebige Media-Apps** (Tidal, Spotify, YouTube Music …). Auto-updating, adaptive Größen von 1x3 bis 4x5 (HxW), drei umschaltbare Styles (Material You / Glass / AMOLED), App-spezifische Actions (Like, Thumbs-up).

## Features

- **Adaptiv:** HxW 1x3 (mini bar) bis 4x5 (volle Karte), 5 Layout-Buckets passend zur Cell-Anzahl
- **3 Styles:** Material You (tonal aus Wallpaper) / Glass (blurred backdrop) / AMOLED (pure black)
- **Auto-Update:** Track-Wechsel in einer Media-App aktualisiert das Widget sofort
- **App-Actions:** Like in Tidal, Thumbs-up in YouTube Music, Heart in Spotify; generisch für andere Apps
- **Per-Widget-Settings:** jedes Widget kann eigenen Style/Mode haben; ConfigureActivity erscheint beim Platzieren

## Erstmal Setup

Einmal Android-Studio öffnen lassen, damit das SDK lokal liegt:

```bash
android-studio  # File → Open → /home/meo/media-widget → "Sync project"
```

Oder direkt headless installieren (siehe `docs/specs/2026-06-09-widget-v2-design.md`).

## Bauen

```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
./gradlew assembleDebug
# Ergebnis: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Berechtigung erteilen

1. App starten → "Berechtigung erteilen" tippen
2. System-Settings → "Media Widget Listener" aktivieren
3. Zurück, Widget platzieren

## Architektur

Siehe [`docs/specs/2026-06-09-widget-v2-design.md`](docs/specs/2026-06-09-widget-v2-design.md) und [`docs/plans/2026-06-09-widget-v2.md`](docs/plans/2026-06-09-widget-v2.md).
