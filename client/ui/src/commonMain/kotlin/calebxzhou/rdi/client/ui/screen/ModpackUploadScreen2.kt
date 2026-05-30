package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.CLIENT_TEST_SUCCESS_MARKER
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ClientModpackTester
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.service.LoadedLocalModpack
import calebxzhou.rdi.client.service.LoadedServerPackResult
import calebxzhou.rdi.client.service.ModpackTester
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.TestStatus
import calebxzhou.rdi.client.service.UploadPayload
import calebxzhou.rdi.client.service.createUploadModpackTask2
import calebxzhou.rdi.client.service.loadServerPackMods
import calebxzhou.rdi.client.service.modpackUploadTaskKey
import calebxzhou.rdi.client.service.toUploadPayload
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.ModGrid
import calebxzhou.rdi.client.ui.comp.ModpackCategorySelector
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.client.ui.comp.Task2DetailDialog
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.IGNORE_MODPACK_TEST
import calebxzhou.rdi.common.model.LoadProgress
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.rdi.common.model.isPlatformCf
import calebxzhou.rdi.common.service.ModpackModProcessor
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.lgr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.jar.JarFile

private enum class UploadMode { CREATE, UPDATE }

private data class PendingMissingModDownload(
    val usage: String,
    val mods: List<Mod>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackUploadScreen2(
    onBack: () -> Unit,
    onUploadSubmitted: (String) -> Unit = { onBack() }
) {
    var title by remember { mutableStateOf("上传整合包") }
    val scope = rememberCoroutineScope()
    var errorText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var loadedModpack by remember { mutableStateOf<LoadedLocalModpack?>(null) }
    var modpackName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf<List<Modpack.Category>>(emptyList()) }
    var iconUrl by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }
    var mcVersionText by remember { mutableStateOf("") }
    var modloaderText by remember { mutableStateOf("") }
    var mods by remember { mutableStateOf<List<Mod>>(emptyList()) }
    var serverPackName by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var progressFraction by remember { mutableStateOf<Float?>(null) }
    var downloadTaskRunId by remember { mutableStateOf<String?>(null) }
    var clientTester by remember { mutableStateOf<ClientModpackTester?>(null) }
    var serverTester by remember { mutableStateOf<ModpackTester?>(null) }
    var uploadMode by remember { mutableStateOf(UploadMode.CREATE) }
    var uploadedModpacks by remember { mutableStateOf<List<Modpack.BriefVo>>(emptyList()) }
    var uploadedModpacksLoading by remember { mutableStateOf(false) }
    var uploadedModpacksLoaded by remember { mutableStateOf(false) }
    var selectedUpdateTarget by remember { mutableStateOf<Modpack.BriefVo?>(null) }
    var showUploadModeDialog by remember { mutableStateOf(false) }
    var pendingMissingModDownload by remember { mutableStateOf<PendingMissingModDownload?>(null) }
    val taskEntries by ClientTaskManager.entries.collectAsState()
    val downloadTaskEntry = remember(taskEntries, downloadTaskRunId) {
        downloadTaskRunId?.let { runId -> taskEntries.firstOrNull { it.runId == runId } }
    }
    val clientTestStatus = clientTester?.status?.collectAsState()
    val clientTestPassSeconds = clientTester?.passSeconds?.collectAsState()
    val clientTestConsoleState = remember { ConsoleState(4000) }
    val serverTestStatus = serverTester?.status?.collectAsState()
    val serverTestPassSeconds = serverTester?.passSeconds?.collectAsState()
    val serverTestConsoleState = remember { ConsoleState(4000) }
    val allowUploadWithoutTests = DEBUG || IGNORE_MODPACK_TEST
    val canSubmitUpload = IGNORE_MODPACK_TEST || (
            !loading &&
            downloadTaskRunId == null &&
            serverTester?.isRunning() == false &&
            clientTester?.isRunning() == false &&
            (uploadMode == UploadMode.CREATE || selectedUpdateTarget != null))
    val canSelectServerPack = editMode &&
            !loading &&
            downloadTaskRunId == null &&
            serverTester?.isRunning() != true &&
            clientTester?.isRunning() != true

    fun resetTestState() {
        clientTestConsoleState.clear()
        serverTestConsoleState.clear()
    }

    fun loadUploadedModpacks(openDialogAfterLoad: Boolean = false) {
        if (uploadedModpacksLoading) return
        if (uploadedModpacksLoaded) {
            if (openDialogAfterLoad) showUploadModeDialog = true
            return
        }
        scope.launch {
            uploadedModpacksLoading = true
            runCatching {
                server.makeRequest<List<Modpack>>("modpack/my").data
                    .orEmpty()
                    .sortedByDescending { pack -> pack.versions.maxOfOrNull { it.time } ?: 0L }
                    .map { it.toLocalBriefVo() }
            }.onSuccess { packs ->
                uploadedModpacks = packs
                uploadedModpacksLoaded = true
                if (openDialogAfterLoad) {
                    showUploadModeDialog = true
                }
            }.onFailure { error ->
                lgr.error { error }
                errorText = error.message ?: "读取已上传整合包失败"
            }
            uploadedModpacksLoading = false
        }
    }

    fun uploadSupportedConfiguredJavaMajors(
        mcVersion: calebxzhou.rdi.common.model.McVersion
    ): List<Int> = mcVersion.supportedJreVers

    fun hasConfiguredUploadJavaPath(major: Int): Boolean = when (major) {
        8 -> !CONF.jre8Path?.trim().isNullOrEmpty()
        21 -> !CONF.jre21Path?.trim().isNullOrEmpty()
        25 -> !CONF.jre25Path?.trim().isNullOrEmpty()
        else -> false
    }

    fun isCurrentJavaSupportedForUpload(
        currentMajor: Int,
        mcVersion: calebxzhou.rdi.common.model.McVersion
    ): Boolean = mcVersion.supportsCurrentJava(currentMajor)

    fun uploadRuntimeRequirementMessageOrNull(mcVersion: calebxzhou.rdi.common.model.McVersion): String? {
        if (!isDesktop) return null
        val currentMajor = currentPlatformJavaMajor()
        if (currentMajor != null && isCurrentJavaSupportedForUpload(currentMajor, mcVersion)) {
            return null
        }
        val supportedConfiguredMajors = uploadSupportedConfiguredJavaMajors(mcVersion)
        if (supportedConfiguredMajors.any(::hasConfiguredUploadJavaPath)) {
            return null
        }
        val javaText = supportedConfiguredMajors.joinToString("或") { "Java$it" }
        val configHint = if (supportedConfiguredMajors.size == 1) {
            "请前往群文件下载安装包，然后在设置界面中选择${javaText}路径"
        } else {
            "请前往群文件下载安装包，然后在设置界面中配置任意一个"
        }
        return "MC${mcVersion.mcVer}需要${javaText}。$configHint"
    }

    fun ensureUploadRuntimeReady(mcVersion: calebxzhou.rdi.common.model.McVersion): Boolean {
        val requirementMessage = uploadRuntimeRequirementMessageOrNull(mcVersion) ?: return true
        errorText = requirementMessage
        return false
    }


    fun enterEditMode() {
        loadedModpack?.let {
            if (!ensureUploadRuntimeReady(it.mcVersion)) return
            clientTester?.dispose(scope)
            serverTester?.dispose(scope)
            if (isDesktop) {
                clientTester = ClientModpackTester(it.copy(mods = mods))
                serverTester = ModpackTester(it.copy(mods = mods))
            } else {
                clientTester = null
                serverTester = null
            }
        }
        resetTestState()
        editMode = true
    }

    fun handleBack() {
        scope.launch {
            loading = true
            title = "清理缓存中..."
            withContext(Dispatchers.IO) {
                runCatching {
                    ClientDirs.packProcDir.deleteRecursivelyNoSymlink()
                    ClientDirs.packProcDir.mkdirs()
                }
            }
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            clientTester?.dispose(scope)
            serverTester?.dispose(scope)
        }
    }

    LaunchedEffect(downloadTaskEntry?.status) {
        when (downloadTaskEntry?.status) {
            Task2Status.DONE -> {
                downloadTaskRunId = null
            }

            Task2Status.FAILED -> {
                errorText = downloadTaskEntry?.snapshot?.errorMessage ?: "下载任务失败"
                downloadTaskRunId = null
            }

            Task2Status.CANCELLED -> {
                downloadTaskRunId = null
            }

            else -> Unit
        }
    }

    fun finishLoading() {
        loading = false
        progressText = null
        progressFraction = null
    }

    fun mapProgress(progress: LoadProgress) {
        when (progress) {
            is LoadProgress.Phase -> {
                progressText = progress.text
                progressFraction = null
            }

            is LoadProgress.Percent -> {
                progressText = progress.text
                progressFraction = progress.fraction.coerceIn(0f, 1f)
            }

            is LoadProgress.Warn -> {
                progressText = progress.text
            }
        }
    }

    fun normalizeVersionNameInput(value: String): String =
        value.replace(' ', '_')

    fun modsNeedDownload(source: List<Mod>): List<Mod> =
        source.filterNot(ModService::isDownloadedModFileValid)

    fun submitMissingModDownload(missingMods: List<Mod>) {
        if (missingMods.isEmpty()) return
        if (downloadTaskRunId != null) {
            errorText = "已有下载任务正在运行"
            return
        }
        downloadTaskRunId = ClientTaskManager.submit(ModService.downloadModsTask2(missingMods))
    }

    fun requireModsDownloadedFor(usage: String, onReady: () -> Unit) {
        scope.launch {
            val missingMods = runCatching {
                withContext(Dispatchers.IO) { modsNeedDownload(mods) }
            }.getOrElse { error ->
                lgr.error { error }
                errorText = error.message ?: "检查缺失Mod失败"
                return@launch
            }
            if (missingMods.isEmpty()) {
                onReady()
            } else {
                pendingMissingModDownload = PendingMissingModDownload(usage, missingMods)
            }
        }
    }

    fun applyServerPack(serverPack: LoadedServerPackResult) {
        val processedMods = ModpackModProcessor.processMods(mergeClientAndServerMods(mods, serverPack.mods))
        mods = processedMods
        loadedModpack = loadedModpack?.copy(
            mods = processedMods,
            serverExtraFiles = serverPack.serverExtraFiles
        )
        serverPackName = buildString {
            append("已选择服务端(")
            append(serverPack.mods.size)
            append("个mod")
            if (serverPack.serverExtraFiles.isNotEmpty()) {
                append(" + ")
                append(serverPack.serverExtraFiles.size)
                append("个额外文件")
            }
            append(")")
        }
        clientTester?.onModsChangedAfterManualEdit()
        serverTester?.onModsChangedAfterManualEdit()
        clientTester?.markPassed(mods)
        serverTester?.markPassed(mods)
    }

    fun defaultCurseForgeUnknownMods(source: List<Mod>): List<Mod> {
        if (source.none { it.isPlatformCf && it.side == Mod.Side.UNKNOWN }) return source
        return source.map { mod ->
            if (mod.isPlatformCf && mod.side == Mod.Side.UNKNOWN) {
                mod.toUiMod().withSide(Mod.Side.BOTH).toMod()
            } else mod
        }
    }

    fun chooseCreateMode() {
        uploadMode = UploadMode.CREATE
        selectedUpdateTarget = null
        modpackName = loadedModpack?.packName ?: modpackName
        selectedCategories = emptyList()
    }

    fun chooseUpdateMode(target: Modpack.BriefVo) {
        uploadMode = UploadMode.UPDATE
        selectedUpdateTarget = target
        modpackName = target.name
        selectedCategories = target.categories
        showUploadModeDialog = false
    }

    fun startUpdateModeSelection() {
        uploadMode = UploadMode.UPDATE
        selectedCategories = selectedUpdateTarget?.categories ?: emptyList()
        loadUploadedModpacks(openDialogAfterLoad = true)
    }

    fun openUploadModeDialog() {
        loadUploadedModpacks(openDialogAfterLoad = true)
    }

    fun closeUploadModeDialog() {
        showUploadModeDialog = false
        if (uploadMode == UploadMode.UPDATE && selectedUpdateTarget == null) {
            chooseCreateMode()
        }
    }

    fun buildUploadPayloadOrNull(): UploadPayload? {
        val current = loadedModpack ?: run {
            errorText = "请先选择整合包文件"
            return null
        }
        val name = modpackName.trim()
        val version = versionName.trim()
        if (name.isBlank()) {
            errorText = "整合包名称不能为空"
            return null
        }
        if (version.isBlank()) {
            errorText = "版本号不能为空"
            return null
        }
        val currentClientTester = clientTester
        val currentServerTester = serverTester
        if (serverPackName == null) {
            if (!allowUploadWithoutTests && isDesktop &&
                currentClientTester != null &&
                currentClientTester.status.value != TestStatus.PASSED
            ) {
                errorText = "请先完成客户端测试并通过"
                return null
            }
            if (!allowUploadWithoutTests && isDesktop &&
                currentServerTester != null &&
                currentServerTester.status.value != TestStatus.PASSED
            ) {
                errorText = "请先完成服务端测试并通过"
                return null
            }
        }
        if (uploadMode == UploadMode.UPDATE && selectedUpdateTarget == null) {
            errorText = "请选择要更新的已有整合包"
            return null
        }
        return current.copy(
            packName = if (uploadMode == UploadMode.UPDATE) selectedUpdateTarget?.name ?: name else name,
            packVersion = version,
            mods = ModpackModProcessor.processMods(mods)
        ).toUploadPayload()
    }

    fun startUploadTask() {
        val payload = buildUploadPayloadOrNull() ?: return
        if (DEBUG) {
            mods = payload.mods
        }
        val runId = ClientTaskManager.submit(
            task = createUploadModpackTask2(
                payload = payload,
                mods = payload.mods,
                modpackName = modpackName.trim(),
                versionName = versionName.trim(),
                iconUrl = iconUrl.trim().ifBlank { null },
                sourceUrl = sourceUrl.trim().ifBlank { null },
                info = infoText.trim().ifBlank { null },
                categories = Modpack.normalizeCategories(selectedCategories),
                updateModpackId = selectedUpdateTarget?.id.takeIf { uploadMode == UploadMode.UPDATE }
            ),
            dedupeKey = modpackUploadTaskKey(
                updateModpackId = selectedUpdateTarget?.id.takeIf { uploadMode == UploadMode.UPDATE },
                modpackName = selectedUpdateTarget?.name.takeIf { uploadMode == UploadMode.UPDATE } ?: modpackName,
                versionName = versionName
            )
        )
        if (runId.isNotBlank()) {
            onUploadSubmitted(runId)
        }
    }

    fun startClientPackSelection() {
        scope.launch {
            val file = pickLocalModpackFile() ?: return@launch
            errorText = null
            loading = true
            progressText = "已选择: ${file.name}"
            progressFraction = null
            val loadResult = ModpackService.load(file) { progress ->
                scope.launch {
                    mapProgress(progress)
                }
            }.getOrElse { error ->
                finishLoading()
                lgr.error { error }
                error.printStackTrace()
                errorText = error.message ?: "读取整合包失败"
                return@launch
            }
            if (!ensureUploadRuntimeReady(loadResult.mcVersion)) {
                finishLoading()
                return@launch
            }

            val processedMods = ModpackModProcessor.processMods(defaultCurseForgeUnknownMods(loadResult.mods))
            loadedModpack = loadResult.copy(mods = processedMods)
            modpackName = loadResult.packName
            versionName = normalizeVersionNameInput(loadResult.packVersion)
            iconUrl = ""
            sourceUrl = ""
            infoText = ""
            selectedCategories = emptyList()
            mcVersionText = loadResult.mcVersion.mcVer
            modloaderText = loadResult.modloader.name
            mods = processedMods
            serverPackName = null
            uploadMode = UploadMode.CREATE
            selectedUpdateTarget = null
            selectedTab = 0
            finishLoading()
            enterEditMode()
        }
    }

    fun selectServerPack() {
        scope.launch {
            val file = pickLocalDirectory("选择服务端安装目录") ?: return@launch
            errorText = null
            loading = true
            progressText = "已选择服务端目录: ${file.name}"
            progressFraction = null
            val serverPack = runCatching {
                loadServerPackMods(file, mods) { progress ->
                    scope.launch { mapProgress(progress) }
                }.getOrThrow()
            }.getOrElse { error ->
                finishLoading()
                lgr.error { error }
                errorText = error.message ?: "读取服务端目录失败"
                return@launch
            }
            applyServerPack(serverPack)
            finishLoading()
            enterEditMode()
        }
    }

    fun startDownloadTestServerTask() {
        val current = loadedModpack ?: run {
            errorText = "请先选择整合包文件"
            return
        }
        if (downloadTaskRunId != null) {
            errorText = "已有下载任务正在运行"
            return
        }
        downloadTaskRunId = ClientTaskManager.submit(
            GameService.downloadTestServerTask2(current.mcVersion, current.modloader)
        )
    }

    fun startServerTestAfterModsReady() {
        val currentTester = serverTester ?: return
        serverTestConsoleState.clear()
        currentTester.startWithAutoFix(
            uiScope = scope,
            getMods = { mods },
            setMods = { updatedMods -> mods = updatedMods },
            onError = { msg -> msg?.let { errorText = it } },
            appendLog = { line -> serverTestConsoleState.append(line) }
        )
    }

    fun startServerTest() {
        if (!isDesktop) {
            errorText = "当前平台暂不支持服务端测试"
            return
        }
        val currentTester = serverTester
        if (currentTester == null) {
            errorText = "请先选择整合包文件"
        } else if (currentTester.isRunning()) {
            errorText = "测试服务器已经在运行中"
        } else {
            requireModsDownloadedFor("服务端测试") {
                startServerTestAfterModsReady()
            }
        }
    }

    fun stopServerTest() {
        serverTester?.stop(
            uiScope = scope,
            appendLog = { line -> serverTestConsoleState.append(line) }
        )
    }

    fun startClientTestAfterModsReady() {
        val currentTester = clientTester ?: return
        clientTestConsoleState.clear()
        currentTester.start(
            uiScope = scope,
            getMods = { mods },
            onError = { msg -> msg?.let { errorText = it } },
            appendLog = { line -> clientTestConsoleState.append(line) }
        )
    }

    fun startClientTest() {
        if (!isDesktop) {
            errorText = "当前平台暂不支持客户端测试"
            return
        }
        val currentTester = clientTester
        if (currentTester == null) {
            errorText = "请先选择整合包文件"
        } else if (currentTester.isRunning()) {
            errorText = "测试客户端已经在运行中"
        } else {
            requireModsDownloadedFor("客户端测试") {
                startClientTestAfterModsReady()
            }
        }
    }

    fun stopClientTest() {
        clientTester?.stop(
            uiScope = scope,
            appendLog = { line -> clientTestConsoleState.append(line) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            errorText?.let { AlertErr(it) }
            if (IGNORE_MODPACK_TEST) {
                AlertWarn("已启用rdi.ignoreModpackTest=true，当前允许跳过客户端测试(client test)和服务端测试(server test)直接上传")
            }

            TitleRow(
                title = title,
                onBack = ::handleBack
            ) {
                if (editMode) {
                    if (serverPackName == null) {
                        Text("如果选择了整合包服务端，就不需要进行测试。")
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF07C",
                        if (serverPackName == null) "选择整合包服务端" else "重选服务端",
                        enabled = canSelectServerPack,
                        onClick = ::selectServerPack
                    )
                    serverPackName?.let {
                        Space8w()
                        Text(it)
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF019",
                        "下载测试服务端",
                        enabled = isDesktop && loadedModpack != null && downloadTaskRunId == null,
                        onClick = ::startDownloadTestServerTask
                    )
                    Space8w()
                    CircleIconButton(
                        "\uF058",
                        "开始传包",
                        bgColor = MaterialColor.GREEN_900.color,
                        enabled = canSubmitUpload,
                        onClick = ::startUploadTask
                    )

                }
            }

            if (!editMode) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircleIconButton(
                        icon = "\uF07C",
                        tooltip = "选择客户端安装包",
                        enabled = !loading,
                        onClick = ::startClientPackSelection
                    )
                }
            } else {
                Space8h()
                TabRow(selectedTabIndex = selectedTab, backgroundColor = Color.White) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("基本信息") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Mod列表(${mods.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("客户端测试") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("服务端测试") }
                    )
                }
                Space8h()

                when (selectedTab) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("上传模式")
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = uploadMode == UploadMode.CREATE,
                                                onClick = ::chooseCreateMode
                                            )
                                            Text("传新包")
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = uploadMode == UploadMode.UPDATE,
                                                onClick = ::startUpdateModeSelection
                                            )
                                            Text("更新已有包")
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 40.dp)
                                            .border(1.dp, MaterialColor.GRAY_300.color, MaterialTheme.shapes.small)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            when (uploadMode) {
                                                UploadMode.CREATE -> "传新包"
                                                UploadMode.UPDATE -> selectedUpdateTarget?.name ?: "未选择整合包"
                                            },
                                            color = if (uploadMode == UploadMode.UPDATE && selectedUpdateTarget == null) {
                                                MaterialColor.GRAY_500.color
                                            } else {
                                                MaterialColor.GRAY_900.color
                                            }
                                        )
                                    }
                                }
                                Space8w()
                                CircleIconButton(
                                    "\uE8B8",
                                    "选择整合包",
                                    enabled = !loading && !uploadedModpacksLoading && uploadMode == UploadMode.UPDATE,
                                    onClick = ::openUploadModeDialog
                                )
                            }
                            Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = modpackName,
                                    onValueChange = { modpackName = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("整合包名称") },
                                    enabled = uploadMode == UploadMode.CREATE
                                )
                                OutlinedTextField(
                                    value = versionName,
                                    onValueChange = { versionName = normalizeVersionNameInput(it) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("版本") },
                                    singleLine = true
                                )
                            }
                            Text("MC版本 $mcVersionText $modloaderText")
                            OutlinedTextField(
                                value = iconUrl,
                                onValueChange = { iconUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("图标链接，可选") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sourceUrl,
                                onValueChange = { sourceUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("发布链接，可选") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = infoText,
                                onValueChange = { infoText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("简介") },
                                maxLines = 10
                            )
                            Text("分类 最多${Modpack.MAX_CATEGORY_COUNT}个")
                            ModpackCategorySelector(
                                selected = selectedCategories,
                                onSelectedChange = { selectedCategories = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            loadedModpack?.let {
                                Text("来源类型 ${it.sourceType.name}")
                            }
                        }
                    }

                    1 -> {
                        val uiMods = remember(mods) { mods.toUiMods() }
                        ModGrid(
                            mods = uiMods,
                            modifier = Modifier.fillMaxSize(),
                            emptyText = "没有可显示的mod",
                            onSideChange = { uiMod, newSide ->
                                if (newSide != uiMod.side) {
                                    mods = updateModSide(
                                        mods = mods,
                                        modKey = modStableKey(uiMod.mod),
                                        newSide = newSide
                                    )
                                    clientTester?.onModsChangedAfterManualEdit()
                                    serverTester?.onModsChangedAfterManualEdit()
                                }
                            }
                        )
                    }

                    2 -> {
                        TestConsolePane(
                            statusText = testStatusText(
                                clientTestStatus?.value ?: TestStatus.NOT_RUN,
                                clientTestPassSeconds?.value
                            ),
                            consoleState = clientTestConsoleState,
                            "创建单机存档，在聊天框发送 $CLIENT_TEST_SUCCESS_MARKER",
                            onStart = ::startClientTest,
                            onStop = ::stopClientTest
                        )
                    }

                    3 -> {
                        TestConsolePane(
                            statusText = testStatusText(
                                serverTestStatus?.value ?: TestStatus.NOT_RUN,
                                serverTestPassSeconds?.value
                            ),
                            consoleState = serverTestConsoleState,
                            "",
                            onStart = ::startServerTest,
                            onStop = ::stopServerTest
                        )
                    }
                }
            }

            if (loading) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("正在读取整合包") },
                    text = {
                        Column {
                            Text(progressText ?: "正在处理...")
                            if (progressFraction != null) {
                                LinearProgressIndicator(
                                    progress = progressFraction ?: 0f,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                                Text(
                                    text = "${((progressFraction ?: 0f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.caption,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                            }
                        }
                    },
                    buttons = {}
                )
            }

        }

        downloadTaskEntry?.let { entry ->
            Task2DetailDialog(
                entry = entry,
                onClose = {}
            )
        }

        pendingMissingModDownload?.let { pending ->
            ConfirmDialog(
                title = "下载缺失Mod",
                message = "${pending.usage}需要先下载缺失Mod${pending.mods.size}个。下载完成后再启动${pending.usage}。",
                onConfirm = {
                    submitMissingModDownload(pending.mods)
                    pendingMissingModDownload = null
                },
                onDismiss = { pendingMissingModDownload = null }
            )
        }

        if (showUploadModeDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(2f / 3f)
                        .fillMaxHeight(2f / 3f),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                    elevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("选择整合包")
                            TextButton(onClick = ::closeUploadModeDialog) {
                                Text("关闭")
                            }
                        }
                        if (uploadedModpacks.isEmpty()) {
                            Text("你还没有已上传整合包")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(280.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uploadedModpacks, key = { it.id.toHexString() }) { modpack ->
                                    modpack.ModpackCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { chooseUpdateMode(modpack) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestConsolePane(
    statusText: String,
    consoleState: ConsoleState,
    tipText: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("测试状态：$statusText")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tipText)
                Space8w()
                CircleIconButton(
                    "\uF04B",
                    "启动测试",
                    bgColor = MaterialColor.GREEN_900.color,
                    onClick = onStart
                )
                CircleIconButton(
                    "\uF04D",
                    "停止测试",
                    bgColor = MaterialColor.RED_900.color,
                    onClick = onStop
                )
            }
        }
        Space8h()
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            Console(
                state = consoleState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun testStatusText(status: TestStatus, passSeconds: String?): String = when (status) {
    TestStatus.NOT_RUN -> "未测试"
    TestStatus.RUNNING -> "测试中"
    TestStatus.PASSED -> "通过${passSeconds?.let { "(${it}s)" } ?: ""}"
    TestStatus.FAILED -> "失败"
    TestStatus.STOPPED -> "已停止"
}

private fun updateModSide(
    mods: List<Mod>,
    modKey: String,
    newSide: Mod.Side
): List<Mod> {
    if (mods.isEmpty()) return mods
    val updated = mods.toMutableList()
    val targetIndex = updated.indexOfFirst { modStableKey(it) == modKey }
    if (targetIndex < 0) return mods
    val origin = updated[targetIndex]
    updated[targetIndex] = origin.toUiMod().withSide(newSide).toMod()
    return updated
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
    categories = categories
)

private data class ModMergeEntry(
    val mod: Mod,
    val strictKey: String,
    val slugKey: String?
)

private fun strictModMergeKey(
    mod: Mod,
    installedModIdCache: MutableMap<String, String?>
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

private fun Mod.asMergeEntry(installedModIdCache: MutableMap<String, String?>): ModMergeEntry =
    ModMergeEntry(
        mod = this,
        strictKey = strictModMergeKey(this, installedModIdCache),
        slugKey = slugModMergeKey(this)
    )

private fun mergeAsBoth(clientMod: Mod, serverMod: Mod): Mod {
    val mergedDownloadUrls = (clientMod.downloadUrls + serverMod.downloadUrls).distinct()
    return clientMod.copy(
        side = Mod.Side.BOTH,
        downloadUrls = mergedDownloadUrls
    ).also { merged ->
        merged.vo = (clientMod.vo ?: serverMod.vo)?.copy(side = Mod.Side.BOTH)
        merged.file = clientMod.file ?: serverMod.file
    }
}

private fun mergeClientAndServerMods(
    clientMods: List<Mod>,
    serverMods: List<Mod>
): List<Mod> {
    val installedModIdCache = mutableMapOf<String, String?>()
    val clientEntries = clientMods.map { it.asMergeEntry(installedModIdCache) }
    val serverEntries = serverMods.map { it.asMergeEntry(installedModIdCache) }
    val serverByStrictKey = serverEntries.associateBy { it.strictKey }
    val uniqueClientSlugKeys = clientEntries
        .mapNotNull { it.slugKey }
        .groupingBy { it }
        .eachCount()
        .filterValues { it == 1 }
        .keys
    val serverByUniqueSlugKey = serverEntries
        .filter { it.slugKey != null }
        .groupBy { it.slugKey!! }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val matchedServerEntries = mutableSetOf<ModMergeEntry>()
    val merged = mutableListOf<Mod>()

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
                    "按slug合并跨来源mod: " +
                        "${clientEntry.mod.platform}:${clientEntry.mod.projectId} ${clientEntry.mod.slug} + " +
                        "${serverMatch.mod.platform}:${serverMatch.mod.projectId} ${serverMatch.mod.slug}"
                }
            }
            matchedServerEntries += serverMatch
            merged += mergeAsBoth(clientEntry.mod, serverMatch.mod)
        } else {
            merged += clientEntry.mod.toUiMod().withSide(Mod.Side.CLIENT).toMod()
        }
    }

    serverEntries.forEach { serverEntry ->
        if (serverEntry in matchedServerEntries) return@forEach
        merged += serverEntry.mod.toUiMod().withSide(Mod.Side.SERVER).toMod()
    }

    return merged
        .distinctBy { strictModMergeKey(it, installedModIdCache) }
        .sortedBy { it.slug.lowercase() }
}

private fun readInstalledModId(
    mod: Mod,
    installedModIdCache: MutableMap<String, String?>
): String? {
    val cacheKey = mod.targetPath.toString()
    return installedModIdCache.getOrPut(cacheKey) {
        runCatching {
            val file = mod.targetPath.toFile().takeIf { it.exists() && it.isFile } ?: return@getOrPut null
            ModService.run {
                JarFile(file).use { jar ->
                    jar.readModMeta()?.primaryModId?.trim()?.lowercase()?.ifBlank { null }
                }
            }
        }.getOrNull()
    }
}
