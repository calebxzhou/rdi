package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.RColumn
import calebxzhou.rdi.client.ui.RRow
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.copyToClipboard
import calebxzhou.rdi.client.ui.space8
import calebxzhou.rdi.common.exception.RequestError
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

fun newOperationMailTitle(): String = "rdi-opr-${ObjectId()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailOperationGuideDialog(
    operationName: String,
    qq: String,
    mailTitle: String,
    encryptedContent: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp).widthIn(max = 600.dp)
        ) {
            RColumn{
                Text(
                    "请登录QQ邮箱，发邮件",
                    style = MaterialTheme.typography.titleLarge
                )
                RRow {
                    MailCopyButton("rdibot@qq.com")
                    Text("收件人 rdibot@qq.com")
                }
                RRow {
                    MailCopyButton(mailTitle)
                    Text("标题")
                    Text(mailTitle, fontSize = 16.sp)
                }
                RRow {
                    MailCopyButton(encryptedContent)
                    Text("内容")
                    Text(encryptedContent, fontSize = 8.sp)
                }
                Text("发件人 ${qq}@qq.com 请勿选择abcdefg@qq.com等字母邮箱地址")
                Text("发送后等60~120秒，可在本页查询${operationName}进度")
                CircleIconButton("\uF00D","关闭"){
                    onDismiss()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.MailCopyButton(
    value: String
) {
    CircleIconButton("\uF0C5","复制",size = 24, showText = false){
        copyToClipboard(value)
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
            title = { Text("请打开QQ邮箱“已发送”界面") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text("复制已发邮件标题，粘贴到下方")
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
                        Text(it, color = MaterialTheme.colorScheme.error)
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
