package com.meo.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric, weil org.json im reinen JVM-Test nur als Stub vorliegt.
@RunWith(RobolectricTestRunner::class)
class MediaMemoryTest {

    private val entry = Remembered(
        pkg = "com.example.player", title = "Beispieltitel \"mit\" Anführungszeichen", artist = "Beispiel & Co",
        positionMs = 61_000, durationMs = 240_000, playing = true,
        playingSince = 1_794_000_000_000, savedAt = 1_794_000_060_000
    )

    @Test fun `entry survives the round trip through JSON`() =
        assertEquals(entry, Remembered.fromJson(entry.pkg, entry.toJson()))

    @Test fun `broken JSON yields no entry instead of a crash`() =
        assertNull(Remembered.fromJson("com.example.player", "{kaputt"))

    @Test fun `missing fields fall back to empty values`() {
        val r = Remembered.fromJson("com.example.player", "{\"title\":\"Nur Titel\"}")!!
        assertEquals("Nur Titel", r.title)
        assertEquals("", r.artist)
        assertEquals(0L, r.playingSince)
        assertEquals(false, r.playing)
    }
}
