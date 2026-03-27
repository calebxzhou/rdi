package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.CodeFontFamily
import calebxzhou.rdi.client.ui.MaterialColor
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import net.peanuuutz.tomlkt.Toml
import kotlin.math.max
import kotlin.math.min

private const val HISTORY_LIMIT = 200
private const val TAB_SPACES = "    "

private val editorTextStyle = TextStyle(
    fontFamily = CodeFontFamily,
    color = MaterialColor.GRAY_900.color,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

private val gutterTextStyle = editorTextStyle.copy(
    color = MaterialColor.GRAY_600.color,
    textAlign = TextAlign.End
)

private val statusTextStyle = TextStyle(
    fontFamily = CodeFontFamily,
    color = MaterialColor.BLUE_GRAY_700.color,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

private val punctuationStyle = SpanStyle(color = MaterialColor.BLUE_GRAY_500.color)
private val jsonKeyStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val jsonStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val jsonNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val jsonLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlTableStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val tomlKeyStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.SemiBold)
private val tomlStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val tomlNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val tomlLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)

private val strictJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    allowTrailingComma = false
    allowComments = false
}

private val syntaxCheckToml = Toml {
    ignoreUnknownKeys = true
}

enum class CodeLanguage(val label: String) {
    PLAIN_TEXT("文本"),
    JSON("JSON"),
    JSON5("JSON5"),
    TOML("TOML");

    companion object {
        fun fromPath(path: String?): CodeLanguage = when {
            path.isNullOrBlank() -> PLAIN_TEXT
            path.endsWith(".json5", ignoreCase = true) -> JSON5
            path.endsWith(".json", ignoreCase = true) -> JSON
            path.endsWith(".toml", ignoreCase = true) -> TOML
            else -> PLAIN_TEXT
        }
    }
}

data class CodeEditorValidation(
    val language: CodeLanguage,
    val isValid: Boolean,
    val message: String
)

