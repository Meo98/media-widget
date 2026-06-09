package com.meo.mediawidget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class StyleAssets(
    @DrawableRes val containerBg: Int,
    @DrawableRes val coverBg: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
    @ColorInt val buttonTint: Int,
    val coverCornerDp: Int,
    val containerCornerDp: Int,
    val useBlurredBackdrop: Boolean
) {
    companion object {
        fun forStyle(style: Style, ctx: Context): StyleAssets = when (style) {
            Style.MATERIAL_YOU -> StyleAssets(
                containerBg = R.drawable.bg_material_you,
                coverBg = R.drawable.bg_cover_material,
                textPrimary = ctx.getColor(R.color.material_text_primary),
                textSecondary = ctx.getColor(R.color.material_text_secondary),
                buttonTint = ctx.getColor(R.color.material_button_tint),
                coverCornerDp = 16, containerCornerDp = 20,
                useBlurredBackdrop = false
            )
            Style.GLASS -> StyleAssets(
                containerBg = R.drawable.bg_glass,
                coverBg = R.drawable.bg_cover_glass,
                textPrimary = ctx.getColor(R.color.glass_text_primary),
                textSecondary = ctx.getColor(R.color.glass_text_secondary),
                buttonTint = ctx.getColor(R.color.glass_button_tint),
                coverCornerDp = 16, containerCornerDp = 24,
                useBlurredBackdrop = true
            )
            Style.AMOLED -> StyleAssets(
                containerBg = R.drawable.bg_amoled,
                coverBg = R.drawable.bg_cover_amoled,
                textPrimary = ctx.getColor(R.color.amoled_text_primary),
                textSecondary = ctx.getColor(R.color.amoled_text_secondary),
                buttonTint = ctx.getColor(R.color.amoled_button_tint),
                coverCornerDp = 4, containerCornerDp = 0,
                useBlurredBackdrop = false
            )
        }
    }
}
