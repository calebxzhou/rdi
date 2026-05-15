package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.client.model.RemoteModSourceFilter
import calebxzhou.rdi.client.service.ModSearchService
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.SimpleTooltip
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.baseShapeRadius
import calebxzhou.rdi.client.ui.comp.RemoteModCard
import calebxzhou.rdi.client.ui.comp.RemoteModSourceIcon
import calebxzhou.rdi.client.ui.iconBitmap
import calebxzhou.rdi.client.ui.loadResourceBitmap
import calebxzhou.rdi.client.ui.roundShape
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.ModrinthSearchIndex
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteModScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    onOpenMod: (RemoteModCardVo) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var mods by remember { mutableStateOf<List<RemoteModCardVo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var totalHits by remember { mutableStateOf(0) }
    var nextModrinthOffset by remember { mutableStateOf(0) }
    var nextCurseForgeOffset by remember { mutableStateOf(0) }
    val searchState = rememberTextFieldState()
    var requestKeyword by rememberSaveable { mutableStateOf("") }
    var requestVersion by rememberSaveable { mutableStateOf(0) }
    val defaultMcVer = requiredMcVer ?: McVersion.V211
    var selectedMcVer by rememberSaveable(requiredMcVer) { mutableStateOf(defaultMcVer) }
    var selectedLoader by rememberSaveable(requiredMcVer) {
        mutableStateOf(defaultMcVer.defaultRemoteModLoader())
    }
    var selectedSort by rememberSaveable { mutableStateOf(ModrinthSearchIndex.RELEVANCE) }
    var selectedSourceFilter by rememberSaveable { mutableStateOf(RemoteModSourceFilter.ALL) }
    var compactFilterPanelExpanded by rememberSaveable { mutableStateOf(false) }

    val lockedMcVer = requiredMcVer
    val loaderOptions = remember(selectedMcVer) { selectedMcVer.supportedRemoteModLoaders() }

    LaunchedEffect(lockedMcVer) {
        lockedMcVer?.let { version ->
            selectedMcVer = version
            val nextLoaderOptions = version.supportedRemoteModLoaders()
            if (selectedLoader !in nextLoaderOptions) {
                selectedLoader = version.defaultRemoteModLoader()
            }
        }
    }

    suspend fun loadMods(reset: Boolean) {
        val offset = if (reset) 0 else mods.size
        val modrinthOffset = if (reset) 0 else nextModrinthOffset
        val curseForgeOffset = if (reset) 0 else nextCurseForgeOffset
        if (reset) {
            loading = true
        } else {
            loadingMore = true
        }
        errorMessage = null
        runCatching {
            ModSearchService.searchMods(
                query = requestKeyword,
                mcVersion = selectedMcVer.mcVer,
                loader = selectedLoader.toModrinthSearchLoader(),
                index = selectedSort,
                sourceFilter = selectedSourceFilter,
                offset = offset,
                modrinthOffset = modrinthOffset,
                curseForgeOffset = curseForgeOffset,
                limit = 20
            )
        }.onSuccess { result ->
            mods = if (reset) result.mods else mods + result.mods
            totalHits = result.totalHits
            nextModrinthOffset = result.nextModrinthOffset
            nextCurseForgeOffset = result.nextCurseForgeOffset
            hasMore = result.hasMore
        }.onFailure { error ->
            if (reset) {
                mods = emptyList()
                totalHits = 0
                nextModrinthOffset = 0
                nextCurseForgeOffset = 0
                hasMore = false
            }
            errorMessage = "加载模组失败: ${error.message ?: error}"
        }
        loading = false
        loadingMore = false
    }

    fun submitSearch() {
        val keyword = searchState.text.toString().trim()
        if (keyword == requestKeyword) {
            requestVersion += 1
        } else {
            requestKeyword = keyword
        }
    }

    LaunchedEffect(loaderOptions) {
        if (selectedLoader !in loaderOptions) {
            selectedLoader = selectedMcVer.defaultRemoteModLoader()
        }
    }

    @Composable
    fun SearchBar() {
        RowV(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text("搜索模组") },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.body2,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            submitSearch()
                            true
                        } else {
                            false
                        }
                    }
            )
            Space8w()
            CircleIconButton(
                icon = "\uE721",
                tooltip = "搜索模组",
                bgColor = MaterialColor.BLUE_700.color
            ) {
                submitSearch()
            }
        }
    }

    @Composable
    fun FilterSidebar(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RemoteModFilterSection("排序") {
                RemoteModFilterGrid(
                    items = listOf(
                        RemoteModFilterChipItem("相关", selectedSort == ModrinthSearchIndex.RELEVANCE) {
                            selectedSort = ModrinthSearchIndex.RELEVANCE
                        },
                        RemoteModFilterChipItem("最热", selectedSort == ModrinthSearchIndex.DOWNLOADS) {
                            selectedSort = ModrinthSearchIndex.DOWNLOADS
                        },
                        RemoteModFilterChipItem("收藏", selectedSort == ModrinthSearchIndex.FOLLOWS) {
                            selectedSort = ModrinthSearchIndex.FOLLOWS
                        },
                        RemoteModFilterChipItem("最近", selectedSort == ModrinthSearchIndex.UPDATED) {
                            selectedSort = ModrinthSearchIndex.UPDATED
                        }
                    )
                )
            }
            RemoteModFilterSection("来源") {
                RemoteModFilterGrid(
                    items = listOf(
                        RemoteModFilterChipItem("全部", selectedSourceFilter == RemoteModSourceFilter.ALL) {
                            selectedSourceFilter = RemoteModSourceFilter.ALL
                        },
                        RemoteModFilterChipItem(
                            text = "Modrinth",
                            selected = selectedSourceFilter == RemoteModSourceFilter.MODRINTH,
                            source = RemoteModSource.MODRINTH,
                            showText = false,
                            tooltip = "Modrinth"
                        ) { selectedSourceFilter = RemoteModSourceFilter.MODRINTH },
                        RemoteModFilterChipItem(
                            text = "CurseForge",
                            selected = selectedSourceFilter == RemoteModSourceFilter.CURSEFORGE,
                            source = RemoteModSource.CURSEFORGE,
                            showText = false,
                            tooltip = "CurseForge"
                        ) { selectedSourceFilter = RemoteModSourceFilter.CURSEFORGE }
                    ),
                    columns = 3
                )
            }
            RemoteModFilterSection("MC版本") {
                val versions = lockedMcVer?.let(::listOf) ?: McVersion.entries.filter { it.enabled }
                RemoteModFilterGrid(
                    items = versions.map { version ->
                        RemoteModFilterChipItem(
                            text = version.simpleVer,
                            selected = selectedMcVer == version,
                            iconPath = version.icon,
                            showText = false,
                            tooltip = version.simpleVer,
                            onClick = {
                                if (lockedMcVer == null) {
                                    selectedMcVer = version
                                    val nextLoaderOptions = version.supportedRemoteModLoaders()
                                    if (selectedLoader !in nextLoaderOptions) {
                                        selectedLoader = version.defaultRemoteModLoader()
                                    }
                                }
                            }
                        )
                    }
                )
            }
            RemoteModFilterSection("Loader") {
                val loaders = loaderOptions
                RemoteModFilterGrid(
                    items = loaders.map { loader ->
                        RemoteModFilterChipItem(
                            text = loader.displayName,
                            selected = selectedLoader == loader,
                            iconName = loader.iconName,
                            showText = false,
                            tooltip = loader.displayName,
                            onClick = { selectedLoader = loader }
                        )
                    }
                )
            }
        }
    }

    @Composable
    fun CompactFilterToggle() {
        RowV(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(
                icon = if (compactFilterPanelExpanded) "\uE70D" else "\uE76C",
                tooltip = if (compactFilterPanelExpanded) "收起搜索与筛选" else "展开搜索与筛选",
                bgColor = if (compactFilterPanelExpanded) MaterialColor.GRAY_700.color else MaterialColor.BLUE_700.color
            ) {
                compactFilterPanelExpanded = !compactFilterPanelExpanded
            }
            Text(
                text = "共找到${if (loading) "--" else totalHits}个模组",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun ResultGrid(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colors.error)
                Space8h()
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                Space8h()
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 430.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mods, key = { "${it.source}:${it.projectId}" }) { mod ->
                    RemoteModCard(
                        mod = mod,
                        onClick = { onOpenMod(mod) }
                    )
                }
                if (!loading && mods.isEmpty()) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Text(
                            text = "没有找到符合条件的模组",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
                if (hasMore) {
                    item(
                        key = "load-more",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator()
                            } else {
                                TextButton(onClick = {
                                    scope.launch { loadMods(reset = false) }
                                }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(requestKeyword, requestVersion, selectedMcVer, selectedLoader, selectedSort, selectedSourceFilter) {
        loadMods(reset = true)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLayout = maxHeight > maxWidth || maxWidth < 960.dp
        if (compactLayout) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactFilterToggle()
                if (compactFilterPanelExpanded) {
                    SearchBar()
                    FilterSidebar()
                }
                ResultGrid(modifier = Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                FilterSidebar(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SearchBar()
                    ResultGrid(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun McVersion.supportedRemoteModLoaders(): List<ModLoader> {
    return buildList {
        if (mcVer == "1.12.2") {
            add(ModLoader.forge)
        }
        addAll(loaderVersions.keys)
    }.distinct()
}

private fun McVersion.defaultRemoteModLoader(): ModLoader =
    if (this == McVersion.V211 && ModLoader.neoforge in supportedRemoteModLoaders()) {
        ModLoader.neoforge
    } else {
        supportedRemoteModLoaders().first()
    }

private fun ModLoader.toModrinthSearchLoader(): String =
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

private val ModLoader.iconName: String
    get() = when (this) {
        ModLoader.forge -> "forge"
        ModLoader.neoforge -> "neoforge"
        ModLoader.cleanroom -> "cleanroom"
    }

@Composable
private fun RemoteModFilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

private data class RemoteModFilterChipItem(
    val text: String,
    val selected: Boolean,
    val source: RemoteModSource? = null,
    val iconPath: String? = null,
    val iconName: String? = null,
    val showText: Boolean = true,
    val tooltip: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteModFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    source: RemoteModSource? = null,
    iconPath: String? = null,
    iconName: String? = null,
    showText: Boolean = true,
    tooltip: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val button: @Composable () -> Unit = {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = roundShape,
            colors = colors,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                source?.let {
                    RemoteModSourceIcon(
                        source = it,
                        modifier = Modifier.size(24.dp)
                    )
                }
                val filterIcon = when {
                    source != null -> null
                    iconPath != null -> remember(iconPath) { loadResourceBitmap(iconPath) }
                    iconName != null -> remember(iconName) { iconBitmap(iconName) }
                    else -> null
                }
                filterIcon?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = text,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (showText || (source == null && filterIcon == null)) {
                    androidx.compose.material3.Text(
                        text = text,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
    Box(modifier = modifier) {
        tooltip?.let {
            SimpleTooltip(it) { button() }
        } ?: button()
    }
}

@Composable
private fun RemoteModFilterGrid(
    items: List<RemoteModFilterChipItem>,
    columns: Int = 4
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    RemoteModFilterChip(
                        text = item.text,
                        selected = item.selected,
                        onClick = item.onClick,
                        source = item.source,
                        iconPath = item.iconPath,
                        iconName = item.iconName,
                        showText = item.showText,
                        tooltip = item.tooltip,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
