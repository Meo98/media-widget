package com.meo.mediawidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetIds {
    fun all(ctx: Context): IntArray {
        val manager = AppWidgetManager.getInstance(ctx)
        val component = ComponentName(ctx, MediaWidgetProvider::class.java)
        return manager.getAppWidgetIds(component)
    }
}
