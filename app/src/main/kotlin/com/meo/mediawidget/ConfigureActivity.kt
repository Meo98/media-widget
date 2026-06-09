package com.meo.mediawidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigureActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)  // default if user backs out

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        setContentView(R.layout.activity_configure)
        setupForm()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun setupForm() {
        val spinnerStyle = findViewById<Spinner>(R.id.spinner_style)
        val spinnerMode = findViewById<Spinner>(R.id.spinner_actions_mode)

        spinnerStyle.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Style.values().map { it.name }
        )
        spinnerMode.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            AppActionsMode.values().map { it.name }
        )

        // Populate from existing config
        val repo = SettingsRepo(this)
        scope.launch {
            val config = withContext(Dispatchers.IO) { repo.resolve(widgetId) }
            spinnerStyle.setSelection(Style.values().indexOf(config.style))
            spinnerMode.setSelection(AppActionsMode.values().indexOf(config.appActionsMode))
            findViewById<CheckBox>(R.id.check_allow_raw).isChecked = config.appActionsAllowRaw
            findViewById<CheckBox>(R.id.check_progress).isChecked = config.showProgressBar
            findViewById<CheckBox>(R.id.check_cover_tap).isChecked = config.openAppOnCoverTap
            // All "inherit" checkboxes default OFF — user can re-enable to inherit globals
        }

        findViewById<Button>(R.id.btn_use_global).setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) { repo.clearWidget(widgetId) }
                finishWithResult()
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val style = if (findViewById<CheckBox>(R.id.check_inherit_style).isChecked) null
                else Style.values()[spinnerStyle.selectedItemPosition]
            val mode = if (findViewById<CheckBox>(R.id.check_inherit_actions_mode).isChecked) null
                else AppActionsMode.values()[spinnerMode.selectedItemPosition]
            val raw = if (findViewById<CheckBox>(R.id.check_inherit_allow_raw).isChecked) null
                else findViewById<CheckBox>(R.id.check_allow_raw).isChecked
            val progress = if (findViewById<CheckBox>(R.id.check_inherit_progress).isChecked) null
                else findViewById<CheckBox>(R.id.check_progress).isChecked
            val cover = if (findViewById<CheckBox>(R.id.check_inherit_cover_tap).isChecked) null
                else findViewById<CheckBox>(R.id.check_cover_tap).isChecked

            scope.launch {
                withContext(Dispatchers.IO) {
                    repo.writeWidgetStyle(widgetId, style)
                    repo.writeWidgetActionsMode(widgetId, mode)
                    repo.writeWidgetActionsRaw(widgetId, raw)
                    repo.writeWidgetProgress(widgetId, progress)
                    repo.writeWidgetCoverTap(widgetId, cover)
                }
                finishWithResult()
            }
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
    }

    private fun finishWithResult() {
        MediaWidgetProvider.requestUpdate(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
