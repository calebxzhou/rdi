package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startHostPlay
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.screen.HostKind
import calebxzhou.rdi.client.ui.screen.HostTarget
import calebxzhou.rdi.client.ui.screen.UnifiedHostBrief
import calebxzhou.rdi.client.ui.screen.allHostSourcesEnded
import calebxzhou.rdi.client.ui.screen.mergeUnifiedHosts
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.serdesJson
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

data class HostListUiState(
    val hosts: List<UnifiedHostBrief> = emptyList(),
    val initialLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val launchingHost: HostTarget? = null,
    val deletingHost: HostTarget? = null,
    val errorMessage: String? = null,
    val okMessage: String? = null,
    val retryLoadOnError: Boolean = false,
)

sealed interface HostListEvent {
    data class OpenMcPlay(val args: McPlayArgs) : HostListEvent
    data class NeedInstall(val result: StartPlayResult.NeedInstall) : HostListEvent
    data class OpenTaskList(val runId: String) : HostListEvent
    data class HostDeleted(val target: HostTarget) : HostListEvent
}

interface HostListGateway {
    suspend fun loadLegacyMine(): Result<List<Host.BriefVo>>
    suspend fun loadLegacyUnavailable(page: Int): Result<List<Host.BriefVo>>
    suspend fun deleteHost(target: HostTarget, deleteWorld: Boolean): Result<Unit>
    suspend fun startHost(host: UnifiedHostBrief): Result<StartPlayResult>
    fun rememberLastPlayed(target: HostTarget, hostName: String): Result<Unit>
}

class RdiHostListGateway : HostListGateway {
    override suspend fun loadLegacyMine(): Result<List<Host.BriefVo>> = resultOf {
        val response = server.makeRequest<List<Host.BriefVo>>("host/my")
        if (!response.ok) throw RequestError(response.msg)
        response.data.orEmpty()
    }

    override suspend fun loadLegacyUnavailable(page: Int): Result<List<Host.BriefVo>> = resultOf {
        val response = server.makeRequest<List<Host.BriefVo>>("host/list/$page")
        if (!response.ok) throw RequestError(response.msg)
        response.data.orEmpty()
    }

    override suspend fun deleteHost(target: HostTarget, deleteWorld: Boolean): Result<Unit> = resultOf {
        val path = "${target.apiRoot}/${target.id}"
        if (target.kind != HostKind.Legacy) {
            throw RequestError("该房间类型暂不可用")
        }
        val body = serdesJson.encodeToString(Host.DeleteDto(deleteWorld))
        val response = server.makeRequest<Unit>(path, HttpMethod.Delete) {
            body?.let { setBody(it) }
        }
        if (!response.ok) throw RequestError(response.msg)
    }

    override suspend fun startHost(host: UnifiedHostBrief): Result<StartPlayResult> = resultOf {
        if (host.target.kind != HostKind.Legacy) {
            throw RequestError("该房间类型暂不可用")
        }
        startHostPlay(host.target.id).getOrThrow()
    }

    override fun rememberLastPlayed(target: HostTarget, hostName: String): Result<Unit> = runCatching {
        LocalCredentials.read().updateLastPlayHost(target.id, hostName)
    }
}

