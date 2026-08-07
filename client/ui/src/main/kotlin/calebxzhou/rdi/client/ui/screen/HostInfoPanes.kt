package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.uiModKey
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.hydrateToUiMods
import calebxzhou.rdi.client.service.toUiMods
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.ModGrid
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

@Composable
internal fun HostOverviewPane(
    host: Host.DetailVo,
    onOpenModpackInfo: (String) -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        host.modpack.ModpackCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenModpackInfo(host.modpack.id.toHexString()) }
        )
    }
}

@Composable
internal fun HostModsPane(
    modCatalog: ModCatalog,
    hostId: ObjectId,
    extraMods: List<Mod>,
    baseVersionMods: List<Mod>,
    disabledMods: List<Mod>,
    canManage: Boolean,
    addExtraModLoading: Boolean,
    addExtraModLoadingText: String,
    onDisabledModsChanged: (List<Mod>) -> Unit,
    onRemoveExtraMods: (List<Mod>) -> Unit,
    onOpenResourceMods: () -> Unit,
    onAddExtraModAdvanced: () -> Unit,
    onOk: (String) -> Unit,
    onError: (String) -> Unit,
    onOpenTaskList: (String) -> Unit
) {
    var selectedTab by remember(hostId) { mutableIntStateOf(0) }
    var selectedExtraKeys by remember(hostId) { mutableStateOf(emptySet<String>()) }
    var selectedBaseKeys by remember(hostId) { mutableStateOf(emptySet<String>()) }
    var selectedDisabledKeys by remember(hostId) { mutableStateOf(emptySet<String>()) }
    var extraUiMods by remember(hostId) { mutableStateOf(extraMods.toUiMods()) }
    var extraModsLoading by remember(hostId) { mutableStateOf(extraMods.isNotEmpty()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(extraMods, modCatalog) {
        selectedExtraKeys = selectedExtraKeys.intersect(extraMods.map(::extraModKey).toSet())
        extraModsLoading = extraMods.isNotEmpty()
        extraUiMods = if (extraMods.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                withContext(Dispatchers.IO) { extraMods.hydrateToUiMods(modCatalog) }
            }.getOrDefault(extraMods.toUiMods())
        }
        extraModsLoading = false
    }
    LaunchedEffect(baseVersionMods) {
        selectedBaseKeys = selectedBaseKeys.intersect(baseVersionMods.map(::extraModKey).toSet())
    }
    LaunchedEffect(disabledMods) {
        selectedDisabledKeys = selectedDisabledKeys.intersect(disabledMods.map(::extraModKey).toSet())
    }
    val selectedExtraMods = extraMods.filter { extraModKey(it) in selectedExtraKeys }
    val selectedBaseMods = baseVersionMods.filter { extraModKey(it) in selectedBaseKeys }
    val selectedDisabledMods = disabledMods.filter { extraModKey(it) in selectedDisabledKeys }
    val activeTab = selectedTab
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SecondaryTabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Tab(activeTab == 0, { selectedTab = 0 }, text = { Text("附加mod") })
            Tab(activeTab == 1, { selectedTab = 1 }, text = { Text("mod总表") })
            Tab(activeTab == 2, { selectedTab = 2 }, text = { Text("已停用的mod") })
        }
        when (activeTab) {
            0 -> HostExtraModsPane(
                extraUiMods = extraUiMods,
                selectedExtraMods = selectedExtraMods,
                selectedExtraModKeys = selectedExtraKeys,
                selectAllExtraMods = extraMods.isNotEmpty() && selectedExtraKeys.size == extraMods.size,
                extraModsLoading = extraModsLoading,
                canManageExtraMods = canManage,
                addExtraModLoading = addExtraModLoading,
                addExtraModLoadingText = addExtraModLoadingText,
                onToggleSelectAll = {
                    selectedExtraKeys = if (it) extraMods.map(::extraModKey).toSet() else emptySet()
                },
                onToggleSelected = { mod, selected ->
                    selectedExtraKeys = selectedExtraKeys.toggle(extraModKey(mod), selected)
                },
                onDownloadSelected = {
                    onOpenTaskList(ClientTaskManager.submit(ModService.downloadModsTask2(selectedExtraMods)))
                },
                onRemoveSelected = { onRemoveExtraMods(selectedExtraMods) },
                onOpenResourceMods = onOpenResourceMods,
                onAddExtraModAdvanced = onAddExtraModAdvanced
            )

            1 -> HostModListPane(
                modCatalog = modCatalog,
                canManage = canManage,
                baseVersionMods = baseVersionMods,
                selectedModListMods = selectedBaseMods,
                selectedModListKeys = selectedBaseKeys,
                selectAllModListMods = baseVersionMods.isNotEmpty() && selectedBaseKeys.size == baseVersionMods.size,
                onToggleSelectAll = {
                    selectedBaseKeys = if (it) baseVersionMods.map(::extraModKey).toSet() else emptySet()
                },
                onToggleSelected = { mod, selected ->
                    selectedBaseKeys = selectedBaseKeys.toggle(extraModKey(mod), selected)
                },
                onDisableSelected = {
                    scope.rdiRequest<List<Mod>>(
                        path = "host/$hostId/mods/disabled",
                        method = HttpMethod.Post,
                        body = serdesJson.encodeToString(selectedBaseMods),
                        onOk = {
                            onDisabledModsChanged(it.data.orEmpty())
                            selectedBaseKeys = emptySet()
                            onOk("已停用选中的mod，重启房间后生效")
                        },
                        onErr = { onError(it.message ?: "停用mod失败") }
                    )
                }
            )

            2 -> HostDisabledModsPane(
                modCatalog = modCatalog,
                canManage = canManage,
                disabledMods = disabledMods,
                selectedDisabledMods = selectedDisabledMods,
                selectedDisabledModKeys = selectedDisabledKeys,
                selectAllDisabledMods = disabledMods.isNotEmpty() && selectedDisabledKeys.size == disabledMods.size,
                onToggleSelectAll = {
                    selectedDisabledKeys = if (it) disabledMods.map(::extraModKey).toSet() else emptySet()
                },
                onToggleSelected = { mod, selected ->
                    selectedDisabledKeys = selectedDisabledKeys.toggle(extraModKey(mod), selected)
                },
                onEnableSelected = {
                    scope.rdiRequest<List<Mod>>(
                        path = "host/$hostId/mods/disabled",
                        method = HttpMethod.Delete,
                        body = serdesJson.encodeToString(selectedDisabledMods),
                        onOk = {
                            onDisabledModsChanged(it.data.orEmpty())
                            selectedDisabledKeys = emptySet()
                            onOk("已恢复选中的mod，重启房间后生效")
                        },
                        onErr = { onError(it.message ?: "启用mod失败") }
                    )
                }
            )

        }
    }
}

