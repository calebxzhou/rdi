package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.server
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.util.secondsToHumanDateTime
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId


@Composable
fun MailDetailScreen(
    mailId: String,
    onBack: () -> Unit
) {
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
    mailId: String,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val oid = remember(mailId) { runCatching { ObjectId(mailId) }.getOrNull() }
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mail by remember { mutableStateOf<Mail?>(null) }

    fun loadMail() {
        if (oid == null) {
            loading = false
            errorMessage = "邮件ID无效"
            return
        }
        loading = true
        errorMessage = null
        scope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { server.makeRequest<Mail>("mail/${oid}") }.getOrNull()
            }
            loading = false
            if (response == null) {
                errorMessage = "加载邮件失败"
            } else if (!response.ok) {
                errorMessage = response.msg
            } else {
                mail = response.data
            }
        }
    }

    LaunchedEffect(mailId) { loadMail() }

    Column(modifier = modifier) {
        TitleRow(mail?.title ?: "邮件详情", onBack = onClose) {
            if (oid != null) {
                CircleIconButton(
                    icon = "\uEA81",
                    label = "删除邮件",
                    bgColor = MaterialTheme.colorScheme.error,
                ) {
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            runCatching { server.makeRequest<Unit>("mail/${oid}", HttpMethod.Delete) }.getOrNull()
                        }
                        if (response == null || !response.ok) {
                            errorMessage = response?.msg ?: "删除失败"
                        } else {
                            onDeleted()
                        }
                    }
                }
            }
        }

        ContentBody {
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text(errorMessage ?: "加载失败", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { loadMail() }) {
                        Text("重试")
                    }
                }
                mail != null -> {
                    Text("发件人: ${mail!!.senderId}")
                    Text("时间: ${mail!!._id.timestamp.secondsToHumanDateTime}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            val lines = mail!!.content
                                .trimStart('\n', '\r')
                                .split('\n')
                            itemsIndexed(lines) { idx, line ->
                                Text(
                                    text = line,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal
                                )
                                if (idx != lines.lastIndex) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            RVerticalScrollbar(
                                listState = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
