# Media Widget v2 — Design Spec

**Datum:** 2026-06-09
**Status:** Approved (per Chat-Brainstorming)
**Vorgänger:** v1 (4x2 fixed-size Material-You-Bar, commit `47aecfd`)

## Goals

Erweiterung des bestehenden Widgets um:

1. **Auto-Update-Bug fixen** — Widget reagiert von selbst auf Track-Wechsel statt nur auf Button-Klicks
2. **Multi-Size** — adaptive Layouts zwischen HxW 1x3 (kleinster horizontaler Bar) und 4x5 (volle Karte), inklusive aller Zwischengrößen
3. **Multi-Style** — drei umschaltbare Themes: Material You / Glass / AMOLED
4. **App-spezifische Actions** — Like, Thumbs-up etc., je nach aktiver Quelle (Tidal, YouTube Music, Spotify, generisch)
5. **Settings** — globale Defaults plus per-Widget-Overrides via Configure Activity

## Non-Goals (v2 explizit deferred)

- Interaktives Progress-Seeking (RemoteViews SeekBar erst stabil ab API 35; v2 ist Read-only Progress-Bar)
- Lockscreen-Widget-Variante (Android 12+ feature, andere Provider-Config nötig)
- Custom Album-Art-Crop (Cover wird centerCrop angezeigt, kein User-Pan)
- Mehrere parallele Media-Sessions im selben Widget anzeigen (eine Session pro Widget-Instanz, Picker via Settings)

## Architektur

### Komponenten-Übersicht

```
┌──────────────────────────────────────────────────────────────────┐
│ NotifListener (Service, läuft solange Permission aktiv)         │
│  └── MediaSessionTracker (Singleton)                            │
│       ├── OnActiveSessionsChangedListener  → Session-Wechsel    │
│       └── per-Controller MediaController.Callback               │
│            └── debounced (100ms) → MediaWidgetProvider.update() │
└──────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ MediaWidgetProvider                                              │
│  onUpdate(ids) für jede id:                                     │
│    1. Bucket = pickForSize(options.minWidth, options.minHeight) │
│    2. config = SettingsRepo.resolve(id) [INHERIT-aware]         │
│    3. RemoteViews aus Bucket-Layout                             │
│    4. apply(style) → bg + text colors + cover backdrop          │
│    5. bind(metadata, transport, app-actions)                    │
│    6. AppWidgetManager.updateAppWidget(id, sizeFMap)            │
└──────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ DataStore: media_widget_prefs                                   │
│   global.* keys (defaults für alle)                             │
│   w.<id>.* keys (per-widget overrides, value=INHERIT erlaubt)   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ MainActivity                ConfigureActivity                    │
│  - Permission-Check          - läuft beim Widget-Place           │
│  - Global Defaults UI        - per-Widget Override-UI            │
│  - Placed Widgets List       - "Use global defaults" Schnellweg  │
└──────────────────────────────────────────────────────────────────┘
```

### Datenfluss bei Track-Wechsel (Beispiel: Spotify wechselt Song)

1. Spotify aktualisiert MediaSession-Metadata
2. `MediaController.Callback.onMetadataChanged()` feuert für unseren Controller
3. `MediaSessionTracker` debounce-Handler queued `updateRunnable` (100ms delay)
4. Nach Debounce: `AppWidgetManager.getInstance(ctx).updateAppWidget(componentName, RemoteViews(...))` wird für alle Instanzen aufgerufen
5. Jede `MediaWidgetProvider.onUpdate()` baut Layout + Style + bindet neue Metadata
6. Android rendert neuen RemoteViews-State auf den Homescreen

### Datenfluss bei Button-Klick (z.B. Play/Pause)

1. User tippt Play/Pause-Button
2. RemoteViews `PendingIntent.getBroadcast` triggert `MediaWidgetProvider.onReceive` mit action `ACTION_PLAY_PAUSE`
3. `MediaState.pickActive()` liefert Controller
4. `transportControls.play()` oder `.pause()`
5. Spotify-Internal: state ändert sich → MediaSession-State-Update → Callback feuert → Tracker queued Update → wie oben
6. (Wir pushen NICHT mehr manuell aus dem Broadcast — eine Quelle der Wahrheit)

