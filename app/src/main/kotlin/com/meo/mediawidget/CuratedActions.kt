package com.meo.mediawidget

import androidx.annotation.DrawableRes

object CuratedActions {

    data class Polish(@DrawableRes val icon: Int, val label: String)

    private val PATTERNS: List<Pair<Regex, Polish>> = listOf(
        Regex("(?i)(thumbs?[_\\s]?down|dislike)") to Polish(R.drawable.ic_thumb_down, "Dislike"),
        Regex("(?i)(thumbs?[_\\s]?up)") to Polish(R.drawable.ic_thumb_up, "Thumbs up"),
        Regex("(?i)(like|favou?rite|heart|love)") to Polish(R.drawable.ic_heart, "Like"),
        Regex("(?i)(queue|add.*playlist)") to Polish(R.drawable.ic_queue, "Queue"),
        Regex("(?i)shuffle") to Polish(R.drawable.ic_shuffle, "Shuffle"),
        Regex("(?i)(repeat|loop)") to Polish(R.drawable.ic_repeat, "Repeat"),
    )

    fun polish(actionName: String): Polish? =
        PATTERNS.firstOrNull { (re, _) -> re.containsMatchIn(actionName) }?.second
}
