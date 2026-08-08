package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.ErrorText
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.model.UiMod
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.client.ui.comp.ModGrid
import calebxzhou.rdi.common.model.*
import kotlinx.coroutines.launch
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditAction
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditViewModel
import calebxzhou.rdi.client.ui.screen.selectHostExtraModFiles
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf



@Composable
fun ModpackVersionEditScreen(
    modpackId: String,
    verName: String,
    onBack: () -> Unit,
    viewModel: ModpackVersionEditViewModel = koinViewModel(key = "$modpackId:$verName") {
        parametersOf(modpackId, verName)
    },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var selectedModKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var deleteConfirmMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }

    LaunchedEffect(uiState.okMessage) {
        uiState.okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearOkMessage()
        }
    }

    LaunchedEffect(uiState.completedAction) {
        when (uiState.completedAction) {
            ModpackVersionEditAction.EDIT -> editingMods = emptyList()
            ModpackVersionEditAction.DELETE -> {
                selectedModKeys = emptySet()
                deleteConfirmMods = emptyList()
            }
            ModpackVersionEditAction.ADD, null -> Unit
        }
        if (uiState.completedAction != null) viewModel.clearCompletedAction()
    }

    LaunchedEffect(uiState.reloadToken) {
        selectedModKeys = emptySet()
    }

    val currentVersion = uiState.version
    val selectedMods = uiState.uiMods.filter { it.key in selectedModKeys }
    val canMutate = currentVersion?.status?.let {
        it != Modpack.Status.WAIT && it != Modpack.Status.BUILDING
    } ?: false
    val versionStatusText = when (currentVersion?.status) {
        Modpack.Status.OK -> "可编辑"
        Modpack.Status.FAIL -> "可编辑，上一轮构建失败"
        Modpack.Status.WAIT -> "等待构建中"
        Modpack.Status.BUILDING -> "构建中"
        null -> "未加载"
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(
                title = buildString {
                    append("版本Mod编辑")
                    uiState.pack?.name?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" V")
                    append(verName)
                },
                onBack = onBack
            ) {
                Text(
                    "版本状态: $versionStatusText",
                    color = if (canMutate) themeNow.primary else themeNow.onSurfaceVariant
                )
                if (currentVersion != null) {
                    TitleTabBar(
                        items = listOf(
                            TitleTabItem(0, "\uF1B2", "Mod编辑${currentVersion.mods.size}个"),
                            TitleTabItem(1, "\uF15B", "文件编辑")
                        ),
                        selected = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
                uiState.errorMessage?.let { ErrorText(it) }
            }
            ContentBody {
                if (uiState.loading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!uiState.loading && currentVersion == null) {
                Text("未找到版本信息", color = MaterialTheme.colorScheme.error)
            }

            if (currentVersion != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!canMutate) {
                        Text(
                            "当前版本正在重构，暂时不能增删改Mod。等状态回到可编辑后再操作。",
                            color = themeNow.onSurfaceVariant
                        )
                    }
                    when (selectedTab) {
                        0 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedMods.isEmpty()) "点击Mod卡片以多选" else "已选中${selectedMods.size}个Mod",
                                        color = themeNow.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CircleIconButton(
                                        icon = "\uF021",
                                        label = "刷新",
                                        bgColor = MaterialTheme.colorScheme.primary,
                                        enabled = !uiState.loading && !uiState.uiModsLoading
                                    ) {
                                        viewModel.reload()
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uF067",
                                        label = if (canMutate) {
                                            if (uiState.addDialogLoading) uiState.addDialogLoadingText.ifBlank { "匹配中..." } else "添加Mod"
                                        } else {
                                            "当前版本不可修改"
                                        },
                                        bgColor = themeNow.secondary,
                                        enabled = canMutate && !uiState.addDialogLoading
                                    ) {
                                        val mcVersion = uiState.pack?.mcVer
                                        if (mcVersion == null) {
                                            return@CircleIconButton
                                        }
                                        scope.launch {
                                            val files = selectHostExtraModFiles() ?: return@launch
                                            viewModel.matchFiles(files, mcVersion)
                                        }
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uF044",
                                        label = if (canMutate) "批量编辑选中Mod" else "当前版本不可修改",
                                        bgColor = themeNow.tertiary,
                                        enabled = canMutate && selectedMods.isNotEmpty() && !uiState.editDialogSaving
                                    ) {
                                        editingMods = selectedMods
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uEA81",
                                        label = if (canMutate) "批量删除选中Mod" else "当前版本不可修改",
                                        bgColor = MaterialTheme.colorScheme.error,
                                        enabled = canMutate && selectedMods.isNotEmpty()
                                    ) {
                                        deleteConfirmMods = selectedMods
                                    }
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        uiState.uiModsLoading -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator()
                                                Text("正在载入Mod信息...")
                                            }
                                        }

                                        uiState.uiMods.isEmpty() -> {
                                            Text("当前版本没有Mod")
                                        }

                                        else -> {
                                            ModGrid(
                                                mods = uiState.uiMods,
                                                modifier = Modifier.fillMaxSize(),
                                                selectedKeys = selectedModKeys,
                                                emptyText = "当前版本没有Mod",
                                                onModClick = { uiMod ->
                                                    selectedModKeys = if (uiMod.key in selectedModKeys) {
                                                        selectedModKeys - uiMod.key
                                                    } else {
                                                        selectedModKeys + uiMod.key
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("文件编辑(file editing)暂未实现", color = themeNow.onSurfaceVariant)
                            }
                        }
                    }
                }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }

    if (uiState.addDialogOpen) {
        val selectedPendingUiMods = uiState.pendingAddUiMods.filter {
            it.key in uiState.selectedPendingAddKeys
        }
        Dialog(
            onDismissRequest = {
                if (!uiState.addDialogLoading) {
                    viewModel.resetAddDialog()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("添加版本Mod", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "会复用房间附加Mod(extra mod)的本地jar匹配逻辑。当前会把选中的Mod一次性提交，版本只重构1次。",
                        color = themeNow.onSurfaceVariant
                    )
                    Text(
                        if (selectedPendingUiMods.isEmpty()) "点击下方Mod卡片选择要添加的Mod" else "当前准备添加${selectedPendingUiMods.size}个Mod",
                        color = themeNow.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ModGrid(
                            mods = uiState.pendingAddUiMods,
                            modifier = Modifier.fillMaxSize(),
                            selectedKeys = uiState.selectedPendingAddKeys,
                            emptyText = "当前没有可添加的Mod",
                            onModClick = viewModel::togglePendingAdd,
                            onSideChange = if (uiState.addDialogLoading) {
                                null
                            } else {
                                viewModel::changePendingAddSide
                            }
                        )
                    }
                    if (uiState.rejectedAddFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(255, 244, 244))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("以下文件未能加入候选", color = MaterialTheme.colorScheme.error)
                            Text(
                                uiState.rejectedAddFiles.joinToString("\n") { "• $it" },
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        uiState.addDialogError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Space8w()
                        }
                        TextButton(
                            enabled = !uiState.addDialogLoading,
                            onClick = viewModel::resetAddDialog,
                        ) {
                            Text("取消")
                        }
                        Space8w()
                        TextButton(
                            enabled = !uiState.addDialogLoading && selectedPendingUiMods.isNotEmpty() && canMutate,
                            onClick = {
                                viewModel.addSelectedMods(selectedPendingUiMods.map(UiMod::toMod))
                            }
                        ) {
                            Text(if (uiState.addDialogLoading) "添加中..." else "添加")
                        }
                    }
                }
            }
        }
    }

    if (editingMods.isNotEmpty()) {
        VersionModBatchEditDialog(
            uiMods = editingMods,
            saving = uiState.editDialogSaving,
            onDismiss = {
                if (!uiState.editDialogSaving) editingMods = emptyList()
            },
            onSave = viewModel::saveEdits,
        )
    }

    if (deleteConfirmMods.isNotEmpty()) {
        ConfirmDialog(
            title = "删除版本Mod",
            message = if (deleteConfirmMods.size == 1) {
                "确定删除《${deleteConfirmMods.first().displayName}》吗？删除后版本会自动重构。"
            } else {
                "确定删除选中的${deleteConfirmMods.size}个Mod吗？删除后版本会自动重构。"
            },
            onConfirm = {
                val targetRefs = deleteConfirmMods.map { ModRef(it.projectId, it.fileId) }
                viewModel.deleteMods(targetRefs)
                deleteConfirmMods = emptyList()
            },
            onDismiss = { deleteConfirmMods = emptyList() }
        )
    }
}

