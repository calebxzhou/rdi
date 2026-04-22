package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleTabBar
import calebxzhou.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Modpack

/**
 * calebxzhou @ 2026-01-13 18:27
 */
typealias ResourceScreenTitleActions = @Composable RowScope.() -> Unit

@Serializable
enum class ResourceTab(
    val icon: String,
    val label: String
) {
    All("\uDB86\uDDD5", "全部整合包"),
    Installed("\uDB86\uDDD7", "已安装整合包"),
    McResources("\uDB80\uDF73", "MC资源");

    companion object {
        fun fromRouteValue(value: String?): ResourceTab {
            return entries.firstOrNull { it.name == value } ?: All
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScreen(
    initialCategory: ResourceTab = ResourceTab.All,
    requiredMcVer: McVersion? = null,
    onBack: (() -> Unit) = {},
    onOpenUpload: (() -> Unit) = {},
    onOpenInfo: ((String) -> Unit) = {},
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }

    MainColumn {
        TitleRow("资源", onBack) {
            TitleTabBar(
                items = remember {
                    ResourceTab.entries.map { TitleTabItem(it, it.icon, it.label) }
                },
                selected = category,
                onSelect = { category = it }
            )
            Space8w()
            if (loggedAccount.hasMsid) {
                CircleIconButton(
                    icon = "\uDB80\uDFD5",
                    tooltip = "传包",
                    bgColor = MaterialColor.DEEP_PURPLE_700.color
                ) {
                    onOpenUpload()
                }
            }
        }
        Space8h()
        Box(modifier = Modifier.fillMaxSize()) {
            when (category) {
                ResourceTab.All -> {
                    RemoteModpackPane(
                        onOpenInfo = onOpenInfo
                    )
                }

                ResourceTab.Installed -> {
                    InstalledResourcePane(
                        onOpenPlay = onOpenPlay,
                        onOpenTaskList = onOpenTaskList,
                        showMcVersionShortcut = false,
                        showPaneActions = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ResourceTab.McResources -> {
                    McVersionPane(
                        requiredMcVer = requiredMcVer,
                        onOpenTaskList = onOpenTaskList,
                        showPaneActions = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteModpackPane(
    onOpenInfo: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
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
                    texts = versions.map { it?.mcVer ?: "全部" },
                    selectedIndex = versions.indexOf(selectedMcVer),
                    onChipClick = { index -> selectedMcVer = versions[index] }
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
                    Text("清空筛选", color = MaterialColor.RED_700.color)
                }
            }
        }
    }

    @Composable
    fun SearchBar() {
        RowV(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text("搜索整合包名或简介") },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.body2,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
                tooltip = "搜索",
                bgColor = MaterialColor.BLUE_700.color
            ) {
                submitSearch()
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
                bgColor = if (expanded) MaterialColor.GRAY_700.color else MaterialColor.BLUE_700.color
            ) {
                compactFilterPanelExpanded = !compactFilterPanelExpanded
            }
            if (activeFilterCount > 0) {
                Text(
                    text = "已启用$activeFilterCount 项筛选",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }

    @Composable
    fun ResultList(modifier: Modifier = Modifier) {
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(modpacks, key = { it.id.toHexString() }) { modpack ->
                    modpack.ModpackCard(onClick = { onOpenInfo(modpack.id.toHexString()) })
                }
                if (!loading && modpacks.isEmpty()) {
                    item("empty") {
                        Text(
                            text = if (onlyMine) "你还没有符合筛选条件的整合包" else "没有找到符合条件的整合包",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
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

    LaunchedEffect(requestKeyword, requestVersion, selectedCategory, selectedMcVer, selectedSort, onlyMine) {
        loadModpacks(reset = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

private fun Modpack.SearchSort.toLabel(): String = when (this) {
    Modpack.SearchSort.RELEVANCE -> "相关"
    Modpack.SearchSort.UPDATED -> "最近"
    Modpack.SearchSort.POPULAR -> "最热"
    Modpack.SearchSort.NAME -> "名称"
}

@Composable
private fun RemoteModpackFilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
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
    modifier: Modifier = Modifier
) {
    androidx.compose.material.Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialColor.BLUE_700.color else MaterialColor.GRAY_200.color
    ) {
        Text(
            text = text,
            color = if (selected) MaterialColor.WHITE.color else MaterialColor.GRAY_900.color,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        )
    }
}

private data class RemoteModpackGridChipItem(
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun RemoteModpackFilterGrid(
    texts: List<String>,
    selectedIndex: Int,
    onChipClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    RemoteModpackFilterGrid(
        items = texts.mapIndexed { index, text ->
            RemoteModpackGridChipItem(
                text = text,
                selected = index == selectedIndex,
                onClick = { onChipClick(index) }
            )
        },
        modifier = modifier
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
