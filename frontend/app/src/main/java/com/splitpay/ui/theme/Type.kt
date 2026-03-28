package com.splitpay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.splitpay.R

@OptIn(ExperimentalTextApi::class)
val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal,   variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium,   variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold,     variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter_variable, FontWeight.Black,    variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

val Typography = Typography(
    displayLarge   = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Black,    fontSize = 57.sp),
    displayMedium  = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Black,    fontSize = 45.sp),
    displaySmall   = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,     fontSize = 36.sp),
    headlineLarge  = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,     fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,     fontSize = 28.sp),
    headlineSmall  = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge     = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium    = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,   fontSize = 16.sp),
    titleSmall     = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium     = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    labelMedium    = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.5.sp),
)