private data class HistoryEntry(
    val text: String,
    val selection: TextRange
)

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun CodeEditor(
    text: String,
    enabled: Boolean,
    language: CodeLanguage = CodeLanguage.PLAIN_TEXT,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onValidationChange: (CodeEditorValidation?) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val onValidationChangeState by rememberUpdatedState(onValidationChange)
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lineHeightPx = with(density) { editorTextStyle.lineHeight.roundToPx().coerceAtLeast(1) }

    var editorValue by remember {
        mutableStateOf(buildEditorValue(text, TextRange(text.length), language))
    }
    var history by remember {
        mutableStateOf(listOf(HistoryEntry(text, TextRange(text.length))))
    }
    var historyIndex by remember { mutableStateOf(0) }
    var lastDispatchedText by remember { mutableStateOf(text) }
    var viewportHeightPx by remember { mutableStateOf(0) }

    val lineCount = remember(editorValue.text) {
        editorValue.text.count { it == '\n' } + 1
    }
    val lineNumberText = remember(lineCount) {
        buildString {
            for (index in 1..lineCount) {
                append(index)
                if (index != lineCount) append('\n')
            }
        }
    }
    val gutterWidth = remember(lineCount) {
        ((max(2, lineCount.toString().length) * 10) + 18).dp
    }
    val validation = remember(editorValue.text, language) {
        validateCodeContent(editorValue.text, language)
    }
    val canUndo = historyIndex > 0
    val canRedo = historyIndex < history.lastIndex
    val pageLineCount = max(1, viewportHeightPx / lineHeightPx)

    fun updateEditor(
        newText: String,
        selection: TextRange,
        recordHistory: Boolean,
        dispatchChange: Boolean,
        composition: TextRange? = null
    ) {
        val safeSelection = selection.coerceToText(newText.length)
        editorValue = buildEditorValue(
            text = newText,
            selection = safeSelection,
            language = language,
            composition = composition
        )

        if (recordHistory) {
            val baseEntries = history.take(historyIndex + 1)
            val lastEntry = baseEntries.lastOrNull()
            val nextEntries = when {
                lastEntry == null -> listOf(HistoryEntry(newText, safeSelection))
                lastEntry.text == newText -> {
                    baseEntries.dropLast(1) + HistoryEntry(newText, safeSelection)
                }
                else -> {
                    (baseEntries + HistoryEntry(newText, safeSelection)).takeLast(HISTORY_LIMIT)
                }
            }
            history = nextEntries
            historyIndex = nextEntries.lastIndex
        }

        if (dispatchChange && lastDispatchedText != newText) {
            lastDispatchedText = newText
            onValueChangeState(newText)
        }
    }

    fun replaceSelection(replacement: String) {
        val current = editorValue
        val start = min(current.selection.start, current.selection.end)
        val end = max(current.selection.start, current.selection.end)
        val nextText = buildString(current.text.length - (end - start) + replacement.length) {
            append(current.text, 0, start)
            append(replacement)
            append(current.text, end, current.text.length)
        }
        val nextCaret = start + replacement.length
        updateEditor(
            newText = nextText,
            selection = TextRange(nextCaret),
            recordHistory = true,
            dispatchChange = true
        )
    }

    fun copySelection(): Boolean {
        if (editorValue.selection.collapsed) return false
        clipboardManager.setText(AnnotatedString(editorValue.selectedText))
        return true
    }

    fun cutSelection(): Boolean {
        if (!enabled || editorValue.selection.collapsed) return false
        clipboardManager.setText(AnnotatedString(editorValue.selectedText))
        replaceSelection("")
        return true
    }

    fun pasteClipboard(): Boolean {
        if (!enabled) return false
        val clipboardText = clipboardManager.getText()?.text ?: return false
        replaceSelection(clipboardText)
        return true
    }

    fun applyHistory(targetIndex: Int): Boolean {
        val entry = history.getOrNull(targetIndex) ?: return false
        historyIndex = targetIndex
        editorValue = buildEditorValue(entry.text, entry.selection, language)
        if (lastDispatchedText != entry.text) {
            lastDispatchedText = entry.text
            onValueChangeState(entry.text)
        }
        return true
    }

    fun moveCaret(target: Int, extendSelection: Boolean) {
        val clampedTarget = target.coerceIn(0, editorValue.text.length)
        val selection = if (extendSelection) {
            TextRange(editorValue.selection.start, clampedTarget)
        } else {
            TextRange(clampedTarget)
        }
        editorValue = buildEditorValue(
            text = editorValue.text,
            selection = selection,
            language = language,
            composition = editorValue.composition
        )
    }

    fun scrollByPage(direction: Int) {
        if (viewportHeightPx <= 0) return
        scope.launch {
            val nextValue = (verticalScroll.value + direction * viewportHeightPx)
                .coerceIn(0, verticalScroll.maxValue)
            verticalScroll.scrollTo(nextValue)
        }
    }

    LaunchedEffect(text) {
        if (text != editorValue.text && text != lastDispatchedText) {
            val selection = TextRange(text.length)
            editorValue = buildEditorValue(text, selection, language)
            history = listOf(HistoryEntry(text, selection))
            historyIndex = 0
            lastDispatchedText = text
        }
    }

    LaunchedEffect(language) {
        val selection = editorValue.selection.coerceToText(editorValue.text.length)
        editorValue = buildEditorValue(editorValue.text, selection, language, editorValue.composition)
        history = listOf(HistoryEntry(editorValue.text, selection))
        historyIndex = 0
        lastDispatchedText = editorValue.text
    }

    LaunchedEffect(validation) {
        onValidationChangeState(validation)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialColor.GRAY_100.color)
                .height(16.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = language.label,
                color = MaterialColor.BLUE_GRAY_700.color,
                style = statusTextStyle,
                fontWeight = FontWeight.SemiBold
            )
            validation?.let {
                Text(
                    text = it.message,
                    color = if (it.isValid) MaterialColor.GREEN_800.color else MaterialColor.RED_800.color,
                    style = statusTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } ?: Spacer(modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialColor.GRAY_50.color)
                .onSizeChanged { viewportHeightPx = it.height }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .verticalScroll(verticalScroll)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        val extendSelection = event.isShiftPressed
                        val caret = editorValue.selection.end
                        val shortcutPressed = event.isCtrlPressed || event.isMetaPressed

                        when (event.key) {
                            Key.MoveHome -> {
                                val target = if (shortcutPressed) {
                                    0
                                } else {
                                    lineStartOffset(editorValue.text, caret)
                                }
                                moveCaret(target, extendSelection)
                                if (shortcutPressed) {
                                    scope.launch { verticalScroll.scrollTo(0) }
                                }
                                return@onPreviewKeyEvent true
                            }

                            Key.MoveEnd -> {
                                val target = if (shortcutPressed) {
                                    editorValue.text.length
                                } else {
                                    lineEndOffset(editorValue.text, caret)
                                }
                                moveCaret(target, extendSelection)
                                if (shortcutPressed) {
                                    scope.launch { verticalScroll.scrollTo(verticalScroll.maxValue) }
                                }
                                return@onPreviewKeyEvent true
                            }

                            Key.PageUp -> {
                                moveCaret(moveCaretByLines(editorValue.text, caret, -pageLineCount), extendSelection)
                                scrollByPage(-1)
                                return@onPreviewKeyEvent true
                            }

                            Key.PageDown -> {
                                moveCaret(moveCaretByLines(editorValue.text, caret, pageLineCount), extendSelection)
                                scrollByPage(1)
                                return@onPreviewKeyEvent true
                            }
                        }

                        if (event.key == Key.Tab && enabled) {
                            replaceSelection(TAB_SPACES)
                            return@onPreviewKeyEvent true
                        }

                        if (!shortcutPressed) return@onPreviewKeyEvent false

                        when (event.key) {
                            Key.Z -> {
                                if (event.isShiftPressed) {
                                    enabled && canRedo && applyHistory(historyIndex + 1)
                                } else {
                                    enabled && canUndo && applyHistory(historyIndex - 1)
                                }
                            }

                            Key.Y -> enabled && canRedo && applyHistory(historyIndex + 1)
                            Key.C -> copySelection()
                            Key.X -> cutSelection()
                            Key.V -> pasteClipboard()
                            else -> false
                        }
                    }
            ) {
                Text(
                    text = lineNumberText,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(gutterWidth)
                        .background(MaterialColor.GRAY_100.color)
                        .padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    style = gutterTextStyle
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialColor.GRAY_200.color)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            BasicTextField(
                                value = editorValue,
                                onValueChange = { nextValue ->
                                    val previousText = editorValue.text
                                    val safeSelection = nextValue.selection.coerceToText(nextValue.text.length)
                                    if (nextValue.text != previousText) {
                                        updateEditor(
                                            newText = nextValue.text,
                                            selection = safeSelection,
                                            recordHistory = true,
                                            dispatchChange = true,
                                            composition = nextValue.composition
                                        )
                                    } else {
                                        editorValue = buildEditorValue(
                                            text = nextValue.text,
                                            selection = safeSelection,
                                            language = language,
                                            composition = nextValue.composition
                                        )
                                    }
                                },
                                enabled = enabled,
                                textStyle = editorTextStyle,
                                cursorBrush = SolidColor(MaterialColor.BLUE_700.color),
                                modifier = Modifier
                                    .widthIn(min = 10.dp)
                                    .padding(bottom = 10.dp)
                            )
                            if (editorValue.text.endsWith('\n')) {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(12.dp)
            ) {
                PlatformVerticalScrollbar(
                    scrollState = verticalScroll,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

private fun buildEditorValue(
    text: String,
    selection: TextRange,
    language: CodeLanguage,
    composition: TextRange? = null
): TextFieldValue {
    return TextFieldValue(
        annotatedString = highlightCode(text, language),
        selection = selection.coerceToText(text.length),
        composition = composition?.coerceToText(text.length)
    )
}

private fun highlightCode(text: String, language: CodeLanguage): AnnotatedString = buildAnnotatedString {
    append(text)
    when (language) {
        CodeLanguage.JSON,
        CodeLanguage.JSON5 -> applyJsonHighlight(text)
        CodeLanguage.TOML -> applyTomlHighlight(text)
        CodeLanguage.PLAIN_TEXT -> Unit
    }
}

private fun AnnotatedString.Builder.applyJsonHighlight(text: String) {
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '"' -> {
                val end = scanQuotedText(text, index, '"')
                val nextTokenStart = skipWhitespace(text, end)
                val style = if (nextTokenStart < text.length && text[nextTokenStart] == ':') {
                    jsonKeyStyle
                } else {
                    jsonStringStyle
                }
                safeAddStyle(style, index, end)
                index = end
            }

            '{', '}', '[', ']', ':', ',' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }

            else -> {
                val numberMatch = jsonNumberRegex.find(text, index)
                if (numberMatch != null && numberMatch.range.first == index) {
                    safeAddStyle(jsonNumberStyle, index, numberMatch.range.last + 1)
                    index = numberMatch.range.last + 1
                    continue
                }
                val literalMatch = jsonLiteralRegex.find(text, index)
                if (literalMatch != null && literalMatch.range.first == index) {
                    safeAddStyle(jsonLiteralStyle, index, literalMatch.range.last + 1)
                    index = literalMatch.range.last + 1
                    continue
                }
                index++
            }
        }
    }
}

