package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.client.service.ResourcepackSearchService
import calebxzhou.rdi.client.service.ShaderSearchService
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.comp.ModrinthProjectCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModrinthSearchIndex
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    onOpenShader: (ModrinthProjectCardVo) -> Unit = {}
) = ModrinthProjectListScreen(
    requiredMcVer = requiredMcVer,
    modifier = modifier,
    projectDisplayName = "光影包",
    searchProject = { query, mcVersion, index, offset, limit ->
        ShaderSearchService.searchShaders(query, mcVersion, index, offset, limit)
    },
    onOpenProject = onOpenShader
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcepackListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    onOpenResourcepack: ((ModrinthProjectCardVo) -> Unit)? = null
) = ModrinthProjectListScreen(
    requiredMcVer = requiredMcVer,
    modifier = modifier,
    projectDisplayName = "资源包",
    searchProject = { query, mcVersion, index, offset, limit ->
        ResourcepackSearchService.searchResourcepacks(query, mcVersion, index, offset, limit)
    },
    onOpenProject = onOpenResourcepack
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModrinthProjectListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    projectDisplayName: String,
    searchProject: suspend (
        query: String?,
        mcVersion: String?,
        index: ModrinthSearchIndex,
        offset: Int,
        limit: Int
    ) -> ModrinthProjectSearchResult,
    onOpenProject: ((ModrinthProjectCardVo) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ModrinthProjectCardVo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var totalHits by remember { mutableStateOf(0) }
    val searchState = rememberTextFieldState()
    var requestKeyword by rememberSaveable { mutableStateOf("") }
    var requestVersion by rememberSaveable { mutableStateOf(0) }
    var selectedMcVer by rememberSaveable { mutableStateOf(requiredMcVer) }
    var selectedSort by rememberSaveable { mutableStateOf(ModrinthSearchIndex.DOWNLOADS) }
    var compactFilterPanelExpanded by rememberSaveable { mutableStateOf(false) }

    suspend fun loadProjects(reset: Boolean) {
        val offset = if (reset) 0 else projects.size
        if (reset) {
            loading = true
        } else {
            loadingMore = true
        }
        errorMessage = null
        runCatching {
            searchProject(
                requestKeyword,
                 selectedMcVer?.mcVer,
               selectedSort,
                offset,
                20
            )
        }.onSuccess { result ->
            projects = if (reset) result.projects else projects + result.projects
            totalHits = result.totalHits
            hasMore = result.offset + result.limit < result.totalHits
        }.onFailure { error ->
            if (reset) {
                projects = emptyList()
                totalHits = 0
                hasMore = false
            }
            errorMessage = "加载${projectDisplayName}失败: ${error.message ?: error}"
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

    fun clearFilters() {
        searchState.setTextAndPlaceCursorAtEnd("")
        requestKeyword = ""
        selectedMcVer = requiredMcVer
        selectedSort = ModrinthSearchIndex.DOWNLOADS
        requestVersion += 1
    }

    @Composable
    fun SearchBar() {
        RowV(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text("搜索${projectDisplayName}") },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
            Space8w()
            CircleIconButton(
                icon = "\uF002",
                tooltip = "搜索${projectDisplayName}",
                showText = false,
            ) {
                submitSearch()
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
                text = "共找到${if (loading) "--" else totalHits}个${projectDisplayName}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 400.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(projects, key = { it.projectId }) { project ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ModrinthProjectCard(
                            project = project,
                            modifier = Modifier.widthIn(max = 400.dp),
                            onClick = onOpenProject?.let { open -> { open(project) } }
                        )
                    }
                }
                if (!loading && projects.isEmpty()) {
                    item(
                        key = "empty",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Text(
                            text = "没有找到符合条件的${projectDisplayName}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
                                    scope.launch { loadProjects(reset = false) }
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

    LaunchedEffect(projectDisplayName, requestKeyword, requestVersion, selectedMcVer, selectedSort) {
        loadProjects(reset = true)
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
                }
                ResultList(modifier = Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SearchBar()
                    ResultList(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModrinthProjectFilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        content()
    }
}

private data class ModrinthProjectFilterChipItem(
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun ModrinthProjectFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ModrinthProjectFilterGrid(
    items: List<ModrinthProjectFilterChipItem>,
    columns: Int = 3
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
                    ModrinthProjectFilterChip(
                        text = item.text,
                        selected = item.selected,
                        onClick = item.onClick,
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
