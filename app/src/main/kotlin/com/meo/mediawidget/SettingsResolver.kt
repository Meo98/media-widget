package com.meo.mediawidget

object SettingsResolver {

    fun resolveString(perWidget: String?, global: String?, fallback: String): String {
        if (perWidget != null && perWidget != "INHERIT") return perWidget
        return global ?: fallback
    }

    fun resolveBool(
        perWidgetOverridden: Boolean,
        perWidgetValue: Boolean,
        global: Boolean?,
        fallback: Boolean
    ): Boolean {
        if (perWidgetOverridden) return perWidgetValue
        return global ?: fallback
    }
}
