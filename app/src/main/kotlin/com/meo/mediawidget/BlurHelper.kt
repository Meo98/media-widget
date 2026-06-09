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
     * Approximates a frosted-glass backdrop by:
     *  1. Downsampling album art to 32x32 (heavy averaging effect)
     *  2. Upscaling back to 512x512 with bilinear filtering (smears pixels)
     *  3. Overlaying a 40% black tint for legibility of white text on top
     *
     * RenderEffect-blur is more elegant but it only works on real Views,
     * not RemoteViews bitmaps. This bitmap downsample trick is the
     * idiomatic approach for RemoteViews-backdrops.
     */
    fun blurForGlass(src: Bitmap, ctx: Context): Bitmap {
        val tiny = src.scale(32, 32, true)
        val upscaled = tiny.scale(512, 512, true)
        val tinted = Bitmap.createBitmap(upscaled.width, upscaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tinted)
        val paint = Paint().apply {
            isAntiAlias = true
            colorFilter = PorterDuffColorFilter(0x66000000.toInt(), PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawBitmap(upscaled, 0f, 0f, paint)
        if (tiny != src) tiny.recycle()
        if (upscaled != tinted) upscaled.recycle()
        return tinted
    }
}
