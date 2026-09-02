package calebxzau.rdi.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.util.javaExePath
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.packproc.LoadedLocalModpack
import calebxzau.rdi.client.packproc.LoadedServerPackResult
import calebxzau.rdi.client.packproc.ModpackProcessor
import calebxzau.rdi.client.packproc.PackProcessingPaths
import calebxzau.rdi.client.packproc.toUploadPayload
import calebxzau.rdi.client.ui.currentJavaMajor
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ClientModpackTester
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.service.ModpackTester
import calebxzhou.rdi.client.service.TestStatus
import calebxzhou.rdi.client.service.createUploadModpackTask2
import calebxzhou.rdi.client.service.hydrateToUiMods
import calebxzhou.rdi.client.service.hydrateToUiModsInBatches
import calebxzhou.rdi.client.service.modpackUploadTaskKey
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.IGNORE_MODPACK_TEST
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.LoadProgress
import calebxzhou.rdi.common.model.MODPACK_INFO_MAX_CHARACTERS
import calebxzhou.rdi.common.model.MODPACK_INFO_MIN_CHARACTERS
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.validateIconUrl
import calebxzhou.rdi.common.service.validateIconUrlAddress
import calebxzhou.rdi.common.model.modpackInfoCharacterCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId
import java.io.File
import java.util.jar.JarFile

enum class ModpackUploadMode { CREATE, UPDATE }

data class ModpackUploadDraft(
    val name: String = "",
    val versionName: String = "",
    val categories: List<Modpack.Category> = emptyList(),
    val iconUrl: String = "",
    val sourceUrl: String = "",
    val info: String = "",
)

data class ModpackUploadFieldErrors(
    val info: String? = null,
    val iconUrl: String? = null,
    val categories: String? = null,
) {
    val isEmpty: Boolean
        get() = info == null && iconUrl == null && categories == null

    val firstMessage: String?
        get() = info ?: iconUrl ?: categories
}

private fun validateModpackUploadDraft(
    draft: ModpackUploadDraft,
    mode: ModpackUploadMode,
): ModpackUploadFieldErrors {
    if (mode == ModpackUploadMode.UPDATE) return ModpackUploadFieldErrors()

    val normalizedInfo = draft.info.trim()
    val infoLength = normalizedInfo.modpackInfoCharacterCount()
    val infoError = when {
        normalizedInfo.isBlank() -> "简介不能为空"
        infoLength < MODPACK_INFO_MIN_CHARACTERS ->
            "简介至少需要${MODPACK_INFO_MIN_CHARACTERS}个字符"
        infoLength > MODPACK_INFO_MAX_CHARACTERS ->
            "简介最多${MODPACK_INFO_MAX_CHARACTERS}个字符"
        else -> null
    }
    val normalizedIconUrl = draft.iconUrl.trim()
    val iconError = when {
        normalizedIconUrl.isBlank() -> "图标链接不能为空"
        else -> validateIconUrlAddress(normalizedIconUrl).fold(
            onSuccess = { null },
            onFailure = { cause -> cause.message ?: "图标链接无效" },
        )
    }
    val categoriesError = when {
        draft.categories.isEmpty() -> "至少选择1个分类"
        draft.categories.distinct().size > Modpack.MAX_CATEGORY_COUNT ->
            "分类最多选择${Modpack.MAX_CATEGORY_COUNT}个"
        else -> null
    }
    return ModpackUploadFieldErrors(infoError, iconError, categoriesError)
}

data class PendingMissingModDownload(
    val usage: String,
    val mods: List<Mod>,
)

data class PreparedClientPack(
    val pack: LoadedLocalModpack,
    val uiMods: List<UiMod>,
)

data class PreparedServerPack(
    val pack: LoadedServerPackResult,
    val uiMods: List<UiMod>,
)

data class ModpackUploadSubmission(
    val pack: LoadedLocalModpack,
    val uiMods: List<UiMod>,
    val draft: ModpackUploadDraft,
    val updateModpackId: ObjectId?,
)

