package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.hydrateToUiModsInBatches
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.screen.matchHostExtraModFiles
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModBatchReplaceItem
import calebxzhou.rdi.common.model.ModRef
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.displaySlugOrProject
import calebxzhou.rdi.common.model.normalizedProjectId
import calebxzhou.rdi.common.model.normalizedSlug
import calebxzhou.rdi.common.serdesJson
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import java.io.File

enum class VersionRebuildLifecycle {
    Idle,
    AwaitingBusy,
    ObservedBusy,
}

fun transitionVersionRebuildLifecycle(
    lifecycle: VersionRebuildLifecycle,
    status: Modpack.Status,
): VersionRebuildLifecycle = when (lifecycle) {
    VersionRebuildLifecycle.Idle -> VersionRebuildLifecycle.Idle
    VersionRebuildLifecycle.AwaitingBusy -> when (status) {
        Modpack.Status.WAIT, Modpack.Status.BUILDING -> VersionRebuildLifecycle.ObservedBusy
        Modpack.Status.OK, Modpack.Status.FAIL -> VersionRebuildLifecycle.AwaitingBusy
    }
    VersionRebuildLifecycle.ObservedBusy -> when (status) {
        Modpack.Status.WAIT, Modpack.Status.BUILDING -> VersionRebuildLifecycle.ObservedBusy
        Modpack.Status.OK, Modpack.Status.FAIL -> VersionRebuildLifecycle.Idle
    }
}

fun shouldApplyVersionMonitorResponse(
    currentReloadToken: Int,
    responseReloadToken: Int,
): Boolean = currentReloadToken == responseReloadToken

data class ModpackVersionInfoUiState(
    val loading: Boolean = true,
    val pack: Modpack.DetailVo? = null,
    val version: Modpack.Version? = null,
    val uiMods: List<UiMod> = emptyList(),
    val uiModsLoading: Boolean = false,
    val okMessage: String? = null,
    val errorMessage: String? = null,
    val addDialogOpen: Boolean = false,
    val addDialogLoading: Boolean = false,
    val addDialogLoadingText: String = "",
    val addDialogError: String? = null,
    val pendingAddUiMods: List<UiMod> = emptyList(),
    val selectedPendingAddKeys: Set<String> = emptySet(),
    val rejectedAddFiles: List<String> = emptyList(),
    val editDialogSaving: Boolean = false,
    val completedAction: ModpackVersionInfoAction? = null,
    val reloadToken: Int = 0,
    val rebuildLifecycle: VersionRebuildLifecycle = VersionRebuildLifecycle.Idle,
) {
    val versionActionPending: Boolean
        get() = rebuildLifecycle != VersionRebuildLifecycle.Idle
}

enum class ModpackVersionInfoAction {
    ADD,
    EDIT,
    DELETE,
}

private const val REBUILD_MONITOR_INTERVAL_MS = 1_500L
private const val MAX_REBUILD_MONITOR_POLLS = 40
private const val MAX_REBUILD_MONITOR_FAILURES = 3

sealed interface ModpackVersionInfoEvent {
    data class ShowSnackbar(val message: String) : ModpackVersionInfoEvent
    data object VersionDeleted : ModpackVersionInfoEvent
}

