package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.client.ui.screen.HostKind
import calebxzhou.rdi.common.net.json
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

private val lgr by Loggers

data class HostCreateViewModelArgs(
    val hostId: String?,
    val defaultHostName: String,
    val kind: HostKind = HostKind.Legacy,
    val sourceId: String? = null,
    val legacyVersionName: String? = null,
    val displayName: String? = null,
    val legacyMcVersion: String? = null,
)

enum class HostCreateLoadTarget {
    HOST,
}

data class HostCreateLoadIssue(
    val target: HostCreateLoadTarget,
    val cause: Throwable,
)

data class HostCreateInitialData(
    val host: Host.DetailVo? = null,
    val issues: List<HostCreateLoadIssue> = emptyList(),
)

sealed interface HostCreateSubmission {
    data class CreateLegacy(val dto: Host.CreateDto) : HostCreateSubmission

    data class EditLegacy(
        val hostId: ObjectId,
        val options: Host.OptionsDto,
    ) : HostCreateSubmission
}

sealed interface HostCreateEvent {
    data class LegacyCreateSubmitted(val message: String) : HostCreateEvent

    data class EditSaved(val message: String) : HostCreateEvent

    data class HostPackUpdateSubmitted(val message: String) : HostCreateEvent
}

data class HostCreateUiState(
    val loading: Boolean = true,
    val editHost: Host.DetailVo? = null,
    val editHostId: ObjectId? = null,
    val title: String = "创建新房间",
    val hostName: String,
    val intro: String = "",
    val selectedModpackId: String = "",
    val hostKind: HostKind = HostKind.Legacy,
    val packSourceId: String? = null,
    val packDisplayName: String = "",
    val selectedVersionName: String = "",
    val noSave: Boolean = false,
    val difficulty: Int = 3,
    val gameMode: Int = 0,
    val currentMcVersion: McVersion? = null,
    val levelType: String = "minecraft:normal",
    val levelChoice: Int = 0,
    val customLevelTypeText: String = "",
    val customLevelTypeError: String? = null,
    val whitelist: Boolean = true,
    val allowCheats: Boolean = false,
    val gameRules: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val submitting: Boolean = false,
    val updatingPack: Boolean = false,
) {
    val isEditMode: Boolean get() = editHostId != null
    val isLegacyCreate: Boolean get() = !isEditMode && hostKind == HostKind.Legacy
    val selectedPackTitle: String
        get() = packDisplayName.ifBlank { "未选择整合包" }
}

interface HostCreateGateway {
    suspend fun loadInitial(hostId: ObjectId?): Result<HostCreateInitialData>

    suspend fun createOrUpdate(submission: HostCreateSubmission): Result<Unit>

    suspend fun requestHostPackUpdate(hostId: ObjectId): Result<Unit>
}

class RdiHostCreateGateway : HostCreateGateway {
    override suspend fun loadInitial(hostId: ObjectId?): Result<HostCreateInitialData> = resultOf {
        val host = hostId?.let { loadRemote<Host.DetailVo>("host/${it.toHexString()}/detail") }
        val issues = buildList {
            host?.exceptionOrNull()?.let { add(HostCreateLoadIssue(HostCreateLoadTarget.HOST, it)) }
        }
        val loadedHost = when {
            host == null -> null
            host.isSuccess -> host.getOrThrow()
            else -> null
        }
        HostCreateInitialData(
            host = loadedHost,
            issues = issues,
        )
    }

    override suspend fun createOrUpdate(submission: HostCreateSubmission): Result<Unit> = resultOf {
        when (submission) {
            is HostCreateSubmission.CreateLegacy -> {
                val response = server.makeRequest<Unit>(
                    path = "host/v2",
                    method = HttpMethod.Post,
                ) {
                    json()
                    setBody(submission.dto)
                }
                if (!response.ok) throw RequestError(response.msg)
                Unit
            }

            is HostCreateSubmission.EditLegacy -> {
                val response = server.makeRequest<Unit>(
                    path = "host/${submission.hostId.toHexString()}/options",
                    method = HttpMethod.Put,
                ) {
                    json()
                    setBody(submission.options)
                }
                if (!response.ok) throw RequestError(response.msg)
                Unit
            }
        }
    }

    override suspend fun requestHostPackUpdate(hostId: ObjectId): Result<Unit> = resultOf {
        val response = server.makeRequest<Unit>(
            path = "host/${hostId.toHexString()}/update",
            method = HttpMethod.Post,
        )
        if (!response.ok) throw RequestError(response.msg)
    }

    private suspend inline fun <reified T> loadRemote(path: String): Result<T> = resultOf {
        val response = server.makeRequest<T>(path)
        if (!response.ok) throw RequestError(response.msg)
        response.data ?: throw RequestError("服务器未返回数据")
    }
}