data class ModpackUploadUiState(
    val title: String = "上传整合包",
    val loading: Boolean = false,
    val iconValidationRunning: Boolean = false,
    val editMode: Boolean = false,
    val loadedModpack: LoadedLocalModpack? = null,
    val draft: ModpackUploadDraft = ModpackUploadDraft(),
    val draftErrors: ModpackUploadFieldErrors = ModpackUploadFieldErrors(),
    val uiMods: List<UiMod> = emptyList(),
    val uiModsLoading: Boolean = false,
    val serverPackName: String? = null,
    val progressText: String? = null,
    val progressFraction: Float? = null,
    val downloadTaskRunId: String? = null,
    val downloadTaskEntry: Task2Entry? = null,
    val uploadMode: ModpackUploadMode = ModpackUploadMode.CREATE,
    val uploadedModpacks: List<Modpack.BriefVo> = emptyList(),
    val uploadedModpacksLoading: Boolean = false,
    val uploadedModpacksLoaded: Boolean = false,
    val selectedUpdateTarget: Modpack.BriefVo? = null,
    val pendingMissingModDownload: PendingMissingModDownload? = null,
    val ignoreModpackTest: Boolean = false,
    val clientTestStatus: TestStatus = TestStatus.NOT_RUN,
    val clientTestPassSeconds: String? = null,
    val serverTestStatus: TestStatus = TestStatus.NOT_RUN,
    val serverTestPassSeconds: String? = null,
    val mcVersionText: String = "",
    val modloaderText: String = "",
    val errorMessage: String? = null,
) {
    val allowUploadWithoutTests: Boolean
        get() = DEBUG || ignoreModpackTest

    val uploadDisabledReason: String?
        get() {
            validateModpackUploadDraft(draft, uploadMode).firstMessage?.let { return it }
            if (iconValidationRunning) return "正在验证图标链接"
            if (ignoreModpackTest) return null
            return when {
                loading -> "正在处理整合包"
                uiModsLoading -> "正在补充Mod详细信息"
                downloadTaskRunId != null -> "正在下载测试服务端"
                serverTestStatus == TestStatus.RUNNING -> "服务端测试进行中"
                clientTestStatus == TestStatus.RUNNING -> "客户端测试进行中"
                uploadMode == ModpackUploadMode.UPDATE && selectedUpdateTarget == null ->
                    "请选择要更新的已有整合包"
                else -> null
            }
        }

    val canSubmitUpload: Boolean
        get() = uploadDisabledReason == null

    val canSelectServerPack: Boolean
        get() = editMode &&
            !loading &&
            !uiModsLoading &&
            downloadTaskRunId == null &&
            serverTestStatus != TestStatus.RUNNING &&
            clientTestStatus != TestStatus.RUNNING
}

sealed interface ModpackUploadEvent {
    data class ClientPackLoaded(val initialTab: Int = 0) : ModpackUploadEvent
    data object OpenUploadModeDialog : ModpackUploadEvent
    data class UploadSubmitted(val runId: String) : ModpackUploadEvent
    data object NavigateBack : ModpackUploadEvent
}

interface ModpackUploadGateway {
    val taskEntries: StateFlow<List<Task2Entry>>
    val ignoreModpackTestInitially: Boolean

    suspend fun loadUploadedModpacks(): Result<List<Modpack.BriefVo>>

    suspend fun prepareClientPack(
        file: File,
        onProgress: (LoadProgress) -> Unit,
    ): Result<PreparedClientPack>

    fun hydrateMods(mods: List<UiMod>): Flow<List<UiMod>>

    suspend fun prepareServerPack(
        directory: File,
        clientUiMods: List<UiMod>,
        onProgress: (LoadProgress) -> Unit,
    ): Result<PreparedServerPack>

    suspend fun validateRuntime(mcVersion: McVersion): Result<Unit>

    suspend fun findMissingMods(mods: List<Mod>): Result<List<Mod>>

    fun queueMissingModDownload(mods: List<Mod>): Result<String>

    fun queueTestServer(pack: LoadedLocalModpack): Result<String>

    fun queueUpload(submission: ModpackUploadSubmission): Result<String>

    fun enableIgnoreModpackTest(): Result<Unit>

    suspend fun cleanup(): Result<Unit>
}

