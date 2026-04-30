package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionDependencyVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.ModrinthProjectInfoService
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.RemoteModDownloadService
import calebxzhou.rdi.client.service.RemoteModLocalization
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.ui.BottomSnakebar
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.client.ui.comp.WebPagePane
import calebxzhou.rdi.client.ui.openUrl
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.model.Role
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteModInfoScreen(
    mod: RemoteModCardVo,
    onBack: () -> Unit
) {
    var project by remember { mutableStateOf<ModrinthProjectInfoVo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var downloadVersion by remember { mutableStateOf<ModrinthProjectVersionVo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mod.source, mod.projectId) {
        loading = true
        errorMessage = null
        runCatching {
            when (mod.source) {
                RemoteModSource.MODRINTH -> ModrinthProjectInfoService.loadProjectInfo(mod.projectId)
                RemoteModSource.CURSEFORGE -> error("CurseForge详情暂未接入")
            }
        }.onSuccess {
            project = it
        }.onFailure {
            errorMessage = "加载模组详情失败: ${it.message ?: it}"
        }
        loading = false
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val title = project?.title ?: mod.title
    val downloadsText = project?.downloadsText ?: mod.downloadsText
    val followsText = project?.followsText ?: mod.followsText
    val sourceSlug = project?.slug ?: mod.slug
    val mcmodId = if (mod.source == RemoteModSource.MODRINTH) {
        RemoteModLocalization.mcmodIdByModrinthSlug(sourceSlug)
    } else {
        null
    }
    val mcmodUrl = mcmodId?.let { "https://www.mcmod.cn/class/$it.html" }
    val tabs = remember(mcmodUrl) {
        buildList {
            add(RemoteModInfoTab.Download)
            add(RemoteModInfoTab.Description)
            if (mcmodUrl != null) {
                add(RemoteModInfoTab.Mcmod)
            }
            add(RemoteModInfoTab.Versions)
        }
    }

    LaunchedEffect(tabs.size) {
        if (selectedTab !in tabs.indices) {
            selectedTab = 0
        }
    }
    val activeTabIndex = selectedTab.takeIf { it in tabs.indices } ?: 0
    val activeTab = tabs[activeTabIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow(title, onBack) {
                Text("\uF019 $downloadsText".asIconText, color = MaterialColor.GRAY_900.color)
                followsText?.let {
                    Space8w()
                    Text("\uDB80\uDED1 $it".asIconText, color = MaterialColor.GRAY_900.color)
                }
                mod.slug?.takeIf(String::isNotBlank)?.let { slug ->
                    Space8w()
                    CircleIconButton(
                        icon = "\uE8A7",
                        tooltip = "打开Modrinth",
                        bgColor = MaterialColor.GREEN_700.color
                    ) {
                        openUrl("https://modrinth.com/mod/$slug")
                    }
                }
            }
            Space8h()
            TabRow(selectedTabIndex = activeTabIndex, backgroundColor = Color.White) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTabIndex == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) }
                    )
                }
            }
            Space8h()
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }

                errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colors.error)

                activeTab == RemoteModInfoTab.Download -> RemoteModDownloadTab(project) { downloadVersion = it }
                activeTab == RemoteModInfoTab.Description || activeTab == RemoteModInfoTab.Mcmod ->
                    RemoteModWebInfoTab(activeTab, project, mod, mcmodUrl)
                else -> RemoteModVersionsTab(project)
            }
        }
        BottomSnakebar(snackbarHostState)
    }

    val loadedProject = project
    if (loadedProject != null) downloadVersion?.let { version ->
        RemoteModDownloadTargetDialog(
            project = loadedProject,
            version = version,
            onTaskSubmitted = { okMessage = it },
            onDismiss = { downloadVersion = null }
        )
    }
}

private enum class RemoteModInfoTab(val label: String) {
    Download("下载"),
    Description("描述"),
    Mcmod("百科"),
    Versions("版本")
}

