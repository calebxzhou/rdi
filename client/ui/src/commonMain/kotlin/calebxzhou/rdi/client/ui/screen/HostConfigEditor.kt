package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.CodeEditor
import calebxzhou.rdi.client.ui.comp.CodeEditorValidation
import calebxzhou.rdi.client.ui.comp.CodeLanguage
import calebxzhou.rdi.client.ui.comp.PlatformVerticalScrollbar
import calebxzhou.rdi.common.model.Host

@Composable
fun HostConfigEditor(
    files: List<Host.ConfigFileEntry>,
    selectedPath: String?,
    loadingFiles: Boolean,
    statusMessage: String?,
    onSelectFile: (String) -> Unit,
    onReloadList: () -> Unit
) {
    var sortColumn by remember { mutableStateOf(HostConfigSortColumn.PATH) }
    var sortAscending by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val sortedFiles = remember(files, sortColumn, sortAscending) {
        files.sortedWith { left, right ->
            val result = when (sortColumn) {
                HostConfigSortColumn.PATH -> left.path.lowercase().compareTo(right.path.lowercase())
                HostConfigSortColumn.TIME -> left.updateTime.compareTo(right.updateTime)
                HostConfigSortColumn.SIZE -> left.size.compareTo(right.size)
            }
            if (sortAscending) result else -result
        }
    }

    fun toggleSort(column: HostConfigSortColumn) {
        if (sortColumn == column) {
            sortAscending = !sortAscending
        } else {
            sortColumn = column
            sortAscending = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("配置文件", style = MaterialTheme.typography.subtitle1)
                Text(
                    text = statusMessage ?: "点击一行即可打开并编辑配置文件",
                    color = MaterialColor.GRAY_700.color,
                    style = MaterialTheme.typography.caption
                )
            }
            TextButton(enabled = !loadingFiles, onClick = onReloadList) {
                Text("刷新列表")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            when {
                loadingFiles -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                files.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("config目录中没有可编辑文件", color = MaterialColor.GRAY_700.color)
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HostConfigTableHeader(
                            sortColumn = sortColumn,
                            sortAscending = sortAscending,
                            onSortChange = ::toggleSort
                        )
                        Divider(color = Color(0xFFEAEAEA))
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(sortedFiles, key = { it.path }) { file ->
                                    HostConfigTableRow(
                                        file = file,
                                        selected = file.path == selectedPath,
                                        onClick = { onSelectFile(file.path) }
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(12.dp)
                            ) {
                                PlatformVerticalScrollbar(
                                    listState = listState,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostConfigEditorOverlay(
    visible: Boolean,
    selectedPath: String?,
    editorText: String,
    loadingContent: Boolean,
    saving: Boolean,
    dirty: Boolean,
    validationErrorMessage: String?,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onSave: () -> Unit,
    onEditorChange: (String) -> Unit,
    onValidationChange: (CodeEditorValidation?) -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TitleRow(
                    selectedPath ?: "", { onClose() },
                ) {
                    Text(
                        text = when {
                            saving -> "保存中..."
                            loadingContent -> "读取中..."
                            validationErrorMessage != null -> validationErrorMessage
                            dirty -> "有未保存修改"
                            else -> ""
                        },
                        color = when {
                            validationErrorMessage != null -> MaterialColor.RED_800.color
                            dirty -> MaterialColor.ORANGE_900.color
                            else -> MaterialColor.GRAY_700.color
                        },
                        style = MaterialTheme.typography.caption
                    )

                    Space8w()
                    CircleIconButton(
                        icon = "\uDB81\uDC50",
                        tooltip = "还原",
                        enabled = selectedPath != null && !loadingContent && !saving,
                        showText = false
                    ) {
                        onReload()
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uF0C7",
                        tooltip = "保存",
                        enabled = selectedPath != null && dirty && !loadingContent && !saving && validationErrorMessage == null,
                        showText = false,
                        bgColor = MaterialColor.GREEN_900.color
                    ) {
                        onSave()
                    }

                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (loadingContent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF8F8F8), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("正在读取配置文件...", color = MaterialColor.GRAY_700.color)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFDFDFD), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFD8D8D8), RoundedCornerShape(12.dp))
                        ) {
                            CodeEditor(
                                text = editorText,
                                enabled = selectedPath != null && !saving,
                                language = CodeLanguage.fromPath(selectedPath),
                                onValueChange = onEditorChange,
                                onValidationChange = onValidationChange,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostConfigTableHeader(
    sortColumn: HostConfigSortColumn,
    sortAscending: Boolean,
    onSortChange: (HostConfigSortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HostConfigHeaderCell(
            title = "文件路径",
            column = HostConfigSortColumn.PATH,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.56f),
            onSortChange = onSortChange
        )
        HostConfigHeaderCell(
            title = "修改时间",
            column = HostConfigSortColumn.TIME,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.26f),
            onSortChange = onSortChange
        )
        HostConfigHeaderCell(
            title = "大小",
            column = HostConfigSortColumn.SIZE,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.18f),
            onSortChange = onSortChange
        )
    }
}

@Composable
private fun HostConfigHeaderCell(
    title: String,
    column: HostConfigSortColumn,
    activeColumn: HostConfigSortColumn,
    ascending: Boolean,
    modifier: Modifier = Modifier,
    onSortChange: (HostConfigSortColumn) -> Unit
) {
    val suffix = if (column == activeColumn) {
        if (ascending) " ↑" else " ↓"
    } else {
        ""
    }
    Text(
        text = title + suffix,
        modifier = modifier.clickable { onSortChange(column) },
        color = if (column == activeColumn) MaterialColor.BLUE_700.color else MaterialColor.GRAY_700.color,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HostConfigTableRow(
    file: Host.ConfigFileEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(238, 245, 255) else Color(250, 250, 250),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.path,
            modifier = Modifier.weight(0.56f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialColor.BLUE_900.color else MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.body2
        )
        Text(
            text = file.updateTime.takeIf { it > 0 }?.millisToHumanDateTime ?: "--",
            modifier = Modifier.weight(0.26f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.caption
        )
        Text(
            text = file.size.humanFileSize,
            modifier = Modifier.weight(0.18f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.caption
        )
    }
}

private enum class HostConfigSortColumn {
    PATH,
    TIME,
    SIZE
}