private fun AnnotatedString.Builder.applyTomlHighlight(text: String) {
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        applyTomlLineHighlight(text, lineStart, lineEnd)
        if (lineEnd == text.length) {
            break
        }
        lineStart = lineEnd + 1
    }
}

private fun AnnotatedString.Builder.applyTomlLineHighlight(
    text: String,
    start: Int,
    end: Int
) {
    if (start >= end) return

    val commentStart = findTomlCommentStart(text, start, end)
    val codeEnd = commentStart ?: end
    if (commentStart != null) {
        safeAddStyle(tomlCommentStyle, commentStart, end)
    }

    val contentStart = skipWhitespace(text, start, codeEnd)
    if (contentStart >= codeEnd) return

    if (text[contentStart] == '[') {
        safeAddStyle(tomlTableStyle, contentStart, codeEnd)
        return
    }

    val equalsIndex = findTomlEquals(text, contentStart, codeEnd)
    if (equalsIndex != -1) {
        safeAddStyle(tomlKeyStyle, contentStart, equalsIndex)
        safeAddStyle(punctuationStyle, equalsIndex, equalsIndex + 1)
        applyTomlValueHighlight(text, equalsIndex + 1, codeEnd)
    } else {
        applyTomlValueHighlight(text, contentStart, codeEnd)
    }
}

