package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.ui.screen.importRdiModpackTask2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModpackDownloadMethodDialog(
    packName: String,
    packVer: String,
    onDismiss: () -> Unit,
    onDirectDownload: () -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null,
    onImportMessage: (String) -> Unit = {},
    onImportError: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showFriendTransferTodo by remember { mutableStateOf(false) }
    val packTitle = listOf(packName, packVer).filter(String::isNotBlank).joinToString(" ")

    fun importRdiModpack() {
        scope.launch {
            val task = withContext(Dispatchers.IO) {
                runCatching {
                    onImportMessage("开始导入...")
                    importRdiModpackTask2(onImportMessage)
                }
            }.getOrElse {
                onImportError(it.message ?: "导入失败")
                return@launch
            }
            onDismiss()
            val runId = ClientTaskManager.submit(task)
            if (onOpenTaskList != null) {
                onOpenTaskList(runId)
            } else {
                onImportMessage("已加入任务列表")
            }
        }
    }

    if (showFriendTransferTodo) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("让朋友发我") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1.让他打开资源-已安装整合包界面")
                    Text("2.选择${packTitle.ifBlank { "这个整合包" }}")
                    Text("3.点击右侧“导出RDI包”按钮")
                    Text("4.让他通过QQ等工具把包发给你")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(onClick = ::importRdiModpack) {
                    Text("5.点此选择他传完的包")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择下载方式") },
        text = { Text("请选择${packTitle.ifBlank { "整合包" }}下载方式") },
        confirmButton = {
            TextButton(onClick = onDirectDownload) {
                Text("2.直接下载")
            }
        },
        dismissButton = {
            TextButton(onClick = { showFriendTransferTodo = true }) {
                Text("1.让朋友把下完的发我，速度更快")
            }
        }
    )
}
