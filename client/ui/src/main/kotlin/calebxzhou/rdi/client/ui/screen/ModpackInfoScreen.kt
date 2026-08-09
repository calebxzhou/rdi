package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TinyClickCopyText
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.*
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.isDav
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoEvent
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoViewModel
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.text.isNotBlank

/**
 * calebxzhou @ 2026-01-17 20:44
 */
@Composable
fun ModpackInfoScreen(
    modpackId: String,
    onBack: () -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null,
    onOpenVersionEdit: ((String) -> Unit)? = null,
    viewModel: ModpackInfoViewModel = koinViewModel(key = modpackId) {
        parametersOf(modpackId)
    },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDeletePack by remember { mutableStateOf(false) }
    var confirmDeleteVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var confirmRebuildVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var confirmRedownloadVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    val versionListState = rememberLazyListState()
    var downloadMethodVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var dialogErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(dialogMessage) {
        dialogMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            dialogMessage = null
        }
    }

    LaunchedEffect(viewModel, onBack, onOpenTaskList) {
        viewModel.events.collect { event ->
            when (event) {
                is ModpackInfoEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                }

                is ModpackInfoEvent.EditSaved -> {
                    showEditDialog = false
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                }

                is ModpackInfoEvent.InstallQueued -> {
                    if (onOpenTaskList != null) {
                        onOpenTaskList(event.runId)
                    } else {
                        snackbarHostState.showSnackbar("已加入任务列表", duration = SnackbarDuration.Short)
                    }
                }

                is ModpackInfoEvent.SelectDownloadMethod -> {
                    downloadMethodVersion = viewModel.uiState.value.pack?.versions
                        ?.firstOrNull { it.name == event.versionName }
                }

                is ModpackInfoEvent.ConfirmRedownload -> {
                    confirmRedownloadVersion = viewModel.uiState.value.pack?.versions
                        ?.firstOrNull { it.name == event.versionName }
                }

                ModpackInfoEvent.PackDeleted -> onBack()
            }
        }
    }
    val pack = uiState.pack
    val isAuthor = pack?.let {  it.authorId == loggedAccount._id || loggedAccount.isDav } ?: false

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(pack?.let { "整合包 · ${it.name}" } ?: "整合包详情", onBack) {
                (uiState.errorMessage ?: dialogErrorMessage)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                pack?.let { pack ->
                    TinyClickCopyText("mid", pack._id.toString())
                    HeadButton(pack.authorId, showName = false)
                    Text(pack.mcVer.simpleVer)
                }
                if (isAuthor) {
                    CircleIconButton(
                        icon = "\uF01F",
                        tooltip = "修改信息",
                        showText = false,
                        bgColor = themeNow.tertiary
                    ) {
                        dialogErrorMessage = null
                        viewModel.beginEdit()
                        showEditDialog = true
                    }
                    CircleIconButton(
                        icon = "\uEA81",
                        tooltip = "删除整合包",
                        bgColor = MaterialTheme.colorScheme.error,
                        showText = false
                    ) { confirmDeletePack = true }


                }
            }
            ContentBody {
                if (uiState.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!uiState.loading && pack == null) {
                Text("未找到整合包信息")
            }

            if (pack != null) {
                val tabTitles = buildList {
                    add("简介")
                    add("Mod列表(${pack.modCount})")
                    add("\uF019 下载版本(${pack.versions.size})")
                }
                val activeTab = selectedTab.takeIf { it in tabTitles.indices } ?: 0
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Space8h()
                when (activeTab) {
                    0 -> {
                        ModpackIntroTabContent(
                            pack = pack
                        )
                    }

                    1 -> {
                        if (pack.categories.isNotEmpty()) {
                            ModpackCategoryChips(
                                categories = pack.categories,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        if (pack.versions.isEmpty()) {
                            Text("此整合包暂无可用版本，等待作者上传....", color = Color.Gray)
                        } else {
                            if (uiState.modsLoading) {
                                Text("正在载入${pack.modCount}个Mod的详细信息...")
                            }
                            Space8h()
                            ModGrid(
                                mods = uiState.mods,
                                emptyText = "没有可显示的mod"
                            )
                        }
                    }

                    else -> {
                        Space8h()
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                state = versionListState,
                                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pack.versions, key = { it.name }) { version ->
                                val statusText = when (version.status) {
                                    Modpack.Status.OK -> "\uF058 可用"
                                    Modpack.Status.BUILDING -> "\uEEFF 构建中"
                                    Modpack.Status.FAIL -> "\uEA87 构建失败"
                                    Modpack.Status.WAIT -> "\uE641 等待构建"
                                }
                                val statusColor = when (version.status) {
                                    Modpack.Status.OK -> themeNow.primary
                                    Modpack.Status.BUILDING -> themeNow.tertiary
                                    Modpack.Status.FAIL -> MaterialTheme.colorScheme.error
                                    Modpack.Status.WAIT -> themeNow.onSurfaceVariant
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "V${version.name} - \uE641 ${version.time.millisToHumanDateTime} - \uF0C7${version.totalSize?.humanFileSize ?: ""}".asIconText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(statusText.asIconText, color = statusColor)
                                    Space8w()
                                    if (isAuthor) {
                                        onOpenVersionEdit?.let { openVersionEdit ->
                                            CircleIconButton(
                                                icon = "\uF044",
                                                label = "编辑版本Mod",
                                                bgColor = themeNow.secondary
                                            ) { openVersionEdit(version.name) }
                                            Space8w()
                                        }
                                        CircleIconButton(
                                            icon = "\uEA81",
                                            label = "删除版本",
                                            bgColor = MaterialTheme.colorScheme.error
                                        ) { confirmDeleteVersion = version }
                                        Space8w()
                                        CircleIconButton(
                                            icon = "\uF0AD",
                                            label = "重构",
                                            bgColor = MaterialTheme.colorScheme.primary
                                        ) { confirmRebuildVersion = version }
                                    }
                                    if (version.status == Modpack.Status.OK) {
                                        Space8w()
                                        CircleIconButton(
                                            icon = "\uF019",
                                            label = "下载整合包"
                                        ) {
                                            viewModel.requestDownload(version.name)
                                        }
                                    }
                                }
                            }
                                item {
                                    if (pack.versions.isEmpty()) {
                                        Text("此整合包暂无可用版本，等待作者上传....", color = Color.Gray)
                                    }
                                }
                            }
                            RVerticalScrollbar(
                                listState = versionListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }

    if (confirmDeletePack && pack != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePack = false },
            title = { Text("确认删除") },
            text = { Text("确定要永久删除整合包 ${pack.name} 吗？无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePack = false
                    viewModel.deletePack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePack = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditDialog && pack != null) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.savingEdit) showEditDialog = false
            },
            title = { Text("修改整合包信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = uiState.editDraft.name,
                        onValueChange = {
                            viewModel.updateEditDraft(uiState.editDraft.copy(name = it))
                        },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.editDraft.iconUrl,
                        onValueChange = {
                            viewModel.updateEditDraft(uiState.editDraft.copy(iconUrl = it))
                        },
                        label = { Text("图标链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.editDraft.sourceUrl,
                        onValueChange = {
                            viewModel.updateEditDraft(uiState.editDraft.copy(sourceUrl = it))
                        },
                        label = { Text("来源链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.editDraft.info,
                        onValueChange = {
                            viewModel.updateEditDraft(uiState.editDraft.copy(info = it))
                        },
                        label = { Text("简介") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("分类 最多${Modpack.MAX_CATEGORY_COUNT}个")
                    ModpackCategorySelector(
                        selected = uiState.editDraft.categories,
                        onSelectedChange = {
                            viewModel.updateEditDraft(uiState.editDraft.copy(categories = it))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::saveEdit,
                    enabled = !uiState.savingEdit,
                ) {
                    Text(if (uiState.savingEdit) "保存中..." else "保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    enabled = !uiState.savingEdit,
                ) {
                    Text("取消")
                }
            }
        )
    }

    confirmRedownloadVersion?.let { version ->
        val currentPack = pack
        if (currentPack != null) {
            ConfirmDialog(
                title = "确认重新下载",
                message = "整合包版本 ${version.name} 已存在，是否重新下载？",
                onConfirm = {
                    confirmRedownloadVersion = null
                    downloadMethodVersion = version
                },
                onDismiss = { confirmRedownloadVersion = null }
            )
        } else {
            confirmRedownloadVersion = null
        }
    }

    downloadMethodVersion?.let { version ->
        val currentPack = pack
        if (currentPack != null) {
            ModpackDownloadMethodDialog(
                packName = currentPack.name,
                packVer = version.name,
                onDismiss = { downloadMethodVersion = null },
                onDirectDownload = {
                    downloadMethodVersion = null
                    viewModel.installVersion(version.name)
                },
                onOpenTaskList = onOpenTaskList,
                onImportMessage = { dialogMessage = it },
                onImportError = { dialogErrorMessage = it }
            )
        }
    }

    confirmDeleteVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmDeleteVersion = null },
            title = { Text("确认删除版本") },
            text = { Text("确定要永久删除版本 V${version.name} 吗？无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteVersion = null
                    viewModel.deleteVersion(version.name)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteVersion = null }) {
                    Text("取消")
                }
            }
        )
    }

    confirmRebuildVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmRebuildVersion = null },
            title = { Text("确认重构版本") },
            text = { Text("整合包出现mod不完整等问题，可重构以解决。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRebuildVersion = null
                    viewModel.rebuildVersion(version.name)
                }) {
                    Text("重构")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRebuildVersion = null }) {
                    Text("取消")
                }
            }
        )
    }

}

@Composable
private fun ModpackIntroTabContent(
    pack: Modpack.DetailVo
) {
    val sourceUrl = pack.sourceUrl?.trim()?.takeIf(String::isNotBlank)
    if (sourceUrl != null) {
        WebPagePane(
            url = sourceUrl,
            title = sourceUrl ?: "来源网页",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val scrollState = rememberScrollState()
    val displaySummary = pack.info?.takeIf(String::isNotBlank)

    RScrollableColumn(
        modifier = Modifier.fillMaxWidth(),
        state = scrollState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pack.categories.isNotEmpty()) {
            ModpackCategoryChips(categories = pack.categories)
        }
        displaySummary?.let {
            Text(it)
        }
        if (displaySummary == null) {
            Text("无")
        }
    }
}
