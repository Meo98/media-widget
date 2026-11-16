package com.meo.mediawidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.graphics.scale

object BlurHelper {

    /**
     * Unscharfer Hintergrund aus dem Cover. RenderEffect gibt es für
     * RemoteViews-Bitmaps nicht, also von Hand:
     *  1. stark verkleinern (mittelt die Farben),
     *  2. Kastenfilter in zwei Durchgängen (nimmt die Pixelkanten weg),
     *  3. in zwei Schritten weich hochskalieren,
     *  4. 40 % Schwarz darüber, damit heller Text lesbar bleibt.
     */
    fun blurForGlass(src: Bitmap, ctx: Context): Bitmap {
        val small = src.scale(SMALL, SMALL, true)
        val px = IntArray(SMALL * SMALL)
        small.getPixels(px, 0, SMALL, 0, 0, SMALL, SMALL)
        repeat(2) { boxBlur(px, SMALL, SMALL, RADIUS) }
        val blurred = Bitmap.createBitmap(px, SMALL, SMALL, Bitmap.Config.ARGB_8888)
        val mid = blurred.scale(SMALL * 3, SMALL * 3, true)
        val big = mid.scale(OUT, OUT, true)

        val tinted = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = PorterDuffColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(tinted).drawBitmap(big, 0f, 0f, paint)
        if (small !== src) small.recycle()
        blurred.recycle(); mid.recycle(); big.recycle()
        return tinted
    }

    /** Kastenfilter, erst waagrecht, dann senkrecht. Ränder werden wiederholt. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val n = 2 * r + 1
        for (y in 0 until h) for (x in 0 until w) {
            var red = 0; var green = 0; var blue = 0
            for (k in -r..r) {
                val sx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                val sy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                val c = src[sy * w + sx]
                red += (c shr 16) and 0xFF; green += (c shr 8) and 0xFF; blue += c and 0xFF
            }
            dst[y * w + x] = (0xFF shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
        }
    }

    private const val SMALL = 48
    private const val RADIUS = 4
    private const val OUT = 288
}
