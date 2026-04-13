package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var fclDialogText by remember { mutableStateOf<String?>(null) }
    var fclDialogDirName by remember { mutableStateOf<String?>(null) }

    MainBox {
        MainColumn {
            TitleRow("Minecraft版本", onBack) {
                if (isDesktop) {
                    Space8w()
                    CircleIconButton("\uDB85\uDC03", "不限速网盘下载") {
                        openUrl("https://www.123865.com/s/iWSWvd-Zrtdd")
                    }
                    Space8w()
                    CircleIconButton("\uEE38", "网盘下载完的导入") {
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
            }
            Space8h()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1040.dp)
                        .padding(bottom = 16.dp)
                ) {
                    val portrait = maxHeight > maxWidth
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("若下载不成功，从网盘下载，然后手动导入。（不限速，需要手机号登录，免费）")
                        if (requiredMcVer != null) {
                            Text(
                                text = "请先下载所需版本：${requiredMcVer.mcVer}",
                                color = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Space8h()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (portrait) 1 else 2),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(McVersion.entries) { mcver ->
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
