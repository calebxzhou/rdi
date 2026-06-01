package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.hydrateToUiMods
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.ModGrid
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackVersionEditScreen(
    modpackId: String,
    verName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pack by remember { mutableStateOf<Modpack.DetailVo?>(null) }
    var version by remember { mutableStateOf<Modpack.Version?>(null) }
    var uiMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var uiModsLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedModKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addDialogOpen by remember { mutableStateOf(false) }
    var addDialogLoading by remember { mutableStateOf(false) }
    var addDialogLoadingText by remember { mutableStateOf("") }
    var addDialogError by remember { mutableStateOf<String?>(null) }
    var pendingAddUiMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var selectedPendingAddKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rejectedAddFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var editDialogSaving by remember { mutableStateOf(false) }
    var deleteConfirmMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }

    fun resetAddDialog() {
        addDialogLoading = false
        addDialogLoadingText = ""
        addDialogError = null
        pendingAddUiMods = emptyList()
        selectedPendingAddKeys = emptySet()
        rejectedAddFiles = emptyList()
    }

    fun reload() {
        loading = true
        errorMessage = null
        scope.rdiRequest<Modpack.DetailVo>(
            path = "modpack/$modpackId/detail",
            onOk = { response ->
                val detail = response.data
                val currentVersion = detail?.versions?.firstOrNull { it.name == verName }
                pack = detail
                version = currentVersion
                selectedModKeys = emptySet()
                if (currentVersion == null) {
                    uiModsLoading = false
                    uiMods = emptyList()
                    errorMessage = "未找到版本 V$verName"
                    return@rdiRequest
                }
                uiModsLoading = true
                uiMods = emptyList()
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        runCatching { currentVersion.mods.hydrateToUiMods() }
                            .getOrElse {
                                it.printStackTrace()
                                currentVersion.mods.toUiMods()
                            }
                    }
                    uiMods = loaded
                    uiModsLoading = false
                }
            },
            onErr = { errorMessage = "加载版本信息失败: ${it.message}" },
            onDone = { loading = false }
        )
    }

    LaunchedEffect(modpackId, verName) {
        reload()
    }

    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val currentVersion = version
    val selectedMods = uiMods.filter { it.key in selectedModKeys }
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

    MainBox {
        MainColumn {
            TitleRow(
                title = buildString {
                    append("版本Mod编辑")
                    pack?.name?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" V")
                    append(verName)
                },
                onBack = onBack
            ) {
                errorMessage?.let { ErrorText(it) }
            }
            Space8h()
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!loading && currentVersion == null) {
                Text("未找到版本信息", color = MaterialTheme.colorScheme.error)
            }

            if (currentVersion != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "版本状态: $versionStatusText",
                        color = if (canMutate) MaterialColor.GREEN_900.color else MaterialColor.ORANGE_900.color
                    )
                    if (!canMutate) {
                        Text(
                            "当前版本正在重构，暂时不能增删改Mod。等状态回到可编辑后再操作。",
                            color = MaterialColor.GRAY_700.color
                        )
                    }
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                        listOf("Mod编辑${currentVersion.mods.size}个", "文件编辑").forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
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
                                        color = MaterialColor.GRAY_700.color,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CircleIconButton(
                                        icon = "\uF021",
                                        tooltip = "刷新",
                                        bgColor = MaterialColor.BLUE_700.color,
                                        enabled = !loading && !uiModsLoading
                                    ) {
                                        reload()
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uF067",
                                        tooltip = if (canMutate) {
                                            if (addDialogLoading) addDialogLoadingText.ifBlank { "匹配中..." } else "添加Mod"
                                        } else {
                                            "当前版本不可修改"
                                        },
                                        bgColor = MaterialColor.PURPLE_700.color,
                                        enabled = canMutate && !addDialogLoading
                                    ) {
                                        val mcVersion = pack?.mcVer
                                        if (mcVersion == null) {
                                            errorMessage = "无法获取当前整合包MC版本"
                                            return@CircleIconButton
                                        }
                                        scope.launch {
                                            val files = selectHostExtraModFiles() ?: return@launch
                                            resetAddDialog()
                                            addDialogLoading = true
                                            addDialogLoadingText = "正在匹配Mod..."
                                            val matchResult = try {
                                                matchHostExtraModFiles(files, mcVersion) { progress ->
                                                    addDialogLoadingText = progress
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "匹配Mod失败"
                                                addDialogLoading = false
                                                addDialogLoadingText = ""
                                                return@launch
                                            }
                                            val dedupeResult = filterVersionModsForAdding(
                                                candidateMods = matchResult.matchedMods.map { mod ->
                                                    if (mod.side == Mod.Side.UNKNOWN) {
                                                        mod.toUiMod().withSide(Mod.Side.BOTH).toMod()
                                                    } else {
                                                        mod
                                                    }
                                                },
                                                existingMods = version?.mods.orEmpty()
                                            )
                                            val acceptedUiMods = dedupeResult.acceptedMods.toUiMods()
                                            pendingAddUiMods = acceptedUiMods
                                            rejectedAddFiles = matchResult.rejectedFiles + dedupeResult.rejectedMessages
                                            selectedPendingAddKeys = acceptedUiMods.map(UiMod::key).toSet()
                                            addDialogLoading = false
                                            addDialogLoadingText = ""
                                            if (pendingAddUiMods.isEmpty() && rejectedAddFiles.isEmpty()) {
                                                errorMessage = "没有在网上搜索到这些Mod的信息"
                                                return@launch
                                            }
                                            addDialogOpen = true
                                        }
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uF044",
                                        tooltip = if (canMutate) "批量编辑选中Mod" else "当前版本不可修改",
                                        bgColor = MaterialColor.YELLOW_900.color,
                                        enabled = canMutate && selectedMods.isNotEmpty() && !editDialogSaving
                                    ) {
                                        editingMods = selectedMods
                                    }
                                    Space8w()
                                    CircleIconButton(
                                        icon = "\uEA81",
                                        tooltip = if (canMutate) "批量删除选中Mod" else "当前版本不可修改",
                                        bgColor = MaterialColor.RED_900.color,
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
                                        uiModsLoading -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator()
                                                Text("正在载入Mod信息...")
                                            }
                                        }

                                        uiMods.isEmpty() -> {
                                            Text("当前版本没有Mod")
                                        }

                                        else -> {
                                            ModGrid(
                                                mods = uiMods,
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
                                Text("文件编辑(file editing)暂未实现", color = MaterialColor.GRAY_700.color)
                            }
                        }
                    }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }

    if (addDialogOpen) {
        val selectedPendingUiMods = pendingAddUiMods.filter { it.key in selectedPendingAddKeys }
        Dialog(
            onDismissRequest = {
                if (!addDialogLoading) {
                    resetAddDialog()
                    addDialogOpen = false
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
                        color = MaterialColor.GRAY_700.color
                    )
                    Text(
                        if (selectedPendingUiMods.isEmpty()) "点击下方Mod卡片选择要添加的Mod" else "当前准备添加${selectedPendingUiMods.size}个Mod",
                        color = MaterialColor.GRAY_700.color
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ModGrid(
                            mods = pendingAddUiMods,
                            modifier = Modifier.fillMaxSize(),
                            selectedKeys = selectedPendingAddKeys,
                            emptyText = "当前没有可添加的Mod",
                            onModClick = { uiMod ->
                                selectedPendingAddKeys = if (uiMod.key in selectedPendingAddKeys) {
                                    selectedPendingAddKeys - uiMod.key
                                } else {
                                    selectedPendingAddKeys + uiMod.key
                                }
                            },
                            onSideChange = if (addDialogLoading) {
                                null
                            } else {
                                { uiMod, side ->
                                    pendingAddUiMods = pendingAddUiMods.map {
                                        if (it.key == uiMod.key) it.withSide(side) else it
                                    }
                                }
                            }
                        )
                    }
                    if (rejectedAddFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(255, 244, 244))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("以下文件未能加入候选", color = MaterialColor.RED_900.color)
                            Text(
                                rejectedAddFiles.joinToString("\n") { "• $it" },
                                color = MaterialColor.RED_900.color,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        addDialogError?.let {
                            Text(
                                text = it,
                                color = MaterialColor.RED_900.color,
                                modifier = Modifier.weight(1f)
                            )
                            Space8w()
                        }
                        TextButton(
                            enabled = !addDialogLoading,
                            onClick = {
                                resetAddDialog()
                                addDialogOpen = false
                            }
                        ) {
                            Text("取消")
                        }
                        Space8w()
                        TextButton(
                            enabled = !addDialogLoading && selectedPendingUiMods.isNotEmpty() && canMutate,
                            onClick = {
                                val targetMods = selectedPendingUiMods.map(UiMod::toMod)
                                addDialogLoading = true
                                addDialogError = null
                                scope.rdiRequestU(
                                    path = versionModsBatchPath(modpackId, verName),
                                    method = HttpMethod.Post,
                                    body = serdesJson.encodeToString(targetMods),
                                    onOk = {
                                        okMessage = "已添加${targetMods.size}个Mod，版本开始重构"
                                        addDialogOpen = false
                                        resetAddDialog()
                                        reload()
                                    },
                                    onErr = { addDialogError = it.message ?: "添加Mod失败" },
                                    onDone = { addDialogLoading = false }
                                )
                            }
                        ) {
                            Text(if (addDialogLoading) "添加中..." else "添加")
                        }
                    }
                }
            }
        }
    }

    if (editingMods.isNotEmpty()) {
        VersionModBatchEditDialog(
            uiMods = editingMods,
            saving = editDialogSaving,
            onDismiss = {
                if (!editDialogSaving) editingMods = emptyList()
            },
            onSave = save@{ replaceItems ->
                if (currentVersion != null) {
                    val selectedOriginalKeys = editingMods.map { versionModKey(it.mod) }.toSet()
                    val preservedMods = currentVersion.mods.filter { versionModKey(it) !in selectedOriginalKeys }
                    val finalMods = preservedMods + replaceItems.map(ModBatchReplaceItem::mod)
                    /*val duplicates = finalMods.groupingBy(::versionModIdentity).eachCount().filterValues { it > 1 }
                    if (duplicates.isNotEmpty()) {
                        errorMessage = "批量编辑后版本里有重复Mod，请检查slug或projectId"
                        return@save
                    }*/
                }
                editDialogSaving = true
                scope.rdiRequestU(
                    path = versionModsBatchPath(modpackId, verName),
                    method = HttpMethod.Put,
                    body = serdesJson.encodeToString(replaceItems),
                    onOk = {
                        okMessage = "已批量更新${replaceItems.size}个Mod，版本开始重构"
                        editingMods = emptyList()
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "更新Mod失败" },
                    onDone = { editDialogSaving = false }
                )
            }
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
                scope.rdiRequestU(
                    path = versionModsBatchPath(modpackId, verName),
                    method = HttpMethod.Delete,
                    body = serdesJson.encodeToString(targetRefs),
                    onOk = {
                        okMessage = "已删除${targetRefs.size}个Mod，版本开始重构"
                        selectedModKeys = selectedModKeys - deleteConfirmMods.map(UiMod::key).toSet()
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "删除Mod失败" }
                )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
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
                            Text("${index + 1}. ${state.displayName}", color = MaterialColor.GRAY_900.color)
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

private data class VersionModAddFilterResult(
    val acceptedMods: List<Mod>,
    val rejectedMessages: List<String>
)

private fun filterVersionModsForAdding(candidateMods: List<Mod>, existingMods: List<Mod>): VersionModAddFilterResult {
    val existingKeys = existingMods.map(::versionModIdentity)
        .filter { it.isNotBlank() }
        .toSet()
    val acceptedMods = mutableListOf<Mod>()
    val pendingKeys = mutableSetOf<String>()
    val rejectedMessages = mutableListOf<String>()

    candidateMods.forEach { mod ->
        val key = versionModIdentity(mod)
        if (key.isBlank()) {
            acceptedMods += mod
            return@forEach
        }
        when {
            key in existingKeys -> rejectedMessages += "${mod.displaySlugOrProject}：版本中已存在同名Mod"
            !pendingKeys.add(key) -> rejectedMessages += "${mod.displaySlugOrProject}：本次选择中已有同名Mod"
            else -> acceptedMods += mod
        }
    }

    return VersionModAddFilterResult(
        acceptedMods = acceptedMods,
        rejectedMessages = rejectedMessages.distinct()
    )
}

private fun versionModIdentity(mod: Mod): String = mod.normalizedSlug.ifBlank {
    mod.normalizedProjectId.lowercase()
}

private fun versionModKey(mod: Mod): String = "${mod.platform}:${mod.projectId}:${mod.fileId}"

private fun versionModsPath(modpackId: String, verName: String): String =
    "modpack/$modpackId/version/${verName.encodeURLPathPart()}/mods"

private fun versionModsBatchPath(modpackId: String, verName: String): String =
    "${versionModsPath(modpackId, verName)}/batch"
