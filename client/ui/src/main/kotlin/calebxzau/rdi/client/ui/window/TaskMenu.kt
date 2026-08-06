package calebxzau.rdi.client.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status

@Composable
internal fun TaskMenuButton(onOpenTask: (String) -> Unit) {
    val entries by ClientTaskManager.entries.collectAsState()
    val sortedEntries = remember(entries) { sortTaskEntries(entries) }
    val activeCount = entries.count { !it.status.isTerminal }
    var expanded by remember { mutableStateOf(false) }
    var knownTerminalIds by remember {
        mutableStateOf(entries.filter { it.status.isTerminal }.mapTo(mutableSetOf()) { it.runId })
    }

    LaunchedEffect(entries) {
        val terminalIds = entries.filter { it.status.isTerminal }.mapTo(mutableSetOf()) { it.runId }
        if ((terminalIds - knownTerminalIds).isNotEmpty()) expanded = true
        knownTerminalIds = terminalIds
    }

    Box {
        ChromeIconButton("\uF019", "任务") { expanded = !expanded }
        if (activeCount > 0) TaskCountBadge(activeCount, Modifier.align(Alignment.TopEnd))

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(420.dp)
        ) {
            if (sortedEntries.isEmpty()) {
                Text(
                    "暂无任务",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sortedEntries.forEach { entry ->
                    TaskMenuRow(
                        entry = entry,
                        onOpen = {
                            expanded = false
                            onOpenTask(entry.runId)
                        }
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = ClientTaskManager::clearFinished,
                    enabled = entries.any { it.status.isTerminal },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)
                ) {
                    Text("清除已结束任务")
                }
            }
        }
    }
}

@Composable
private fun TaskMenuRow(entry: Task2Entry, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val fraction = if (entry.status == Task2Status.DONE) 1f else entry.snapshot.currentFraction?.coerceIn(0f, 1f)

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.task.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.progressText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    entry.status.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hovered) {
                when (taskHoverAction(entry)) {
                    TaskHoverAction.CANCEL -> TaskActionButton("×", MaterialTheme.colorScheme.errorContainer) {
                        ClientTaskManager.cancel(entry.runId)
                    }
                    TaskHoverAction.REMOVE -> TaskActionButton("\uF2ED", MaterialTheme.colorScheme.surfaceVariant) {
                        ClientTaskManager.remove(entry.runId)
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun TaskActionButton(icon: String, color: Color, onClick: () -> Unit) {
    CircleIconButton(
        icon = icon,
        size = 26.dp,
        bgColor = color,
        iconColor = MaterialTheme.colorScheme.onSurface,
        showText = false,
        onClick = onClick
    )
}

@Composable
private fun TaskCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

internal fun sortTaskEntries(entries: List<Task2Entry>): List<Task2Entry> = entries.sortedWith(
    compareBy<Task2Entry> {
        when (it.status) {
            Task2Status.RUNNING -> 0
            Task2Status.QUEUED -> 1
            else -> 2
        }
    }.thenByDescending { it.createdAt }
)

internal fun taskHoverAction(entry: Task2Entry): TaskHoverAction? = when {
    entry.status == Task2Status.RUNNING -> TaskHoverAction.CANCEL
    entry.status.isTerminal -> TaskHoverAction.REMOVE
    else -> null
}

internal enum class TaskHoverAction { CANCEL, REMOVE }

private fun Task2Entry.progressText(): String = snapshot.currentFraction?.let {
    "${(it.coerceIn(0f, 1f) * 100).toInt()}%"
} ?: snapshot.currentMessage

private fun Task2Status.displayName(): String = when (this) {
    Task2Status.QUEUED -> "排队中"
    Task2Status.RUNNING -> "执行中"
    Task2Status.DONE -> "已完成"
    Task2Status.FAILED -> "失败"
    Task2Status.CANCELLED -> "已取消"
}
