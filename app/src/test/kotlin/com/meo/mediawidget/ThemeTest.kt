package com.meo.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, weil org.json und android.graphics.Color im reinen JVM-Test Stubs sind.
@RunWith(RobolectricTestRunner::class)
class ThemeTest {

    private val custom = Theme(
        id = "custom-1a2b3c4d", name = "Mein \"Stil\"",
        light = ThemeColors(
            background = 0x80112233.toInt(), cover = 0xFF303030.toInt(), text = 0xFFFFFFFF.toInt(),
            subtext = 0xFF888888.toInt(), buttons = 0xFFFFFFFF.toInt(), play = 0xFFFF5629.toInt()
        ),
        radiusDp = 18, coverRadiusDp = 12, blurBackdrop = true
    )

    @Test fun `custom themes survive the round trip through JSON`() =
        assertEquals(listOf(custom), Themes.parseCustom(Themes.encodeCustom(listOf(custom))))

    @Test fun `launcher radius survives the round trip`() {
        val t = custom.copy(radiusDp = Theme.SYSTEM_RADIUS)
        assertEquals(Theme.SYSTEM_RADIUS, Themes.parseCustom(Themes.encodeCustom(listOf(t))).single().radiusDp)
    }

    @Test fun `broken or missing storage yields no custom themes`() {
        assertTrue(Themes.parseCustom(null).isEmpty())
        assertTrue(Themes.parseCustom("{kaputt").isEmpty())
    }

    @Test fun `stored entry cannot shadow a built-in theme`() {
        val fake = custom.copy(id = "GLASS")
        assertTrue(Themes.parseCustom(Themes.encodeCustom(listOf(fake))).isEmpty())
    }

    @Test fun `old enum names still resolve to their theme`() {
        for (id in listOf("MATERIAL_YOU", "GLASS", "AMOLED", "OXYGEN")) {
            assertEquals(id, Themes.find(id, emptyList()).id)
        }
    }

    @Test fun `deleted or unknown theme falls back to the default`() {
        assertEquals(Themes.DEFAULT_ID, Themes.find("custom-gone", emptyList()).id)
        assertEquals(Themes.DEFAULT_ID, Themes.find(null, emptyList()).id)
    }

    @Test fun `custom theme is found by id`() =
        assertEquals(custom, Themes.find(custom.id, listOf(custom)))

    @Test fun `built-in ids are unique`() =
        assertEquals(Themes.builtIn.size, Themes.builtIn.map { it.id }.toSet().size)

    @Test fun `hex colours parse with and without hash`() {
        assertEquals(0xFFFF5629.toInt(), ThemeEditorActivity.parseHex("#FF5629"))
        assertEquals(0xFFFF5629.toInt(), ThemeEditorActivity.parseHex(" ff5629 "))
    }

    @Test fun `invalid hex colours are rejected`() {
        assertNull(ThemeEditorActivity.parseHex("#FFF"))
        assertNull(ThemeEditorActivity.parseHex("#GG5629"))
        assertNull(ThemeEditorActivity.parseHex(""))
    }
}