class RdiModpackUploadGateway(
    private val modCatalog: ModCatalog,
) : ModpackUploadGateway {
    private val processor = ModpackProcessor(
        PackProcessingPaths(
            workDir = ClientDirs.packProcDir,
            modCacheDir = DL_MOD_DIR,
        )
    )

    override val taskEntries: StateFlow<List<Task2Entry>>
        get() = ClientTaskManager.entries

    override val ignoreModpackTestInitially: Boolean
        get() = IGNORE_MODPACK_TEST

    override suspend fun loadUploadedModpacks(): Result<List<Modpack.BriefVo>> = resultOf {
        val response = server.makeRequest<List<Modpack>>("modpack/my")
        if (!response.ok) throw RequestError(response.msg)
        response.data.orEmpty()
            .sortedByDescending { pack -> pack.versions.maxOfOrNull { it.time } ?: 0L }
            .map(Modpack::toLocalBriefVo)
    }

    override suspend fun prepareClientPack(
        file: File,
        onProgress: (LoadProgress) -> Unit,
    ): Result<PreparedClientPack> = resultOf {
        val pack = processor.loadLocalModpack(
            file = file,
            modCatalog = modCatalog,
            onProgress = onProgress,
        ).getOrThrow()
        val initialUiMods = defaultCurseForgeUnknownMods(pack.mods.toUiMods())
        val processedUiMods = processUiMods(initialUiMods).getOrThrow()
        PreparedClientPack(
            pack = pack.copy(mods = processedUiMods.map(UiMod::toMod)),
            uiMods = processedUiMods,
        )
    }

    override fun hydrateMods(mods: List<UiMod>): Flow<List<UiMod>> =
        mods.hydrateToUiModsInBatches(modCatalog)

    override suspend fun prepareServerPack(
        directory: File,
        clientUiMods: List<UiMod>,
        onProgress: (LoadProgress) -> Unit,
    ): Result<PreparedServerPack> = resultOf {
        val serverPack = processor.loadServerPack(
            file = directory,
            clientMods = clientUiMods.map(UiMod::toMod),
            onProgress = onProgress,
        ).getOrThrow()
        val serverUiMods = serverPack.mods.hydrateToUiMods(modCatalog)
        val mergedUiMods = mergeClientAndServerMods(clientUiMods, serverUiMods)
        PreparedServerPack(serverPack, processUiMods(mergedUiMods).getOrThrow())
    }

    override suspend fun validateRuntime(mcVersion: McVersion): Result<Unit> = resultOf {
        val currentMajor = currentJavaMajor()
        if (javaExePath.isNotBlank() && mcVersion.supportsCurrentJava(currentMajor)) return@resultOf
        val javaText = mcVersion.supportedJreVers.joinToString("或") { "Java$it" }
        error("MC${mcVersion.mcVer}需要${javaText}。请使用${javaText}启动RDI后再上传")
    }

    override suspend fun findMissingMods(mods: List<Mod>): Result<List<Mod>> = resultOf {
        mods.filterNot(ModService::isDownloadedModFileValid)
    }

    private fun processUiMods(mods: List<UiMod>): Result<List<UiMod>> = runCatching {
        preserveUiMods(
            source = mods,
            updatedMods = processor.processUploadMods(mods.map(UiMod::toMod)),
        )
    }

    override fun queueMissingModDownload(mods: List<Mod>): Result<String> = runCatching {
        ClientTaskManager.submit(ModService.downloadModsTask2(mods))
    }

    override fun queueTestServer(pack: LoadedLocalModpack): Result<String> = runCatching {
        ClientTaskManager.submit(GameService.downloadTestServerTask2(pack.mcVersion, pack.modloader))
    }

    override fun queueUpload(submission: ModpackUploadSubmission): Result<String> = runCatching {
        val draft = submission.draft
        val processedUiMods = processUiMods(submission.uiMods).getOrThrow()
        val payload = submission.pack.copy(
            packName = draft.name,
            packVersion = draft.versionName,
            mods = processedUiMods.map(UiMod::toMod),
        ).toUploadPayload()
        ClientTaskManager.submit(
            task = createUploadModpackTask2(
                processor = processor,
                payload = payload,
                mods = payload.mods,
                modpackName = draft.name,
                versionName = draft.versionName,
                iconUrl = draft.iconUrl.trim().ifBlank { null },
                sourceUrl = draft.sourceUrl.trim().ifBlank { null },
                info = draft.info.trim().ifBlank { null },
                categories = Modpack.normalizeCategories(draft.categories),
                updateModpackId = submission.updateModpackId,
            ),
            dedupeKey = modpackUploadTaskKey(
                updateModpackId = submission.updateModpackId,
                modpackName = draft.name,
                versionName = draft.versionName,
            ),
        )
    }

    override fun enableIgnoreModpackTest(): Result<Unit> = runCatching {
        System.setProperty("rdi.ignoreModpackTest", "true")
    }

    override suspend fun cleanup(): Result<Unit> = resultOf {
        ClientDirs.packProcDir.deleteRecursivelyNoSymlink()
        ClientDirs.packProcDir.mkdirs()
    }
}

