package com.meo.mediawidget

enum class Style { MATERIAL_YOU, GLASS, AMOLED }

enum class AppActionsMode { AUTO, POLISHED_ONLY, OFF }

data class WidgetConfig(
    val style: Style = Style.MATERIAL_YOU,
    val appActionsMode: AppActionsMode = AppActionsMode.AUTO,
    val appActionsAllowRaw: Boolean = true,
    val showProgressBar: Boolean = true,
    val openAppOnCoverTap: Boolean = true,
    val preferredApp: String? = null
) {
    companion object {
        val DEFAULT = WidgetConfig()
    }
}
