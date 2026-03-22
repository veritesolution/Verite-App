package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val outfitFamily = FontFamily(
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.W200),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.W300),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.W400),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.W500),
    Font(googleFont = GoogleFont("Outfit"), fontProvider = provider, weight = FontWeight.W600),
)

val cormorantFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Cormorant Garamond"),
        fontProvider = provider,
        weight = FontWeight.W500,
        style = FontStyle.Italic
    ),
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = outfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = outfitFamily,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = outfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
