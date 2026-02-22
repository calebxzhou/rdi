package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.secondsToHumanDateTime
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.common.net.json
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-13 23:19
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    onBack: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var mails by remember { mutableStateOf<List<Mail.Vo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<ObjectId>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

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

    MainColumn {
        TitleRow("信箱", onBack = onBack) {
            errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
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
            Text("全选")
            Spacer(12.wM)
            CircleIconButton(
                "\uEA81",
                "删除所选邮件",
                enabled = selectedIds.isNotEmpty(),
                contentPadding = PaddingValues(start = 1.dp, top = 0.dp, end = 0.dp, bottom = 1.dp),
                bgColor = MaterialColor.RED_900.color
            ) {
                if (selectedIds.isEmpty()) {
                    errorMessage = "请选择至少一封邮件"
                } else {
                    confirmDelete = true
                }
            }
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }



        if (!loading && mails.isEmpty()) {
            Text("什么都没有~", color = Color.Black)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mails, key = { it.id.toHexString() }) { mail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(mail.id.toHexString()) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(mail.id),
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) {
                                    selectedIds + mail.id
                                } else {
                                    selectedIds - mail.id
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mail.title,
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${mail.intro}...",
                                style = MaterialTheme.typography.body2,
                                color = MaterialColor.GRAY_500.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(mail.senderName, style = MaterialTheme.typography.caption)
                        Text(
                            text = mail.id.timestamp.secondsToHumanDateTime,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
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
                                }.body<Response<Unit>>()
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
                    Text("删除", color = MaterialTheme.colors.error)
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
