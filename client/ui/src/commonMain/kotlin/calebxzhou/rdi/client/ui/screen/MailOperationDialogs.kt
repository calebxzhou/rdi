package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.copyToClipboard
import calebxzhou.rdi.common.exception.RequestError
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

fun newOperationMailTitle(): String = "rdi-opr-${ObjectId()}"

@Composable
fun MailOperationGuideDialog(
    operationName: String,
    qq: String,
    mailTitle: String,
    encryptedContent: String,
    onDismiss: () -> Unit
) {
    var copiedName by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface,
            modifier = Modifier.padding(16.dp).widthIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "${operationName}流程",
                    style = MaterialTheme.typography.h6
                )
                Text(
                    """登录QQ邮箱，向rdibot@qq.com发送邮件
标题 $mailTitle
内容 $encryptedContent
发件人选择${qq}@qq.com（不要选abc@qq.com这种字母邮箱地址）
发送后等60秒，可在本页查询${operationName}进度""",
                    style = MaterialTheme.typography.body2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    MailCopyButton("收件人", "rdibot@qq.com", copiedName) { copiedName = it }
                    MailCopyButton("标题", mailTitle, copiedName) { copiedName = it }
                    MailCopyButton("内容", encryptedContent, copiedName) { copiedName = it }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MailCopyButton(
    name: String,
    value: String,
    copiedName: String?,
    onCopied: (String) -> Unit
) {
    Button(
        onClick = {
            copyToClipboard(value)
            onCopied(name)
        },
        modifier = Modifier.weight(1f)
    ) {
        Text(if (copiedName == name) "已复制$name" else "复制$name")
    }
}

@Composable
fun ReceiptQueryDialogs(
    operationName: String,
    showQueryDialog: Boolean,
    onShowQueryDialogChange: (Boolean) -> Unit,
    onResultClosed: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var receiptMailTitle by remember { mutableStateOf("") }
    var receiptQueryLoading by remember { mutableStateOf(false) }
    var receiptQueryError by remember { mutableStateOf<String?>(null) }
    var receiptQueryResult by remember { mutableStateOf<String?>(null) }
    var showReceiptResultDialog by remember { mutableStateOf(false) }

    if (showQueryDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!receiptQueryLoading) {
                    onShowQueryDialogChange(false)
                }
            },
            title = { Text("${operationName}进度查询") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text("粘贴邮件标题，例如rdi-opr-xxxxxxxxxxxxxxxxxxxxxxxx")
                    OutlinedTextField(
                        value = receiptMailTitle,
                        onValueChange = {
                            receiptMailTitle = it
                            receiptQueryError = null
                        },
                        label = { Text("邮件标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    receiptQueryError?.let {
                        Text(it, color = MaterialTheme.colors.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !receiptQueryLoading,
                    onClick = {
                        val rawTitle = receiptMailTitle.trim()
                        val prefix = "rdi-opr-"
                        if (!rawTitle.startsWith(prefix)) {
                            receiptQueryError = "标题必须以rdi-opr-开头"
                            return@TextButton
                        }
                        val receiptId = rawTitle.removePrefix(prefix).trim()
                        if (!ObjectId.isValid(receiptId)) {
                            receiptQueryError = "标题里的回执ID无效"
                            return@TextButton
                        }
                        receiptQueryLoading = true
                        receiptQueryError = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    server.makeRequest<String>(
                                        path = "receipt/$receiptId",
                                        method = HttpMethod.Get
                                    )
                                }
                            }.onSuccess { resp ->
                                if (!resp.ok) {
                                    receiptQueryError = resp.msg.ifBlank { "查询失败" }
                                    return@onSuccess
                                }
                                receiptQueryResult = resp.data ?: ""
                                onShowQueryDialogChange(false)
                                showReceiptResultDialog = true
                            }.onFailure {
                                receiptQueryError = (it as? RequestError)?.message ?: it.message ?: "查询失败"
                            }
                            receiptQueryLoading = false
                        }
                    }
                ) {
                    Text(if (receiptQueryLoading) "查询中..." else "查询")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !receiptQueryLoading,
                    onClick = { onShowQueryDialogChange(false) }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showReceiptResultDialog) {
        fun closeResultDialog() {
            val result = receiptQueryResult.orEmpty()
            showReceiptResultDialog = false
            onResultClosed(result)
        }
        AlertDialog(
            onDismissRequest = { closeResultDialog() },
            title = { Text("${operationName}进度") },
            text = {
                Text(receiptQueryResult.orEmpty())
            },
            confirmButton = {
                TextButton(onClick = { closeResultDialog() }) {
                    Text("关闭")
                }
            }
        )
    }
}
