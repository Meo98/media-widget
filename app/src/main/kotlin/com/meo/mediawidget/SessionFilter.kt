package com.meo.mediawidget

import android.media.AudioAttributes

/** Was wir über eine Session wissen müssen, um zu entscheiden, ob sie "Medien" ist. */
data class SessionInfo(
    val pkg: String,
    /** AudioAttributes.USAGE_* der Session, null wenn unbekannt. */
    val usage: Int?,
    val hasTitle: Boolean
)

/**
 * Trennt echte Medien-Sessions von Anruf-/Konferenz-Sessions. Teams, Meet
 * und Telefon-Apps öffnen während eines Anrufs ebenfalls eine MediaSession
 * (für die Headset-Tasten) — die gehört nicht ins Widget.
 */
object SessionFilter {

    /** Apps, die ohne Zutun des Users ausgeblendet sind. Im App-Menü umschaltbar. */
    val DEFAULT_HIDDEN: Set<String> = setOf(
        "com.microsoft.teams",
        "us.zoom.videomeetings",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.meetings",
        "com.cisco.webex.meetings",
        "com.skype.raider",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
    )

    private val CALL_USAGES = setOf(
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
    )

    /** User-Entscheid (overrides) schlägt die eingebaute Liste. */
    fun isVisible(pkg: String, overrides: Map<String, Boolean>): Boolean =
        overrides[pkg] ?: (pkg !in DEFAULT_HIDDEN)

    fun isCall(usage: Int?): Boolean = usage != null && usage in CALL_USAGES

    fun isMedia(info: SessionInfo, overrides: Map<String, Boolean>): Boolean =
        isVisible(info.pkg, overrides) && !isCall(info.usage) && info.hasTitle
}
