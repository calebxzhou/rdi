package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.isAdmin
import calebxzhou.rdi.common.model.isDav
import io.ktor.client.plugins.sse.SSEBufferPolicy
import kotlinx.coroutines.Job
import org.bson.types.ObjectId

@Composable
fun HostBackendScreen(
    hostId: ObjectId,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val consoleState = remember(hostId) { ConsoleState() }
    val snackbarHostState = remember { SnackbarHostState() }
    var host by remember { mutableStateOf<Host.DetailVo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var logStreamJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(hostId) {
        scope.rdiRequest<Host.DetailVo>(
            path = "host/$hostId/detail",
            onOk = { response ->
                host = response.data
                if (host == null) errorMessage = "无法加载房间信息"
            },
            onErr = { errorMessage = it.message ?: "无法加载房间信息" },
            onDone = { loading = false }
        )
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val currentHost = host
    val canView = currentHost?.let {
        it.ownerId == loggedAccount._id || it.members.any { member -> member.id == loggedAccount._id } || loggedAccount.isDav
    } ?: false
    val canManage = currentHost?.let {
        it.isAdmin(loggedAccount) || it.ownerId == loggedAccount._id || loggedAccount.isDav
    } ?: false

    DisposableEffect(hostId, canView) {
        if (!canView) {
            onDispose { }
        } else {
            consoleState.clear()
            logStreamJob = scope.sse(
                path = "host/$hostId/log/stream",
                bufferPolicy = SSEBufferPolicy.LastEvents(50),
                onEvent = { event ->
                    if (event.event == "heartbeat") return@sse
                    if (event.event == "error") {
                        errorMessage = "读取日志错误: ${event.data ?: "unknown"}"
                        logStreamJob?.cancel()
                        logStreamJob = null
                        return@sse
                    }
                    event.data?.ifBlank { null }?.let(consoleState::append)
                },
                onError = {
                    errorMessage = "读取日志错误: ${it.message}"
                    logStreamJob?.cancel()
                    logStreamJob = null
                }
            )
            onDispose {
                logStreamJob?.cancel()
                logStreamJob = null
            }
        }
    }

    errorMessage?.let { AlertErr(it) { errorMessage = null } }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(currentHost?.let { "${it.name} - 后台" } ?: "房间后台", onBack = onBack)
            ContentBody {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    currentHost == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法加载房间信息", color = MaterialTheme.colorScheme.error)
                    }
                    !canView -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("仅房间成员可查看后台", color = MaterialTheme.colorScheme.error)
                    }
                    else -> HostConsolePane(
                        hostId = hostId,
                        state = consoleState,
                        canManage = canManage,
                        onOk = { okMessage = it },
                        onError = { errorMessage = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }
}
