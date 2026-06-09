package com.meo.mediawidget

import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BucketTest {

    private fun size(wDp: Float, hDp: Float) = SizeF(wDp, hDp)

    @Test fun `HxW 1x3 picks MICRO_BAR`() = assertEquals(Bucket.MICRO_BAR, Bucket.pickForSize(size(210f, 70f)))
    @Test fun `HxW 1x4 picks BAR`() = assertEquals(Bucket.BAR, Bucket.pickForSize(size(290f, 70f)))
    @Test fun `HxW 1x5 picks BAR`() = assertEquals(Bucket.BAR, Bucket.pickForSize(size(370f, 70f)))
    @Test fun `HxW 2x3 picks MID_CARD`() = assertEquals(Bucket.MID_CARD, Bucket.pickForSize(size(210f, 140f)))
    @Test fun `HxW 2x5 picks MID_CARD`() = assertEquals(Bucket.MID_CARD, Bucket.pickForSize(size(370f, 140f)))
    @Test fun `HxW 3x3 picks MID_CARD`() = assertEquals(Bucket.MID_CARD, Bucket.pickForSize(size(210f, 210f)))
    @Test fun `HxW 3x5 picks MID_CARD`() = assertEquals(Bucket.MID_CARD, Bucket.pickForSize(size(370f, 210f)))
    @Test fun `HxW 4x3 picks WIDE`() = assertEquals(Bucket.WIDE, Bucket.pickForSize(size(210f, 290f)))
    @Test fun `HxW 4x4 picks WIDE`() = assertEquals(Bucket.WIDE, Bucket.pickForSize(size(290f, 290f)))
    @Test fun `HxW 4x5 picks MEGA`() = assertEquals(Bucket.MEGA, Bucket.pickForSize(size(370f, 290f)))
}
