package calebxzau.rdi.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private fun TextStyle.withUiFontFamily() = copy(fontFamily = UIFontFamily)

val AppTypography: Typography
    get() = Typography().run {
        copy(
            displayLarge = displayLarge.withUiFontFamily(),
            displayMedium = displayMedium.withUiFontFamily(),
            displaySmall = displaySmall.withUiFontFamily(),
            headlineLarge = headlineLarge.withUiFontFamily(),
            headlineMedium = headlineMedium.withUiFontFamily(),
            headlineSmall = headlineSmall.withUiFontFamily(),
            titleLarge = titleLarge.withUiFontFamily(),
            titleMedium = titleMedium.withUiFontFamily(),
            titleSmall = titleSmall.withUiFontFamily(),
            bodyLarge = bodyLarge.withUiFontFamily(),
            bodyMedium = bodyMedium.withUiFontFamily(),
            bodySmall = bodySmall.withUiFontFamily(),
            labelLarge = labelLarge.withUiFontFamily(),
            labelMedium = labelMedium.withUiFontFamily(),
            labelSmall = labelSmall.withUiFontFamily()
        )
    }

private val RdiColorScheme = lightColorScheme(
    primary = Color(0xFF386A1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F397),
    onPrimaryContainer = Color(0xFF072100),
    inversePrimary = Color(0xFF9DD67D),
    secondary = Color(0xFF55624C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386666),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBEC),
    onTertiaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF0F3E8),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFF8FAF0),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE1E3DA),
    onSurfaceVariant = Color(0xFF43483E),
    surfaceTint = Color(0xFF386A1F),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF1F1EA),
    surfaceDim = Color(0xFFD9DBD1),
    surfaceBright = Color(0xFFF8FAF0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5EB),
    surfaceContainer = Color(0xFFEDEFE5),
    surfaceContainerHigh = Color(0xFFE7E9DF),
    surfaceContainerHighest = Color(0xFFE1E3DA),
    outline = Color(0xFF74796D),
    outlineVariant = Color(0xFFC3C8BB),
    scrim = Color(0xFF000000),
    primaryFixed = Color(0xFFB8F397),
    primaryFixedDim = Color(0xFF9DD67D),
    onPrimaryFixed = Color(0xFF072100),
    onPrimaryFixedVariant = Color(0xFF205106),
    secondaryFixed = Color(0xFFD9E7CB),
    secondaryFixedDim = Color(0xFFBDCBB0),
    onSecondaryFixed = Color(0xFF131F0D),
    onSecondaryFixedVariant = Color(0xFF3E4A35),
    tertiaryFixed = Color(0xFFBBEBEC),
    tertiaryFixedDim = Color(0xFFA0CFD0),
    onTertiaryFixed = Color(0xFF002020),
    onTertiaryFixedVariant = Color(0xFF1E4E4E)
)

private val RdiShapes = Shapes(
    extraSmall = baseRoundCornerShape
)

@Composable
fun RTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RdiColorScheme,
        typography = AppTypography,
        shapes = RdiShapes,
        content = content
    )
}
val themeNow @Composable
get() = MaterialTheme.colorScheme