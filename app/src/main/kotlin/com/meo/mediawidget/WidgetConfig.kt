package com.meo.mediawidget

enum class AppActionsMode(val labelRes: Int) {
    AUTO(R.string.actions_mode_auto),
    POLISHED_ONLY(R.string.actions_mode_polished),
    OFF(R.string.actions_mode_off)
}

/** Was ein einzelnes Widget abweichend vom Standard gesetzt hat. null = folgt dem Standard. */
data class WidgetOverrides(
    /** Kennung des Stils (siehe [Themes]). */
    val themeId: String? = null,
    val appActionsMode: AppActionsMode? = null,
    val appActionsAllowRaw: Boolean? = null,
    val showProgressBar: Boolean? = null,
    val openAppOnCoverTap: Boolean? = null
)

data class WidgetConfig(
    val theme: Theme = Themes.builtIn.first(),
    val appActionsMode: AppActionsMode = AppActionsMode.AUTO,
    val appActionsAllowRaw: Boolean = true,
    val showProgressBar: Boolean = true,
    val openAppOnCoverTap: Boolean = true,
    val preferredApp: String? = null,
    /** User-selected source-app package (per widget). Null = auto (most-active session). */
    val selectedAppPackage: String? = null,
    /** Wann der User die App angetippt hat (ms). Später gestartete Apps gewinnen wieder. */
    val selectedAt: Long = 0L,
    /** Letzten Titel beendeter Apps weiter anzeigen und Wiederstart anbieten. */
    val rememberLast: Boolean = true,
    /** User-Entscheide "App im Widget zeigen" (Paket → sichtbar). Fehlt ein Paket, gilt SessionFilter.DEFAULT_HIDDEN. */
    val appVisibility: Map<String, Boolean> = emptyMap()
) {
    companion object {
        val DEFAULT = WidgetConfig()
    }
}
