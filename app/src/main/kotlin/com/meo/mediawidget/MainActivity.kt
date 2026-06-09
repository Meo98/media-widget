package com.meo.mediawidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val explain = TextView(this).apply {
            text = getString(R.string.permission_explain)
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }

        val openBtn = Button(this).apply {
            text = getString(R.string.open_settings)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        root.addView(title)
        root.addView(explain)
        root.addView(openBtn)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Wenn der User aus den Settings zurückkommt: Widget neu zeichnen.
        MediaWidgetProvider.requestUpdate(this)
    }
}