class HostListViewModel(
    private val gateway: HostListGateway,
    private val useMockData: Boolean = Const.USE_MOCK_DATA,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HostListUiState())
    val uiState: StateFlow<HostListUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<HostListEvent>(Channel.BUFFERED)
    val events: Flow<HostListEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var generation = 0L
    private var sourceState = HostListSourceState()
    private var nextErrorId = 0L
    private var activeErrorId: Long? = null
    private var retryLoadAfterCurrentErrorId: Long? = null
    private var pendingOkMessage: String? = null

    init {
        refresh()
    }

    fun refresh() {
        val currentGeneration = generation + 1
        val currentSourceState = HostListSourceState()
        generation = currentGeneration
        sourceState = currentSourceState
        loadJob?.cancel()
        loadJob = null
        activeErrorId = null
        retryLoadAfterCurrentErrorId = null
        pendingOkMessage = null
        _uiState.value = HostListUiState(initialLoading = true)

        if (useMockData) {
            _uiState.value = HostListUiState(
                hosts = generateMockHosts().map(UnifiedHostBrief::fromLegacy),
                initialLoading = false,
            )
            currentSourceState.legacyAvailableEnded = true
            currentSourceState.legacyUnavailableEnded = true
            return
        }

        loadJob = viewModelScope.launch {
            loadMoreInternal(currentGeneration, currentSourceState)
        }
    }

    fun loadMore() {
        if (useMockData || loadJob?.isActive == true || _uiState.value.retryLoadOnError) return
        val currentGeneration = generation
        val currentSourceState = sourceState
        if (allSourcesEnded(currentSourceState)) return
        loadJob = viewModelScope.launch {
            loadMoreInternal(currentGeneration, currentSourceState)
        }
    }

    private suspend fun loadMoreInternal(
        currentGeneration: Long,
        currentSourceState: HostListSourceState,
    ) {
        if (!isCurrent(currentGeneration, currentSourceState)) return
        _uiState.update { it.copy(loadingMore = true) }
        val loaded = mutableListOf<UnifiedHostBrief>()
        var retryAfterFinish = false
        try {
            if (!currentSourceState.legacyAvailableEnded) {
                val result = resultOf { gateway.loadLegacyMine().getOrThrow() }
                if (!isCurrent(currentGeneration, currentSourceState)) return
                if (result.isSuccess) {
                    loaded += result.getOrThrow().map(UnifiedHostBrief::fromLegacy)
                    currentSourceState.legacyAvailableEnded = true
                } else {
                    showSourceError("加载我的房间失败", result.exceptionOrNull() ?: IllegalStateException("未知错误"))
                }
            }
            if (!currentSourceState.legacyUnavailableEnded) {
                val result = resultOf {
                    gateway.loadLegacyUnavailable(currentSourceState.legacyUnavailablePage).getOrThrow()
                }
                if (!isCurrent(currentGeneration, currentSourceState)) return
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    if (data.isEmpty()) {
                        currentSourceState.legacyUnavailableEnded = true
                    } else {
                        loaded += data.map(UnifiedHostBrief::fromLegacy)
                        currentSourceState.legacyUnavailablePage++
                    }
                } else {
                    showSourceError("加载公开房间失败", result.exceptionOrNull() ?: IllegalStateException("未知错误"))
                }
            }
            if (isCurrent(currentGeneration, currentSourceState)) {
                _uiState.update { it.copy(hosts = mergeUnifiedHosts(it.hosts, loaded)) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (cause: Throwable) {
            if (isCurrent(currentGeneration, currentSourceState)) {
                showSourceError("加载房间失败", cause)
            }
        } finally {
            if (isCurrent(currentGeneration, currentSourceState)) {
                _uiState.update { it.copy(initialLoading = false, loadingMore = false) }
                val deferredErrorId = retryLoadAfterCurrentErrorId
                retryLoadAfterCurrentErrorId = null
                if (deferredErrorId != null && activeErrorId == null) {
                    _uiState.update { it.copy(retryLoadOnError = false) }
                    retryAfterFinish = true
                }
                loadJob = null
            }
            if (retryAfterFinish && isCurrent(currentGeneration, currentSourceState)) loadMore()
        }
    }

    fun startHost(host: UnifiedHostBrief) {
        if (_uiState.value.launchingHost != null) return
        val target = host.target
        _uiState.update { it.copy(launchingHost = target) }
        viewModelScope.launch {
            try {
                resultOf { gateway.startHost(host).getOrThrow() }.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is StartPlayResult.Ready -> {
                                gateway.rememberLastPlayed(target, host.name).getOrThrow()
                                eventChannel.trySend(HostListEvent.OpenMcPlay(outcome.args))
                            }
                            is StartPlayResult.NeedMod -> showError(
                                "房间缺少必要Mod：${outcome.modSlugs.joinToString("、")}",
                            )
                            is StartPlayResult.NeedInstall -> eventChannel.trySend(HostListEvent.NeedInstall(outcome))
                            is StartPlayResult.Installing -> {
                                showError("整合包正在下载，请等待下载完成后再启动")
                                eventChannel.trySend(HostListEvent.OpenTaskList(outcome.runId))
                            }
                        }
                    },
                    onFailure = { showError(it.message ?: "无法开始游玩") },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                showError(cause.message ?: "无法开始游玩")
            } finally {
                _uiState.update { state ->
                    if (state.launchingHost == target) state.copy(launchingHost = null) else state
                }
            }
        }
    }

    fun deleteHost(host: UnifiedHostBrief, deleteWorld: Boolean) {
        if (_uiState.value.deletingHost != null) return
        val target = host.target
        _uiState.update { it.copy(deletingHost = target) }
        viewModelScope.launch {
            try {
                resultOf { gateway.deleteHost(target, deleteWorld).getOrThrow() }.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                hosts = it.hosts.filterNot { brief -> brief.target == target },
                            )
                        }
                        showOk("已删除")
                        eventChannel.trySend(HostListEvent.HostDeleted(target))
                    },
                    onFailure = { showError(it.message ?: "删除房间失败") },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                showError(cause.message ?: "删除房间失败")
            } finally {
                _uiState.update { state ->
                    if (state.deletingHost == target) state.copy(deletingHost = null) else state
                }
            }
        }
    }

    fun clearError() {
        val retry = _uiState.value.retryLoadOnError
        val dismissedErrorId = activeErrorId
        activeErrorId = null
        _uiState.update {
            it.copy(
                errorMessage = null,
                retryLoadOnError = false,
                okMessage = pendingOkMessage,
            )
        }
        if (retry && dismissedErrorId != null) {
            if (loadJob?.isActive == true) retryLoadAfterCurrentErrorId = dismissedErrorId else loadMore()
        }
    }

    fun clearOkMessage() {
        pendingOkMessage = null
        _uiState.update { it.copy(okMessage = null) }
    }

    fun showError(message: String) {
        activeErrorId = ++nextErrorId
        retryLoadAfterCurrentErrorId = null
        _uiState.update { it.copy(errorMessage = message, okMessage = null, retryLoadOnError = false) }
    }

    private fun showSourceError(prefix: String, cause: Throwable) {
        activeErrorId = ++nextErrorId
        _uiState.update {
            it.copy(
                errorMessage = "$prefix：${cause.message ?: "未知错误"}",
                okMessage = null,
                retryLoadOnError = true,
            )
        }
    }

    private fun showOk(message: String) {
        pendingOkMessage = message
        if (_uiState.value.errorMessage == null) {
            _uiState.update { it.copy(okMessage = message) }
        }
    }

    private fun allSourcesEnded(state: HostListSourceState): Boolean = allHostSourcesEnded(
        state.legacyAvailableEnded,
        state.legacyUnavailableEnded,
    )

    private fun isCurrent(currentGeneration: Long, currentSourceState: HostListSourceState): Boolean =
        generation == currentGeneration && sourceState === currentSourceState
}

private data class HostListSourceState(
    var legacyAvailableEnded: Boolean = false,
    var legacyUnavailablePage: Int = 0,
    var legacyUnavailableEnded: Boolean = false,
)

private fun generateMockHosts(): List<Host.BriefVo> = List(20) { index ->
    val base = Host.BriefVo.TEST
    base.copy(name = "${base.name} #${index + 1}", port = base.port + index)
}

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (cause: Throwable) {
    Result.failure(cause)
}
