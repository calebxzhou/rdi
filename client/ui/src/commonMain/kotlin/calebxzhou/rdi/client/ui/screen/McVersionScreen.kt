package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var selectedMcVer by rememberSaveable(requiredMcVer) { mutableStateOf(requiredMcVer) }

    val titleActions: ResourceScreenTitleActions? = remember(isDesktop, onOpenTaskList) {
        if (isDesktop) {
            {
                CircleIconButton("\uDB85\uDC03", "不限速网盘下载") {
                    openUrl("https://www.123865.com/s/iWSWvd-Zrtdd")
                }
                Space8w()
                CircleIconButton("\uEE38", "导入RDI资源") {
                    val files = selectRdiPackFiles() ?: return@CircleIconButton
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
                }
            }
        } else {
            null
        }
    }

    fun submitTask(task: Task2) {
        val runId = ClientTaskManager.submit(task)
        onOpenTaskList?.invoke(runId)
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
        SideEffect {
            onTitleActionsChange(titleActions)
        }
        DisposableEffect(Unit) {
            onDispose {
                onTitleActionsChange(null)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showPaneActions && titleActions != null) {
            FlowRowV(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "若下载不成功，从网盘下载，然后手动导入。（不限速，需要手机号登录，免费）"
                )
                RowV(horizontalArrangement = Arrangement.End) {
                    titleActions(this)
                }
            }
        } else {
            Text("若下载不成功，从网盘下载，然后手动导入。（不限速，需要手机号登录，免费）")
        }
        if (requiredMcVer != null) {
            Text(
                text = "MC${requiredMcVer.mcVer}版本资源需要更新。请点击下载",
                color = MaterialTheme.colors.error
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Space8h()
        McVersionActionRow(
            selectedMcVer = selectedMcVer,
            onDownloadAll = { mcver ->
                submitTask(GameService.downloadVersionTask2(mcver, mcver.firstLoader))
            },
            onDownloadAssets = { mcver ->
                val runId = ClientTaskManager.submit(
                    task = GameService.downloadAssetsOnlyTask2(mcver),
                    dedupeKey = "mc-assets:${mcver.mcVer}"
                )
                onOpenTaskList?.invoke(runId)
            },
            onInstallLoader = { mcver, loader ->
                submitTask(GameService.downloadLoaderTask2(mcver, loader))
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
            style = MaterialTheme.typography.subtitle1
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
                if (selected == null) {
                    CircleIconButton(
                        icon = "\uEEFF",
                        tooltip = "安装最新loader",
                        bgColor = MaterialColor.TEAL_900.color,
                        enabled = false
                    ) {}
                } else {
                    selected.loaderVersions.forEach { (loader, _) ->
                        CircleIconButton(
                            icon = "\uEEFF",
                            tooltip = "更新${loader.name.lowercase()}",
                            bgColor = MaterialColor.TEAL_900.color,
                            enabled = enabled
                        ) {
                            onInstallLoader(selected, loader)
                        }
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
