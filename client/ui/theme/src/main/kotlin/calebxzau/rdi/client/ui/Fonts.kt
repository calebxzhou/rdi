package calebxzau.rdi.client.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private const val UI_FONT = "assets/fonts/1oppo.ttf"
private const val CODE_FONT = "assets/fonts/jetbrainsmono.ttf"
private const val ICON_FONT = "assets/fonts/symbolsnerdfont.ttf"

private val uiFontWeights = mapOf(
    FontWeight.Normal to 400,
    FontWeight.Medium to 500,
    FontWeight.SemiBold to 600,
    FontWeight.Bold to 600
)

private val uiFonts = uiFontWeights.map { (weight, variationWeight) ->
    Font(
        resource = UI_FONT,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(variationWeight))
    )
}

private fun fontVariants(resource: String) = uiFontWeights.keys.map { Font(resource, weight = it) }

private val codeFonts = fontVariants(CODE_FONT)
private val iconFonts = fontVariants(ICON_FONT)

val UIFontFamily: FontFamily = FontFamily(uiFonts + iconFonts)
val CodeFontFamily: FontFamily = FontFamily(codeFonts + uiFonts + iconFonts)
val IconFontFamily: FontFamily = FontFamily(iconFonts)