private fun Set<String>.toggle(key: String, selected: Boolean): Set<String> =
    if (selected) this + key else this - key

private fun extraModKey(mod: Mod): String = mod.uiModKey

@Composable
internal fun HostConsolePane(
    hostId: ObjectId,
    state: ConsoleState,
    canManage: Boolean,
    onOk: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showCommandDialog by remember(hostId) { mutableStateOf(false) }
    var command by remember(hostId) { mutableStateOf("") }
    var commandSending by remember(hostId) { mutableStateOf(false) }
    var commandResult by remember(hostId) { mutableStateOf<String?>(null) }
    var pendingAction by remember(hostId) { mutableStateOf<HostRuntimeAction?>(null) }

    fun sendAction(action: HostRuntimeAction) {
        scope.rdiRequestU(
            path = "host/$hostId/${action.path}",
            method = HttpMethod.Post,
            onOk = { onOk(action.successMessage) },
            onErr = { onError(it.message ?: action.errorMessage) }
        )
    }

    Box(modifier) {
        Console(state = state, modifier = Modifier.fillMaxSize())
        if (canManage) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                CircleIconButton(
                    "\uF04B",
                    "启动",
                    showText = false,
                    bgColor = themeNow.primary
                ) { sendAction(HostRuntimeAction.Start) }
                CircleIconButton(
                    "\uF120",
                    "发送命令",
                    showText = false,
                    bgColor = themeNow.tertiary
                ) {
                    commandResult = null
                    showCommandDialog = true
                }
                CircleIconButton("\uF01E", "重启", showText = false, bgColor = MaterialTheme.colorScheme.primary) {
                    pendingAction = HostRuntimeAction.Restart
                }
                CircleIconButton("\uF04D", "停止", showText = false, bgColor = MaterialTheme.colorScheme.error) {
                    pendingAction = HostRuntimeAction.Stop
                }
                CircleIconButton("\uF05E", "强制停止", showText = false, bgColor = MaterialTheme.colorScheme.error) {
                    pendingAction = HostRuntimeAction.ForceStop
                }
            }
        }
    }

    if (showCommandDialog) {
        val normalizedCommand = command.trim().removePrefix("/")
        AlertDialog(
            onDismissRequest = { if (!commandSending) showCommandDialog = false },
            title = { Text("发送服务器命令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("命令会以服务器控制台身份执行，不需要输入开头的/")
                    OutlinedTextField(
                        value = command,
                        onValueChange = {
                            command = it
                            commandResult = null
                        },
                        enabled = !commandSending,
                        singleLine = true,
                        label = { Text("命令") },
                        placeholder = { Text("say hello") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    commandResult?.let { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("命令返回：")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1B1D1F), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(result, color = Color(0xFFE0E0E0), fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalizedCommand.isNotBlank() && !commandSending,
                    onClick = {
                        commandSending = true
                        commandResult = null
                        scope.rdiRequest<String>(
                            path = "host/$hostId/command",
                            method = HttpMethod.Post,
                            params = mapOf("command" to normalizedCommand),
                            onOk = {
                                commandResult = it.data?.ifBlank { "OK" } ?: "OK"
                                onOk("命令已执行")
                            },
                            onErr = { onError(it.message ?: "发送命令失败") },
                            onDone = { commandSending = false }
                        )
                    }
                ) {
                    Text(if (commandSending) "发送中..." else "发送")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !commandSending,
                    onClick = { showCommandDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    pendingAction?.let { action ->
        ConfirmDialog(
            title = action.confirmTitle,
            message = action.confirmMessage,
            onConfirm = {
                pendingAction = null
                sendAction(action)
            },
            onDismiss = { pendingAction = null }
        )
    }
}

private enum class HostRuntimeAction(
    val path: String,
    val confirmTitle: String,
    val confirmMessage: String,
    val successMessage: String,
    val errorMessage: String
) {
    Start("start", "", "", "启动指令已发送", "启动失败"),
    Restart("restart", "确认重启", "确定重启该房间吗？", "重启指令已发送", "重启失败"),
    Stop("stop", "确认停止", "确定停止该房间吗？", "停止指令已发送", "停止失败"),
    ForceStop("force-stop", "确认强制停止", "确定强制停止该房间吗？", "强制停止指令已发送", "强制停止失败")
}

@Composable
internal fun HostFilesPane(
    hostId: ObjectId,
    canManage: Boolean,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    if (canManage) {
        HostFileExplorer(
            hostId = hostId,
            enableTaczUpload = true,
            onOpenTaskList = onOpenTaskList
        )
    } else {
        Text("仅房间管理员可查看文件", color = themeNow.onSurfaceVariant)
    }
}

@Composable
internal fun HostExtraModsPane(
    extraUiMods: List<UiMod>,
    selectedExtraMods: List<Mod>,
    selectedExtraModKeys: Set<String>,
    selectAllExtraMods: Boolean,
    extraModsLoading: Boolean,
    canManageExtraMods: Boolean,
    addExtraModLoading: Boolean,
    addExtraModLoadingText: String,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onDownloadSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onOpenResourceMods: () -> Unit,
    onAddExtraModAdvanced: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            var addExtraModShiftPressed by remember { mutableStateOf(false) }
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedExtraMods.size}个",
                    color = themeNow.onSurfaceVariant
                )
                Checkbox(selectAllExtraMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF019",
                    label = "下载",
                    enabled = selectedExtraMods.isNotEmpty(),
                    bgColor = themeNow.primary,
                ) {
                    onDownloadSelected()
                }
                if (canManageExtraMods) {
                    CircleIconButton(
                        icon = "\uEA81",
                        label = "删除",
                        enabled = selectedExtraMods.isNotEmpty(),
                        bgColor = MaterialTheme.colorScheme.error,
                    ) {
                        onRemoveSelected()
                    }
                    Box(
                        modifier = Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    addExtraModShiftPressed = awaitPointerEvent().keyboardModifiers.isShiftPressed
                                }
                            }
                        }
                    ) {
                        CircleIconButton(
                            icon = "\uF067",
                            label = if (addExtraModLoading) {
                                addExtraModLoadingText.ifBlank { "匹配中..." }
                            } else {
                                "附加Mod"
                            },
                            enabled = !addExtraModLoading,
                            bgColor = themeNow.secondary,
                        ) {
                            if (addExtraModShiftPressed) {
                                onAddExtraModAdvanced()
                            } else {
                                onOpenResourceMods()
                            }
                        }
                    }
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以在整合包之外再添加更多Mod。",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以在整合包之外再添加更多Mod。",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (extraModsLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text("正在载入附加Mod信息...", color = themeNow.onSurfaceVariant)
            }
        }
    }

    if (!extraModsLoading && extraUiMods.isEmpty()) {
        Text("当前没有附加Mod", color = themeNow.onSurfaceVariant)
    } else if (!extraModsLoading) {
        ModGrid(
            mods = extraUiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedExtraModKeys,
            emptyText = "当前没有附加Mod",
            onModClick = { uiMod ->
                val selected = uiMod.key in selectedExtraModKeys
                onToggleSelected(uiMod.mod, !selected)
            }
        )
    }
}


@Composable
internal fun HostModListPane(
    modCatalog: ModCatalog,
    canManage: Boolean,
    baseVersionMods: List<Mod>,
    selectedModListMods: List<Mod>,
    selectedModListKeys: Set<String>,
    selectAllModListMods: Boolean,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onDisableSelected: () -> Unit
) {
    val uiMods by produceState(initialValue = baseVersionMods.toUiMods(), baseVersionMods) {
        value = if (baseVersionMods.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    baseVersionMods.hydrateToUiMods(modCatalog)
                }
            }.getOrDefault(baseVersionMods.toUiMods())
        }
    }
    if (canManage) BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedModListMods.size}个",
                    color = themeNow.onSurfaceVariant
                )
                Checkbox(selectAllModListMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF2ED",
                    label = "停用选中的mod",
                    enabled = selectedModListMods.isNotEmpty(),
                    bgColor = MaterialTheme.colorScheme.error,
                ) {
                    onDisableSelected()
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以停用整合包中的mod",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以停用整合包中的mod",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (baseVersionMods.isEmpty()) {
        Text("当前整合包版本没有可显示的Mod", color = themeNow.onSurfaceVariant)
    } else {
        ModGrid(
            mods = uiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedModListKeys,
            emptyText = "当前整合包版本没有可显示的Mod",
            onModClick = if (canManage) {
                { uiMod ->
                    val selected = uiMod.key in selectedModListKeys
                    onToggleSelected(uiMod.mod, !selected)
                }
            } else null
        )
    }
}


