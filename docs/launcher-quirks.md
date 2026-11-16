# Launcher and platform quirks

Things that cost time while building this widget and are hard to find documented.

## Media sessions need notification access

`MediaSessionManager.getActiveSessions()` throws a `SecurityException` unless the
caller holds notification access. An empty `NotificationListenerService` is enough;
it only has to be enabled by the user.

## Other apps are invisible without `<queries>`

Since Android 11, `getApplicationIcon`, `getLaunchIntentForPackage` and
`queryIntentServices` fail for packages the manifest does not declare an interest
in. The widget declares launcher activities, `MediaBrowserService` and
`MEDIA_BUTTON` receivers.

## Call apps open media sessions

Teams keeps an active session with no state and no metadata, and the system telecom
service opens one with `USAGE_VOICE_COMMUNICATION` during calls. `SessionFilter`
drops sessions with a call usage or without a title, plus a short list of known
call apps.

## OnePlus / OPPO / realme launcher

`android:configure` must be the plain class name
(`com.meo.mediawidget.ConfigureActivity`). The launcher prepends the package itself;
with the `package/class` form it resolves `pkg/pkg/.Activity`, fails with
`START_CLASS_NOT_FOUND`, and silently drops the widget. The configure activity also
needs the `DEFAULT` category.

## RemoteViews

- `<Space>` and `<View>` are not allowed, only classes annotated `@RemoteView`.
  Use `<FrameLayout android:layout_weight="1"/>` as a spacer.
- `RenderEffect` is not available, so the Glass style blurs the cover by
  downscaling and upscaling a bitmap.

## Broadcast receivers

- `goAsync()` may be called once per `onReceive`. A second call returns `null`.
- A widget provider is a short-lived receiver. Long work belongs elsewhere; here the
  tracker lives in the notification listener's process and redraws widgets directly.

## Starting another app from a widget

A receiver in the background may not reliably start activities, and a transparent
trampoline activity flashes black on OxygenOS. The play button for a closed app
therefore sends a broadcast and resumes the app without any window (`Resumer`). If
an app cannot be resumed that way, the widget remembers it and the button becomes a
direct activity `PendingIntent` for that app.
