package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.IconMarqueeCard
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.MarqueeIcon
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.RDIClient
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzau.rdi.client.ui.loadImageBitmap
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.lgr
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.HttpImageState
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.rememberPlayerInfoPrefetch
import calebxzhou.rdi.client.service.startHostPlay
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.client.ui.comp.ModpackDownloadMethodDialog
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.util.periodOfDay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-02-25 17:51
 */

@Composable
fun MenuScreen(
    onOpenResources: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenHost2Lobby: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenMcPlay: (McPlayArgs) -> Unit,
    onOpenMcVersions: (McVersion?) -> Unit,
    onOpenTaskList: (String) -> Unit
) {
    val lastPlayHost = remember { LocalCredentials.read().lastPlayHost }
    val scope = rememberCoroutineScope()
    var lastPlayHostBrief by remember { mutableStateOf<Host.BriefVo?>(null) }
    var menuModpackBriefs by remember { mutableStateOf<List<Modpack.ListSimpleVo>>(emptyList()) }
    var menuSkinHeads by remember { mutableStateOf<List<MarqueeIcon.SkinHead>>(emptyList()) }
    var launchingHostId by remember { mutableStateOf<String?>(null) }
    var installConfirmTask by remember { mutableStateOf<StartPlayResult.NeedInstall?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }
    var onlinePlayerIds by remember { mutableStateOf<List<ObjectId>>(emptyList()) }
    var showOldMainWarning by remember { mutableStateOf(RDIClient.OLD_MAIN) }
    val sponsorBitmap = remember {
        loadImageBitmap("assets/sponsor.avif")
            .onFailure { lgr.warn(it) { "加载赞助图片失败" } }
            .getOrNull()
    }

    LaunchedEffect(lastPlayHost?.id) {
        lastPlayHostBrief = null
        val hostId = lastPlayHost?.id ?: return@LaunchedEffect
        runCatching {
            server.makeRequest<Host.BriefVo>("host/$hostId/brief").let { response ->
                if (!response.ok) throw RequestError(response.msg)
                response.data
            }
        }.onSuccess { host ->
            lastPlayHostBrief = host
        }.onFailure { error ->
            lgr.warn(error) { "加载最近游玩房间失败" }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            server.makeRequest<List<ObjectId>>("host/online-player-ids").data ?: emptyList()
        }.onSuccess { ids ->
            onlinePlayerIds = ids.distinct()
        }
    }

    LaunchedEffect(Unit) {
        menuModpackBriefs = runCatching {
            loadMenuModpackBriefs()
        }.onFailure { error ->
            lgr.warn(error) { "加载整合广场失败" }
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        runCatching {
            loadMenuSkinHeads { heads ->
                menuSkinHeads = heads
            }
        }.onFailure { error ->
            lgr.warn(error) { "加载多人房间皮肤失败" }
        }
    }

    fun startHost(host: Host.BriefVo) {
        if (launchingHostId != null) return
        val hostId = host._id.toHexString()
        launchingHostId = hostId
        scope.launch {
            startHostPlay(hostId)
                .onSuccess { result ->
                    when (result) {
                        is StartPlayResult.Ready -> {
                            LocalCredentials.read().updateLastPlayHost(hostId, host.name)
                            onOpenMcPlay(result.args)
                        }
                        is StartPlayResult.NeedMod -> {
                            playError = "房间缺少必要Mod：${result.modSlugs.joinToString("、")}。请先前往模组界面添加。"
                        }
                        is StartPlayResult.NeedInstall -> installConfirmTask = result
                        is StartPlayResult.Installing -> {
                            playError = "整合包正在下载，请等待下载完成后再启动"
                            onOpenTaskList(result.runId)
                        }
                        is StartPlayResult.NeedMc -> {
                            playError = "请更新MC${result.ver.mcVer}版本资源"
                            onOpenMcVersions(result.ver)
                        }
                    }
                }
                .onFailure { playError = it.message ?: "无法开始游玩" }
            launchingHostId = null
        }
    }

    if (showOldMainWarning) {
        AlertDialog(
            onDismissRequest = { showOldMainWarning = false },
            title = { Text("旧版入口即将停止支持") },
            text = {
                Text(
                    "你正在使用旧版入口启动rdi\n" +
                        "此入口将在2026.8.10停止支持\n" +
                        "请打开rdi安装文件夹，从\"start.exe\"启动rdi 然后删掉桌面图标重新创建"
                )
            },
            confirmButton = {
                CircleIconButton(
                    icon = "\uF00C",
                    label = "知道了"
                ) {
                    showOldMainWarning = false
                }
            }
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuAccountSummary(onlinePlayerIds = onlinePlayerIds)
                        PlayerPreviewCard(
                            modifier = Modifier.size(320.dp),
                            onClick = onOpenWardrobe
                        )
                        lastPlayHostBrief?.let { host ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                host.copy(name = "继续游玩·${host.name}").HostCard(
                                    modifier = Modifier.width(320.dp),
                                    onDirectClick = ::startHost,
                                    playEnabled = launchingHostId == null,
                                    playLoading = launchingHostId == host._id.toHexString()
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        MenuActionButtons(
                            modifier = Modifier.widthIn(min = 220.dp),
                            modpackBriefs = menuModpackBriefs,
                            skinHeads = menuSkinHeads,
                            onOpenResources = onOpenResources,
                            onOpenHostLobby = onOpenHostLobby,
                            onOpenHost2Lobby = onOpenHost2Lobby,
                        )

                    }
                }
            }
        }
        sponsorBitmap?.let { bitmap ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                    Text("支持rdi继续走下去~", color = Color.White,style = MaterialTheme.typography.bodyMedium)
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(128.dp).clip(baseRoundCornerShape),
                        contentScale = ContentScale.Fit
                    )

            }
        }
    }

    playError?.let { error ->
        AlertDialog(
            onDismissRequest = { playError = null },
            title = { Text("无法开始游玩") },
            text = { Text(error) },
            confirmButton = {
                CircleIconButton(
                    icon = "\uF00C",
                    label = "知道了"
                ) {
                    playError = null
                }
            }
        )
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
            onImportError = { playError = it }
        )
    }
}

