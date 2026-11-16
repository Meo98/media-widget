package com.meo.mediawidget

/** Eine laufende (lebende) Session, reduziert auf das, was die Auswahl braucht. */
data class LiveInfo(
    val pkg: String,
    val playing: Boolean,
    /** Zeitpunkt (ms), an dem die App zuletzt zu spielen begann. 0 = nie gesehen. */
    val playingSince: Long
)

sealed interface Source {
    /** Die App hat eine aktive Session: normal steuerbar. */
    data class Live(val pkg: String) : Source
    /** Die App ist beendet: wir zeigen den gemerkten Stand und bieten Wiederstart an. */
    data class Remembered(val pkg: String) : Source
    data object None : Source
}

/**
 * Entscheidet, welche App ein Widget zeigt. Reihenfolge:
 *  1. Die im Widget angetippte App — solange seither keine andere App zu
 *     spielen begonnen hat (sonst gewinnt die neuere Absicht).
 *  2. Die global bevorzugte App, wenn sie läuft.
 *  3. Irgendeine laufende Session: spielende zuerst, dann die zuletzt gestartete.
 *  4. Der gemerkte Stand einer beendeten App.
 */
object SourcePicker {

    fun pick(
        live: List<LiveInfo>,
        /** Pakete mit gemerktem Stand, neuestes zuerst. */
        remembered: List<String>,
        selected: String?,
        selectedAt: Long,
        preferred: String?
    ): Source {
        if (selected != null) {
            val newerExists = live.any { it.pkg != selected && it.playingSince > selectedAt }
            if (!newerExists) {
                if (live.any { it.pkg == selected }) return Source.Live(selected)
                if (selected in remembered) return Source.Remembered(selected)
            }
        }
        if (preferred != null && live.any { it.pkg == preferred }) return Source.Live(preferred)

        live.sortedWith(compareByDescending<LiveInfo> { it.playing }.thenByDescending { it.playingSince })
            .firstOrNull()?.let { return Source.Live(it.pkg) }

        if (preferred != null && preferred in remembered) return Source.Remembered(preferred)
        remembered.firstOrNull()?.let { return Source.Remembered(it) }
        return Source.None
    }
}
