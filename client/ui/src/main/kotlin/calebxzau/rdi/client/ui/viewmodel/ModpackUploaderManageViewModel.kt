package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.net.json
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

enum class ModpackUploaderMode {
    EVERYONE,
    AUTHOR_ONLY,
    SELECTED_PLAYERS,
}

data class ModpackUploaderManageUiState(
    val loading: Boolean = true,
    val mode: ModpackUploaderMode = ModpackUploaderMode.EVERYONE,
    val uploaders: List<RAccount.Dto> = emptyList(),
    val selectedIds: List<ObjectId> = emptyList(),
    val resolving: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val dirty: Boolean = false,
)

sealed interface ModpackUploaderManageEvent {
    data object Saved : ModpackUploaderManageEvent
}

interface ModpackUploaderManageGateway {
    suspend fun load(modpackId: String): Result<Modpack.UploaderPolicyVo>
    suspend fun resolve(modpackId: String, playerNameOrQq: String): Result<RAccount.Dto>
    suspend fun save(modpackId: String, allowUploaderIds: List<ObjectId>?): Result<Unit>
}

class RdiModpackUploaderManageGateway : ModpackUploaderManageGateway {
    override suspend fun load(modpackId: String): Result<Modpack.UploaderPolicyVo> = resultOf {
        val response = server.makeRequest<Modpack.UploaderPolicyVo>("modpack/$modpackId/uploaders")
        response.data ?: throw RequestError(response.msg)
    }

    override suspend fun resolve(modpackId: String, playerNameOrQq: String): Result<RAccount.Dto> = resultOf {
        val response = server.makeRequest<RAccount.Dto>(
            path = "modpack/$modpackId/uploaders/resolve",
            method = HttpMethod.Post,
        ) {
            json()
            setBody(Modpack.UploaderResolveDto(playerNameOrQq).json)
        }
        response.data ?: throw RequestError(response.msg)
    }

    override suspend fun save(modpackId: String, allowUploaderIds: List<ObjectId>?): Result<Unit> = resultOf {
        val response = server.makeRequest<Unit>(
            path = "modpack/$modpackId/uploaders",
            method = HttpMethod.Put,
        ) {
            json()
            setBody(Modpack.UploaderPolicyUpdateDto(allowUploaderIds).json)
        }
        if (!response.ok) throw RequestError(response.msg)
    }
}

