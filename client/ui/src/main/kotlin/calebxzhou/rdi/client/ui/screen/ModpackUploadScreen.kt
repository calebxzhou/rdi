package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.AlertWarn
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.modpacktest.CLIENT_TEST_SUCCESS_MARKER
import calebxzau.rdi.modpacktest.ModpackTestStatus
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.ModGrid
import calebxzhou.rdi.client.ui.comp.ModpackCategorySelector
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.client.ui.comp.Task2DetailDialog
import calebxzau.rdi.client.ui.pickLocalDirectory
import calebxzau.rdi.client.ui.pickLocalModpackFile
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadEvent
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadMode
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadUiState
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadViewModel
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.modpackInfoCharacterCount
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ModpackUploadScreen(
    onBack: () -> Unit,
    onUploadSubmitted: (String) -> Unit = { onBack() },
    viewModel: ModpackUploadViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var showUploadModeDialog by remember { mutableStateOf(false) }
    val uploadedModpacksGridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ModpackUploadEvent.ClientPackLoaded -> selectedTab = event.initialTab
                ModpackUploadEvent.OpenUploadModeDialog -> showUploadModeDialog = true
                is ModpackUploadEvent.UploadSubmitted -> onUploadSubmitted(event.runId)
                ModpackUploadEvent.NavigateBack -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun selectClientPack() {
        scope.launch {
            pickLocalModpackFile()?.let(viewModel::loadClientPack)
        }
    }

    fun selectServerPack() {
        scope.launch {
            pickLocalDirectory("选择服务端安装目录")?.let(viewModel::loadServerPack)
        }
    }

    MaxBox {
        ScreenContentSurface(
            size = ScreenContentSize.LARGE,
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.MoveHome) {
                        viewModel.enableIgnoreModpackTest()
                        true
                    } else {
                        false
                    }
                }
        ) {
            TitleRow(
                title = uiState.title,
                onBack = viewModel::closeScreen,
            ) {
                if (uiState.editMode) {
                    if (uiState.serverPackName == null) {
                        Text("如果选择了整合包服务端，就不需要进行测试。")
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF07C",
                        if (uiState.serverPackName == null) "选择整合包服务端" else "重选服务端",
                        enabled = uiState.canSelectServerPack,
                        onClick = ::selectServerPack,
                    )
                    uiState.serverPackName?.let {
                        Space8w()
                        Text(it)
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF019",
                        "下载测试服务端",
                        enabled = uiState.loadedModpack != null &&
                            !uiState.uiModsLoading &&
                            uiState.downloadTaskRunId == null,
                        onClick = viewModel::downloadTestServer,
                    )
                    Space8w()
                    UploadSubmitButton(
                        enabled = uiState.canSubmitUpload,
                        disabledReason = uiState.uploadDisabledReason,
                        onClick = viewModel::submitUpload,
                    )
                }
            }

            ContentBody {
                uiState.errorMessage?.let {
                    AlertErr(it)
                }
                if (uiState.ignoreModpackTest) {
                    AlertWarn("已启用rdi.ignoreModpackTest=true，当前允许跳过客户端测试(client test)和服务端测试(server test)直接上传")
                }

                if (!uiState.editMode) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircleIconButton(
                            icon = "\uF07C",
                            label = "选择客户端安装包",
                            enabled = !uiState.loading,
                            onClick = ::selectClientPack,
                        )
                    }
                } else {
                    Space8h()
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("基本信息") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Mod列表(${uiState.uiMods.size})") },
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("客户端测试") },
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("服务端测试") },
                        )
                    }
                    Space8h()

                    when (selectedTab) {
                        0 -> ModpackUploadInfoTab(
                            state = uiState,
                            viewModel = viewModel,
                        )

                        1 -> Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.uiModsLoading) {
                                Text(
                                    "正在补充Mod详细信息...",
                                    color = themeNow.onSurfaceVariant,
                                )
                                Space8h()
                            }
                            ModGrid(
                                mods = uiState.uiMods,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                emptyText = "没有可显示的mod",
                                onSideChange = viewModel::updateModSide,
                            )
                        }

                        2 -> TestConsolePane(
                            statusText = testStatusText(
                                uiState.clientTestStatus,
                                uiState.clientTestPassSeconds,
                            ),
                            consoleState = viewModel.clientTestConsoleState,
                            hint = "创建单机存档，在聊天框发送$CLIENT_TEST_SUCCESS_MARKER",
                            onStart = viewModel::startClientTest,
                            onStop = viewModel::stopClientTest,
                        )

                        3 -> TestConsolePane(
                            statusText = testStatusText(
                                uiState.serverTestStatus,
                                uiState.serverTestPassSeconds,
                            ),
                            consoleState = viewModel.serverTestConsoleState,
                            hint = "",
                            onStart = viewModel::startServerTest,
                            onStop = viewModel::stopServerTest,
                        )
                    }
                }
            }
        }

        if (uiState.loading) {
            AlertDialog(onDismissRequest = {}) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 420.dp)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("正在读取整合包", style = MaterialTheme.typography.titleLarge)
                        Text(uiState.progressText ?: "正在处理...")
                        val progress = uiState.progressFraction
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        uiState.downloadTaskEntry?.let { entry ->
            Task2DetailDialog(entry = entry, onClose = {})
        }

        uiState.pendingMissingModDownload?.let { pending ->
            ConfirmDialog(
                title = "下载缺失Mod",
                message = "${pending.usage}需要先下载缺失Mod${pending.mods.size}个。下载完成后再启动${pending.usage}。",
                onConfirm = viewModel::confirmMissingModDownload,
                onDismiss = viewModel::dismissMissingModDownload,
            )
        }

        if (showUploadModeDialog) {
            ModpackUploadModeDialog(
                modpacks = uiState.uploadedModpacks,
                gridState = uploadedModpacksGridState,
                onSelect = { target ->
                    viewModel.chooseUpdateMode(target)
                    showUploadModeDialog = false
                },
                onClose = {
                    showUploadModeDialog = false
                    viewModel.closeUploadModeDialog()
                },
            )
        }
    }
}

