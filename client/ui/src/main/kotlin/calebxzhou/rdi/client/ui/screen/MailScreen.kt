package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.wM
import calebxzhou.rdi.client.net.rdiResponse
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.util.toFriendlyDateTime
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-13 23:19
 */

@Composable
fun MailScreen(
    onBack: () -> Unit = {}
) {
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("信箱", onBack = onBack)
            ContentBody {
                MailContent(modifier = Modifier.fillMaxSize())
            }
        }
    }
}


@Composable
private fun MailContent(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var mails by remember { mutableStateOf<List<Mail.Vo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<ObjectId>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedMailId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun reload() {
        loading = true
        errorMessage = null
        scope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { server.makeRequest<List<Mail.Vo>>("mail") }.getOrNull()
            }
            loading = false
            if (response == null) {
                errorMessage = "加载信箱失败"
                mails = emptyList()
                selectedIds = emptySet()
                return@launch
            }
            if (!response.ok) {
                errorMessage = response.msg
                mails = emptyList()
                selectedIds = emptySet()
                return@launch
            }
            mails = response.data ?: emptyList()
            val visible = mails.mapTo(mutableSetOf()) { it.id }
            selectedIds = selectedIds.filter { it in visible }.toSet()
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val allSelected = mails.isNotEmpty() && selectedIds.size == mails.size

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            FlowRowV(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(12.wM)
                    }
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) {
                                mails.map { it.id }.toSet()
                            } else {
                                emptySet()
                            }
                        }
                    )
                    Text("全选", style = MaterialTheme.typography.bodyMedium)
                    Spacer(12.wM)
                    CircleIconButton(
                        "\uEA81",
                        "删除所选邮件",
                        enabled = selectedIds.isNotEmpty(),
                        contentPadding = PaddingValues(start = 1.dp, top = 0.dp, end = 0.dp, bottom = 1.dp),
                        bgColor = MaterialTheme.colorScheme.error
                    ) {
                        if (selectedIds.isEmpty()) {
                            errorMessage = "请选择至少一封邮件"
                        } else {
                            confirmDelete = true
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!loading && mails.isEmpty()) {
                Text("什么都没有~", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                ) {
                    items(mails, key = { it.id.toHexString() }) { mail ->
                        MailListItem(
                            mail = mail,
                            selected = selectedIds.contains(mail.id),
                            onSelectedChange = { checked ->
                                selectedIds = if (checked) {
                                    selectedIds + mail.id
                                } else {
                                    selectedIds - mail.id
                                }
                            },
                            onOpen = { selectedMailId = mail.id.toHexString() }
                        )
                    }
                }
                RVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        selectedMailId?.let { mailId ->
            MailDetailOverlay(
                mailId = mailId,
                onClose = { selectedMailId = null },
                onDeleted = {
                    selectedMailId = null
                    reload()
                }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除") },
            text = { Text("要删除所选的邮件吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val payload = selectedIds.toList().json
                        val response = withContext(Dispatchers.IO) {
                            runCatching {
                                server.createRequest("mail", HttpMethod.Delete) {
                                    json()
                                    setBody(payload)
                                }.rdiResponse<Unit>()
                            }.getOrNull()
                        }
                        if (response == null) {
                            errorMessage = "删除失败"
                            return@launch
                        }
                        if (!response.ok) {
                            errorMessage = response.msg
                            return@launch
                        }
                        selectedIds = emptySet()
                        reload()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MailListItem(
    mail: Mail.Vo,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val timeText = (mail.id.timestamp.toLong() * 1000L).toFriendlyDateTime()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectedChange
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mail.title,
                        modifier = Modifier.widthIn(max = 220.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Space8w()
                    Text(
                        text = mail.intro,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            RRow {
                Text(
                    text = mail.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Space8w()
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BoxScope.MailDetailOverlay(
    mailId: String,
    onClose: () -> Unit,
    onDeleted: () -> Unit
) {
    val dimInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = dimInteractionSource,
                indication = null,
                onClick = onClose
            )
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .fillMaxHeight(0.8f)
            .align(Alignment.Center)
            .clickable(
                interactionSource = panelInteractionSource,
                indication = null,
                onClick = {}
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        MailDetailPanel(
            mailId = mailId,
            onClose = onClose,
            onDeleted = onDeleted,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        )
    }
}