class ModpackUploadViewModel(
    private val gateway: ModpackUploadGateway,
    private val validateFullIconUrl: suspend (String?) -> Result<Unit> = ::validateIconUrl,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ModpackUploadUiState(ignoreModpackTest = gateway.ignoreModpackTestInitially)
    )
    val uiState: StateFlow<ModpackUploadUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ModpackUploadEvent>(Channel.BUFFERED)
    val events: Flow<ModpackUploadEvent> = eventChannel.receiveAsFlow()

    val clientTestConsoleState = ConsoleState()
    val serverTestConsoleState = ConsoleState()

    private var clientTester: ClientModpackTester? = null
    private var serverTester: ModpackTester? = null
    private val testerObservationJobs = mutableListOf<Job>()

    init {
        observeTasks()
    }

    fun updateDraft(draft: ModpackUploadDraft) {
        _uiState.update {
            it.copy(
                draft = draft,
                draftErrors = validateModpackUploadDraft(draft, it.uploadMode),
                errorMessage = null,
            )
        }
    }

    fun updateVersionName(value: String) {
        updateDraft(_uiState.value.draft.copy(versionName = value.replace(' ', '_')))
    }

    fun loadClientPack(file: File) {
        _uiState.update {
            it.copy(
                loading = true,
                progressText = "已选择:${file.name}",
                progressFraction = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    gateway.prepareClientPack(file, ::updateProgress).getOrThrow()
                }
                withContext(Dispatchers.IO) {
                    gateway.validateRuntime(prepared.pack.mcVersion).getOrThrow()
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        editMode = true,
                        loadedModpack = prepared.pack,
                        draftErrors = ModpackUploadFieldErrors(),
                        draft = ModpackUploadDraft(
                            name = prepared.pack.packName,
                            versionName = prepared.pack.packVersion.replace(' ', '_'),
                        ),
                        uiMods = prepared.uiMods,
                        uiModsLoading = prepared.uiMods.isNotEmpty(),
                        serverPackName = null,
                        uploadMode = ModpackUploadMode.CREATE,
                        selectedUpdateTarget = null,
                        mcVersionText = prepared.pack.mcVersion.mcVer,
                        modloaderText = prepared.pack.modloader.name,
                    )
                }
                eventChannel.send(ModpackUploadEvent.ClientPackLoaded())
                try {
                    gateway.hydrateMods(prepared.uiMods)
                        .flowOn(Dispatchers.IO)
                        .collect { batch ->
                            _uiState.update { state ->
                                state.copy(uiMods = mergeHydratedUiMods(state.uiMods, batch))
                            }
                        }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (cause: Throwable) {
                    lgr.warn(cause) { "加载整合包Mod展示信息失败，将使用基础Mod数据" }
                }
                val finalUiMods = _uiState.value.uiMods
                val finalPack = prepared.pack.copy(mods = finalUiMods.map(UiMod::toMod))
                replaceTesters(finalPack, finalUiMods)
                _uiState.update {
                    it.copy(
                        loadedModpack = finalPack,
                        uiModsLoading = false,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                reportError("读取整合包失败", cause)
            } finally {
                _uiState.update { it.copy(uiModsLoading = false) }
                finishLoading()
            }
        }
    }

    fun loadServerPack(directory: File) {
        val state = _uiState.value
        if (state.loadedModpack == null) {
            _uiState.update { it.copy(errorMessage = "请先选择整合包文件") }
            return
        }
        _uiState.update {
            it.copy(
                loading = true,
                progressText = "已选择服务端目录:${directory.name}",
                progressFraction = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    gateway.prepareServerPack(directory, state.uiMods, ::updateProgress).getOrThrow()
                }
                applyServerPack(prepared)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                reportError("读取服务端目录失败", cause)
            } finally {
                finishLoading()
            }
        }
    }

    fun updateModSide(uiMod: UiMod, newSide: Mod.Side) {
        if (newSide == uiMod.side) return
        _uiState.update {
            it.copy(uiMods = updateModSide(it.uiMods, modStableKey(uiMod.mod), newSide))
        }
        clientTester?.onModsChangedAfterManualEdit()
        serverTester?.onModsChangedAfterManualEdit()
    }

    fun chooseCreateMode() {
        _uiState.update {
            it.copy(
                uploadMode = ModpackUploadMode.CREATE,
                selectedUpdateTarget = null,
                draftErrors = ModpackUploadFieldErrors(),
                draft = it.draft.copy(
                    name = it.loadedModpack?.packName ?: it.draft.name,
                    categories = emptyList(),
                    iconUrl = "",
                    info = "",
                ),
            )
        }
    }

    fun chooseUpdateMode(target: Modpack.BriefVo) {
        _uiState.update {
            it.copy(
                uploadMode = ModpackUploadMode.UPDATE,
                selectedUpdateTarget = target,
                draftErrors = ModpackUploadFieldErrors(),
                draft = it.draft.copy(
                    name = target.name,
                    categories = target.categories,
                    iconUrl = target.icon.orEmpty(),
                    info = target.info.orEmpty(),
                ),
            )
        }
    }

    fun requestUpdateModeSelection() {
        _uiState.update {
            it.copy(
                uploadMode = ModpackUploadMode.UPDATE,
                draft = it.draft.copy(
                    categories = it.selectedUpdateTarget?.categories ?: emptyList()
                ),
            )
        }
        loadUploadedModpacks(openDialogAfterLoad = true)
    }

    fun requestUploadModeDialog() {
        loadUploadedModpacks(openDialogAfterLoad = true)
    }

    fun closeUploadModeDialog() {
        if (_uiState.value.uploadMode == ModpackUploadMode.UPDATE &&
            _uiState.value.selectedUpdateTarget == null
        ) {
            chooseCreateMode()
        }
    }

    fun startClientTest() {
        val tester = clientTester
        when {
            _uiState.value.uiModsLoading -> _uiState.update { it.copy(errorMessage = "Mod信息仍在载入中") }
            tester == null -> _uiState.update { it.copy(errorMessage = "请先选择整合包文件") }
            tester.isRunning() -> _uiState.update { it.copy(errorMessage = "测试客户端已经在运行中") }
            else -> requireModsDownloadedFor("客户端测试") { startClientTestAfterModsReady(tester) }
        }
    }

    fun stopClientTest() {
        clientTester?.stop(
            uiScope = viewModelScope,
            appendLog = clientTestConsoleState::append,
        )
    }

    fun startServerTest() {
        val tester = serverTester
        when {
            _uiState.value.uiModsLoading -> _uiState.update { it.copy(errorMessage = "Mod信息仍在载入中") }
            tester == null -> _uiState.update { it.copy(errorMessage = "请先选择整合包文件") }
            tester.isRunning() -> _uiState.update { it.copy(errorMessage = "测试服务器已经在运行中") }
            else -> requireModsDownloadedFor("服务端测试") { startServerTestAfterModsReady(tester) }
        }
    }

    fun stopServerTest() {
        serverTester?.stop(
            uiScope = viewModelScope,
            appendLog = serverTestConsoleState::append,
        )
    }

    fun dismissMissingModDownload() {
        _uiState.update { it.copy(pendingMissingModDownload = null) }
    }

    fun confirmMissingModDownload() {
        val pending = _uiState.value.pendingMissingModDownload ?: return
        if (_uiState.value.downloadTaskRunId != null) {
            _uiState.update { it.copy(errorMessage = "已有下载任务正在运行") }
            return
        }
        gateway.queueMissingModDownload(pending.mods)
            .onSuccess { runId ->
                _uiState.update {
                    it.copy(pendingMissingModDownload = null)
                }
                setDownloadRunId(runId)
            }
            .onFailure { reportError("提交Mod下载任务失败", it) }
    }

    fun downloadTestServer() {
        val pack = _uiState.value.loadedModpack
        if (pack == null) {
            _uiState.update { it.copy(errorMessage = "请先选择整合包文件") }
            return
        }
        if (_uiState.value.downloadTaskRunId != null) {
            _uiState.update { it.copy(errorMessage = "已有下载任务正在运行") }
            return
        }
        gateway.queueTestServer(pack)
            .onSuccess(::setDownloadRunId)
            .onFailure { reportError("提交测试服务端下载失败", it) }
    }

    fun submitUpload() {
        val state = _uiState.value
        if (state.iconValidationRunning) return
        val pack = state.loadedModpack
        if (pack == null) {
            _uiState.update { it.copy(errorMessage = "请先选择整合包文件") }
            return
        }
        val name = state.draft.name.trim()
        val versionName = state.draft.versionName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "整合包名称不能为空") }
            return
        }
        if (versionName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "版本号不能为空") }
            return
        }
        val draftErrors = validateModpackUploadDraft(state.draft, state.uploadMode)
        if (!draftErrors.isEmpty) {
            _uiState.update {
                it.copy(
                    draftErrors = draftErrors,
                    errorMessage = draftErrors.firstMessage,
                )
            }
            return
        }
        if (state.serverPackName == null && !state.allowUploadWithoutTests) {
            if (state.clientTestStatus != TestStatus.PASSED) {
                _uiState.update { it.copy(errorMessage = "请先完成客户端测试并通过") }
                return
            }
            if (state.serverTestStatus != TestStatus.PASSED) {
                _uiState.update { it.copy(errorMessage = "请先完成服务端测试并通过") }
                return
            }
        }
        if (state.uploadMode == ModpackUploadMode.UPDATE && state.selectedUpdateTarget == null) {
            _uiState.update { it.copy(errorMessage = "请选择要更新的已有整合包") }
            return
        }
        val finalName = if (state.uploadMode == ModpackUploadMode.UPDATE) {
            state.selectedUpdateTarget?.name ?: name
        } else {
            name
        }
        val submission = ModpackUploadSubmission(
            pack = pack,
            uiMods = state.uiMods,
            draft = state.draft.copy(name = finalName, versionName = versionName),
            updateModpackId = state.selectedUpdateTarget?.id
                .takeIf { state.uploadMode == ModpackUploadMode.UPDATE },
        )
        if (state.uploadMode == ModpackUploadMode.CREATE) {
            _uiState.update {
                it.copy(iconValidationRunning = true, errorMessage = null)
            }
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        validateFullIconUrl(submission.draft.iconUrl).getOrThrow()
                    }
                    val currentState = _uiState.value
                    if (currentState.uploadMode != ModpackUploadMode.CREATE ||
                        currentState.draft != state.draft ||
                        currentState.loadedModpack !== pack
                    ) {
                        _uiState.update {
                            it.copy(
                                iconValidationRunning = false,
                                errorMessage = "上传信息已更改，请重新提交",
                            )
                        }
                        return@launch
                    }
                    queueUploadSubmission(submission)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (cause: Throwable) {
                    reportError("验证图标链接失败", cause)
                } finally {
                    _uiState.update { it.copy(iconValidationRunning = false) }
                }
            }
            return
        }
        queueUploadSubmission(submission)
    }

    private fun queueUploadSubmission(submission: ModpackUploadSubmission) {
        gateway.queueUpload(submission)
            .onSuccess { runId ->
                if (runId.isNotBlank()) viewModelScope.launch {
                    eventChannel.send(ModpackUploadEvent.UploadSubmitted(runId))
                }
            }
            .onFailure { reportError("提交上传任务失败", it) }
    }

    fun enableIgnoreModpackTest() {
        gateway.enableIgnoreModpackTest()
            .onSuccess { _uiState.update { it.copy(ignoreModpackTest = true) } }
            .onFailure { reportError("启用跳过测试失败", it) }
    }

    fun closeScreen() {
        disposeTesters()
        _uiState.update { it.copy(loading = true, title = "清理缓存中...") }
        viewModelScope.launch {
            gateway.cleanup()
                .onFailure { lgr.warn(it) { "清理整合包处理缓存失败" } }
            eventChannel.send(ModpackUploadEvent.NavigateBack)
        }
    }

    override fun onCleared() {
        disposeTesters()
        eventChannel.close()
        super.onCleared()
    }

    private fun loadUploadedModpacks(openDialogAfterLoad: Boolean) {
        val state = _uiState.value
        if (state.uploadedModpacksLoading) return
        if (state.uploadedModpacksLoaded) {
            if (openDialogAfterLoad) viewModelScope.launch {
                eventChannel.send(ModpackUploadEvent.OpenUploadModeDialog)
            }
            return
        }
        _uiState.update { it.copy(uploadedModpacksLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val packs = withContext(Dispatchers.IO) {
                    gateway.loadUploadedModpacks().getOrThrow()
                }
                _uiState.update {
                    it.copy(
                        uploadedModpacks = packs,
                        uploadedModpacksLoaded = true,
                    )
                }
                if (openDialogAfterLoad) {
                    eventChannel.send(ModpackUploadEvent.OpenUploadModeDialog)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                reportError("读取已上传整合包失败", cause)
            } finally {
                _uiState.update { it.copy(uploadedModpacksLoading = false) }
            }
        }
    }

    private fun requireModsDownloadedFor(usage: String, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val missingMods = withContext(Dispatchers.IO) {
                    gateway.findMissingMods(_uiState.value.uiMods.map(UiMod::toMod)).getOrThrow()
                }
                if (missingMods.isEmpty()) {
                    onReady()
                } else {
                    _uiState.update {
                        it.copy(pendingMissingModDownload = PendingMissingModDownload(usage, missingMods))
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                reportError("检查缺失Mod失败", cause)
            }
        }
    }

    private fun startClientTestAfterModsReady(tester: ClientModpackTester) {
        clientTestConsoleState.clear()
        tester.start(
            uiScope = viewModelScope,
            getMods = { _uiState.value.uiMods.map(UiMod::toMod) },
            onError = { message -> _uiState.update { it.copy(errorMessage = message) } },
            appendLog = clientTestConsoleState::append,
        )
    }

    private fun startServerTestAfterModsReady(tester: ModpackTester) {
        serverTestConsoleState.clear()
        tester.startWithAutoFix(
            uiScope = viewModelScope,
            getMods = { _uiState.value.uiMods.map(UiMod::toMod) },
            setMods = { mods ->
                _uiState.update {
                    it.copy(uiMods = preserveUiMods(it.uiMods, mods))
                }
            },
            onError = { message -> _uiState.update { it.copy(errorMessage = message) } },
            appendLog = serverTestConsoleState::append,
        )
    }

    private fun applyServerPack(prepared: PreparedServerPack) {
        val processedUiMods = prepared.uiMods
        val updatedPack = _uiState.value.loadedModpack?.copy(
            mods = processedUiMods.map(UiMod::toMod),
            serverExtraFiles = prepared.pack.serverExtraFiles,
        )
        _uiState.update {
            it.copy(
                uiMods = processedUiMods,
                loadedModpack = updatedPack,
                serverPackName = buildServerPackName(prepared.pack),
                editMode = true,
            )
        }
        if (updatedPack != null) replaceTesters(updatedPack, processedUiMods)
    }

    private fun replaceTesters(pack: LoadedLocalModpack, uiMods: List<UiMod>) {
        disposeTesters()
        clientTestConsoleState.clear()
        serverTestConsoleState.clear()
        _uiState.update {
            it.copy(
                clientTestStatus = TestStatus.NOT_RUN,
                clientTestPassSeconds = null,
                serverTestStatus = TestStatus.NOT_RUN,
                serverTestPassSeconds = null,
            )
        }
        clientTester = ClientModpackTester(pack.copy(mods = uiMods.map(UiMod::toMod)))
        serverTester = ModpackTester(pack.copy(mods = uiMods.map(UiMod::toMod)))
        observeTesters()
    }

    private fun observeTesters() {
        val client = clientTester ?: return
        val server = serverTester ?: return
        testerObservationJobs += viewModelScope.launch {
            client.status.collectLatest { status ->
                _uiState.update { it.copy(clientTestStatus = status) }
            }
        }
        testerObservationJobs += viewModelScope.launch {
            client.passSeconds.collectLatest { seconds ->
                _uiState.update { it.copy(clientTestPassSeconds = seconds) }
            }
        }
        testerObservationJobs += viewModelScope.launch {
            server.status.collectLatest { status ->
                _uiState.update { it.copy(serverTestStatus = status) }
            }
        }
        testerObservationJobs += viewModelScope.launch {
            server.passSeconds.collectLatest { seconds ->
                _uiState.update { it.copy(serverTestPassSeconds = seconds) }
            }
        }
    }

    private fun observeTasks() {
        viewModelScope.launch {
            gateway.taskEntries.collectLatest { entries ->
                val runId = _uiState.value.downloadTaskRunId
                val entry = runId?.let { id -> entries.firstOrNull { it.runId == id } }
                updateDownloadTaskEntry(entry)
            }
        }
    }

    private fun setDownloadRunId(runId: String) {
        val entry = gateway.taskEntries.value.firstOrNull { it.runId == runId }
        _uiState.update {
            it.copy(downloadTaskRunId = runId, downloadTaskEntry = entry)
        }
        updateDownloadTaskEntry(entry)
    }

    private fun updateDownloadTaskEntry(entry: Task2Entry?) {
        when (entry?.status) {
            Task2Status.DONE, Task2Status.CANCELLED -> {
                _uiState.update { it.copy(downloadTaskRunId = null, downloadTaskEntry = null) }
            }

            Task2Status.FAILED -> {
                _uiState.update {
                    it.copy(
                        downloadTaskRunId = null,
                        downloadTaskEntry = null,
                        errorMessage = entry.snapshot.errorMessage ?: "下载任务失败",
                    )
                }
            }

            else -> _uiState.update { it.copy(downloadTaskEntry = entry) }
        }
    }

    private fun disposeTesters() {
        testerObservationJobs.forEach { it.cancel() }
        testerObservationJobs.clear()
        clientTester?.dispose(viewModelScope)
        serverTester?.dispose(viewModelScope)
        clientTester = null
        serverTester = null
    }

    private fun updateProgress(progress: LoadProgress) {
        _uiState.update {
            when (progress) {
                is LoadProgress.Phase -> it.copy(progressText = progress.text, progressFraction = null)
                is LoadProgress.Percent -> it.copy(
                    progressText = progress.text,
                    progressFraction = progress.fraction.coerceIn(0f, 1f),
                )
                is LoadProgress.Warn -> it.copy(progressText = progress.text)
            }
        }
    }

    private fun finishLoading() {
        _uiState.update {
            it.copy(loading = false, progressText = null, progressFraction = null)
        }
    }

    private fun reportError(prefix: String, cause: Throwable) {
        lgr.warn(cause) { prefix }
        _uiState.update {
            it.copy(errorMessage = cause.message ?: prefix)
        }
    }
}

