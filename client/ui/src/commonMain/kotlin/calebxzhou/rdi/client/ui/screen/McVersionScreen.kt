package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.McVersionCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Task2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McVersionScreen(
    onBack: () -> Unit,
    requiredMcVer: McVersion? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var titleActions by remember { mutableStateOf<ResourceScreenTitleActions?>(null) }

    MainBox {
        MainColumn {
            TitleRow("Minecraft版本", onBack) {
                titleActions?.invoke(this)
            }
            Space8h()
            McVersionPane(
                requiredMcVer = requiredMcVer,
                onOpenTaskList = onOpenTaskList,
                onTitleActionsChange = { titleActions = it }
            )
        }
    }
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
                text = "请先下载所需版本：${requiredMcVer.mcVer}",
                color = MaterialTheme.colors.error
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Space8h()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .width(540.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                McVersion.entries.forEach { mcver ->
                    McVersionCard(
                        mcver = mcver,
                        highlight = requiredMcVer == mcver,
                        onOpenFclDialog = { text, dirName ->
                            fclDialogText = text
                            fclDialogDirName = dirName
                        },
                        onOpenTaskList = onOpenTaskList
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