private fun AnnotatedString.Builder.applyTomlValueHighlight(
    text: String,
    start: Int,
    end: Int
) {
    var index = start
    while (index < end) {
        when (text[index]) {
            '"', '\'' -> {
                val quote = text[index]
                val quoteLength = detectTripleQuote(text, index, end, quote)
                val closeIndex = scanTomlString(text, index, end, quote, quoteLength)
                safeAddStyle(tomlStringStyle, index, closeIndex)
                index = closeIndex
            }

            '[', ']', '{', '}', ',', '.' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }

            '#', ' ', '\t' -> index++
            else -> {
                val tokenEnd = scanTomlBareToken(text, index, end)
                val token = text.substring(index, tokenEnd)
                when {
                    token == "true" || token == "false" -> {
                        safeAddStyle(tomlLiteralStyle, index, tokenEnd)
                    }

                    tomlNumberRegex.matches(token) || tomlDateTimeRegex.matches(token) -> {
                        safeAddStyle(tomlNumberStyle, index, tokenEnd)
                    }
                }
                index = tokenEnd
            }
        }
    }
}

fun validateCodeContent(text: String, language: CodeLanguage): CodeEditorValidation? = when (language) {
    CodeLanguage.JSON -> runCatching {
        strictJson.parseToJsonElement(text)
    }.fold(
        onSuccess = {
            CodeEditorValidation(language, true, "JSON语法正确")
        },
        onFailure = {
            CodeEditorValidation(language, false, "JSON语法错误: ${it.shortMessage}")
        }
    )

    CodeLanguage.JSON5 -> runCatching {
        strictJson.parseToJsonElement(normalizeJson5ToJson(text))
    }.fold(
        onSuccess = {
            CodeEditorValidation(language, true, "JSON5语法正确")
        },
        onFailure = {
            CodeEditorValidation(language, false, "JSON5语法错误: ${it.shortMessage}")
        }
    )

    CodeLanguage.TOML -> runCatching {
        syntaxCheckToml.parseToTomlTable(text)
    }.fold(
        onSuccess = {
            CodeEditorValidation(language, true, "TOML语法正确")
        },
        onFailure = {
            CodeEditorValidation(language, false, "TOML语法错误: ${it.shortMessage}")
        }
    )

    CodeLanguage.PLAIN_TEXT -> null
}

