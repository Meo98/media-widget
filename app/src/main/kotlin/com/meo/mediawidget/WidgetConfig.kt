package com.meo.mediawidget

enum class Style { MATERIAL_YOU, GLASS, AMOLED }

enum class AppActionsMode { AUTO, POLISHED_ONLY, OFF }

data class WidgetConfig(
    val style: Style = Style.MATERIAL_YOU,
    val appActionsMode: AppActionsMode = AppActionsMode.AUTO,
    val appActionsAllowRaw: Boolean = true,
    val showProgressBar: Boolean = true,
    val openAppOnCoverTap: Boolean = true,
    val preferredApp: String? = null,
    /** User-selected source-app package (per widget). Null = auto (most-active session). */
    val selectedAppPackage: String? = null
) {
    companion object {
        val DEFAULT = WidgetConfig()
    }
}
