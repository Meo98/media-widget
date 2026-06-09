package com.meo.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsResolutionTest {

    @Test
    fun `per-widget value wins over global`() {
        val r = SettingsResolver.resolveString(
            perWidget = "GLASS",
            global = "MATERIAL_YOU",
            fallback = "MATERIAL_YOU"
        )
        assertEquals("GLASS", r)
    }

    @Test
    fun `INHERIT sentinel falls back to global`() {
        val r = SettingsResolver.resolveString(
            perWidget = "INHERIT",
            global = "AMOLED",
            fallback = "MATERIAL_YOU"
        )
        assertEquals("AMOLED", r)
    }

    @Test
    fun `null per-widget falls back to global`() {
        val r = SettingsResolver.resolveString(
            perWidget = null,
            global = "GLASS",
            fallback = "MATERIAL_YOU"
        )
        assertEquals("GLASS", r)
    }

    @Test
    fun `null per-widget and null global use fallback`() {
        val r = SettingsResolver.resolveString(
            perWidget = null,
            global = null,
            fallback = "MATERIAL_YOU"
        )
        assertEquals("MATERIAL_YOU", r)
    }

    @Test
    fun `boolean per-widget overrides global`() {
        val r = SettingsResolver.resolveBool(
            perWidgetOverridden = true,
            perWidgetValue = false,
            global = true,
            fallback = true
        )
        assertEquals(false, r)
    }

    @Test
    fun `boolean per-widget not-overridden falls back to global`() {
        val r = SettingsResolver.resolveBool(
            perWidgetOverridden = false,
            perWidgetValue = false,
            global = true,
            fallback = false
        )
        assertEquals(true, r)
    }
}
