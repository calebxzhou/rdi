package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.firstLoader
import calebxzhou.rdi.client.model.firstLoaderVersion
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.ui.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McVersionPane(
    requiredMcVer: McVersion? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    showPaneActions: Boolean = false,
    onTitleActionsChange: (ResourceScreenTitleActions?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var fclDialogText by remember { mutableStateOf<String?>(null) }
    var fclDialogDirName by remember { mutableStateOf<String?>(null) }
    var downloadSourceDialogAction by remember { mutableStateOf<McVersionDownloadAction?>(null) }
    var showGroupFileDialog by remember { mutableStateOf(false) }
    var selectedMcVer by rememberSaveable(requiredMcVer) { mutableStateOf(requiredMcVer) }


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

    fun openFclGuide(mcver: McVersion) {
        val guideText = buildString {
            appendLine("1.打开FCL启动器")
            appendLine("2.点击左侧的\uDB80\uDD62按钮")
            appendLine("3.在上方选择“游戏”")
            appendLine("4.选择${mcver.mcVer}")
            appendLine("5.点击${mcver.firstLoader.name}")
            appendLine("6.点击版本${mcver.firstLoaderVersion.ver}")
            appendLine("7.填入名称${mcver.firstLoaderVersion.dirName}，必须一模一样，填错会导致无法启动！填错会导致无法启动！填错会导致无法启动！")
            append("8.点击名称栏右侧的\uDB80\uDDDA等待安装完成")
        }
        fclDialogText = guideText
        fclDialogDirName = mcver.firstLoaderVersion.dirName
    }

    if (!showPaneActions) {
        DisposableEffect(Unit) {
            onDispose {
                onTitleActionsChange(null)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
            onOpenFclGuide = ::openFclGuide
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

    fclDialogText?.let {
        AlertDialog(
            onDismissRequest = {
                fclDialogText = null
                fclDialogDirName = null
            },
            title = { Text("FCL下载提示") },
            text = { Text(it.asIconText, color = MaterialColor.GRAY_900.color) },
            dismissButton = {
                TextButton(onClick = {
                    fclDialogText = null
                    fclDialogDirName = null
                }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dirName = fclDialogDirName
                    if (!dirName.isNullOrBlank()) {
                        copyToClipboard(dirName)
                    }
                    openGameLauncher()
                    fclDialogText = null
                    fclDialogDirName = null
                }) {
                    Text("复制版本名称并启动FCL")
                }
            }
        )
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
                    Text("从群文件下载")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadSourceDialogAction = null
                    runMojangDownload(action)
                }) {
                    Text("从mojang官方服务器下载")
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
                        Text("MC运行资源",fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McVersionActionRow(
    selectedMcVer: McVersion?,
    onDownloadAll: (McVersion) -> Unit,
    onDownloadAssets: (McVersion) -> Unit,
    onInstallLoader: (McVersion, ModLoader) -> Unit,
    onOpenFclGuide: (McVersion) -> Unit
) {
    val selected = selectedMcVer
    val enabled = selected?.enabled == true
    FlowRowV(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = selected?.let { "已选择MC ${it.mcVer}" } ?: "请选择MC版本",
            color = if (selected == null) MaterialColor.GRAY_700.color else MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.titleMedium
        )
        RowV(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = space8
        ) {
            if (isDesktop) {
                CircleIconButton(
                    icon = "\uF019",
                    tooltip = "更新全部",
                    enabled = enabled
                ) {
                    selected?.let(onDownloadAll)
                }
                CircleIconButton(
                    icon = "\uDB80\uDF73",
                    tooltip = "更新音频",
                    bgColor = MaterialColor.BLUE_700.color,
                    enabled = enabled
                ) {
                    selected?.let(onDownloadAssets)
                }
                selected?.loaderVersions?.forEach { (loader, _) ->
                    CircleIconButton(
                        icon = "\uEEFF",
                        tooltip = "更新${loader.name.lowercase()}",
                        bgColor = MaterialColor.TEAL_900.color,
                        enabled = enabled
                    ) {
                        onInstallLoader(selected, loader)
                    }
                }
            } else {
                CircleIconButton(
                    icon = "\uF019",
                    tooltip = "使用FCL下载",
                    enabled = selected != null
                ) {
                    selected?.let(onOpenFclGuide)
                }
            }
        }
    }
}
