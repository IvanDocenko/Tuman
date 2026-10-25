package com.tuman.browser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.tuman.browser.data.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Midnight = Color(0xFF090E19)
val FogSurface = Color(0xFF151E2D)
val FogText = Color(0xFFEAF2FF)
val MutedFog = Color(0xFF9BAAC1)

val LocalCustomization = staticCompositionLocalOf { Customization() }
@Composable
fun tr(en: String, ru: String) = LocalCustomization.current.language.text(en, ru)

@Composable
fun TumanTheme(settings: Customization = Customization(), content: @Composable () -> Unit) {
    val dark = settings.theme == ThemeMode.DARK || (settings.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val accent by animateColorAsState(Color(settings.accentArgb), tween(if (settings.animations) 280 else 0), label = "accent")
    val onAccent = if (accent.red * 0.2126f + accent.green * 0.7152f + accent.blue * 0.0722f > 0.55f)
        Midnight else Color.White
    val palette = settings.palette
    val background by animateColorAsState(Color(if (dark) palette.darkBackground else palette.lightBackground), tween(if (settings.animations) 500 else 0), label = "palette-background")
    val surface by animateColorAsState(if (dark) Color(palette.darkSurface) else lerp(Color(palette.lightBackground), Color.White, .65f), tween(if (settings.animations) 500 else 0), label = "palette-surface")
    val density = LocalDensity.current
    CompositionLocalProvider(LocalCustomization provides settings,
        LocalDensity provides Density(density.density, density.fontScale * settings.uiScale)) {
    MaterialTheme(
        colorScheme = if (!dark) lightColorScheme(
            primary = accent.copy(red = accent.red * .48f, green = accent.green * .48f, blue = accent.blue * .48f),
            onPrimary = Color.White, secondary = accent.copy(red = accent.red * .5f, green = accent.green * .5f, blue = accent.blue * .5f), secondaryContainer = lerp(background, accent, .20f), onSecondaryContainer = Midnight,
            background = background, onBackground = Midnight,
            surface = surface, onSurface = Midnight,
            surfaceContainer = surface, surfaceContainerLow = background,
            surfaceContainerHigh = lerp(surface, accent, .08f), surfaceContainerHighest = lerp(surface, accent, .12f),
            surfaceVariant = lerp(background, accent, .10f), onSurfaceVariant = Color(0xFF526078),
            outline = Color(0xFF7B889B)
        ) else darkColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = lerp(accent, Color(0xFFB7A5FF), .4f), onSecondary = Midnight, secondaryContainer = lerp(surface, accent, .18f), onSecondaryContainer = FogText,
            background = background, onBackground = FogText,
            surface = surface, onSurface = FogText,
            surfaceContainer = surface, surfaceContainerLow = lerp(background, surface, .6f),
            surfaceContainerHigh = lerp(surface, accent, .06f), surfaceContainerHighest = lerp(surface, accent, .10f),
            surfaceVariant = lerp(surface, accent, .10f), onSurfaceVariant = MutedFog,
            outline = Color(0xFF526078), error = Color(0xFFFFA7AF)
        ),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-2).sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
}
