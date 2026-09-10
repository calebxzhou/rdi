package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzau.rdi.client.service.BaseWorldApi
import calebxzau.rdi.client.service.BaseWorldUploadRequest
import calebxzau.rdi.client.service.BaseWorldUploadService
import calebxzau.rdi.client.service.validateBaseWorldLevelType
import calebxzau.rdi.client.service.validateBaseWorldName
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzau.rdi.client.service.baseWorldUploadTaskKey
import calebxzau.rdi.client.service.baseWorldGeneratedTaskKey
import calebxzau.rdi.client.service.currentBaseWorldApi
import calebxzau.rdi.common.model.BaseWorld
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

data class BaseWorldListUiState(
    val ownerId: String? = null,
    val worlds: List<BaseWorld> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val deletingWorldId: java.util.UUID? = null,
    val deletionInProgress: Boolean = false,
    val deletionErrorMessage: String? = null,
    val renamingWorldId: UUID? = null,
    val renameInProgress: Boolean = false,
    val renameErrorMessage: String? = null,
    val renameSuccessWorldId: UUID? = null,
)

interface BaseWorldListGateway {
    suspend fun list(ownerId: String): Result<List<BaseWorld>>
    suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit>
    suspend fun rename(ownerId: String, worldId: UUID, name: String): Result<BaseWorld>
}

