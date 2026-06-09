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
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediaWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        scope.launch { ids.forEach { repo.clearWidget(it) } }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val controller = MediaState.pickActive(ctx)?.transportControls
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val state = MediaState.pickActive(ctx)?.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) controller?.pause() else controller?.play()
            }
            ACTION_NEXT -> controller?.skipToNext()
            ACTION_PREV -> controller?.skipToPrevious()
        }
        // No manual push — MediaSessionTracker fires via Callback after the action takes effect
    }

    private fun renderInto(ctx: Context, manager: AppWidgetManager, id: Int) {
        scope.launch {
            val config = SettingsRepo(ctx).resolve(id)
            val controller = MediaState.pickActive(ctx)

            val micro = build(ctx, Bucket.MICRO_BAR, config, controller)
            val bar = build(ctx, Bucket.BAR, config, controller)
            val mid = build(ctx, Bucket.MID_CARD, config, controller)
            val wide = build(ctx, Bucket.WIDE, config, controller)
            val mega = build(ctx, Bucket.MEGA, config, controller)

            val rv = RemoteViews(mapOf(
                SizeF(210f, 70f) to micro,
                SizeF(290f, 70f) to bar,
                SizeF(370f, 70f) to bar,
                SizeF(140f, 140f) to mid,
                SizeF(370f, 210f) to mid,
                SizeF(210f, 290f) to wide,
                SizeF(290f, 290f) to wide,
                SizeF(370f, 290f) to mega
            ))
            manager.updateAppWidget(id, rv)
        }
    }

    private fun build(
        ctx: Context, bucket: Bucket, config: WidgetConfig, controller: MediaController?
    ): RemoteViews {
        val layoutId = when (bucket) {
            Bucket.MICRO_BAR -> R.layout.widget_microbar
            Bucket.BAR -> R.layout.widget_bar
            Bucket.MID_CARD -> R.layout.widget_midcard
            Bucket.WIDE -> R.layout.widget_wide
            Bucket.MEGA -> R.layout.widget_mega
        }
        val rv = RemoteViews(ctx.packageName, layoutId)
        val assets = StyleAssets.forStyle(config.style, ctx)
        bindContent(ctx, rv, bucket, controller)
        applyStyle(ctx, rv, bucket, assets, controller)
        bindClicks(ctx, rv, bucket)
        return rv
    }

    private fun bindContent(ctx: Context, rv: RemoteViews, bucket: Bucket, controller: MediaController?) {
        if (controller == null) {
            tryText(rv, R.id.title, ctx.getString(R.string.idle_title))
            tryText(rv, R.id.artist, ctx.getString(
                if (MediaState.listenerEnabled(ctx)) R.string.idle_artist
                else R.string.need_permission
            ))
            tryImageRes(rv, R.id.cover, R.drawable.ic_music)
            tryImageRes(rv, R.id.btn_play_pause, R.drawable.ic_play)
            return
        }
        val md = controller.metadata
        tryText(rv, R.id.title, md?.titleOrFallback(ctx) ?: ctx.getString(R.string.unknown_title))
        tryText(rv, R.id.artist, md?.artistOrFallback(ctx) ?: ctx.getString(R.string.unknown_artist))

        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art != null) rv.setImageViewBitmap(R.id.cover, art)
        else tryImageRes(rv, R.id.cover, R.drawable.ic_music)

        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        tryImageRes(rv, R.id.btn_play_pause, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun bindClicks(ctx: Context, rv: RemoteViews, bucket: Bucket) {
        rv.setOnClickPendingIntent(R.id.btn_play_pause, transportIntent(ctx, ACTION_PLAY_PAUSE))
        // prev/next exist in BAR+. setOnClickPendingIntent on a missing id no-ops since API 26 but
        // we guard with try-catch to be defensive across the bucket layouts that omit them.
        try { rv.setOnClickPendingIntent(R.id.btn_prev, transportIntent(ctx, ACTION_PREV)) } catch (_: Throwable) {}
        try { rv.setOnClickPendingIntent(R.id.btn_next, transportIntent(ctx, ACTION_NEXT)) } catch (_: Throwable) {}
    }

    private fun applyStyle(
        ctx: Context, rv: RemoteViews, bucket: Bucket, assets: StyleAssets, controller: MediaController?
    ) {
        rv.setInt(R.id.container, "setBackgroundResource", assets.containerBg)
        tryColor(rv, R.id.title, assets.textPrimary)
        tryColor(rv, R.id.artist, assets.textSecondary)
        tryTint(rv, R.id.btn_play_pause, assets.buttonTint)
        tryTint(rv, R.id.btn_prev, assets.buttonTint)
        tryTint(rv, R.id.btn_next, assets.buttonTint)
        tryInt(rv, R.id.cover, "setBackgroundResource", assets.coverBg)

        if (assets.useBlurredBackdrop && bucket == Bucket.MEGA) {
            val art = controller?.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: controller?.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            if (art != null) {
                rv.setImageViewBitmap(R.id.backdrop, BlurHelper.blurForGlass(art, ctx))
                rv.setViewVisibility(R.id.backdrop, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.backdrop, View.GONE)
            }
        } else {
            tryVisibility(rv, R.id.backdrop, View.GONE)
        }
    }

    private fun tryColor(rv: RemoteViews, viewId: Int, color: Int) {
        try { rv.setTextColor(viewId, color) } catch (_: Throwable) {}
    }

    private fun tryTint(rv: RemoteViews, viewId: Int, color: Int) {
        try { rv.setInt(viewId, "setColorFilter", color) } catch (_: Throwable) {}
    }

    private fun tryInt(rv: RemoteViews, viewId: Int, method: String, value: Int) {
        try { rv.setInt(viewId, method, value) } catch (_: Throwable) {}
    }

    private fun tryVisibility(rv: RemoteViews, viewId: Int, visibility: Int) {
        try { rv.setViewVisibility(viewId, visibility) } catch (_: Throwable) {}
    }

    private fun transportIntent(ctx: Context, action: String): PendingIntent {
        val intent = Intent(ctx, MediaWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            ctx, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Tolerant setter: ignores views not present in this bucket's layout. */
    private fun tryText(rv: RemoteViews, viewId: Int, text: CharSequence) {
        try { rv.setTextViewText(viewId, text) } catch (_: Throwable) {}
    }

    private fun tryImageRes(rv: RemoteViews, viewId: Int, resId: Int) {
        try { rv.setImageViewResource(viewId, resId) } catch (_: Throwable) {}
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.meo.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.meo.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.meo.mediawidget.ACTION_PREV"

        fun requestUpdate(ctx: Context) {
            val intent = Intent(ctx, MediaWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(ctx)
                    .getAppWidgetIds(ComponentName(ctx, MediaWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            ctx.sendBroadcast(intent)
        }
    }
}

private fun MediaMetadata.titleOrFallback(ctx: Context): String =
    getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ?: ctx.getString(R.string.unknown_title)

private fun MediaMetadata.artistOrFallback(ctx: Context): String =
    getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: ctx.getString(R.string.unknown_artist)