@Composable
private fun UploadSubmitButton(
    enabled: Boolean,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val button: @Composable () -> Unit = {
        CircleIconButton(
            icon = "\uF058",
            label = "开始传包",
            bgColor = themeNow.primary,
            enabled = enabled,
            onClick = onClick,
        )
    }
    if (enabled) {
        button()
    } else {
        SimpleTooltip(disabledReason ?: "当前暂时不能上传") {
            button()
        }
    }
}

@Composable
private fun ModpackUploadInfoTab(
    state: ModpackUploadUiState,
    viewModel: ModpackUploadViewModel,
) {
    val metadataEnabled = state.uploadMode == ModpackUploadMode.CREATE
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RRow {
            RadioButton(
                selected = state.uploadMode == ModpackUploadMode.CREATE,
                onClick = viewModel::chooseCreateMode,
            )
            Text("传新包")
            RadioButton(
                selected = state.uploadMode == ModpackUploadMode.UPDATE,
                onClick = viewModel::requestUpdateModeSelection,
            )
            Text("更新已有包")
            CircleIconButton(
                "\uE8B8",
                "选择整合包",
                enabled = !state.loading &&
                    !state.uploadedModpacksLoading &&
                    state.uploadMode == ModpackUploadMode.UPDATE,
                onClick = viewModel::requestUploadModeDialog,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draft.name,
                onValueChange = { viewModel.updateDraft(state.draft.copy(name = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("整合包名称") },
                enabled = state.uploadMode == ModpackUploadMode.CREATE,
            )
            OutlinedTextField(
                value = state.draft.versionName,
                onValueChange = viewModel::updateVersionName,
                modifier = Modifier.weight(1f),
                label = { Text("版本") },
                singleLine = true,
            )
        }
        RRow {
            OutlinedTextField(
                value = state.draft.iconUrl,
                onValueChange = { viewModel.updateDraft(state.draft.copy(iconUrl = it)) },
                label = { Text("图标链接") },
                singleLine = true,
                enabled = metadataEnabled,
                isError = state.draftErrors.iconUrl != null,
                supportingText = { state.draftErrors.iconUrl?.let { Text(it) } },
            )
            OutlinedTextField(
                value = state.draft.sourceUrl,
                onValueChange = { viewModel.updateDraft(state.draft.copy(sourceUrl = it)) },
                label = { Text("原帖发布链接") },
                singleLine = true,
                enabled = metadataEnabled,
            )
            OutlinedTextField(
                value = state.draft.info,
                onValueChange = { viewModel.updateDraft(state.draft.copy(info = it)) },
                label = { Text("简介(10~100字符)") },
                singleLine = true,
                enabled = metadataEnabled,
                isError = state.draftErrors.info != null,
                supportingText = {
                    Text(
                        state.draftErrors.info
                            ?: "${state.draft.info.trim().modpackInfoCharacterCount()}/100"
                    )
                },
            )
        }
        Text("分类(至少1个，最多${Modpack.MAX_CATEGORY_COUNT}个)")
        ModpackCategorySelector(
            selected = state.draft.categories,
            onSelectedChange = { viewModel.updateDraft(state.draft.copy(categories = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = metadataEnabled,
        )
        state.draftErrors.categories?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModpackUploadModeDialog(
    modpacks: List<Modpack.BriefVo>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onSelect: (Modpack.BriefVo) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(2f / 3f).fillMaxHeight(2f / 3f),
            shape = MaterialTheme.shapes.medium,
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("选择整合包")
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                if (modpacks.isEmpty()) {
                    Text("你还没有已上传整合包")
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(280.dp),
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(modpacks, key = { it.id.toHexString() }) { modpack ->
                                modpack.ModpackCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onSelect(modpack) },
                                )
                            }
                        }
                        RVerticalScrollbar(
                            gridState = gridState,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TestConsolePane(
    statusText: String,
    consoleState: ConsoleState,
    hint: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(statusText)
            if (hint.isNotBlank()) Text(hint)
            Spacer(Modifier.weight(1f))
            CircleIconButton("\uF04B", "开始", onClick = onStart)
            CircleIconButton("\uF04D", "停止", onClick = onStop)
        }
        Space8h()
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            Console(state = consoleState, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun testStatusText(status: ModpackTestStatus, passSeconds: String?): String = when (status) {
    ModpackTestStatus.NOT_RUN -> "未测试"
    ModpackTestStatus.RUNNING -> "测试中"
    ModpackTestStatus.PASSED -> "通过${passSeconds?.let { "(${it}s)" } ?: ""}"
    ModpackTestStatus.FAILED -> "失败"
    ModpackTestStatus.STOPPED -> "已停止"
}
