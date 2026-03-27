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
import calebxzhou.rdi.common.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McVersionScreen(
    onBack: () -> Unit,
    requiredMcVer: McVersion? = null,
    onOpenTask: ((Task) -> Unit)? = null
) {
    var fclDialogText by remember { mutableStateOf<String?>(null) }
    var fclDialogDirName by remember { mutableStateOf<String?>(null) }

    MainBox {
        MainColumn {
            TitleRow("Minecraft版本", onBack) {
                Text("需要先下载对应MC版本的资源，才能玩整合包。")
                if (isDesktop) {
                    Space8w()
                    CircleIconButton("\uDB85\uDC03", "从网盘下载") {
                        openUrl("https://www.123865.com/s/iWSWvd-Zrtdd")
                    }
                    Space8w()
                    CircleIconButton("\uEE38", "导入MC版本") {
                        val files = selectRdiPackFiles() ?: return@CircleIconButton
                        val task = if (files.size == 1) {
                            buildImportPackTask(files.first())
                        } else {
                            Task.Sequence(
                                name = "导入MC版本",
                                subTasks = files.map { buildImportPackTask(it) }
                            )
                        }
                        onOpenTask?.invoke(task)
                    }
                }
            }
            Space8h()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1040.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text("若下载不成功，可尝试从网盘下载，然后手动导入。")
                    if (requiredMcVer != null) {
                        Text(
                            text = "请先下载所需版本：${requiredMcVer.mcVer}",
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(McVersion.entries) { mcver ->
                            McVersionCard(
                                mcver = mcver,
                                highlight = requiredMcVer == mcver,
                                onOpenTask = onOpenTask,
                                onOpenFclDialog = { text, dirName ->
                                    fclDialogText = text
                                    fclDialogDirName = dirName
                                }
                            )
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