private fun buildServerPackName(serverPack: LoadedServerPackResult): String = buildString {
    append("已选择服务端(")
    append(serverPack.mods.size)
    append("个mod")
    if (serverPack.serverExtraFiles.isNotEmpty()) {
        append("+")
        append(serverPack.serverExtraFiles.size)
        append("个额外文件")
    }
    append(")")
}

private fun updateModSide(
    uiMods: List<UiMod>,
    modKey: String,
    newSide: Mod.Side,
): List<UiMod> {
    val targetIndex = uiMods.indexOfFirst { modStableKey(it.mod) == modKey }
    if (targetIndex < 0) return uiMods
    return uiMods.toMutableList().apply {
        this[targetIndex] = this[targetIndex].withSide(newSide)
    }
}

private fun preserveUiMods(source: List<UiMod>, updatedMods: List<Mod>): List<UiMod> {
    val sourceByKey = source.associateBy { modStableKey(it.mod) }
    return updatedMods.map { updatedMod ->
        sourceByKey[modStableKey(updatedMod)]?.let { sourceUiMod ->
            sourceUiMod.copy(
                mod = updatedMod,
                card = sourceUiMod.card?.copy(side = updatedMod.side),
            )
        } ?: UiMod(mod = updatedMod)
    }
}

private fun mergeHydratedUiMods(current: List<UiMod>, hydrated: List<UiMod>): List<UiMod> {
    val currentByKey = current.associateBy { modStableKey(it.mod) }
    return hydrated.map { hydratedMod ->
        currentByKey[modStableKey(hydratedMod.mod)]?.let { currentMod ->
            hydratedMod.copy(
                mod = hydratedMod.mod.copy(side = currentMod.side),
                card = hydratedMod.card?.copy(side = currentMod.side),
                file = currentMod.file ?: hydratedMod.file,
            )
        } ?: hydratedMod
    }
}

