package com.meo.mediawidget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

/** Die Farben eines Stils (ARGB). */
data class ThemeColors(
    val background: Int,
    /** Fläche hinter dem Cover, sichtbar solange kein Bild da ist. */
    val cover: Int,
    val text: Int,
    val subtext: Int,
    val buttons: Int,
    val play: Int
)

/**
 * Ein Widget-Stil als Datensatz. Die eingebauten Vorlagen ([Themes.builtIn])
 * und die vom User erstellten Stile haben dieselbe Form; nur die Kennung
 * unterscheidet sie.
 */
data class Theme(
    val id: String,
    val name: String,
    val light: ThemeColors,
    val dark: ThemeColors = light,
    /** Eckenradius in dp, oder [SYSTEM_RADIUS] für den Radius des Launchers. */
    val radiusDp: Int,
    val coverRadiusDp: Int,
    /** Unscharfes Cover hinter dem Inhalt, in allen Widget-Grössen. */
    val blurBackdrop: Boolean = false,
    /** Material You: Farben kommen aus dem Hintergrundbild, [light]/[dark] sind nur Rückfall. */
    val dynamic: Boolean = false
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    fun colors(ctx: Context): ThemeColors {
        if (dynamic) return ThemeColors(
            background = ctx.getColor(R.color.material_surface),
            cover = ctx.getColor(R.color.material_cover_bg),
            text = ctx.getColor(R.color.material_text_primary),
            subtext = ctx.getColor(R.color.material_text_secondary),
            buttons = ctx.getColor(R.color.material_button_tint),
            play = ctx.getColor(R.color.material_button_tint)
        )
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) dark else light
    }

    /**
     * Färbt ein Widget-Layout ein. Fehlt eine View in dieser Layout-Stufe,
     * überspringt RemoteViews die Aktion beim Anwenden.
     */
    fun applyTo(ctx: Context, rv: RemoteViews, art: Bitmap?) {
        val c = colors(ctx)
        rv.setInt(R.id.container, "setBackgroundColor", c.background)
        // Derselbe Radius für die Fläche und das unscharfe Bild dahinter.
        for (id in intArrayOf(R.id.container, R.id.backdrop)) {
            if (radiusDp == SYSTEM_RADIUS) {
                rv.setViewOutlinePreferredRadiusDimen(id, android.R.dimen.system_app_widget_background_radius)
            } else {
                rv.setViewOutlinePreferredRadius(id, radiusDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        rv.setInt(R.id.cover, "setBackgroundColor", c.cover)
        rv.setViewOutlinePreferredRadius(R.id.cover, coverRadiusDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)

        rv.setTextColor(R.id.title, c.text)
        rv.setTextColor(R.id.artist, c.subtext)
        rv.setInt(R.id.btn_play_pause, "setColorFilter", c.play)
        rv.setInt(R.id.btn_prev, "setColorFilter", c.buttons)
        rv.setInt(R.id.btn_next, "setColorFilter", c.buttons)

        if (blurBackdrop && art != null) {
            rv.setImageViewBitmap(R.id.backdrop, BlurHelper.blurForGlass(art, ctx))
            rv.setViewVisibility(R.id.backdrop, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.backdrop, View.GONE)
        }
    }

    /** Nur für eigene Stile: sie haben einen einzigen Farbsatz für hell und dunkel. */
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name)
        .put("bg", light.background).put("cover", light.cover)
        .put("text", light.text).put("sub", light.subtext)
        .put("btn", light.buttons).put("play", light.play)
        .put("radius", radiusDp).put("coverRadius", coverRadiusDp).put("blur", blurBackdrop)

    companion object {
        const val SYSTEM_RADIUS = -1
        const val CUSTOM_PREFIX = "custom-"

        fun fromJson(o: JSONObject): Theme? = try {
            Theme(
                id = o.getString("id"), name = o.getString("name"),
                light = ThemeColors(
                    background = o.getInt("bg"), cover = o.getInt("cover"),
                    text = o.getInt("text"), subtext = o.getInt("sub"),
                    buttons = o.getInt("btn"), play = o.getInt("play")
                ),
                radiusDp = o.optInt("radius", 24), coverRadiusDp = o.optInt("coverRadius", 14),
                blurBackdrop = o.optBoolean("blur", false)
            )
        } catch (_: Exception) { null }
    }
}

object Themes {

    const val DEFAULT_ID = "MATERIAL_YOU"
    private const val ORANGE = 0xFFFF5629.toInt()

    private fun rgb(hex: Long) = hex.toInt()

    /**
     * Eingebaute Vorlagen. Die Kennungen der ersten vier sind die Namen des
     * früheren Enums, damit gespeicherte Einstellungen weiter gelten. Die
     * Hersteller-Vorlagen sind Annäherungen an deren Systemwidgets.
     */
    val builtIn: List<Theme> = listOf(
        Theme(
            id = "MATERIAL_YOU", name = "Material You (Pixel)", dynamic = true,
            light = ThemeColors(rgb(0xFFE8EAF0), rgb(0xFFD0D8F0), rgb(0xFF1B1B1F), rgb(0xFF46464F), rgb(0xFF3F5AA9), rgb(0xFF3F5AA9)),
            radiusDp = Theme.SYSTEM_RADIUS, coverRadiusDp = 16
        ),
        Theme(
            id = "GLASS", name = "Glass",
            light = ThemeColors(rgb(0x80000000), rgb(0x40FFFFFF), Color.WHITE, rgb(0xCCFFFFFF), Color.WHITE, Color.WHITE),
            radiusDp = 24, coverRadiusDp = 16, blurBackdrop = true
        ),
        Theme(
            id = "AMOLED", name = "AMOLED",
            light = ThemeColors(Color.BLACK, rgb(0xFF101010), Color.WHITE, rgb(0xFF808080), Color.WHITE, Color.WHITE),
            radiusDp = 0, coverRadiusDp = 4
        ),
        Theme(
            id = "OXYGEN", name = "OxygenOS (OnePlus, OPPO)",
            light = ThemeColors(Color.WHITE, rgb(0xFFF2F2F2), rgb(0xFF141414), rgb(0xFF808080), rgb(0xFF141414), ORANGE),
            dark = ThemeColors(rgb(0xFF202020), rgb(0xFF303030), rgb(0xFFF0F0F0), rgb(0xFF8A8A8A), rgb(0xFFF0F0F0), ORANGE),
            radiusDp = Theme.SYSTEM_RADIUS, coverRadiusDp = 14
        ),
        Theme(
            id = "ONE_UI", name = "One UI (Samsung)",
            light = ThemeColors(rgb(0xFFFCFCFC), rgb(0xFFEDEDED), rgb(0xFF0A0A0A), rgb(0xFF7A7A7A), rgb(0xFF0A0A0A), rgb(0xFF0381FE)),
            dark = ThemeColors(rgb(0xFF252525), rgb(0xFF3A3A3A), rgb(0xFFFAFAFA), rgb(0xFF9A9A9A), rgb(0xFFFAFAFA), rgb(0xFF3E91FF)),
            radiusDp = 26, coverRadiusDp = 18
        ),
        Theme(
            id = "HYPER_OS", name = "HyperOS (Xiaomi)",
            light = ThemeColors(rgb(0xFFFFFFFF), rgb(0xFFF0F0F0), rgb(0xFF000000), rgb(0xFF8C8C8C), rgb(0xFF000000), rgb(0xFF3482FF)),
            dark = ThemeColors(rgb(0xFF242424), rgb(0xFF363636), rgb(0xFFFFFFFF), rgb(0xFF999999), rgb(0xFFFFFFFF), rgb(0xFF3482FF)),
            radiusDp = 28, coverRadiusDp = 16
        ),
        Theme(
            id = "NOTHING", name = "Nothing OS",
            light = ThemeColors(rgb(0xFFEDEDED), rgb(0xFFD6D6D6), rgb(0xFF1B1B1D), rgb(0xFF6E6E6E), rgb(0xFF1B1B1D), rgb(0xFFD71921)),
            dark = ThemeColors(rgb(0xFF1B1B1D), rgb(0xFF2C2C2E), rgb(0xFFFFFFFF), rgb(0xFF8E8E93), rgb(0xFFFFFFFF), rgb(0xFFD71921)),
            radiusDp = 22, coverRadiusDp = 10
        ),
    )

    /** Unbekannte Kennung (z. B. gelöschter eigener Stil) fällt auf die Standardvorlage zurück. */
    fun find(id: String?, custom: List<Theme>): Theme =
        (builtIn + custom).firstOrNull { it.id == id } ?: builtIn.first()

    fun parseCustom(json: String?): List<Theme> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { Theme.fromJson(arr.getJSONObject(it)) }
                .filter { it.isCustom }
        } catch (_: Exception) { emptyList() }
    }

    fun encodeCustom(themes: List<Theme>): String =
        JSONArray().apply { themes.forEach { put(it.toJson()) } }.toString()

    /**
     * Neuer eigener Stil als Kopie einer Vorlage. Übernommen werden die Farben,
     * die [ctx] gerade zeigt (hell oder dunkel), als einziger Farbsatz.
     */
    fun newCustom(ctx: Context, base: Theme, name: String, id: String): Theme {
        val c = base.colors(ctx)
        return Theme(
            id = id, name = name, light = c,
            radiusDp = base.radiusDp, coverRadiusDp = base.coverRadiusDp,
            blurBackdrop = base.blurBackdrop
        )
    }
}
