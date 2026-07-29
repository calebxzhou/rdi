package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ensureUploadFfmpegReady
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.baseShapeRadius
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.client.ui.loadResourceBitmap
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Modpack
import kotlinx.coroutines.launch

/**
 * calebxzhou @ 2026-05-14 11:29
 */

@Composable
fun RemoteModpackScreen(
    onOpenInfo: (String) -> Unit,
    onOpenUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var uploadErrorText by remember { mutableStateOf<String?>(null) }
    var modpacks by remember { mutableStateOf<List<Modpack.BriefVo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    val searchState = rememberTextFieldState()
    var selectedCategory by rememberSaveable { mutableStateOf<Modpack.Category?>(null) }
    var selectedMcVer by rememberSaveable { mutableStateOf<McVersion?>(null) }
    var selectedSort by rememberSaveable { mutableStateOf(Modpack.SearchSort.UPDATED) }
    var hasMore by remember { mutableStateOf(false) }
    var requestKeyword by rememberSaveable { mutableStateOf("") }
    var requestVersion by remember { mutableStateOf(0) }
    var compactFilterPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var miniCardMode by rememberSaveable { mutableStateOf(false) }

    val openUploadAfterToolCheck: () -> Unit = {
        scope.launch {
            runCatching {
                ensureUploadFfmpegReady()
            }.onSuccess {
                onOpenUpload?.invoke()
            }.onFailure { error ->
                uploadErrorText = error.message ?: "检查传包工具失败"
            }
        }
    }

    suspend fun loadModpacks(reset: Boolean) {
        val offset = if (reset) 0 else modpacks.size
        if (reset) {
            loading = true
        } else {
            loadingMore = true
        }
        errorMessage = null
        val result = runCatching {
            server.makeRequest<Modpack.SearchResultVo>(
                path = "modpack/search",
                params = buildMap<String, Any> {
                    put("offset", offset)
                    put("limit", 24)
                    if (requestKeyword.isNotBlank()) {
                        put("q", requestKeyword)
                    }
                    if (onlyMine) {
                        put("mine", true)
                    }
                    if (selectedMcVer != null) {
                        put("mcVer", selectedMcVer!!.mcVer)
                    }
                    if (selectedSort != Modpack.SearchSort.RELEVANCE) {
                        put("sort", selectedSort.name)
                    }
                    if (selectedCategory != null) {
                        put("category", selectedCategory!!.name)
                    }
                }
            ).data ?: Modpack.SearchResultVo()
        }
        result.onSuccess { page ->
            hasMore = page.hasMore
            modpacks = if (reset) page.items else modpacks + page.items
        }.onFailure {
            errorMessage = "加载整合包失败: ${it.message}"
            if (reset) {
                modpacks = emptyList()
                hasMore = false
            }
        }
        loading = false
        loadingMore = false
    }

    fun submitSearch() {
        val keyword = searchState.text.toString().trim()
        if (requestKeyword == keyword) {
            requestVersion += 1
        } else {
            requestKeyword = keyword
        }
    }

    fun clearFilters() {
        val hasAppliedFilters = requestKeyword.isNotBlank() ||
            onlyMine ||
            selectedCategory != null ||
            selectedMcVer != null ||
            selectedSort != Modpack.SearchSort.UPDATED
        searchState.setTextAndPlaceCursorAtEnd("")
        requestKeyword = ""
        onlyMine = false
        selectedCategory = null
        selectedMcVer = null
        selectedSort = Modpack.SearchSort.UPDATED
        if (!hasAppliedFilters) {
            return
        }
    }

    fun activeFilterCount(): Int {
        var count = 0
        if (requestKeyword.isNotBlank()) count += 1
        if (onlyMine) count += 1
        if (selectedCategory != null) count += 1
        if (selectedMcVer != null) count += 1
        if (selectedSort != Modpack.SearchSort.UPDATED) count += 1
        return count
    }

    @Composable
    fun FilterSidebar(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RemoteModpackFilterSection("分类") {
                RemoteModpackCategoryFilter(
                    selected = selectedCategory,
                    onlyMine = onlyMine,
                    onSelectedChange = { selectedCategory = it },
                    onOnlyMineChange = { onlyMine = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            RemoteModpackFilterSection("排序") {
                RemoteModpackFilterGrid(
                    texts = Modpack.SearchSort.entries.map { it.toLabel() },
                    selectedIndex = Modpack.SearchSort.entries.indexOf(selectedSort),
                    onChipClick = { index -> selectedSort = Modpack.SearchSort.entries[index] }
                )
            }
            RemoteModpackFilterSection("MC版本") {
                val versions = listOf<McVersion?>(null) + McVersion.entries.filter { it.enabled }
                RemoteModpackFilterGrid(
                    items = versions.map { version ->
                        RemoteModpackGridChipItem(
                            text = version?.simpleVer ?: "全部",
                            selected = selectedMcVer == version,
                            iconPath = version?.icon,
                            showText = version == null,
                            tooltip = version?.simpleVer,
                            onClick = { selectedMcVer = version }
                        )
                    },
                    columns = 4
                )
            }
            if (
                requestKeyword.isNotBlank() ||
                onlyMine ||
                selectedCategory != null ||
                selectedMcVer != null ||
                selectedSort != Modpack.SearchSort.UPDATED
            ) {
                TextButton(onClick = ::clearFilters) {
                    Text("清空筛选", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    @Composable
    fun SearchBar() {
        RRow {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text("搜索整合包名或简介") },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(baseShapeRadius.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
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
            CircleIconButton(
                icon = "\uF002",
                tooltip = "搜索",
                showText = false
            ) {
                submitSearch()
            }
            CircleIconButton(
                icon = if (miniCardMode) "\uF03A" else "\uDB80\uDEC1",
                tooltip = if (miniCardMode) "普通" else "简略",
                bgColor = if (miniCardMode) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {
                miniCardMode = !miniCardMode
            }
            if (onOpenUpload != null && loggedAccount.hasMsid) {
                CircleIconButton(
                    icon = "\uDB80\uDFD5",
                    tooltip = "传包"
                ) {
                    openUploadAfterToolCheck()
                }
            }
        }
    }

    @Composable
    fun CompactFilterToggle() {
        val expanded = compactFilterPanelExpanded
        val activeFilterCount = activeFilterCount()
        RowV(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(
                icon = if (expanded) "\uE70D" else "\uE76C",
                tooltip = if (expanded) "收起搜索与筛选" else "展开搜索与筛选",
                bgColor = if (expanded) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {
                compactFilterPanelExpanded = !compactFilterPanelExpanded
            }
            if (activeFilterCount > 0) {
                Text(
                    text = "已启用$activeFilterCount 项筛选",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    fun ResultList(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
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

            if (miniCardMode) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(modpacks, key = { it.id.toHexString() }) { modpack ->
                        modpack.ModpackCard(
                            miniMode = true,
                            onClick = { onOpenInfo(modpack.id.toHexString()) }
                        )
                    }
                    if (!loading && modpacks.isEmpty()) {
                        item(
                            key = "empty",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = if (onlyMine) "你还没有符合筛选条件的整合包" else "没有找到符合条件的整合包",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        scope.launch {
                                            loadModpacks(reset = false)
                                        }
                                    }) {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(modpacks, key = { it.id.toHexString() }) { modpack ->
                        modpack.ModpackCard(
                            miniMode = false,
                            onClick = { onOpenInfo(modpack.id.toHexString()) }
                        )
                    }
                    if (!loading && modpacks.isEmpty()) {
                        item("empty") {
                            Text(
                                text = if (onlyMine) "你还没有符合筛选条件的整合包" else "没有找到符合条件的整合包",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hasMore) {
                        item("load-more") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator()
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            loadModpacks(reset = false)
                                        }
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
    }

    LaunchedEffect(requestKeyword, requestVersion, selectedCategory, selectedMcVer, selectedSort, onlyMine) {
        loadModpacks(reset = true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            uploadErrorText?.let { AlertErr(it) { uploadErrorText = null } }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                        ResultList(modifier = Modifier.weight(1f))
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
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        )
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SearchBar()
                            ResultList(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun Modpack.SearchSort.toLabel(): String = when (this) {
    Modpack.SearchSort.RELEVANCE -> "相关"
    Modpack.SearchSort.UPDATED -> "最近"
    Modpack.SearchSort.POPULAR -> "最热"
    // Modpack.SearchSort.NAME -> "名称"
}

@Composable
private fun RemoteModpackFilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun RemoteModpackCategoryFilter(
    selected: Modpack.Category?,
    onlyMine: Boolean,
    onSelectedChange: (Modpack.Category?) -> Unit,
    onOnlyMineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryItems = listOf(
        RemoteModpackGridChipItem(
            text = "我的包",
            selected = onlyMine,
            onClick = { onOnlyMineChange(!onlyMine) }
        ),
        RemoteModpackGridChipItem(
            text = "全部",
            selected = selected == null,
            onClick = { onSelectedChange(null) }
        )
    ) + Modpack.Category.entries.map { category ->
        RemoteModpackGridChipItem(
            text = category.label,
            selected = selected == category,
            onClick = {
                onSelectedChange(
                    if (selected == category) null else category
                )
            }
        )
    }
    RemoteModpackFilterGrid(
        items = categoryItems,
        modifier = modifier
    )
}


@Composable
private fun RemoteModpackFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconPath: String? = null,
    showText: Boolean = true,
    tooltip: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val button: @Composable () -> Unit = {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = baseRoundCornerShape,
            colors = colors,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                iconPath?.let { path ->
                    Image(
                        bitmap = remember(path) { loadResourceBitmap(path) },
                        contentDescription = text,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (showText || iconPath == null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
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

private data class RemoteModpackGridChipItem(
    val text: String,
    val selected: Boolean,
    val iconPath: String? = null,
    val showText: Boolean = true,
    val tooltip: String? = null,
    val onClick: () -> Unit
)

@Composable
private fun RemoteModpackFilterGrid(
    texts: List<String>,
    selectedIndex: Int,
    onChipClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    RemoteModpackFilterGrid(
        items = texts.mapIndexed { index, text ->
            RemoteModpackGridChipItem(
                text = text,
                selected = index == selectedIndex,
                onClick = { onChipClick(index) }
            )
        },
        modifier = modifier,
        columns = columns
    )
}

@Composable
private fun RemoteModpackFilterGrid(
    items: List<RemoteModpackGridChipItem>,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    RemoteModpackFilterChip(
                        text = item.text,
                        selected = item.selected,
                        onClick = item.onClick,
                        iconPath = item.iconPath,
                        showText = item.showText,
                        tooltip = item.tooltip,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
