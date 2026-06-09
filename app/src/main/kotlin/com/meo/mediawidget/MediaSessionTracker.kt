package com.meo.mediawidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

object MediaSessionTracker {

    private var appContext: Context? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pushUpdate = Runnable { reallyPushUpdate() }

    fun start(ctx: Context) {
        if (appContext != null) return  // already started
        val app = ctx.applicationContext
        appContext = app

        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(app, NotifListener::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            rebind(controllers ?: emptyList())
            scheduleUpdate()
        }
        sessionsListener = listener

        try {
            msm.addOnActiveSessionsChangedListener(listener, component)
            rebind(msm.getActiveSessions(component))
            scheduleUpdate()
        } catch (_: SecurityException) {
            // permission not granted yet; will retry when onListenerConnected fires again
        }
    }

    fun stop() {
        val app = appContext ?: return
        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        sessionsListener?.let { msm?.removeOnActiveSessionsChangedListener(it) }
        sessionsListener = null

        controllerCallbacks.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        controllerCallbacks.clear()

        mainHandler.removeCallbacks(pushUpdate)
        appContext = null
    }

    private fun rebind(controllers: List<MediaController>) {
        // unsubscribe controllers no longer active
        val stale = controllerCallbacks.keys.filterNot { c -> controllers.any { it == c } }
        stale.forEach { c ->
            controllerCallbacks.remove(c)?.let { c.unregisterCallback(it) }
        }
        // subscribe new controllers
        controllers.filterNot { it in controllerCallbacks }.forEach { controller ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleUpdate()
                override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleUpdate()
                override fun onSessionDestroyed() = scheduleUpdate()
            }
            controller.registerCallback(cb)
            controllerCallbacks[controller] = cb
        }
    }

    private fun scheduleUpdate() {
        mainHandler.removeCallbacks(pushUpdate)
        mainHandler.postDelayed(pushUpdate, DEBOUNCE_MS)
    }

    private fun reallyPushUpdate() {
        val app = appContext ?: return
        MediaWidgetProvider.requestUpdate(app)
    }

    private const val DEBOUNCE_MS = 100L
}
