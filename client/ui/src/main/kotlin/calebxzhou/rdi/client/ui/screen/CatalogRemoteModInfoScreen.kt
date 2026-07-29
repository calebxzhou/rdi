package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ImageIconButton
import calebxzau.rdi.client.ui.MainColumn
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.lgr
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.client.modcatalog.CatalogDetailsRequest
import calebxzhou.rdi.client.modcatalog.CatalogDigestAlgorithm
import calebxzhou.rdi.client.modcatalog.CatalogFile
import calebxzhou.rdi.client.modcatalog.CatalogFileCursor
import calebxzhou.rdi.client.modcatalog.CatalogFileRequest
import calebxzhou.rdi.client.modcatalog.CatalogFileRef
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.modcatalog.CatalogModDetails
import calebxzhou.rdi.client.modcatalog.CatalogProjectRef
import calebxzhou.rdi.client.modcatalog.CatalogTarget
import calebxzhou.rdi.client.modcatalog.DependencyRequest
import calebxzhou.rdi.client.modcatalog.DependencyRequirement
import calebxzhou.rdi.client.modcatalog.DependencyTarget
import calebxzhou.rdi.client.modcatalog.EnvironmentRequirement
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.client.modcatalog.ReleaseChannel
import calebxzhou.rdi.client.modcatalog.ResolvedDownload
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.LocalContentInstallRecord
import calebxzhou.rdi.client.service.LocalContentInstallStore
import calebxzhou.rdi.client.service.LocalContentType
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.RemoteModDownloadService
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.McPlayStore
import calebxzhou.rdi.client.ui.comp.CatalogModCard
import calebxzhou.rdi.client.ui.comp.WebPagePane
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.model.Role
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RemoteModInfoScreen(
    route: RemoteModInfoRoute,
    catalog: ModCatalog,
    onBack: () -> Unit,
    onOpenDependencyMod: (CatalogMod) -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null,
    onTargetUnavailable: () -> Unit = onBack
) {
    var mod by remember(route.platform, route.projectId) { mutableStateOf<CatalogMod?>(null) }
    var targetPack by remember(route.targetLocalVersionId) { mutableStateOf<ModpackLocalDir?>(null) }
    var loading by remember(route.platform, route.projectId, route.targetLocalVersionId) { mutableStateOf(true) }
    var errorMessage by remember(route.platform, route.projectId, route.targetLocalVersionId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(route) {
        loading = true
        errorMessage = null
        val loadedPack = route.targetLocalVersionId?.let { versionId ->
            ModpackService.getLocalPackDirs().firstOrNull { it.versionId == versionId }
        }
        if (route.targetLocalVersionId != null && loadedPack == null) {
            loading = false
            onTargetUnavailable()
            return@LaunchedEffect
        }
        runCatching {
            val platform = ModPlatform.valueOf(route.platform.uppercase())
            val ref = CatalogProjectRef(platform, route.projectId)
            catalog.getMods(setOf(ref)).getOrThrow().value[ref]
                ?: error("模组不存在")
        }.onSuccess { loadedMod ->
            mod = loadedMod
            targetPack = loadedPack
        }.onFailure { cause ->
            errorMessage = cause.message ?: "加载模组详情失败"
        }
        loading = false
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            when {
                loading -> MainColumn { CircularProgressIndicator() }
                errorMessage != null -> MainColumn {
                    TitleRow("模组详情", onBack)
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                mod != null -> RemoteModInfoContent(
                    catalog = catalog,
                    mod = mod!!,
                    onBack = onBack,
                    onOpenDependencyMod = onOpenDependencyMod,
                    targetHostId = route.targetHostId?.let(::ObjectId),
                    targetHost2Id = route.targetHost2Id,
                    targetHostMcVer = targetPack?.vo?.mcVer ?: route.requiredMcVer?.let(McVersion::from),
                    targetLoader = targetPack?.vo?.modloader ?: route.requiredLoader?.let(ModLoader::from),
                    targetLocalPack = targetPack,
                    onOpenTaskList = onOpenTaskList,
                    onTargetUnavailable = onTargetUnavailable
                )
            }
        }
    }
}

@Composable
private fun RemoteModInfoContent(
    catalog: ModCatalog,
    mod: CatalogMod,
    onBack: () -> Unit,
    onOpenDependencyMod: (CatalogMod) -> Unit = {},
    targetHostId: ObjectId? = null,
    targetHost2Id: String? = null,
    targetHostMcVer: McVersion? = null,
    targetLoader: ModLoader? = null,
    targetLocalPack: ModpackLocalDir? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    onTargetUnavailable: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var details by remember(mod.identity.stableKey) { mutableStateOf<CatalogModDetails?>(null) }
    var detailsLoading by remember(mod.identity.stableKey) { mutableStateOf(true) }
    var errorMessage by remember(mod.identity.stableKey) { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(mod.identity.stableKey) { mutableStateOf(0) }
    val versions = remember { McVersion.entries.filter(McVersion::enabled) }
    var selectedMcVersion by rememberSaveable(mod.identity.stableKey) {
        mutableStateOf(targetHostMcVer ?: McVersion.V211)
    }
    var selectedLoader by rememberSaveable(mod.identity.stableKey) {
        mutableStateOf(
            targetLocalPack?.vo?.modloader ?: targetLoader
            ?: (targetHostMcVer ?: McVersion.V211).loaderVersions.keys.first()
        )
    }
    var includeAlpha by rememberSaveable(mod.identity.stableKey) { mutableStateOf(false) }
    var files by remember(mod.identity.stableKey) { mutableStateOf<List<CatalogFile>>(emptyList()) }
    var fileCursor by remember(mod.identity.stableKey) { mutableStateOf<CatalogFileCursor?>(null) }
    var filesLoading by remember(mod.identity.stableKey) { mutableStateOf(false) }
    var loadingMore by remember(mod.identity.stableKey) { mutableStateOf(false) }
    var expandedFile by remember(mod.identity.stableKey) { mutableStateOf<CatalogFileRef?>(null) }
    var changelog by remember(mod.identity.stableKey) { mutableStateOf("") }
    var dependencyMods by remember(mod.identity.stableKey) { mutableStateOf<List<CatalogMod>>(emptyList()) }
    var dependencySummary by remember(mod.identity.stableKey) { mutableStateOf<String?>(null) }
    var expansionLoading by remember(mod.identity.stableKey) { mutableStateOf(false) }
    var downloadSelection by remember(mod.identity.stableKey) { mutableStateOf<DownloadSelection?>(null) }
    var localInstallRecords by remember(targetLocalPack?.versionId) {
        mutableStateOf<List<LocalContentInstallRecord>>(emptyList())
    }
    var localInstallHealth by remember(targetLocalPack?.versionId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val taskEntries by ClientTaskManager.entries.collectAsState()

    val loaders = (targetLocalPack?.vo?.modloader ?: targetLoader)?.let(::listOf)
        ?: selectedMcVersion.loaderVersions.keys.toList()
    val mcmodUrl = mod.mcmodId?.let { "https://www.mcmod.cn/class/$it.html" }
    val tabs = buildList {
        add("下载")
        add("介绍")
        if (mcmodUrl != null) add("百科")
    }

    LaunchedEffect(mod.identity.stableKey) {
        detailsLoading = true
        val outcome = catalog.getDetails(CatalogDetailsRequest(mod)).getOrElse { cause ->
            lgr.warn(cause) { "加载模组详情失败" }
            errorMessage = "加载模组详情失败，请稍后重试"
            null
        }
        details = outcome?.value
        detailsLoading = false
    }

    LaunchedEffect(
        targetLocalPack?.versionId,
        mod.identity.stableKey,
        taskEntries.map { it.runId to it.status }
    ) {
        localInstallRecords = targetLocalPack?.let { pack ->
            withContext(Dispatchers.IO) {
                LocalContentInstallStore.read(pack).getOrElse { cause ->
                    lgr.warn(cause) { "读取本地Mod安装状态失败" }
                    emptyList()
                }
            }
        }.orEmpty()
        localInstallHealth = targetLocalPack?.let { pack ->
            withContext(Dispatchers.IO) {
                val projectIds = mod.sources.map { it.ref.projectId }.toSet()
                localInstallRecords.filter {
                    it.type == LocalContentType.MOD && it.projectId in projectIds
                }.mapNotNull { record ->
                    val file = pack.dir.resolve("mods").resolve(record.fileName)
                    val problem = when {
                        !file.isFile -> "文件缺失"
                        record.hash == null -> null
                        record.source == "mr" && !file.toPath().sha1.equals(record.hash, ignoreCase = true) ->
                            "文件已被修改"
                        record.source == "cf" && file.toPath().murmur2.toString() != record.hash ->
                            "文件已被修改"
                        else -> null
                    }
                    problem?.let { record.projectKey to it }
                }.toMap()
            }
        }.orEmpty()
    }

    LaunchedEffect(selectedMcVersion) {
        if (selectedLoader !in loaders) selectedLoader = loaders.first()
    }

    suspend fun loadFiles(reset: Boolean) {
        if (reset) filesLoading = true else loadingMore = true
        errorMessage = null
        val outcome = catalog.listFiles(
            CatalogFileRequest(
                mod = mod,
                target = CatalogTarget(selectedMcVersion, selectedLoader),
                channels = if (includeAlpha) ReleaseChannel.entries.toSet()
                else setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA),
                cursor = if (reset) null else fileCursor
            )
        ).getOrElse { cause ->
            lgr.warn(cause) { "加载模组可用版本失败" }
            errorMessage = "加载可用版本失败，请稍后重试"
            null
        }
        outcome?.let {
            files = (if (reset) it.value.items else files + it.value.items)
                .sortedBy { file -> if (file.channel == ReleaseChannel.BETA) 0 else 1 }
            fileCursor = it.value.nextCursor
            if (it.issues.isNotEmpty()) errorMessage = "部分目录信息暂时不可用，已自动选择可用结果"
        }
        filesLoading = false
        loadingMore = false
    }

    LaunchedEffect(selectedTab, selectedMcVersion, selectedLoader, includeAlpha) {
        if (selectedTab == 0) loadFiles(reset = true)
    }

    LaunchedEffect(expandedFile) {
        val ref = expandedFile ?: return@LaunchedEffect
        val file = files.firstOrNull { it.ref == ref } ?: return@LaunchedEffect
        expansionLoading = true
        changelog = catalog.getChangelog(ref).getOrElse { cause ->
            lgr.warn(cause) { "加载模组更新说明失败" }
            errorMessage = "加载更新说明失败"
            ""
        }
        val graph = catalog.resolveDependencies(
            DependencyRequest(listOf(file), CatalogTarget(selectedMcVersion, selectedLoader))
        ).getOrElse { cause ->
            lgr.warn(cause) { "解析模组依赖失败" }
            errorMessage = "解析依赖失败"
            null
        }
        if (graph != null) {
            val dependencyFiles = graph.value.nodes.values.map { it.file }.filter { it.ref != ref }
            val refs = dependencyFiles.mapTo(linkedSetOf(), CatalogFile::project)
            dependencyMods = catalog.getMods(refs).getOrElse { cause ->
                lgr.warn(cause) { "加载依赖模组信息失败" }
                errorMessage = "加载依赖信息失败"
                null
            }?.value?.values?.distinctBy { it.identity.stableKey }.orEmpty()
            dependencySummary = "依赖${dependencyFiles.size}个，未解析${graph.value.unresolved.size}个，循环${graph.value.cycles.size}个"
        }
        expansionLoading = false
    }

    val title = mod.nameCn ?: mod.name
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow(title, onBack) {
                Text("\uF019 ${mod.downloadCount}".asIconText, color = MaterialColor.GRAY_900.color)
                if (selectedTab == 0) {
                    Space8w()
                    if (targetLocalPack != null) {
                        Text(
                            "${targetLocalPack.vo.mcVer.mcVer} · ${targetLocalPack.vo.modloader.displayName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else versions.filter { targetHostMcVer == null || it == targetHostMcVer }.forEach { version ->
                        ImageIconButton(
                            icon = version.iconName,
                            tooltip = "MC${version.mcVer}",
                            size = 34,
                            showText = false,
                            enabled = targetHostMcVer == null || version == targetHostMcVer,
                            bgColor = if (selectedMcVersion == version) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) { selectedMcVersion = version }
                    }
                    loaders.forEach { loader ->
                        ImageIconButton(
                            icon = loader.name,
                            tooltip = loader.displayName,
                            size = 34,
                            showText = false,
                            bgColor = if (selectedLoader == loader) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) { selectedLoader = loader }
                    }
                }
            }
            Space8h()
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = index == selectedTab, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            Space8h()
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                selectedTab == 0 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { includeAlpha = !includeAlpha }) {
                            Text(if (includeAlpha) "隐藏Alpha" else "显示Alpha")
                        }
                    }
                    if (filesLoading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files, key = { it.ref.fileId }) { file ->
                            val baseInstallStatus = when {
                                targetLocalPack == null -> null
                                else -> taskEntries.localModTaskStatus(targetLocalPack, file)
                                    ?: localInstallRecords.modStatus(file, localInstallHealth)
                            }
                            val installStatus = if (
                                targetLocalPack != null &&
                                McPlayStore.aliveCount(targetLocalPack.versionId) > 0 &&
                                localInstallRecords.hasManagedMod(file) &&
                                baseInstallStatus != "已安装"
                            ) "运行中不可更新" else baseInstallStatus
                            CatalogFileRow(
                                file = file,
                                installStatus = installStatus,
                                downloadEnabled = installStatus !in setOf("下载中", "已安装", "运行中不可更新"),
                                expanded = expandedFile == file.ref,
                                expansionLoading = expansionLoading && expandedFile == file.ref,
                                changelog = changelog.takeIf { expandedFile == file.ref }.orEmpty(),
                                dependencySummary = dependencySummary.takeIf { expandedFile == file.ref },
                                dependencyMods = dependencyMods.takeIf { expandedFile == file.ref }.orEmpty(),
                                onToggle = {
                                    expandedFile = if (expandedFile == file.ref) null else file.ref
                                    changelog = ""
                                    dependencyMods = emptyList()
                                    dependencySummary = null
                                },
                                onOpenDependency = onOpenDependencyMod,
                                onDownload = {
                                    if (targetLocalPack != null && !targetLocalPack.dir.isDirectory) {
                                        onTargetUnavailable()
                                    } else scope.launch {
                                        val resolved = catalog.resolveDownload(file).getOrElse { cause ->
                                            lgr.warn(cause) { "解析模组下载信息失败" }
                                            snackbar.showSnackbar(
                                                "无法获取下载信息，请稍后重试",
                                                duration = SnackbarDuration.Short
                                            )
                                            null
                                        }
                                        if (resolved != null) downloadSelection = DownloadSelection(file, resolved)
                                    }
                                }
                            )
                        }
                        if (!filesLoading && files.isEmpty()) item { Text("没有兼容版本") }
                        if (fileCursor != null) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                if (loadingMore) CircularProgressIndicator() else TextButton(
                                    onClick = { scope.launch { loadFiles(reset = false) } }
                                ) { Text("加载更多") }
                            }
                        }
                    }
                }

                selectedTab == 1 -> when {
                    detailsLoading -> CircularProgressIndicator()
                    details != null -> LazyColumn(Modifier.fillMaxSize()) {
                        item { Text(details!!.description.toReadableDescription()) }
                    }
                    else -> Text("暂无介绍")
                }

                mcmodUrl != null -> WebPagePane(mcmodUrl, title, Modifier.fillMaxSize())
            }
        }
        BottomSnakebarM3(snackbar)
    }

    downloadSelection?.let { selection ->
        CatalogDownloadDialog(
            catalog = catalog,
            mod = mod,
            file = selection.file,
            resolved = selection.resolved,
            targetHostId = targetHostId,
            targetHost2Id = targetHost2Id,
            targetHostMcVer = targetHostMcVer,
            targetLocalPack = targetLocalPack,
            onDismiss = { downloadSelection = null },
            onSubmitted = { message, runId ->
                downloadSelection = null
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = message,
                        actionLabel = runId?.let { "查看任务" },
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && runId != null) onOpenTaskList?.invoke(runId)
                }
            }
        )
    }
}

