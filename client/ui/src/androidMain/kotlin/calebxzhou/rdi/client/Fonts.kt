package calebxzhou.rdi.client

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

private val oppoFont = Font(R.font.oppo)
private val codeFont = Font(R.font.jetbrainsmono)
private val iconFont = Font(R.font.symbolsnerdfont)

actual val UIFontFamily: FontFamily = FontFamily(oppoFont, iconFont)
actual val CodeFontFamily: FontFamily = FontFamily(codeFont, oppoFont, iconFont)
actual val IconFontFamily: FontFamily = FontFamily(iconFont)
