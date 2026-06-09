package com.meo.mediawidget

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

object MediaSessionTracker {

    private var appContext: Context? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val controllerCallbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pushUpdate = Runnable { reallyPushUpdate() }

    fun start(ctx: Context) {
        if (appContext != null) return  // already started
        val app = ctx.applicationContext

        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(app, NotifListener::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            rebind(controllers ?: emptyList())
            scheduleUpdate()
        }

        // Set up registration first; only commit state on full success so a
        // SecurityException doesn't leak the listener or block future start() calls.
        val initialControllers = try {
            msm.addOnActiveSessionsChangedListener(listener, component, mainHandler)
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            try { msm.removeOnActiveSessionsChangedListener(listener) } catch (_: Throwable) {}
            return
        }

        appContext = app
        sessionsListener = listener
        rebind(initialControllers)
        scheduleUpdate()
    }

    fun stop() {
        val app = appContext ?: return
        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        sessionsListener?.let { msm?.removeOnActiveSessionsChangedListener(it) }
        sessionsListener = null

        controllerCallbacks.values.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        controllerCallbacks.clear()

        mainHandler.removeCallbacks(pushUpdate)
        appContext = null
    }

    private fun rebind(controllers: List<MediaController>) {
        val incomingTokens = controllers.map { it.sessionToken }.toSet()

        // unsubscribe controllers whose session token is no longer active
        val staleTokens = controllerCallbacks.keys.filterNot { it in incomingTokens }
        staleTokens.forEach { token ->
            controllerCallbacks.remove(token)?.let { (controller, cb) -> controller.unregisterCallback(cb) }
        }

        // subscribe controllers whose session token isn't yet tracked
        controllers.filterNot { it.sessionToken in controllerCallbacks }.forEach { controller ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleUpdate()
                override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleUpdate()
                override fun onSessionDestroyed() = scheduleUpdate()
            }
            controller.registerCallback(cb, mainHandler)
            controllerCallbacks[controller.sessionToken] = controller to cb
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
