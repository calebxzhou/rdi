package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzau.rdi.client.lgr
import calebxzau.rdi.common.model.BaseWorld
import calebxzhou.rdi.common.model.Modpack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ModpackVersionBaseWorldManageUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val version: Modpack.Version? = null,
    val worlds: List<BaseWorld> = emptyList(),
    val draftId: UUID? = null,
    val draftRequired: Boolean = false,
    val errorMessage: String? = null,
    val dataLoaded: Boolean = false,
) {
    val selectedWorld: BaseWorld?
        get() = draftId?.let { id -> worlds.firstOrNull { it.id == id } }

    val selectedWorldUnavailable: Boolean
        get() = draftId != null && selectedWorld == null && dataLoaded

    val canSave: Boolean
        get() = dataLoaded && !loading && !saving && version != null &&
            (draftId == null || selectedWorld != null)
}

sealed interface ModpackVersionBaseWorldManageEvent {
    data object Saved : ModpackVersionBaseWorldManageEvent
}

class ModpackVersionBaseWorldManageViewModel(
    private val gateway: ModpackInfoGateway,
    private val modpackId: String,
    private val verName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModpackVersionBaseWorldManageUiState())
    val uiState: StateFlow<ModpackVersionBaseWorldManageUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ModpackVersionBaseWorldManageEvent>(Channel.BUFFERED)
    val events: Flow<ModpackVersionBaseWorldManageEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        reload()
    }

    fun reload() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                dataLoaded = false,
                errorMessage = null,
                version = null,
                worlds = emptyList(),
                draftId = null,
                draftRequired = false,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { gateway.loadDetail(modpackId).getOrThrow() }
                val version = detail?.versions?.firstOrNull { it.name == verName }
                    ?: throw IllegalStateException("未找到版本 V$verName")
                val worlds = withContext(Dispatchers.IO) { gateway.loadReadyBaseWorlds().getOrThrow() }
                if (!isCurrent(generation)) return@launch
                _uiState.update {
                    it.copy(
                        loading = false,
                        dataLoaded = true,
                        version = version,
                        worlds = worlds,
                        draftId = version.baseWorld?.id,
                        draftRequired = version.baseWorld?.required == true,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                if (!isCurrent(generation)) return@launch
                lgr.warn(cause) { "加载版本地图模板设置失败" }
                _uiState.update {
                    it.copy(
                        loading = false,
                        dataLoaded = false,
                        errorMessage = cause.message ?: "加载地图模板失败",
                    )
                }
            }
        }
    }

    fun select(worldId: UUID) {
        val state = _uiState.value
        if (state.loading || state.saving) return
        if (state.draftId == worldId) {
            _uiState.update { it.copy(draftId = null, draftRequired = false, errorMessage = null) }
            return
        }
        if (state.worlds.none { it.id == worldId }) return
        _uiState.update { it.copy(draftId = worldId, errorMessage = null) }
    }

    fun setRequired(required: Boolean) {
        val state = _uiState.value
        if (state.loading || state.saving || (required && state.draftId == null)) return
        _uiState.update { it.copy(draftRequired = required, errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val binding = state.draftId?.let { Modpack.Version.BaseWorldBinding(it, state.draftRequired) }
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    gateway.mutate(
                        modpackId,
                        ModpackInfoMutation.UpdateVersionBaseWorld(verName, binding),
                    ).getOrThrow()
                }
                _uiState.update { it.copy(saving = false) }
                eventChannel.send(ModpackVersionBaseWorldManageEvent.Saved)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                lgr.warn(cause) { "更新版本地图模板失败" }
                _uiState.update {
                    it.copy(saving = false, errorMessage = "更新地图模板失败: ${cause.message ?: "未知错误"}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun isCurrent(generation: Long): Boolean = generation == loadGeneration

    override fun onCleared() {
        loadJob?.cancel()
        eventChannel.close()
        super.onCleared()
    }
}
