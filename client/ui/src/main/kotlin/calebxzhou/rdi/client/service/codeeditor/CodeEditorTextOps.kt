package calebxzhou.rdi.client.service.codeeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.min

val TextFieldValue.selectedText: String
    get() {
        if (selection.collapsed) return ""
        val start = min(selection.start, selection.end)
        val end = kotlin.math.max(selection.start, selection.end)
        return text.substring(start, end)
    }

fun TextRange.coerceToText(length: Int): TextRange {
    return TextRange(
        start = start.coerceIn(0, length),
        end = end.coerceIn(0, length)
    )
}

fun lineStartOffset(text: String, offset: Int): Int {
    if (text.isEmpty()) return 0
    val clampedOffset = offset.coerceIn(0, text.length)
    if (clampedOffset == 0) return 0
    val searchFrom = (clampedOffset - 1).coerceAtLeast(0)
    val previousNewline = text.lastIndexOf('\n', searchFrom)
    return if (previousNewline == -1) 0 else previousNewline + 1
}

private fun lineEndOffset(text: String, offset: Int): Int {
    if (text.isEmpty()) return 0
    val clampedOffset = offset.coerceIn(0, text.length)
    val nextNewline = text.indexOf('\n', clampedOffset)
    return if (nextNewline == -1) text.length else nextNewline
}

fun caretLineEndOffset(text: String, offset: Int): Int {
    val lineEnd = lineEndOffset(text, offset)
    return if (lineEnd > 0 && text.getOrNull(lineEnd - 1) == '\r') lineEnd - 1 else lineEnd
}

fun moveCaretByLines(text: String, offset: Int, lineDelta: Int): Int {
    if (text.isEmpty() || lineDelta == 0) return offset.coerceIn(0, text.length)

    val clampedOffset = offset.coerceIn(0, text.length)
    val currentLineStart = lineStartOffset(text, clampedOffset)
    val currentColumn = clampedOffset - currentLineStart
    var targetLineStart = currentLineStart

    if (lineDelta > 0) {
        repeat(lineDelta) {
            val currentLineEnd = lineEndOffset(text, targetLineStart)
            if (currentLineEnd >= text.length) {
                targetLineStart = text.length
                return@repeat
            }
            targetLineStart = currentLineEnd + 1
        }
    } else {
        repeat(-lineDelta) {
            if (targetLineStart <= 0) {
                targetLineStart = 0
                return@repeat
            }
            targetLineStart = lineStartOffset(text, targetLineStart - 1)
        }
    }

    if (targetLineStart >= text.length) return text.length

    val targetLineEnd = lineEndOffset(text, targetLineStart)
    return min(targetLineStart + currentColumn, targetLineEnd)
}