## §1 — MediaSessionTracker

**Datei:** `app/src/main/kotlin/com/meo/mediawidget/MediaSessionTracker.kt`

Singleton mit Lifecycle gebunden an `NotifListener`:
- `NotifListener.onListenerConnected()` → `Tracker.start(this)`
- `NotifListener.onListenerDisconnected()` → `Tracker.stop()`

Hält zwei Listener-Ebenen:
- **OnActiveSessionsChangedListener**: feuert wenn Sessions hinzukommen/verschwinden
- **MediaController.Callback** pro registrierten Controller: feuert bei `onPlaybackStateChanged`, `onMetadataChanged`, `onSessionDestroyed`

Bookkeeping: `mutableMap<MediaController, MediaController.Callback>`. Bei Session-Set-Wechsel:
- Stale Callbacks (Controller nicht mehr aktiv) → `unregisterCallback` + remove
- Neue Controller → neuer Callback + register + put

**Debouncing:** Alle Update-Trigger gehen durch `Handler(Looper.getMainLooper())` mit 100ms `postDelayed`. `removeCallbacks` vor jedem neuen post → letzter Event innerhalb 100ms gewinnt.

**Output:** `AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, MediaWidgetProvider::class.java))` → Broadcast mit `ACTION_APPWIDGET_UPDATE` an alle Instanzen.

## §2 — Sizes & Layouts

### Bucket-Definition

| Bucket | HxW gedeckt | SizeF-Map-Key (dp) | Inhalt |
|---|---|---|---|
| `MICRO_BAR` | 1x3 | (210, 70) | Cover-Icon links + Play/Pause rechts |
| `BAR` | 1x4, 1x5 | (290, 70), (370, 70) | Cover small + Title/Artist + ⏮▶⏭ inline |
| `MID_CARD` | 2x3, 2x4, 2x5, 3x3, 3x4, 3x5 | div. SizeF in range | Cover oben + Title/Artist + Transport-Row (H=5 + App-Actions) |
| `WIDE` | 4x3, 4x4 | (210, 290), (290, 290) | Cover groß links + Title/Artist + Transport rechts (4x4 + App-Actions) |
| `MEGA` | 4x5 | (370, 290) | Voll-Karte mit Cover + Text + Transport + App-Actions + Progress-Bar |

### SizeF-Map Construction

```kotlin
// im MediaWidgetProvider, ein gemeinsamer RemoteViews wird für alle Größen erzeugt:
val microRv = build(ctx, Bucket.MICRO_BAR, config, controller)
val barRv   = build(ctx, Bucket.BAR,       config, controller)
val midRv   = build(ctx, Bucket.MID_CARD,  config, controller)
val wideRv  = build(ctx, Bucket.WIDE,      config, controller)
val megaRv  = build(ctx, Bucket.MEGA,      config, controller)

val rv = RemoteViews(mapOf(
    SizeF(210f,  70f) to microRv,
    SizeF(290f,  70f) to barRv,
    SizeF(370f,  70f) to barRv,
    SizeF(140f, 140f) to midRv,
    SizeF(370f, 210f) to midRv,
    SizeF(210f, 290f) to wideRv,
    SizeF(290f, 290f) to wideRv,
    SizeF(370f, 290f) to megaRv
))
manager.updateAppWidget(id, rv)
```

Android pickt die größte SizeF die in die verfügbare Fläche passt.

### widget_info.xml

```xml
<appwidget-provider
    android:minWidth="210dp"
    android:minHeight="70dp"
    android:targetCellWidth="5"
    android:targetCellHeight="4"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_bar"
    android:previewLayout="@layout/widget_mega"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:configure="com.meo.mediawidget/.ConfigureActivity"/>
```

## §3 — Styles

### Style-Definition (`Style.kt`)

