package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.space8
import calebxzhou.rdi.client.model.firstLoader
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.ui.*
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.client.ui.comp.McVersionCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Task2

private sealed interface McVersionDownloadAction {
    val mcVer: McVersion

    data class All(override val mcVer: McVersion) : McVersionDownloadAction
    data class Assets(override val mcVer: McVersion) : McVersionDownloadAction
    data class Loader(override val mcVer: McVersion, val loader: ModLoader) : McVersionDownloadAction
}


@Composable
fun McVersionPane(
    requiredMcVer: McVersion? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    showPaneActions: Boolean = false,
    onTitleActionsChange: (ResourceScreenTitleActions?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var downloadSourceDialogAction by remember { mutableStateOf<McVersionDownloadAction?>(null) }
    var showGroupFileDialog by remember { mutableStateOf(false) }
    var selectedMcVer by rememberSaveable(requiredMcVer) { mutableStateOf(requiredMcVer) }
    var showAdvancedActions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }


    fun submitTask(task: Task2) {
        val runId = ClientTaskManager.submit(task)
        onOpenTaskList?.invoke(runId)
    }

    fun submitAssetsTask(mcver: McVersion) {
        val runId = ClientTaskManager.submit(
            task = GameService.downloadAssetsOnlyTask2(mcver),
            dedupeKey = "mc-assets:${mcver.mcVer}"
        )
        onOpenTaskList?.invoke(runId)
    }

    fun runMojangDownload(action: McVersionDownloadAction) {
        when (action) {
            is McVersionDownloadAction.All -> {
                submitTask(GameService.downloadVersionTask2(action.mcVer, action.mcVer.firstLoader))
            }

            is McVersionDownloadAction.Assets -> {
                submitAssetsTask(action.mcVer)
            }

            is McVersionDownloadAction.Loader -> {
                submitTask(GameService.downloadLoaderTask2(action.mcVer, action.loader))
            }
        }
    }

    if (!showPaneActions) {
        DisposableEffect(Unit) {
            onDispose {
                onTitleActionsChange(null)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.ShiftLeft && event.type == KeyEventType.KeyDown -> {
                        showAdvancedActions = true
                        false
                    }
                    event.key == Key.ShiftRight && event.type == KeyEventType.KeyDown -> {
                        showAdvancedActions = true
                        false
                    }
                    event.key == Key.ShiftLeft && event.type == KeyEventType.KeyUp -> {
                        showAdvancedActions = false
                        false
                    }
                    event.key == Key.ShiftRight && event.type == KeyEventType.KeyUp -> {
                        showAdvancedActions = false
                        false
                    }
                    else -> false
                }
            }
    ) {
        if (requiredMcVer != null) {
            Text(
                text = "MC${requiredMcVer.mcVer}版本资源需要更新。请点击下载",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Space8h()
        McVersionActionRow(
            selectedMcVer = selectedMcVer,
            onDownloadAll = { mcver ->
                downloadSourceDialogAction = McVersionDownloadAction.All(mcver)
            },
            onDownloadAssets = { mcver ->
                downloadSourceDialogAction = McVersionDownloadAction.Assets(mcver)
            },
            onInstallLoader = { mcver, loader ->
                downloadSourceDialogAction = McVersionDownloadAction.Loader(mcver, loader)
            },
            showAdvancedActions = showAdvancedActions
        )
        Space8h()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(McVersion.entries, key = { it.mcVer }) { mcver ->
                    McVersionCard(
                        mcver = mcver,
                        highlight = selectedMcVer == mcver,
                        onClick = { selectedMcVer = mcver }
                    )
                }
            }
        }
    }

    downloadSourceDialogAction?.let { action ->
        AlertDialog(
            onDismissRequest = { downloadSourceDialogAction = null },
            title = { Text("要从哪里下载？") },
            text = { Text("请选择MC${action.mcVer.mcVer}版本资源下载来源") },
            dismissButton = {
                TextButton(onClick = {
                    downloadSourceDialogAction = null
                    showGroupFileDialog = true
                }) {
                    Text("从群文件下载(非常快)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadSourceDialogAction = null
                    runMojangDownload(action)
                }) {
                    Text("从ojng国外服务器下载(很慢)")
                }
            }
        )
    }

    if (showGroupFileDialog) {
        AlertDialog(
            onDismissRequest = { showGroupFileDialog = false },
            title = { Text("请打开RDI群文件") },
            text = {
                Column {
                    RRow {
                        Text("1.打开")
                        Text("MC运行资源", fontWeight = FontWeight.Bold)
                        Text("文件夹")
                    }
                    Text("2.下载${selectedMcVer?.simpleVer?:"对应MC版本"}.rdimcpack文件")
                    Text("3.耐心等待下载完成")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showGroupFileDialog = false
                    val files = selectRdiPackFiles() ?: return@TextButton
                    val task = if (files.size == 1) {
                        buildImportPackTask2(files.first())
                    } else {
                        Task2.Sequence(
                            title = "导入MC版本",
                            children = files.map { buildImportPackTask2(it) }
                        )
                    }
                    val runId = ClientTaskManager.submit(task)
                    onOpenTaskList?.invoke(runId)
                }) {
                    Text("4.点此选择已下载好的文件")
                }
            }
        )
    }
}


@Composable
private fun McVersionActionRow(
    selectedMcVer: McVersion?,
    onDownloadAll: (McVersion) -> Unit,
    onDownloadAssets: (McVersion) -> Unit,
    onInstallLoader: (McVersion, ModLoader) -> Unit,
    showAdvancedActions: Boolean
) {
    val selected = selectedMcVer
    val enabled = selected?.enabled == true
    FlowRowV(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = selected?.let { "已选择MC ${it.mcVer}" } ?: "请选择MC版本",
            style = MaterialTheme.typography.titleMedium
        )
        RowV(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = space8
        ) {
            CircleIconButton(
                icon = "\uF019",
                label = "更新全部",
                enabled = enabled
            ) {
                selected?.let(onDownloadAll)
            }
            if (showAdvancedActions) {
                CircleIconButton(
                    icon = "\uDB80\uDF73",
                    label = "更新音频",
                    bgColor = MaterialTheme.colorScheme.primary,
                    enabled = enabled
                ) {
                    selected?.let(onDownloadAssets)
                }
                selected?.loaderVersions?.forEach { (loader, _) ->
                    CircleIconButton(
                        icon = "\uEEFF",
                        label = "更新${loader.name.lowercase()}",
                        bgColor = themeNow.tertiary,
                        enabled = enabled
                    ) {
                        onInstallLoader(selected, loader)
                    }
                }
            }
        }
    }
}
