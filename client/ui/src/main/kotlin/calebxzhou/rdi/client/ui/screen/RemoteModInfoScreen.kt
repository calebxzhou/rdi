package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import calebxzhou.rdi.client.service.CurseForgeProjectInfoService
import calebxzhou.rdi.client.service.ModrinthProjectInfoService
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.RemoteModDownloadService
import calebxzhou.rdi.client.service.RemoteModDependencyService
import calebxzhou.rdi.client.service.RemoteModLocalization
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ImageIconButton
import calebxzau.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.HostCard
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.client.ui.comp.RemoteModCard
import calebxzhou.rdi.client.ui.comp.WebPagePane
import calebxzau.rdi.client.ui.openUrl
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
import org.bson.types.ObjectId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LegacyRemoteModInfoScreen(
    mod: RemoteModCardVo,
    onBack: () -> Unit,
    onOpenDependencyMod: (RemoteModCardVo) -> Unit = {},
    targetHostId: ObjectId? = null,
    targetHostMcVer: McVersion? = null
) {
    var project by remember { mutableStateOf<ModrinthProjectInfoVo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var downloadVersion by remember { mutableStateOf<ModrinthProjectVersionVo?>(null) }
    var loadingCurseForgeVersionFilterKey by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mod.source, mod.projectId) {
        loading = true
        errorMessage = null
        runCatching {
            when (mod.source) {
                RemoteModSource.MODRINTH -> ModrinthProjectInfoService.loadProjectInfo(mod.projectId)
                RemoteModSource.CURSEFORGE -> CurseForgeProjectInfoService.loadProjectInfo(mod.projectId)
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
    val mcmodId = when (mod.source) {
        RemoteModSource.MODRINTH -> RemoteModLocalization.mcmodIdByModrinthSlug(sourceSlug)
        RemoteModSource.CURSEFORGE -> RemoteModLocalization.mcmodIdByCurseForgeSlug(sourceSlug)
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
    val versions = project?.versions.orEmpty()
    val lockedGameVersion = targetHostMcVer?.mcVer
    val supportedGameVersions = remember {
        McVersion.entries.filter { it.enabled }
    }
    val visibleGameVersions = remember(supportedGameVersions, lockedGameVersion) {
        lockedGameVersion?.let { locked ->
            supportedGameVersions.filter { it.mcVer == locked }
        } ?: supportedGameVersions
    }

    fun ModrinthProjectVersionVo.supportsSelectedLoader(
        loader: ModLoader
    ): Boolean =
        loaders.isEmpty() || loaders.any { it.equals(loader.toModrinthLoader(), ignoreCase = true) }

    fun versionMatchesDownloadFilter(
        version: ModrinthProjectVersionVo,
        gameVersion: String,
        loader: ModLoader
    ): Boolean =
        gameVersion in version.gameVersions && version.supportsSelectedLoader(loader)

    var selectedGameVersion by rememberSaveable(project?.projectId) { mutableStateOf<String?>(null) }
    var selectedLoader by rememberSaveable(project?.projectId) { mutableStateOf<ModLoader?>(null) }
    val availableLoaders = remember(selectedGameVersion, visibleGameVersions) {
        visibleGameVersions
            .firstOrNull { it.mcVer == selectedGameVersion }
            ?.supportedRdiRemoteModLoaders()
            .orEmpty()
    }

    LaunchedEffect(project?.projectId, visibleGameVersions, lockedGameVersion) {
        selectedGameVersion = lockedGameVersion
            ?.takeIf { locked -> visibleGameVersions.any { it.mcVer == locked } }
            ?: selectedGameVersion?.takeIf { selected -> visibleGameVersions.any { it.mcVer == selected } }
            ?: visibleGameVersions.firstOrNull { it == McVersion.V211 }?.mcVer
            ?: visibleGameVersions.firstOrNull()?.mcVer
    }
    LaunchedEffect(availableLoaders) {
        selectedLoader = selectedLoader
            ?.takeIf { it in availableLoaders }
            ?: availableLoaders.firstOrNull { it == ModLoader.neoforge }
            ?: availableLoaders.firstOrNull()
    }
    var loadedCurseForgeVersionFilterKey by rememberSaveable(mod.source.name, mod.projectId) { mutableStateOf<String?>(null) }
    LaunchedEffect(mod.source, mod.projectId, selectedGameVersion, selectedLoader) {
        if (mod.source != RemoteModSource.CURSEFORGE) return@LaunchedEffect
        val gameVersion = selectedGameVersion ?: return@LaunchedEffect
        val loader = selectedLoader ?: return@LaunchedEffect
        if (project == null) return@LaunchedEffect
        val filterKey = "$gameVersion:${loader.name}"
        if (loadedCurseForgeVersionFilterKey == filterKey) return@LaunchedEffect
        loadingCurseForgeVersionFilterKey = filterKey
        runCatching {
            CurseForgeProjectInfoService.loadProjectVersions(
                projectId = mod.projectId,
                mcVersion = gameVersion,
                loader = loader.toModrinthLoader()
            )
        }.onSuccess { loadedVersions ->
            project = project?.withMergedVersions(loadedVersions)
            loadedCurseForgeVersionFilterKey = filterKey
        }.onFailure {
            snackbarHostState.showSnackbar(
                it.message ?: "版本列表加载失败",
                duration = SnackbarDuration.Short
            )
        }
        if (loadingCurseForgeVersionFilterKey == filterKey) {
            loadingCurseForgeVersionFilterKey = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow(title, onBack) {
                Text("\uF019 $downloadsText".asIconText, color = MaterialColor.GRAY_900.color)
                followsText?.let {
                    Space8w()
                    Text("\uDB80\uDED1 $it".asIconText, color = MaterialColor.GRAY_900.color)
                }
                if (activeTab == RemoteModInfoTab.Download && supportedGameVersions.isNotEmpty()) {
                    selectedGameVersion?.let { gameVersion ->
                        Space8w()
                        McVersionIconSelector(
                            versions = visibleGameVersions,
                            selectedGameVersion = gameVersion,
                            enabled = lockedGameVersion == null,
                            onSelect = { selectedGameVersion = it.mcVer }
                        )
                    }
                    selectedLoader?.let { loader ->
                        Space8w()
                        ModLoaderIconSelector(
                            loaders = availableLoaders,
                            selectedLoader = loader,
                            onSelect = { selectedLoader = it }
                        )
                    }
                }
                (project?.sourceUrl ?: mod.defaultSourceUrl())?.takeIf(String::isNotBlank)?.let { url ->
                    Space8w()
                    CircleIconButton(
                        icon = "\uE8A7",
                        tooltip = "打开来源页面",
                        bgColor = MaterialColor.GREEN_700.color
                    ) {
                        openUrl(url)
                    }
                }
            }
            Space8h()
            TabRow(
                selectedTabIndex = activeTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
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

                errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)

                activeTab == RemoteModInfoTab.Download -> RemoteModDownloadTab(
                    project = project,
                    selectedGameVersion = selectedGameVersion,
                    selectedLoader = selectedLoader,
                    versionsLoading = loadingCurseForgeVersionFilterKey != null,
                    versionFilter = { version, gameVersion, loader ->
                        versionMatchesDownloadFilter(version, gameVersion, loader)
                    },
                    onOpenDependencyMod = onOpenDependencyMod,
                    onDownload = { downloadVersion = it }
                )
                activeTab == RemoteModInfoTab.Description || activeTab == RemoteModInfoTab.Mcmod ->
                    RemoteModWebInfoTab(activeTab, project, mod, mcmodUrl)
                else -> RemoteModVersionsTab(project, lockedGameVersion)
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }

    val loadedProject = project
    if (loadedProject != null) downloadVersion?.let { version ->
        RemoteModDownloadTargetDialog(
            project = loadedProject,
            version = version,
            onTaskSubmitted = { okMessage = it },
            onDismiss = { downloadVersion = null },
            targetHostId = targetHostId,
            targetHostMcVer = targetHostMcVer
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
private fun McVersionIconSelector(
    versions: List<McVersion>,
    selectedGameVersion: String,
    enabled: Boolean,
    onSelect: (McVersion) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        versions.forEach { version ->
            val selected = version.mcVer == selectedGameVersion
            ImageIconButton(
                icon = version.iconName,
                tooltip = "MC${version.mcVer}",
                size = 36,
                contentPadding = PaddingValues(0.dp),
                bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialColor.PURPLE_100.color,
                enabled = enabled || selected,
                showText = false
            ) {
                if (enabled) onSelect(version)
            }
        }
    }
}

@Composable
private fun ModLoaderIconSelector(
    loaders: List<ModLoader>,
    selectedLoader: ModLoader,
    onSelect: (ModLoader) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        loaders.forEach { loader ->
            val selected = loader == selectedLoader
            ImageIconButton(
                icon = loader.name,
                tooltip = loader.name,
                size = 36,
                contentPadding = PaddingValues(0.dp),
                bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialColor.PURPLE_100.color,
                showText = false
            ) {
                onSelect(loader)
            }
        }
    }
}

@Composable
private fun RemoteModTitleFilterChip(
    label: String,
    text: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .width(100.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialColor.BLUE_700.color),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = MaterialColor.GRAY_700.color,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = text,
                        color = MaterialColor.BLUE_700.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "\uEB6E".asIconText,
                    color = MaterialColor.BLUE_700.color,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
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
        tab == RemoteModInfoTab.Description && mod.source == RemoteModSource.CURSEFORGE ->
            (project?.sourceUrl ?: mod.defaultSourceUrl())
                ?.takeIf(String::isNotBlank)
                ?.let { RemoteModWebPage(url = it) }
        tab == RemoteModInfoTab.Mcmod && mcmodUrl != null -> RemoteModWebPage(url = mcmodUrl, title = "MC百科")
        else -> null
    }
    if (page == null) {
        val description = project?.description?.takeIf(String::isNotBlank)
        if (tab == RemoteModInfoTab.Description && description != null) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = description.toReadableModrinthDescription(),
                        color = MaterialColor.GRAY_900.color
                    )
                }
            }
        } else {
            Text("暂无来源网页", color = MaterialColor.GRAY_700.color)
        }
    } else {
        WebPagePane(
            url = page.url,
            title = page.title,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private data class RemoteModWebPage(val url: String, val title: String? = null)

private fun RemoteModCardVo.defaultSourceUrl(): String? =
    when (source) {
        RemoteModSource.MODRINTH -> slug?.takeIf(String::isNotBlank)?.let { "https://modrinth.com/mod/$it" }
        RemoteModSource.CURSEFORGE -> slug?.takeIf(String::isNotBlank)
            ?.let { "https://www.curseforge.com/minecraft/mc-mods/$it" }
    }

private fun ModrinthProjectInfoVo.withMergedVersions(
    newVersions: List<ModrinthProjectVersionVo>
): ModrinthProjectInfoVo {
    if (newVersions.isEmpty()) return this
    val mergedVersions = (newVersions + versions)
        .distinctBy { it.id }
        .sortedByDescending { it.rawPublished }
    return copy(
        gameVersions = (gameVersions + newVersions.flatMap { it.gameVersions }).distinct(),
        loaders = (loaders + newVersions.flatMap { it.loaders }).distinct(),
        versionIds = mergedVersions.map { it.id },
        versions = mergedVersions
    )
}

@Composable
private fun RemoteModVersionsTab(project: ModrinthProjectInfoVo?, lockedGameVersion: String?) {
    val allVersions = project?.versions.orEmpty()
    val versions = remember(allVersions, lockedGameVersion) {
        lockedGameVersion?.let { locked ->
            allVersions.filter { locked in it.gameVersions }
        } ?: allVersions
    }
    when {
        project == null -> Text("正在载入版本...", color = MaterialColor.GRAY_700.color)
        allVersions.isEmpty() -> Text("暂无版本", color = MaterialColor.GRAY_700.color)
        versions.isEmpty() -> Text("当前房间MC版本没有可用版本", color = MaterialColor.GRAY_700.color)
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
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
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
                    style = MaterialTheme.typography.titleMedium,
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("依赖", color = MaterialColor.GRAY_800.color, fontWeight = FontWeight.Bold)
                    version.dependencies.take(8).forEach { dependency ->
                        RemoteModDependencyRow(dependency)
                    }
                }
            }
            if (version.changelog.isNotBlank() && version.changelog != "暂无更新日志") {
                Text(
                    text = version.changelog.toReadableModrinthDescription(),
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
    selectedGameVersion: String?,
    selectedLoader: ModLoader?,
    versionsLoading: Boolean,
    versionFilter: (ModrinthProjectVersionVo, String, ModLoader) -> Boolean,
    onOpenDependencyMod: (RemoteModCardVo) -> Unit,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    val versions = project?.versions.orEmpty()
    val filteredVersions = remember(versions, selectedGameVersion, selectedLoader) {
        val gameVersion = selectedGameVersion
        val loader = selectedLoader
        if (gameVersion == null || loader == null) {
            emptyList()
        } else {
            versions.filter { version ->
                versionFilter(version, gameVersion, loader)
            }.sortedByDescending { it.rawPublished }
        }
    }

    when {
        project == null -> Text("正在载入下载版本...", color = MaterialColor.GRAY_700.color)
        versionsLoading -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        versions.isEmpty() -> Text("暂无可下载版本", color = MaterialColor.GRAY_700.color)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("download-dependencies") {
                RemoteModDependenciesPane(
                    versions = filteredVersions,
                    onOpenDependencyMod = onOpenDependencyMod
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

@Composable
private fun RemoteModDependenciesPane(
    versions: List<ModrinthProjectVersionVo>,
    onOpenDependencyMod: (RemoteModCardVo) -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var dependencies by remember { mutableStateOf<List<RemoteModCardVo>>(emptyList()) }
    val requiredDependencyKey = remember(versions) {
        versions
            .flatMap { it.dependencies }
            .filter { it.required }
            .joinToString("|") { "${it.source}:${it.projectId}:${it.versionId}" }
    }

    LaunchedEffect(requiredDependencyKey) {
        if (requiredDependencyKey.isBlank()) {
            dependencies = emptyList()
            errorMessage = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        errorMessage = null
        runCatching {
            RemoteModDependencyService.loadRequiredDependencyCards(versions)
        }.onSuccess {
            dependencies = it
        }.onFailure {
            dependencies = emptyList()
            errorMessage = it.message ?: "前置Mod加载失败"
        }
        loading = false
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("前置Mod", color = MaterialColor.GRAY_900.color, fontWeight = FontWeight.Bold)
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }

                errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                dependencies.isEmpty() -> Text("当前版本没有必需前置Mod", color = MaterialColor.GRAY_700.color)
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dependencies.forEach { dependency ->
                        RemoteModCard(
                            mod = dependency,
                            modifier = Modifier.width(260.dp),
                            compact = true,
                            onClick = { onOpenDependencyMod(dependency) }
                        )
                    }
                }
            }
        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteModDownloadVersionCard(
    version: ModrinthProjectVersionVo,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
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
    onDismiss: () -> Unit,
    targetHostId: ObjectId? = null,
    targetHostMcVer: McVersion? = null
) {
    var step by remember(targetHostId) {
        mutableStateOf(if (targetHostId == null) RemoteModDownloadStep.Target else RemoteModDownloadStep.FixedHost)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
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

                RemoteModDownloadStep.FixedHost -> {
                    val fixedTargetHostId = targetHostId
                    if (fixedTargetHostId == null) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("当前房间信息已失效", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onDismiss) {
                                Text("关闭")
                            }
                        }
                    } else {
                        RemoteModFixedHostTargetPane(
                            project = project,
                            version = version,
                            targetHostId = fixedTargetHostId,
                            targetHostMcVer = targetHostMcVer,
                            onTaskSubmitted = onTaskSubmitted,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

private enum class RemoteModDownloadStep {
    Target,
    Host,
    Local,
    FixedHost
}

private data class RemoteModHostTarget(
    val brief: Host.BriefVo,
    val detail: Host.DetailVo
)

@Composable
private fun RemoteModFixedHostTargetPane(
    project: ModrinthProjectInfoVo,
    version: ModrinthProjectVersionVo,
    targetHostId: ObjectId,
    targetHostMcVer: McVersion?,
    onTaskSubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "添加到当前房间",
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("会添加为当前房间的附加Mod。", color = MaterialColor.GRAY_700.color)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("取消")
            }
            TextButton(
                enabled = !submitting,
                onClick = {
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            if (targetHostMcVer != null && targetHostMcVer.mcVer !in version.gameVersions) {
                                error("房间MC${targetHostMcVer.mcVer}与当前Mod版本不匹配，当前Mod版本支持${version.supportedMcVersionText()}")
                            }
                            val mod = RemoteModDownloadService.toMod(project, version)
                            val response = server.makeRequest<Unit>(
                                path = "host/$targetHostId/mods/extra",
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
            style = MaterialTheme.typography.titleMedium,
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("只显示你拥有管理权限，且MC版本匹配的房间。", color = MaterialColor.GRAY_700.color)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                it.filter { packdir -> packdir.vo.mcVer.mcVer in version.gameVersions }
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("只显示MC版本匹配的本地整合包，会下载到所选整合包的mods目录。", color = MaterialColor.GRAY_700.color)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

private fun String.toReadableModrinthDescription(): String {
    return replace(Regex("<iframe[\\s\\S]*?</iframe>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n--------------------\n")
        .replace(Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")) { match ->
            val alt = match.groupValues[1].ifBlank { "图片" }
            "$alt: ${match.groupValues[2]}"
        }
        .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)")) { match ->
            "${match.groupValues[1]} (${match.groupValues[2]})"
        }
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lines()
        .map { it.trimEnd() }
        .dropWhile(String::isBlank)
        .joinToString("\n")
}

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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