private suspend fun loadMenuModpackBriefs(): List<Modpack.ListSimpleVo> {
    val response = server.makeRequest<List<Modpack.ListSimpleVo>>(
        path = "modpack/list-simple",
        params = mapOf("hasIconOnly" to true)
    )
    if (!response.ok) throw RequestError(response.msg)
    return response.data.orEmpty()
}

private suspend fun loadMenuSkinHeads(
    onHeadsChanged: (List<MarqueeIcon.SkinHead>) -> Unit
) {
    val response = server.makeRequest<List<String>>("player/unique-skin-list")
    if (!response.ok) throw RequestError(response.msg)

    val skinUrls = response.data.orEmpty()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    var heads = skinUrls.map {
        MarqueeIcon.SkinHead(
            image = null,
            contentDescription = "玩家皮肤"
        )
    }
    onHeadsChanged(heads)

    coroutineScope {
        skinUrls.forEachIndexed { index, skinUrl ->
            launch {
                val image = HttpImageState.fetch(skinUrl).bitmap
                    ?.takeIf { it.width >= 64 && it.height >= 32 }
                heads = heads.mapIndexed { currentIndex, head ->
                    if (currentIndex == index) head.copy(image = image) else head
                }
                onHeadsChanged(heads)
            }
        }
    }
}

@Composable
private fun MenuAccountSummary(
    onlinePlayerIds: List<ObjectId>,
    modifier: Modifier = Modifier
) {
    val playerIds = remember(onlinePlayerIds) {
        listOf(loggedAccount._id) + onlinePlayerIds
    }
    rememberPlayerInfoPrefetch(playerIds)


    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            Text("${periodOfDay}好，${loggedAccount.name}")

        FlowRowV(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("\uEC17 ${onlinePlayerIds.size} ")
            onlinePlayerIds.forEach {
                HeadButton(it, showName = false, avatarSize = 12.dp)
            }
        }
    }

}

@Composable
private fun MenuActionButtons(
    modifier: Modifier = Modifier,
    modpackBriefs: List<Modpack.ListSimpleVo>,
    skinHeads: List<MarqueeIcon.SkinHead>,
    onOpenResources: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenHost2Lobby: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        val marqueeIcons = remember(modpackBriefs) {
            modpackBriefs.mapNotNull { brief ->
                brief.iconUrl
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { iconUrl ->
                        MarqueeIcon.Url(
                            url = iconUrl,
                            contentDescription = brief.name
                        )
                    }
            }
        }
        IconMarqueeCard(
            title = "整合广场",
            subtitle = "浏览大家喜欢玩的整合包",
            icons = marqueeIcons,
            modifier = Modifier
                .width(320.dp)
                .clip(baseRoundCornerShape)
                .clickable(onClick = onOpenResources)
        )
        IconMarqueeCard(
            title = "多人房间",
            subtitle = "选择创建房间跟大家一起玩",
            icons = skinHeads,
            modifier = Modifier
                .width(320.dp)
                .clip(baseRoundCornerShape)
                .clickable(onClick = onOpenHostLobby)
        )
        /*if(DEBUG){

            CircleIconButton(
                icon = "\uF1B3",
                label = "新版房间"
            ) {
                onOpenHost2Lobby()
            }
        }*/
    }
}

@Composable
private fun PlayerPreviewCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PlayerModel(
            skinUrl = loggedAccount.cloth.skin,
            capeUrl = loggedAccount.cloth.cape,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = Color.Transparent,
            autoRotate = true,
            animateWalk = true,
            showOuterLayer = true,
            isSlim = loggedAccount.cloth.isSlim,
            maxRenderSide = 480,
            noControl = true
        )
    }
}
