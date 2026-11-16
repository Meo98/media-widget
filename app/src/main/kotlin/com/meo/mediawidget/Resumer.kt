package com.meo.mediawidget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Startet die Wiedergabe einer beendeten App wieder. Android bietet dafür
 * keinen garantierten Weg, deshalb drei Stufen, von unauffällig bis sicher:
 *
 *  1. MediaBrowserService der App binden und play() schicken — derselbe Weg,
 *     den die System-Mediensteuerung für "Fortsetzen" nimmt. Manche Apps
 *     lassen nur bekannte Aufrufer zu.
 *  2. Der App die Medientaste "Play" direkt zustellen.
 *  3. Die App öffnen ([Outcome.OPEN_APP]): der Aufrufer merkt sich das, und der
 *     Play-Knopf des Widgets öffnet diese App künftig direkt.
 *
 * Wo die App weiterspielt, entscheidet sie selbst; die meisten merken sich
 * Titel und Position.
 */
object Resumer {

    enum class Outcome { PLAYING, OPEN_APP }

    private const val TAG = "MediaWidget"
    private const val POLL_MS = 250L
    private const val MEDIA_BUTTON_AT_MS = 1800L
    private const val GIVE_UP_AT_MS = 3400L
    private const val KEEP_BOUND_MS = 10_000L
    private const val ACTION_BROWSER_SERVICE = "android.media.browse.MediaBrowserService"
    private const val EXTRA_RECENT = "android.service.media.extra.RECENT"

    private val handler = Handler(Looper.getMainLooper())
    /** Hält die Bindung noch kurz, damit die App Zeit hat, ihren Dienst in den Vordergrund zu heben. */
    private var browser: MediaBrowser? = null

    fun resume(ctx: Context, pkg: String, onDone: (Outcome) -> Unit) {
        val app = ctx.applicationContext
        val start = System.currentTimeMillis()
        var finished = false
        var buttonSent = false

        fun finish(outcome: Outcome) {
            if (finished) return
            finished = true
            Log.i(TAG, "resume $pkg → $outcome nach ${System.currentTimeMillis() - start} ms")
            onDone(outcome)
        }

        // Session lebt doch noch (pausiert)? Dann genügt ein normales play().
        MediaState.rawSessions(app).firstOrNull { it.packageName == pkg }?.transportControls?.play()

        if (!connectBrowser(app, pkg)) {
            buttonSent = true
            sendPlayButton(app, pkg)
        }

        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                val elapsed = System.currentTimeMillis() - start
                when {
                    MediaState.isPlaying(app, pkg) -> finish(Outcome.PLAYING)
                    elapsed >= GIVE_UP_AT_MS -> finish(Outcome.OPEN_APP)
                    else -> {
                        if (!buttonSent && elapsed >= MEDIA_BUTTON_AT_MS) {
                            buttonSent = true
                            sendPlayButton(app, pkg)
                        }
                        handler.postDelayed(this, POLL_MS)
                    }
                }
            }
        }
        handler.postDelayed(poll, POLL_MS)
    }

    private fun connectBrowser(app: Context, pkg: String): Boolean {
        val service = try {
            app.packageManager.queryIntentServices(Intent(ACTION_BROWSER_SERVICE).setPackage(pkg), 0)
                .firstOrNull()?.serviceInfo
        } catch (_: Throwable) { null } ?: return false

        releaseBrowser()
        return try {
            lateinit var b: MediaBrowser
            b = MediaBrowser(
                app, ComponentName(service.packageName, service.name),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        try {
                            val tc = MediaController(app, b.sessionToken).transportControls
                            tc.prepare()
                            tc.play()
                        } catch (t: Throwable) {
                            Log.i(TAG, "resume $pkg: play() über Browser scheiterte: $t")
                        }
                    }
                    override fun onConnectionFailed() {
                        Log.i(TAG, "resume $pkg: App lehnt die Browser-Verbindung ab")
                    }
                },
                Bundle().apply { putBoolean(EXTRA_RECENT, true) }
            )
            b.connect()
            browser = b
            handler.postDelayed({ if (browser === b) releaseBrowser() }, KEEP_BOUND_MS)
            true
        } catch (t: Throwable) {
            Log.i(TAG, "resume $pkg: Browser nicht nutzbar: $t")
            false
        }
    }

    private fun releaseBrowser() {
        try { browser?.disconnect() } catch (_: Throwable) {}
        browser = null
    }

    private fun sendPlayButton(app: Context, pkg: String) {
        try {
            val receiver = app.packageManager
                .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg), 0)
                .firstOrNull()?.activityInfo ?: return
            val component = ComponentName(receiver.packageName, receiver.name)
            for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                app.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(component)
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
                )
            }
        } catch (t: Throwable) {
            Log.i(TAG, "resume $pkg: Medientaste nicht zustellbar: $t")
        }
    }
}
