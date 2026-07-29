package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ImageIconButton
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.modcatalog.CatalogSearchCursor
import calebxzhou.rdi.client.modcatalog.CatalogSearchRequest
import calebxzhou.rdi.client.modcatalog.CatalogSort
import calebxzhou.rdi.client.modcatalog.CatalogTarget
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.ui.comp.CatalogModCard
import calebxzhou.rdi.client.ui.iconBitmap
import calebxzhou.rdi.client.ui.loadResourceBitmap
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun RemoteModScreen(
    catalog: ModCatalog,
    requiredMcVer: McVersion? = null,
    requiredLoader: ModLoader? = null,
    modifier: Modifier = Modifier,
    onOpenMod: (CatalogMod) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable { mutableStateOf("") }
    val searchState = rememberTextFieldState(searchText)
    val gridState = rememberLazyGridState()
    var query by rememberSaveable { mutableStateOf("") }
    var requestVersion by rememberSaveable { mutableStateOf(0) }
    var selectedMcVersion by rememberSaveable(requiredMcVer) {
        mutableStateOf(requiredMcVer ?: McVersion.V211)
    }
    var selectedLoader by rememberSaveable(requiredMcVer, requiredLoader) {
        mutableStateOf(requiredLoader ?: (requiredMcVer ?: McVersion.V211).loaderVersions.keys.first())
    }
    var mods by remember { mutableStateOf<List<CatalogMod>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<CatalogSearchCursor?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { searchText = it }
    }

    val versions = remember(requiredMcVer) {
        requiredMcVer?.let(::listOf) ?: McVersion.entries.filter(McVersion::enabled)
    }
    val loaders = remember(selectedMcVersion, requiredLoader) {
        requiredLoader?.let(::listOf) ?: selectedMcVersion.loaderVersions.keys.toList()
    }

    LaunchedEffect(selectedMcVersion) {
        if (selectedLoader !in loaders) selectedLoader = loaders.first()
    }

    suspend fun load(reset: Boolean) {
        if (reset) loading = true else loadingMore = true
        errorMessage = null
        try {
            val outcome = catalog.search(
                CatalogSearchRequest(
                    query = query,
                    target = CatalogTarget(selectedMcVersion, selectedLoader),
                    sort = CatalogSort.RELEVANCE,
                    cursor = if (reset) null else nextCursor
                )
            ).getOrThrow()
            mods = if (reset) outcome.value.items else mods + outcome.value.items
            nextCursor = outcome.value.nextCursor
            if (outcome.issues.isNotEmpty()) errorMessage = "部分目录信息暂时不可用，已显示可用结果"
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            lgr.warn(cause) { "加载模组目录失败" }
            if (reset) {
                mods = emptyList()
                nextCursor = null
            }
            errorMessage = "加载模组失败，请稍后重试"
        } finally {
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(query, requestVersion, selectedMcVersion, selectedLoader) {
        load(reset = true)
    }

    fun submitSearch() {
        val next = searchState.text.toString().trim()
        if (next == query) requestVersion++ else query = next
    }

    @Composable
    fun SearchBar() {
        RowV(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text("搜索模组") },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(44.dp).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        submitSearch()
                        true
                    } else false
                }
            )
            Space8w()
            CircleIconButton(
                "\uF002",
                "搜索",
                showText = false,
                bgColor = MaterialTheme.colorScheme.primary
            ) {
                submitSearch()
            }
        }
    }

    @Composable
    fun FilterButton(
        selected: Boolean,
        tooltip: String,
        iconPath: String? = null,
        iconName: String? = null,
        onClick: () -> Unit
    ) {
        SimpleTooltip(tooltip) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                shape = baseRoundCornerShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                val bitmap = when {
                    iconPath != null -> remember(iconPath) { loadResourceBitmap(iconPath) }
                    iconName != null -> remember(iconName) { iconBitmap(iconName) }
                    else -> null
                }
                bitmap?.let {
                    Image(it, tooltip, Modifier.size(24.dp), contentScale = ContentScale.Fit)
                } ?: Text(tooltip)
            }
        }
    }

    @Composable
    fun Filters() {
        Column(
            modifier = Modifier.width(50.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            versions.forEach { version ->
                FilterButton(
                    selected = selectedMcVersion == version,
                    tooltip = "MC${version.mcVer}",
                    iconPath = version.icon
                ) {
                    if (requiredMcVer == null) selectedMcVersion = version
                }
            }
            loaders.forEach { loader ->
                FilterButton(
                    selected = selectedLoader == loader,
                    tooltip = loader.name,
                    iconName = loader.name
                ) { if (requiredLoader == null) selectedLoader = loader }
            }
        }
    }

    @Composable
    fun Results() {
        Column(Modifier.fillMaxSize()) {
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(360.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(mods, key = { it.identity.stableKey }) { mod ->
                    CatalogModCard(mod, onClick = { onOpenMod(mod) })
                }
                if (!loading && mods.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("没有找到符合条件的模组")
                }
                if (nextCursor != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (loadingMore) CircularProgressIndicator() else TextButton(
                            onClick = { scope.launch { load(reset = false) } }
                        ) { Text("加载更多") }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxHeight > maxWidth || maxWidth < 960.dp) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchBar()
                if (requiredMcVer == null || requiredLoader == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        versions.forEach { version ->
                        ImageIconButton(
                            icon = version.iconName,
                            tooltip = "MC${version.mcVer}",
                            size = 36,
                            showText = false,
                            bgColor = if (version == selectedMcVersion) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) { if (requiredMcVer == null) selectedMcVersion = version }
                        }
                    }
                }
                Results()
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (requiredMcVer == null || requiredLoader == null) Filters()
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchBar()
                    Results()
                }
            }
        }
    }
}
