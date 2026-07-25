package calebxzau.rdi.client.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import calebxzhou.rdi.RDIClient

private val fontDir = RDIClient.DIR.resolve("run").resolve("fonts").takeIf { it.exists() }
    ?: RDIClient.DIR.resolve("fonts")

private val uiFont = fontDir.resolve("1oppo.ttf")
private val codeFont = fontDir.resolve("jetbrainsmono.ttf")
private val iconFont = fontDir.resolve("symbolsnerdfont.ttf")

val UIFontFamily: FontFamily = FontFamily(listOf(uiFont, iconFont).map { Font(it) })
val CodeFontFamily: FontFamily = FontFamily(listOf(codeFont, uiFont, iconFont).map { Font(it) })
val IconFontFamily: FontFamily = FontFamily(listOf(iconFont).map { Font(it) })
