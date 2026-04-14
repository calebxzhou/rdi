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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.CodeFontFamily
import calebxzhou.rdi.client.service.codeeditor.CodeEditorValidation
import calebxzhou.rdi.client.service.codeeditor.CodeLanguage
import calebxzhou.rdi.client.service.codeeditor.buildEditorValue
import calebxzhou.rdi.client.service.codeeditor.caretLineEndOffset
import calebxzhou.rdi.client.service.codeeditor.coerceToText
import calebxzhou.rdi.client.service.codeeditor.lineStartOffset
import calebxzhou.rdi.client.service.codeeditor.moveCaretByLines
import calebxzhou.rdi.client.service.codeeditor.selectedText
import calebxzhou.rdi.client.service.codeeditor.validateCodeContent
import calebxzhou.rdi.client.ui.MaterialColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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

private data class HistoryEntry(
    val text: String,
    val selection: TextRange
)

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

    val lineCount = remember(editorValue.text) { editorValue.text.count { it == '\n' } + 1 }
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
                lastEntry.text == newText -> baseEntries.dropLast(1) + HistoryEntry(newText, safeSelection)
                else -> (baseEntries + HistoryEntry(newText, safeSelection)).takeLast(HISTORY_LIMIT)
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
        updateEditor(
            newText = nextText,
            selection = TextRange(start + replacement.length),
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
            text = editorValue.annotatedString.text,
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

        fun handleEditorKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
            if (event.type != KeyEventType.KeyDown) return false
            val extendSelection = event.isShiftPressed
            val caret = editorValue.selection.end
            val shortcutPressed = event.isCtrlPressed || event.isMetaPressed

            when (event.key) {
                Key.MoveHome -> {
                    val target = if (shortcutPressed) 0 else lineStartOffset(editorValue.text, caret)
                    moveCaret(target, extendSelection)
                    scope.launch {
                        yield()
                        moveCaret(target, extendSelection)
                    }
                    if (shortcutPressed) {
                        scope.launch { verticalScroll.scrollTo(0) }
                    }
                    return true
                }
                Key.MoveEnd -> {
                    val target = if (shortcutPressed) editorValue.text.length else caretLineEndOffset(editorValue.text, caret)
                    moveCaret(target, extendSelection)
                    scope.launch {
                        yield()
                        moveCaret(target, extendSelection)
                    }
                    if (shortcutPressed) {
                        scope.launch { verticalScroll.scrollTo(verticalScroll.maxValue) }
                    }
                    return true
                }
                Key.PageUp -> {
                    moveCaret(moveCaretByLines(editorValue.text, caret, -pageLineCount), extendSelection)
                    scrollByPage(-1)
                    return true
                }
                Key.PageDown -> {
                    moveCaret(moveCaretByLines(editorValue.text, caret, pageLineCount), extendSelection)
                    scrollByPage(1)
                    return true
                }
            }

            if (event.key == Key.Tab && enabled) {
                replaceSelection(TAB_SPACES)
                return true
            }
            if (!shortcutPressed) return false

            return when (event.key) {
                Key.Z -> if (event.isShiftPressed) {
                    enabled && canRedo && applyHistory(historyIndex + 1)
                } else {
                    enabled && canUndo && applyHistory(historyIndex - 1)
                }
                Key.Y -> enabled && canRedo && applyHistory(historyIndex + 1)
                Key.C -> copySelection()
                Key.X -> cutSelection()
                Key.V -> pasteClipboard()
                else -> false
            }
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
                    .padding(end = 12.dp, bottom = 12.dp)
                    .verticalScroll(verticalScroll)
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
                    BoxWithConstraints(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        val minContentWidth = maxWidth
                        Column(modifier = Modifier.widthIn(min = minContentWidth)) {
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
                                    .widthIn(min = minContentWidth)
                                    .padding(bottom = 10.dp)
                                    .onPreviewKeyEvent(::handleEditorKeyEvent)
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
                    .padding(bottom = 12.dp)
                    .width(12.dp)
            ) {
                PlatformVerticalScrollbar(
                    scrollState = verticalScroll,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = gutterWidth + 1.dp, end = 12.dp)
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                PlatformHorizontalScrollbar(
                    scrollState = horizontalScroll,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