```kotlin
enum class Style { MATERIAL_YOU, GLASS, AMOLED }

data class StyleAssets(
    @DrawableRes val containerBg: Int,
    @DrawableRes val coverBg: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
    @ColorInt val buttonTint: Int,
    val coverCornerDp: Int,
    val containerCornerDp: Int,
    val useBlurredBackdrop: Boolean
)

fun Style.assets(ctx: Context): StyleAssets = when (this) {
    MATERIAL_YOU -> StyleAssets(
        containerBg = R.drawable.bg_material_you,
        coverBg     = R.drawable.bg_cover_material,
        textPrimary = ctx.getColor(R.color.material_text_primary),
        textSecondary = ctx.getColor(R.color.material_text_secondary),
        buttonTint  = ctx.getColor(R.color.material_button_tint),
        coverCornerDp = 16, containerCornerDp = 20,
        useBlurredBackdrop = false
    )
    GLASS -> StyleAssets(
        containerBg = R.drawable.bg_glass,
        coverBg     = R.drawable.bg_cover_glass,
        textPrimary = ctx.getColor(R.color.glass_text_primary),
        textSecondary = ctx.getColor(R.color.glass_text_secondary),
        buttonTint  = ctx.getColor(R.color.glass_button_tint),
        coverCornerDp = 16, containerCornerDp = 24,
        useBlurredBackdrop = true
    )
    AMOLED -> StyleAssets(
        containerBg = R.drawable.bg_amoled,
        coverBg     = R.drawable.bg_cover_amoled,
        textPrimary = ctx.getColor(R.color.amoled_text_primary),
        textSecondary = ctx.getColor(R.color.amoled_text_secondary),
        buttonTint  = ctx.getColor(R.color.amoled_button_tint),
        coverCornerDp = 4, containerCornerDp = 0,
        useBlurredBackdrop = false
    )
}
```

### Render-Time Apply

```kotlin
fun applyStyle(rv: RemoteViews, assets: StyleAssets, coverArt: Bitmap?) {
    rv.setInt(R.id.container, "setBackgroundResource", assets.containerBg)
    rv.setTextColor(R.id.title, assets.textPrimary)
    rv.setTextColor(R.id.artist, assets.textSecondary)
    // ... tint buttons via setColorFilter

    if (assets.useBlurredBackdrop && coverArt != null) {
        val blurred = BlurHelper.blurForGlass(coverArt, ctx)
        rv.setImageViewBitmap(R.id.backdrop, blurred)
    }
}
```

### Color-Resources (values + values-night)

**values/colors.xml** (light):
```xml
<!-- Material You: tonale, weiche Pastell-Farben aus Wallpaper -->
<color name="material_text_primary">@android:color/system_neutral1_900</color>
<color name="material_text_secondary">@android:color/system_neutral2_700</color>
<color name="material_button_tint">@android:color/system_accent1_600</color>

<!-- Glass: weiß auf blurred backdrop -->
<color name="glass_text_primary">#FFFFFFFF</color>
<color name="glass_text_secondary">#CCFFFFFF</color>
<color name="glass_button_tint">#FFFFFFFF</color>

<!-- AMOLED: max-contrast -->
<color name="amoled_text_primary">#FFFFFFFF</color>
<color name="amoled_text_secondary">#FF808080</color>
<color name="amoled_button_tint">#FFFFFFFF</color>
```

**values-night/colors.xml** (dark): Material-Werte invertieren (system_neutral1_50 / system_neutral2_200 / system_accent1_300), Glass + AMOLED bleiben (sind eh dark-only sinnvoll).

## §4 — App-Actions

### Hybrid Resolution (`CuratedActions.kt`)

```kotlin
object CuratedActions {
    data class Polish(@DrawableRes val icon: Int, val label: String)

    private val PATTERNS = listOf(
        Regex("(?i)(like|favorite|favourite|heart|love)") to Polish(R.drawable.ic_heart, "Like"),
        Regex("(?i)(dislike|thumb.?down)")                to Polish(R.drawable.ic_thumb_down, "Dislike"),
        Regex("(?i)(thumb.?up)")                          to Polish(R.drawable.ic_thumb_up, "Thumbs up"),
        Regex("(?i)(queue|add.*playlist)")                to Polish(R.drawable.ic_queue, "Queue"),
        Regex("(?i)(shuffle)")                            to Polish(R.drawable.ic_shuffle, "Shuffle"),
        Regex("(?i)(repeat|loop)")                        to Polish(R.drawable.ic_repeat, "Repeat"),
    )

    fun polish(actionName: String): Polish? =
        PATTERNS.firstOrNull { (re, _) -> re.containsMatchIn(actionName) }?.second
}
```