private fun defaultCurseForgeUnknownMods(source: List<UiMod>): List<UiMod> = source.map { uiMod ->
    if (uiMod.platform.equals("cf", ignoreCase = true) && uiMod.side == Mod.Side.UNKNOWN) {
        uiMod.withSide(Mod.Side.BOTH)
    } else {
        uiMod
    }
}

private fun modStableKey(mod: Mod): String =
    "${mod.platform}:${mod.projectId}:${mod.fileId}:${mod.hash}"

private fun Modpack.toLocalBriefVo(): Modpack.BriefVo = Modpack.BriefVo(
    id = _id,
    name = name,
    authorId = authorId,
    authorName = "",
    mcVer = mcVer,
    modloader = modloader,
    modCount = versions.maxOfOrNull { it.mods.size } ?: 0,
    fileSize = versions.lastOrNull()?.totalSize ?: 0L,
    lastUpdatedTime = versions.lastOrNull()?.time ?: 0L,
    icon = iconUrl,
    info = info,
    categories = categories,
)

private data class ModMergeEntry(
    val uiMod: UiMod,
    val strictKey: String,
    val slugKey: String?,
) {
    val mod: Mod
        get() = uiMod.mod
}

private fun strictModMergeKey(
    mod: Mod,
    installedModIdCache: MutableMap<String, String?>,
): String {
    val modId = readInstalledModId(mod, installedModIdCache)
    return when {
        !modId.isNullOrBlank() -> "modid:$modId"
        mod.slug.isNotBlank() -> "slug:${mod.slug.lowercase()}"
        mod.projectId.isNotBlank() -> "project:${mod.projectId}"
        else -> modStableKey(mod)
    }
}

