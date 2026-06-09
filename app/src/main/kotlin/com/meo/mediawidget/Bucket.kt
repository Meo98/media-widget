package com.meo.mediawidget

import android.util.SizeF

enum class Bucket {
    MICRO_BAR,  // 1x3 (HxW)
    BAR,        // 1x4, 1x5
    MID_CARD,   // 2x3-3x5
    WIDE,       // 4x3, 4x4
    MEGA;       // 4x5

    companion object {

        // Cell dp constants — based on stock Pixel launcher (70dp per cell).
        // pickForSize() does its own thresholding; these are documentation.
        private const val W_3 = 210f
        private const val W_4 = 290f
        private const val W_5 = 370f
        private const val H_1 = 70f
        private const val H_2 = 140f
        private const val H_3 = 210f
        private const val H_4 = 290f

        /**
         * Map a (width, height) dp size to its layout bucket. Thresholds
         * are conservative — borderline sizes fall to the smaller bucket
         * to avoid rendering overflow.
         */
        fun pickForSize(sizeDp: SizeF): Bucket {
            val w = sizeDp.width
            val h = sizeDp.height
            return when {
                h < H_2 && w < W_4 -> MICRO_BAR        // height <2 rows, width <4 cols
                h < H_2 -> BAR                         // height <2 rows, width >=4 cols
                h < H_4 -> MID_CARD                    // height <4 rows (covers 2x3-3x5)
                w < W_5 -> WIDE                        // height >=4 rows, width <5 cols
                else -> MEGA                           // height >=4 rows AND width >=5 cols
            }
        }
    }
}
