package com.meo.mediawidget.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ScrollView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RadioButton
import android.widget.Switch
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Kleine Bausteine im Stil der OxygenOS-17-Einstellungen (am Gerät abgemessen):
 * schwarzer Grund, abgerundete Karten, eingerückte Trennlinien, graue Abschnittstitel.
 * Schalter und Radiobuttons kommen bewusst vom System (Theme.DeviceDefault),
 * damit OnePlus sie selber zeichnet.
 */
class Ui(private val ctx: Context, forceDark: Boolean = false) {
    /** forceDark: z. B. Alarm-Bildschirm, der wie der OnePlus-Wecker immer dunkel ist. */
    val dark = forceDark ||
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val bg = if (dark) Color.BLACK else Color.rgb(242, 242, 242)
    val card = if (dark) Color.rgb(32, 32, 32) else Color.WHITE
    val text = if (dark) Color.rgb(240, 240, 240) else Color.rgb(20, 20, 20)
    val subtle = if (dark) Color.rgb(138, 138, 138) else Color.rgb(128, 128, 128)
    val divider = if (dark) Color.rgb(48, 48, 48) else Color.rgb(230, 230, 230)
    val ripple = if (dark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)

    /**
     * Akzentfarbe. OnePlus/OPPO zeichnen ihre Schalter in eigenem Orange (am Gerät gemessen: #FF5629),
     * nicht in der Material-You-Farbe, die Android fremden Apps gibt. Andere Hersteller: Systemfarbe.
     */
    val accent: Int = if (Build.MANUFACTURER.lowercase() in setOf("oneplus", "oppo", "realme")) Color.rgb(255, 86, 41)
        else ctx.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent)).run { getColor(0, Color.rgb(255, 86, 41)).also { recycle() } }
    private val offTrack = if (dark) Color.rgb(74, 74, 74) else Color.rgb(210, 210, 210)

    private val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

    /** Systemschalter, nur umgefärbt wie bei OnePlus: weisser Knopf, orange Spur. */
    fun switch(on: Boolean) = Switch(ctx).apply {
        isChecked = on; isClickable = false; isFocusable = false
        // Volle Spur in Akzentfarbe, weisser Knopf innen (OxygenOS-Form, ca. 44×26 dp).
        trackDrawable = GradientDrawable().apply {
            cornerRadius = dp(13).toFloat(); setSize(dp(44), dp(26))
            color = ColorStateList(checkedStates, intArrayOf(accent, offTrack))
        }
        thumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setSize(dp(26), dp(26)); setColor(Color.WHITE)
            setStroke(dp(3), Color.TRANSPARENT) // Abstand zum Spurrand
        }
        thumbTintList = null; trackTintList = null
    }

    fun radio(on: Boolean) = RadioButton(ctx).apply {
        isChecked = on; isClickable = false; isFocusable = false
        buttonTintList = ColorStateList(checkedStates, intArrayOf(accent, subtle))
    }

    /**
     * Baut das Seitengerüst wie in den Systemeinstellungen: randlos, scrollbar,
     * ohne Titelleiste. Gibt die Liste zurück, in die die Seite ihre Karten legt.
     */
    fun page(activity: Activity): LinearLayout {
        activity.actionBar?.hide()
        activity.window.decorView.setBackgroundColor(bg)
        if (!dark) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(light, light)
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            addView(list); clipToPadding = false
            // Randlos (Android 15+): Inhalt nicht unter Statusleiste und Gestenleiste schieben.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom + dp(24)); insets
            }
        }
        // Abdeckung unter der Statusleiste, damit der Inhalt beim Scrollen nicht darunter durchläuft.
        val scrim = View(activity).apply { setBackgroundColor(bg) }
        val frame = FrameLayout(activity).apply {
            addView(scroll); addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, 0))
            setOnApplyWindowInsetsListener { _, insets ->
                scrim.layoutParams.height = insets.getInsets(WindowInsets.Type.statusBars()).top
                scrim.requestLayout(); scroll.dispatchApplyWindowInsets(insets); insets
            }
        }
        activity.setContentView(frame)
        return list
    }

    /** Grauer Wert rechts in einer Zeile, z. B. der gewählte Stil. */
    fun value(t: String) = TextView(ctx).apply {
        text = t; textSize = 15f; setTextColor(subtle); maxLines = 1
    }

    /** Knöpfe am Seitenende mit demselben Rand wie die Karten. */
    fun buttons(vararg pills: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), 0)
        pills.forEach { addView(it) }
    }

    fun dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    fun title(t: String) = TextView(ctx).apply {
        text = t; textSize = 34f; setTextColor(this@Ui.text)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(24), dp(24), dp(24), dp(16))
    }

    /** Grauer Abschnittstitel über einer Karte, wie «Gekoppelte Geräte». */
    fun section(t: String) = TextView(ctx).apply {
        text = t; textSize = 14f; setTextColor(subtle)
        setPadding(dp(28), dp(24), dp(28), dp(10))
    }

    /** Grauer Hinweistext unter einer Karte. */
    fun footnote(t: String) = TextView(ctx).apply {
        text = t; textSize = 14f; setTextColor(subtle)
        setPadding(dp(28), dp(10), dp(28), dp(4))
    }

    /** Abgerundete Karte; Zeilen mit [CardBuilder.row] hinzufügen. */
    fun card(build: CardBuilder.() -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 20)
            clipToOutline = true
        }
        CardBuilder(this, box).build()
        return box.also {
            it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                leftMargin = dp(14); rightMargin = dp(14)
            }
        }
    }

    class CardBuilder(private val ui: Ui, private val box: LinearLayout) {
        private var count = 0

        /**
         * Eine Zeile: optional Symbol links, Titel + Untertitel, rechts ein Element
         * (Schalter, Radiobutton, Text) oder ein Pfeil, wenn [chevron].
         */
        fun row(
            title: String,
            subtitle: String? = null,
            icon: Int? = null,
            /** Fertiges Bild (z. B. App-Icon), wird nicht eingefärbt. */
            iconDrawable: Drawable? = null,
            end: View? = null,
            chevron: Boolean = false,
            titleColor: Int? = null,
            onClick: (() -> Unit)? = null,
        ): LinearLayout {
            if (count++ > 0) box.addView(View(box.context).apply { setBackgroundColor(ui.divider) },
                LinearLayout.LayoutParams(MATCH_PARENT, 1).apply {
                    leftMargin = ui.dp(if (icon != null) 64 else if (iconDrawable != null) 68 else 20)
                    rightMargin = ui.dp(20)
                })
            val line = LinearLayout(box.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(60)
                setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14))
                if (onClick != null) {
                    background = RippleDrawable(ColorStateList.valueOf(ui.ripple), null, null)
                    setOnClickListener { onClick() }
                }
            }
            if (icon != null) line.addView(ImageView(box.context).apply {
                setImageResource(icon); setColorFilter(ui.text)
            }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { rightMargin = ui.dp(20) })

            if (iconDrawable != null) line.addView(ImageView(box.context).apply {
                setImageDrawable(iconDrawable)
            }, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)).apply { rightMargin = ui.dp(16) })

            val texts = LinearLayout(box.context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(box.context).apply {
                text = title; textSize = 17f; setTextColor(titleColor ?: ui.text)
            })
            if (subtitle != null) texts.addView(TextView(box.context).apply {
                text = subtitle; textSize = 14f; setTextColor(ui.subtle); setPadding(0, ui.dp(2), 0, 0)
            })
            line.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            // Feste Grösse des Elements (z. B. Status-Punkt) übernehmen, sonst WRAP_CONTENT.
            if (end != null) line.addView(end, LinearLayout.LayoutParams(
                end.layoutParams?.width ?: WRAP_CONTENT, end.layoutParams?.height ?: WRAP_CONTENT,
            ).apply { leftMargin = ui.dp(12) })
            if (chevron) line.addView(TextView(box.context).apply {
                text = "›"; textSize = 26f; setTextColor(ui.subtle); setPadding(ui.dp(12), 0, 0, ui.dp(3))
            })
            box.addView(line)
            return line
        }

        /** Beliebiger Inhalt in der Karte (z. B. Log-Text). */
        fun content(v: View) {
            if (count++ > 0) box.addView(View(box.context).apply { setBackgroundColor(ui.divider) },
                LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { leftMargin = ui.dp(20); rightMargin = ui.dp(20) })
            box.addView(v)
        }
    }

    /**
     * Pillen-Knopf wie bei OxygenOS (Wecker, Dialoge): voll orange für die Hauptaktion,
     * dunkelgrau für die Nebenaktion.
     */
    fun pill(label: String, primary: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 18f; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else this@Ui.text)
        val fill = if (primary) accent else (if (dark) Color.rgb(44, 44, 44) else Color.rgb(228, 228, 228))
        background = RippleDrawable(ColorStateList.valueOf(ripple), rounded(fill, 28), rounded(Color.WHITE, 28))
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
    }

    /** Kleiner farbiger Punkt für den Status. */
    fun dot(color: Int) = View(ctx).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
    }
}
