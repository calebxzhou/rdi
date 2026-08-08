package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzau.rdi.client.modcatalog.CatalogMod
import calebxzau.rdi.client.ui.SearchField
import calebxzau.rdi.client.ui.viewmodel.RemoteModViewModel
import calebxzhou.rdi.client.ui.comp.CatalogModCard
import calebxzhou.rdi.client.ui.iconBitmapPng
import calebxzhou.rdi.client.ui.loadResourceBitmap
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RemoteModScreen(
    route: RemoteModRoute,
    onBack: () -> Unit = {},
    onOpenMod: (CatalogMod) -> Unit = {},
    viewModel: RemoteModViewModel = koinViewModel(
        key = remoteModViewModelKey(route)
    ) {
        parametersOf(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val filterScrollState = rememberScrollState()
    val requiredMcVersion = route.requiredMcVer?.let(McVersion::from)
    val requiredLoader = route.requiredLoader?.let(ModLoader::from)
    val versions = requiredMcVersion?.let(::listOf)
        ?: McVersion.entries.filter { it.enabled && (requiredLoader == null || requiredLoader in it.loaderVersions) }
    val loaders = requiredLoader?.let(::listOf) ?: state.selectedMcVersion.loaderVersions.keys.toList()

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("模组", onBack) {
                SearchField(
                    value = state.searchText,
                    onValueChange = viewModel::setSearchText,
                    placeholder = "搜索模组",
                    onSearch = viewModel::submitSearch,
                    modifier = Modifier.width(240.dp),
                    loading = state.loading,
                )
            }
            ContentBody {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (requiredMcVersion == null || requiredLoader == null) {
                        RScrollableColumn(
                            modifier = Modifier.width(50.dp).fillMaxHeight(),
                            state = filterScrollState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            versions.forEach { version ->
                                RemoteModFilterButton(
                                    selected = state.selectedMcVersion == version,
                                    tooltip = "MC${version.mcVer}",
                                    iconPath = version.icon,
                                    onClick = { viewModel.selectMcVersion(version) },
                                )
                            }
                            loaders.forEach { loader ->
                                RemoteModFilterButton(
                                    selected = state.selectedLoader == loader,
                                    tooltip = loader.name,
                                    iconName = loader.name,
                                    onClick = { viewModel.selectLoader(loader) },
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(360.dp),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(state.mods, key = { it.identity.stableKey }) { mod ->
                                        CatalogModCard(mod, onClick = { onOpenMod(mod) })
                                    }
                                    if (!state.loading && state.mods.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text("没有找到符合条件的模组")
                                        }
                                    }
                                    if (state.nextCursor != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                                if (state.loadingMore) {
                                                    CircularProgressIndicator()
                                                } else {
                                                    TextButton(onClick = viewModel::loadMore) { Text("加载更多") }
                                                }
                                            }
                                        }
                                    }
                                }
                                RVerticalScrollbar(
                                    gridState = gridState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteModFilterButton(
    selected: Boolean,
    tooltip: String,
    iconPath: String? = null,
    iconName: String? = null,
    onClick: () -> Unit,
) {
    SimpleTooltip(tooltip) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            shape = baseRoundCornerShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            contentPadding = PaddingValues(4.dp),
        ) {
            val bitmap = when {
                iconPath != null -> remember(iconPath) { loadResourceBitmap(iconPath) }
                iconName != null -> remember(iconName) { iconBitmapPng(iconName) }
                else -> null
            }
            bitmap?.let {
                androidx.compose.foundation.Image(
                    it,
                    tooltip,
                    Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )
            } ?: Text(tooltip)
        }
    }
}

private fun remoteModViewModelKey(route: RemoteModRoute): String =
    "${route.requiredMcVer}:${route.requiredLoader}:${route.targetLocalVersionId}:${route.targetHostId}:${route.targetHost2Id}:${route.fromAllHosts}:${route.fromHostMods}"
