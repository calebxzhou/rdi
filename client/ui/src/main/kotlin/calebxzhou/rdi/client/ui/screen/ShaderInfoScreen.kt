package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzhou.rdi.client.model.ModrinthProjectGalleryVo
import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.ModrinthProjectDownloadService
import calebxzhou.rdi.client.service.ModrinthProjectInfoService
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.HttpImage
import calebxzhou.rdi.client.ui.comp.LoadingFlowGrid
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzau.rdi.client.ui.copyToClipboard
import calebxzau.rdi.client.ui.openUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShaderInfoScreen(
    projectId: String,
    initialTitle: String,
    initialDownloadsText: String? = null,
    initialFollowsText: String? = null,
    onBack: () -> Unit
) = ModrinthProjectInfoScreen(
    projectId = projectId,
    initialTitle = initialTitle,
    initialDownloadsText = initialDownloadsText,
    initialFollowsText = initialFollowsText,
    projectDisplayName = "光影包",
    targetDirName = "shaderpacks",
    onBack = onBack
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResourcepackInfoScreen(
    projectId: String,
    initialTitle: String,
    initialDownloadsText: String? = null,
    initialFollowsText: String? = null,
    onBack: () -> Unit
) = ModrinthProjectInfoScreen(
    projectId = projectId,
    initialTitle = initialTitle,
    initialDownloadsText = initialDownloadsText,
    initialFollowsText = initialFollowsText,
    projectDisplayName = "资源包",
    targetDirName = "resourcepacks",
    onBack = onBack
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModrinthProjectInfoScreen(
    projectId: String,
    initialTitle: String,
    initialDownloadsText: String? = null,
    initialFollowsText: String? = null,
    projectDisplayName: String,
    targetDirName: String,
    onBack: () -> Unit
) {
    var project by remember { mutableStateOf<ModrinthProjectInfoVo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var previewGallery by remember { mutableStateOf<ModrinthProjectGalleryVo?>(null) }
    var downloadVersion by remember { mutableStateOf<ModrinthProjectVersionVo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId, projectDisplayName) {
        loading = true
        errorMessage = null
        runCatching {
            ModrinthProjectInfoService.loadProjectInfo(projectId)
        }.onSuccess {
            project = it
        }.onFailure {
            errorMessage = "加载${projectDisplayName}详情失败: ${it.message ?: it}"
        }
        loading = false
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val title = project?.title ?: initialTitle
    val downloadsText = project?.downloadsText ?: initialDownloadsText
    val followsText = project?.followsText ?: initialFollowsText
    val tabTitles = buildList {
        add("描述")
        add("相册")
        add("日志")
        add("下载")
    }
    val activeTab = selectedTab.takeIf { it in tabTitles.indices } ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow(title, onBack) {
                downloadsText?.let {
                    Text("\uF019 $it".asIconText, color = MaterialColor.GRAY_900.color)
                    Space8w()
                }
                followsText?.let {
                    Text("\uDB80\uDED1 $it".asIconText, color = MaterialColor.GRAY_900.color)
                }
            }
            Space8h()
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, tabTitle ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tabTitle) }
                    )
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

            when (activeTab) {
                0 -> ShaderDescriptionTab(project)
                1 -> ShaderGalleryTab(project, projectDisplayName) { previewGallery = it }
                2 -> ShaderChangelogTab(project)
                3 -> ShaderDownloadTab(project, projectDisplayName) { downloadVersion = it }
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
    downloadVersion?.let { version ->
        ShaderDownloadPackDialog(
            version = version,
            projectDisplayName = projectDisplayName,
            targetDirName = targetDirName,
            onTaskAdded = { okMessage = "已加入任务列表" },
            onDismiss = { downloadVersion = null }
        )
    }
}

@Composable
private fun ShaderChangelogTab(project: ModrinthProjectInfoVo?) {
    if (project == null) {
        Text("正在载入日志...", color = MaterialColor.GRAY_700.color)
        return
    }
    if (project.versions.isEmpty()) {
        ShaderPlaceholderTab("暂无版本日志")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        lazyItems(project.versions, key = { it.id }) { version ->
            ShaderChangelogCard(version)
        }
    }
}

@Composable
private fun ShaderChangelogCard(version: ModrinthProjectVersionVo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShaderVersionHeader(version)
            Text(
                text = version.changelog.toReadableModrinthDescription(),
                color = MaterialColor.GRAY_900.color,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ShaderDownloadTab(
    project: ModrinthProjectInfoVo?,
    projectDisplayName: String,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    if (project == null) {
        Text("正在载入下载版本...", color = MaterialColor.GRAY_700.color)
        return
    }
    if (project.versions.isEmpty()) {
        ShaderPlaceholderTab("暂无可下载版本")
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .width(1180.dp)
        ) {
            item("header") {
                ShaderDownloadTableHeader()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            lazyItems(project.versions, key = { it.id }) { version ->
                ShaderDownloadTableRow(version, projectDisplayName, onDownload)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ShaderDownloadTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShaderTableHeaderText("Name", Modifier.weight(1.6f))
        ShaderTableHeaderText("Game version", Modifier.weight(2.4f))
        ShaderTableHeaderText("Platforms", Modifier.weight(1.8f))
        ShaderTableHeaderText("Published", Modifier.weight(1.3f))
        ShaderTableHeaderText("Downloads", Modifier.weight(1f))
        Spacer(modifier = Modifier.width(56.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShaderDownloadTableRow(
    version: ModrinthProjectVersionVo,
    projectDisplayName: String,
    onDownload: (ModrinthProjectVersionVo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.6f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShaderReleaseBadge(version.versionType)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = version.versionNumber,
                    color = MaterialColor.GRAY_900.color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = version.name,
                    color = MaterialColor.GRAY_900.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        FlowRow(
            modifier = Modifier.weight(2.4f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            version.gameVersions.toDisplayVersionChips().forEach { ShaderInfoChip(it) }
        }
        FlowRow(
            modifier = Modifier.weight(1.8f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            version.loaders.forEach { ShaderInfoChip(it.toPlatformLabel()) }
        }
        Text(
            text = version.publishedText,
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = version.downloadsText,
            color = MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        CircleIconButton(
            icon = "\uF019",
            tooltip = "下载${projectDisplayName}",
            bgColor = MaterialColor.GREEN_700.color,
            showText = false,
            enabled = version.primaryFile != null
        ) {
            onDownload(version)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShaderDownloadPackDialog(
    version: ModrinthProjectVersionVo,
    projectDisplayName: String,
    targetDirName: String,
    onTaskAdded: () -> Unit,
    onDismiss: () -> Unit
) {
    val file = version.primaryFile
    var packdirs by remember(version.id) { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember(version.id) { mutableStateOf<ModpackLocalDir?>(null) }
    var loading by remember(version.id) { mutableStateOf(true) }
    var errorMessage by remember(version.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(version.id) {
        loading = true
        errorMessage = null
        runCatching {
            ModpackService.getLocalPackDirs()
        }.onSuccess {
            packdirs = it
            selectedPack = it.firstOrNull()
        }.onFailure {
            errorMessage = "读取已安装整合包失败: ${it.message ?: it}"
        }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 980.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "下载${projectDisplayName}${file?.filename}，要装到哪个包里？",
                    color = MaterialColor.GRAY_900.color,
                    style = MaterialTheme.typography.titleMedium
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LoadingFlowGrid(
                    loading = loading,
                    items = packdirs,
                    emptyText = "没有已安装整合包",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) { packdir ->
                    ModpackManageCard(
                        modifier = Modifier.widthIn(max = 350.dp),
                        packdir = packdir,
                        selected = selectedPack?.versionId == packdir.versionId,
                        miniMode = true,
                        onClick = { selectedPack = packdir }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(
                        enabled = file != null && selectedPack != null,
                        onClick = {
                            val targetFile = file ?: return@TextButton
                            val targetPack = selectedPack ?: return@TextButton
                            ClientTaskManager.submit(
                                task = ModrinthProjectDownloadService.downloadProjectFileTask2(
                                    file = targetFile,
                                    packdir = targetPack,
                                    projectDisplayName = projectDisplayName,
                                    targetDirName = targetDirName
                                ),
                                dedupeKey = "modrinth-project-file:${targetDirName}:${targetPack.versionId}:${targetFile.sha1 ?: targetFile.url}"
                            )
                            onTaskAdded()
                            onDismiss()
                        }
                    ) {
                        Text("加入下载任务")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShaderTableHeaderText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = MaterialColor.GRAY_900.color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}

@Composable
private fun ShaderReleaseBadge(versionType: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialColor.GREEN_50.color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = versionType?.firstOrNull()?.uppercaseChar()?.toString() ?: "R",
            color = MaterialColor.GREEN_700.color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ShaderInfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            color = MaterialColor.GRAY_800.color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun List<String>.toDisplayVersionChips(): List<String> {
    if (isEmpty()) return listOf("未知版本")
    val majorGroups = map { it.toMcVersionChipText() }.distinct().asReversed()
    return if (majorGroups.size <= 6) {
        majorGroups
    } else {
        majorGroups.take(6) + "+${majorGroups.size - 6}"
    }
}

private fun String.toMcVersionChipText(): String {
    val parts = split('.')
    return if (parts.size >= 2 && parts[0].all(Char::isDigit) && parts[1].all(Char::isDigit)) {
        "${parts[0]}.${parts[1]}.x"
    } else {
        this
    }
}

private fun String.toPlatformLabel(): String =
    when (lowercase()) {
        "iris" -> "\u2699 Iris"
        "optifine" -> "OF OptiFine"
        else -> replaceFirstChar { it.uppercase() }
    }

@Composable
private fun ShaderVersionHeader(version: ModrinthProjectVersionVo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = version.versionNumber,
                color = MaterialColor.GRAY_900.color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = version.versionType ?: "unknown",
                color = MaterialColor.BLUE_700.color,
                maxLines = 1
            )
            Text("\uF019 ${version.downloadsText}".asIconText, color = MaterialColor.GRAY_800.color)
        }
        Text(
            text = "${version.publishedText} · ${version.loaders.joinToString(" / ")} · ${version.gameVersions.toVersionRangeText()}",
            color = MaterialColor.GRAY_700.color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShaderDescriptionTab(project: ModrinthProjectInfoVo?) {
    if (project == null) {
        Text("正在载入描述...", color = MaterialColor.GRAY_700.color)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = project.summary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialColor.GRAY_900.color
                )
                Text(
                    text = project.description.toReadableModrinthDescription(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialColor.GRAY_900.color
                )
            }
        }
    }
}

@Composable
private fun ShaderPlaceholderTab(text: String) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = MaterialColor.GRAY_700.color)
    }
}

@Composable
private fun ShaderGalleryTab(
    project: ModrinthProjectInfoVo?,
    projectDisplayName: String,
    onPreview: (ModrinthProjectGalleryVo) -> Unit
) {
    if (project == null) {
        Text("正在载入相册...", color = MaterialColor.GRAY_700.color)
        return
    }
    if (project.gallery.isEmpty()) {
        ShaderPlaceholderTab("暂无相册图片")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = Modifier.fillMaxSize(),
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

@OptIn(ExperimentalMaterial3Api::class)
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
                            color = MaterialColor.GRAY_900.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CircleIconButton(
                        icon = "\uE8C8",
                        tooltip = "复制链接",
                        showText = false,
                        bgColor = MaterialColor.GRAY_700.color
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
                        bgColor = MaterialColor.RED_700.color
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

private fun List<String>.toVersionRangeText(): String {
    if (isEmpty()) return "未知MC版本"
    if (size <= 4) return joinToString(" / ")
    return "${first()} - ${last()}（${size}个版本）"
}