private data class EditableVersionModState(
    val originalProjectId: String,
    val originalFileId: String,
    val displayName: String,
    val platform: String,
    val projectId: String,
    val slug: String,
    val fileId: String,
    val hash: String,
    val downloadUrlsText: String,
    val side: Mod.Side,
)

private fun UiMod.toEditableVersionModState() = EditableVersionModState(
    originalProjectId = projectId,
    originalFileId = fileId,
    displayName = displayName,
    platform = platform,
    projectId = projectId,
    slug = slug,
    fileId = fileId,
    hash = hash,
    downloadUrlsText = mod.downloadUrls.joinToString("\n"),
    side = side
)

private fun EditableVersionModState.toBatchReplaceItem(): ModBatchReplaceItem {
    val normalizedPlatform = platform.trim().lowercase()
    val normalizedProjectId = projectId.trim()
    val normalizedSlug = slug.trim()
    val normalizedFileId = fileId.trim()
    val normalizedHash = hash.trim()
    require(normalizedPlatform.isNotBlank()) { "platform不能为空" }
    require(normalizedProjectId.isNotBlank()) { "projectId不能为空" }
    require(normalizedSlug.isNotBlank()) { "slug不能为空" }
    require(normalizedFileId.isNotBlank()) { "fileId不能为空" }
    require(normalizedHash.isNotBlank()) { "hash不能为空" }
    return ModBatchReplaceItem(
        projectId = originalProjectId,
        fileId = originalFileId,
        mod = Mod(
            platform = normalizedPlatform,
            projectId = normalizedProjectId,
            slug = normalizedSlug,
            fileId = normalizedFileId,
            hash = normalizedHash,
            side = side,
            downloadUrls = downloadUrlsText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()
        )
    )
}

