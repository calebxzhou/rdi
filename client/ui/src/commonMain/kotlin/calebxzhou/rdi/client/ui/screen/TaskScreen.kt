package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.toFixed
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.hM
import calebxzhou.rdi.client.ui.platformKeepScreenOn
import calebxzhou.rdi.client.ui.wM
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskProgress

/**
 * calebxzhou @ 2026-01-16 11:12
 */
@Composable
fun TaskScreen(
    task: Task,
    autoClose: Boolean = false,
    onBack: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    val execution by TaskExecutionRuntime.state.collectAsState()
    val taskActive = TaskExecutionRuntime.isCurrentTask(task)
    val currentMessage = if (taskActive) execution.currentMessage else "准备中"
    val currentFraction = if (taskActive) execution.currentFraction else null
    val errorMessage = if (taskActive) execution.errorMessage else null
    val done = taskActive && execution.done && errorMessage == null

    platformKeepScreenOn(taskActive && execution.running)

    var expandState by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var handledCompletionRunId by remember(task) { mutableStateOf<Long?>(null) }

    LaunchedEffect(task) {
        val expandSeed = mutableMapOf<String, Boolean>()
        initExpandState(task, listOf(task.name), expandSeed)
        expandState = expandSeed
        handledCompletionRunId = null
        TaskExecutionRuntime.start(task)
    }

    LaunchedEffect(taskActive, execution.runId, execution.done, execution.errorMessage) {
        if (!taskActive || !execution.done || execution.errorMessage != null) return@LaunchedEffect
        if (handledCompletionRunId == execution.runId) return@LaunchedEffect
        handledCompletionRunId = execution.runId
        onDone()
        if (autoClose) onBack()
    }

    MainColumn {
        TitleRow(task.name, onBack) {}
        Space8h()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactHeader = maxWidth < 560.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                if (compactHeader) {
                    Text("进度 ${((currentFraction ?: 0f) * 100).toFixed(1)}% · ${currentMessage.truncate(120)}")
                    Space8h()
                    LinearProgressIndicator(
                        progress = currentFraction?.coerceIn(0f, 1f) ?: 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Space8h()
                        Text(it, color = MaterialTheme.colors.error)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentMessage.truncate(80),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(8.wM)
                        LinearProgressIndicator(
                            progress = currentFraction?.coerceIn(0f, 1f) ?: 0f,
                            modifier = Modifier.widthIn(min = 140.dp, max = 320.dp)
                        )
                        Spacer(8.wM)
                        Text("${((currentFraction ?: 0f) * 100).toFixed(1)}%")
                    }
                    errorMessage?.let {
                        Space8h()
                        Text(it, color = MaterialTheme.colors.error)
                    }
                }
            }
        }

        if (done) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("任务已完成", style = MaterialTheme.typography.h4)
            }
        } else {
            Spacer(8.hM)
            val listState = rememberLazyListState()
            val treeRows by remember(task, expandState) {
                derivedStateOf {
                    buildTaskRows(task, listOf(task.name), 0, expandState)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(treeRows, key = { _, item -> taskPathKey(item.keyPath) }) { _, item ->
                        val key = taskPathKey(item.keyPath)
                        TaskTreeRow(
                            row = item,
                            progress = if (taskActive) execution.progressByPath[key] else null,
                            done = taskActive && key in execution.donePaths,
                            expanded = expandState[key] ?: item.defaultExpanded,
                            onToggle = {
                                val current = expandState[key] ?: item.defaultExpanded
                                expandState = expandState.toMutableMap().apply {
                                    this[key] = !current
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class TaskTreeRowState(
    val keyPath: List<String>,
    val name: String,
    val level: Int,
    val isGroup: Boolean,
    val defaultExpanded: Boolean
)

private fun buildTaskRows(
    task: Task,
    keyPath: List<String>,
    level: Int,
    expandState: Map<String, Boolean>,
    rows: MutableList<TaskTreeRowState> = mutableListOf()
): List<TaskTreeRowState> {
    val isGroup = task is Task.Group || task is Task.Sequence
    rows += TaskTreeRowState(
        keyPath = keyPath,
        name = task.name,
        level = level,
        isGroup = isGroup,
        defaultExpanded = task.defaultExpandedInTaskTree()
    )
    if (isGroup) {
        val key = taskPathKey(keyPath)
        val expanded = expandState[key] ?: task.defaultExpandedInTaskTree()
        if (expanded) {
            val children = when (task) {
                is Task.Group -> task.subTasks
                is Task.Sequence -> task.subTasks
                else -> emptyList()
            }
            children.forEachIndexed { index, sub ->
                buildTaskRows(
                    sub,
                    keyPath + taskChildKeySegment(sub.name, index),
                    level + 1,
                    expandState,
                    rows
                )
            }
        }
    }
    return rows
}

@Composable
private fun TaskTreeRow(
    row: TaskTreeRowState,
    progress: TaskProgress?,
    done: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val message = when {
        done -> "完成"
        progress != null -> progress.message
        else -> "等待中"
    }
    val fraction = progress?.fraction ?: if (done) 1f else 0f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactRow = maxWidth < 720.dp
        val indent = (row.level * if (compactRow) 14 else 24).coerceAtMost(240).dp
        if (compactRow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent)
                    .then(if (row.isGroup) Modifier.clickable { onToggle() } else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.isGroup) {
                        Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.subtitle2)
                        Spacer(8.wM)
                    } else {
                        Spacer(12.wM)
                    }
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${(fraction * 100).toFixed(1)}%")
                }
                Text(message.truncate(60), style = MaterialTheme.typography.body2)
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent)
                    .then(if (row.isGroup) Modifier.clickable { onToggle() } else Modifier)
            ) {
                if (row.isGroup) {
                    Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.subtitle2)
                    Spacer(8.wM)
                } else {
                    Spacer(12.wM)
                }
                Text(row.name, style = MaterialTheme.typography.subtitle2)
                Spacer(modifier = Modifier.weight(1f))
                Text(message.truncate(60), style = MaterialTheme.typography.body2)
                Spacer(8.wM)
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.widthIn(min = 120.dp, max = 200.dp)
                )
                Spacer(8.wM)
                Text("${(fraction * 100).toFixed(1)}%")
            }
        }
    }
}

private fun String.truncate(maxChars: Int): String {
    if (length <= maxChars) return this
    if (maxChars <= 1) return "…"
    return substring(0, maxChars - 1) + "…"
}

private fun initExpandState(
    task: Task,
    path: List<String>,
    expandState: MutableMap<String, Boolean>
) {
    if (task is Task.Group || task is Task.Sequence) {
        val key = taskPathKey(path)
        expandState[key] = task.defaultExpandedInTaskTree()
        val children = when (task) {
            is Task.Group -> task.subTasks
            is Task.Sequence -> task.subTasks
            else -> emptyList()
        }
        children.forEachIndexed { index, sub ->
            initExpandState(sub, path + taskChildKeySegment(sub.name, index), expandState)
        }
    }
}

private fun Task.defaultExpandedInTaskTree(): Boolean = when (this) {
    is Task.Group -> subTasks.size <= 10
    is Task.Sequence -> subTasks.size <= 10
    else -> true
}