### Rendering in MediaWidgetProvider

```kotlin
fun bindAppActions(rv: RemoteViews, controller: MediaController, config: WidgetConfig) {
    if (config.appActionsMode == OFF) {
        rv.setViewVisibility(R.id.actions_row, View.GONE); return
    }
    val raw = controller.playbackState?.customActions ?: return
    val slots = listOf(R.id.action_slot_1, R.id.action_slot_2, R.id.action_slot_3, R.id.action_slot_4)
    val maxSlots = if (currentBucket == MEGA) 4 else 2

    var slotIdx = 0
    for (action in raw) {
        if (slotIdx >= maxSlots) break
        val polish = CuratedActions.polish(action.action)
        val (icon, label) = when {
            polish != null -> polish.icon to polish.label
            config.appActionsMode == AUTO && config.allowRaw ->
                loadForeignIcon(controller.packageName, action.icon)?.let { it to action.name.toString() }
                    ?: continue
            else -> continue
        }
        val slotId = slots[slotIdx++]
        rv.setViewVisibility(slotId, View.VISIBLE)
        rv.setImageViewResource(slotId, icon)  // oder setImageViewBitmap für foreign
        rv.setOnClickPendingIntent(slotId, customActionPendingIntent(action.action))
    }
    // hide unused slots
    for (i in slotIdx until slots.size) rv.setViewVisibility(slots[i], View.GONE)
}
```

### Click → MediaController

`onReceive` mit Action `ACTION_CUSTOM` und Extra `EXTRA_CUSTOM_ACTION_NAME`:
```kotlin
val name = intent.getStringExtra(EXTRA_CUSTOM_ACTION_NAME) ?: return
MediaState.pickActive(context)?.transportControls?.sendCustomAction(name, null)
```

## §5 — Settings (DataStore)

### Schema

**Globale Keys (alle als String oder Boolean):**
- `global.style`: "MATERIAL_YOU" | "GLASS" | "AMOLED"
- `global.appActionsMode`: "AUTO" | "POLISHED_ONLY" | "OFF"
- `global.appActionsAllowRaw`: Boolean
- `global.showProgressBar`: Boolean (greift nur in MEGA-Bucket)
- `global.openAppOnCoverTap`: Boolean
- `global.preferredApp`: String? (Package-Name, beeinflusst `MediaState.pickActive` Tiebreaker)

**Per-Widget Keys** (`w.<appWidgetId>.*`): Jeder globale Key existiert nochmal als per-Widget-Override mit zusätzlichem Wert `"INHERIT"` (für String-Keys) bzw. einer separaten `w.<id>.<key>.overridden`-Boolean (für Booleans).

### `SettingsRepo.kt`

```kotlin
class SettingsRepo(private val ctx: Context) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("media_widget_prefs")

    suspend fun resolve(widgetId: Int): WidgetConfig {
        val prefs = ctx.dataStore.data.first()
        return WidgetConfig(
            style = resolveString(prefs, "w.$widgetId.style", "global.style", "MATERIAL_YOU")
                .let { Style.valueOf(it) },
            appActionsMode = resolveString(prefs, "w.$widgetId.appActionsMode", "global.appActionsMode", "AUTO")
                .let { AppActionsMode.valueOf(it) },
            // ...
        )
    }

    private fun resolveString(prefs: Preferences, perKey: String, globalKey: String, fallback: String): String {
        val per = prefs[stringPreferencesKey(perKey)]
        if (per != null && per != "INHERIT") return per
        return prefs[stringPreferencesKey(globalKey)] ?: fallback
    }

    suspend fun writePerWidget(widgetId: Int, key: String, value: String) { /* ... */ }
    suspend fun clearPerWidget(widgetId: Int) { /* alle w.<id>.* keys löschen, called from onDeleted */ }
    suspend fun writeGlobal(key: String, value: String) { /* ... */ }
}
```

