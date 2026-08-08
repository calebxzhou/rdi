package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.modcatalog.CatalogMod
import calebxzau.rdi.client.modcatalog.CatalogSearchCursor
import calebxzau.rdi.client.modcatalog.CatalogSearchRequest
import calebxzau.rdi.client.modcatalog.CatalogSort
import calebxzau.rdi.client.modcatalog.CatalogTarget
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.ui.screen.RemoteModRoute
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RemoteModUiState(
    val searchText: String = "",
    val query: String = "",
    val selectedMcVersion: McVersion = McVersion.V211,
    val selectedLoader: ModLoader = McVersion.V211.loaderVersions.keys.first(),
    val mods: List<CatalogMod> = emptyList(),
    val nextCursor: CatalogSearchCursor? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)

class RemoteModViewModel(
    private val route: RemoteModRoute,
    private val catalog: ModCatalog,
) : ViewModel() {
    private val requiredMcVersion = route.requiredMcVer?.let(McVersion::from)
    private val requiredLoader = route.requiredLoader?.let(ModLoader::from)
    private val initialMcVersion = requiredMcVersion
        ?: McVersion.entries.firstOrNull { version ->
            version.enabled && (requiredLoader == null || requiredLoader in version.loaderVersions)
        }
        ?: McVersion.V211
    private val initialLoader = requiredLoader?.takeIf { it in initialMcVersion.loaderVersions }
        ?: initialMcVersion.loaderVersions.keys.first()

    private val _uiState = MutableStateFlow(
        RemoteModUiState(
            selectedMcVersion = initialMcVersion,
            selectedLoader = initialLoader,
        )
    )
    val uiState: StateFlow<RemoteModUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    fun setSearchText(value: String) {
        _uiState.update { it.copy(searchText = value) }
    }

    fun submitSearch() {
        val query = _uiState.value.searchText.trim()
        _uiState.update { it.copy(query = query) }
        load(reset = true)
    }

    fun selectMcVersion(version: McVersion) {
        if (requiredMcVersion != null) return
        val loader = _uiState.value.selectedLoader
            .takeIf { it in availableLoadersFor(version) }
            ?: availableLoadersFor(version).first()
        _uiState.update {
            it.copy(
                selectedMcVersion = version,
                selectedLoader = loader,
            )
        }
        load(reset = true)
    }

    fun selectLoader(loader: ModLoader) {
        if (requiredLoader != null || loader !in availableLoadersFor(_uiState.value.selectedMcVersion)) return
        if (_uiState.value.selectedLoader == loader) return
        _uiState.update { it.copy(selectedLoader = loader) }
        load(reset = true)
    }

    fun loadMore() {
        load(reset = false)
    }

    private fun availableLoadersFor(version: McVersion): List<ModLoader> =
        requiredLoader?.let(::listOf) ?: version.loaderVersions.keys.toList()

    private fun load(reset: Boolean) {
        val state = _uiState.value
        val cursor = if (reset) null else state.nextCursor ?: return
        if (!reset && state.loadingMore) return
        if (reset) searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    nextCursor = if (reset) null else it.nextCursor,
                    errorMessage = null,
                )
            }
            try {
                val requestState = _uiState.value
                val outcome = catalog.search(
                    CatalogSearchRequest(
                        query = requestState.query,
                        target = CatalogTarget(
                            requestState.selectedMcVersion,
                            requestState.selectedLoader,
                        ),
                        sort = CatalogSort.DOWNLOADS,
                        cursor = cursor,
                    )
                ).getOrThrow()
                _uiState.update {
                    it.copy(
                        mods = if (reset) outcome.value.items else it.mods + outcome.value.items,
                        nextCursor = outcome.value.nextCursor,
                        errorMessage = if (outcome.issues.isNotEmpty()) {
                            "部分目录信息暂时不可用，已显示可用结果"
                        } else {
                            it.errorMessage
                        },
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                lgr.warn(cause) { "加载模组目录失败" }
                _uiState.update {
                    it.copy(
                        mods = if (reset) emptyList() else it.mods,
                        nextCursor = if (reset) null else it.nextCursor,
                        errorMessage = "加载模组失败，请稍后重试",
                    )
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    _uiState.update { it.copy(loading = false, loadingMore = false) }
                }
            }
        }
    }
}