class ModpackUploaderManageViewModel(
    private val modpackId: String,
    private val gateway: ModpackUploaderManageGateway = RdiModpackUploaderManageGateway(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModpackUploaderManageUiState())
    val uiState: StateFlow<ModpackUploaderManageUiState> = _uiState.asStateFlow()
    private val eventsChannel = Channel<ModpackUploaderManageEvent>(Channel.BUFFERED)
    val events: Flow<ModpackUploaderManageEvent> = eventsChannel.receiveAsFlow()

    private var generation = 0L
    private var loadJob: Job? = null
    private var resolveJob: Job? = null
    private var saveJob: Job? = null
    private var savedWire: List<ObjectId>? = null
    private var selectedDraft: List<ObjectId> = emptyList()

    init {
        reload()
    }

    fun reload() {
        val requestGeneration = ++generation
        loadJob?.cancel()
        resolveJob?.cancel()
        saveJob?.cancel()
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            gateway.load(modpackId).fold(
                onSuccess = { policy ->
                    if (requestGeneration != generation) return@fold
                    val policyIds = policy.allowUploaderIds
                    val ids = policyIds.orEmpty()
                    val mode = when (policyIds) {
                        null -> ModpackUploaderMode.EVERYONE
                        else -> if (policyIds.isEmpty()) {
                            ModpackUploaderMode.AUTHOR_ONLY
                        } else {
                            ModpackUploaderMode.SELECTED_PLAYERS
                        }
                    }
                    selectedDraft = ids
                    savedWire = policy.allowUploaderIds
                    _uiState.value = ModpackUploaderManageUiState(
                        loading = false,
                        mode = mode,
                        uploaders = policy.uploaders,
                        selectedIds = ids,
                    )
                },
                onFailure = { error ->
                    if (requestGeneration == generation) {
                        _uiState.update { it.copy(loading = false, errorMessage = error.message ?: "加载版本上传权限失败") }
                    }
                },
            )
        }
    }

    fun setMode(mode: ModpackUploaderMode) {
        if (_uiState.value.loading || _uiState.value.saving) return
        if (mode == ModpackUploaderMode.SELECTED_PLAYERS) {
            _uiState.update { it.copy(mode = mode, selectedIds = selectedDraft, dirty = wireValue(mode, selectedDraft) != savedWire, errorMessage = null) }
        } else {
            _uiState.update { it.copy(mode = mode, dirty = wireValue(mode, selectedDraft) != savedWire, errorMessage = null) }
        }
    }

    fun resolveAndAdd(rawIdentifier: String) {
        val identifier = rawIdentifier.trim()
        if (identifier.isBlank() || _uiState.value.resolving || _uiState.value.saving) {
            if (identifier.isBlank()) _uiState.update { it.copy(errorMessage = "玩家名或QQ不能为空") }
            return
        }
        val requestGeneration = generation
        resolveJob?.cancel()
        _uiState.update { it.copy(resolving = true, errorMessage = null) }
        resolveJob = viewModelScope.launch {
            gateway.resolve(modpackId, identifier).fold(
                onSuccess = { account ->
                    if (requestGeneration != generation) return@fold
                    if (account.id in selectedDraft) {
                        _uiState.update { it.copy(resolving = false, errorMessage = "该玩家已在名单中") }
                        return@fold
                    }
                    selectedDraft = selectedDraft + account.id
                    _uiState.update {
                        it.copy(
                            resolving = false,
                            selectedIds = selectedDraft,
                            uploaders = it.uploaders + account,
                            dirty = wireValue(it.mode, selectedDraft) != savedWire,
                        )
                    }
                },
                onFailure = { error ->
                    if (requestGeneration == generation) _uiState.update { it.copy(resolving = false, errorMessage = error.message ?: "找不到该玩家") }
                },
            )
        }
    }

    fun remove(id: ObjectId) {
        if (_uiState.value.loading || _uiState.value.saving) return
        selectedDraft = selectedDraft.filterNot { it == id }
        _uiState.update {
            it.copy(
                selectedIds = selectedDraft,
                uploaders = it.uploaders.filterNot { account -> account.id == id },
                dirty = wireValue(it.mode, selectedDraft) != savedWire,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving || !state.dirty) return
        if (state.mode == ModpackUploaderMode.SELECTED_PLAYERS && selectedDraft.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请至少添加一名玩家") }
            return
        }
        val wireValue = wireValue(state.mode, selectedDraft)
        val requestGeneration = ++generation
        saveJob?.cancel()
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        saveJob = viewModelScope.launch {
            gateway.save(modpackId, wireValue).fold(
                onSuccess = {
                    if (requestGeneration != generation) return@fold
                    savedWire = wireValue
                    _uiState.update { it.copy(saving = false, dirty = false) }
                    eventsChannel.send(ModpackUploaderManageEvent.Saved)
                },
                onFailure = { error ->
                    if (requestGeneration == generation) _uiState.update { it.copy(saving = false, errorMessage = error.message ?: "保存版本上传权限失败") }
                },
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun wireValue(mode: ModpackUploaderMode, selectedIds: List<ObjectId>): List<ObjectId>? = when (mode) {
        ModpackUploaderMode.EVERYONE -> null
        ModpackUploaderMode.AUTHOR_ONLY -> emptyList()
        ModpackUploaderMode.SELECTED_PLAYERS -> selectedIds
    }
}

private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (error: Throwable) {
    Result.failure(error)
}