private fun List<calebxzhou.rdi.common.model.Task2Entry>.localModTaskStatus(
    packdir: ModpackLocalDir,
    file: CatalogFile
): String? {
    val key = "catalog-mod-local:${packdir.versionId}:${file.project.projectId}"
    val entry = lastOrNull { it.dedupeKey == key } ?: return null
    return when (entry.status) {
        Task2Status.QUEUED, Task2Status.RUNNING -> "下载中"
        Task2Status.FAILED, Task2Status.CANCELLED -> "安装失败"
        Task2Status.DONE -> null
    }
}

@Composable
private fun CatalogFileRow(
    file: CatalogFile,
    installStatus: String?,
    downloadEnabled: Boolean,
    expanded: Boolean,
    expansionLoading: Boolean,
    changelog: String,
    dependencySummary: String?,
    dependencyMods: List<CatalogMod>,
    onToggle: () -> Unit,
    onOpenDependency: (CatalogMod) -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(file.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${file.channel.displayName} · ${file.fileSize.humanFileSize} · ${file.publishedAt.displayDate()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    installStatus?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
                CircleIconButton(
                    icon = if (expanded) "\uF077" else "\uF078",
                    tooltip = if (expanded) "收起详情" else "更新说明与依赖",
                    showText = false,
                    bgColor = MaterialTheme.colorScheme.secondary
                ) { onToggle() }
                CircleIconButton(
                    "\uF019",
                    when (installStatus) {
                        "可更新" -> "更新"
                        "文件缺失", "文件已被修改", "安装失败" -> "重新安装"
                        else -> "下载"
                    },
                    showText = false,
                    enabled = downloadEnabled,
                    bgColor = MaterialColor.GREEN_700.color
                ) {
                    onDownload()
                }
            }
            if (expanded) {
                HorizontalDivider()
                if (expansionLoading) CircularProgressIndicator()
                dependencySummary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                dependencyMods.forEach { dependency ->
                    CatalogModCard(dependency, compact = true, onClick = { onOpenDependency(dependency) })
                }
                if (changelog.isNotBlank()) {
                    Text("更新说明", fontWeight = FontWeight.Bold)
                    Text(changelog.toReadableDescription())
                }
                if (!expansionLoading && changelog.isBlank() && dependencyMods.isEmpty()) Text("没有更多说明")
            }
        }
    }
}

