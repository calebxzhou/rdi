package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.*
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.*
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.model.Role
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun Host2LobbyScreen(onBack: () -> Unit, onCreate: () -> Unit, onOpen: (String) -> Unit) {
    var myOnly by remember { mutableStateOf(true) }
    var hosts by remember { mutableStateOf<List<Host2.BriefVo>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        runCatching { server.makeRequest<List<Host2.BriefVo>>(if (myOnly) "host2/my" else "host2/list") }
            .onSuccess { response ->
                if (!response.ok) message = response.msg else hosts = response.data.orEmpty()
            }.onFailure { message = it.message }
    }
    LaunchedEffect(myOnly) { reload() }
    MaxBox {
        ScreenContentSurface(ScreenContentSize.LARGE) {
            TitleRow("新版房间", onBack) {
                CircleIconButton("\uDB81\uDC90", "创建新版房间", onClick = onCreate)
            }
            ContentBody {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(myOnly, { myOnly = true }, { Text("我的") })
                    FilterChip(!myOnly, { myOnly = false }, { Text("全部") })
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hosts, key = { it.id.toString() }) { host ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(host.id.toString()) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(host.name, style = MaterialTheme.typography.titleMedium)
                                Text("MC${host.mcVersion.mcVer} · ${host.modLoader} · ${host.status.host2Text()}")
                                Text(host.intro, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Host2CreateScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(McVersion.entries.first { it.enabled }) }
    var loader by remember(version) { mutableStateOf(version.loaderVersions.keys.first()) }
    var whitelist by remember { mutableStateOf(true) }
    var versionMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    MaxBox {
        ScreenContentSurface(ScreenContentSize.MEDIUM) {
            TitleRow("创建新版房间", onBack)
            ContentBody {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(intro, { intro = it }, label = { Text("简介") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircleIconButton("\uF1B2", "MC${version.mcVer}") { versionMenu = true }
                        DropdownMenu(versionMenu, { versionMenu = false }) {
                            McVersion.entries.filter { it.enabled }.forEach { item ->
                                DropdownMenuItem({ Text("MC${item.mcVer}") }, onClick = {
                                    version = item
                                    loader = item.loaderVersions.keys.first()
                                    versionMenu = false
                                })
                            }
                        }
                        version.loaderVersions.keys.forEach { item ->
                            FilterChip(loader == item, { loader = item }, { Text(item.name) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(whitelist, { whitelist = it })
                        Text("仅成员可加入")
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    CircleIconButton("\uF00C", if (submitting) "创建中" else "创建", enabled = !submitting && name.isNotBlank()) {
                        submitting = true
                        scope.launch {
                            runCatching {
                                val dto = Host2.CreateDto(name, intro, version, loader, whitelist)
                                val response = server.makeRequest<Host2.DetailVo>("host2", HttpMethod.Post) {
                                    contentType(ContentType.Application.Json)
                                    setBody(serdesJson.encodeToString(dto))
                                }
                                if (!response.ok) throw RequestError(response.msg)
                                response.data ?: throw RequestError("创建新版房间失败")
                            }.onSuccess { onCreated(it.id.toString()) }
                                .onFailure { message = it.message }
                            submitting = false
                        }
                    }
                }
            }
        }
    }
}

private enum class Host2DetailTab(val text: String) { OVERVIEW("概览"), MODS("Mod"), FILES("文件") }

@Composable
fun Host2InfoScreen(
    hostId: String,
    onBack: () -> Unit,
    onOpenMods: (McVersion) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenPlay: (McPlayArgs) -> Unit
) {
    val scope = rememberCoroutineScope()
    val id = remember(hostId) { UUID.fromString(hostId) }
    var host by remember { mutableStateOf<Host2.DetailVo?>(null) }
    var mods by remember { mutableStateOf<List<Host2.ModVo>>(emptyList()) }
    var tab by remember { mutableStateOf(Host2DetailTab.OVERVIEW) }
    var message by remember { mutableStateOf<String?>(null) }
    var githubOpen by remember { mutableStateOf(false) }
    var githubRepoUrl by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf<GithubRepoRef?>(null) }
    var githubAssets by remember { mutableStateOf<List<GithubReleaseAsset>>(emptyList()) }
    var githubLoading by remember { mutableStateOf(false) }
    var joinPackOpen by remember { mutableStateOf(false) }
    var joinPacks by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    suspend fun reload() {
        runCatching { server.makeRequest<Host2.DetailVo>("host2/$hostId") }
            .onSuccess { response -> host = response.data ?: host }
            .onFailure { message = it.message }
    }
    suspend fun request(path: String) {
        val response = server.makeRequest<Unit>("host2/$hostId/$path", HttpMethod.Post)
        if (!response.ok) throw RequestError(response.msg)
        reload()
    }
    LaunchedEffect(hostId) {
        while (true) {
            reload()
            delay(2000)
        }
    }
    LaunchedEffect(tab, host?.operation) {
        if (tab == Host2DetailTab.MODS) mods = server.makeRequest<List<Host2.ModVo>>("host2/$hostId/mods").data.orEmpty()
    }
    MaxBox {
        ScreenContentSurface(ScreenContentSize.LARGE) {
            TitleRow(host?.name ?: "新版房间", onBack) {
                host?.let { current ->
                    when (current.status) {
                        HostStatus.STOPPED -> if (
                            current.role != Role.GUEST && current.setupStatus == Host2SetupStatus.READY
                        ) {
                            CircleIconButton("\uF04B", "启动") {
                                scope.launch { runCatching { request("start") }.onFailure { message = it.message } }
                            }
                        }
                        else -> if (current.role in setOf(Role.OWNER, Role.ADMIN)) {
                            CircleIconButton("\uF04D", "停止") { scope.launch { runCatching { request("stop") }.onFailure { message = it.message } } }
                            CircleIconButton("\uF2F9", "重启") { scope.launch { runCatching { request("restart") }.onFailure { message = it.message } } }
                        }
                    }
                    if (current.status == HostStatus.PLAYABLE) {
                        CircleIconButton("\uF11B", "加入") { joinPackOpen = true }
                    }
                }
            }
            ContentBody {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Host2DetailTab.entries
                        .filter { it != Host2DetailTab.FILES || host?.role in setOf(Role.OWNER, Role.ADMIN) }
                        .forEach { item -> FilterChip(tab == item, { tab = item }, { Text(item.text) }) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (tab) {
                    Host2DetailTab.OVERVIEW -> host?.let { current ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("MC${current.mcVersion.mcVer} · ${current.modLoader}")
                            Text("配置:${current.setupStatus} · 状态:${current.status.host2Text()}")
                            Text(current.intro)
                            if (current.setupStatus in setOf(Host2SetupStatus.AWAITING_UPLOAD, Host2SetupStatus.FAILED) && current.role == Role.OWNER) {
                                CircleIconButton("\uF093", "选择解压后的server目录") {
                                    scope.launch {
                                        val directory = pickLocalDirectory("选择解压后的server根目录") ?: return@launch
                                        val runId = ClientTaskManager.submit(buildHost2ServerPackUploadTask(id, directory), "host2-pack-$id")
                                        onOpenTask(runId)
                                    }
                                }
                            }
                            if (current.setupStatus == Host2SetupStatus.PROCESSING) Text("server pack正在服务器处理，请稍候")
                            Text("成员${current.members.size + 1}人")
                            current.members.forEach { Text("${it.playerId} · ${it.role}") }
                        }
                    }
                    Host2DetailTab.MODS -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            host?.takeIf { it.role in setOf(Role.OWNER, Role.ADMIN) }?.let {
                                CircleIconButton("\uF067", "从CurseForge/Modrinth选择Mod") { onOpenMods(it.mcVersion) }
                                CircleIconButton("\uF09B", "从GitHub选择Mod") { githubOpen = true }
                            }
                        }
                        items(mods, key = { "${it.mod.platform}:${it.mod.projectId}" }) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.mod.slug)
                                        Text("${item.mod.platform} · ${item.mod.side}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    val canManage = host?.let { it.role in setOf(Role.OWNER, Role.ADMIN) && it.status == HostStatus.STOPPED } == true
                                    Switch(item.enabled, { enabled ->
                                        scope.launch {
                                            val dto = Host2.SetModsEnabledDto(listOf(Host2.ModKey(item.mod.platform, item.mod.projectId)), enabled)
                                            runCatching {
                                                val response = server.makeRequest<Unit>("host2/$hostId/mods/enabled", HttpMethod.Put) {
                                                    contentType(ContentType.Application.Json)
                                                    setBody(serdesJson.encodeToString(dto))
                                                }
                                                if (!response.ok) throw RequestError(response.msg)
                                                mods = server.makeRequest<List<Host2.ModVo>>("host2/$hostId/mods").data.orEmpty()
                                            }.onFailure { message = it.message }
                                        }
                                    }, enabled = canManage)
                                    if (canManage) CircleIconButton("\uF1F8", "删除${item.mod.slug}", showText = false) {
                                        scope.launch {
                                            runCatching {
                                                val keys = listOf(Host2.ModKey(item.mod.platform, item.mod.projectId))
                                                val response = server.makeRequest<Unit>("host2/$hostId/mods", HttpMethod.Delete) {
                                                    contentType(ContentType.Application.Json)
                                                    setBody(serdesJson.encodeToString(keys))
                                                }
                                                if (!response.ok) throw RequestError(response.msg)
                                                mods = server.makeRequest<List<Host2.ModVo>>("host2/$hostId/mods").data.orEmpty()
                                            }.onFailure { message = it.message }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Host2DetailTab.FILES -> HostFileExplorer(hostId, "host2", Modifier.fillMaxSize())
                }
            }
        }
    }
    if (githubOpen) {
        AlertDialog(
            onDismissRequest = { if (!githubLoading) githubOpen = false },
            title = { Text("从GitHub选择Mod") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        githubRepoUrl,
                        { githubRepoUrl = it },
                        label = { Text("GitHub仓库链接") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (githubAssets.isEmpty()) {
                        Text("读取Release后选择一个Jar文件")
                    } else {
                        LazyColumn(Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(githubAssets, key = GithubReleaseAsset::key) { asset ->
                                TextButton(
                                    enabled = !githubLoading,
                                    onClick = {
                                        val repo = githubRepo ?: return@TextButton
                                        githubLoading = true
                                        scope.launch {
                                            runCatching {
                                                val mod = GithubExtraModService.buildExtraModFromAsset(repo, asset, Mod.Side.BOTH) {
                                                    message = it
                                                }.getOrThrow()
                                                val response = server.makeRequest<Unit>("host2/$hostId/mods", HttpMethod.Post) {
                                                    contentType(ContentType.Application.Json)
                                                    setBody(serdesJson.encodeToString(listOf(mod)))
                                                }
                                                if (!response.ok) throw RequestError(response.msg)
                                            }.onSuccess {
                                                githubOpen = false
                                                githubAssets = emptyList()
                                                mods = server.makeRequest<List<Host2.ModVo>>("host2/$hostId/mods").data.orEmpty()
                                            }.onFailure { message = it.message }
                                            githubLoading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("${asset.releaseName} · ${asset.name} · ${asset.sizeText}") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !githubLoading && githubRepoUrl.isNotBlank(),
                    onClick = {
                        githubLoading = true
                        scope.launch {
                            GithubExtraModService.fetchReleases(githubRepoUrl)
                                .onSuccess { (repo, releases) ->
                                    githubRepo = repo
                                    githubAssets = releases.flatMap { it.assets }
                                }
                                .onFailure { message = it.message }
                            githubLoading = false
                        }
                    }
                ) { Text(if (githubLoading) "读取中" else "读取Release") }
            },
            dismissButton = { TextButton({ githubOpen = false }, enabled = !githubLoading) { Text("取消") } }
        )
    }
    if (joinPackOpen) {
        LaunchedEffect(host?.mcVersion, host?.modLoader) {
            val current = host ?: return@LaunchedEffect
            joinPacks = runCatching { ModpackService.getLocalPackDirs() }
                .getOrElse {
                    message = it.message
                    emptyList()
                }
                .filter { it.vo.mcVer == current.mcVersion && it.vo.modloader == current.modLoader }
        }
        AlertDialog(
            onDismissRequest = { joinPackOpen = false },
            title = { Text("选择本地整合包") },
            text = {
                if (joinPacks.isEmpty()) {
                    Text("没有已安装且版本匹配的整合包")
                } else {
                    LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(joinPacks, key = ModpackLocalDir::versionId) { pack ->
                            TextButton(
                                onClick = {
                                    val current = host ?: return@TextButton
                                    joinPackOpen = false
                                    onOpenPlay(
                                        McPlayArgs(
                                            title = "游玩 ${current.name}",
                                            mcVer = current.mcVersion,
                                            modLoader = current.modLoader,
                                            versionId = pack.versionId,
                                            playArg = "${server.hqUrl}\n" +
                                                "127.0.0.1:55667\n" +
                                                "${current.name}\n" +
                                                "${current.port}\n" +
                                                "${loggedAccount.uuid}\n" +
                                                loggedAccount.name,
                                            modpackName = pack.vo.name,
                                            versionDir = pack.dir.absolutePath
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${pack.vo.name} ${pack.verName}") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ joinPackOpen = false }) { Text("取消") } }
        )
    }
}

private fun HostStatus.host2Text() = when (this) {
    HostStatus.STARTED -> "启动中"
    HostStatus.PLAYABLE -> "可游玩"
    HostStatus.STOPPED -> "已停止"
    HostStatus.PAUSED -> "已暂停"
    HostStatus.UNKNOWN -> "未知"
}
