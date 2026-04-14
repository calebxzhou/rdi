package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.SpacerFullW
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.Task2DetailDialog
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status

/**
 * calebxzhou @ 2026-04-03 23:22
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    initialSelectedRunId: String? = null
) {
    val entries by ClientTaskManager.entries.collectAsState()
    val finishedCount = entries.count { it.status.isTerminal }
    var selectedRunId by remember(initialSelectedRunId) { mutableStateOf(initialSelectedRunId) }
    val selectedEntry = entries.firstOrNull { it.runId == selectedRunId }

    MainColumn {
        TitleRow("任务列表", onBack) {
            if (finishedCount > 0) {
                CircleIconButton(
                    icon = "\uF2ED",
                    tooltip = "清空已完成",
                    bgColor = MaterialColor.RED_600.color
                ) {
                    ClientTaskManager.clearFinished()
                }
            }
        }
        Space8h()
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("当前没有任务")
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
    selectedEntry?.let { entry ->
        Task2DetailDialog(
            entry = entry,
            onClose = { selectedRunId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEntryCard(
    entry: Task2Entry,
    onOpenDetail: () -> Unit
) {
    val snapshot = entry.snapshot
    val status = entry.status
    val fraction = snapshot.currentFraction?.coerceIn(0f, 1f)
    val statusColor = when (status) {
        Task2Status.QUEUED -> MaterialColor.GRAY_600.color
        Task2Status.RUNNING -> MaterialTheme.colors.primary
        Task2Status.DONE -> MaterialColor.GREEN_900.color
        Task2Status.FAILED -> MaterialTheme.colors.error
        Task2Status.CANCELLED -> MaterialColor.GRAY_500.color
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        elevation = 2.dp
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
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        text = status.text,
                        color = statusColor,
                        style = MaterialTheme.typography.body2
                    )
                }
                when {
                    status == Task2Status.RUNNING -> {
                        CircleIconButton(
                            icon = "\uF05E",
                            tooltip = "结束任务",
                            bgColor = MaterialColor.RED_600.color
                        ) {
                            ClientTaskManager.cancel(entry.runId)
                        }
                    }

                    status.isTerminal -> {
                        CircleIconButton(
                            icon = "\uF2ED",
                            tooltip = "移除",
                            bgColor = MaterialColor.GRAY_500.color
                        ) {
                            ClientTaskManager.remove(entry.runId)
                        }
                    }
                }
            }

            if (fraction != null) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.caption
                )
            }

            Text(
                text = snapshot.currentMessage.ifBlank { "准备中" },
                style = MaterialTheme.typography.body2
            )

            snapshot.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "节点${snapshot.donePaths.size}/${snapshot.progressByPath.size.coerceAtLeast(snapshot.donePaths.size)}",
                    style = MaterialTheme.typography.caption
                )
                SpacerFullW()
                if (entry.startedAt != null) {
                    Text(
                        text = "Run ${entry.runId.take(8)}",
                        style = MaterialTheme.typography.caption,
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