private fun List<LocalContentInstallRecord>.modStatus(
    file: CatalogFile,
    health: Map<String, String>
): String? {
    val source = when (file.ref.platform) {
        ModPlatform.CURSEFORGE -> "cf"
        ModPlatform.MODRINTH -> "mr"
    }
    val record = firstOrNull {
        it.type == LocalContentType.MOD && it.source == source && it.projectId == file.project.projectId
    } ?: return "未安装"
    return when {
        health[record.projectKey] != null -> health.getValue(record.projectKey)
        record.versionId == file.ref.fileId -> "已安装"
        else -> "可更新"
    }
}

private fun List<LocalContentInstallRecord>.hasManagedMod(file: CatalogFile): Boolean {
    val source = if (file.ref.platform == ModPlatform.CURSEFORGE) "cf" else "mr"
    return any {
        it.type == LocalContentType.MOD && it.source == source && it.projectId == file.project.projectId
    }
}

private data class DownloadSelection(val file: CatalogFile, val resolved: ResolvedDownload)

private sealed interface CatalogInstallTarget {
    data class Local(val pack: ModpackLocalDir) : CatalogInstallTarget
    data class HostTarget(val id: ObjectId, val name: String, val mcVersion: McVersion?) : CatalogInstallTarget
    data class Host2Target(val id: String, val mcVersion: McVersion?) : CatalogInstallTarget
}

