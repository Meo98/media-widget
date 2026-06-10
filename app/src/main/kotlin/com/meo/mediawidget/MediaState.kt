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
     *   1. Wenn preferredPackage gesetzt: bevorzuge Controller mit passendem packageName.
     *   2. Einen mit PlaybackState == PLAYING (es läuft gerade was).
     *   3. Sonst den ersten mit nicht-null Metadata (zuletzt aktiv).
     *   4. Sonst null.
     */
    fun pickActive(context: Context, preferredPackage: String? = null): MediaController? {
        if (!listenerEnabled(context)) return null
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return null
        val component = ComponentName(context, NotifListener::class.java)

        val controllers = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            return null
        }

        if (preferredPackage != null) {
            val preferred = controllers.firstOrNull { it.packageName == preferredPackage }
            if (preferred != null) return preferred
        }

        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        if (playing != null) return playing

        return controllers.firstOrNull { it.metadata != null }
    }

    /**
     * Liste der aktuell aktiven Media-Sessions, sortiert nach Relevanz:
     * Playing zuerst, dann mit Metadata, dann andere. Pro Package nur 1
     * Eintrag (verschiedene Sessions derselben App werden dedupliziert).
     *
     * Wir lassen auch Sessions ohne PlaybackState durch (z.B. KDE-Connect
     * MPRIS proxy hat oft state == null bis die remote App spielt) — sonst
     * verschwinden sie aus dem Switcher obwohl sie aktiv sind.
     */
    fun listActiveSessions(context: Context): List<MediaController> {
        if (!listenerEnabled(context)) return emptyList()
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return emptyList()
        val component = ComponentName(context, NotifListener::class.java)

        val controllers = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            return emptyList()
        }

        // Sort: playing > has-metadata > others. Dedupe by package.
        val sorted = controllers.sortedWith(compareByDescending<MediaController> { c ->
            when {
                c.playbackState?.state == PlaybackState.STATE_PLAYING -> 3
                c.playbackState?.state == PlaybackState.STATE_PAUSED -> 2
                c.metadata != null -> 1
                else -> 0
            }
        })
        val seen = mutableSetOf<String>()
        return sorted.filter { seen.add(it.packageName) }
    }
}
