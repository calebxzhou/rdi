package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.KeepAliveAnimatedTabHost
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startHostPlay
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.client.ui.comp.ModpackDownloadMethodDialog
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.serdesJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-15 14:15
 */
enum class HostTab(
    val icon: String,
    val label: String
) {
    MyHosts("\uF04B", "我的房间"),
    AllHosts("\uF0C0", "所有房间"),
    Worlds("\uDB85\uDC5C", "存档");

    companion object {
        fun fromRouteValue(value: String?): HostTab {
            return entries.firstOrNull { it.name == value } ?: MyHosts
        }
    }
}

@Composable
fun HostListScreen(
    onBack: (() -> Unit),
    onOpenHostInfo: ((String, Boolean) -> Unit),
    onOpenHostMembers: ((String, Boolean) -> Unit),
    onOpenHostMods: ((String, Boolean) -> Unit),
    onOpenHostFiles: ((String, Boolean) -> Unit),
    onOpenHostBackend: ((String, Boolean) -> Unit),
    onOpenHostSettings: ((String, Boolean) -> Unit),
    onOpenMcPlay: ((McPlayArgs, Boolean) -> Unit),
    onOpenMcVersions: ((McVersion?, Boolean) -> Unit),
    onOpenHostCreate: (() -> Unit),
    onOpenTaskList: ((String) -> Unit),
    initialTab: HostTab = HostTab.MyHosts,
) {
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) }
    val launchingHost = remember { mutableStateOf<String?>(null) }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("房间", onBack = onBack) {
                TitleTabBar(
                    items = remember {
                        HostTab.entries.map { TitleTabItem(it, it.icon, it.label) }
                    },
                    selected = currentTab,
                    onSelect = { currentTab = it }
                )
            }
            ContentBody {
                KeepAliveAnimatedTabHost(
                    selected = currentTab,
                    order = HostTab.entries::indexOf,
                    modifier = Modifier.fillMaxSize(),
                ) { activeTab ->
                    when (activeTab) {
                        HostTab.MyHosts -> HostBrowserPane(
                            emptyStateText = "暂无你的房间，点击上方创建新房间或等待朋友邀请",
                            listPathForPage = { pageIndex -> "host/my/$pageIndex" },
                            onOpenHostInfo = { hostId -> onOpenHostInfo(hostId, false) },
                            onOpenHostMembers = { hostId -> onOpenHostMembers(hostId, false) },
                            onOpenHostMods = { hostId -> onOpenHostMods(hostId, false) },
                            onOpenHostFiles = { hostId -> onOpenHostFiles(hostId, false) },
                            onOpenHostBackend = { hostId -> onOpenHostBackend(hostId, false) },
                            onOpenHostSettings = { hostId -> onOpenHostSettings(hostId, false) },
                            onOpenMcPlay = { onOpenMcPlay(it, false) },
                            onOpenMcVersions = { onOpenMcVersions(it, false) },
                            launchingHost = launchingHost,
                            onOpenTaskList = onOpenTaskList
                        ) {
                            FlowRowV(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("显示你创建/受邀的房间")
                                CircleIconButton("\uDB81\uDC90", "创建新房间") {
                                    onOpenHostCreate()
                                }
                            }
                        }

                        HostTab.AllHosts -> HostBrowserPane(
                            emptyStateText = "暂无可展示的房间",
                            listPathForPage = { pageIndex -> "host/list/$pageIndex" },
                            onOpenHostInfo = { hostId -> onOpenHostInfo(hostId, true) },
                            onOpenHostMembers = { hostId -> onOpenHostMembers(hostId, true) },
                            onOpenHostMods = { hostId -> onOpenHostMods(hostId, true) },
                            onOpenHostFiles = { hostId -> onOpenHostFiles(hostId, true) },
                            onOpenHostBackend = { hostId -> onOpenHostBackend(hostId, true) },
                            onOpenHostSettings = { hostId -> onOpenHostSettings(hostId, true) },
                            onOpenMcPlay = { onOpenMcPlay(it, true) },
                            onOpenMcVersions = { onOpenMcVersions(it, true) },
                            launchingHost = launchingHost,
                            onOpenTaskList = onOpenTaskList
                        )

                        HostTab.Worlds -> WorldListPane(
                            /*
                            onOpenBirdView = onOpenBirdView,
                            onOpenLocalBirdView = onOpenLocalBirdView,
                            */
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HostBrowserPane(
    emptyStateText: String,
    listPathForPage: (Int) -> String,
    onOpenHostInfo: ((String) -> Unit),
    onOpenHostMembers: ((String) -> Unit),
    onOpenHostMods: ((String) -> Unit),
    onOpenHostFiles: ((String) -> Unit),
    onOpenHostBackend: ((String) -> Unit),
    onOpenHostSettings: ((String) -> Unit),
    onOpenMcPlay: ((McPlayArgs) -> Unit),
    onOpenMcVersions: ((McVersion?) -> Unit),
    launchingHost: MutableState<String?>,
    onOpenTaskList: ((String) -> Unit),
    modifier: Modifier = Modifier,
    headerActions: (@Composable ColumnScope.() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<Host.BriefVo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var installConfirmTask by remember { mutableStateOf<StartPlayResult.NeedInstall?>(null) }
    var deleteHost by remember { mutableStateOf<Host.DetailVo?>(null) }
    var deleteWorld by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var reachedEnd by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    fun startHost(host: Host.BriefVo) {
        if (launchingHost.value != null) return
        val hostId = host._id.toHexString()
        launchingHost.value = hostId
        scope.launch {
            startHostPlay(hostId)
                .onSuccess { result ->
                    when (result) {
                        is StartPlayResult.Ready -> {
                            LocalCredentials.read().updateLastPlayHost(hostId, host.name)
                            onOpenMcPlay(result.args)
                        }
                        is StartPlayResult.NeedMod -> {
                            errorMessage = "房间缺少必要Mod：${result.modSlugs.joinToString("、")}。请先前往模组界面添加。"
                        }
                        is StartPlayResult.NeedInstall -> installConfirmTask = result
                        is StartPlayResult.Installing -> {
                            errorMessage = "整合包正在下载，请等待下载完成后再启动"
                            onOpenTaskList(result.runId)
                        }
                        is StartPlayResult.NeedMc -> {
                            errorMessage = "请更新MC${result.ver.mcVer}版本资源"
                            onOpenMcVersions(result.ver)
                        }
                    }
                }
                .onFailure { errorMessage = it.message ?: "无法开始游玩" }
            launchingHost.value = null
        }
    }

    fun prepareDelete(host: Host.BriefVo) {
        scope.rdiRequest<Host.DetailVo>(
            path = "host/${host._id}/detail",
            onOk = { response ->
                deleteHost = response.data
                deleteWorld = false
            },
            onErr = { errorMessage = it.message ?: "无法加载房间信息" }
        )
    }

    fun resetList() {
        page = 0
        reachedEnd = false
        hosts = emptyList()
        initialLoading = true
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
        if (pageIndex == 0) {
            initialLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (Const.USE_MOCK_DATA) {
            hosts = generateMockHosts()
            initialLoading = false
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

    Column(modifier = modifier.fillMaxSize()) {
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
        okMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        if (headerActions != null) {
            Spacer(modifier = Modifier.height(8.dp))
            headerActions.invoke(this)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (initialLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (hosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyStateText,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        } else {
            val shownHosts = remember(hosts, loggedAccount._id) {
                if (loggedAccount.isDav) hosts.map { it.copy(playable = true) } else hosts
            }
            val playableHosts = remember(shownHosts) { shownHosts.filter { it.playable } }
            val nonPlayableHosts = remember(shownHosts) { shownHosts.filter { !it.playable } }

            @Composable
            fun renderHostCard(host: Host.BriefVo) {
                host.HostCard(
                    onClick = {
                        onOpenHostInfo.invoke(host._id.toHexString())
                    },
                    onPlay = ::startHost,
                    onOpenMembers = { onOpenHostMembers(host._id.toHexString()) },
                    onOpenMods = { onOpenHostMods(host._id.toHexString()) },
                    onOpenFiles = { onOpenHostFiles(host._id.toHexString()) },
                    onOpenBackend = { onOpenHostBackend(host._id.toHexString()) },
                    onOpenSettings = { onOpenHostSettings(host._id.toHexString()) },
                    onDelete = ::prepareDelete,
                    playEnabled = launchingHost.value == null,
                    playLoading = launchingHost.value == host._id.toHexString()
                )
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playableHosts.isNotEmpty()) {
                    items(playableHosts) { host ->
                        renderHostCard(host)
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("暂无可游玩的房间")
                    }
                }

                if (nonPlayableHosts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "以下房间由于不在线或已启用白名单，无法游玩",
                            style = MaterialTheme.typography.titleMedium
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

    installConfirmTask?.let { install ->
        ModpackDownloadMethodDialog(
            packName = install.task.title,
            packVer = "",
            onDismiss = { installConfirmTask = null },
            onDirectDownload = {
                installConfirmTask = null
                val runId = ClientTaskManager.submit(install.task, dedupeKey = install.dedupeKey)
                onOpenTaskList(runId)
            },
            onOpenTaskList = onOpenTaskList,
            onImportError = { errorMessage = it }
        )
    }

    deleteHost?.let { host ->
        AlertDialog(
            onDismissRequest = { deleteHost = null },
            title = { Text("确认删除") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("确认删除房间吗？")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteWorld,
                            enabled = host.worldId != null,
                            onCheckedChange = { deleteWorld = it }
                        )
                        Text(if (host.worldId == null) "该房间没有关联存档" else "同时删除关联存档（不可恢复）")
                    }
                    Text(if (deleteWorld && host.worldId != null) "房间和存档都会被删除，无法恢复。" else "默认仅删除房间，存档会保留，可导出或复用。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.rdiRequestU(
                        path = "host/${host._id}",
                        method = HttpMethod.Delete,
                        body = serdesJson.encodeToString(Host.DeleteDto(deleteWorld && host.worldId != null)),
                        onOk = {
                            hosts = hosts.filterNot { it._id == host._id }
                            errorMessage = null
                            okMessage = "已删除"
                            deleteHost = null
                        },
                        onErr = { errorMessage = it.message ?: "删除失败" }
                    )
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteHost = null }) { Text("取消") }
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
