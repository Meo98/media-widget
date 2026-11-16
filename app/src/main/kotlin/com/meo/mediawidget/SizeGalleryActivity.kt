package com.meo.mediawidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.meo.mediawidget.ui.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zeigt das Widget in vielen Grössen untereinander: echtes Layout, aktueller
 * Stil, laufender Titel. Zum Vergleichen und um zu prüfen, dass jede Grösse
 * stimmt, ohne ein Widget auf dem Homescreen hin und her zu ziehen.
 */
class SizeGalleryActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val list = ui.page(this)
        list.addView(ui.title(getString(R.string.size_gallery)))

        scope.launch {
            val config = withContext(Dispatchers.IO) { SettingsRepo(this@SizeGalleryActivity).readGlobalConfig() }
            val provider = MediaWidgetProvider()
            for (h in HEIGHTS) for (w in WIDTHS) {
                val bucket = Bucket.pickForSize(android.util.SizeF(w.toFloat(), h.toFloat()))
                list.addView(ui.section("$w × $h dp · ${bucket.name}"))
                val widget = FrameLayout(this@SizeGalleryActivity)
                try {
                    val rv = provider.render(
                        this@SizeGalleryActivity, config, w.toFloat(), h.toFloat(),
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    )
                    widget.addView(rv.apply(applicationContext, widget))
                } catch (t: Throwable) {
                    widget.addView(ui.value(t.toString()))
                }
                // Farbiger Verlauf als Ersatz fürs Hintergrundbild
                list.addView(FrameLayout(this@SizeGalleryActivity).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF7E57C2.toInt(), 0xFFFF7043.toInt(), 0xFFFFCA28.toInt())
                    ).apply { cornerRadius = ui.dp(20).toFloat() }
                    setPadding(0, ui.dp(12), 0, ui.dp(12))
                    addView(widget, FrameLayout.LayoutParams(ui.dp(w), ui.dp(h), Gravity.CENTER_HORIZONTAL))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        leftMargin = ui.dp(8); rightMargin = ui.dp(8)
                    }
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        /** Breiten und Höhen, wie sie ein OnePlus-Launcher für 2–4 Spalten und 1–4 Zeilen meldet. */
        val WIDTHS = listOf(172, 249, 326)
        val HEIGHTS = listOf(76, 179, 282, 385)
    }
}
