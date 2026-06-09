package com.meo.mediawidget

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

/**
 * Kapselt den Zugriff auf den "aktuell aktiven" MediaController des
 * Geräts. Liefert null wenn entweder:
 *   - der Notification-Listener noch nicht aktiviert wurde
 *     (= SecurityException beim getActiveSessions-Aufruf), oder
 *   - keine App gerade eine Media-Session offen hält.
 */
object MediaState {

    fun listenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, NotifListener::class.java).flattenToString()
        return flat.split(":").any { it == expected }
    }

    /**
     * Wählt den "interessantesten" Controller:
     *   1. Einen mit PlaybackState == PLAYING (es läuft gerade was).
     *   2. Sonst den ersten mit nicht-null Metadata (zuletzt aktiv).
     *   3. Sonst null.
     */
    fun pickActive(context: Context): MediaController? {
        if (!listenerEnabled(context)) return null
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val component = ComponentName(context, NotifListener::class.java)

        val controllers = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            return null
        }

        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        if (playing != null) return playing

        return controllers.firstOrNull { it.metadata != null }
    }
}
