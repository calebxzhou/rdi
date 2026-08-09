package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.hydrateToUiMods
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class ModpackVersionEditUiState(
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
    val completedAction: ModpackVersionEditAction? = null,
    val reloadToken: Int = 0,
)

enum class ModpackVersionEditAction {
    ADD,
    EDIT,
    DELETE,
}

class ModpackVersionEditViewModel(
    private val modCatalog: ModCatalog,
    private val modpackId: String,
    private val verName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModpackVersionEditUiState())
    val uiState: StateFlow<ModpackVersionEditUiState> = _uiState.asStateFlow()

    private var reloadJob: Job? = null
    private var hydrateJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        hydrateJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                errorMessage = null,
                uiMods = emptyList(),
                uiModsLoading = false,
                reloadToken = it.reloadToken + 1,
            )
        }
        reloadJob = viewModelScope.rdiRequest<Modpack.DetailVo>(
            path = "modpack/$modpackId/detail",
            onOk = { response ->
                val detail = response.data
                val currentVersion = detail?.versions?.firstOrNull { it.name == verName }
                _uiState.update {
                    it.copy(
                        pack = detail,
                        version = currentVersion,
                        uiMods = emptyList(),
                        uiModsLoading = currentVersion != null,
                        errorMessage = if (currentVersion == null) "未找到版本 V$verName" else null,
                    )
                }
                if (currentVersion != null) {
                    hydrateJob = viewModelScope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching { currentVersion.mods.hydrateToUiMods(modCatalog) }
                                .getOrElse {
                                    lgr.warn(it) { "加载版本Mod展示信息失败，将使用基础Mod数据" }
                                    currentVersion.mods.toUiMods()
                                }
                        }
                        _uiState.update {
                            it.copy(
                                uiMods = loaded,
                                uiModsLoading = false,
                            )
                        }
                    }
                }
            },
            onErr = {
                lgr.warn(it) { "加载版本信息失败" }
                _uiState.update { state -> state.copy(errorMessage = "加载版本信息失败: ${it.message}") }
            },
            onDone = { _uiState.update { it.copy(loading = false) } },
        )
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
                        okMessage = "已添加${targetMods.size}个Mod，版本开始重构",
                        completedAction = ModpackVersionEditAction.ADD,
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
        _uiState.update { it.copy(editDialogSaving = true) }
        viewModelScope.rdiRequestU(
            path = versionModsBatchPath(modpackId, verName),
            method = HttpMethod.Put,
            body = serdesJson.encodeToString(replaceItems),
            onOk = {
                _uiState.update {
                    it.copy(
                        okMessage = "已批量更新${replaceItems.size}个Mod，版本开始重构",
                        completedAction = ModpackVersionEditAction.EDIT,
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
        viewModelScope.rdiRequestU(
            path = versionModsBatchPath(modpackId, verName),
            method = HttpMethod.Delete,
            body = serdesJson.encodeToString(targetRefs),
            onOk = {
                _uiState.update {
                    it.copy(
                        okMessage = "已删除${targetRefs.size}个Mod，版本开始重构",
                        completedAction = ModpackVersionEditAction.DELETE,
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
}

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
