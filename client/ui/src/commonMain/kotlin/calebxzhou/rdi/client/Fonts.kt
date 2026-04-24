package calebxzhou.rdi.client

import androidx.compose.ui.text.font.FontFamily

/**
 * Font families shared across platforms.
 * Desktop: loaded from local font files
 * Android: loaded from assets/bundled fonts
 */
expect val UIFontFamily: FontFamily
expect val CodeFontFamily: FontFamily
expect val IconFontFamily: FontFamily
