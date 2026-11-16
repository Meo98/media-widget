package com.meo.mediawidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetIds {
    /** Alle Provider-Klassen; jede ist ein Eintrag in der Widget-Auswahl. */
    private val PROVIDERS = listOf(
        MediaWidgetProvider::class.java,
        MediaWidgetCardProvider::class.java,
        MediaWidgetLargeProvider::class.java,
    )

    fun all(ctx: Context): IntArray {
        val manager = AppWidgetManager.getInstance(ctx)
        return PROVIDERS.flatMap { manager.getAppWidgetIds(ComponentName(ctx, it)).toList() }.toIntArray()
    }
}
