package com.meo.mediawidget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("media_widget_prefs")

class SettingsRepo(private val ctx: Context) {

    private object Keys {
        val GLOBAL_STYLE = stringPreferencesKey("global.style")
        val GLOBAL_ACTIONS_MODE = stringPreferencesKey("global.appActionsMode")
        val GLOBAL_ACTIONS_RAW = booleanPreferencesKey("global.appActionsAllowRaw")
        val GLOBAL_PROGRESS = booleanPreferencesKey("global.showProgressBar")
        val GLOBAL_COVER_TAP = booleanPreferencesKey("global.openAppOnCoverTap")
        val GLOBAL_PREFERRED_APP = stringPreferencesKey("global.preferredApp")

        fun widgetStyle(id: Int) = stringPreferencesKey("w.$id.style")
        fun widgetActionsMode(id: Int) = stringPreferencesKey("w.$id.appActionsMode")
        fun widgetActionsRaw(id: Int) = booleanPreferencesKey("w.$id.appActionsAllowRaw")
        fun widgetActionsRawSet(id: Int) = booleanPreferencesKey("w.$id.appActionsAllowRaw.set")
        fun widgetProgress(id: Int) = booleanPreferencesKey("w.$id.showProgressBar")
        fun widgetProgressSet(id: Int) = booleanPreferencesKey("w.$id.showProgressBar.set")
        fun widgetCoverTap(id: Int) = booleanPreferencesKey("w.$id.openAppOnCoverTap")
        fun widgetCoverTapSet(id: Int) = booleanPreferencesKey("w.$id.openAppOnCoverTap.set")

        fun widgetAllPrefix(id: Int) = "w.$id."
    }

    /** Returns global defaults without per-widget fallback (no widget id needed). */
    suspend fun readGlobalConfig(): WidgetConfig {
        val prefs = ctx.dataStore.data.first()
        return WidgetConfig(
            style = Style.valueOf(prefs[Keys.GLOBAL_STYLE] ?: Style.MATERIAL_YOU.name),
            appActionsMode = AppActionsMode.valueOf(prefs[Keys.GLOBAL_ACTIONS_MODE] ?: AppActionsMode.AUTO.name),
            appActionsAllowRaw = prefs[Keys.GLOBAL_ACTIONS_RAW] ?: true,
            showProgressBar = prefs[Keys.GLOBAL_PROGRESS] ?: true,
            openAppOnCoverTap = prefs[Keys.GLOBAL_COVER_TAP] ?: true,
            preferredApp = prefs[Keys.GLOBAL_PREFERRED_APP]
        )
    }

    suspend fun resolve(widgetId: Int): WidgetConfig {
        val prefs = ctx.dataStore.data.first()
        return WidgetConfig(
            style = Style.valueOf(SettingsResolver.resolveString(
                perWidget = prefs[Keys.widgetStyle(widgetId)],
                global = prefs[Keys.GLOBAL_STYLE],
                fallback = Style.MATERIAL_YOU.name
            )),
            appActionsMode = AppActionsMode.valueOf(SettingsResolver.resolveString(
                perWidget = prefs[Keys.widgetActionsMode(widgetId)],
                global = prefs[Keys.GLOBAL_ACTIONS_MODE],
                fallback = AppActionsMode.AUTO.name
            )),
            appActionsAllowRaw = SettingsResolver.resolveBool(
                perWidgetOverridden = prefs[Keys.widgetActionsRawSet(widgetId)] ?: false,
                perWidgetValue = prefs[Keys.widgetActionsRaw(widgetId)] ?: true,
                global = prefs[Keys.GLOBAL_ACTIONS_RAW],
                fallback = true
            ),
            showProgressBar = SettingsResolver.resolveBool(
                perWidgetOverridden = prefs[Keys.widgetProgressSet(widgetId)] ?: false,
                perWidgetValue = prefs[Keys.widgetProgress(widgetId)] ?: true,
                global = prefs[Keys.GLOBAL_PROGRESS],
                fallback = true
            ),
            openAppOnCoverTap = SettingsResolver.resolveBool(
                perWidgetOverridden = prefs[Keys.widgetCoverTapSet(widgetId)] ?: false,
                perWidgetValue = prefs[Keys.widgetCoverTap(widgetId)] ?: true,
                global = prefs[Keys.GLOBAL_COVER_TAP],
                fallback = true
            ),
            preferredApp = prefs[Keys.GLOBAL_PREFERRED_APP]
        )
    }

    suspend fun writeGlobalStyle(s: Style) = ctx.dataStore.edit { it[Keys.GLOBAL_STYLE] = s.name }
    suspend fun writeGlobalActionsMode(m: AppActionsMode) = ctx.dataStore.edit { it[Keys.GLOBAL_ACTIONS_MODE] = m.name }
    suspend fun writeGlobalActionsRaw(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_ACTIONS_RAW] = v }
    suspend fun writeGlobalProgress(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_PROGRESS] = v }
    suspend fun writeGlobalCoverTap(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_COVER_TAP] = v }
    suspend fun writeGlobalPreferredApp(pkg: String?) = ctx.dataStore.edit {
        if (pkg == null) it.remove(Keys.GLOBAL_PREFERRED_APP) else it[Keys.GLOBAL_PREFERRED_APP] = pkg
    }

    suspend fun writeWidgetStyle(id: Int, s: Style?) = ctx.dataStore.edit {
        if (s == null) it.remove(Keys.widgetStyle(id)) else it[Keys.widgetStyle(id)] = s.name
    }
    suspend fun writeWidgetActionsMode(id: Int, m: AppActionsMode?) = ctx.dataStore.edit {
        if (m == null) it.remove(Keys.widgetActionsMode(id)) else it[Keys.widgetActionsMode(id)] = m.name
    }
    suspend fun writeWidgetActionsRaw(id: Int, v: Boolean?) = ctx.dataStore.edit {
        if (v == null) {
            it.remove(Keys.widgetActionsRaw(id)); it.remove(Keys.widgetActionsRawSet(id))
        } else {
            it[Keys.widgetActionsRaw(id)] = v; it[Keys.widgetActionsRawSet(id)] = true
        }
    }
    suspend fun writeWidgetProgress(id: Int, v: Boolean?) = ctx.dataStore.edit {
        if (v == null) {
            it.remove(Keys.widgetProgress(id)); it.remove(Keys.widgetProgressSet(id))
        } else {
            it[Keys.widgetProgress(id)] = v; it[Keys.widgetProgressSet(id)] = true
        }
    }
    suspend fun writeWidgetCoverTap(id: Int, v: Boolean?) = ctx.dataStore.edit {
        if (v == null) {
            it.remove(Keys.widgetCoverTap(id)); it.remove(Keys.widgetCoverTapSet(id))
        } else {
            it[Keys.widgetCoverTap(id)] = v; it[Keys.widgetCoverTapSet(id)] = true
        }
    }

    suspend fun clearWidget(id: Int) = ctx.dataStore.edit { prefs ->
        val prefix = Keys.widgetAllPrefix(id)
        val toRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
        toRemove.forEach { prefs.remove(it) }
    }
}
