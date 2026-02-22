package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.secondsToHumanDateTime
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.PlatformVerticalScrollbar
import calebxzhou.rdi.common.model.Mail
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    mailId: String,
    onBack: () -> Unit
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

    MainColumn {
        TitleRow(mail?.title ?: "邮件详情", onBack = onBack) {
            if (oid != null) {
                CircleIconButton(
                    icon = "\uEA81",
                    tooltip = "删除邮件",
                    bgColor = MaterialColor.RED_900.color,
                ) {
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            runCatching { server.makeRequest<Unit>("mail/${oid}", HttpMethod.Delete) }.getOrNull()
                        }
                        if (response == null || !response.ok) {
                            errorMessage = response?.msg ?: "删除失败"
                        } else {
                            onBack()
                        }
                    }
                }
            }
        }

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
                Text(errorMessage ?: "加载失败", color = MaterialTheme.colors.error)
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
                                color = Color(0xFF333333),
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
                            .background(Color(0xFFE9E9E9))
                    ) {
                        PlatformVerticalScrollbar(
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
