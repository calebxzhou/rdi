package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.CodeFontFamily
import calebxzhou.rdi.client.ui.AlertErr
import calebxzhou.rdi.client.ui.AlertOk
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.pickSaveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ConsoleState(
    private val maxLogLines: Int = 1000
) {
    private val _lines: SnapshotStateList<String> = mutableStateListOf()
    val lines: List<String> get() = _lines

    // Track how many lines were removed from the beginning
    private var _removedLinesCount = mutableStateOf(0)
    val removedLinesCount: State<Int> = _removedLinesCount

    fun append(line: String) {
        val trimmed = line.trimEnd('\r')
        if (trimmed.isBlank()) return
        _lines.add(trimmed)
        var removed = 0
        while (_lines.size > maxLogLines) {
            _lines.removeAt(0)
            removed++
        }
        if (removed > 0) {
            _removedLinesCount.value += removed
        }
    }

    fun appendAll(items: Iterable<String>) {
        items.forEach { append(it) }
    }

    fun clear() {
        _lines.clear()
        _removedLinesCount.value = 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Console(
    state: ConsoleState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lineCount = state.lines.size
    val removedCount by state.removedLinesCount
    val scope = rememberCoroutineScope()
    var showSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var lockScroll  by remember { mutableStateOf(false) }
    var previousRemovedCount by remember { mutableStateOf(0) }

    // Custom text toolbar that auto-copies on selection
    val customTextToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                onCopyRequested?.invoke()
            }

            override fun hide() {}

        }
    }

    // Handle scroll position when lines are added or removed
    LaunchedEffect(lineCount, removedCount) {
        if (lineCount > 0) {
            if (lockScroll) {
                // When locked, adjust scroll position if lines were removed from the beginning
                val linesRemovedSinceLastUpdate = removedCount - previousRemovedCount
                if (linesRemovedSinceLastUpdate > 0) {
                    // Adjust scroll position to compensate for removed lines
                    val currentIndex = listState.firstVisibleItemIndex
                    val newIndex = (currentIndex - linesRemovedSinceLastUpdate).coerceAtLeast(0)
                    listState.scrollToItem(newIndex, listState.firstVisibleItemScrollOffset)
                }
            } else {
                // When unlocked, scroll to bottom
                listState.animateScrollToItem(lineCount - 1)
            }
        }
        previousRemovedCount = removedCount
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp) // Make room for scrollbar
                            .background(Color.White),
                        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 4.dp)
                    ) {
                        items(state.lines.size) { index ->
                            Text(
                                text = formatLine(state.lines[index]),
                                modifier = Modifier.padding(vertical = 0.dp),
                                fontFamily = CodeFontFamily,
                                color = Color.Black,
                                fontSize = TextUnit(9f, TextUnitType.Sp),
                                lineHeight = TextUnit(10f, TextUnitType.Sp),
                            )
                        }
                    }
                }
            }

            // Vertical scrollbar on the right
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(14.dp)
                    .background(Color(0xFFE5E5E5))
            ) {
                PlatformVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp)
                )
            }
        }

        // Export button in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                CircleIconButton(
                    icon = if(lockScroll) "\uF023" else "\uF2FC",
                    tooltip = if(lockScroll) "解锁滚动" else "锁定滚动",
                    bgColor = MaterialColor.BLUE_900.color,
                    size = 32,
                    showText = false
                ) {
                    lockScroll = !lockScroll
                }
                CircleIconButton(
                    icon = "\uEF11",
                    tooltip = "导出日志",
                    bgColor = MaterialColor.YELLOW_900.color,
                    size = 32,
                    showText = false
                ) {
                    scope.launch {
                        val result = exportLogsToZip(state.lines)
                        if (result.isSuccess) {
                            showSuccess = true
                        } else {
                            showError = result.exceptionOrNull()?.message ?: "导出失败"
                        }
                    }
                }
                // go downmost
                CircleIconButton(
                    icon = "\uF103",
                    tooltip = "翻到最下面",
                    bgColor = MaterialColor.PINK_900.color,
                    size = 32,
                    showText = false
                ) {
                    scope.launch {
                        if (lineCount > 0) {
                            listState.animateScrollToItem(lineCount - 1)
                        }
                    }
                }
            }
        }
    }

    // Success/Error dialogs
    if (showSuccess) {
        AlertOk("日志已成功导出")
        showSuccess = false
    }
    showError?.let { error ->
        AlertErr("导出失败: $error")
        showError = null
    }
}

private suspend fun exportLogsToZip(lines: List<String>): Result<java.io.File> = withContext(Dispatchers.IO) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val suggestedName = "console_log_$timestamp"

        val outputFile = pickSaveFile(suggestedName, "zip")
            ?: return@withContext Result.failure(Exception("用户取消了操作"))

        // Create zip file
        ZipOutputStream(outputFile.outputStream()).use { zipOut ->
            val entry = ZipEntry("console_log_$timestamp.log")
            zipOut.putNextEntry(entry)

            lines.forEach { line ->
                zipOut.write("$line\n".toByteArray(Charsets.UTF_8))
            }

            zipOut.closeEntry()
        }

        Result.success(outputFile)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private val timePrefixRegex = Regex("""^\[[^\]]*\]""")
private val levelSectionRegex = Regex("""^\s*\[[^\]]*/(INFO|WARN|WARNING|ERROR|SEVERE|FATAL)\]""", RegexOption.IGNORE_CASE)
private val timeColor = Color(0xFF0D47A1)
private val infoColor = Color(0xFF1B5E20)
private val goldColor = Color(0xFF8A6A00)
private val normalColor = Color(0xFF202124)
private val warningColor = Color(0xFF9A6700)
private val errorColor = Color(0xFFC62828)

private fun formatLine(line: String): AnnotatedString {
    val trimmed = line.trimEnd('\r')
    return buildAnnotatedString {
        val timeMatch = timePrefixRegex.find(trimmed)
        if (timeMatch != null && timeMatch.range.first == 0) {
            withStyle(SpanStyle(color = timeColor)) { append(timeMatch.value) }
            val afterTime = trimmed.substring(timeMatch.range.last + 1)
            val levelMatch = levelSectionRegex.find(afterTime)
            if (levelMatch != null && levelMatch.range.first == 0) {
                val levelSection = levelMatch.value
                withStyle(SpanStyle(color = pickLevelColor(levelMatch.groupValues[1]))) { append(levelSection) }
                withStyle(SpanStyle(color = pickColor(afterTime.substring(levelMatch.range.last + 1)))) {
                    append(afterTime.substring(levelMatch.range.last + 1))
                }
            } else {
                withStyle(SpanStyle(color = pickColor(afterTime))) { append(afterTime) }
            }
            return@buildAnnotatedString
        }
        withStyle(SpanStyle(color = pickColor(trimmed))) { append(trimmed) }
    }
}

private fun pickLevelColor(level: String): Color {
    return when {
        level.equals("INFO", true) -> infoColor
        level.equals("WARN", true) || level.equals("WARNING", true) -> goldColor
        else -> errorColor
    }
}

private fun pickColor(text: String): Color {
    return when {
        text.contains("ERROR", true) ||
                text.contains("SEVERE", true) ||
                text.contains("FATAL", true) ||
                text.contains("Exception", true) -> errorColor

        text.contains("WARN", true) ||
                text.contains("WARNING", true) -> warningColor

        else -> normalColor
    }
}
