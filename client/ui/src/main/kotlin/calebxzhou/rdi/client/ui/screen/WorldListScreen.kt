package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MainColumn
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.ui.comp.WorldCard
import calebxzhou.rdi.common.model.World
import io.ktor.http.*

/**
 * calebxzhou @ 2026-01-15 21:16
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldListScreen(
    onBack: (() -> Unit)? = null,
    /*
    onOpenBirdView: (String) -> Unit = {},
    onOpenLocalBirdView: (() -> Unit)? = null
    */
) {
    MainColumn {
        TitleRow("存档", onBack = { onBack?.invoke() ?: Unit })
        Spacer(modifier = Modifier.height(8.dp))
        WorldListPane(
            /*
            onOpenBirdView = onOpenBirdView,
            onOpenLocalBirdView = onOpenLocalBirdView,
            */
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldListPane(
    /*
    onOpenBirdView: (String) -> Unit = {},
    onOpenLocalBirdView: (() -> Unit)? = null,
    */
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var worlds by remember { mutableStateOf<List<World.Vo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<World.Vo?>(null) }
    var confirmReset by remember { mutableStateOf<World.Vo?>(null) }
    var confirmCopy by remember { mutableStateOf<World.Vo?>(null) }
    var selectedWorld by remember { mutableStateOf<World.Vo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    fun reload() {
        loading = true
        errorMessage = null
        scope.rdiRequest<List<World.Vo>>(
            "world",
            onDone = {
                loading = false
            },
            onErr = {
                errorMessage = "加载存档失败:${it.message}"
                worlds = emptyList()
            },
            onOk = {
                worlds = it.data ?: emptyList()
            }
        )
    }
    LaunchedEffect(Unit) {
        reload()
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            FlowRowV(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("存档", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Space8w()
                    }
                    val canOperate = selectedWorld != null
                    /* World bird view entrances are disabled.
                    CircleIconButton(
                        "\uDB85\uDDC6", "俯视图开发中", enabled = canOperate && DEBUG,
                    ) {
                        selectedWorld?.let { onOpenBirdView(it.id.toHexString()) }
                    }
                    if (onOpenLocalBirdView != null) {
                        Space8w()
                        CircleIconButton(
                            "\uF07C",
                            "打开本地存档"
                        ) {
                            onOpenLocalBirdView()
                        }
                    }
                    */
                    Space8w()
                    CircleIconButton(
                        icon = "\uDB80\uDD67",
                        tooltip = "上传存档(开发中)",
                        enabled = false,
                    ) {
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uDB80\uDD62",
                        tooltip = "下载存档(开发中)",
                        enabled = false,
                    ) {

                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uDB81\uDC50",
                        tooltip = "重置",
                        enabled = canOperate,
                        bgColor = MaterialTheme.colorScheme.tertiary,

                        ) {
                        selectedWorld?.let { confirmReset = it }
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uEA81",
                        tooltip = "删除",
                        enabled = canOperate,
                        bgColor = MaterialTheme.colorScheme.error,

                        ) {
                        selectedWorld?.let { confirmDelete = it }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!loading && worlds.isEmpty()) {
                Text("没有存档。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (worlds.isNotEmpty()) {
                WorldTableHeader()
                Space8h()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(worlds, key = { it.id.toHexString() }) { world ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (selectedWorld?.id == world.id) 2.dp else 1.dp,
                                    color = if (selectedWorld?.id == world.id) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = baseRoundCornerShape
                                )
                                .padding(2.dp)
                        ) {
                            world.WorldCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedWorld = world }
                            )
                        }
                    }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)

    }
    confirmCopy?.let { world ->
        AlertDialog(
            onDismissRequest = { confirmCopy = null },
            title = { Text("确认复制") },
            text = { Text("要给存档“${world.name}”复制一份一模一样的吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCopy = null
                    scope.rdiRequestU(
                        "world/${world.id}/copy",
                        method = HttpMethod.Post,
                        onOk = {
                            okMessage = "已复制"
                            reload()
                        },
                        onErr = {
                            errorMessage = "复制失败: ${it.message}"
                        }
                    )
                }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCopy = null }) {
                    Text("取消")
                }
            }
        )
    }

    confirmReset?.let { world ->
        var resetConfirmName by remember(world.id) { mutableStateOf("") }
        val resetConfirmed = resetConfirmName == world.name
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("确认重置") },
            text = {
                Column {
                    Text("要清空存档“${world.name}”的世界数据吗？存档条目会保留，但世界内容无法恢复。使用该存档的房间必须先停止。")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetConfirmName,
                        onValueChange = { resetConfirmName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输入存档名确认") },
                        placeholder = { Text(world.name) },
                        singleLine = true,
                        isError = resetConfirmName.isNotEmpty() && !resetConfirmed
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = resetConfirmed,
                    onClick = {
                        confirmReset = null
                        scope.rdiRequestU(
                            "world/${world.id}/reset",
                            method = HttpMethod.Post,
                            onOk = {
                                okMessage = "已重置"
                                reload()
                            },
                            onErr = {
                                errorMessage = "重置失败: ${it.message}"
                            }
                        )
                    }
                ) {
                    Text(
                        "重置",
                        color = if (resetConfirmed) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = null }) {
                    Text("取消")
                }
            }
        )
    }

    confirmDelete?.let { world ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("确认删除") },
            text = { Text("要永久删除存档“${world.name}”及其所有的回档点吗？无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.rdiRequestU(
                        "world/${world.id}",
                        method = HttpMethod.Delete,
                        onOk = {
                            okMessage = "已删除"
                            reload()
                        },
                        onErr = {
                            errorMessage = "删除失败: ${it.message}"
                        }
                    )
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WorldTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.width(56.dp))
        Text(
            text = "名称",
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = "整合包",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = "大小",
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = "创建时间",
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