class HostCreateViewModel(
    args: HostCreateViewModelArgs,
    private val gateway: HostCreateGateway,
) : ViewModel() {
    private val requestedHostId = args.hostId
        ?.trim()
        ?.takeIf(ObjectId::isValid)
        ?.let(::ObjectId)

    private val _uiState = MutableStateFlow(
        HostCreateUiState(
            hostName = args.defaultHostName,
            editHostId = requestedHostId,
            hostKind = args.kind,
            packSourceId = args.sourceId?.trim()?.takeIf(String::isNotBlank),
            selectedVersionName = args.legacyVersionName?.trim().orEmpty(),
            packDisplayName = args.displayName?.trim().orEmpty(),
            currentMcVersion = if (args.kind == HostKind.Legacy && requestedHostId == null) {
                parseMcVersion(args.legacyMcVersion)
            } else {
                null
            },
        )
    )
    val uiState: StateFlow<HostCreateUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<HostCreateEvent>(Channel.BUFFERED)
    val events: Flow<HostCreateEvent> = eventChannel.receiveAsFlow()
    init {
        loadInitial()
    }

    fun updateHostName(name: String) {
        _uiState.update { it.copy(hostName = name) }
    }

    fun updateIntro(intro: String) {
        _uiState.update { it.copy(intro = intro) }
    }

    fun updateNoSave(noSave: Boolean) {
        _uiState.update { it.copy(noSave = noSave) }
    }

    fun updateDifficulty(difficulty: Int) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun updateGameMode(gameMode: Int) {
        _uiState.update { it.copy(gameMode = gameMode) }
    }

    fun selectLevelChoice(choice: Int) {
        _uiState.update {
            when (choice) {
                0 -> it.copy(levelChoice = 0, levelType = "minecraft:normal")
                1 -> it.copy(levelChoice = 1, levelType = "minecraft:flat")
                2 -> it.copy(
                    levelChoice = 2,
                    levelType = skyblockLevelType(it.currentMcVersion),
                )
                else -> it
            }
        }
    }

    fun beginCustomLevelType() {
        _uiState.update {
            it.copy(
                customLevelTypeText = if (it.levelChoice == 3) it.levelType else it.customLevelTypeText,
                customLevelTypeError = null,
            )
        }
    }

    fun updateCustomLevelTypeText(text: String) {
        _uiState.update { it.copy(customLevelTypeText = text, customLevelTypeError = null) }
    }

    fun applyCustomLevelType(): Boolean {
        val trimmed = _uiState.value.customLevelTypeText.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(customLevelTypeError = "请输入地形ID") }
            return false
        }
        _uiState.update {
            it.copy(
                levelChoice = 3,
                levelType = trimmed,
                customLevelTypeText = trimmed,
                customLevelTypeError = null,
            )
        }
        return true
    }

    fun cancelCustomLevelType() {
        _uiState.update { it.copy(customLevelTypeError = null) }
    }

    fun updateWhitelist(whitelist: Boolean) {
        _uiState.update { it.copy(whitelist = whitelist) }
    }

    fun updateAllowCheats(allowCheats: Boolean) {
        _uiState.update { it.copy(allowCheats = allowCheats) }
    }

    fun updateGameRules(gameRules: Map<String, String>) {
        _uiState.update { it.copy(gameRules = gameRules.toMap()) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return
        val trimmedName = state.hostName.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "请输入房间名称")
            }
            return
        }
        val submission = state.editHostId?.let { hostId ->
            HostCreateSubmission.EditLegacy(
                hostId = hostId,
                options = Host.OptionsDto(
                    name = trimmedName,
                    difficulty = state.difficulty,
                    gameMode = state.gameMode,
                    levelType = state.levelType,
                    allowCheats = state.allowCheats,
                    whitelist = state.whitelist,
                    gameRules = state.gameRules.toMap(),
                ),
            )
        } ?: createSubmission(state, trimmedName) ?: return

        _uiState.update { it.copy(submitting = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    gateway.createOrUpdate(submission).getOrThrow()
                }
                when (submission) {
                    is HostCreateSubmission.CreateLegacy -> eventChannel.send(
                        HostCreateEvent.LegacyCreateSubmitted("房间创建中，服务器准备完成后会通过邮箱通知你")
                    )
                    is HostCreateSubmission.EditLegacy -> eventChannel.send(HostCreateEvent.EditSaved("设置已保存"))
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                val prefix = if (submission is HostCreateSubmission.EditLegacy) "保存失败" else "创建失败"
                lgr.warn(cause) { prefix }
                _uiState.update {
                    it.copy(statusMessage = "$prefix: ${cause.message ?: "未知错误"}")
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    fun requestHostPackUpdate() {
        val hostId = _uiState.value.editHostId ?: return
        if (_uiState.value.updatingPack) return
        _uiState.update { it.copy(updatingPack = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    gateway.requestHostPackUpdate(hostId).getOrThrow()
                }
                eventChannel.send(HostCreateEvent.HostPackUpdateSubmitted("已提交更新"))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                lgr.warn(cause) { "更新房间整合包失败" }
                _uiState.update {
                    it.copy(errorMessage = cause.message ?: "更新失败")
                }
            } finally {
                _uiState.update { it.copy(updatingPack = false) }
            }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            try {
                val initial = withContext(Dispatchers.IO) {
                    gateway.loadInitial(requestedHostId).getOrThrow()
                }
                _uiState.update { state -> applyInitial(state, initial) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                lgr.warn(cause) { "加载房间创建数据失败" }
                _uiState.update {
                    it.copy(errorMessage = "无法载入房间创建数据: ${cause.message ?: "未知错误"}")
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun applyInitial(
        state: HostCreateUiState,
        initial: HostCreateInitialData,
    ): HostCreateUiState {
        val host = initial.host
        val issueByTarget = initial.issues.associateBy(HostCreateLoadIssue::target)
        val base = state.copy(
            errorMessage = issueByTarget[HostCreateLoadTarget.HOST]?.cause?.let {
                "无法加载房间信息: ${it.message ?: "未知错误"}"
            },
        )
        if (host == null) return base
        return base.copy(
            editHost = host,
            title = "编辑房间 · ${host.name}",
            hostName = host.name,
            intro = host.intro,
            selectedModpackId = host.modpack.id.toHexString(),
            hostKind = HostKind.Legacy,
            packSourceId = host.modpack.id.toHexString(),
            packDisplayName = host.modpack.name,
            selectedVersionName = host.packVer,
            difficulty = host.difficulty,
            gameMode = host.gameMode,
            currentMcVersion = host.modpack.mcVer,
            levelType = mappedLevelType(host.levelType, host.modpack.mcVer),
            levelChoice = levelChoice(host.levelType),
            customLevelTypeText = if (levelChoice(host.levelType) == 3) host.levelType else "",
            whitelist = host.whitelist,
            allowCheats = host.allowCheats,
            gameRules = host.gameRules.toMap(),
        )
    }

    private fun createSubmission(
        state: HostCreateUiState,
        trimmedName: String,
    ): HostCreateSubmission? {
        val sourceId = state.packSourceId
        if (sourceId == null) {
            _uiState.update {
                it.copy(
                    statusMessage = "请从“我的整合包”的菜单发起创建多人房间",
                )
            }
            return null
        }
        return when (state.hostKind) {
            HostKind.Legacy -> {
                if (!ObjectId.isValid(sourceId) || state.selectedVersionName.isBlank()) {
                    _uiState.update { it.copy(statusMessage = "整合包来源或版本无效，请从“我的整合包”的菜单发起创建多人房间") }
                    return null
                }
                if (state.levelChoice == 3 && state.levelType.isBlank()) {
                    _uiState.update { it.copy(statusMessage = "自定义地形不能为空") }
                    return null
                }
                HostCreateSubmission.CreateLegacy(
                    Host.CreateDto(
                        name = trimmedName,
                        modpackId = ObjectId(sourceId),
                        packVer = state.selectedVersionName.trim(),
                        saveWorld = !state.noSave,
                        worldId = null,
                        difficulty = state.difficulty,
                        gameMode = state.gameMode,
                        levelType = state.levelType.trim(),
                        allowCheats = state.allowCheats,
                        whitelist = state.whitelist,
                        gameRules = state.gameRules.toMutableMap(),
                    )
                )
            }
            HostKind.Host2 -> {
                _uiState.update { it.copy(statusMessage = "该房间类型暂不可用") }
                null
            }
        }
    }
}

private fun levelChoice(levelType: String): Int = when {
    levelType.contains("skyblock", ignoreCase = true) -> 2
    levelType == "minecraft:flat" -> 1
    levelType == "minecraft:normal" || levelType.isBlank() -> 0
    else -> 3
}

private fun mappedLevelType(levelType: String, mcVersion: McVersion): String = when (levelChoice(levelType)) {
    0 -> "minecraft:normal"
    1 -> "minecraft:flat"
    2 -> skyblockLevelType(mcVersion)
    else -> levelType
}

private fun skyblockLevelType(mcVersion: McVersion?): String {
    val minor = mcVersion?.vMinor?.toIntOrNull()
    return if (minor != null && minor <= 16) {
        "skyblockbuilder:custom_skyblock"
    } else {
        "skyblockbuilder:skyblock"
    }
}

private fun parseMcVersion(value: String?): McVersion? {
    val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    return McVersion.entries.firstOrNull { it.name == normalized }
        ?: McVersion.from(normalized)
}

private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (cause: Throwable) {
    Result.failure(cause)
}
