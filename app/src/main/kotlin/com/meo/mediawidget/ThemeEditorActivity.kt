package com.meo.mediawidget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.TextView
import com.meo.mediawidget.ui.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Editor für einen eigenen Widget-Stil: Farben, Deckkraft, Eckenradius,
 * Unschärfe. Oben läuft eine echte Vorschau mit; gespeichert wird erst
 * über den Knopf.
 */
class ThemeEditorActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var list: LinearLayout
    private lateinit var repo: SettingsRepo
    private lateinit var preview: FrameLayout

    /** Arbeitskopie; landet erst mit "Speichern" im Speicher. */
    private lateinit var theme: Theme
    private var isNew = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepo(this)
        ui = Ui(this)
        list = ui.page(this)

        val id = intent?.getStringExtra(EXTRA_THEME_ID)
        scope.launch {
            val existing = withContext(Dispatchers.IO) { repo.readCustomThemes() }.firstOrNull { it.id == id }
            isNew = existing == null
            theme = existing ?: Themes.newCustom(
                this@ThemeEditorActivity,
                base = withContext(Dispatchers.IO) { repo.readGlobalConfig() }.theme,
                name = getString(R.string.my_style),
                id = Theme.CUSTOM_PREFIX + UUID.randomUUID().toString().take(8)
            )
            render()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private val colors get() = theme.light

    private fun setColors(c: ThemeColors) {
        theme = theme.copy(light = c, dark = c)
    }

    private fun render() {
        list.removeAllViews()
        list.addView(ui.title(getString(R.string.editor_title)))

        // Farbiger Verlauf als "Hintergrundbild", damit Deckkraft und Ecken sichtbar sind.
        preview = FrameLayout(this)
        list.addView(FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7E57C2.toInt(), 0xFFFF7043.toInt(), 0xFFFFCA28.toInt())
            ).apply { cornerRadius = ui.dp(20).toFloat() }
            setPadding(ui.dp(12), ui.dp(16), ui.dp(12), ui.dp(16))
            addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, ui.dp(76)))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                leftMargin = ui.dp(14); rightMargin = ui.dp(14); bottomMargin = ui.dp(16)
            }
        })
        refreshPreview()

        list.addView(ui.card {
            row(getString(R.string.style_name), end = ui.value(theme.name), chevron = true) { editName() }
            row(getString(R.string.style_base), getString(R.string.style_base_help), chevron = true) { pickBase() }
        })

        list.addView(ui.section(getString(R.string.colors_title)))
        list.addView(ui.card {
            colorRow(this, R.string.color_background, colors.background) {
                // Deckkraft bleibt, nur der Farbton wechselt
                setColors(colors.copy(background = withAlpha(it, Color.alpha(colors.background))))
            }
            colorRow(this, R.string.color_text, colors.text) { setColors(colors.copy(text = it)) }
            colorRow(this, R.string.color_subtext, colors.subtext) { setColors(colors.copy(subtext = it)) }
            colorRow(this, R.string.color_buttons, colors.buttons) { setColors(colors.copy(buttons = it)) }
            colorRow(this, R.string.color_play, colors.play) { setColors(colors.copy(play = it)) }
        })

        list.addView(ui.section(getString(R.string.shape_title)))
        val systemRadius = theme.radiusDp == Theme.SYSTEM_RADIUS
        list.addView(ui.card {
            content(slider(R.string.opacity, Color.alpha(colors.background) * 100 / 255, 100, "%") {
                setColors(colors.copy(background = withAlpha(colors.background, it * 255 / 100)))
            })
            row(getString(R.string.radius_system), end = ui.switch(systemRadius)) {
                theme = theme.copy(radiusDp = if (systemRadius) 24 else Theme.SYSTEM_RADIUS)
                render()
            }
            if (!systemRadius) content(slider(R.string.radius, theme.radiusDp, 40, " dp") {
                theme = theme.copy(radiusDp = it, coverRadiusDp = (it * 2 / 3).coerceAtMost(18))
            })
            row(getString(R.string.blur), getString(R.string.blur_help), end = ui.switch(theme.blurBackdrop)) {
                theme = theme.copy(blurBackdrop = !theme.blurBackdrop)
                render()
            }
        })

        val save = ui.pill(getString(R.string.save), primary = true) {
            scope.launch {
                withContext(Dispatchers.IO) { repo.saveCustomTheme(theme) }
                MediaWidgetProvider.requestUpdate(this@ThemeEditorActivity)
                finish()
            }
        }
        val delete = ui.pill(getString(R.string.delete_style), primary = false) {
            scope.launch {
                withContext(Dispatchers.IO) { repo.deleteCustomTheme(theme.id) }
                MediaWidgetProvider.requestUpdate(this@ThemeEditorActivity)
                finish()
            }
        }
        list.addView(if (isNew) ui.buttons(save) else ui.buttons(save, delete))
    }

    /** Zeichnet das echte Widget-Layout mit Beispieltext in die Vorschau. */
    private fun refreshPreview() {
        val rv = RemoteViews(packageName, R.layout.widget_bar)
        rv.setTextViewText(R.id.title, getString(R.string.preview_title))
        rv.setTextViewText(R.id.artist, getString(R.string.preview_artist))
        theme.applyTo(this, rv, art = null, allowBackdrop = false)
        preview.removeAllViews()
        try {
            preview.addView(rv.apply(applicationContext, preview))
        } catch (_: Throwable) { /* Vorschau ist Zugabe */ }
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun swatch(color: Int, sizeDp: Int) = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(withAlpha(color, 255))
            setStroke(ui.dp(1), ui.subtle)
        }
        layoutParams = LinearLayout.LayoutParams(ui.dp(sizeDp), ui.dp(sizeDp))
    }

    private fun colorRow(card: Ui.CardBuilder, title: Int, current: Int, onPick: (Int) -> Unit) {
        card.row(getString(title), end = swatch(current, 26), chevron = true) {
            pickColor(title, current) { onPick(it); render() }
        }
    }

    /** Beschrifteter Schieber; die Vorschau folgt beim Ziehen. */
    private fun slider(label: Int, value: Int, max: Int, unit: String, onChange: (Int) -> Unit): View {
        val caption = TextView(this).apply {
            textSize = 17f; setTextColor(ui.text); text = "${getString(label)}: $value$unit"
        }
        val bar = SeekBar(this).apply {
            this.max = max; progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    caption.text = "${getString(label)}: $p$unit"
                    onChange(p)
                    refreshPreview()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14))
            addView(caption)
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(8) })
        }
    }

    private fun editName() {
        val input = EditText(this).apply {
            setText(theme.name); setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.style_name)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { theme = theme.copy(name = it) }
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickBase() {
        val bases = Themes.builtIn
        AlertDialog.Builder(this)
            .setTitle(R.string.style_base)
            .setItems(bases.map { it.name }.toTypedArray()) { _, which ->
                theme = Themes.newCustom(this, bases[which], theme.name, theme.id)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Farbwahl: Palette zum Antippen oder Hex-Wert eintippen. */
    private fun pickColor(title: Int, current: Int, onPick: (Int) -> Unit) {
        val hex = EditText(this).apply {
            setText(String.format("#%06X", current and 0xFFFFFF))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = "#RRGGBB"
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dialog: AlertDialog
        PALETTE.toList().chunked(6).forEach { rowColors ->
            box.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER
                rowColors.forEach { c ->
                    addView(FrameLayout(this@ThemeEditorActivity).apply {
                        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
                        addView(swatch(c, 36))
                        setOnClickListener { dialog.dismiss(); onPick(c) }
                    })
                }
            })
        }
        box.addView(hex, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(12) })
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(padded(box))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parseHex(hex.text.toString())?.let(onPick)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun padded(v: View) = FrameLayout(this).apply {
        setPadding(ui.dp(24), ui.dp(12), ui.dp(24), 0)
        addView(v)
    }

    companion object {
        const val EXTRA_THEME_ID = "theme_id"

        /** "#RRGGBB" oder "RRGGBB" → deckende Farbe; alles andere null. */
        fun parseHex(text: String): Int? {
            val digits = text.trim().removePrefix("#")
            if (digits.length != 6) return null
            return digits.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
        }

        private val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFF2F2F2.toInt(), 0xFFBDBDBD.toInt(), 0xFF808080.toInt(), 0xFF202020.toInt(), 0xFF000000.toInt(),
            0xFFFF5629.toInt(), 0xFFD71921.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(),
            0xFF0381FE.toInt(), 0xFF03A9F4.toInt(), 0xFF009688.toInt(), 0xFF34C759.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
            0xFF1DB954.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFF1A237E.toInt(), 0xFF3E2723.toInt(), 0xFF004D40.toInt(),
        )
    }
}
