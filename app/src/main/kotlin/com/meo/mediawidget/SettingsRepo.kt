package com.meo.mediawidget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("media_widget_prefs")

class SettingsRepo(private val ctx: Context) {

    private object Keys {
        val GLOBAL_STYLE = stringPreferencesKey("global.style")
        val CUSTOM_THEMES = stringPreferencesKey("themes.custom")
        val GLOBAL_ACTIONS_MODE = stringPreferencesKey("global.appActionsMode")
        val GLOBAL_ACTIONS_RAW = booleanPreferencesKey("global.appActionsAllowRaw")
        val GLOBAL_PROGRESS = booleanPreferencesKey("global.showProgressBar")
        val GLOBAL_COVER_TAP = booleanPreferencesKey("global.openAppOnCoverTap")
        val GLOBAL_PREFERRED_APP = stringPreferencesKey("global.preferredApp")
        val GLOBAL_REMEMBER = booleanPreferencesKey("global.rememberLast")

        const val APP_VISIBLE_PREFIX = "app.visible."
        fun appVisible(pkg: String) = booleanPreferencesKey(APP_VISIBLE_PREFIX + pkg)
        fun widgetSelectedAt(id: Int) = longPreferencesKey("w.$id.selectedAt")

        fun widgetStyle(id: Int) = stringPreferencesKey("w.$id.style")
        fun widgetActionsMode(id: Int) = stringPreferencesKey("w.$id.appActionsMode")
        fun widgetActionsRaw(id: Int) = booleanPreferencesKey("w.$id.appActionsAllowRaw")
        fun widgetActionsRawSet(id: Int) = booleanPreferencesKey("w.$id.appActionsAllowRaw.set")
        fun widgetProgress(id: Int) = booleanPreferencesKey("w.$id.showProgressBar")
        fun widgetProgressSet(id: Int) = booleanPreferencesKey("w.$id.showProgressBar.set")
        fun widgetCoverTap(id: Int) = booleanPreferencesKey("w.$id.openAppOnCoverTap")
        fun widgetCoverTapSet(id: Int) = booleanPreferencesKey("w.$id.openAppOnCoverTap.set")
        fun widgetSelectedApp(id: Int) = stringPreferencesKey("w.$id.selectedAppPackage")

        fun widgetAllPrefix(id: Int) = "w.$id."
    }

    /** Returns global defaults without per-widget fallback (no widget id needed). */
    suspend fun readGlobalConfig(): WidgetConfig {
        val prefs = ctx.dataStore.data.first()
        return WidgetConfig(
            theme = Themes.find(prefs[Keys.GLOBAL_STYLE], Themes.parseCustom(prefs[Keys.CUSTOM_THEMES])),
            appActionsMode = AppActionsMode.valueOf(prefs[Keys.GLOBAL_ACTIONS_MODE] ?: AppActionsMode.AUTO.name),
            appActionsAllowRaw = prefs[Keys.GLOBAL_ACTIONS_RAW] ?: true,
            showProgressBar = prefs[Keys.GLOBAL_PROGRESS] ?: true,
            openAppOnCoverTap = prefs[Keys.GLOBAL_COVER_TAP] ?: true,
            preferredApp = prefs[Keys.GLOBAL_PREFERRED_APP],
            rememberLast = prefs[Keys.GLOBAL_REMEMBER] ?: true,
            appVisibility = appVisibility(prefs)
        )
    }

    private fun appVisibility(prefs: Preferences): Map<String, Boolean> =
        prefs.asMap().entries
            .filter { it.key.name.startsWith(Keys.APP_VISIBLE_PREFIX) && it.value is Boolean }
            .associate { it.key.name.removePrefix(Keys.APP_VISIBLE_PREFIX) to (it.value as Boolean) }

    /** Nur die eigenen Abweichungen eines Widgets, ohne Rückfall auf den Standard. */
    suspend fun readWidgetOverrides(id: Int): WidgetOverrides {
        val prefs = ctx.dataStore.data.first()
        fun bool(set: Preferences.Key<Boolean>, value: Preferences.Key<Boolean>) =
            if (prefs[set] == true) prefs[value] else null
        return WidgetOverrides(
            themeId = prefs[Keys.widgetStyle(id)]?.takeIf { it != "INHERIT" },
            appActionsMode = prefs[Keys.widgetActionsMode(id)]?.takeIf { it != "INHERIT" }
                ?.let { AppActionsMode.valueOf(it) },
            appActionsAllowRaw = bool(Keys.widgetActionsRawSet(id), Keys.widgetActionsRaw(id)),
            showProgressBar = bool(Keys.widgetProgressSet(id), Keys.widgetProgress(id)),
            openAppOnCoverTap = bool(Keys.widgetCoverTapSet(id), Keys.widgetCoverTap(id))
        )
    }