@Composable
private fun RemoteModWebInfoTab(
    tab: RemoteModInfoTab,
    project: ModrinthProjectInfoVo?,
    mod: RemoteModCardVo,
    mcmodUrl: String?
) {
    val slug = project?.slug ?: mod.slug
    val page = when {
        tab == RemoteModInfoTab.Description && mod.source == RemoteModSource.MODRINTH && !slug.isNullOrBlank() ->
            RemoteModWebPage(url = "https://modrinth.com/mod/$slug")
        tab == RemoteModInfoTab.Mcmod && mcmodUrl != null -> RemoteModWebPage(url = mcmodUrl, title = "MC百科")
        else -> null
    }
    if (page == null) {
        Text("暂无来源网页", color = MaterialColor.GRAY_700.color)
    } else {
        WebPagePane(
            url = page.url,
            title = page.title,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private data class RemoteModWebPage(val url: String, val title: String? = null)

@Composable
private fun RemoteModVersionsTab(project: ModrinthProjectInfoVo?) {
    val versions = project?.versions.orEmpty()
    when {
        project == null -> Text("正在载入版本...", color = MaterialColor.GRAY_700.color)
        versions.isEmpty() -> Text("暂无版本", color = MaterialColor.GRAY_700.color)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(versions, key = { it.id }) { version ->
                RemoteModVersionCard(version)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteModVersionCard(version: ModrinthProjectVersionVo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        elevation = 1.dp,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = version.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialColor.GRAY_900.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = version.publishedText,
                    color = MaterialColor.GRAY_700.color,
                    maxLines = 1
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteModInfoChip(version.versionNumber)
                version.versionType?.let { RemoteModInfoChip(it) }
                version.environment?.let { RemoteModInfoChip(it) }
                version.loaders.forEach { RemoteModInfoChip(it.replaceFirstChar { ch -> ch.uppercase() }) }
                version.gameVersions.take(6).forEach { RemoteModInfoChip(it) }
            }
            version.primaryFile?.let { file ->
                RemoteModFileRow(file)
            }
            if (version.dependencies.isNotEmpty()) {
                Divider(color = MaterialColor.GRAY_200.color)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("依赖", color = MaterialColor.GRAY_800.color, fontWeight = FontWeight.Bold)
                    version.dependencies.take(8).forEach { dependency ->
                        RemoteModDependencyRow(dependency)
                    }
                }
            }
            if (version.changelog.isNotBlank() && version.changelog != "暂无更新日志") {
                Text(
                    text = version.changelog,
                    color = MaterialColor.GRAY_900.color,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RemoteModDownloadTab(
    project: ModrinthProjectInfoVo?,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    val versions = project?.versions.orEmpty()
    val supportedGameVersions = remember(versions) {
        McVersion.entries.filter { mcVersion ->
            mcVersion.enabled && versions.any { version ->
                mcVersion.mcVer in version.gameVersions &&
                        mcVersion.supportedRdiRemoteModLoaders().any { loader ->
                            version.loaders.any { it.equals(loader.toModrinthLoader(), ignoreCase = true) }
                        }
            }
        }
    }
    var selectedGameVersion by rememberSaveable(project?.projectId) { mutableStateOf<String?>(null) }
    var selectedLoader by rememberSaveable(project?.projectId) { mutableStateOf<ModLoader?>(null) }
    val availableLoaders = remember(versions, selectedGameVersion, supportedGameVersions) {
        val mcVersion = supportedGameVersions.firstOrNull { it.mcVer == selectedGameVersion }
        val projectLoaders = versions
            .filter { version -> selectedGameVersion?.let { it in version.gameVersions } != false }
            .flatMap { it.loaders }
        mcVersion?.supportedRdiRemoteModLoaders().orEmpty()
            .filter { loader -> projectLoaders.any { it.equals(loader.toModrinthLoader(), ignoreCase = true) } }
    }

    LaunchedEffect(supportedGameVersions) {
        selectedGameVersion = selectedGameVersion
            ?.takeIf { selected -> supportedGameVersions.any { it.mcVer == selected } }
            ?: supportedGameVersions.firstOrNull { it == McVersion.V211 }?.mcVer
            ?: supportedGameVersions.firstOrNull()?.mcVer
    }
    LaunchedEffect(availableLoaders) {
        selectedLoader = selectedLoader
            ?.takeIf { it in availableLoaders }
            ?: availableLoaders.firstOrNull { it == ModLoader.neoforge }
            ?: availableLoaders.firstOrNull()
    }
    val filteredVersions = remember(versions, selectedGameVersion, selectedLoader) {
        val gameVersion = selectedGameVersion
        val loader = selectedLoader
        if (gameVersion == null || loader == null) {
            emptyList()
        } else {
            versions.filter { version ->
                gameVersion in version.gameVersions &&
                        version.loaders.any { it.equals(loader.toModrinthLoader(), ignoreCase = true) }
            }.sortedByDescending { it.rawPublished }
        }
    }

    when {
        project == null -> Text("正在载入下载版本...", color = MaterialColor.GRAY_700.color)
        versions.isEmpty() -> Text("暂无可下载版本", color = MaterialColor.GRAY_700.color)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("download-filter") {
                RemoteModDownloadFilterBar(
                    gameVersions = supportedGameVersions,
                    loaders = availableLoaders,
                    selectedGameVersion = selectedGameVersion,
                    selectedLoader = selectedLoader,
                    onSelectGameVersion = { selectedGameVersion = it },
                    onSelectLoader = { selectedLoader = it }
                )
            }
            if (filteredVersions.isEmpty()) {
                item("download-empty-filtered") {
                    Text("没有符合筛选条件的版本", color = MaterialColor.GRAY_700.color)
                }
            }
            items(filteredVersions, key = { "download-${it.id}" }) { version ->
                RemoteModDownloadVersionCard(version, onDownload)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteModDownloadFilterBar(
    gameVersions: List<McVersion>,
    loaders: List<ModLoader>,
    selectedGameVersion: String?,
    selectedLoader: ModLoader?,
    onSelectGameVersion: (String) -> Unit,
    onSelectLoader: (ModLoader) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RemoteModDownloadFilterLine(
                title = "MC版本",
                items = gameVersions.map { it.mcVer },
                selected = selectedGameVersion,
                onSelect = onSelectGameVersion
            )
            RemoteModDownloadLoaderFilterLine(
                title = "Loader",
                items = loaders.map { it to it.displayName },
                selected = selectedLoader,
                onSelect = onSelectLoader,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteModDownloadFilterLine(
    title: String,
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    textMapper: (String) -> String = { it }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            color = MaterialColor.GRAY_700.color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                RemoteModDownloadFilterChip(
                    text = textMapper(item),
                    selected = item == selected || item.equals(selected, ignoreCase = true),
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteModDownloadLoaderFilterLine(
    title: String,
    items: List<Pair<ModLoader, String>>,
    selected: ModLoader?,
    onSelect: (ModLoader) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            color = MaterialColor.GRAY_700.color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (loader, text) ->
                RemoteModDownloadFilterChip(
                    text = text,
                    selected = loader == selected,
                    onClick = { onSelect(loader) }
                )
            }
        }
    }
}

@Composable
private fun RemoteModDownloadFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialColor.BLUE_700.color else MaterialColor.GRAY_100.color,
        border = BorderStroke(1.dp, if (selected) MaterialColor.BLUE_700.color else MaterialColor.GRAY_300.color)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun McVersion.supportedRdiRemoteModLoaders(): List<ModLoader> =
    buildList {
        if (mcVer == "1.12.2") {
            add(ModLoader.forge)
        }
        addAll(loaderVersions.keys)
    }.distinct()

private fun ModLoader.toModrinthLoader(): String =
    when (this) {
        ModLoader.cleanroom -> "forge"
        else -> name
    }

private val ModLoader.displayName: String
    get() = when (this) {
        ModLoader.forge -> "Forge"
        ModLoader.neoforge -> "NeoForge"
        ModLoader.cleanroom -> "Cleanroom"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteModDownloadVersionCard(
    version: ModrinthProjectVersionVo,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        elevation = 1.dp,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = version.versionNumber,
                color = MaterialColor.GRAY_900.color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = version.primaryFile?.sizeText ?: "未知大小",
                color = MaterialColor.GRAY_700.color,
                maxLines = 1
            )
            Text(
                text = version.publishedText,
                color = MaterialColor.GRAY_700.color,
                maxLines = 1
            )
            CircleIconButton(
                icon = "\uF019",
                tooltip = "下载模组",
                bgColor = MaterialColor.GREEN_700.color,
                enabled = version.primaryFile != null,
                showText = false
            ) {
                onDownload(version)
            }
        }
    }
}

@Composable
private fun RemoteModDownloadTargetDialog(
    project: ModrinthProjectInfoVo,
    version: ModrinthProjectVersionVo,
    onTaskSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(RemoteModDownloadStep.Target) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            elevation = 8.dp
        ) {
            when (step) {
                RemoteModDownloadStep.Target -> RemoteModDownloadTargetChoice(
                    version = version,
                    onDismiss = onDismiss,
                    onSelectHost = { step = RemoteModDownloadStep.Host },
                    onSelectLocal = { step = RemoteModDownloadStep.Local }
                )

                RemoteModDownloadStep.Host -> RemoteModHostTargetPane(
                    project = project,
                    version = version,
                    onBack = { step = RemoteModDownloadStep.Target },
                    onTaskSubmitted = onTaskSubmitted,
                    onDismiss = onDismiss
                )

                RemoteModDownloadStep.Local -> RemoteModLocalTargetPane(
                    project = project,
                    version = version,
                    onBack = { step = RemoteModDownloadStep.Target },
                    onTaskSubmitted = onTaskSubmitted,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private enum class RemoteModDownloadStep {
    Target,
    Host,
    Local
}

private data class RemoteModHostTarget(
    val brief: Host.BriefVo,
    val detail: Host.DetailVo
)

@Composable
private fun RemoteModDownloadTargetChoice(
    version: ModrinthProjectVersionVo,
    onDismiss: () -> Unit,
    onSelectHost: () -> Unit,
    onSelectLocal: () -> Unit
) {
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "下载${version.primaryFile?.filename ?: version.versionNumber}，要应用到哪里？",
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "全房间共用会作为房间模组，所有玩家同步；仅客户端自用只安装到当前客户端。",
            color = MaterialColor.GRAY_700.color
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
            TextButton(onClick = onSelectHost) {
                Text("全房间共用")
            }
            TextButton(onClick = onSelectLocal) {
                Text("仅客户端自用")
            }
        }
    }
}

@Composable
private fun RemoteModHostTargetPane(
    project: ModrinthProjectInfoVo,
    version: ModrinthProjectVersionVo,
    onBack: () -> Unit,
    onTaskSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<RemoteModHostTarget>>(emptyList()) }
    var selectedHost by remember { mutableStateOf<RemoteModHostTarget?>(null) }
    var adminHostCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version.id) {
        loading = true
        errorMessage = null
        runCatching {
            loadAdminHosts()
        }.onSuccess {
            val compatibleHosts = it.filter { host -> host.supportsRemoteModVersion(version) }
            adminHostCount = it.size
            hosts = compatibleHosts
            selectedHost = compatibleHosts.firstOrNull()
        }.onFailure {
            errorMessage = it.message ?: "加载房间失败"
        }
        loading = false
    }

    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择房间",
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )
        Text("只显示你拥有管理权限，且MC版本匹配的房间。", color = MaterialColor.GRAY_700.color)
        errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
        if (loading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (hosts.isEmpty()) {
            val emptyText = if (adminHostCount == 0) {
                "没有可管理的房间"
            } else {
                "没有匹配MC版本的房间，当前Mod版本支持${version.supportedMcVersionText()}"
            }
            Text(emptyText, color = MaterialColor.GRAY_700.color)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hosts, key = { it.brief._id.toHexString() }) { host ->
                    host.brief.HostCard(
                        miniMode = true,
                        selected = selectedHost?.brief?._id == host.brief._id,
                        onClick = { selectedHost = host }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, enabled = !submitting) {
                Text("返回")
            }
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("取消")
            }
            TextButton(
                enabled = !submitting && selectedHost != null,
                onClick = {
                    val host = selectedHost ?: return@TextButton
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            if (!host.supportsRemoteModVersion(version)) {
                                error("房间MC${host.detail.modpack.mcVer.mcVer}与当前Mod版本不匹配，当前Mod版本支持${version.supportedMcVersionText()}")
                            }
                            val mod = RemoteModDownloadService.toMod(project, version)
                            val response = server.makeRequest<Unit>(
                                path = "host/${host.brief._id}/mods/extra",
                                method = HttpMethod.Post
                            ) {
                                contentType(ContentType.Application.Json)
                                setBody(serdesJson.encodeToString(listOf(mod)))
                            }
                            if (!response.ok) error(response.msg)
                        }.onSuccess {
                            onTaskSubmitted("已提交房间附加Mod添加任务，请在邮件中查看进度")
                            onDismiss()
                        }.onFailure {
                            errorMessage = it.message ?: "提交房间附加Mod失败"
                        }
                        submitting = false
                    }
                }
            ) {
                Text(if (submitting) "提交中..." else "开始下载")
            }
        }
    }
}

@Composable
private fun RemoteModLocalTargetPane(
    project: ModrinthProjectInfoVo,
    version: ModrinthProjectVersionVo,
    onBack: () -> Unit,
    onTaskSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var packdirs by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var localPackCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version.id) {
        loading = true
        errorMessage = null
        runCatching {
            ModpackService.getLocalPackDirs()
        }.onSuccess {
            localPackCount = it.size
            val compatiblePackdirs = if (version.gameVersions.isEmpty()) {
                emptyList()
            } else {
                it.filter { packdir -> packdir.readLocalMcVersion() in version.gameVersions }
            }
            packdirs = compatiblePackdirs
            selectedPack = compatiblePackdirs.firstOrNull()
        }.onFailure {
            errorMessage = it.message ?: "加载本地整合包失败"
        }
        loading = false
    }

    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择本地整合包",
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold
        )
        Text("只显示MC版本匹配的本地整合包，会下载到所选整合包的mods目录。", color = MaterialColor.GRAY_700.color)
        errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
        if (loading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (packdirs.isEmpty()) {
            val emptyText = when {
                version.gameVersions.isEmpty() -> "当前Mod版本缺少MC版本信息，不能自动安装"
                localPackCount == 0 -> "没有已安装的本地整合包"
                else -> "没有匹配MC版本的本地整合包，当前Mod版本支持${version.supportedMcVersionText()}"
            }
            Text(emptyText, color = MaterialColor.GRAY_700.color)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(packdirs, key = { it.versionId }) { packdir ->
                    ModpackManageCard(
                        packdir = packdir,
                        selected = selectedPack?.versionId == packdir.versionId,
                        miniMode = true,
                        onClick = { selectedPack = packdir }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
            TextButton(
                enabled = selectedPack != null,
                onClick = {
                    val packdir = selectedPack ?: return@TextButton
                    val mod = runCatching {
                        RemoteModDownloadService.toMod(project, version)
                    }.getOrElse {
                        errorMessage = it.message ?: "创建下载任务失败"
                        return@TextButton
                    }
                    ClientTaskManager.submit(
                        task = RemoteModDownloadService.downloadToLocalModpackTask2(mod, packdir),
                        dedupeKey = "remote-mod-local:${packdir.versionId}:${mod.projectId}:${mod.fileId}"
                    )
                    onTaskSubmitted("已加入任务列表")
                    onDismiss()
                }
            ) {
                Text("开始下载")
            }
        }
    }
}

private fun RemoteModHostTarget.supportsRemoteModVersion(version: ModrinthProjectVersionVo): Boolean =
    detail.modpack.mcVer.mcVer in version.gameVersions

private fun ModrinthProjectVersionVo.supportedMcVersionText(): String =
    gameVersions.takeIf { it.isNotEmpty() }?.joinToString("、") { "MC$it" } ?: "未知MC版本"

private fun ModpackLocalDir.readLocalMcVersion(): String? = runCatching {
    val manifest = dir.resolve("$versionId.json")
    if (!manifest.isFile) return@runCatching null
    serdesJson.parseToJsonElement(manifest.readText())
        .jsonObject["inheritsFrom"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)
}.getOrNull()

private suspend fun loadAdminHosts(): List<RemoteModHostTarget> {
    val result = mutableListOf<RemoteModHostTarget>()
    var page = 0
    while (true) {
        val pageResponse = server.makeRequest<List<Host.BriefVo>>("host/my/$page")
        if (!pageResponse.ok) error(pageResponse.msg)
        val pageHosts = pageResponse.data.orEmpty()
        if (pageHosts.isEmpty()) break
        pageHosts.forEach { brief ->
            val detailResponse = server.makeRequest<Host.DetailVo>("host/${brief._id}/detail")
            if (!detailResponse.ok) error(detailResponse.msg)
            val detail = detailResponse.data ?: return@forEach
            val canManage = detail.ownerId == loggedAccount._id || detail.members.any {
                it.id == loggedAccount._id && it.role.level <= Role.ADMIN.level
            }
            if (canManage) {
                result += RemoteModHostTarget(brief, detail)
            }
        }
        page += 1
    }
    return result
}

@Composable
private fun RemoteModFileRow(file: ModrinthProjectVersionFileVo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.filename,
            color = MaterialColor.GRAY_900.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(file.sizeText, color = MaterialColor.GRAY_700.color, maxLines = 1)
        TextButton(onClick = { openUrl(file.url) }) {
            Text("打开")
        }
    }
}

@Composable
private fun RemoteModDependencyRow(dependency: ModrinthProjectVersionDependencyVo) {
    val target = dependency.projectId ?: dependency.versionId ?: "未知"
    val type = dependency.dependencyType ?: "dependency"
    Text(
        text = "$type: $target",
        color = MaterialColor.GRAY_800.color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RemoteModInfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialColor.GRAY_100.color,
        border = BorderStroke(1.dp, MaterialColor.GRAY_300.color)
    ) {
        Text(
            text = text,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
