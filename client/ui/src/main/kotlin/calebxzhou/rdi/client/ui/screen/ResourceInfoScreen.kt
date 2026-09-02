package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import calebxzhou.rdi.client.model.ModrinthProjectGalleryVo
import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.LocalContentInstallRecord
import calebxzhou.rdi.client.service.LocalContentInstallStore
import calebxzhou.rdi.client.service.LocalContentType
import calebxzhou.rdi.client.service.ModrinthProjectDownloadService
import calebxzhou.rdi.client.service.ModrinthProjectInfoService
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.MainColumn
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.HttpImage
import calebxzau.rdi.client.ui.copyToClipboard
import calebxzau.rdi.client.ui.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun ResourceInfoScreen(
    route: ResourceInfoRoute,
    modCatalog: ModCatalog,
    onOpenTaskList: ((String) -> Unit)? = null,
    onTargetUnavailable: () -> Unit = {},
    onBack: () -> Unit
) {
    val disabledTarget = route.hasDisabledCatalogLocalTargetKind()
    val localTarget = route.localCatalogTarget()
    var targetPack by remember(localTarget) { mutableStateOf<ModpackLocalDir?>(null) }
    var resolvingTarget by remember(localTarget) {
        mutableStateOf(localTarget != null)
    }

    LaunchedEffect(disabledTarget, localTarget) {
        if (disabledTarget) {
            resolvingTarget = false
            onTargetUnavailable()
            return@LaunchedEffect
        }
        if (localTarget == null) {
            resolvingTarget = false
            return@LaunchedEffect
        }
        when (localTarget.kind) {
            CatalogLocalTargetKind.Legacy -> targetPack = ModpackService.getLocalPackDirs().firstOrNull { it.versionId == localTarget.id }
        }
        resolvingTarget = false
        if (targetPack == null) onTargetUnavailable()
    }

    if (disabledTarget) return

    val type = ResourceInfoType.valueOf(route.type)
    val projectDisplayName = when (type) {
        ResourceInfoType.ResourcePack -> "资源包"
        ResourceInfoType.Shader -> "光影包"
    }
    val targetDirName = when (type) {
        ResourceInfoType.ResourcePack -> "resourcepacks"
        ResourceInfoType.Shader -> "shaderpacks"
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            if (resolvingTarget) {
                MainColumn { CircularProgressIndicator() }
            } else if (localTarget == null || targetPack != null) {
                ModrinthProjectInfoContent(
                    modCatalog = modCatalog,
                    projectId = route.projectId,
                    projectDisplayName = projectDisplayName,
                    targetDirName = targetDirName,
                    targetPack = targetPack,
                    onOpenTaskList = onOpenTaskList,
                    onTargetUnavailable = onTargetUnavailable,
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun ModrinthProjectInfoContent(
    modCatalog: ModCatalog,
    projectId: String,
    projectDisplayName: String,
    targetDirName: String,
    targetPack: ModpackLocalDir? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    onTargetUnavailable: () -> Unit = {},
    onBack: () -> Unit
) {
    var project by remember { mutableStateOf<ModrinthProjectInfoVo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var taskRunId by remember { mutableStateOf<String?>(null) }
    var previewGallery by remember { mutableStateOf<ModrinthProjectGalleryVo?>(null) }
    var includeAlpha by rememberSaveable(projectId) { mutableStateOf(false) }
    var installRecord by remember(projectId, targetPack?.versionId) {
        mutableStateOf<LocalContentInstallRecord?>(null)
    }
    val taskEntries by ClientTaskManager.entries.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId, projectDisplayName) {
        loading = true
        errorMessage = null
        runCatching {
            ModrinthProjectInfoService.loadProjectInfo(modCatalog, projectId)
        }.onSuccess {
            project = it
        }.onFailure {
            errorMessage = "加载${projectDisplayName}详情失败: ${it.message ?: it}"
        }
        loading = false
    }
    LaunchedEffect(projectId, targetPack?.versionId, taskEntries.map { it.runId to it.status }) {
        val pack = targetPack ?: return@LaunchedEffect
        val type = if (targetDirName == "resourcepacks") {
            LocalContentType.RESOURCE_PACK
        } else {
            LocalContentType.SHADER_PACK
        }
        val recordResult = withContext(Dispatchers.IO) {
            LocalContentInstallStore.find(pack, type, "modrinth", projectId)
        }
        recordResult.onSuccess { installRecord = it }
            .onFailure { errorMessage = "读取安装状态失败: ${it.message ?: it}" }
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            val result = snackbarHostState.showSnackbar(
                message = it,
                actionLabel = taskRunId?.let { "查看任务" },
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) taskRunId?.let { runId -> onOpenTaskList?.invoke(runId) }
            okMessage = null
            taskRunId = null
        }
    }

    val title = project?.title ?: projectDisplayName

    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow(title, onBack) {
                OutlinedButton(
                    enabled = project?.sourceUrl != null,
                    onClick = { project?.sourceUrl?.let(::openUrl) }
                ) {
                    Text("打开原帖")
                }
            }
            Space8h()

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Space8h()
            }

            project?.let { loadedProject ->
                ShaderDownloadVersionRow(
                    project = loadedProject,
                    requiredMcVersion = targetPack?.mcVersion?.mcVer,
                    includeAlpha = includeAlpha,
                    targetPack = targetPack,
                    targetAvailable = targetPack != null,
                    installRecord = installRecord,
                    taskEntries = taskEntries,
                    projectId = projectId,
                    targetDirName = targetDirName,
                    onToggleAlpha = { includeAlpha = !includeAlpha },
                    onDownload = { version ->
                        val pack = targetPack
                        val file = version.primaryFile
                        when {
                            pack == null -> errorMessage = "请先从已安装整合包中选择目标"
                            pack != null && !pack.dir.isDirectory -> onTargetUnavailable()
                            file == null -> errorMessage = "该版本没有可安装文件"
                            else -> {
                                val legacyPack = requireNotNull(pack)
                                taskRunId = ClientTaskManager.submit(
                                    task = ModrinthProjectDownloadService.downloadProjectFileTask2(
                                        file = file,
                                        packdir = legacyPack,
                                        projectId = projectId,
                                        versionId = version.id,
                                        projectDisplayName = projectDisplayName,
                                        targetDirName = targetDirName
                                    ),
                                    dedupeKey = "modrinth-project-file:$targetDirName:${legacyPack.versionId}:$projectId"
                                )
                                okMessage = "已加入任务列表"
                            }
                        }
                    }
                )
                Space8h()
                Text("相册", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Space8h()
                ShaderGalleryGrid(
                    project = loadedProject,
                    projectDisplayName = projectDisplayName,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onPreview = { previewGallery = it }
                )
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }

    previewGallery?.let { gallery ->
        ShaderImagePreviewDialog(
            gallery = gallery,
            projectDisplayName = projectDisplayName,
            onDismiss = { previewGallery = null }
        )
    }
}

@Composable
private fun ShaderDownloadVersionRow(
    project: ModrinthProjectInfoVo,
    requiredMcVersion: String? = null,
    includeAlpha: Boolean,
    targetPack: ModpackLocalDir?,
    targetAvailable: Boolean,
    installRecord: LocalContentInstallRecord?,
    taskEntries: List<Task2Entry>,
    projectId: String,
    targetDirName: String,
    onToggleAlpha: () -> Unit,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    val compatibleVersions = project.versions.filter { version ->
        (requiredMcVersion == null || requiredMcVersion in version.gameVersions) &&
                (includeAlpha || !version.versionType.equals("alpha", ignoreCase = true))
    }.sortedBy { if (it.versionType.equals("beta", ignoreCase = true)) 0 else 1 }
    val taskStatus = targetPack?.let { taskEntries.localContentTaskStatus(it, targetDirName, projectId) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("点击要安装的版本", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onToggleAlpha) {
                Text(if (includeAlpha) "隐藏Alpha" else "显示Alpha")
            }
        }
        if (compatibleVersions.isEmpty()) {
            Text("没有兼容版本", color = themeNow.onSurfaceVariant)
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                compatibleVersions.forEach { version ->
                    Card(
                        onClick = { onDownload(version) },
                        enabled = targetAvailable && (targetPack == null || installRecord == null) &&
                                taskStatus != "下载中" && version.primaryFile != null,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = version.versionNumber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun List<Task2Entry>.localContentTaskStatus(
    packdir: ModpackLocalDir,
    targetDirName: String,
    projectId: String
): String? {
    val key = "modrinth-project-file:$targetDirName:${packdir.versionId}:$projectId"
    val entry = lastOrNull { it.dedupeKey == key } ?: return null
    return when (entry.status) {
        Task2Status.QUEUED, Task2Status.RUNNING -> "下载中"
        Task2Status.FAILED, Task2Status.CANCELLED -> "安装失败"
        Task2Status.DONE -> null
    }
}


@Composable
private fun ShaderGalleryGrid(
    project: ModrinthProjectInfoVo,
    projectDisplayName: String,
    modifier: Modifier = Modifier,
    onPreview: (ModrinthProjectGalleryVo) -> Unit
) {
    if (project.gallery.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无相册图片", color = themeNow.onSurfaceVariant)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(project.gallery, key = { it.rawUrl ?: it.url }) { gallery ->
                ShaderGalleryCard(
                    gallery = gallery,
                    projectDisplayName = projectDisplayName,
                    onClick = { onPreview(gallery) }
                )
            }
        }
        RVerticalScrollbar(
            gridState = gridState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ShaderGalleryCard(
    gallery: ModrinthProjectGalleryVo,
    projectDisplayName: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                HttpImage(
                    imgUrl = gallery.url,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = gallery.name ?: "${projectDisplayName}相册图片",
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}


@Composable
private fun ShaderImagePreviewDialog(
    gallery: ModrinthProjectGalleryVo,
    projectDisplayName: String,
    onDismiss: () -> Unit
) {
    val imageUrl = gallery.rawUrl ?: gallery.url
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1100.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    gallery.name?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CircleIconButton(
                        icon = "\uE8C8",
                        tooltip = "复制链接",
                        showText = false,
                        bgColor = themeNow.surfaceVariant,
                        iconColor = themeNow.onSurfaceVariant
                    ) {
                        copyToClipboard(imageUrl)
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uE89E",
                        tooltip = "浏览器打开",
                        showText = false,
                        bgColor = MaterialTheme.colorScheme.primary
                    ) {
                        openUrl(imageUrl)
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uE5CD",
                        tooltip = "关闭",
                        showText = false,
                        bgColor = MaterialTheme.colorScheme.error
                    ) {
                        onDismiss()
                    }
                }
                HttpImage(
                    imgUrl = imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .aspectRatio(16f / 9f),
                    contentDescription = gallery.name ?: "${projectDisplayName}相册大图",
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
