package com.meo.mediawidget

import android.media.AudioAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFilterTest {

    private fun info(pkg: String, usage: Int? = AudioAttributes.USAGE_MEDIA, hasTitle: Boolean = true) =
        SessionInfo(pkg, usage, hasTitle)

    @Test fun `music app with a title is media`() =
        assertTrue(SessionFilter.isMedia(info("com.spotify.music"), emptyMap()))

    @Test fun `Teams is hidden by default`() =
        assertFalse(SessionFilter.isMedia(info("com.microsoft.teams"), emptyMap()))

    @Test fun `user can switch Teams back on`() =
        assertTrue(SessionFilter.isMedia(info("com.microsoft.teams"), mapOf("com.microsoft.teams" to true)))

    @Test fun `user can hide a music app`() =
        assertFalse(SessionFilter.isMedia(info("com.spotify.music"), mapOf("com.spotify.music" to false)))

    @Test fun `a call in an unknown app is not media`() =
        assertFalse(
            SessionFilter.isMedia(info("org.example.chat", AudioAttributes.USAGE_VOICE_COMMUNICATION), emptyMap())
        )

    @Test fun `a ringing phone is not media`() =
        assertFalse(
            SessionFilter.isMedia(info("org.example.chat", AudioAttributes.USAGE_NOTIFICATION_RINGTONE), emptyMap())
        )

    @Test fun `session without a title is not media`() =
        assertFalse(SessionFilter.isMedia(info("com.spotify.music", hasTitle = false), emptyMap()))

    @Test fun `unknown audio usage counts as media`() =
        assertTrue(SessionFilter.isMedia(info("com.spotify.music", usage = null), emptyMap()))
}