    /** Setzt Stil und Verhalten eines Widgets auf den Standard zurück; die gewählte App bleibt. */
    suspend fun clearWidgetOverrides(id: Int) = ctx.dataStore.edit {
        it.remove(Keys.widgetStyle(id)); it.remove(Keys.widgetActionsMode(id))
        it.remove(Keys.widgetActionsRaw(id)); it.remove(Keys.widgetActionsRawSet(id))
        it.remove(Keys.widgetProgress(id)); it.remove(Keys.widgetProgressSet(id))
        it.remove(Keys.widgetCoverTap(id)); it.remove(Keys.widgetCoverTapSet(id))
    }

    suspend fun resolve(widgetId: Int): WidgetConfig {
        val prefs = ctx.dataStore.data.first()
        return WidgetConfig(
            theme = Themes.find(
                SettingsResolver.resolveString(
                    perWidget = prefs[Keys.widgetStyle(widgetId)],
                    global = prefs[Keys.GLOBAL_STYLE],
                    fallback = Themes.DEFAULT_ID
                ),
                Themes.parseCustom(prefs[Keys.CUSTOM_THEMES])
            ),
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
            preferredApp = prefs[Keys.GLOBAL_PREFERRED_APP],
            selectedAppPackage = prefs[Keys.widgetSelectedApp(widgetId)],
            selectedAt = prefs[Keys.widgetSelectedAt(widgetId)] ?: 0L,
            rememberLast = prefs[Keys.GLOBAL_REMEMBER] ?: true,
            appVisibility = appVisibility(prefs)
        )
    }

    suspend fun writeWidgetSelectedApp(id: Int, pkg: String?, at: Long = System.currentTimeMillis()) = ctx.dataStore.edit {
        if (pkg == null) {
            it.remove(Keys.widgetSelectedApp(id)); it.remove(Keys.widgetSelectedAt(id))
        } else {
            it[Keys.widgetSelectedApp(id)] = pkg; it[Keys.widgetSelectedAt(id)] = at
        }
    }

    suspend fun writeGlobalRemember(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_REMEMBER] = v }
    suspend fun writeAppVisible(pkg: String, visible: Boolean) = ctx.dataStore.edit { it[Keys.appVisible(pkg)] = visible }

    suspend fun writeGlobalStyle(themeId: String) = ctx.dataStore.edit { it[Keys.GLOBAL_STYLE] = themeId }

    suspend fun readCustomThemes(): List<Theme> =
        Themes.parseCustom(ctx.dataStore.data.first()[Keys.CUSTOM_THEMES])

    /** Legt einen eigenen Stil an oder ersetzt den mit derselben Kennung. */
    suspend fun saveCustomTheme(theme: Theme) = ctx.dataStore.edit {
        val old = Themes.parseCustom(it[Keys.CUSTOM_THEMES])
        val new = if (old.any { t -> t.id == theme.id }) old.map { t -> if (t.id == theme.id) theme else t }
            else old + theme
        it[Keys.CUSTOM_THEMES] = Themes.encodeCustom(new)
    }

    /** Widgets, die den Stil nutzten, fallen über [Themes.find] auf die Standardvorlage zurück. */
    suspend fun deleteCustomTheme(id: String) = ctx.dataStore.edit {
        it[Keys.CUSTOM_THEMES] = Themes.encodeCustom(Themes.parseCustom(it[Keys.CUSTOM_THEMES]).filter { t -> t.id != id })
    }
    suspend fun writeGlobalActionsMode(m: AppActionsMode) = ctx.dataStore.edit { it[Keys.GLOBAL_ACTIONS_MODE] = m.name }
    suspend fun writeGlobalActionsRaw(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_ACTIONS_RAW] = v }
    suspend fun writeGlobalProgress(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_PROGRESS] = v }
    suspend fun writeGlobalCoverTap(v: Boolean) = ctx.dataStore.edit { it[Keys.GLOBAL_COVER_TAP] = v }
    suspend fun writeGlobalPreferredApp(pkg: String?) = ctx.dataStore.edit {
        if (pkg == null) it.remove(Keys.GLOBAL_PREFERRED_APP) else it[Keys.GLOBAL_PREFERRED_APP] = pkg
    }

    suspend fun writeWidgetStyle(id: Int, themeId: String?) = ctx.dataStore.edit {
        if (themeId == null) it.remove(Keys.widgetStyle(id)) else it[Keys.widgetStyle(id)] = themeId
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