### ConfigureActivity Flow

1. `onCreate` → liest `appWidgetId` aus Intent
2. Setze `setResult(RESULT_CANCELED)` als Default (Android entfernt Widget bei Abbruch)
3. Zeige Form (activity_configure.xml): Style-Spinner, Mode-Spinner, Toggles, jeweils mit "Inherit"-Checkbox
4. "Speichern" → SettingsRepo.writePerWidget(...), dann `MediaWidgetProvider.requestUpdate(this, widgetId)`, dann `setResult(RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, id))` + `finish()`
5. "Use global defaults" → schreibt nichts (alle Inherits), setResult OK, finish

### MainActivity Layout

```
┌─────────────────────────────┐
│ Berechtigung: ✓ aktiv       │
│  (oder Knopf "erteilen")    │
├─────────────────────────────┤
│ Globale Defaults            │
│  Style:         [Material▾] │
│  App-Actions:   [Auto    ▾] │
│  ☑ Raw Actions erlauben     │
│  ☑ Progress-Bar (in MEGA)   │
│  ☐ Cover-Tap öffnet App     │
│  Preferred App: [Auto    ▾] │
├─────────────────────────────┤
│ Platzierte Widgets (N)      │
│  #42 — Material (überschr.) │
│      [Reconfigure] [Reset]  │
│  #43 — global (INHERIT)     │
│      [Reconfigure]          │
│  ...                        │
└─────────────────────────────┘
```

`WidgetIds.kt`: `AppWidgetManager.getAppWidgetIds(ComponentName(ctx, MediaWidgetProvider::class.java))` liefert die Liste.

## §6 — Auto-Update-Fix

Bereits in §1 (MediaSessionTracker) und Datenfluss-Diagrammen oben dokumentiert. Zur Klarheit nochmal die Kern-Änderung:

**Vorher:** Widget rendert nur wenn `onUpdate` oder `onReceive(button-click)` aufgerufen wird. Track-Wechsel von außen → nichts.

**Nachher:** `MediaController.Callback.onMetadataChanged()` + `onPlaybackStateChanged()` triggern via Tracker einen `AppWidgetManager.updateAppWidget()`-Broadcast. Funktioniert weil:
- `NotifListener` ist gegen Android-Background-Restrictions geschützt (Notification-Listener-Permission hält den Process am Leben)
- Tracker registriert Callbacks im selben Process → keine IPC-Latenz
- 100ms Debounce verhindert Render-Storm bei chunky Metadata-Updates

## §7 — File Plan

### Neu

**Kotlin (8 Klassen):**
- `MediaSessionTracker.kt`
- `SettingsRepo.kt`
- `Style.kt`
- `Bucket.kt`
- `CuratedActions.kt`
- `BlurHelper.kt`
- `ConfigureActivity.kt`
- `WidgetIds.kt`

**Layouts (8 XML):**
- `widget_microbar.xml`, `widget_bar.xml`, `widget_midcard.xml`, `widget_wide.xml`, `widget_mega.xml`
- `activity_main.xml`, `activity_configure.xml`, `item_placed_widget.xml`

**Drawables (~14):**
- Icons: `ic_heart`, `ic_thumb_up`, `ic_thumb_down`, `ic_queue`, `ic_shuffle`, `ic_repeat`, `ic_more`
- Container-Backgrounds: `bg_material_you.xml`, `bg_glass.xml`, `bg_amoled.xml`
- Cover-Backdrops: `bg_cover_material.xml`, `bg_cover_glass.xml`, `bg_cover_amoled.xml`
- Glass-Button-Backdrop: `button_circle_glass.xml`

### Modifiziert

| Datei | Änderung |
|---|---|
| `MediaWidgetProvider.kt` | SizeF→Bucket pick, Style-apply, App-Actions binding, `onDeleted`, `onAppWidgetOptionsChanged` |
| `NotifListener.kt` | start/stop des Trackers in onListenerConnected/Disconnected |
| `MediaController.kt` → rename `MediaState.kt` | unverändert |
| `MainActivity.kt` | full settings-UI, ersetzt code-built layout |
| `AndroidManifest.xml` | + ConfigureActivity intent-filter |
| `xml/widget_info.xml` | min 210x70dp, target 5x4, + configure attribute |
| `app/build.gradle.kts` | + DataStore dependency |
| `gradle/libs.versions.toml` | + datastore version |

