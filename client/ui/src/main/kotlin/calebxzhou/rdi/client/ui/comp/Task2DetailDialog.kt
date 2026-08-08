package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.RVerticalScrollbar as SharedRVerticalScrollbar
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.Task2Snapshot
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.rdi.common.model.task2ChildPathSegment
import calebxzhou.rdi.common.model.task2PathKey

@Suppress("UnusedBoxWithConstraintsScope")

@Composable
fun Task2DetailDialog(
    entry: Task2Entry,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val expandState = remember(entry.runId) { mutableStateMapOf<String, Boolean>() }
    val rows = buildTask2Rows(
        task = entry.task,
        path = listOf(entry.task.id),
        level = 0,
        expandState = expandState
    )
    val listState = rememberLazyListState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(3f / 4f)
                    .fillMaxHeight(3f / 4f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.task.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = entry.status.text,
                                color = entry.status.color(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        CircleIconButton(
                            icon = "\uF00D",
                            label = "隐藏到后台",
                            bgColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            onClose()
                        }
                    }
                    Space8h()
                    entry.snapshot.currentFraction?.coerceIn(0f, 1f)?.let {
                        LinearProgressIndicator(
                            progress = { it },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Space8h()
                    }
                    Text(
                        text = entry.snapshot.currentMessage.ifBlank { "准备中" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    entry.snapshot.errorMessage?.let {
                        Space8h()
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Space8h()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rows, key = { task2PathKey(it.path) }) { row ->
                                Task2DetailRow(
                                    row = row,
                                    snapshot = entry.snapshot,
                                    expanded = expandState[row.key] ?: row.defaultExpanded,
                                    onToggle = {
                                        if (row.isGroup) {
                                            val current = expandState[row.key] ?: row.defaultExpanded
                                            expandState[row.key] = !current
                                        }
                                    }
                                )
                            }
                        }
                        SharedRVerticalScrollbar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

private data class Task2RowState(
    val path: List<String>,
    val key: String,
    val title: String,
    val level: Int,
    val isGroup: Boolean,
    val defaultExpanded: Boolean
)

private fun buildTask2Rows(
    task: Task2,
    path: List<String>,
    level: Int,
    expandState: Map<String, Boolean>,
    rows: MutableList<Task2RowState> = mutableListOf()
): List<Task2RowState> {
    val key = task2PathKey(path)
    val isGroup = task is Task2.Group || task is Task2.Sequence
    val defaultExpanded = when (task) {
        is Task2.Group -> task.defaultExpanded
        is Task2.Sequence -> task.defaultExpanded
        else -> true
    }
    rows += Task2RowState(
        path = path,
        key = key,
        title = task.title,
        level = level,
        isGroup = isGroup,
        defaultExpanded = defaultExpanded
    )
    if (!isGroup) return rows
    val expanded = expandState[key] ?: defaultExpanded
    if (!expanded) return rows
    val children = when (task) {
        is Task2.Group -> task.children
        is Task2.Sequence -> task.children
        else -> emptyList()
    }
    children.forEachIndexed { index, child ->
        buildTask2Rows(
            task = child,
            path = path + task2ChildPathSegment(child, index),
            level = level + 1,
            expandState = expandState,
            rows = rows
        )
    }
    return rows
}

@Composable
private fun Task2DetailRow(
    row: Task2RowState,
    snapshot: Task2Snapshot,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val progress = snapshot.progressByPath[row.key]
    val status = when {
        row.key in snapshot.donePaths -> Task2Status.DONE
        progress != null -> Task2Status.RUNNING
        snapshot.status == Task2Status.FAILED && row.level == 0 -> Task2Status.FAILED
        snapshot.status == Task2Status.CANCELLED && row.level == 0 -> Task2Status.CANCELLED
        else -> Task2Status.QUEUED
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.level * 16).dp)
            .then(if (row.isGroup) Modifier.clickable { onToggle() } else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.isGroup) {
                Text(if (expanded) "▼" else "▶", color = status.color())
                Space8w()
            }
            Text(
                text = row.title,
                color = status.color(),
                style = if (row.level == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = status.text,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            text = progress.detailText(status),
            style = MaterialTheme.typography.labelMedium
        )
        progress?.fraction?.coerceIn(0f, 1f)?.let {
            LinearProgressIndicator(
                progress = { it },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Task2Progress?.detailText(status: Task2Status): String = when {
    this == null -> status.text
    fraction != null -> "${message} ${(fraction!!.coerceIn(0f, 1f) * 100).toInt()}%"
    else -> message
}

private val Task2Status.text: String
    get() = when (this) {
        Task2Status.QUEUED -> "排队中"
        Task2Status.RUNNING -> "执行中"
        Task2Status.DONE -> "已完成"
        Task2Status.FAILED -> "失败"
        Task2Status.CANCELLED -> "已取消"
    }

@Composable
private fun Task2Status.color(): Color = when (this) {
        Task2Status.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
        Task2Status.RUNNING -> MaterialTheme.colorScheme.primary
        Task2Status.DONE -> MaterialTheme.colorScheme.tertiary
        Task2Status.FAILED -> MaterialTheme.colorScheme.error
        Task2Status.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
