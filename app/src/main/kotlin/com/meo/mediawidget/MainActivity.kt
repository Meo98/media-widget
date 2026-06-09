package com.meo.mediawidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: SettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = SettingsRepo(this)

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupGlobalDefaults()
    }

    override fun onResume() {
        super.onResume()
        renderPermissionStatus()
        renderGlobalDefaults()
        renderPlacedWidgets()
        MediaWidgetProvider.requestUpdate(this)
    }

    private fun renderPermissionStatus() {
        val on = MediaState.listenerEnabled(this)
        findViewById<TextView>(R.id.permission_status).text =
            getString(if (on) R.string.permission_active else R.string.permission_inactive)
    }

    private fun setupGlobalDefaults() {
        findViewById<Spinner>(R.id.spinner_global_style).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Style.values().map { it.name }
        )
        findViewById<Spinner>(R.id.spinner_global_actions_mode).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, AppActionsMode.values().map { it.name }
        )
        findViewById<Button>(R.id.btn_save_globals).setOnClickListener { saveGlobalDefaults() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun renderGlobalDefaults() {
        scope.launch {
            val config = withContext(Dispatchers.IO) { repo.readGlobalConfig() }
            findViewById<Spinner>(R.id.spinner_global_style)
                .setSelection(Style.values().indexOf(config.style))
            findViewById<Spinner>(R.id.spinner_global_actions_mode)
                .setSelection(AppActionsMode.values().indexOf(config.appActionsMode))
            findViewById<CheckBox>(R.id.check_global_allow_raw).isChecked = config.appActionsAllowRaw
            findViewById<CheckBox>(R.id.check_global_progress).isChecked = config.showProgressBar
            findViewById<CheckBox>(R.id.check_global_cover_tap).isChecked = config.openAppOnCoverTap
        }
    }

    private fun saveGlobalDefaults() {
        val style = Style.values()[findViewById<Spinner>(R.id.spinner_global_style).selectedItemPosition]
        val mode = AppActionsMode.values()[findViewById<Spinner>(R.id.spinner_global_actions_mode).selectedItemPosition]
        val raw = findViewById<CheckBox>(R.id.check_global_allow_raw).isChecked
        val progress = findViewById<CheckBox>(R.id.check_global_progress).isChecked
        val cover = findViewById<CheckBox>(R.id.check_global_cover_tap).isChecked
        scope.launch {
            withContext(Dispatchers.IO) {
                repo.writeGlobalStyle(style)
                repo.writeGlobalActionsMode(mode)
                repo.writeGlobalActionsRaw(raw)
                repo.writeGlobalProgress(progress)
                repo.writeGlobalCoverTap(cover)
            }
            MediaWidgetProvider.requestUpdate(this@MainActivity)
        }
    }

    private fun renderPlacedWidgets() {
        val list = findViewById<LinearLayout>(R.id.placed_widgets_list)
        val empty = findViewById<TextView>(R.id.no_widgets_text)
        list.removeAllViews()
        val ids = WidgetIds.all(this)
        empty.visibility = if (ids.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        ids.forEach { id ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_placed_widget, list, false)
            row.findViewById<TextView>(R.id.widget_label).text = "Widget #$id"
            row.findViewById<Button>(R.id.btn_reconfigure).setOnClickListener {
                val intent = Intent(this, ConfigureActivity::class.java)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                startActivity(intent)
            }
            row.findViewById<Button>(R.id.btn_reset).setOnClickListener {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.clearWidget(id) }
                    MediaWidgetProvider.requestUpdate(this@MainActivity)
                }
            }
            list.addView(row)
        }
    }
}
