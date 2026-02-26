package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startPlay
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Task
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-15 14:15
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostListScreen(
    onBack: (() -> Unit),
    onOpenHostInfo: ((String) -> Unit)? = null,
    onOpenModpackList: (() -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTask: ((Task) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<Host.BriefVo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var installConfirmTask by remember { mutableStateOf<Task?>(null) }
    var page by remember { mutableStateOf(0) }
    val onlinePlayers = remember(hosts) {
        hosts.flatMap { it.onlinePlayerIds }.distinct()
    }
    var loadingMore by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val snackbarHostState = remember { SnackbarHostState() }
    fun resetList() {
        page = 0
        reachedEnd = false
        hosts = emptyList()
    }

    suspend fun loadPage(pageIndex: Int) {
        if (loadingMore || reachedEnd) return
        loadingMore = true
        val response = server.makeRequest<List<Host.BriefVo>>("host/list/$pageIndex")
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
        // Header / Toolbar
        TitleRow("地图大厅 · ${onlinePlayers.size}人在线", onBack = onBack) {
            errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
            if (onlinePlayers.isNotEmpty()) {
                Column(modifier = Modifier.widthIn(max = 600.dp)) {
                    FlowRow {
                        onlinePlayers.forEach {
                            HeadButton(it, showName = false, avatarSize = 12.dp)
                        }
                    }
                }
            }
            Space8w()
            ImageIconButton(
                "grass_block", "大家的整合包",
                bgColor = Color.LightGray
            ) {
                onOpenModpackList?.invoke()
            }
            Space8w()


        }
        Spacer(modifier = Modifier.height(16.dp))

        if (hosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无可展示的地图",
                    style = MaterialTheme.typography.h6,
                    color = Color.Gray
                )
            }
        } else {
            val playableHosts = remember(hosts) { hosts.filter { it.playable } }
            val nonPlayableHosts = remember(hosts) { hosts.filter { !it.playable } }
            @Composable
            fun renderHostCard(host: Host.BriefVo) {
                host.HostCard(onClickPlay = {
                    scope.launch {
                        val res = server.makeRequest<Host.DetailVo>("host/${host._id}/detail")
                        val detail = res.data
                            ?: run {
                                errorMessage = "获取地图信息失败: ${res.msg}"
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
                }, onClick = {
                    onOpenHostInfo?.invoke(host._id.toHexString())
                })
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (playableHosts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("能玩的图", style = MaterialTheme.typography.subtitle1)
                    }
                    items(playableHosts) { host ->
                        renderHostCard(host)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("暂无可游玩的地图", color = Color.Gray)
                    }
                }

                if (nonPlayableHosts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("以下地图由于不在线或已启用白名单，无法游玩", style = MaterialTheme.typography.subtitle1, color = Color.Gray)
                    }
                    items(nonPlayableHosts) { host ->
                        renderHostCard(host)
                    }
                }

                    /*calebxzhou.rdi.client.ui.component.HostCard(
                        host = host,
                        onClick = {
                            HostInfoFragment(host._id).go()
                        },
                        onPlayClick = {
                            scope.launch {
                                val res = server.makeRequest<Host>("host/${host._id}")
                                res.data?.startPlay()
                            }
                        }
                    )*/

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
            text = { Text("未下载此地图的整合包，是否立即下载？") },
            confirmButton = {
                TextButton(onClick = {
                    installConfirmTask = null
                    if (onOpenTask != null) {
                        onOpenTask(task)
                    } else {
                        errorMessage = "暂不支持在此页面下载"
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
