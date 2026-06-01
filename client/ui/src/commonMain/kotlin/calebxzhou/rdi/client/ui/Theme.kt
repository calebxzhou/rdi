package calebxzhou.rdi.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import calebxzhou.rdi.client.UIFontFamily

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
    background = Color.White,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    /* surfaceDim = Color.White,
     surfaceBright = Color.White,
     surfaceContainerLowest = Color.White,
     surfaceContainerLow = Color.White,
     surfaceContainer = Color.White,
     surfaceContainerHigh = Color(0xFFF8F8F8),
     surfaceContainerHighest = Color(0xFFF5F5F5),
     surfaceVariant = Color(0xFFF5F5F5),
     onSurfaceVariant = Color(0xFF5F6368),
     primary = Color(0xFF4F46E5),
     onPrimary = Color.White,
     primaryContainer = Color(0xFFE0E7FF),
     onPrimaryContainer = Color(0xFF1E1B4B),
     secondary = Color(0xFF2563EB),
     onSecondary = Color.White,
     secondaryContainer = Color(0xFFDBEAFE),
     onSecondaryContainer = Color(0xFF172554),
     tertiary = Color(0xFF047857),
     onTertiary = Color.White,
     tertiaryContainer = Color(0xFFD1FAE5),
     onTertiaryContainer = Color(0xFF064E3B),
     error = Color(0xFFD64545),
     onError = Color.White,
     outline = Color(0xFFC7CDD4),
     outlineVariant = Color(0xFFE2E5EA)*/
)

@Composable
fun RdiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RdiColorScheme,
        typography = AppTypography,
        content = content
    )
}
