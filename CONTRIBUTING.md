# Contributing

Thanks for wanting to improve Media Widget. This is a small hobby project, so keep
it simple: open an issue or send a pull request.

## Reporting a bug

Most bugs depend on the phone, the launcher and the media app. Please include:

- phone model and Android version, and the launcher if it is not the stock one
- the media app (and what it was doing: playing, paused, closed)
- the widget size, for layout problems a screenshot

For layout or "wrong app" problems the debug log helps a lot:

```bash
adb shell setprop log.tag.MediaWidget DEBUG
adb logcat -s MediaWidget
```

Each redraw prints one line with the size the launcher reports, the chosen layout
and the app the widget shows.

## Building

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

JDK 17 and the Android SDK with platform 35. The debug build has its own
application id and installs next to the release.

## Where things are

| You want to | Look at |
| --- | --- |
| add or tune a style | `Theme.kt` (`Themes.builtIn`) |
| hide a call app by default | `SessionFilter.kt` (`DEFAULT_HIDDEN`) |
| change which app a widget shows | `SourcePicker.kt` and its test |
| change a widget layout | `tools/gen_layouts.py`, then run it (see below) |
| change which layout a size gets | `Bucket.kt` and its test |
| translate | copy `res/values/strings.xml` to `res/values-<lang>/` |

The widget layouts in `app/src/main/res/layout/widget_*.xml` and `preview_*.xml`
are generated, so all sizes keep the same view ids. Edit the generator and run:

```bash
python3 tools/gen_layouts.py app/src/main/res/layout
```

To check a layout change in every size, open the app and tap **Preview all sizes**.

## Pull requests

- One topic per pull request.
- Logic that can be tested without a phone (filters, pickers, parsing) gets a unit
  test. Run `./gradlew testDebugUnitTest` before pushing; CI runs it too.
- Widgets are `RemoteViews`. Only views annotated `@RemoteView` work, and only
  methods that `RemoteViews` can call. `docs/launcher-quirks.md` lists what bit us.
- Code comments are in German for historical reasons. English is fine too.

## License

By contributing you agree that your contribution is licensed under the MIT license
of this project.
