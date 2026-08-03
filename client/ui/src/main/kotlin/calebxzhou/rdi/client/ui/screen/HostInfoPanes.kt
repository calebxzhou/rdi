package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.uiModKey
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.hydrateToUiMods
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.MaterialColor
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

private const val HOST_MODS_TACZ_TAB_INDEX = 3

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
    hasTacz: Boolean,
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
    LaunchedEffect(hasTacz) {
        if (!hasTacz && selectedTab == HOST_MODS_TACZ_TAB_INDEX) selectedTab = 0
    }

    val selectedExtraMods = extraMods.filter { extraModKey(it) in selectedExtraKeys }
    val selectedBaseMods = baseVersionMods.filter { extraModKey(it) in selectedBaseKeys }
    val selectedDisabledMods = disabledMods.filter { extraModKey(it) in selectedDisabledKeys }
    val activeTab = if (!hasTacz && selectedTab == HOST_MODS_TACZ_TAB_INDEX) 0 else selectedTab
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
            if (hasTacz) {
                Tab(
                    activeTab == HOST_MODS_TACZ_TAB_INDEX,
                    { selectedTab = HOST_MODS_TACZ_TAB_INDEX },
                    text = { Text("TaCZ枪包") }
                )
            }
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

            HOST_MODS_TACZ_TAB_INDEX -> HostTaczPackPane(
                hostId = hostId,
                canManage = canManage,
                onOk = onOk,
                onError = onError,
                onOpenTaskList = onOpenTaskList
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
                    bgColor = MaterialColor.GREEN_900.color
                ) { sendAction(HostRuntimeAction.Start) }
                CircleIconButton(
                    "\uF120",
                    "发送命令",
                    showText = false,
                    bgColor = MaterialColor.TEAL_900.color
                ) {
                    commandResult = null
                    showCommandDialog = true
                }
                CircleIconButton("\uF01E", "重启", showText = false, bgColor = MaterialTheme.colorScheme.primary) {
                    pendingAction = HostRuntimeAction.Restart
                }
                CircleIconButton("\uF04D", "停止", showText = false, bgColor = MaterialColor.RED_700.color) {
                    pendingAction = HostRuntimeAction.Stop
                }
                CircleIconButton("\uF05E", "强制停止", showText = false, bgColor = MaterialColor.RED_900.color) {
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
internal fun HostFilesPane(hostId: ObjectId, canManage: Boolean) {
    if (canManage) {
        HostFileExplorer(hostId)
    } else {
        Text("仅房间管理员可查看文件", color = MaterialColor.GRAY_700.color)
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
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllExtraMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF019",
                    tooltip = "下载",
                    enabled = selectedExtraMods.isNotEmpty(),
                    bgColor = MaterialColor.GREEN_900.color,
                ) {
                    onDownloadSelected()
                }
                if (canManageExtraMods) {
                    CircleIconButton(
                        icon = "\uEA81",
                        tooltip = "删除",
                        enabled = selectedExtraMods.isNotEmpty(),
                        bgColor = MaterialColor.RED_900.color,
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
                            tooltip = if (addExtraModLoading) {
                                addExtraModLoadingText.ifBlank { "匹配中..." }
                            } else {
                                "附加Mod"
                            },
                            enabled = !addExtraModLoading,
                            bgColor = MaterialColor.PURPLE_700.color,
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
                    color = MaterialColor.GRAY_700.color,
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
                    color = MaterialColor.GRAY_700.color,
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
                Text("正在载入附加Mod信息...", color = MaterialColor.GRAY_700.color)
            }
        }
    }

    if (!extraModsLoading && extraUiMods.isEmpty()) {
        Text("当前没有附加Mod", color = MaterialColor.GRAY_700.color)
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
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllModListMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF2ED",
                    tooltip = "停用选中的mod",
                    enabled = selectedModListMods.isNotEmpty(),
                    bgColor = MaterialColor.RED_900.color,
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
                    color = MaterialColor.GRAY_700.color,
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
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (baseVersionMods.isEmpty()) {
        Text("当前整合包版本没有可显示的Mod", color = MaterialColor.GRAY_700.color)
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
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllDisabledMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF0E2",
                    tooltip = "启用选中的mod",
                    enabled = selectedDisabledMods.isNotEmpty(),
                    bgColor = MaterialColor.GREEN_900.color,
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
                    color = MaterialColor.GRAY_700.color,
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
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (disabledMods.isEmpty()) {
        Text("当前没有停用mod", color = MaterialColor.GRAY_700.color)
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

@Composable
internal fun HostTaczPackPane(
    hostId: ObjectId,
    canManage: Boolean,
    onOk: (String) -> Unit,
    onError: (String) -> Unit,
    onOpenTaskList: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var files by remember(hostId) { mutableStateOf<List<Host.FileEntry>>(emptyList()) }
    var loading by remember(hostId) { mutableStateOf(false) }
    var deletingPath by remember(hostId) { mutableStateOf<String?>(null) }
    var deleteConfirmFile by remember(hostId) { mutableStateOf<Host.FileEntry?>(null) }

    fun loadFiles() {
        loading = true
        scope.rdiRequest<List<Host.FileEntry>>(
            path = "host/$hostId/files",
            params = mapOf("path" to TACZ_ROOT_DIR),
            onOk = { response ->
                files = response.data.orEmpty().filter { it.isTaczZipFileEntry() }
            },
            onErr = { onError(it.message ?: "读取TaCZ枪包失败") },
            onDone = { loading = false }
        )
    }

    fun uploadFiles() {
        if (!canManage) return
        val selectedFiles = selectHostTaczFiles() ?: return
        val notZipFile = selectedFiles.firstOrNull { !it.extension.equals("zip", ignoreCase = true) }
        if (notZipFile != null) {
            onError("${notZipFile.name}不是zip文件，TaCZ枪包只允许上传zip")
            return
        }
        if (selectedFiles.size > TACZ_MAX_ZIP_FILES) {
            onError("TaCZ枪包最多只能上传${TACZ_MAX_ZIP_FILES}个zip文件")
            return
        }
        val tooLargeFile = selectedFiles.firstOrNull { it.length() > TACZ_FILE_MAX_BYTES }
        if (tooLargeFile != null) {
            onError("${tooLargeFile.name}超过100MB，单个文件最大允许100MB")
            return
        }
        val task = createHostTaczUploadTask(
            hostId = hostId,
            files = selectedFiles,
            onUploaded = {
                withContext(Dispatchers.Main) {
                    onOk("已上传TaCZ枪包文件${selectedFiles.size}个")
                    loadFiles()
                }
            }
        )
        val runId = ClientTaskManager.submit(task)
        onOpenTaskList(runId)
    }

    fun deleteFile(file: Host.FileEntry) {
        if (!canManage || deletingPath != null) return
        deletingPath = file.path
        scope.rdiRequestU(
            path = "host/$hostId/files/file",
            method = HttpMethod.Delete,
            body = serdesJson.encodeToString(Host.FileDeleteDto(file.path)),
            onOk = {
                onOk("已删除${file.name}")
                loadFiles()
            },
            onErr = { onError(it.message ?: "删除TaCZ枪包文件失败") },
            onDone = { deletingPath = null }
        )
    }

    LaunchedEffect(hostId) {
        loadFiles()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TaCZ枪包", fontWeight = FontWeight.Bold, color = MaterialColor.GRAY_900.color)
                    Text(TACZ_ROOT_DIR, color = MaterialColor.GRAY_700.color, fontSize = 13.sp)
                }
                CircleIconButton(
                    icon = "\uF021",
                    tooltip = "刷新",
                    showText = false,
                    bgColor = MaterialColor.GRAY_200.color,
                    iconColor = MaterialColor.GRAY_900.color,
                    enabled = !loading
                ) {
                    loadFiles()
                }
                if (canManage) {
                    CircleIconButton(
                        icon = "\uF093",
                        tooltip = "上传文件",
                        showText = false,
                        bgColor = MaterialColor.GREEN_900.color,
                        enabled = !loading
                    ) {
                        uploadFiles()
                    }
                }
            }

            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }

                files.isEmpty() -> Text("当前目录没有枪包文件", color = MaterialColor.GRAY_700.color)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        HostTaczFileRow(
                            file = file,
                            canManage = canManage,
                            deleting = deletingPath == file.path,
                            onDelete = { deleteConfirmFile = file }
                        )
                    }
                }
            }
        }
    }

    deleteConfirmFile?.let { file ->
        ConfirmDialog(
            title = "确认删除TaCZ枪包",
            message = "确定删除${file.name}吗？删除后需要重新上传才能恢复。",
            onConfirm = {
                deleteConfirmFile = null
                deleteFile(file)
            },
            onDismiss = { deleteConfirmFile = null }
        )
    }
}


@Composable
private fun HostTaczFileRow(
    file: Host.FileEntry,
    canManage: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialColor.GRAY_50.color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uF15B".asIconText,
            color = MaterialColor.GRAY_700.color,
            fontSize = 18.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = MaterialColor.GRAY_900.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${file.size.humanFileSize} · ${file.updateTime.takeIf { it > 0 }?.millisToHumanDateTime ?: "--"}",
                color = MaterialColor.GRAY_700.color,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canManage) {
            CircleIconButton(
                icon = "\uF1F8",
                tooltip = "删除文件",
                size = 28,
                showText = false,
                bgColor = MaterialColor.RED_700.color,
                enabled = !deleting
            ) {
                onDelete()
            }
        }
    }
}
