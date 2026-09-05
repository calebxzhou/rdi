package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.modcatalog.CatalogMod
import calebxzau.rdi.client.ui.SearchField
import calebxzau.rdi.client.ui.viewmodel.ModCatalogViewModel
import calebxzhou.rdi.client.ui.comp.CatalogModCard
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModCatalogScreen(
    route: ModCatalogRoute,
    onBack: () -> Unit = {},
    onOpenMod: (CatalogMod) -> Unit = {},
    viewModel: ModCatalogViewModel = koinViewModel(
        key = modCatalogViewModelKey(route)
    ) {
        parametersOf(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.query) {
        gridState.scrollToItem(0)
    }

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

private fun modCatalogViewModelKey(route: ModCatalogRoute): String =
    "${route.requiredMcVer}:${route.requiredLoader}:${route.targetLocalVersionId}:${route.targetHostId}:${route.fromAllHosts}:${route.fromHostMods}"
