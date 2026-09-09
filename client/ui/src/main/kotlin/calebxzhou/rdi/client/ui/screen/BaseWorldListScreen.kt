package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.BaseWorldCard
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.viewmodel.BaseWorldListViewModel
import calebxzau.rdi.common.model.BaseWorld
import calebxzhou.rdi.common.util.humanFileSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val baseWorldDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")

@Composable
fun BaseWorldListScreen(
    onBack: () -> Unit,
    onOpenUpload: () -> Unit,
    viewModel: BaseWorldListViewModel = viewModel { BaseWorldListViewModel() },
) {
    val account by AccountSessionStore.account.collectAsStateWithLifecycle()
    val ownerId = remember(account._id) { account._id.toHexString() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val taskEntries by ClientTaskManager.entries.collectAsStateWithLifecycle()
    val terminalUploadSignature = remember(ownerId, taskEntries) {
        taskEntries
            .asSequence()
            .filter { entry ->
                entry.status.isTerminal &&
                    entry.dedupeKey?.startsWith("baseworld-upload:${ownerId}:") == true
            }
            .joinToString("|") { entry -> "${entry.runId}:${entry.status}" }
    }
    var menuWorldId by remember { mutableStateOf<UUID?>(null) }
    var detailWorld by remember { mutableStateOf<BaseWorld?>(null) }
    var deleteWorld by remember { mutableStateOf<BaseWorld?>(null) }
    var deleteName by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(ownerId, terminalUploadSignature) {
        viewModel.refresh(ownerId)
    }
    DisposableEffect(lifecycleOwner, ownerId) {
        var hasResumed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasResumed) viewModel.refresh(ownerId)
                hasResumed = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ownerId) {
        detailWorld = null
        deleteWorld = null
        deleteName = ""
        viewModel.prepareDeletion()
    }
    LaunchedEffect(state.worlds, state.deletingWorldId, deleteWorld?.id) {
        val selected = deleteWorld
        if (selected != null && state.deletingWorldId == null && state.worlds.none { it.id == selected.id }) {
            deleteWorld = null
            deleteName = ""
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow(title = "地图模板", onBack = onBack) {
                CircleIconButton(
                    icon = "\uF067",
                    label = "添加地图",
                    enabled = true,
                    onClick = onOpenUpload,
                )
            }
            ContentBody {
                when {
                    state.loading && state.worlds.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.errorMessage != null && state.worlds.isEmpty() ->
                        AlertErr(state.errorMessage!!) {
                            viewModel.clearErrorMessage()
                            viewModel.refresh(ownerId)
                        }

                    state.worlds.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text("还没有地图模板，上传一个世界开始使用") }

                    else -> {
                        if (state.errorMessage != null) {
                            AlertErr(state.errorMessage!!, viewModel::clearErrorMessage)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { viewModel.refresh(ownerId) }) { Text("重试") }
                            }
                        }
                        FlowRowV(
                            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.worlds.forEach { world ->
                                Box(
                                    modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 220.dp, max = 350.dp),
                                ) {
                                    BaseWorldCard(
                                    world = world,
                                    deleteEnabled = !state.deletionInProgress,
                                    menuExpanded = menuWorldId == world.id,
                                    onClick = { menuWorldId = world.id },
                                    onDismissMenu = { menuWorldId = null },
                                    onDetails = {
                                        menuWorldId = null
                                        detailWorld = world
                                    },
                                    onDelete = {
                                        menuWorldId = null
                                        viewModel.prepareDeletion()
                                        deleteName = ""
                                        deleteWorld = world
                                    },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailWorld?.let { world ->
        BaseWorldDetailsDialog(world = world, uploaderName = account.name, onDismiss = { detailWorld = null })
    }
    deleteWorld?.let { world ->
        val deleting = state.deletionInProgress && state.deletingWorldId == world.id
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteWorld = null },
            title = { Text("删除地图模板") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("删除后将永久移除这个地图模板，且无法恢复。请输入地图名称确认删除：")
                    OutlinedTextField(
                        value = deleteName,
                        onValueChange = { deleteName = it; viewModel.prepareDeletion() },
                        label = { Text(world.name) },
                        singleLine = true,
                        enabled = !deleting,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting && deleteName == world.name,
                    onClick = { viewModel.delete(ownerId, world.id) },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteWorld = null }) { Text("取消") }
            },
        )
    }
    state.deletionErrorMessage?.let { AlertErr(it, viewModel::prepareDeletion) }
}

@Composable
private fun BaseWorldDetailsDialog(world: BaseWorld, uploaderName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(world.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BaseWorldDetailRow("地图大小", world.size.humanFileSize)
                BaseWorldDetailRow("上传时间", formatBaseWorldUploadTime(world.id))
                BaseWorldDetailRow("上传者", uploaderName)
                BaseWorldDetailRow(
                    "生成器设置",
                    world.generatorSettings?.let { if (it.isEmpty()) "空字符串" else it } ?: "未设置",
                )
                BaseWorldDetailRow("世界类型", world.levelType)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun BaseWorldDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}

private fun formatBaseWorldUploadTime(id: UUID): String {
    val timestamp = (id.mostSignificantBits ushr 16) and 0xFFFFFFFFFFFFL
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(baseWorldDateTimeFormatter)
}