@Composable
private fun VersionModBatchEditDialog(
    uiMods: List<UiMod>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<ModBatchReplaceItem>) -> Unit
) {
    val dialogKey = remember(uiMods) { uiMods.map(UiMod::key).sorted().joinToString("|") }
    var editStates by remember(dialogKey) {
        mutableStateOf(uiMods.map { it.toEditableVersionModState() })
    }
    var localError by remember(dialogKey) { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.92f),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("批量编辑${editStates.size}个Mod", style = MaterialTheme.typography.titleLarge)
                localError?.let { ErrorText(it) }
                RScrollableColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    editStates.forEachIndexed { index, state ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(248, 248, 248))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("${index + 1}. ${state.displayName}", )
                            OutlinedTextField(
                                value = state.platform,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(platform = value) }
                                },
                                label = { Text("平台(platform)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.projectId,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(projectId = value) }
                                },
                                label = { Text("项目ID(projectId)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.slug,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(slug = value) }
                                },
                                label = { Text("slug") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.fileId,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(fileId = value) }
                                },
                                label = { Text("文件ID(fileId)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.hash,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(hash = value) }
                                },
                                label = { Text("hash") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.downloadUrlsText,
                                onValueChange = { value ->
                                    editStates = editStates.updateAt(index) { copy(downloadUrlsText = value) }
                                },
                                label = { Text("下载地址(downloadUrls)，一行一个") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("运行侧(side)")
                            Mod.Side.entries.forEach { candidate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.side == candidate,
                                        onClick = {
                                            editStates = editStates.updateAt(index) { copy(side = candidate) }
                                        }
                                    )
                                    Text(candidate.text)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !saving,
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    Space8w()
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            val replaceItems = runCatching {
                                editStates.map(EditableVersionModState::toBatchReplaceItem)
                            }.getOrElse {
                                localError = it.message ?: "Mod信息不合法"
                                return@TextButton
                            }
                            localError = null
                            onSave(replaceItems)
                        }
                    ) {
                        Text(if (saving) "保存中..." else "保存")
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.updateAt(index: Int, transform: T.() -> T): List<T> = mapIndexed { currentIndex, value ->
    if (currentIndex == index) value.transform() else value
}

