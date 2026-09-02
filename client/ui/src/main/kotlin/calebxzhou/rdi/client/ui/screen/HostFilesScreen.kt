package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.isAdmin
import calebxzhou.rdi.common.model.isDav
import org.bson.types.ObjectId

@Composable
fun HostFilesScreen(
    hostId: ObjectId,
    onBack: () -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var host by remember(hostId) { mutableStateOf<Host.DetailVo?>(null) }
    var loading by remember(hostId) { mutableStateOf(true) }
    var errorMessage by remember(hostId) { mutableStateOf<String?>(null) }

    LaunchedEffect(hostId) {
        loading = true
        errorMessage = null
        scope.rdiRequest<Host.DetailVo>(
            path = "host/$hostId/detail",
            onOk = { response ->
                host = response.data
                if (host == null) errorMessage = "无法加载房间信息"
            },
            onErr = { errorMessage = "加载房间信息失败: ${it.message}" },
            onDone = { loading = false }
        )
    }
    val currentHost = host

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(
                title = currentHost?.let { "${it.name} - 文件" } ?: "房间文件",
                onBack = onBack
            )
            ContentBody {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    currentHost == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage ?: "无法加载房间信息")
                    }

                    else -> HostFilesPane(
                        hostId = hostId,
                        canManage = currentHost.isAdmin(loggedAccount) ||
                            currentHost.ownerId == loggedAccount._id ||
                            loggedAccount.isDav,
                        onOpenTaskList = onOpenTaskList
                    )
                }
            }
        }
    }
}

@Composable
fun HostFilesScreen(
    target: HostTarget,
    onBack: () -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null,
) {
    if (target.kind == HostKind.Legacy) {
        val objectId = target.objectIdOrNull()
        if (objectId == null) HostDetailRouteError("房间ID格式错误", onBack)
        else HostFilesScreen(objectId, onBack, onOpenTaskList)
    } else {
        HostDetailRouteError("该房间类型暂不可用", onBack)
    }
}

/* @Composable
private fun UnifiedHost2FilesScreen(
    target: HostTarget,
    onBack: () -> Unit,
    onOpenTaskList: ((String) -> Unit)?,
) {
    val id = target.id
    val scope = rememberCoroutineScope()
    var detail by remember(id) { mutableStateOf<calebxzhou.rdi.common.model.Host2.DetailVo?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        scope.rdiRequest<calebxzhou.rdi.common.model.Host2.DetailVo>(
            path = "host2/$id",
            onOk = { response -> detail = response.data },
            onErr = { error = it.message ?: "无法加载房间信息" },
            onDone = { loading = false },
        )
    }
    val current = detail
    MaxBox {
        ScreenContentSurface(ScreenContentSize.LARGE) {
            TitleRow(current?.let { "${it.name} - 文件" } ?: "房间文件", onBack)
            ContentBody {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "无法加载房间信息") }
                    current.role !in setOf(calebxzhou.rdi.model.Role.OWNER, calebxzhou.rdi.model.Role.ADMIN) && !loggedAccount.isDav ->
                        Text("仅房间管理员可查看文件")
                    else -> HostFileExplorer(
                        hostId = id,
                        apiRoot = "host2",
                        modifier = Modifier.fillMaxSize(),
                        onOpenTaskList = onOpenTaskList,
                    )
                }
            }
        }
    }
} */