class RdiBaseWorldListGateway(
    private val apiProvider: () -> BaseWorldApi = { currentBaseWorldApi() },
) : BaseWorldListGateway {
    override suspend fun list(ownerId: String): Result<List<BaseWorld>> = try {
        Result.success(apiProvider().list())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun delete(ownerId: String, worldId: java.util.UUID): Result<Unit> = try {
        apiProvider().delete(worldId)
        Result.success(Unit)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun rename(ownerId: String, worldId: UUID, name: String): Result<BaseWorld> = try {
        Result.success(apiProvider().rename(worldId, BaseWorld.NameUpdateDto(name)))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

class BaseWorldListViewModel(
    private val gateway: BaseWorldListGateway = RdiBaseWorldListGateway(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(BaseWorldListUiState())
    val uiState: StateFlow<BaseWorldListUiState> = _uiState.asStateFlow()

    private var refreshGeneration = 0L
    private var loadJob: Job? = null
    private var deletionJob: Job? = null
    private var renameJob: Job? = null
    private data class OwnerDeletionState(
        val reconciled: MutableSet<UUID> = mutableSetOf(),
        val renamed: MutableMap<UUID, BaseWorld> = mutableMapOf(),
    )
    private val deletionStates = mutableMapOf<String, OwnerDeletionState>()

    private fun deletionState(ownerId: String): OwnerDeletionState =
        deletionStates.getOrPut(ownerId) { OwnerDeletionState() }

    fun refresh(ownerId: String) {
        val ownerChanged = _uiState.value.ownerId != ownerId
        val requestGeneration = ++refreshGeneration
        loadJob?.cancel()
        _uiState.update { state ->
            if (ownerChanged) {
                BaseWorldListUiState(
                    ownerId = ownerId,
                    loading = true,
                    deletionInProgress = deletionJob?.isActive == true,
                )
            } else {
                state.copy(ownerId = ownerId, loading = true, errorMessage = null)
            }
        }
        loadJob = viewModelScope.launch {
            try {
                val result = gateway.list(ownerId)
                if (requestGeneration != refreshGeneration || _uiState.value.ownerId != ownerId) return@launch
                result.fold(
                    onSuccess = { worlds ->
                        _uiState.update { state ->
                            state.copy(
                                ownerId = ownerId,
                                worlds = worlds
                                    .filterNot { it.id in deletionState(ownerId).reconciled }
                                    .map { deletionState(ownerId).renamed[it.id] ?: it },
                                loading = false,
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                ownerId = ownerId,
                                loading = false,
                                errorMessage = error.message ?: "加载地图模板失败",
                            )
                        }
                    },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                if (requestGeneration == refreshGeneration && _uiState.value.ownerId == ownerId) {
                    _uiState.update {
                        it.copy(
                            ownerId = ownerId,
                            loading = false,
                            errorMessage = error.message ?: "加载地图模板失败",
                        )
                    }
                }
            }
        }
    }

    fun delete(ownerId: String, worldId: java.util.UUID) {
        val currentState = _uiState.value
        if (currentState.ownerId != ownerId || currentState.deletionInProgress || deletionJob?.isActive == true) return
        _uiState.update {
            it.copy(
                deletingWorldId = worldId,
                deletionInProgress = true,
                deletionErrorMessage = null,
            )
        }
        deletionJob = viewModelScope.launch {
            val result = try {
                gateway.delete(ownerId, worldId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = {
                    deletionState(ownerId).reconciled += worldId
                    if (_uiState.value.ownerId == ownerId) {
                        _uiState.update {
                            it.copy(
                                worlds = it.worlds.filterNot { world -> world.id == worldId },
                                deletingWorldId = null,
                                deletionInProgress = false,
                                deletionErrorMessage = null,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(deletionInProgress = false) }
                    }
                },
                onFailure = { error ->
                    if (_uiState.value.ownerId == ownerId) {
                        _uiState.update {
                            it.copy(
                                deletingWorldId = null,
                                deletionInProgress = false,
                                deletionErrorMessage = error.message ?: "删除地图模板失败",
                            )
                        }
                    } else {
                        _uiState.update { it.copy(deletionInProgress = false) }
                    }
                },
            )
        }
    }

    fun prepareDeletion() {
        if (_uiState.value.deletingWorldId == null) {
            _uiState.update { it.copy(deletionErrorMessage = null) }
        }
    }

    fun rename(ownerId: String, worldId: UUID, name: String) {
        val currentState = _uiState.value
        if (currentState.ownerId != ownerId || currentState.renameInProgress || renameJob?.isActive == true) return
        if (currentState.worlds.none { it.id == worldId }) return
        val validation = validateBaseWorldName(name)
        if (validation.isFailure) {
            _uiState.update {
                it.copy(renameErrorMessage = validation.exceptionOrNull()?.message ?: "地图模板名称无效")
            }
            return
        }
        _uiState.update {
            it.copy(renamingWorldId = worldId, renameInProgress = true, renameErrorMessage = null, renameSuccessWorldId = null)
        }
        renameJob = viewModelScope.launch {
            val result = try {
                gateway.rename(ownerId, worldId, name)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { renamed ->
                    deletionState(ownerId).renamed[worldId] = renamed
                    if (_uiState.value.ownerId == ownerId) {
                        _uiState.update {
                            it.copy(
                                worlds = it.worlds.map { listed -> if (listed.id == worldId) renamed else listed },
                                renamingWorldId = null,
                                renameInProgress = false,
                                renameErrorMessage = null,
                                renameSuccessWorldId = worldId,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(renameInProgress = false, renamingWorldId = null) }
                    }
                },
                onFailure = { error ->
                    if (_uiState.value.ownerId == ownerId) {
                        _uiState.update {
                            it.copy(
                                renameInProgress = false,
                                renameErrorMessage = error.message ?: "修改地图模板名称失败",
                            )
                        }
                    } else {
                        _uiState.update { it.copy(renameInProgress = false, renamingWorldId = null) }
                    }
                },
            )
        }
    }

    fun clearRenameError() {
        _uiState.update { it.copy(renameErrorMessage = null) }
    }

    fun clearRenameSuccess() {
        _uiState.update { it.copy(renameSuccessWorldId = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

enum class BaseWorldLevelTypeChoice {
    Normal,
    Flat,
    Skyblock,
    Custom,
}

data class BaseWorldUploadUiState(
    val ownerId: String? = null,
    val directory: File? = null,
    val autogeneratedName: String? = null,
    val name: String = "",
    val levelType: String = "minecraft:normal",
    val levelTypeChoice: BaseWorldLevelTypeChoice = BaseWorldLevelTypeChoice.Normal,
    val customLevelTypeText: String = "",
    val customLevelTypeError: String? = null,
    val generatorSettings: String = "",
    val selectingDirectory: Boolean = false,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = validateBaseWorldName(name).isSuccess &&
            validateBaseWorldLevelType(levelType).isSuccess &&
            !selectingDirectory &&
            !submitting
}

fun validateBaseWorldDirectory(directory: File): Result<Unit> = runCatching {
    require(directory.exists()) { "选择的路径不存在" }
    require(directory.isDirectory) { "请选择地图文件夹" }
    val levelDat = directory.resolve("level.dat")
    require(levelDat.isFile) { "不是有效的MC地图文件夹" }
    require(levelDat.length() > 0L) { "没有成功读取地图摘要文件" }
}

fun validateBaseWorldGeneratorSettings(value: String): Result<Unit> = runCatching {
    if (value.isBlank()) return@runCatching
    require(Json.parseToJsonElement(value) is JsonObject) {
        "生成器设置必须是JSON对象"
    }
}

interface BaseWorldUploadSubmitter {
    fun submit(ownerId: String, request: BaseWorldUploadRequest): Result<String>
}

class RdiBaseWorldUploadSubmitter(
    private val apiProvider: () -> BaseWorldApi = { currentBaseWorldApi() },
    private val submitTask: (calebxzhou.rdi.common.model.Task2, String) -> String = { task, key ->
        ClientTaskManager.submit(task, key)
    },
) : BaseWorldUploadSubmitter {
    override fun submit(ownerId: String, request: BaseWorldUploadRequest): Result<String> = runCatching {
        val api = apiProvider()
        val task = BaseWorldUploadService(api).uploadTask(request)
        val key = request.directory?.let { baseWorldUploadTaskKey(ownerId, it) }
            ?: baseWorldGeneratedTaskKey(ownerId, request.name, request.levelType, request.generatorSettings)
        submitTask(task, key)
    }
}

class BaseWorldUploadViewModel(
    private val picker: suspend (String) -> File? = {
        calebxzau.rdi.client.ui.pickLocalDirectory(it)
    },
    private val directoryValidator: suspend (File) -> Result<Unit> = { directory ->
        withContext(Dispatchers.IO) { validateBaseWorldDirectory(directory) }
    },
    private val submitter: BaseWorldUploadSubmitter = RdiBaseWorldUploadSubmitter(),
    private val isRunActive: (String) -> Boolean = { runId ->
        ClientTaskManager.entry(runId)?.status?.isTerminal == false
    },
    private val accountIdProvider: () -> String = {
        AccountSessionStore.current._id.toHexString()
    },
) : ViewModel() {
    private val _uiState = MutableStateFlow(BaseWorldUploadUiState())
    val uiState: StateFlow<BaseWorldUploadUiState> = _uiState.asStateFlow()

    private var selectionGeneration = 0L
    private var activeSubmissionKey: String? = null
    private var activeRunId: String? = null

    fun setOwner(ownerId: String) {
        if (_uiState.value.ownerId == ownerId) return
        selectionGeneration++
        activeSubmissionKey = null
        activeRunId = null
        _uiState.value = BaseWorldUploadUiState(ownerId = ownerId)
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun selectLevelType(choice: BaseWorldLevelTypeChoice) {
        when (choice) {
            BaseWorldLevelTypeChoice.Normal -> _uiState.update {
                it.copy(
                    levelType = "minecraft:normal",
                    levelTypeChoice = choice,
                    customLevelTypeError = null,
                    errorMessage = null,
                )
            }

            BaseWorldLevelTypeChoice.Flat -> _uiState.update {
                it.copy(
                    levelType = "minecraft:flat",
                    levelTypeChoice = choice,
                    customLevelTypeError = null,
                    errorMessage = null,
                )
            }

            BaseWorldLevelTypeChoice.Skyblock -> _uiState.update {
                it.copy(
                    levelType = "skyblockbuilder:skyblock",
                    levelTypeChoice = choice,
                    customLevelTypeError = null,
                    errorMessage = null,
                )
            }

            BaseWorldLevelTypeChoice.Custom -> Unit
        }
    }

    fun beginCustomLevelType() {
        _uiState.update {
            it.copy(
                customLevelTypeText = if (it.levelTypeChoice == BaseWorldLevelTypeChoice.Custom) {
                    it.levelType
                } else {
                    ""
                },
                customLevelTypeError = null,
                errorMessage = null,
            )
        }
    }

    fun updateCustomLevelTypeText(value: String) {
        _uiState.update {
            it.copy(
                customLevelTypeText = value,
                customLevelTypeError = null,
                errorMessage = null,
            )
        }
    }

    fun applyCustomLevelType(): Boolean {
        val value = _uiState.value.customLevelTypeText.trim()
        val validation = validateBaseWorldLevelType(value)
        val error = validation.exceptionOrNull()?.message
        if (error != null) {
            _uiState.update { it.copy(customLevelTypeError = error) }
            return false
        }
        _uiState.update {
            it.copy(
                levelType = value,
                levelTypeChoice = BaseWorldLevelTypeChoice.Custom,
                customLevelTypeText = value,
                customLevelTypeError = null,
                errorMessage = null,
            )
        }
        return true
    }

    fun cancelCustomLevelType() {
        _uiState.update { it.copy(customLevelTypeError = null) }
    }

    fun updateGeneratorSettings(value: String) {
        _uiState.update { it.copy(generatorSettings = value, errorMessage = null) }
    }

    fun selectDirectory() {
        if (_uiState.value.selectingDirectory || _uiState.value.submitting) return
        val requestGeneration = ++selectionGeneration
        val ownerAtStart = _uiState.value.ownerId
        _uiState.update { it.copy(selectingDirectory = true, errorMessage = null) }
        viewModelScope.launch {
            val selected = try {
                picker("选择地图文件夹")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                if (requestGeneration == selectionGeneration && ownerAtStart == _uiState.value.ownerId) {
                    _uiState.update {
                        it.copy(
                            selectingDirectory = false,
                            errorMessage = error.message ?: "选择地图文件夹失败",
                        )
                    }
                }
                return@launch
            }
            if (requestGeneration != selectionGeneration || ownerAtStart != _uiState.value.ownerId) return@launch
            if (selected == null) {
                _uiState.update { it.copy(selectingDirectory = false) }
                return@launch
            }
            val validation = try {
                directoryValidator(selected)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (requestGeneration != selectionGeneration || ownerAtStart != _uiState.value.ownerId) return@launch
            validation.fold(
                onSuccess = {
                    _uiState.update { state ->
                        val oldAutoName = state.autogeneratedName
                        val shouldAutofill = state.name.isBlank() || state.name == oldAutoName
                        state.copy(
                            directory = selected,
                            autogeneratedName = selected.name,
                            name = if (shouldAutofill) selected.name else state.name,
                            selectingDirectory = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            selectingDirectory = false,
                            errorMessage = error.message ?: "地图文件夹无效",
                        )
                    }
                },
            )
        }
    }

    fun submit(ownerId: String = _uiState.value.ownerId ?: accountIdProvider()): String? {
        val state = _uiState.value
        if (state.selectingDirectory) return showSubmitError("正在选择地图文件夹")
        val normalizedOwner = ownerId.trim()
        if (normalizedOwner.isBlank()) return showSubmitError("当前账号无效")
        if (state.ownerId != normalizedOwner) return showSubmitError("账号已变化，请重新提交")
        val nameResult = validateBaseWorldName(state.name)
        if (nameResult.isFailure) {
            return showSubmitError(nameResult.exceptionOrNull()?.message ?: "地图模板名称无效")
        }
        val levelType = state.levelType.trim()
        val levelTypeResult = validateBaseWorldLevelType(levelType)
        if (levelTypeResult.isFailure) {
            return showSubmitError(levelTypeResult.exceptionOrNull()?.message ?: "地形类型必须是foo:bar格式")
        }
        val settingsResult = validateBaseWorldGeneratorSettings(state.generatorSettings)
        if (settingsResult.isFailure) {
            return showSubmitError(settingsResult.exceptionOrNull()?.message ?: "生成器设置无效")
        }
        val key = state.directory?.absoluteFile?.toPath()?.normalize()?.toString()
            ?: baseWorldGeneratedTaskKey(normalizedOwner, state.name, levelType, state.generatorSettings)
        if (activeSubmissionKey == key && activeRunId != null) {
            if (isRunActive(activeRunId!!)) return activeRunId
            activeSubmissionKey = null
            activeRunId = null
        }
        if (state.submitting) return null
        val request = BaseWorldUploadRequest(
            directory = state.directory,
            name = state.name,
            levelType = levelType,
            generatorSettings = state.generatorSettings.trim().takeIf(String::isNotBlank),
        )
        _uiState.update { it.copy(submitting = true, errorMessage = null) }
        return submitter.submit(normalizedOwner, request).fold(
            onSuccess = { runId ->
                activeSubmissionKey = key
                activeRunId = runId
                _uiState.update { it.copy(submitting = false) }
                runId
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "无法开始上传",
                    )
                }
                null
            },
        )
    }

    private fun showSubmitError(message: String): Nothing? {
        _uiState.update { it.copy(errorMessage = message) }
        return null
    }
}
