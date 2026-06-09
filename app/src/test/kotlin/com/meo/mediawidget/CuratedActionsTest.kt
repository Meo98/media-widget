package com.meo.mediawidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CuratedActionsTest {

    @Test fun `matches tidal favorite as Like`() {
        val p = CuratedActions.polish("com.tidal.android.action.FAVORITE_TRACK")
        assertNotNull(p)
        assertEquals("Like", p!!.label)
    }

    @Test fun `matches spotify thumbs up`() {
        assertNotNull(CuratedActions.polish("com.spotify.thumbs_up"))
    }

    @Test fun `matches youtube music like_track`() {
        val p = CuratedActions.polish("yt.music.like_track")
        assertNotNull(p); assertEquals("Like", p!!.label)
    }

    @Test fun `matches dislike or thumb_down`() {
        assertNotNull(CuratedActions.polish("dislike_track"))
        assertNotNull(CuratedActions.polish("thumb_down"))
        assertNotNull(CuratedActions.polish("ACTION_THUMBS_DOWN"))
    }

    @Test fun `matches queue and add to playlist`() {
        assertNotNull(CuratedActions.polish("ADD_TO_QUEUE"))
        assertNotNull(CuratedActions.polish("add_to_playlist"))
    }

    @Test fun `matches shuffle and repeat`() {
        assertNotNull(CuratedActions.polish("shuffle_on"))
        assertNotNull(CuratedActions.polish("toggle_repeat"))
        assertNotNull(CuratedActions.polish("loop_mode"))
    }

    @Test fun `returns null for unknown action`() {
        assertNull(CuratedActions.polish("some.unrelated.weird.action"))
        assertNull(CuratedActions.polish(""))
    }
}