### Gelöscht

- `media_widget.xml` (alt) → ersetzt durch 5 Bucket-Layouts
- `widget_background.xml`, `widget_cover_background.xml` → ersetzt durch bg_*-Varianten

## Testing-Strategie

- **Manuell auf Gerät:** placement → configure activity → widget renders → trigger track change in Tidal/YT Music/Spotify → widget updates without click
- **Edge cases:**
  - keine Session aktiv → idle state, alle Buckets
  - Permission noch nicht erteilt → idle state mit "tap to grant" hint
  - Resize während Wiedergabe → `onAppWidgetOptionsChanged` triggert re-render mit neuem Bucket
  - User löscht Widget → `onDeleted` cleart `w.<id>.*` keys
  - Reboot → `onListenerConnected` startet Tracker neu, alle Widgets refreshen
- **Style-Visuals:** Screenshot pro (Bucket × Style) in 4 Apps (Tidal, Spotify, YT Music, plus eine ohne CustomActions). 5 × 3 × 4 = 60 Screenshots als Sanity-Check.

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| `MediaSessionTracker` leaks bei Service-Restart | mittel | strenges `unregisterCallback()` in `stop()`, plus map.clear() |
| Foreign-Resource-Loading scheitert auf manchen ROMs | niedrig | try-catch um `getResourcesForApplication`, fallback auf "MORE"-Icon |
| Blur (RenderEffect) zu langsam auf älteren Geräten | niedrig | nur API 31+ targetting, 32x32 Downsampling vor Blur, Cache pro Bitmap-Hash |
| DataStore migration wenn Schema später ändert | niedrig | wir nutzen flat Preferences (keine Proto), Default-Fallbacks fangen fehlende Keys ab |
| App-Actions in Tidal/YT Music brechen bei App-Update | mittel | Regex-basiert statt name-exact match, plus AUTO-mode hat raw-fallback |
| RemoteViews 4-action-Slot zu klein bei Apps mit 6+ Actions | niedrig | erste N + Overflow-Button der App öffnet |

## Open Questions (für Implementation-Plan)

- **Layout-Glass:** Wie genau soll das Backdrop-Blur Layered werden? Eigener ImageView hinter dem Container, oder als BG-Drawable mit `BitmapDrawable`? (Vermutlich ImageView, weil RemoteViews `setImageViewBitmap` darauf direkt arbeiten kann.)
- **ConfigureActivity-UI:** Code-built Forms (LinearLayout + setOnClickListener) oder XML-Layout mit findViewById? Konsistenz mit MainActivity-Direction. → XML wird wahrscheinlich gewinnen weil Recyclerview für placed-widgets-list sowieso XML braucht.
- **MainActivity Placed-Widgets-Liste:** RecyclerView oder einfach `LinearLayout.addView()` (es wird selten >5 sein)? → RecyclerView bringt Adapter-Overhead für eine Liste die selten >5 Items hat. Wahrscheinlich plain views.

Diese drei werden im Implementation-Plan entschieden.

## Acceptance

Spec gilt als implementiert wenn:

1. Widget kann in jeder HxW-Größe zwischen 1x3 und 4x5 platziert werden
2. Track-Wechsel in einer beliebigen Media-App updated alle platzierten Widget-Instanzen innerhalb 200ms ohne User-Interaction
3. Style-Wechsel in Settings ist innerhalb 1s visuell sichtbar auf allen INHERIT-Widgets
4. App-Actions (Like/Thumbs) funktionieren in Tidal, YouTube Music und Spotify (curated)
5. Configure Activity erscheint beim Widget-Platzieren und persistiert per-Widget-Settings korrekt
6. Widget-Delete cleart DataStore-Keys (kein Müll-Leak nach 100 Place/Remove-Zyklen)