private val TextFieldValue.selectedText: String
    get() {
        if (selection.collapsed) return ""
        val start = min(selection.start, selection.end)
        val end = max(selection.start, selection.end)
        return text.substring(start, end)
    }

private fun TextRange.coerceToText(length: Int): TextRange {
    return TextRange(
        start = start.coerceIn(0, length),
        end = end.coerceIn(0, length)
    )
}

private fun lineStartOffset(text: String, offset: Int): Int {
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

private fun moveCaretByLines(text: String, offset: Int, lineDelta: Int): Int {
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

private fun scanQuotedText(text: String, start: Int, quote: Char): Int {
    var index = start + 1
    while (index < text.length) {
        when {
            text[index] == '\\' -> index += 2
            text[index] == quote -> return index + 1
            else -> index++
        }
    }
    return text.length
}

private fun findTomlCommentStart(text: String, start: Int, end: Int): Int? {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    while (index < end) {
        when {
            !inSingleQuote && text[index] == '"' && !text.isEscaped(index) -> {
                inDoubleQuote = !inDoubleQuote
            }

            !inDoubleQuote && text[index] == '\'' -> {
                inSingleQuote = !inSingleQuote
            }

            !inDoubleQuote && !inSingleQuote && text[index] == '#' -> return index
        }
        index++
    }
    return null
}

private fun findTomlEquals(text: String, start: Int, end: Int): Int {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    while (index < end) {
        when {
            !inSingleQuote && text[index] == '"' && !text.isEscaped(index) -> {
                inDoubleQuote = !inDoubleQuote
            }

            !inDoubleQuote && text[index] == '\'' -> {
                inSingleQuote = !inSingleQuote
            }

            !inDoubleQuote && !inSingleQuote && text[index] == '=' -> return index
        }
        index++
    }
    return -1
}

private fun detectTripleQuote(text: String, start: Int, end: Int, quote: Char): Int {
    return if (start + 2 < end && text[start + 1] == quote && text[start + 2] == quote) {
        3
    } else {
        1
    }
}

private fun scanTomlString(
    text: String,
    start: Int,
    end: Int,
    quote: Char,
    quoteLength: Int
): Int {
    var index = start + quoteLength
    while (index < end) {
        if (quoteLength == 3) {
            if (index + 2 < end && text[index] == quote && text[index + 1] == quote && text[index + 2] == quote) {
                return index + 3
            }
            index++
            continue
        }

        when {
            quote == '"' && text[index] == '\\' -> index += 2
            text[index] == quote -> return index + 1
            else -> index++
        }
    }
    return end
}

private fun scanTomlBareToken(text: String, start: Int, end: Int): Int {
    var index = start
    while (index < end && text[index] !in tomlTokenBreakChars) {
        index++
    }
    return index
}

private fun skipWhitespace(text: String, start: Int, end: Int = text.length): Int {
    var index = start
    while (index < end && text[index].isWhitespace()) {
        index++
    }
    return index
}

private fun String.isEscaped(index: Int): Boolean {
    var slashCount = 0
    var current = index - 1
    while (current >= 0 && this[current] == '\\') {
        slashCount++
        current--
    }
    return slashCount % 2 == 1
}

private fun AnnotatedString.Builder.safeAddStyle(style: SpanStyle, start: Int, end: Int) {
    if (start in 0 until end) {
        addStyle(style, start, end)
    }
}

private val Throwable.shortMessage: String
    get() = message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.take(120)
        ?: "未知错误"

private fun normalizeJson5ToJson(text: String): String = Json5Normalizer(text).normalize()

private class Json5Normalizer(private val source: String) {
    private var index = 0

    fun normalize(): String {
        skipTrivia()
        val normalized = parseValue()
        skipTrivia()
        if (index != source.length) {
            fail("多余内容")
        }
        return normalized
    }

    private fun parseValue(): String {
        skipTrivia()
        if (index >= source.length) fail("缺少值")
        return when (val ch = source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"', '\'' -> parseString(ch)
            '+', '-', '.' -> parseNumberOrIdentifier()
            in '0'..'9' -> parseNumberOrIdentifier()
            else -> {
                if (isIdentifierStart(ch)) {
                    parseIdentifierValue()
                } else {
                    fail("无效字符 ${ch}")
                }
            }
        }
    }

    private fun parseObject(): String {
        expect('{')
        val builder = StringBuilder()
        builder.append('{')
        skipTrivia()
        var first = true
        while (!consumeIf('}')) {
            if (!first) {
                builder.append(',')
            }
            first = false
            builder.append(parseObjectKey())
            skipTrivia()
            expect(':')
            builder.append(':')
            builder.append(parseValue())
            skipTrivia()
            if (consumeIf(',')) {
                skipTrivia()
                if (consumeIf('}')) {
                    builder.append('}')
                    return builder.toString()
                }
            } else {
                expect('}')
                builder.append('}')
                return builder.toString()
            }
        }
        builder.append('}')
        return builder.toString()
    }

    private fun parseArray(): String {
        expect('[')
        val builder = StringBuilder()
        builder.append('[')
        skipTrivia()
        var first = true
        while (!consumeIf(']')) {
            if (!first) {
                builder.append(',')
            }
            first = false
            builder.append(parseValue())
            skipTrivia()
            if (consumeIf(',')) {
                skipTrivia()
                if (consumeIf(']')) {
                    builder.append(']')
                    return builder.toString()
                }
            } else {
                expect(']')
                builder.append(']')
                return builder.toString()
            }
        }
        builder.append(']')
        return builder.toString()
    }

    private fun parseObjectKey(): String {
        skipTrivia()
        if (index >= source.length) fail("缺少对象键")
        return when (val ch = source[index]) {
            '"', '\'' -> parseString(ch)
            else -> {
                if (!isIdentifierStart(ch)) fail("无效对象键")
                quoteJsonString(readIdentifier())
            }
        }
    }

    private fun parseIdentifierValue(): String {
        val identifier = readIdentifier()
        return when (identifier) {
            "true", "false", "null" -> identifier
            "Infinity", "+Infinity", "-Infinity", "NaN", "+NaN", "-NaN" -> "0"
            else -> fail("无效标识符 $identifier")
        }
    }

    private fun parseNumberOrIdentifier(): String {
        val token = readToken()
        return canonicalizeJson5Token(token)
    }

    private fun parseString(quote: Char): String {
        expect(quote)
        val content = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                quote -> return quoteJsonString(content.toString())
                '\\' -> content.append(parseEscape())
                '\r', '\n' -> fail("字符串缺少结束引号")
                else -> content.append(ch)
            }
        }
        fail("字符串缺少结束引号")
    }

    private fun parseEscape(): String {
        if (index >= source.length) fail("无效转义")
        val escaped = source[index++]
        return when (escaped) {
            '\r' -> {
                if (index < source.length && source[index] == '\n') index++
                ""
            }
            '\n' -> ""
            '\'', '"', '\\', '/' -> escaped.toString()
            'b' -> "\b"
            'f' -> "\u000C"
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            'v' -> "\u000B"
            '0' -> {
                if (index < source.length && source[index].isDigit()) {
                    fail("不支持八进制转义")
                }
                "\u0000"
            }
            'x' -> readHexEscape(2)
            'u' -> readHexEscape(4)
            else -> escaped.toString()
        }
    }

    private fun readHexEscape(length: Int): String {
        if (index + length > source.length) fail("无效Unicode转义")
        val hex = source.substring(index, index + length)
        if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            fail("无效Unicode转义")
        }
        index += length
        return hex.toInt(16).toChar().toString()
    }

    private fun readIdentifier(): String {
        val start = index
        index++
        while (index < source.length && isIdentifierPart(source[index])) {
            index++
        }
        return source.substring(start, index)
    }

    private fun readToken(): String {
        val start = index
        while (index < source.length) {
            val ch = source[index]
            if (ch.isWhitespace() || ch == ',' || ch == ']' || ch == '}') {
                break
            }
            if (ch == '/' && index + 1 < source.length) {
                val next = source[index + 1]
                if (next == '/' || next == '*') {
                    break
                }
            }
            index++
        }
        return source.substring(start, index)
    }

    private fun canonicalizeJson5Token(token: String): String {
        if (token.isBlank()) fail("缺少值")
        return when (token) {
            "true", "false", "null" -> token
            "Infinity", "+Infinity", "-Infinity", "NaN", "+NaN", "-NaN" -> "0"
            else -> canonicalizeJson5Number(token)
        }
    }

    private fun canonicalizeJson5Number(token: String): String {
        val normalized = when {
            token.startsWith("+0x", ignoreCase = true) || token.startsWith("-0x", ignoreCase = true) || token.startsWith("0x", ignoreCase = true) -> {
                val sign = if (token.startsWith('-')) "-" else ""
                val digits = token.removePrefix("+").removePrefix("-").removePrefix("0x").removePrefix("0X")
                if (digits.isEmpty() || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    fail("无效十六进制数字 $token")
                }
                val decimal = digits.toLongOrNull(16) ?: fail("十六进制数字过大 $token")
                sign + decimal.toString()
            }
            else -> token.removePrefix("+")
                .replace(Regex("^\\."), "0.")
                .replace(Regex("^-\\."), "-0.")
                .replace(Regex("\\.$"), ".0")
        }
        if (!jsonNumberRegex.matches(normalized)) {
            fail("无效数字 $token")
        }
        return normalized
    }

    private fun skipTrivia() {
        while (index < source.length) {
            when (source[index]) {
                ' ', '\t', '\r', '\n' -> index++
                '/' -> {
                    if (index + 1 >= source.length) return
                    when (source[index + 1]) {
                        '/' -> {
                            index += 2
                            while (index < source.length && source[index] != '\r' && source[index] != '\n') {
                                index++
                            }
                        }
                        '*' -> {
                            index += 2
                            while (index + 1 < source.length && !(source[index] == '*' && source[index + 1] == '/')) {
                                index++
                            }
                            if (index + 1 >= source.length) fail("注释未闭合")
                            index += 2
                        }
                        else -> return
                    }
                }
                else -> return
            }
        }
    }

    private fun expect(ch: Char) {
        skipTrivia()
        if (!consumeIf(ch)) {
            fail("缺少字符 $ch")
        }
    }

    private fun consumeIf(ch: Char): Boolean {
        if (index < source.length && source[index] == ch) {
            index++
            return true
        }
        return false
    }

    private fun isIdentifierStart(ch: Char): Boolean =
        ch == '_' || ch == '$' || ch.isLetter()

    private fun isIdentifierPart(ch: Char): Boolean =
        isIdentifierStart(ch) || ch.isDigit()

    private fun fail(message: String): Nothing {
        throw IllegalArgumentException("${message}，位置${index + 1}")
    }
}

private fun quoteJsonString(text: String): String = buildString(text.length + 2) {
    append('"')
    text.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch < ' ') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

private val jsonNumberRegex = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
private val jsonLiteralRegex = Regex("(?:true|false|null)\\b")
private val tomlNumberRegex = Regex("[+-]?(?:\\d(?:[\\d_]*\\d)?)(?:\\.\\d(?:[\\d_]*\\d)?)?(?:[eE][+-]?\\d(?:[\\d_]*\\d)?)?")
private val tomlDateTimeRegex = Regex("\\d{4}-\\d{2}-\\d{2}(?:[Tt ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)?(?:[Zz]|[+-]\\d{2}:\\d{2})?")
private val tomlTokenBreakChars = charArrayOf(' ', '\t', ',', ']', '}', '#')
