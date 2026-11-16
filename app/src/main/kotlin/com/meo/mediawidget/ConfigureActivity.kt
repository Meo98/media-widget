package com.meo.mediawidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.meo.mediawidget.ui.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Einstellungen eines einzelnen Widgets. Jede Zeile kann dem Standard folgen
 * oder einen eigenen Wert haben. Erscheint beim Platzieren und über die Hauptseite.
 */
class ConfigureActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var list: LinearLayout
    private lateinit var repo: SettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bricht der User beim Platzieren ab, verwirft der Launcher das Widget.
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        repo = SettingsRepo(this)
        ui = Ui(this)
        list = ui.page(this)
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun save(write: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { write() }
            MediaWidgetProvider.requestUpdate(this@ConfigureActivity)
            render()
        }
    }

    private fun render() {
        scope.launch {
            val (own, global) = withContext(Dispatchers.IO) {
                repo.readWidgetOverrides(widgetId) to repo.readGlobalConfig()
            }
            list.removeAllViews()
            list.addView(ui.title(getString(R.string.configure_title)))
            list.addView(ui.section(getString(R.string.widget_label_fmt, widgetId)))
            list.addView(ui.card {
                choiceRow(
                    this, R.string.setting_style, null,
                    options = Style.entries.map { it.label }, own = own.style?.ordinal,
                    globalLabel = global.style.label
                ) { repo.writeWidgetStyle(widgetId, it?.let { i -> Style.entries[i] }) }

                choiceRow(
                    this, R.string.setting_actions_mode, null,
                    options = AppActionsMode.entries.map { getString(it.labelRes) },
                    own = own.appActionsMode?.ordinal,
                    globalLabel = getString(global.appActionsMode.labelRes)
                ) { repo.writeWidgetActionsMode(widgetId, it?.let { i -> AppActionsMode.entries[i] }) }

                boolRow(this, R.string.setting_allow_raw, R.string.setting_allow_raw_help,
                    own.appActionsAllowRaw, global.appActionsAllowRaw) { repo.writeWidgetActionsRaw(widgetId, it) }
                boolRow(this, R.string.setting_progress, R.string.setting_progress_help,
                    own.showProgressBar, global.showProgressBar) { repo.writeWidgetProgress(widgetId, it) }
                boolRow(this, R.string.setting_cover_tap, R.string.setting_cover_tap_help,
                    own.openAppOnCoverTap, global.openAppOnCoverTap) { repo.writeWidgetCoverTap(widgetId, it) }
            })
            list.addView(ui.footnote(getString(R.string.configure_subtitle)))

            list.addView(ui.buttons(
                ui.pill(getString(R.string.done), primary = true) { finishWithResult() },
                ui.pill(getString(R.string.reset_widget), primary = false) {
                    save { repo.clearWidgetOverrides(widgetId) }
                }
            ))
        }
    }

    private fun onOff(v: Boolean) = getString(if (v) R.string.choice_on else R.string.choice_off)

    private fun boolRow(
        card: Ui.CardBuilder, title: Int, help: Int,
        own: Boolean?, global: Boolean, write: suspend (Boolean?) -> Unit
    ) = choiceRow(
        card, title, help,
        options = listOf(onOff(true), onOff(false)),
        own = own?.let { if (it) 0 else 1 }, globalLabel = onOff(global)
    ) { picked -> write(picked?.let { it == 0 }) }

    /**
     * Zeile mit Auswahl-Dialog. Erster Eintrag ist immer "Standard verwenden";
     * [write] bekommt null dafür, sonst den Index in [options].
     */
    private fun choiceRow(
        card: Ui.CardBuilder, title: Int, help: Int?,
        options: List<String>, own: Int?, globalLabel: String, write: suspend (Int?) -> Unit
    ) {
        val defaultLabel = getString(R.string.choice_default, globalLabel)
        card.row(
            title = getString(title),
            subtitle = help?.let { getString(it) },
            end = ui.value(own?.let { options[it] } ?: globalLabel),
            chevron = true
        ) {
            val current = own?.plus(1) ?: 0
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems((listOf(defaultLabel) + options).toTypedArray(), current) { dialog, which ->
                    dialog.dismiss()
                    if (which != current) save { write(if (which == 0) null else which - 1) }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun finishWithResult() {
        MediaWidgetProvider.requestUpdate(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