private fun slugModMergeKey(mod: Mod): String? =
    mod.slug.trim().lowercase().takeIf(String::isNotBlank)?.let { "slug:$it" }

private fun UiMod.asMergeEntry(installedModIdCache: MutableMap<String, String?>): ModMergeEntry =
    ModMergeEntry(
        uiMod = this,
        strictKey = strictModMergeKey(mod, installedModIdCache),
        slugKey = slugModMergeKey(mod),
    )

private fun mergeAsBoth(clientMod: UiMod, serverMod: UiMod): UiMod {
    val mergedDownloadUrls = (clientMod.mod.downloadUrls + serverMod.mod.downloadUrls).distinct()
    return clientMod.copy(
        mod = clientMod.mod.copy(side = Mod.Side.BOTH, downloadUrls = mergedDownloadUrls),
        card = (clientMod.card ?: serverMod.card)?.copy(side = Mod.Side.BOTH),
        file = clientMod.file ?: serverMod.file,
    )
}

private fun mergeClientAndServerMods(
    clientMods: List<UiMod>,
    serverMods: List<UiMod>,
): List<UiMod> {
    val installedModIdCache = mutableMapOf<String, String?>()
    val clientEntries = clientMods.map { it.asMergeEntry(installedModIdCache) }
    val serverEntries = serverMods.map { it.asMergeEntry(installedModIdCache) }
    val serverByStrictKey = serverEntries.associateBy { it.strictKey }
    val uniqueClientSlugKeys = clientEntries.mapNotNull { it.slugKey }
        .groupingBy { it }
        .eachCount()
        .filterValues { it == 1 }
        .keys
    val serverByUniqueSlugKey = serverEntries.filter { it.slugKey != null }
        .groupBy { it.slugKey!! }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val matchedServerEntries = mutableSetOf<ModMergeEntry>()
    val merged = mutableListOf<UiMod>()

    clientEntries.forEach { clientEntry ->
        val strictMatch = serverByStrictKey[clientEntry.strictKey]
            ?.takeIf { it !in matchedServerEntries }
        val slugMatch = clientEntry.slugKey
            ?.takeIf { it in uniqueClientSlugKeys }
            ?.let { serverByUniqueSlugKey[it] }
            ?.takeIf { it !in matchedServerEntries }
        val serverMatch = strictMatch ?: slugMatch
        if (serverMatch != null) {
            if (strictMatch == null) {
                lgr.info {
                    "按slug合并跨来源mod:" +
                        "${clientEntry.mod.platform}:${clientEntry.mod.projectId} ${clientEntry.mod.slug}+" +
                        "${serverMatch.mod.platform}:${serverMatch.mod.projectId} ${serverMatch.mod.slug}"
                }
            }
            matchedServerEntries += serverMatch
            merged += mergeAsBoth(clientEntry.uiMod, serverMatch.uiMod)
        } else {
            merged += clientEntry.uiMod.withSide(Mod.Side.CLIENT)
        }
    }
    serverEntries.forEach { serverEntry ->
        if (serverEntry !in matchedServerEntries) {
            merged += serverEntry.uiMod.withSide(Mod.Side.SERVER)
        }
    }
    return merged.distinctBy { strictModMergeKey(it.mod, installedModIdCache) }
        .sortedBy { it.slug.lowercase() }
}

private fun readInstalledModId(
    mod: Mod,
    installedModIdCache: MutableMap<String, String?>,
): String? {
    val cacheKey = mod.targetPath.toString()
    return installedModIdCache.getOrPut(cacheKey) {
        runCatching {
            val file = mod.targetPath.toFile().takeIf { it.exists() && it.isFile }
                ?: return@getOrPut null
            ModService.run {
                JarFile(file).use { jar ->
                    jar.readModMeta()?.primaryModId?.trim()?.lowercase()?.ifBlank { null }
                }
            }
        }.getOrElse { cause ->
            lgr.warn(cause) { "读取Mod元数据失败:${mod.fileName}" }
            null
        }
    }
}

private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (cause: Throwable) {
    Result.failure(cause)
}
