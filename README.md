# Media Widget

[![Latest release](https://img.shields.io/github/v/release/Meo98/media-widget)](https://github.com/Meo98/media-widget/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-brightgreen.svg)](#requirements)

A home screen widget that shows and controls whatever is playing on your phone,
in **any media app**: Spotify, Tidal, YouTube Music, Pocket Casts, audiobooks, VLC,
KDE Connect and everything else that registers a media session.

No account, no ads, no internet permission. *[Deutsch](README.de.md)*

## Features

- **Works with every media app.** The widget reads Android's media sessions, so it
  needs no per-app integration.
- **Remembers what you listened to.** Close the app and the widget keeps title and
  cover. Press play and it starts the app again where you left off.
- **App switcher.** Icons in the corner show your running and recent media apps.
  Tap one to make the widget control that app.
- **Call apps stay out.** Teams, Meet, Zoom and the phone app open a media session
  during calls. The widget ignores them, and you decide per app what may appear.
- **Five layouts, one widget.** Resize it from a 1×3 bar to a 4×5 card and the
  layout follows.
- **Four styles.** Material You (colours from your wallpaper), Glass (blurred
  cover), AMOLED (pure black) and OxygenOS (matches OnePlus/OPPO system widgets).
  Set a default and override it per widget.
- **Feels like a system app.** The settings follow your phone's own look, and the
  APK is 8 MB with no third-party UI libraries.
- **App actions.** Like, thumbs up, shuffle, repeat, skip 30 s and other actions the
  app offers appear as extra buttons.
- **English and German.**

## Install

**[Download the latest APK](https://github.com/Meo98/media-widget/releases/latest)** and open it on your phone.

To get updates automatically, add the repository URL to
[Obtainium](https://github.com/ImranR98/Obtainium):

```
https://github.com/Meo98/media-widget
```

## Setup

1. Open **Media Widget** and tap **Grant access**.
2. Enable **Media Widget Listener** in the list.
3. Long-press your home screen, choose **Widgets** and place **Media Widget**.

### Why notification access?

Android only lets an app see other apps' media sessions if it holds notification
access. There is no narrower permission. The widget's listener is an
[empty class](app/src/main/kotlin/com/meo/mediawidget/NotifListener.kt): it reads
no notifications, and the app has no internet permission, so nothing can leave
your phone.

## Requirements

- Android 12 or newer
- A media app that registers a `MediaSession` (practically all of them do)

## Good to know

- **Resuming a closed app** depends on that app. The widget first asks the app's
  media service to continue, then sends it a play command, and if neither works it
  opens the app. Android limits what apps may start in the background, so some
  apps need that last step.
- **The progress bar** updates when the track or play state changes, not every
  second. Widgets are not allowed to redraw continuously.

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You need JDK 17 and the Android SDK (platform 35). Run the unit tests with
`./gradlew testDebugUnitTest`.

## How it works

- `NotifListener` holds the notification access and hosts `MediaSessionTracker`,
  which listens for session and playback changes and redraws the widgets.
- `SessionFilter` separates media from calls; `SourcePicker` decides which app a
  widget shows. Both are plain functions with unit tests.
- `MediaMemory` stores the last title and cover per app in private app storage.
- `Resumer` restarts a closed app (media browser service, then media button, then
  opening the app).
- `MediaWidgetProvider` picks one of five layouts from the widget's size and fills
  it. Widgets are `RemoteViews` only, no Compose.

Launcher quirks found along the way (OnePlus/OPPO `android:configure`, the
`RemoteViews` view whitelist, `goAsync()` limits) are documented in
[docs/launcher-quirks.md](docs/launcher-quirks.md).

## Contributing

Bug reports and pull requests are welcome. If the widget picks the wrong layout
on your launcher, enable the debug log and attach the `render` line:

```bash
adb shell setprop log.tag.MediaWidget DEBUG
adb logcat -s MediaWidget
```

## License

MIT, see [LICENSE](LICENSE).
