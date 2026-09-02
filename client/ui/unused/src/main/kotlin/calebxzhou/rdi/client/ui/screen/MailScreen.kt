package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.service.SocialService
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.wM
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.MailKind
import calebxzau.rdi.common.model.FriendRequestStatus
import calebxzhou.rdi.common.util.toFriendlyDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAIL_PAGE_SIZE = 100

@Composable
fun MailScreen(onBack: () -> Unit = {}) {
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("信箱", onBack = onBack)
            ContentBody { MailContent(modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun MailContent(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var mails by remember { mutableStateOf<List<Mail.Vo>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var page by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedMailId by remember { mutableStateOf<UUID?>(null) }

    fun loadPage(nextPage: Int, replace: Boolean) {
        if (nextPage > 0 && (loadingMore || !hasMore)) return
        if (replace) loading = true else loadingMore = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { SocialService.loadMails(nextPage) }
            result.fold(
                onSuccess = { loaded ->
                    mails = if (replace) loaded else (mails + loaded).distinctBy { it.id }
                    page = nextPage
                    hasMore = loaded.size >= MAIL_PAGE_SIZE
                    selectedIds = selectedIds.intersect(mails.mapTo(hashSetOf()) { it.id })
                    loading = false
                    loadingMore = false
                },
                onFailure = { cause ->
                    errorMessage = cause.message ?: "加载信箱失败"
                    loading = false
                    loadingMore = false
                }
            )
        }
    }

    LaunchedEffect(Unit) { loadPage(0, replace = true) }
    LaunchedEffect(listState, hasMore, loadingMore, mails.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { it ?: -1 }
            .distinctUntilChanged()
            .filter { it >= mails.lastIndex && it >= 0 }
            .collect { loadPage(page + 1, replace = false) }
    }

    val allSelected = mails.isNotEmpty() && selectedIds.size == mails.size
    val selectedPending = mails.any { it.id in selectedIds && it.kind == MailKind.FriendRequest && it.requestStatus == FriendRequestStatus.Pending }
    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            FlowRowV(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(12.wM)
                    }
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) mails.mapTo(hashSetOf()) { it.id } else emptySet()
                        }
                    )
                    Text("全选", style = MaterialTheme.typography.bodyMedium)
                    Spacer(12.wM)
                    CircleIconButton(
                        icon = "\uEA81",
                        label = "删除所选邮件",
                        enabled = selectedIds.isNotEmpty(),
                        bgColor = MaterialTheme.colorScheme.error
                    ) { confirmDelete = true }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            } else if (mails.isEmpty() && errorMessage == null) {
                Text("什么都没有~", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                ) {
                    items(mails, key = { it.id }) { mail ->
                        MailListItem(
                            mail = mail,
                            selected = mail.id in selectedIds,
                            onSelectedChange = { checked ->
                                selectedIds = if (checked) selectedIds + mail.id else selectedIds - mail.id
                            },
                            onOpen = { selectedMailId = mail.id }
                        )
                    }
                    if (loadingMore) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
                RVerticalScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        selectedMailId?.let { mailId ->
            MailDetailOverlay(
                mailId = mailId,
                onClose = { selectedMailId = null },
                onDeleted = { selectedMailId = null; loadPage(0, replace = true) },
                onChanged = { loadPage(0, replace = true) }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除") },
            text = {
                Text(if (selectedPending) "所选邮件包含待处理好友申请，删除后将视为拒绝。确定继续吗？" else "要删除所选的邮件吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = selectedIds
                    scope.launch {
                        SocialService.deleteMails(ids).fold(
                            onSuccess = { selectedIds = emptySet(); loadPage(0, replace = true) },
                            onFailure = { errorMessage = it.message ?: "删除失败" }
                        )
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun MailListItem(
    mail: Mail.Vo,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = if (mail.unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(mail.title, Modifier.widthIn(max = 220.dp), style = MaterialTheme.typography.titleMedium, fontWeight = if (mail.unread) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Space8w()
                Text(mail.intro, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = mail.requestStatus
                if (mail.kind == MailKind.FriendRequest && status != null) {
                    Spacer(Modifier.width(8.dp))
                    MailStatusChip(status)
                }
            }
            Spacer(Modifier.width(12.dp))
            RRow {
                Text(mail.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Space8w()
                Text(mail.createdAt.toFriendlyDateTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MailStatusChip(status: FriendRequestStatus) {
    AssistChip(onClick = {}, enabled = false, label = { Text(status.label()) })
}

private fun FriendRequestStatus.label(): String = when (this) {
    FriendRequestStatus.Pending -> "待处理"
    FriendRequestStatus.Accepted -> "已接受"
    FriendRequestStatus.Rejected -> "已拒绝"
    FriendRequestStatus.Expired -> "已过期"
}

@Composable
private fun BoxScope.MailDetailOverlay(
    mailId: UUID,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    onChanged: () -> Unit,
) {
    val dimInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable(dimInteractionSource, indication = null, onClick = onClose))
    Surface(
        modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f).align(Alignment.Center).clickable(panelInteractionSource, indication = null, onClick = {}),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        MailDetailPanel(mailId, onClose, onDeleted, onChanged, Modifier.fillMaxSize().padding(20.dp))
    }
}
