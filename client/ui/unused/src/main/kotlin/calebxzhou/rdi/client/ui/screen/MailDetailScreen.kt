package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.service.SocialService
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.MailAction
import calebxzhou.rdi.common.util.toFriendlyDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun MailDetailScreen(mailId: String, onBack: () -> Unit) {
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            MailDetailPanel(
                mailId = mailId,
                onClose = onBack,
                onDeleted = onBack,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MailDetailPanel(
    mailId: UUID,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
    onChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mail by remember { mutableStateOf<Mail.Dto?>(null) }
    var confirmReject by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }

    fun loadMail() {
        loading = true
        errorMessage = null
        scope.launch {
            SocialService.loadMail(mailId).fold(
                onSuccess = { mail = it; loading = false; onChanged() },
                onFailure = { errorMessage = it.message ?: "加载邮件失败"; loading = false }
            )
        }
    }

    fun act(action: MailAction) {
        actionLoading = true
        scope.launch {
            SocialService.actOnMail(mailId, action).fold(
                onSuccess = {
                    actionLoading = false
                    if (action == MailAction.Reject) onDeleted() else {
                        loadMail()
                    }
                },
                onFailure = { errorMessage = it.message ?: "处理好友申请失败"; actionLoading = false }
            )
        }
    }

    fun deleteMail() {
        scope.launch {
            SocialService.deleteMail(mailId).fold(
                onSuccess = { onDeleted() },
                onFailure = { errorMessage = it.message ?: "删除失败" }
            )
        }
    }

    LaunchedEffect(mailId) { loadMail() }
    Column(modifier = modifier) {
        TitleRow(mail?.title ?: "邮件详情", onBack = onClose) {
            CircleIconButton(icon = "\uEA81", label = "删除邮件", bgColor = MaterialTheme.colorScheme.error) {
                if (mail?.kind == calebxzhou.rdi.common.model.MailKind.FriendRequest &&
                    mail?.requestStatus == calebxzau.rdi.common.model.FriendRequestStatus.Pending) {
                    confirmDelete = true
                } else deleteMail()
            }
        }
        ContentBody {
            when {
                loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                errorMessage != null -> {
                    Text(errorMessage ?: "加载失败", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { loadMail() }) { Text("重试") }
                }
                mail != null -> {
                    val value = mail!!
                    Text("发件人: ${value.senderName}")
                    Text("时间: ${value.createdAt.toFriendlyDateTime()}")
                    if (value.actions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            value.actions.distinct().forEach { action ->
                                when (action) {
                                    MailAction.Accept -> TextButton(enabled = !actionLoading, onClick = { act(action) }) { Text("接受") }
                                    MailAction.Reject -> TextButton(enabled = !actionLoading, onClick = { confirmReject = true }) { Text("拒绝", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().weight(1f, fill = true)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            val lines = value.content.trimStart('\n', '\r').split('\n')
                            itemsIndexed(lines) { idx, line ->
                                Text(line, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal)
                                if (idx != lines.lastIndex) Spacer(Modifier.height(2.dp))
                            }
                        }
                        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(14.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            RVerticalScrollbar(listState = listState, modifier = Modifier.fillMaxHeight().padding(2.dp))
                        }
                    }
                }
            }
        }
    }
    if (confirmReject) {
        AlertDialog(
            onDismissRequest = { confirmReject = false },
            title = { Text("拒绝好友申请") },
            text = { Text("确定拒绝这条好友申请吗？") },
            confirmButton = {
                TextButton(onClick = { confirmReject = false; act(MailAction.Reject) }) {
                    Text("拒绝", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReject = false }) { Text("取消") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除好友申请") },
            text = { Text("删除这条待处理申请将视为拒绝，确定继续吗？") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; deleteMail() }) {
                    Text("删除并拒绝", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
fun MailDetailPanel(
    mailId: String,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
    onChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val parsedId = remember(mailId) { runCatching { UUID.fromString(mailId) }.getOrNull() }
    if (parsedId == null) {
        Text("邮件ID无效", color = MaterialTheme.colorScheme.error, modifier = modifier.padding(20.dp))
    } else {
        MailDetailPanel(parsedId, onClose, onDeleted, onChanged, modifier)
    }
}
