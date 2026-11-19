package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FontChoice
import com.example.data.FontScaleOption

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val NunitoFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold)
)

val LexendFamily = FontFamily(
    Font(R.font.lexend, FontWeight.Normal),
    Font(R.font.lexend, FontWeight.Medium),
    Font(R.font.lexend, FontWeight.SemiBold),
    Font(R.font.lexend, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.Sans -> PlusJakartaSansFamily
    FontChoice.Rounded -> NunitoFamily
    FontChoice.Dyslexia -> LexendFamily
}

val LocalBlueBlPalette = staticCompositionLocalOf { Themes.default().palette }
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalHighContrast = staticCompositionLocalOf { false }
val LocalFontScale = staticCompositionLocalOf { FontScaleOption.Medium }

fun buildBlueBlTypography(
    fontChoice: FontChoice = FontChoice.Sans,
    fontScale: FontScaleOption = FontScaleOption.Medium
): Typography {
    val family = fontFamilyFor(fontChoice)
    val s = fontScale.scaleFactor
    val extraTracking = if (fontChoice == FontChoice.Dyslexia) 0.35.sp else 0.sp

    return Typography(
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (20f * s).sp,
            lineHeight = (26f * s).sp,
            letterSpacing = extraTracking
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16.5f * s).sp,
            lineHeight = (22f * s).sp,
            letterSpacing = extraTracking
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * s).sp,
            lineHeight = (19f * s).sp,
            letterSpacing = extraTracking
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (15.5f * s).sp,
            lineHeight = (21.5f * s).sp,
            letterSpacing = extraTracking
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = extraTracking
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (12.5f * s).sp,
            lineHeight = (17f * s).sp,
            letterSpacing = extraTracking
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * s).sp,
            lineHeight = (18f * s).sp,
            letterSpacing = extraTracking
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (12.5f * s).sp,
            lineHeight = (16f * s).sp,
            letterSpacing = extraTracking
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * s).sp,
            lineHeight = (14f * s).sp,
            letterSpacing = extraTracking
        )
    )
}

@Composable
fun BlueBlTheme(
    themeDef: BlueBlThemeDef,
    fontChoice: FontChoice = FontChoice.Sans,
    fontScale: FontScaleOption = FontScaleOption.Medium,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val pal = remember(themeDef, highContrast) {
        if (highContrast) themeDef.palette.withHighContrast() else themeDef.palette
    }
    val typography = remember(fontChoice, fontScale) {
        buildBlueBlTypography(fontChoice, fontScale)
    }

    val colorScheme = if (pal.isLight) {
        lightColorScheme(
            primary = pal.accent,
            onPrimary = pal.onAccent,
            secondary = pal.accent2,
            onSecondary = pal.onAccent,
            background = pal.bg,
            onBackground = pal.fg,
            surface = pal.headerBg,
            onSurface = pal.fg,
            surfaceVariant = pal.composerFieldBg,
            onSurfaceVariant = pal.dim,
            outline = pal.border,
            error = pal.error,
            onError = pal.onAccent
        )
    } else {
        darkColorScheme(
            primary = pal.accent,
            onPrimary = pal.onAccent,
            secondary = pal.accent2,
            onSecondary = pal.onAccent,
            background = pal.bg,
            onBackground = pal.fg,
            surface = pal.headerBg,
            onSurface = pal.fg,
            surfaceVariant = pal.composerFieldBg,
            onSurfaceVariant = pal.dim,
            outline = pal.border,
            error = pal.error,
            onError = pal.onAccent
        )
    }

    CompositionLocalProvider(
        LocalBlueBlPalette provides pal,
        LocalReduceMotion provides reduceMotion,
        LocalHighContrast provides highContrast,
        LocalFontScale provides fontScale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
