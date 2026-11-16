package com.meo.mediawidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

open class MediaWidgetProvider : AppWidgetProvider() {

    /**
     * Was ein Widget gerade zeigt: entweder eine lebende Session ([controller])
     * oder den gemerkten Stand einer beendeten App ([remembered]) — oder nichts.
     */
    private class Shown(
        val controller: MediaController?,
        val remembered: Remembered?,
        /** Lebende Medien-Sessions, für den App-Umschalter. */
        val sessions: List<MediaController>,
        /** Gemerkte, nicht mehr laufende Apps, neueste zuerst. */
        val memory: List<Remembered>
    ) {
        val pkg: String? get() = controller?.packageName ?: remembered?.pkg
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> renderInto(ctx, manager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, manager: AppWidgetManager,
        id: Int, newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(ctx, manager, id, newOptions)
        renderInto(ctx, manager, id)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        super.onDeleted(ctx, ids)
        val repo = SettingsRepo(ctx)
        runBlocking { ids.forEach { repo.clearWidget(it) } }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)

        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (intent.action == ACTION_SWITCH_APP) {
            val pkg = intent.getStringExtra(EXTRA_SWITCH_APP_PACKAGE) ?: return
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                runBlocking { SettingsRepo(ctx).writeWidgetSelectedApp(widgetId, pkg) }
                requestUpdate(ctx)
            }
            return
        }
        if (intent.action == ACTION_RESUME) {
            val pkg = intent.getStringExtra(EXTRA_RESUME_PACKAGE) ?: return
            resumeClosedApp(ctx, pkg)
            return
        }
        if (intent.action !in TRANSPORT_ACTIONS) return

