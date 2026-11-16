package com.meo.mediawidget

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

/**
 * Kapselt den Zugriff auf die aktiven Media-Sessions des Geräts. Ohne
 * aktivierten Notification-Listener verweigert Android den Zugriff
 * (SecurityException) — dann liefern alle Funktionen leere Ergebnisse.
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

    /** Alle aktiven Sessions, ungefiltert. Leer, wenn die Berechtigung fehlt. */
    fun rawSessions(context: Context): List<MediaController> {
        if (!listenerEnabled(context)) return emptyList()
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return emptyList()
        return try {
            msm.getActiveSessions(ComponentName(context, NotifListener::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Die Sessions, die ins Widget gehören: keine Anrufe, keine ausgeblendeten
     * Apps, nur solche mit Titel. Spielende zuerst, pro App ein Eintrag.
     */
    fun mediaSessions(context: Context, visibility: Map<String, Boolean>): List<MediaController> {
        val media = rawSessions(context).filter { c ->
            SessionFilter.isMedia(
                SessionInfo(
                    pkg = c.packageName,
                    usage = c.playbackInfo?.audioAttributes?.usage,
                    hasTitle = c.metadata.hasTitle()
                ),
                visibility
            )
        }
        val sorted = media.sortedByDescending { c ->
            when (c.playbackState?.state) {
                PlaybackState.STATE_PLAYING -> 2
                PlaybackState.STATE_PAUSED -> 1
                else -> 0
            }
        }
        val seen = mutableSetOf<String>()
        return sorted.filter { seen.add(it.packageName) }
    }

    fun isPlaying(context: Context, pkg: String): Boolean =
        rawSessions(context).any { it.packageName == pkg && it.playbackState?.state == PlaybackState.STATE_PLAYING }

    private fun MediaMetadata?.hasTitle(): Boolean =
        !(this?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: this?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)).isNullOrBlank()
}