@Composable
internal fun HostDisabledModsPane(
    modCatalog: ModCatalog,
    canManage: Boolean,
    disabledMods: List<Mod>,
    selectedDisabledMods: List<Mod>,
    selectedDisabledModKeys: Set<String>,
    selectAllDisabledMods: Boolean,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onEnableSelected: () -> Unit
) {
    val uiMods by produceState(initialValue = disabledMods.toUiMods(), disabledMods) {
        value = if (disabledMods.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    disabledMods.hydrateToUiMods(modCatalog)
                }
            }.getOrDefault(disabledMods.toUiMods())
        }
    }
    if (canManage) BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedDisabledMods.size}个",
                    color = themeNow.onSurfaceVariant
                )
                Checkbox(selectAllDisabledMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF0E2",
                    label = "启用选中的mod",
                    enabled = selectedDisabledMods.isNotEmpty(),
                    bgColor = themeNow.primary,
                ) {
                    onEnableSelected()
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以重新启用已停用的mod",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以重新启用已停用的mod",
                    color = themeNow.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (disabledMods.isEmpty()) {
        Text("当前没有停用mod", color = themeNow.onSurfaceVariant)
    } else {
        ModGrid(
            mods = uiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedDisabledModKeys,
            emptyText = "当前没有停用mod",
            onModClick = if (canManage) {
                { uiMod ->
                    val selected = uiMod.key in selectedDisabledModKeys
                    onToggleSelected(uiMod.mod, !selected)
                }
            } else null
        )
    }
}
