package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.model.Role
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.isAdmin
import calebxzhou.rdi.common.model.isDav
import io.ktor.client.plugins.sse.SSEBufferPolicy
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

@Composable
fun HostBackendScreen(
    target: HostTarget,
    onBack: () -> Unit,
) {
    LegacyHostRoute(target, onBack) { objectId ->
        HostBackendScreen(objectId, onBack)
    }
}

/* Host2 backend is retained for the later re-enable, but is not compiled into this release.
@Composable
private fun UnifiedHost2BackendScreen(target: HostTarget, onBack: () -> Unit) {
    val id = target.id
    val scope = rememberCoroutineScope()
    val consoleState = remember(id) { calebxzhou.rdi.client.ui.comp.ConsoleState() }
    var detail by remember(id) { mutableStateOf<calebxzhou.rdi.common.model.Host2.DetailVo?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var streamJob by remember(id) { mutableStateOf<Job?>(null) }
    var commandDialog by remember(id) { mutableStateOf(false) }
    var command by remember(id) { mutableStateOf("") }
    var commandSending by remember(id) { mutableStateOf(false) }
    var commandResult by remember(id) { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        scope.rdiRequest<calebxzhou.rdi.common.model.Host2.DetailVo>(
            path = "host2/$id",
            onOk = { detail = it.data },
            onErr = { error = it.message ?: "无法加载房间信息" },
            onDone = { loading = false },
        )
    }
    val current = detail
    val canView = current?.let { it.role != Role.GUEST || loggedAccount.isDav } == true
    val canManage = current?.let { it.role in setOf(Role.OWNER, Role.ADMIN) || loggedAccount.isDav } == true
    DisposableEffect(id, canView) {
        if (!canView) return@DisposableEffect onDispose { }
        consoleState.clear()
        streamJob = scope.sse(
            path = "host2/$id/log/stream",
            bufferPolicy = SSEBufferPolicy.LastEvents(50),
            onEvent = { event -> event.data?.ifBlank { null }?.let(consoleState::append) },
            onError = { error = "读取日志错误: ${it.message}" },
        )
        onDispose {
            streamJob?.cancel()
            streamJob = null
        }
    }
    MaxBox {
        ScreenContentSurface(ScreenContentSize.LARGE) {
            TitleRow(current?.let { "${it.name} - 后台" } ?: "房间后台", onBack)
            ContentBody {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    current == null -> Text(error ?: "无法加载房间信息", color = MaterialTheme.colorScheme.error)
                    !canView -> Text("仅房间成员可查看后台", color = MaterialTheme.colorScheme.error)
                    else -> Box(Modifier.fillMaxSize()) {
                        calebxzhou.rdi.client.ui.comp.Console(consoleState, Modifier.fillMaxSize())
                        if (canManage) {
                            Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircleIconButton("\uF120", "发送命令", showText = false) { commandDialog = true }
                                CircleIconButton("\uF01E", "重启", showText = false) {
                                    scope.launch { host2Action(id, "restart", { error = it }) }
                                }
                                CircleIconButton("\uF04D", "停止", showText = false, bgColor = MaterialTheme.colorScheme.error) {
                                    scope.launch { host2Action(id, "stop", { error = it }) }
                                }
                                CircleIconButton("\uF04B", "启动", showText = false) {
                                    scope.launch { host2Action(id, "start", { error = it }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (commandDialog) {
        val normalized = command.trim().removePrefix("/")
        AlertDialog(
            onDismissRequest = { if (!commandSending) commandDialog = false },
            title = { Text("发送服务器命令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(command, { command = it }, label = { Text("命令") }, singleLine = true)
                    commandResult?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = normalized.isNotBlank() && !commandSending, onClick = {
                    commandSending = true
                    scope.launch {
                        runCatching {
                            val response = server.makeRequest<String>("host2/$id/command", HttpMethod.Post) {
                                contentType(io.ktor.http.ContentType.Application.Json)
                                setBody(serdesJson.encodeToString(calebxzhou.rdi.common.model.Host2.CommandDto(normalized)))
                            }
                            if (!response.ok) throw RequestError(response.msg)
                            commandResult = response.data ?: "OK"
                        }.onFailure { error = it.message ?: "发送命令失败" }
                        commandSending = false
                    }
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { commandDialog = false }) { Text("取消") } },
        )
    }
}

private suspend fun host2Action(id: String, action: String, onError: (String) -> Unit) {
    runCatching {
        val response = server.makeRequest<Unit>("host2/$id/$action", HttpMethod.Post)
        if (!response.ok) throw RequestError(response.msg)
    }.onFailure { onError(it.message ?: "操作失败") }
}
*/