        // Knöpfe steuern die App, die DIESES Widget zeigt — nicht die global
        // zuletzt aktive. Die Widget-Id reist in den PendingIntent-Extras mit.
        val config = runBlocking {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) SettingsRepo(ctx).resolve(widgetId)
            else SettingsRepo(ctx).readGlobalConfig()
        }
        val controller = resolve(ctx, config).controller ?: return

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
                else controller.transportControls.play()
            }
            ACTION_NEXT -> controller.transportControls.skipToNext()
            ACTION_PREV -> controller.transportControls.skipToPrevious()
            ACTION_CUSTOM -> {
                val name = intent.getStringExtra(EXTRA_CUSTOM_ACTION_NAME) ?: return
                controller.transportControls.sendCustomAction(name, null)
            }
        }
        // Kein manuelles Neuzeichnen: MediaSessionTracker meldet sich, sobald die App reagiert hat.
    }

    /**
     * Startet eine beendete App ohne sichtbares Fenster wieder. goAsync() hält
     * den Empfänger am Leben, bis [Resumer] fertig ist (höchstens ein paar Sekunden).
     * Klappt es nicht, merken wir uns das: der Play-Knopf öffnet diese App künftig direkt.
     */
    private fun resumeClosedApp(ctx: Context, pkg: String) {
        val pending = goAsync()
        Resumer.resume(ctx, pkg) { outcome ->
            try {
                MediaMemory.setNeedsOpen(ctx, pkg, outcome == Resumer.Outcome.OPEN_APP)
                if (outcome == Resumer.Outcome.OPEN_APP) {
                    // Aus dem Hintergrund oft verboten; dann greift beim nächsten Tipp der direkte Start.
                    try {
                        ctx.packageManager.getLaunchIntentForPackage(pkg)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                    } catch (_: Throwable) {}
                    requestUpdate(ctx)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    /** Wählt, was ein Widget mit dieser Konfiguration zeigt (siehe [SourcePicker]). */
    private fun resolve(ctx: Context, config: WidgetConfig): Shown {
        val sessions = MediaState.mediaSessions(ctx, config.appVisibility)
        val livePkgs = sessions.map { it.packageName }.toSet()
        val allMemory = MediaMemory.all(ctx)
        val sinceByPkg = allMemory.associate { it.pkg to it.playingSince }

        val memory = if (!config.rememberLast) emptyList() else allMemory.filter {
            it.title.isNotBlank() && it.pkg !in livePkgs &&
                SessionFilter.isVisible(it.pkg, config.appVisibility) && isInstalled(ctx, it.pkg)
        }
        val live = sessions.map {
            LiveInfo(
                pkg = it.packageName,
                playing = it.playbackState?.state == PlaybackState.STATE_PLAYING,
                playingSince = sinceByPkg[it.packageName] ?: 0L
            )
        }
        val source = SourcePicker.pick(
            live, memory.map { it.pkg },
            selected = config.selectedAppPackage, selectedAt = config.selectedAt,
            preferred = config.preferredApp
        )
        return Shown(
            controller = (source as? Source.Live)?.let { s -> sessions.firstOrNull { it.packageName == s.pkg } },
            remembered = (source as? Source.Remembered)?.let { s -> memory.firstOrNull { it.pkg == s.pkg } },
            sessions = sessions, memory = memory
        )
    }

    private fun isInstalled(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun renderInto(ctx: Context, manager: AppWidgetManager, id: Int) {
        val config = runBlocking { SettingsRepo(ctx).resolve(id) }
        // Tatsächliche Grösse aus den Optionen lesen; die setzt der Launcher.
        val opts = manager.getAppWidgetOptions(id)
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 210).toFloat()
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70).toFloat()
        manager.updateAppWidget(id, render(ctx, config, w, h, id))
    }

    private fun demoCover(ctx: Context): Bitmap? = try {
        val d = ctx.getDrawable(R.drawable.preview_cover)!!
        Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888).also {
            d.setBounds(0, 0, 384, 384); d.draw(android.graphics.Canvas(it))
        }
    } catch (_: Throwable) { null }

    /**
     * Setzt das Cover quadratisch und so gross, wie die Stufe Platz lässt.
     * In kleinen Hochformaten fallen App-Icons, Aktionen und Fortschritt weg,
     * damit das Cover nicht zur Briefmarke wird.
     */
    private fun sizeCover(rv: RemoteViews, bucket: Bucket, w: Float, h: Float) {
        val side = when (bucket) {
            Bucket.MID_CARD -> minOf(h - 24f, w * 0.45f)
            Bucket.MEGA -> minOf(h - 28f, w * 0.45f)
            Bucket.WIDE -> {
                val compact = h < TALL_COMPACT_BELOW
                val showActions = h >= TALL_ACTIONS_FROM
                if (compact) {
                    rv.setViewVisibility(R.id.app_switcher_row, View.GONE)
                    rv.setViewVisibility(R.id.progress, View.GONE)
                }
                if (!showActions) rv.setViewVisibility(R.id.actions_row, View.GONE)
                // Höhe von Rand, Text und Knöpfen (und Aktionen, Fortschritt), gemessen am Layout
                val chrome = (if (compact) 122f else 134f) + (if (showActions) 40f else 0f)
                val fit = minOf(w - 24f, h - chrome)
                // Füllt das Cover die ganze Breite, lägen die App-Icons in der Ecke darauf:
                // dann das Cover etwas kleiner, sodass sie darüber Platz haben.
                if (!compact && w - 24f - fit < 56f) fit - 40f else fit
            }
            else -> return
        }
        if (side < 40f) {
            rv.setViewVisibility(R.id.cover, View.GONE)
        } else {
            rv.setViewVisibility(R.id.cover, View.VISIBLE)
            rv.setViewLayoutWidth(R.id.cover, side, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(R.id.cover, side, TypedValue.COMPLEX_UNIT_DIP)
        }
    }

    /**
     * Baut das Widget für eine Grösse in dp. Auch die Grössen-Übersicht in der
     * App ruft das auf, damit sie exakt zeigt, was der Launcher bekäme.
     */
    fun render(
        ctx: Context, config: WidgetConfig, w: Float, h: Float, id: Int, demo: Boolean = false
    ): RemoteViews {
        // demo: Beispielinhalt statt der echten Wiedergabe, für Vorschau und Bildschirmfotos.
        val shown = if (demo) Shown(
            controller = null,
            remembered = Remembered(
                DEMO_PKG, ctx.getString(R.string.preview_title), ctx.getString(R.string.preview_artist),
                positionMs = 95_000, durationMs = 240_000, playing = false, playingSince = 0, savedAt = 0
            ),
            sessions = emptyList(), memory = emptyList()
        ) else resolve(ctx, config)
        val bucket = Bucket.pickForSize(SizeF(w, h))
        // Einschalten mit: adb shell setprop log.tag.MediaWidget DEBUG
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "render id=$id w=${w}dp h=${h}dp → bucket=$bucket app=${shown.pkg} live=${shown.controller != null}")
        }
        val rv = build(ctx, bucket, config, shown, id, w)
        sizeCover(rv, bucket, w, h)
        return rv
    }

    private fun build(
        ctx: Context, bucket: Bucket, config: WidgetConfig, shown: Shown, widgetId: Int, widthDp: Float
    ): RemoteViews {
        val layoutId = when (bucket) {
            Bucket.MICRO_BAR -> R.layout.widget_microbar
            Bucket.BAR -> R.layout.widget_bar
            Bucket.MID_CARD -> R.layout.widget_midcard
            Bucket.WIDE -> R.layout.widget_wide
            Bucket.MEGA -> R.layout.widget_mega
        }
        val rv = RemoteViews(ctx.packageName, layoutId)
        val md = shown.controller?.metadata
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: shown.remembered?.let { if (it.pkg == DEMO_PKG) demoCover(ctx) else MediaMemory.cover(ctx, it.pkg) }

        bindContent(ctx, rv, bucket, config, shown, art)
        config.theme.applyTo(ctx, rv, art)
        bindAppActions(ctx, rv, bucket, config, shown.controller, widgetId, widthDp)
        bindAppSwitchers(ctx, rv, bucket, shown, widgetId)
        bindClicks(ctx, rv, config, shown, widgetId)
        return rv
    }

    private fun bindContent(
        ctx: Context, rv: RemoteViews, bucket: Bucket,
        config: WidgetConfig, shown: Shown, art: Bitmap?
    ) {
        val controller = shown.controller
        val remembered = shown.remembered

        if (controller == null && remembered == null) {
            tryText(rv, R.id.title, ctx.getString(R.string.idle_title))
            tryText(rv, R.id.artist, ctx.getString(
                if (MediaState.listenerEnabled(ctx)) R.string.idle_artist
                else R.string.need_permission
            ))
            tryImageRes(rv, R.id.cover, R.drawable.ic_music)
            tryImageRes(rv, R.id.btn_play_pause, R.drawable.ic_play)
            tryVisibility(rv, R.id.progress, View.GONE)
            return
        }

        val md = controller?.metadata
        tryText(rv, R.id.title, md?.titleOrNull() ?: remembered?.title ?: ctx.getString(R.string.unknown_title))
        tryText(
            rv, R.id.artist,
            md?.artistOrNull() ?: remembered?.artist?.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.unknown_artist)
        )

        if (art != null) rv.setImageViewBitmap(R.id.cover, art)
        else tryImageRes(rv, R.id.cover, R.drawable.ic_music)

        val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        tryImageRes(rv, R.id.btn_play_pause, if (playing) R.drawable.ic_pause else R.drawable.ic_play)

        // Fortschrittsbalken: in allen Stufen, deren Layout einen hat (ab Karte)
        val posMs = controller?.playbackState?.position ?: remembered?.positionMs ?: 0L
        val durMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: remembered?.durationMs ?: 0L
        if (config.showProgressBar && durMs > 0L) {
            try {
                // In Sekunden rechnen: Millisekunden sprengen Int bei langen Hörbüchern
                val posSec = (posMs / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val durSec = (durMs / 1000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                rv.setProgressBar(R.id.progress, durSec, posSec, false)
                rv.setViewVisibility(R.id.progress, View.VISIBLE)
            } catch (_: Throwable) {
                tryVisibility(rv, R.id.progress, View.GONE)
            }
        } else {
            tryVisibility(rv, R.id.progress, View.GONE)
        }
    }

    private fun bindClicks(
        ctx: Context, rv: RemoteViews, config: WidgetConfig, shown: Shown, widgetId: Int
    ) {
        // Beendete App: Play startet sie wieder (siehe resumePendingIntent).
        val playIntent = shown.remembered?.let { resumePendingIntent(ctx, it.pkg, widgetId) }
            ?: transportIntent(ctx, ACTION_PLAY_PAUSE, widgetId)
        rv.setOnClickPendingIntent(R.id.btn_play_pause, playIntent)
        // Zurück/Weiter fehlen in der kleinsten Stufe; fehlende Ids ignoriert RemoteViews.
        try { rv.setOnClickPendingIntent(R.id.btn_prev, transportIntent(ctx, ACTION_PREV, widgetId)) } catch (_: Throwable) {}
        try { rv.setOnClickPendingIntent(R.id.btn_next, transportIntent(ctx, ACTION_NEXT, widgetId)) } catch (_: Throwable) {}

        val pkg = shown.pkg
        if (config.openAppOnCoverTap && pkg != null) {
            try {
                val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    val pi = PendingIntent.getActivity(
                        ctx, ("cover_${widgetId}_$pkg").hashCode(), launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    rv.setOnClickPendingIntent(R.id.cover, pi)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun tryInt(rv: RemoteViews, viewId: Int, method: String, value: Int) {
        try { rv.setInt(viewId, method, value) } catch (_: Throwable) {}
    }

    private fun tryVisibility(rv: RemoteViews, viewId: Int, visibility: Int) {
        try { rv.setViewVisibility(viewId, visibility) } catch (_: Throwable) {}
    }

    private fun bindAppActions(
        ctx: Context, rv: RemoteViews, bucket: Bucket,
        config: WidgetConfig, controller: MediaController?, widgetId: Int, widthDp: Float
    ) {
        val slotIds = listOf(R.id.action_slot_1, R.id.action_slot_2, R.id.action_slot_3, R.id.action_slot_4)
        // Wie viele App-Aktionen Platz haben, hängt von der Breite neben dem Cover ab.
        val maxSlots = when (bucket) {
            Bucket.MEGA -> if (widthDp >= 380f) 4 else 2
            Bucket.WIDE -> if (widthDp >= 240f) 4 else 2
            Bucket.MID_CARD -> if (widthDp >= 320f) 2 else 0
            Bucket.BAR -> 1
            else -> 0
        }

        if (config.appActionsMode == AppActionsMode.OFF || maxSlots == 0 || controller == null) {
            tryVisibility(rv, R.id.actions_row, View.GONE)
            slotIds.forEach { tryVisibility(rv, it, View.GONE) }
            return
        }

        val raw = controller.playbackState?.customActions.orEmpty()
        var slotIdx = 0
        val pkg = controller.packageName

        for (action in raw) {
            if (slotIdx >= maxSlots) break
            val polish = CuratedActions.polish(action.action.toString())
            val iconRes: Int? = when {
                polish != null -> polish.icon
                config.appActionsMode == AppActionsMode.AUTO && config.appActionsAllowRaw ->
                    null  // unbekannte Aktion → Icon der App verwenden (unten)
                else -> continue
            }
            val slotId = slotIds[slotIdx]
            if (iconRes != null) {
                tryImageRes(rv, slotId, iconRes)
            } else {
                val bmp = loadForeignIcon(ctx, pkg, action.icon) ?: continue
                rv.setImageViewBitmap(slotId, bmp)
            }
            tryVisibility(rv, slotId, View.VISIBLE)
            rv.setOnClickPendingIntent(slotId, customActionPendingIntent(ctx, action.action.toString(), widgetId))
            slotIdx++
        }
        for (i in slotIdx until slotIds.size) tryVisibility(rv, slotIds[i], View.GONE)
        tryVisibility(rv, R.id.actions_row, if (slotIdx > 0) View.VISIBLE else View.GONE)
    }

    /**
     * App-Icons in der Ecke: zuerst die laufenden Apps, dann die gemerkten.
     * Tipp wählt die App für dieses Widget.
     */
    private fun bindAppSwitchers(
        ctx: Context, rv: RemoteViews, bucket: Bucket, shown: Shown, widgetId: Int
    ) {
        val slotIds = listOf(R.id.app_switcher_1, R.id.app_switcher_2, R.id.app_switcher_3)
        val pkgs = (shown.sessions.map { it.packageName } + shown.memory.map { it.pkg }).distinct()
        // Die kleinste Stufe hat keinen Platz dafür
        if (bucket == Bucket.MICRO_BAR || pkgs.isEmpty()) {
            tryVisibility(rv, R.id.app_switcher_row, View.GONE)
            slotIds.forEach { tryVisibility(rv, it, View.GONE) }
            return
        }

        val visible = pkgs.take(slotIds.size)
        visible.forEachIndexed { idx, pkg ->
            val slotId = slotIds[idx]
            val icon = loadAppIcon(ctx, pkg)
            if (icon != null) {
                rv.setImageViewBitmap(slotId, icon)
                tryVisibility(rv, slotId, View.VISIBLE)
                rv.setOnClickPendingIntent(slotId, switchAppPendingIntent(ctx, pkg, widgetId))
                // Gezeigte App voll deckend, die anderen zurückgenommen
                tryInt(rv, slotId, "setImageAlpha", if (pkg == shown.pkg) 255 else 130)
            } else {
                tryVisibility(rv, slotId, View.GONE)
            }
        }
        for (i in visible.size until slotIds.size) tryVisibility(rv, slotIds[i], View.GONE)
        tryVisibility(rv, R.id.app_switcher_row, View.VISIBLE)
    }

    private fun loadAppIcon(ctx: Context, pkg: String): Bitmap? = try {
        val drawable = ctx.packageManager.getApplicationIcon(pkg)
        val size = (28 * ctx.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bmp
    } catch (_: Throwable) { null }

    private fun switchAppPendingIntent(ctx: Context, pkg: String, widgetId: Int): PendingIntent {
        val intent = Intent(ctx, MediaWidgetProvider::class.java)
            .setAction(ACTION_SWITCH_APP)
            .putExtra(EXTRA_SWITCH_APP_PACKAGE, pkg)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            ctx, ("switch_${widgetId}_$pkg").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Play für eine beendete App. Normalfall: unsichtbarer Wiederstart per Broadcast.
     * Hat das bei dieser App schon einmal nicht geklappt, öffnet der Knopf sie direkt.
     */
    private fun resumePendingIntent(ctx: Context, pkg: String, widgetId: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (MediaMemory.needsOpen(ctx, pkg)) {
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                return PendingIntent.getActivity(ctx, ("open_${widgetId}_$pkg").hashCode(), it, flags)
            }
        }
        val intent = Intent(ctx, MediaWidgetProvider::class.java)
            .setAction(ACTION_RESUME)
            .putExtra(EXTRA_RESUME_PACKAGE, pkg)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(ctx, ("resume_${widgetId}_$pkg").hashCode(), intent, flags)
    }

    private fun loadForeignIcon(ctx: Context, pkg: String, iconRes: Int): Bitmap? = try {
        val foreign = ctx.packageManager.getResourcesForApplication(pkg)
        val drawable = foreign.getDrawable(iconRes, null) ?: return null
        val size = (24 * ctx.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bmp
    } catch (_: Throwable) { null }

    private fun customActionPendingIntent(ctx: Context, actionName: String, widgetId: Int): PendingIntent {
        val intent = Intent(ctx, MediaWidgetProvider::class.java)
            .setAction(ACTION_CUSTOM)
            .putExtra(EXTRA_CUSTOM_ACTION_NAME, actionName)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            ctx, ("custom_${widgetId}_$actionName").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun transportIntent(ctx: Context, action: String, widgetId: Int): PendingIntent {
        val intent = Intent(ctx, MediaWidgetProvider::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            ctx, ("${action}_$widgetId").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Toleranter Setter: ignoriert Views, die es in dieser Layout-Stufe nicht gibt. */
    private fun tryText(rv: RemoteViews, viewId: Int, text: CharSequence) {
        try { rv.setTextViewText(viewId, text) } catch (_: Throwable) {}
    }

    private fun tryImageRes(rv: RemoteViews, viewId: Int, resId: Int) {
        try { rv.setImageViewResource(viewId, resId) } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "MediaWidget"
        private const val DEMO_PKG = "demo"
        private const val TALL_COMPACT_BELOW = 230f
        private const val TALL_ACTIONS_FROM = 330f
        const val ACTION_PLAY_PAUSE = "com.meo.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.meo.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.meo.mediawidget.ACTION_PREV"
        const val ACTION_CUSTOM = "com.meo.mediawidget.ACTION_CUSTOM"
        const val EXTRA_CUSTOM_ACTION_NAME = "custom_action_name"
        const val ACTION_SWITCH_APP = "com.meo.mediawidget.ACTION_SWITCH_APP"
        const val EXTRA_SWITCH_APP_PACKAGE = "switch_app_package"
        const val ACTION_RESUME = "com.meo.mediawidget.ACTION_RESUME"
        const val EXTRA_RESUME_PACKAGE = "resume_package"
        private val TRANSPORT_ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_CUSTOM)

        /**
         * Zeichnet alle Widgets neu. Direkt im Prozess statt per Broadcast:
         * schneller, und ein wartender Broadcast kann das Neuzeichnen nicht aufhalten.
         */
        fun requestUpdate(ctx: Context) {
            val app = ctx.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = WidgetIds.all(app)
            if (ids.isNotEmpty()) MediaWidgetProvider().onUpdate(app, manager, ids)
        }
    }
}

/**
 * Zusätzliche Einträge in der Widget-Auswahl. Sie unterscheiden sich nur in
 * Startgrösse und Vorschaubild (siehe res/xml); danach lässt sich jedes Widget
 * auf jede Grösse ziehen und verhält sich gleich.
 */
class MediaWidgetCardProvider : MediaWidgetProvider()

class MediaWidgetLargeProvider : MediaWidgetProvider()

private fun MediaMetadata.titleOrNull(): String? =
    (getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        ?.takeIf { it.isNotBlank() }

private fun MediaMetadata.artistOrNull(): String? =
    (getString(MediaMetadata.METADATA_KEY_ARTIST) ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
        ?.takeIf { it.isNotBlank() }