private data class CatalogHost(val brief: Host.BriefVo, val detail: Host.DetailVo)

@Composable
private fun CatalogDownloadDialog(
    catalog: ModCatalog,
    mod: CatalogMod,
    file: CatalogFile,
    resolved: ResolvedDownload,
    targetHostId: ObjectId?,
    targetHost2Id: String?,
    targetHostMcVer: McVersion?,
    targetLocalPack: ModpackLocalDir?,
    onDismiss: () -> Unit,
    onSubmitted: (String, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var localPacks by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var hosts by remember { mutableStateOf<List<CatalogHost>>(emptyList()) }
    var target by remember { mutableStateOf<CatalogInstallTarget?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localInstallMods by remember(file.ref, targetLocalPack?.versionId) { mutableStateOf<List<Mod>>(emptyList()) }
    var dependenciesLoading by remember(file.ref, targetLocalPack?.versionId) { mutableStateOf(targetLocalPack != null) }
    var updateCount by remember(file.ref, targetLocalPack?.versionId) { mutableStateOf(0) }

    LaunchedEffect(file.ref, targetHostId, targetHost2Id, targetLocalPack?.versionId) {
        loading = true
        try {
            if (targetLocalPack != null) {
                target = CatalogInstallTarget.Local(targetLocalPack)
            } else if (targetHost2Id != null) {
                target = CatalogInstallTarget.Host2Target(targetHost2Id, targetHostMcVer)
            } else if (targetHostId != null) {
                target = CatalogInstallTarget.HostTarget(targetHostId, "当前房间", targetHostMcVer)
            } else {
                localPacks = ModpackService.getLocalPackDirs().filter { pack ->
                    file.minecraftVersions.isEmpty() || pack.vo.mcVer.mcVer in file.minecraftVersions
                }
                hosts = loadCatalogAdminHosts().filter { host ->
                    file.minecraftVersions.isEmpty() || host.detail.modpack.mcVer.mcVer in file.minecraftVersions
                }
                target = localPacks.firstOrNull()?.let { CatalogInstallTarget.Local(it) }
                    ?: hosts.firstOrNull()?.let {
                        CatalogInstallTarget.HostTarget(it.brief._id, it.brief.name, it.detail.modpack.mcVer)
                    }
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            lgr.warn(cause) { "加载模组安装目标失败" }
            errorMessage = "加载安装目标失败，请稍后重试"
        }
        loading = false
    }

    LaunchedEffect(file.ref, targetLocalPack?.versionId) {
        val pack = targetLocalPack ?: return@LaunchedEffect
        dependenciesLoading = true
        resolveRequiredLocalMods(catalog, file, resolved, pack)
            .mapCatching { mods ->
                val records = withContext(Dispatchers.IO) {
                    LocalContentInstallStore.read(pack).getOrThrow()
                }
                val requiresUpdate = mods.any { dependency ->
                    records.firstOrNull {
                        it.type == LocalContentType.MOD && it.source == dependency.platform &&
                                it.projectId == dependency.projectId
                    }?.versionId?.let { it != dependency.fileId } == true
                }
                if (requiresUpdate && McPlayStore.aliveCount(pack.versionId) > 0) {
                    error("必需依赖需要更新，请先关闭当前游戏")
                }
                updateCount = mods.count { dependency ->
                    records.firstOrNull {
                        it.type == LocalContentType.MOD && it.source == dependency.platform &&
                                it.projectId == dependency.projectId
                    }?.versionId?.let { it != dependency.fileId } == true
                }
                mods
            }
            .onSuccess { localInstallMods = it }
            .onFailure { errorMessage = it.message ?: "解析必需依赖失败" }
        dependenciesLoading = false
    }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("安装${resolved.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) CircularProgressIndicator()
                if (targetHostId == null && targetHost2Id == null && targetLocalPack == null) {
                    Text("本地整合包", fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(localPacks, key = { "local:${it.versionId}" }) { pack ->
                            TextButton(
                                onClick = { target = CatalogInstallTarget.Local(pack) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if ((target as? CatalogInstallTarget.Local)?.pack?.versionId == pack.versionId) {
                                        "✓ ${pack.vo.name}"
                                    } else pack.vo.name
                                )
                            }
                        }
                    }
                    Text("房间", fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(hosts, key = { "host:${it.brief._id}" }) { host ->
                            TextButton(
                                onClick = {
                                    target = CatalogInstallTarget.HostTarget(
                                        host.brief._id,
                                        host.brief.name,
                                        host.detail.modpack.mcVer
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if ((target as? CatalogInstallTarget.HostTarget)?.id == host.brief._id) {
                                        "✓ ${host.brief.name}"
                                    } else host.brief.name
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        targetLocalPack?.let { "安装到：${it.vo.name} ${it.verName}" }
                            ?: "将添加到当前房间"
                    )
                    if (targetLocalPack != null && !dependenciesLoading && localInstallMods.isNotEmpty()) {
                        Text("将安装当前Mod及${localInstallMods.size - 1}个必需依赖")
                        if (updateCount > 0) Text("其中${updateCount}个已安装Mod将被替换")
                    }
                }
                if (!loading && target == null) Text("没有兼容的安装目标")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
                    TextButton(
                        enabled = target != null && !submitting && !dependenciesLoading &&
                                (targetLocalPack == null || localInstallMods.isNotEmpty()),
                        onClick = {
                            val selected = target ?: return@TextButton
                            submitting = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val legacyMod = mod.toLegacyMod(file, resolved)
                                    when (selected) {
                                        is CatalogInstallTarget.Local -> {
                                            val runId = ClientTaskManager.submit(
                                                task = RemoteModDownloadService.downloadToLocalModpackTask2(
                                                    localInstallMods.ifEmpty { listOf(legacyMod) }, selected.pack
                                                ),
                                                dedupeKey = "catalog-mod-local:${selected.pack.versionId}:${file.project.projectId}"
                                            )
                                            onSubmitted("已加入任务列表", runId)
                                        }

                                        is CatalogInstallTarget.HostTarget -> {
                                            if (selected.mcVersion != null &&
                                                file.minecraftVersions.isNotEmpty() &&
                                                selected.mcVersion.mcVer !in file.minecraftVersions
                                            ) {
                                                error("目标MC版本与当前模组版本不匹配")
                                            }
                                            val response = server.makeRequest<Unit>(
                                                "host/${selected.id}/mods/extra",
                                                HttpMethod.Post
                                            ) {
                                                contentType(ContentType.Application.Json)
                                                setBody(serdesJson.encodeToString(listOf(legacyMod)))
                                            }
                                            if (!response.ok) error(response.msg)
                                            onSubmitted("已提交房间附加Mod任务", null)
                                        }

                                        is CatalogInstallTarget.Host2Target -> {
                                            if (selected.mcVersion != null &&
                                                file.minecraftVersions.isNotEmpty() &&
                                                selected.mcVersion.mcVer !in file.minecraftVersions
                                            ) {
                                                error("目标MC版本与当前Mod版本不匹配")
                                            }
                                            val response = server.makeRequest<Unit>(
                                                "host2/${selected.id}/mods",
                                                HttpMethod.Post
                                            ) {
                                                contentType(ContentType.Application.Json)
                                                setBody(serdesJson.encodeToString(listOf(legacyMod)))
                                            }
                                            if (!response.ok) error(response.msg)
                                            onSubmitted("已添加到新版房间", null)
                                        }
                                    }
                                } catch (cause: CancellationException) {
                                    throw cause
                                } catch (cause: Exception) {
                                    lgr.warn(cause) { "提交模组安装任务失败" }
                                    errorMessage = "提交安装任务失败，请稍后重试"
                                    submitting = false
                                }
                            }
                        }
                    ) { Text(if (submitting) "提交中..." else "开始安装") }
                }
            }
        }
    }
}

private suspend fun resolveRequiredLocalMods(
    catalog: ModCatalog,
    rootFile: CatalogFile,
    rootDownload: ResolvedDownload,
    packdir: ModpackLocalDir
): Result<List<Mod>> = runCatching {
    val target = CatalogTarget(packdir.vo.mcVer, packdir.vo.modloader)
    val graph = catalog.resolveDependencies(DependencyRequest(listOf(rootFile), target)).getOrThrow().value
    val requiredRefs = linkedSetOf(rootFile.ref)
    var changed: Boolean
    do {
        changed = false
        graph.edges.filter {
            it.requirement == DependencyRequirement.REQUIRED && it.from in requiredRefs
        }.forEach { edge ->
            val dependencyRef = when (val dependency = edge.to) {
                is DependencyTarget.File -> dependency.ref
                is DependencyTarget.Project -> graph.nodes.values
                    .firstOrNull { it.file.project == dependency.ref }
                    ?.file?.ref
            }
            if (dependencyRef != null && requiredRefs.add(dependencyRef)) changed = true
        }
    } while (changed)

    val unresolvedRequired = graph.unresolved.filter { unresolved ->
        graph.edges.any {
            it.from in requiredRefs && it.to == unresolved.target &&
                    it.requirement == DependencyRequirement.REQUIRED
        }
    }
    if (unresolvedRequired.isNotEmpty()) {
        error("有${unresolvedRequired.size}个必需依赖没有兼容版本")
    }

    val files = requiredRefs.map { ref -> graph.nodes.getValue(ref).file }
    val mods = catalog.getMods(files.mapTo(linkedSetOf(), CatalogFile::project)).getOrThrow().value
    files.map { dependencyFile ->
        val catalogMod = mods.getValue(dependencyFile.project)
        val download = if (dependencyFile.ref == rootFile.ref) rootDownload
        else catalog.resolveDownload(dependencyFile).getOrThrow()
        catalogMod.toLegacyMod(dependencyFile, download)
    }.sortedBy { if (it.projectId == rootFile.project.projectId) 0 else 1 }
}

private suspend fun loadCatalogAdminHosts(): List<CatalogHost> {
    val hosts = mutableListOf<CatalogHost>()
    var page = 0
    while (true) {
        val response = server.makeRequest<List<Host.BriefVo>>("host/my/$page")
        if (!response.ok) error(response.msg)
        val briefs = response.data.orEmpty()
        if (briefs.isEmpty()) break
        briefs.forEach { brief ->
            val detailResponse = server.makeRequest<Host.DetailVo>("host/${brief._id}/detail")
            if (!detailResponse.ok) error(detailResponse.msg)
            val detail = detailResponse.data ?: return@forEach
            if (detail.ownerId == loggedAccount._id || detail.members.any {
                    it.id == loggedAccount._id && it.role.level <= Role.ADMIN.level
                }
            ) {
                hosts += CatalogHost(brief, detail)
            }
        }
        page++
    }
    return hosts
}

private fun CatalogMod.toLegacyMod(file: CatalogFile, resolved: ResolvedDownload): Mod {
    val source = sources.firstOrNull { it.ref == file.project } ?: sources.first()
    val platform = when (file.ref.platform) {
        ModPlatform.CURSEFORGE -> "cf"
        ModPlatform.MODRINTH -> "mr"
    }
    val digest = when (file.ref.platform) {
        ModPlatform.CURSEFORGE -> resolved.digests.firstOrNull {
            it.algorithm == CatalogDigestAlgorithm.CURSEFORGE_MURMUR2
        }
        ModPlatform.MODRINTH -> resolved.digests.firstOrNull {
            it.algorithm == CatalogDigestAlgorithm.SHA1
        }
    } ?: error("当前文件缺少校验信息")
    return Mod(
        platform = platform,
        projectId = file.project.projectId,
        slug = source.slug,
        fileId = file.ref.fileId,
        hash = digest.value,
        side = environment.toLegacySide(),
        downloadUrls = listOf(resolved.url)
    )
}

private fun calebxzhou.rdi.client.modcatalog.EnvironmentCompatibility.toLegacySide(): Mod.Side = when {
    client == EnvironmentRequirement.UNSUPPORTED && server != EnvironmentRequirement.UNSUPPORTED -> Mod.Side.SERVER
    server == EnvironmentRequirement.UNSUPPORTED && client != EnvironmentRequirement.UNSUPPORTED -> Mod.Side.CLIENT
    client == EnvironmentRequirement.UNKNOWN && server == EnvironmentRequirement.UNKNOWN -> Mod.Side.UNKNOWN
    else -> Mod.Side.BOTH
}

private val ReleaseChannel.displayName: String
    get() = when (this) {
        ReleaseChannel.RELEASE -> "Release"
        ReleaseChannel.BETA -> "Beta"
        ReleaseChannel.ALPHA -> "Alpha"
    }

private val ModLoader.displayName: String
    get() = when (this) {
        ModLoader.forge -> "Forge"
        ModLoader.neoforge -> "NeoForge"
        ModLoader.cleanroom -> "Cleanroom"
    }

private fun java.time.Instant.displayDate(): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(atZone(ZoneId.systemDefault()))

private fun String.toReadableDescription(): String = replace(Regex("<iframe[\\s\\S]*?</iframe>", RegexOption.IGNORE_CASE), "")
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()
