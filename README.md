# Media Widget

Android-Homescreen-Widget, das die aktuell laufende Wiedergabe einer **beliebigen App** (Tidal, Spotify, YouTube Music …) anzeigt und steuert.

## Erstmal Setup

Einmal Android-Studio öffnen lassen — es lädt das SDK 35 + Build-Tools nach `~/Android/Sdk` und schreibt automatisch `local.properties`:

```bash
android-studio  # File → Open → /home/meo/media-widget → "Sync project"
```

Beim ersten Sync klickt Android Studio dich durch SDK-Download + Lizenzakzept.

## Bauen via CLI

```bash
./gradlew assembleDebug
# Ergebnis: app/build/outputs/apk/debug/app-debug.apk

# Auf angeschlossenes Gerät installieren
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Berechtigung im Gerät erteilen

1. App starten → „Berechtigung erteilen" tippen → System-Listen-Settings
2. „Media Widget Listener" aktivieren
3. Zurück zur App, Homescreen → Widget aus Liste platzieren

Ohne diesen Schritt bleibt das Widget leer („– kein aktives Medium –"), weil Android `MediaSessionManager.getActiveSessions()` ohne aktiven Notification-Listener mit `SecurityException` ablehnt.
