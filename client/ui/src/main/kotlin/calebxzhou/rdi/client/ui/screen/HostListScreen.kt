package calebxzhou.rdi.client.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.client.ui.comp.ModpackDownloadMethodDialog
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.isDav
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
    Mail("\uEB1C", "信箱"),
    Worlds("\uDB85\uDC5C", "存档");

    companion object {
        fun fromRouteValue(value: String?): HostTab {
            return entries.firstOrNull { it.name == value } ?: MyHosts
        }
    }
}

private const val HOST_TAB_FADE_DURATION_MS = 140
private const val HOST_TAB_SLIDE_DURATION_MS = 180

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
@Composable
fun HostListScreen(
    onBack: (() -> Unit),
    onOpenHostInfo: ((String, Boolean) -> Unit),
    onOpenHostCreate: (() -> Unit),
    onOpenMcVersions: ((McVersion?) -> Unit),
    onOpenResourceMods: ((McVersion?, String, Boolean) -> Unit),
    onOpenMcPlay: ((McPlayArgs) -> Unit),
    onOpenTaskList: ((String) -> Unit),
    initialTab: HostTab = HostTab.MyHosts,
    /*
    onOpenBirdView: (String) -> Unit = {},
    onOpenLocalBirdView: (() -> Unit)? = null
    */
) {
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) }

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
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val forward = HostTab.entries.indexOf(targetState) > HostTab.entries.indexOf(initialState)
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(HOST_TAB_SLIDE_DURATION_MS),
                            initialOffsetX = { it / 10 * direction }
                        ) + fadeIn(animationSpec = tween(HOST_TAB_FADE_DURATION_MS))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(HOST_TAB_SLIDE_DURATION_MS),
                                    targetOffsetX = { -it / 10 * direction }
                                ) + fadeOut(animationSpec = tween(HOST_TAB_FADE_DURATION_MS))) using
                                SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "HostTabContent"
                ) { activeTab ->
                    when (activeTab) {
                HostTab.MyHosts -> HostBrowserPane(
                    title = "我的房间",
                    emptyStateText = "暂无你的房间，点击上方创建新房间或等待朋友邀请",
                    listPathForPage = { pageIndex -> "host/my/$pageIndex" },
                    onOpenHostInfo = { hostId -> onOpenHostInfo(hostId, false) },
                    fromAllHosts = false,
                    onOpenMcVersions = onOpenMcVersions,
                    onOpenResourceMods = onOpenResourceMods,
                    onOpenMcPlay = onOpenMcPlay,
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
                    title = "所有房间",
                    emptyStateText = "暂无可展示的房间",
                    listPathForPage = { pageIndex -> "host/list/$pageIndex" },
                    onOpenHostInfo = { hostId -> onOpenHostInfo(hostId, true) },
                    fromAllHosts = true,
                    onOpenMcVersions = onOpenMcVersions,
                    onOpenResourceMods = onOpenResourceMods,
                    onOpenMcPlay = onOpenMcPlay,
                    onOpenTaskList = onOpenTaskList
                )

                HostTab.Mail -> MailPane(
                    modifier = Modifier.fillMaxSize()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostBrowserPane(
    title: String,
    emptyStateText: String,
    listPathForPage: (Int) -> String,
    onOpenHostInfo: ((String) -> Unit),
    fromAllHosts: Boolean,
    onOpenMcVersions: ((McVersion?) -> Unit),
    onOpenResourceMods: ((McVersion?, String, Boolean) -> Unit),
    onOpenMcPlay: ((McPlayArgs) -> Unit),
    onOpenTaskList: ((String) -> Unit),
    modifier: Modifier = Modifier,
    headerActions: (@Composable ColumnScope.() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<Host.BriefVo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var installConfirmTask by remember { mutableStateOf<StartPlayResult.NeedInstall?>(null) }
    var page by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var reachedEnd by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

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
                    }
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
}

private fun generateMockHosts(): List<Host.BriefVo> = List(50) { index ->
    val base = Host.BriefVo.TEST
    base.copy(
        _id = ObjectId(),
        name = "${base.name} #${index + 1}",
        port = base.port + index
    )
}
