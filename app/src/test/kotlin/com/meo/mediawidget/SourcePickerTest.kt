package com.meo.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePickerTest {

    private val spotify = "com.spotify.music"
    private val books = "com.google.android.apps.books"
    private val tidal = "com.aspiro.tidal"

    private fun pick(
        live: List<LiveInfo> = emptyList(), remembered: List<String> = emptyList(),
        selected: String? = null, selectedAt: Long = 0L, preferred: String? = null
    ) = SourcePicker.pick(live, remembered, selected, selectedAt, preferred)

    @Test fun `nothing live and nothing remembered shows nothing`() =
        assertEquals(Source.None, pick())

    @Test fun `playing app beats a paused one`() = assertEquals(
        Source.Live(books),
        pick(live = listOf(LiveInfo(spotify, playing = false, playingSince = 50), LiveInfo(books, true, 10)))
    )

    @Test fun `of two paused apps the one started last wins`() = assertEquals(
        Source.Live(spotify),
        pick(live = listOf(LiveInfo(books, false, 10), LiveInfo(spotify, false, 50)))
    )

    @Test fun `closed app is shown from memory`() =
        assertEquals(Source.Remembered(spotify), pick(remembered = listOf(spotify, books)))

    @Test fun `a live app beats the memory of a closed one`() = assertEquals(
        Source.Live(books),
        pick(live = listOf(LiveInfo(books, false, 0)), remembered = listOf(spotify))
    )

    @Test fun `tapped app stays although another one was already playing`() = assertEquals(
        Source.Live(spotify),
        pick(
            live = listOf(LiveInfo(spotify, false, 10), LiveInfo(books, true, 20)),
            selected = spotify, selectedAt = 100
        )
    )

    @Test fun `an app started after the tap takes over`() = assertEquals(
        Source.Live(books),
        pick(
            live = listOf(LiveInfo(spotify, false, 10), LiveInfo(books, true, 200)),
            selected = spotify, selectedAt = 100
        )
    )

    @Test fun `tapped closed app is shown from memory over a paused live one`() = assertEquals(
        Source.Remembered(spotify),
        pick(live = listOf(LiveInfo(books, false, 20)), remembered = listOf(spotify), selected = spotify, selectedAt = 100)
    )

    @Test fun `stale tap on a closed app loses against newer playback`() = assertEquals(
        Source.Live(books),
        pick(live = listOf(LiveInfo(books, false, 200)), remembered = listOf(spotify), selected = spotify, selectedAt = 100)
    )

    @Test fun `tap on an app that is gone falls back to what is live`() = assertEquals(
        Source.Live(books),
        pick(live = listOf(LiveInfo(books, false, 0)), selected = tidal, selectedAt = 100)
    )

    @Test fun `preferred app wins among live apps`() = assertEquals(
        Source.Live(tidal),
        pick(live = listOf(LiveInfo(books, true, 50), LiveInfo(tidal, false, 10)), preferred = tidal)
    )

    @Test fun `preferred app wins among remembered apps`() =
        assertEquals(Source.Remembered(tidal), pick(remembered = listOf(spotify, tidal), preferred = tidal))
}