class ModpackVersionInfoViewModel(
    private val modCatalog: ModCatalog,
    private val gateway: ModpackInfoGateway,
    private val modpackId: String,
    private val verName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModpackVersionInfoUiState())
    val uiState: StateFlow<ModpackVersionInfoUiState> = _uiState.asStateFlow()
    private val eventChannel = kotlinx.coroutines.channels.Channel<ModpackVersionInfoEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events: kotlinx.coroutines.flow.Flow<ModpackVersionInfoEvent> = eventChannel.receiveAsFlow()

    private var reloadJob: Job? = null
    private var hydrateJob: Job? = null
    private var rebuildMonitorJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        hydrateJob?.cancel()
        val requestToken = _uiState.value.reloadToken + 1
        _uiState.update {
            it.copy(
                loading = true,
                errorMessage = null,
                uiMods = emptyList(),
                uiModsLoading = false,
                reloadToken = requestToken,
            )
        }
        reloadJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { gateway.loadDetail(modpackId).getOrThrow() }
                val currentVersion = detail?.versions?.firstOrNull { it.name == verName }
                _uiState.update { state ->
                    if (state.reloadToken != requestToken) state else state.copy(
                        pack = detail,
                        version = currentVersion,
                        uiMods = currentVersion?.mods?.toUiMods().orEmpty(),
                        uiModsLoading = currentVersion != null,
                        errorMessage = if (currentVersion == null) "未找到版本 V$verName" else null,
                        rebuildLifecycle = currentVersion?.let { version ->
                            transitionVersionRebuildLifecycle(state.rebuildLifecycle, version.status)
                        } ?: state.rebuildLifecycle,
                    )
                }
                if (currentVersion != null) {
                    hydrateJob = viewModelScope.launch {
                        try {
                            currentVersion.mods
                                .hydrateToUiModsInBatches(modCatalog)
                                .flowOn(Dispatchers.IO)
                                .collect { batch ->
                                    _uiState.update { state ->
                                        if (state.reloadToken == requestToken) state.copy(uiMods = batch) else state
                                    }
                                }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (cause: Throwable) {
                            lgr.warn(cause) { "加载版本Mod展示信息失败，将使用基础Mod数据" }
                        } finally {
                            if (currentCoroutineContext().isActive) {
                                _uiState.update { state ->
                                    if (state.reloadToken == requestToken) state.copy(uiModsLoading = false) else state
                                }
                            }
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                lgr.warn(cause) { "加载版本信息失败" }
                _uiState.update { state -> state.copy(errorMessage = "加载版本信息失败: ${cause.message}") }
            } finally {
                if (currentCoroutineContext().isActive) _uiState.update { it.copy(loading = false) }
            }
        }
        if (_uiState.value.versionActionPending && rebuildMonitorJob?.isActive != true) {
            startRebuildMonitor()
        }
    }

    fun resetAddDialog() {
        _uiState.update {
            it.copy(
                addDialogOpen = false,
                addDialogLoading = false,
                addDialogLoadingText = "",
                addDialogError = null,
                pendingAddUiMods = emptyList(),
                selectedPendingAddKeys = emptySet(),
                rejectedAddFiles = emptyList(),
            )
        }
    }

    fun matchFiles(files: List<File>, mcVersion: McVersion) {
        resetAddDialog()
        _uiState.update {
            it.copy(
                addDialogLoading = true,
                addDialogLoadingText = "正在匹配Mod...",
            )
        }
        viewModelScope.launch {
            try {
                val matchResult = matchHostExtraModFiles(modCatalog, files, mcVersion) { progress ->
                    _uiState.update { it.copy(addDialogLoadingText = progress) }
                }
                val dedupeResult = filterVersionModsForAdding(
                    candidateMods = matchResult.matchedMods.map { uiMod ->
                        if (uiMod.side == Mod.Side.UNKNOWN) {
                            uiMod.withSide(Mod.Side.BOTH)
                        } else {
                            uiMod
                        }
                    },
                    existingMods = _uiState.value.version?.mods.orEmpty(),
                )
                val acceptedUiMods = dedupeResult.acceptedMods
                val rejectedFiles = matchResult.rejectedFiles + dedupeResult.rejectedMessages
                _uiState.update {
                    it.copy(
                        addDialogLoading = false,
                        addDialogLoadingText = "",
                        pendingAddUiMods = acceptedUiMods,
                        selectedPendingAddKeys = acceptedUiMods.map(UiMod::key).toSet(),
                        rejectedAddFiles = rejectedFiles,
                        addDialogOpen = acceptedUiMods.isNotEmpty() || rejectedFiles.isNotEmpty(),
                        errorMessage = if (acceptedUiMods.isEmpty() && rejectedFiles.isEmpty()) {
                            "没有在网上搜索到这些Mod的信息"
                        } else {
                            it.errorMessage
                        },
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                lgr.warn(e) { "匹配Mod失败" }
                _uiState.update {
                    it.copy(
                        addDialogLoading = false,
                        addDialogLoadingText = "",
                        errorMessage = e.message ?: "匹配Mod失败",
                    )
                }
            }
        }
    }

    fun togglePendingAdd(uiMod: UiMod) {
        _uiState.update {
            it.copy(
                selectedPendingAddKeys = if (uiMod.key in it.selectedPendingAddKeys) {
                    it.selectedPendingAddKeys - uiMod.key
                } else {
                    it.selectedPendingAddKeys + uiMod.key
                },
            )
        }
    }

    fun changePendingAddSide(uiMod: UiMod, side: Mod.Side) {
        _uiState.update {
            it.copy(
                pendingAddUiMods = it.pendingAddUiMods.map { candidate ->
                    if (candidate.key == uiMod.key) candidate.withSide(side) else candidate
                },
            )
        }
    }

    fun addSelectedMods(targetMods: List<UiMod>) {
        if (!canEditMods()) return
        val rawTargetMods = targetMods.map(UiMod::toMod)
        _uiState.update {
            it.copy(
                addDialogLoading = true,
                addDialogLoadingText = "添加中...",
                addDialogError = null,
            )
        }
        viewModelScope.rdiRequestU(
            path = versionModsBatchPath(modpackId, verName),
            method = HttpMethod.Post,
            body = serdesJson.encodeToString(rawTargetMods),
            onOk = {
                _uiState.update {
                    it.copy(
                        okMessage = "已添加${targetMods.size}个Mod",
                        completedAction = ModpackVersionInfoAction.ADD,
                    )
                }
                resetAddDialog()
                reload()
            },
            onErr = {
                lgr.warn(it) { "添加版本Mod失败" }
                _uiState.update { state -> state.copy(addDialogError = it.message ?: "添加Mod失败") }
            },
            onDone = { _uiState.update { it.copy(addDialogLoading = false, addDialogLoadingText = "") } },
        )
    }

    fun saveEdits(replaceItems: List<ModBatchReplaceItem>) {
        if (!canEditMods()) return
        _uiState.update { it.copy(editDialogSaving = true) }
        viewModelScope.rdiRequestU(
            path = versionModsBatchPath(modpackId, verName),
            method = HttpMethod.Put,
            body = serdesJson.encodeToString(replaceItems),
            onOk = {
                _uiState.update {
                    it.copy(
                        okMessage = "已批量更新${replaceItems.size}个Mod",
                        completedAction = ModpackVersionInfoAction.EDIT,
                    )
                }
                reload()
            },
            onErr = {
                lgr.warn(it) { "更新版本Mod失败" }
                _uiState.update { state -> state.copy(errorMessage = it.message ?: "更新Mod失败") }
            },
            onDone = { _uiState.update { it.copy(editDialogSaving = false) } },
        )
    }

    fun deleteMods(targetRefs: List<ModRef>) {
        if (!canEditMods()) return
        viewModelScope.rdiRequestU(
            path = versionModsBatchPath(modpackId, verName),
            method = HttpMethod.Delete,
            body = serdesJson.encodeToString(targetRefs),
            onOk = {
                _uiState.update {
                    it.copy(
                        okMessage = "已删除${targetRefs.size}个Mod",
                        completedAction = ModpackVersionInfoAction.DELETE,
                    )
                }
                reload()
            },
            onErr = {
                lgr.warn(it) { "删除版本Mod失败" }
                _uiState.update { state -> state.copy(errorMessage = it.message ?: "删除Mod失败") }
            },
        )
    }

    fun clearOkMessage() {
        _uiState.update { it.copy(okMessage = null) }
    }

    fun clearCompletedAction() {
        _uiState.update { it.copy(completedAction = null) }
    }

    private fun canEditMods(): Boolean {
        val state = _uiState.value
        return !state.versionActionPending && state.version?.status == Modpack.Status.OK
    }

    fun deleteVersion() {
        mutateVersion(
            mutation = ModpackInfoMutation.DeleteVersion(verName),
            errorPrefix = "删除失败",
            onSuccess = { eventChannel.send(ModpackVersionInfoEvent.VersionDeleted) },
        )
    }

    fun rebuildVersion() {
        val currentState = _uiState.value
        val currentVersion = currentState.version ?: return
        if (currentState.versionActionPending ||
            currentVersion.status == Modpack.Status.WAIT ||
            currentVersion.status == Modpack.Status.BUILDING
        ) return
        _uiState.update { it.copy(rebuildLifecycle = VersionRebuildLifecycle.AwaitingBusy) }
        mutateVersion(
            mutation = ModpackInfoMutation.RebuildVersion(verName),
            errorPrefix = "重构失败",
            onSuccess = {
                eventChannel.send(ModpackVersionInfoEvent.ShowSnackbar("提交请求了 完事了发信箱告诉你"))
                reload()
                startRebuildMonitor()
            },
            onFailure = { _uiState.update { it.copy(rebuildLifecycle = VersionRebuildLifecycle.Idle) } },
        )
    }

    override fun onCleared() {
        rebuildMonitorJob?.cancel()
        eventChannel.close()
        super.onCleared()
    }

    private fun startRebuildMonitor() {
        rebuildMonitorJob?.cancel()
        rebuildMonitorJob = viewModelScope.launch {
            var polls = 0
            var failures = 0
            while (isActive && _uiState.value.versionActionPending && polls < MAX_REBUILD_MONITOR_POLLS) {
                delay(REBUILD_MONITOR_INTERVAL_MS)
                if (!isActive || !_uiState.value.versionActionPending) break
                polls++
                try {
                    val monitorToken = _uiState.value.reloadToken
                    val detail = withContext(Dispatchers.IO) {
                        gateway.loadDetail(modpackId).getOrThrow()
                    }
                    val currentVersion = detail?.versions?.firstOrNull { it.name == verName }
                    if (currentVersion == null) {
                        _uiState.update { state ->
                            if (shouldApplyVersionMonitorResponse(state.reloadToken, monitorToken)) {
                                state.copy(errorMessage = "未找到版本 V$verName")
                            } else state
                        }
                    } else {
                        _uiState.update { state ->
                            if (!shouldApplyVersionMonitorResponse(state.reloadToken, monitorToken)) state else {
                                state.copy(
                                    pack = detail,
                                    version = currentVersion,
                                    rebuildLifecycle = transitionVersionRebuildLifecycle(
                                        state.rebuildLifecycle,
                                        currentVersion.status,
                                    ),
                                )
                            }
                        }
                    }
                    failures = 0
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (cause: Throwable) {
                    failures++
                    lgr.warn(cause) { "检查版本重构状态失败" }
                    if (failures >= MAX_REBUILD_MONITOR_FAILURES) {
                        eventChannel.send(ModpackVersionInfoEvent.ShowSnackbar("暂时无法确认重构状态，将继续锁定操作"))
                        break
                    }
                }
            }
            if (polls >= MAX_REBUILD_MONITOR_POLLS && _uiState.value.versionActionPending) {
                eventChannel.send(ModpackVersionInfoEvent.ShowSnackbar("重构状态暂未确认，操作仍保持锁定"))
            }
        }
    }

    private fun mutateVersion(
        mutation: ModpackInfoMutation,
        errorPrefix: String,
        onSuccess: suspend () -> Unit,
        onFailure: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { gateway.mutate(modpackId, mutation).getOrThrow() }
                onSuccess()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                onFailure?.invoke()
                reportError(errorPrefix, cause)
            }
        }
    }

    private fun reportError(prefix: String, cause: Throwable) {
        lgr.warn(cause) { prefix }
        _uiState.update { it.copy(errorMessage = "$prefix: ${cause.message ?: "未知错误"}") }
    }
}

/** Source compatibility for persisted callers; new code should use ModpackVersionInfoRoute*. */
typealias ModpackVersionEditUiState = ModpackVersionInfoUiState
typealias ModpackVersionEditAction = ModpackVersionInfoAction
typealias ModpackVersionEditViewModel = ModpackVersionInfoViewModel

private data class VersionModAddFilterResult(
    val acceptedMods: List<UiMod>,
    val rejectedMessages: List<String>,
)

private fun filterVersionModsForAdding(
    candidateMods: List<UiMod>,
    existingMods: List<Mod>,
): VersionModAddFilterResult {
    val existingKeys = existingMods.map(::versionModIdentity)
        .filter { it.isNotBlank() }
        .toSet()
    val acceptedMods = mutableListOf<UiMod>()
    val pendingKeys = mutableSetOf<String>()
    val rejectedMessages = mutableListOf<String>()

    candidateMods.forEach { uiMod ->
        val mod = uiMod.mod
        val key = versionModIdentity(mod)
        if (key.isBlank()) {
            acceptedMods += uiMod
            return@forEach
        }
        when {
            key in existingKeys -> rejectedMessages += "${mod.displaySlugOrProject}：版本中已存在同名Mod"
            !pendingKeys.add(key) -> rejectedMessages += "${mod.displaySlugOrProject}：本次选择中已有同名Mod"
            else -> acceptedMods += uiMod
        }
    }

    return VersionModAddFilterResult(
        acceptedMods = acceptedMods,
        rejectedMessages = rejectedMessages.distinct(),
    )
}

private fun versionModIdentity(mod: Mod): String = mod.normalizedSlug.ifBlank {
    mod.normalizedProjectId.lowercase()
}

private fun versionModsPath(modpackId: String, verName: String): String =
    "modpack/$modpackId/version/${verName.encodeURLPathPart()}/mods"

private fun versionModsBatchPath(modpackId: String, verName: String): String =
    "${versionModsPath(modpackId, verName)}/batch"
