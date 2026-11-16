package com.meo.mediawidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import com.meo.mediawidget.ui.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hauptseite im Stil der Systemeinstellungen: Zugriff, Standardwerte, Apps,
 * platzierte Widgets. Jede Änderung wirkt sofort, einen Speichern-Knopf gibt es nicht.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var list: LinearLayout
    private lateinit var repo: SettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepo(this)
        ui = Ui(this)
        list = ui.page(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        MediaWidgetProvider.requestUpdate(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /** Schreibt eine Einstellung, zeichnet die Widgets neu und baut die Seite frisch auf. */
    private fun save(write: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { write() }
            MediaWidgetProvider.requestUpdate(this@MainActivity)
            render()
        }
    }

    private fun render() {
        scope.launch {
            val config = withContext(Dispatchers.IO) { repo.readGlobalConfig() }
            val overridden = withContext(Dispatchers.IO) {
                WidgetIds.all(this@MainActivity).associateWith { repo.readWidgetOverrides(it) != WidgetOverrides() }
            }
            val custom = withContext(Dispatchers.IO) { repo.readCustomThemes() }
            list.removeAllViews()
            list.addView(ui.title(getString(R.string.app_name)))
            addAccess()
            addDefaults(config, Themes.builtIn + custom)
            addCustomStyles(custom)
            addApps(config)
            addWidgets(overridden)
        }
    }

    private fun addAccess() {
        val on = MediaState.listenerEnabled(this)
        list.addView(ui.section(getString(R.string.access_title)))
        list.addView(ui.card {
            row(
                title = getString(R.string.access_row),
                subtitle = getString(if (on) R.string.access_granted else R.string.access_missing),
                end = ui.dot(if (on) Color.rgb(52, 199, 89) else Color.rgb(255, 69, 58)),
                chevron = true
            ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        if (!on) list.addView(ui.footnote(getString(R.string.permission_explain)))
    }

    private fun addDefaults(config: WidgetConfig, themes: List<Theme>) {
        list.addView(ui.section(getString(R.string.global_defaults)))
        list.addView(ui.card {
            row(getString(R.string.setting_style), end = ui.value(config.theme.name), chevron = true) {
                choose(R.string.setting_style, themes.map { it.name }, themes.indexOfFirst { it.id == config.theme.id }) {
                    save { repo.writeGlobalStyle(themes[it].id) }
                }
            }
            row(
                getString(R.string.setting_actions_mode),
                end = ui.value(getString(config.appActionsMode.labelRes)), chevron = true
            ) {
                choose(
                    R.string.setting_actions_mode, AppActionsMode.entries.map { getString(it.labelRes) },
                    config.appActionsMode.ordinal
                ) { save { repo.writeGlobalActionsMode(AppActionsMode.entries[it]) } }
            }
            toggle(this, R.string.setting_allow_raw, R.string.setting_allow_raw_help, config.appActionsAllowRaw) {
                repo.writeGlobalActionsRaw(it)
            }
            toggle(this, R.string.setting_progress, R.string.setting_progress_help, config.showProgressBar) {
                repo.writeGlobalProgress(it)
            }
            toggle(this, R.string.setting_cover_tap, R.string.setting_cover_tap_help, config.openAppOnCoverTap) {
                repo.writeGlobalCoverTap(it)
            }
            toggle(this, R.string.setting_remember, R.string.setting_remember_help, config.rememberLast) {
                repo.writeGlobalRemember(it)
            }
        })
        list.addView(ui.footnote(getString(R.string.global_defaults_subtitle)))
    }

    /** Eigene Stile des Users und der Einstieg in den Editor. */
    private fun addCustomStyles(custom: List<Theme>) {
        list.addView(ui.section(getString(R.string.custom_styles)))
        list.addView(ui.card {
            for (theme in custom) {
                row(theme.name, chevron = true) { openEditor(theme.id) }
            }
            row(getString(R.string.new_style), getString(R.string.new_style_help), chevron = true) { openEditor(null) }
        })
    }

    private fun openEditor(themeId: String?) {
        startActivity(
            Intent(this, ThemeEditorActivity::class.java).putExtra(ThemeEditorActivity.EXTRA_THEME_ID, themeId)
        )
    }

    /** Zeile mit Schalter; ein Tipp auf die ganze Zeile schaltet um. */
    private fun toggle(
        card: Ui.CardBuilder, title: Int, help: Int, on: Boolean, write: suspend (Boolean) -> Unit
    ) {
        card.row(getString(title), getString(help), end = ui.switch(on)) { save { write(!on) } }
    }

    /** Alle Apps, die je eine Media-Session hatten, mit Schalter "im Widget zeigen". */
    private fun addApps(config: WidgetConfig) {
        val memory = MediaMemory.all(this).associateBy { it.pkg }
        val pm = packageManager
        val apps = (MediaMemory.seenPackages(this) + memory.keys + config.appVisibility.keys)
            .filter { it != packageName }
            .mapNotNull { pkg -> try { pkg to pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null } }
            .map { (pkg, info) -> Triple(pkg, info, pm.getApplicationLabel(info).toString()) }
            .sortedBy { it.third.lowercase() }

        list.addView(ui.section(getString(R.string.apps_title)))
        if (apps.isEmpty()) {
            list.addView(ui.footnote(getString(R.string.apps_empty)))
            return
        }
        list.addView(ui.card {
            for ((pkg, info, label) in apps) {
                val visible = SessionFilter.isVisible(pkg, config.appVisibility)
                row(
                    title = label,
                    subtitle = memory[pkg]?.title?.takeIf { it.isNotBlank() },
                    iconDrawable = pm.getApplicationIcon(info),
                    end = ui.switch(visible)
                ) { save { repo.writeAppVisible(pkg, !visible) } }
            }
        })
        list.addView(ui.footnote(getString(R.string.apps_subtitle)))
    }

    private fun addWidgets(overridden: Map<Int, Boolean>) {
        list.addView(ui.section(getString(R.string.placed_widgets)))
        if (overridden.isEmpty()) {
            list.addView(ui.footnote(getString(R.string.no_widgets_placed)))
            return
        }
        list.addView(ui.card {
            for ((id, custom) in overridden) {
                row(
                    title = getString(R.string.widget_label_fmt, id),
                    subtitle = getString(
                        if (custom) R.string.widget_customised else R.string.widget_follows_defaults
                    ),
                    icon = R.drawable.ic_music, chevron = true
                ) {
                    startActivity(
                        Intent(this@MainActivity, ConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    )
                }
            }
        })
    }

    /** Auswahl-Dialog des Systems; die Wahl gilt sofort beim Antippen. */
    private fun choose(title: Int, labels: List<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                if (which != current) onPick(which)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
