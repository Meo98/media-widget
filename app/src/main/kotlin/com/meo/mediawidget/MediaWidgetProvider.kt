package com.meo.mediawidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.widget.RemoteViews

class MediaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        ids.forEach { id -> render(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV -> {
                handleTransport(context, intent.action!!)
                pushUpdateAll(context)
            }
        }
    }

    private fun handleTransport(context: Context, action: String) {
        val controls = MediaState.pickActive(context)?.transportControls ?: return
        when (action) {
            ACTION_PLAY_PAUSE -> {
                val state = MediaState.pickActive(context)?.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) controls.pause()
                else controls.play()
            }
            ACTION_NEXT -> controls.skipToNext()
            ACTION_PREV -> controls.skipToPrevious()
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.media_widget)
        val controller = MediaState.pickActive(context)

        if (controller == null) {
            views.setTextViewText(R.id.title, context.getString(R.string.idle_title))
            views.setTextViewText(R.id.artist, context.getString(
                if (MediaState.listenerEnabled(context)) R.string.idle_artist
                else R.string.need_permission
            ))
            views.setImageViewResource(R.id.cover, R.drawable.ic_music)
            views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
        } else {
            val md = controller.metadata
            views.setTextViewText(R.id.title, md?.titleOrFallback(context))
            views.setTextViewText(R.id.artist, md?.artistOrFallback(context))

            val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (art != null) views.setImageViewBitmap(R.id.cover, art)
            else views.setImageViewResource(R.id.cover, R.drawable.ic_music)

            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            views.setImageViewResource(
                R.id.btn_play_pause,
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        views.setOnClickPendingIntent(R.id.btn_play_pause, transportIntent(context, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.btn_next, transportIntent(context, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.btn_prev, transportIntent(context, ACTION_PREV))

        manager.updateAppWidget(id, views)
    }

    private fun transportIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MediaWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pushUpdateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, MediaWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        onUpdate(context, manager, ids)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.meo.mediawidget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.meo.mediawidget.ACTION_NEXT"
        const val ACTION_PREV = "com.meo.mediawidget.ACTION_PREV"

        /** Von außen aufrufbar: zwingt sofortiges Re-Render aller Instanzen. */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, MediaWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
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
