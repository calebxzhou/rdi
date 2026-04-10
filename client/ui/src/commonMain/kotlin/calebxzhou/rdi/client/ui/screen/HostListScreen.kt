package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startPlay
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Task2
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-15 14:15
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onBack: (() -> Unit),
    onOpenHostAll: (() -> Unit)? = null,
    onOpenHostInfo: ((String) -> Unit)? = null,
    onOpenHostCreate: (() -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    HostBrowserScreen(
        title = "我的房间",
        emptyStateText = "暂无你的房间，点击右上角创建或等待朋友邀请",
        listPathForPage = { pageIndex -> "host/my/$pageIndex" },
        onBack = onBack,
        onOpenHostInfo = onOpenHostInfo,
        onOpenMcVersions = onOpenMcVersions,
        onOpenMcPlay = onOpenMcPlay,
        onOpenTaskList = onOpenTaskList
    ) {
        Text("显示你创建/受邀的房间")
        Space8w()
        onOpenHostAll?.let {
            CircleIconButton("\uF0C0", "房间大厅") {
                it()
            }
            Space8w()
        }
        CircleIconButton("\uDB81\uDC90", "创建新房间") {
            onOpenHostCreate?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HostBrowserScreen(
    title: String,
    emptyStateText: String,
    listPathForPage: (Int) -> String,
    onBack: (() -> Unit),
    onOpenHostInfo: ((String) -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<Host.BriefVo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var installConfirmTask by remember { mutableStateOf<Task2?>(null) }
    var page by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    fun resetList() {
        page = 0
        reachedEnd = false
        hosts = emptyList()
    }

    suspend fun loadPage(pageIndex: Int) {
        if (loadingMore || reachedEnd) return
        loadingMore = true
        val response = server.makeRequest<List<Host.BriefVo>>(listPathForPage(pageIndex))
        val data = response.data ?: emptyList()
        if (data.isEmpty()) {
            reachedEnd = true
        } else {
            hosts = hosts + data
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) {
        if (Const.USE_MOCK_DATA) {
            hosts = generateMockHosts()
        } else {
            resetList()
            loadPage(0)
        }
    }

    LaunchedEffect(gridState, hosts.size) {
        if (Const.USE_MOCK_DATA) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                val index = lastIndex ?: return@collect
                if (!loadingMore && !reachedEnd && index >= hosts.size - 4) {
                    page += 1
                    loadPage(page)
                }
            }
    }

    MainColumn {
        TitleRow(title, onBack = onBack) {
            errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
            if (errorMessage != null) {
                Space8w()
            }
            headerActions()
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (hosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyStateText,
                    style = MaterialTheme.typography.h6,
                    color = Color.Gray
                )
            }
        } else {
            val playableHosts = remember(hosts) { hosts.filter { it.playable } }
            val nonPlayableHosts = remember(hosts) { hosts.filter { !it.playable } }

            @Composable
            fun renderHostCard(host: Host.BriefVo) {
                host.HostCard(
                    onClickPlay = {
                        scope.launch {
                            val res = server.makeRequest<Host.DetailVo>("host/${host._id}/detail")
                            val detail = res.data
                                ?: run {
                                    errorMessage = "获取房间信息失败: ${res.msg}"
                                    return@launch
                                }
                            val args = try {
                                detail.startPlay()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "无法开始游玩"
                                return@launch
                            }
                            when (args) {
                                is StartPlayResult.Ready -> {
                                    LocalCredentials.read().updateLastPlayHost(
                                        id = detail._id.toHexString(),
                                        name = detail.name
                                    )
                                    if (onOpenMcPlay != null) {
                                        onOpenMcPlay(args.args)
                                    } else {
                                        errorMessage = "暂不支持在此页面游玩"
                                    }
                                }

                                is StartPlayResult.NeedInstall -> {
                                    installConfirmTask = args.task
                                }

                                is StartPlayResult.NeedMc -> {
                                    errorMessage = "未安装MC版本资源：${args.ver.mcVer}，请先下载"
                                    onOpenMcVersions?.invoke(args.ver)
                                }
                            }
                        }
                    },
                    onClick = {
                        onOpenHostInfo?.invoke(host._id.toHexString())
                    }
                )
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (playableHosts.isNotEmpty()) {
                    items(playableHosts) { host ->
                        renderHostCard(host)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("暂无可游玩的房间", color = Color.Gray)
                    }
                }

                if (nonPlayableHosts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "以下房间由于不在线或已启用白名单，无法游玩",
                            style = MaterialTheme.typography.subtitle1,
                            color = Color.Gray
                        )
                    }
                    items(nonPlayableHosts) { host ->
                        renderHostCard(host)
                    }
                }

                if (loadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    installConfirmTask?.let { task ->
        AlertDialog(
            onDismissRequest = { installConfirmTask = null },
            title = { Text("未下载整合包") },
            text = { Text("未下载此房间的整合包，是否立即下载？") },
            confirmButton = {
                TextButton(onClick = {
                    installConfirmTask = null
                    val runId = ClientTaskManager.submit(task)
                    if (onOpenTaskList != null) {
                        onOpenTaskList(runId)
                    } else {
                        errorMessage = "已加入任务列表"
                    }
                }) { Text("下载") }
            },
            dismissButton = {
                TextButton(onClick = { installConfirmTask = null }) { Text("取消") }
            }
        )
    }
}

private fun generateMockHosts(): List<Host.BriefVo> = List(50) { index ->
    val base = Host.BriefVo.TEST
    base.copy(
        _id = ObjectId(),
        name = "${base.name} #${index + 1}",
        port = base.port + index
    )
}
