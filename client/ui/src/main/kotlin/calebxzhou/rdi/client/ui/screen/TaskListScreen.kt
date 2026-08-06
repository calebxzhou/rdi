package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.*
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.ui.comp.Task2DetailDialog
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status

/**
 * calebxzhou @ 2026-04-03 23:22
 */

@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    initialSelectedRunId: String? = null
) {
    val entries by ClientTaskManager.entries.collectAsState()
    val finishedCount = entries.count { it.status.isTerminal }
    var selectedRunId by remember(initialSelectedRunId) { mutableStateOf(initialSelectedRunId) }
    val selectedEntry = entries.firstOrNull { it.runId == selectedRunId }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow("任务列表", onBack) {
                if (finishedCount > 0) {
                    CircleIconButton(
                        icon = "\uF2ED",
                        label = "清空已完成",
                        bgColor = MaterialTheme.colorScheme.error
                    ) {
                        ClientTaskManager.clearFinished()
                    }
                }
            }
            ContentBody {
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当前没有任务",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { it.runId }) { entry ->
                            TaskEntryCard(
                                entry = entry,
                                onOpenDetail = { selectedRunId = entry.runId }
                            )
                        }
                    }
                }
            }
        }
    }
    selectedEntry?.let { entry ->
        Task2DetailDialog(
            entry = entry,
            onClose = { selectedRunId = null }
        )
    }
}


@Composable
private fun TaskEntryCard(
    entry: Task2Entry,
    onOpenDetail: () -> Unit
) {
    val snapshot = entry.snapshot
    val status = entry.status
    val fraction = snapshot.currentFraction?.coerceIn(0f, 1f)
    val statusColor = when (status) {
        Task2Status.QUEUED -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
        Task2Status.RUNNING -> MaterialTheme.colorScheme.primary
        Task2Status.DONE -> MaterialTheme.colorScheme.tertiary
        Task2Status.FAILED -> MaterialTheme.colorScheme.error
        Task2Status.CANCELLED -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowV(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.task.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = status.text,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                when {
                    status == Task2Status.RUNNING -> {
                        CircleIconButton(
                            icon = "\uF05E",
                            label = "结束任务",
                            bgColor = MaterialTheme.colorScheme.error
                        ) {
                            ClientTaskManager.cancel(entry.runId)
                        }
                    }

                    status.isTerminal -> {
                        CircleIconButton(
                            icon = "\uF2ED",
                            label = "移除",
                            bgColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                        ) {
                            ClientTaskManager.remove(entry.runId)
                        }
                    }
                }
            }

            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = snapshot.currentMessage.ifBlank { "准备中" },
                style = MaterialTheme.typography.bodyMedium
            )

            snapshot.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "节点${snapshot.donePaths.size}/${snapshot.progressByPath.size.coerceAtLeast(snapshot.donePaths.size)}",
                    style = MaterialTheme.typography.labelMedium
                )
                SpacerFullW()
                if (entry.startedAt != null) {
                    Text(
                        text = "Run ${entry.runId.take(8)}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }
        }
    }
}

private val Task2Status.text: String
    get() = when (this) {
        Task2Status.QUEUED -> "排队中"
        Task2Status.RUNNING -> "执行中"
        Task2Status.DONE -> "已完成"
        Task2Status.FAILED -> "失败"
        Task2Status.CANCELLED -> "已取消"
    }
