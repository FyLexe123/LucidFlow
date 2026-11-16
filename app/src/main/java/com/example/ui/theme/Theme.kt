package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AuraDarkColorScheme = darkColorScheme(
    primary = GlacialCyan,
    onPrimary = MidnightAbyss,
    primaryContainer = Color(0xFF103558),
    onPrimaryContainer = Color(0xFFCFF4FF),
    secondary = AuroraViolet,
    onSecondary = MidnightAbyss,
    secondaryContainer = Color(0xFF272B66),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = BioluminescentMint,
    onTertiary = MidnightAbyss,
    background = MidnightAbyss,
    onBackground = FrostedWhite,
    surface = DeepObsidian,
    onSurface = FrostedWhite,
    surfaceVariant = TwilightIndigo,
    onSurfaceVariant = MistSlate,
    outline = Color(0x3DFFFFFF),
    error = CoralAlert,
    onError = MidnightAbyss
)

private val AuraLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = PureGlassWhite,
    primaryContainer = Color(0xFFD6EFFF),
    onPrimaryContainer = Color(0xFF002B45),
    secondary = RoyalIndigo,
    onSecondary = PureGlassWhite,
    secondaryContainer = Color(0xFFE4E6FF),
    onSecondaryContainer = Color(0xFF19155C),
    tertiary = JadeMint,
    onTertiary = PureGlassWhite,
    background = GlacierPearl,
    onBackground = ObsidianInk,
    surface = PureGlassWhite,
    onSurface = ObsidianInk,
    surfaceVariant = IceMist,
    onSurfaceVariant = SlateInk,
    outline = Color(0x2B0F172A),
    error = RoseError,
    onError = PureGlassWhite
)

@Immutable
data class LiquidGlassTokens(
    val isDark: Boolean,
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val specularHighlight: Color,
    val specularShadow: Color,
    val ambientOrbOne: Color,
    val ambientOrbTwo: Color,
    val ambientOrbThree: Color,
    val backdropStart: Color,
    val backdropEnd: Color,
    val accentSuccess: Color,
    val accentWarning: Color
)

private val DarkGlassTokens = LiquidGlassTokens(
    isDark = true,
    glassFillTop = Color(0x30FFFFFF),
    glassFillBottom = Color(0x14FFFFFF),
    specularHighlight = Color(0x66FFFFFF),
    specularShadow = Color(0x1838BDF8),
    ambientOrbOne = Color(0x550EA5E9),
    ambientOrbTwo = Color(0x4D6366F1),
    ambientOrbThree = Color(0x3D10B981),
    backdropStart = Color(0xFF040712),
    backdropEnd = Color(0xFF0B142C),
    accentSuccess = BioluminescentMint,
    accentWarning = AmberPulse
)

private val LightGlassTokens = LiquidGlassTokens(
    isDark = false,
    glassFillTop = Color(0xCCFFFFFF),
    glassFillBottom = Color(0x99F0F6FF),
    specularHighlight = Color(0xFFFFFFFF),
    specularShadow = Color(0x330284C7),
    ambientOrbOne = Color(0x5538BDF8),
    ambientOrbTwo = Color(0x44818CF8),
    ambientOrbThree = Color(0x4434D399),
    backdropStart = Color(0xFFEFF5FC),
    backdropEnd = Color(0xFFDFECFA),
    accentSuccess = JadeMint,
    accentWarning = Color(0xFFD97706)
)

val LocalLiquidGlass = staticCompositionLocalOf { DarkGlassTokens }

val AuraShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun AuraGlassTheme(
    darkModeSetting: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (darkModeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> AuraDarkColorScheme
        else -> AuraLightColorScheme
    }

    val glassTokens = if (isDark) DarkGlassTokens else LightGlassTokens

    CompositionLocalProvider(LocalLiquidGlass provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuraTypography,
            shapes = AuraShapes,
            content = content
        )
    }
}
