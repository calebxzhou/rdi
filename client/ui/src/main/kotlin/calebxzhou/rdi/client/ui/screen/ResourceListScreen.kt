package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.ScrollableContentBody
import calebxzau.rdi.client.ui.SearchField
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.ModrinthProjectCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModrinthSearchIndex
import kotlinx.coroutines.launch


@Composable
fun ShaderListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenShader: (ModrinthProjectCardVo) -> Unit = {}
) = ModrinthProjectListScreen(
    requiredMcVer = requiredMcVer,
    modifier = modifier,
    projectDisplayName = "光影包",
    onBack = onBack,
    searchProject = { query, mcVersion, index, offset, limit ->
        ShaderSearchService.searchShaders(query, mcVersion, index, offset, limit)
    },
    onOpenProject = onOpenShader
)


@Composable
fun ResourcepackListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenResourcepack: ((ModrinthProjectCardVo) -> Unit)? = null
) = ModrinthProjectListScreen(
    requiredMcVer = requiredMcVer,
    modifier = modifier,
    projectDisplayName = "资源包",
    onBack = onBack,
    searchProject = { query, mcVersion, index, offset, limit ->
        ResourcepackSearchService.searchResourcepacks(query, mcVersion, index, offset, limit)
    },
    onOpenProject = onOpenResourcepack
)


@Composable
fun ModrinthProjectListScreen(
    requiredMcVer: McVersion? = null,
    modifier: Modifier = Modifier,
    projectDisplayName: String,
    onBack: () -> Unit = {},
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
    var searchText by rememberSaveable { mutableStateOf("") }
    var requestKeyword by rememberSaveable { mutableStateOf("") }
    var requestVersion by rememberSaveable { mutableStateOf(0) }
    var selectedMcVer by rememberSaveable { mutableStateOf(requiredMcVer) }
    var selectedSort by rememberSaveable { mutableStateOf(ModrinthSearchIndex.DOWNLOADS) }

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
        val keyword = searchText.trim()
        if (keyword == requestKeyword) {
            requestVersion += 1
        } else {
            requestKeyword = keyword
        }
    }

    @Composable
    fun ResultList() {
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.Top
        ) {
            projects.forEach { project ->
                ModrinthProjectCard(
                    project = project,
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    onClick = onOpenProject?.let { open -> { open(project) } }
                )
            }
        }
        if (!loading && projects.isEmpty()) {
            Text(
                text = "没有找到符合条件的${projectDisplayName}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        if (hasMore) {
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

    LaunchedEffect(projectDisplayName, requestKeyword, requestVersion, selectedMcVer, selectedSort) {
        loadProjects(reset = true)
    }

    MaxBox {
        ScreenContentSurface(
            size = ScreenContentSize.LARGE,
            modifier = modifier
        ) {
            TitleRow(projectDisplayName, onBack) {
                SearchField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = "搜索${projectDisplayName}",
                    onSearch = ::submitSearch,
                    modifier = Modifier.width(240.dp),
                    loading = loading,
                )
            }
            ScrollableContentBody(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultList()
            }
        }
    }
}

