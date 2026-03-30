package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.client.service.*
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.ModpackUploadResumeState
import calebxzhou.rdi.client.ui.ModpackUploadResumeStore
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.ModCard
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.service.ModService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Modpack Upload Screen - self-contained file selection and parsing.
 *
 * Flow:
 * 1. Show title row with "select file" button; rest of screen is blank.
 * 2. After user selects a file/dir, immediately read mods info and check local downloads.
 * 3. If all mods are already ready, enter edit/test/upload step directly.
 * 4. If some mods are missing, keep the screen on step 1 and let user click 下一步 to open task screen.
 * 5. "Upload version" mode: modpack name is read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackUploadScreen(
    onBack: () -> Unit,
    onOpenTask: (Task, Boolean, (() -> Unit)?) -> Unit = { _, _, _ -> },
    updateModpackId: ObjectId? = null,
    updateModpackName: String? = null
) {
    val scope = rememberCoroutineScope()
    val modsGridState = rememberLazyGridState()
    val restoredState = remember {
        ModpackUploadResumeStore.state.also { ModpackUploadResumeStore.state = null }
    }
    var shouldPersistState by remember { mutableStateOf(true) }
    val latestShouldPersist by rememberUpdatedState(shouldPersistState)

    // --- State: file selection & parsing ---
    var payload by remember { mutableStateOf(restoredState?.payload) }
    var selectedSourceName by remember { mutableStateOf(restoredState?.selectedSourceName) }
    var parseProgress by remember { mutableStateOf(restoredState?.parseProgress) }
    var errorText by remember { mutableStateOf(restoredState?.errorText) }
    var errorDialogVersion by remember { mutableIntStateOf(if (restoredState?.errorText != null) 1 else 0) }
    var isResolvingMods by remember { mutableStateOf(false) }

    // --- State: download task ---
    var pendingDownloadMods by remember { mutableStateOf(restoredState?.pendingDownloadMods ?: emptyList()) }

    // --- State: editable fields (basic info tab) ---
    var modpackName by remember { mutableStateOf(restoredState?.modpackName ?: "") }
    var versionName by remember { mutableStateOf(restoredState?.versionName ?: "") }
    var iconUrl by remember { mutableStateOf(restoredState?.iconUrl ?: "") }
    var sourceUrl by remember { mutableStateOf(restoredState?.sourceUrl ?: "") }
    var infoText by remember { mutableStateOf(restoredState?.infoText ?: "") }
    var mcVersionText by remember { mutableStateOf(restoredState?.mcVersionText ?: "") }
    var modloaderText by remember { mutableStateOf(restoredState?.modloaderText ?: "") }

    // --- State: mods list ---
    var mods by remember { mutableStateOf(restoredState?.mods ?: emptyList()) }

    // --- State: tabs ---
    var selectedTab by remember { mutableStateOf(0) }

    // --- State: upload step ---
    var uploadStep by remember { mutableStateOf<UploadStep>(UploadStep.Idle) }

    // --- State: tester ---
    var tester by remember { mutableStateOf<ModpackTester?>(null) }
    val testStatus = tester?.status?.collectAsState()
    val testPassSeconds = tester?.passSeconds?.collectAsState()
    val testedModsSignature = tester?.testedModsSignature?.collectAsState()
    val testConsoleState = remember { ConsoleState(4000) }

    fun persistIdleState(
        savedPendingDownloadMods: List<Mod> = pendingDownloadMods,
        savedParseProgress: String? = parseProgress,
        savedErrorText: String? = errorText
    ) {
        if (!latestShouldPersist || uploadStep != UploadStep.Idle) return
        ModpackUploadResumeStore.state = ModpackUploadResumeState(
            payload = payload,
            selectedSourceName = selectedSourceName,
            parseProgress = savedParseProgress,
            errorText = savedErrorText,
            pendingDownloadMods = savedPendingDownloadMods,
            modpackName = modpackName,
            versionName = versionName,
            iconUrl = iconUrl,
            sourceUrl = sourceUrl,
            infoText = infoText,
            mcVersionText = mcVersionText,
            modloaderText = modloaderText,
            mods = mods
        )
    }

    // Cleanup tester on dispose
    DisposableEffect(Unit) {
        onDispose {
            tester?.dispose(scope)
            if (latestShouldPersist && uploadStep == UploadStep.Idle) {
                persistIdleState()
            } else if (!latestShouldPersist) {
                ModpackUploadResumeStore.state = null
            }
        }
    }

    fun exitUploadScreen() {
        shouldPersistState = false
        ModpackUploadResumeStore.state = null
        onBack()
    }

    fun showError(message: String) {
        errorText = message
        errorDialogVersion += 1
    }

    // Reset error when tab changes
    LaunchedEffect(selectedTab) {
        errorText = null
    }

    // Scroll mods grid to top when switching to mods tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && mods.isNotEmpty()) {
            modsGridState.scrollToItem(0)
        }
    }

    fun modsNeedDownload(source: List<Mod>): List<Mod> =
        source.filterNot(ModService::isDownloadedModFileValid)

    fun resetSelectedPayload(nextPayload: UploadPayload, sourceName: String) {
        val oldPayload = payload
        if (oldPayload?.sourceDir != nextPayload.sourceDir) {
            runCatching { oldPayload?.sourceDir?.deleteRecursivelyNoSymlink() }
        }
        tester?.dispose(scope)
        tester = null
        payload = nextPayload
        selectedSourceName = sourceName
        modpackName = updateModpackName ?: nextPayload.sourceName.take(16)
        versionName = nextPayload.sourceVersion
        iconUrl = ""
        sourceUrl = ""
        infoText = ""
        mcVersionText = nextPayload.mcVersion.mcVer
        modloaderText = nextPayload.modloader.name
        mods = emptyList()
        pendingDownloadMods = emptyList()
        selectedTab = 0
        parseProgress = null
        errorText = null
        isResolvingMods = false
        uploadStep = UploadStep.Idle
    }

    fun enterEditing(currentPayload: UploadPayload) {
        currentPayload.mods = mods.toMutableList()
        tester?.dispose(scope)
        tester = ModpackTester(currentPayload)
        selectedTab = 0
        parseProgress = null
        isResolvingMods = false
        pendingDownloadMods = emptyList()
        errorText = null
        uploadStep = UploadStep.Editing
    }

    fun openModsDownloadTask(currentPayload: UploadPayload, targetMods: List<Mod>) {
        if (targetMods.isEmpty()) {
            errorText = null
            return
        }
        errorText = null
        onOpenTask(
            ModService.downloadModsTask(targetMods),
            true
        ) {
            val remaining = modsNeedDownload(mods)
            errorText = null
            pendingDownloadMods = remaining
            persistIdleState(
                savedPendingDownloadMods = remaining,
                savedParseProgress = null,
                savedErrorText = null
            )
        }
    }

    fun handleNextStep(currentPayload: UploadPayload) {
        val remaining = modsNeedDownload(mods)
        pendingDownloadMods = remaining
        if (remaining.isEmpty()) {
            errorText = null
            enterEditing(currentPayload)
        } else {
            openModsDownloadTask(currentPayload, remaining)
        }
    }

    fun loadModsAfterSelection(currentPayload: UploadPayload) {
        if (isResolvingMods) return
        isResolvingMods = true
        pendingDownloadMods = emptyList()
        errorText = null
        parseProgress = "正在读取Mod信息..."
        scope.launch(Dispatchers.IO) {
            val resolvedMods = loadUploadPayloadMods(
                payload = currentPayload,
                onProgress = { msg ->
                    scope.launch { parseProgress = msg }
                },
                onError = { msg ->
                    scope.launch { showError(msg) }
                }
            ) ?: run {
                scope.launch {
                    isResolvingMods = false
                    parseProgress = null
                }
                return@launch
            }

            val pendingMods = modsNeedDownload(resolvedMods)
            scope.launch {
                mods = resolvedMods
                currentPayload.mods = resolvedMods.toMutableList()
                isResolvingMods = false
                parseProgress = null
                if (pendingMods.isEmpty()) {
                    enterEditing(currentPayload)
                } else {
                    pendingDownloadMods = pendingMods
                }
            }
        }
    }

    fun startUpload(currentPayload: UploadPayload, name: String, version: String) {
        errorText = null
        uploadStep = UploadStep.Uploading
        scope.launch(Dispatchers.IO) {
            uploadModpack(
                payload = currentPayload,
                mods = mods,
                modpackName = name,
                versionName = version,
                iconUrl = iconUrl,
                sourceUrl = sourceUrl,
                info = infoText,
                updateModpackId = updateModpackId,
                onProgress = { scope.launch { parseProgress = it } },
                onError = { scope.launch { showError(it) } },
                onDone = { summary ->
                    scope.launch { uploadStep = UploadStep.Done(summary) }
                }
            )
        }
    }

    MainColumn {
        errorText?.let {
            key(errorDialogVersion) {
                AlertErr(it)
            }
        }
        when (uploadStep) {
            is UploadStep.Idle -> {
                // === STEP 1: File selection ===
                val currentPayload = payload
                val isProcessing = parseProgress != null || isResolvingMods
                val needsDownload = pendingDownloadMods.isNotEmpty()
                val canProceed = currentPayload != null && mods.isNotEmpty() && !isProcessing
                TitleRow(
                    title = updateModpackName?.let { "为整合包$it 上传新版" } ?: "上传整合包",
                    onBack = ::exitUploadScreen
                ) {
                    currentPayload?.let {
                        Text(selectedSourceName ?: it.sourceName)
                        Space8w()
                        if (canProceed) {
                            CircleIconButton(
                                "\uF054",
                                "下一步",
                                bgColor = MaterialColor.GREEN_900.color,
                                enabled = !isProcessing
                            ) {
                                handleNextStep(it)
                            }
                        }
                        Space8w()
                    }
                    parseProgress?.let {
                        Text(it)
                        Space8w()
                    }
                    CircleIconButton(
                        "\uF07C",
                        "选择整合包文件/目录",
                        enabled = !isProcessing
                    ) {
                        val file = pickModpackFile(
                            onError = ::showError
                        ) ?: return@CircleIconButton
                        errorText = null
                        parseProgress = "已选择: ${file.name}，正在读取..."
                        scope.launch(Dispatchers.IO) {
                            val parsed = parseUploadPayload(
                                file = file,
                                onProgress = { msg ->
                                    scope.launch { parseProgress = msg }
                                },
                                onError = { msg ->
                                    scope.launch { showError(msg); parseProgress = null }
                                }
                            ) ?: return@launch

                            scope.launch {
                                resetSelectedPayload(parsed, file.name)
                                loadModsAfterSelection(parsed)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val hint = when {
                        currentPayload == null && parseProgress == null -> "点击右上角按钮选择整合包文件或目录"
                        isResolvingMods || !parseProgress.isNullOrBlank() -> parseProgress ?: "正在处理整合包..."
                        currentPayload != null && mods.isNotEmpty() && !needsDownload -> "Mod已准备好，点击右上角下一步进入编辑"
                        needsDownload -> "共有${pendingDownloadMods.size}个Mod未下载，点击右上角下一步开始下载"
                        currentPayload != null -> "正在整理整合包信息..."
                        else -> ""
                    }
                    if (hint.isNotBlank()) {
                        Text(hint)
                    }
                }
            }

            is UploadStep.Editing -> {
                // === STEP 2: Tabs view ===
                val currentPayload = payload ?: return@MainColumn
                val currentTester = tester

                TitleRow(
                    title = updateModpackName?.let { "为整合包$it 上传新版" } ?: "确认整合包信息",
                    onBack = ::exitUploadScreen
                ) {
                    Text("上传此包的服务端可大幅提高成功率。")
                    CircleIconButton("","我有服务端"){

                    }
                    Space8w()
                    CircleIconButton("\uF058", "确认上传") {
                        //if(!DEBUG){
                            if (currentTester != null) {
                                val signatureNow = currentTester.currentModsSignature(mods)
                                val currentTestStatus = testStatus?.value
                                val currentTestedSig = testedModsSignature?.value
                                if (currentTestStatus != TestStatus.PASSED || currentTestedSig != signatureNow) {
                                    showError("请先在运行测试页完成测试并通过")
                                    return@CircleIconButton
                                }
                            }
                       // }
                        val name = modpackName.trim()
                        val version = versionName.trim()
                        if (name.isBlank()) {
                            showError("整合包名称不能为空")
                            return@CircleIconButton
                        }
                        if (version.isBlank()) {
                            showError("版本号不能为空")
                            return@CircleIconButton
                        }
                        startUpload(currentPayload, name, version)
                    }
                }

                Space8h()

                // Tab row
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
                        text = { Text("运行测试") }
                    )
                }
                Space8h()

                when (selectedTab) {
                    0 -> {
                        // === Basic Info Tab ===
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = modpackName,
                                    onValueChange = { modpackName = it },
                                    modifier = 250.wM,
                                    label = { Text("整合包名称") },
                                    enabled = updateModpackName == null
                                )
                                OutlinedTextField(
                                    value = versionName,
                                    onValueChange = { versionName = it },
                                    modifier = 120.wM,
                                    label = { Text("版本") }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("MC版本 $mcVersionText $modloaderText")
                            }
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
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                label = { Text("简介，若不填写则自动从发布链接读取") },
                                maxLines = 10
                            )
                        }
                    }

                    1 -> {
                        // === Mods Tab ===
                        val sortedMods = remember(mods) {
                            mods.asSequence()
                                .sortedBy { it.vo?.nameCn ?: it.vo?.name ?: it.slug }
                                .toList()
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(350.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            state = modsGridState,
                        ) {
                            items(sortedMods, key = { mod -> modStableKey(mod) }) { mod ->
                                val card = mod.vo
                                if (card != null) {
                                    card.ModCard(
                                        currentSide = mod.side,
                                        onSideChange = { newSide ->
                                            if (newSide == mod.side) return@ModCard
                                            val updatedMods = updateModSide(
                                                mods = mods,
                                                index = -1,
                                                modKey = modStableKey(mod),
                                                newSide = newSide
                                            )
                                            currentTester?.onModsChangedAfterManualEdit()
                                            mods = updatedMods
                                        }
                                    )
                                } else {
                                    Text(mod.slug)
                                }
                            }
                        }
                    }

                    2 -> {
                        // === Test Tab ===
                        if (currentTester == null) {
                            Text("请先选择整合包文件")
                            return@MainColumn
                        }
                        val statusText = when (testStatus?.value) {
                            TestStatus.NOT_RUN -> "未测试"
                            TestStatus.RUNNING -> "测试中"
                            TestStatus.PASSED -> "通过${testPassSeconds?.value?.let { "(${it}s)" } ?: ""}"
                            TestStatus.FAILED -> "失败"
                            TestStatus.STOPPED -> "已停止"
                            null -> "未测试"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("测试状态：$statusText")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircleIconButton(
                                    "\uF04B", "启动测试",
                                    bgColor = MaterialColor.GREEN_900.color
                                ) {
                                    if (currentTester.isRunning()) {
                                        showError("测试服务器已经在运行中")
                                        return@CircleIconButton
                                    }
                                    testConsoleState.clear()
                                    currentTester.startWithAutoFix(
                                        uiScope = scope,
                                        getMods = { mods },
                                        setMods = { updatedMods -> mods = updatedMods },
                                        onError = {it?.let { showError(it) }},
                                        appendLog = { line -> testConsoleState.append(line) }
                                    )
                                }
                                CircleIconButton(
                                    "\uF04D", "停止测试",
                                    bgColor = MaterialColor.RED_900.color
                                ) {
                                    currentTester.stop(
                                        uiScope = scope,
                                        appendLog = { line -> testConsoleState.append(line) }
                                    )
                                }
                            }
                        }
                        Space8h()
                        Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                            Console(
                                state = testConsoleState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            is UploadStep.Uploading -> {
                val currentPayload = payload
                TitleRow(
                    title = "上传整合包",
                    onBack = ::exitUploadScreen
                ) {
                    if (!errorText.isNullOrBlank() && currentPayload != null) {
                        CircleIconButton(
                            icon = "\uF2F9",
                            tooltip = "重试上传",
                            bgColor = MaterialColor.YELLOW_900.color
                        ) {
                            val name = modpackName.trim()
                            val version = versionName.trim()
                            if (name.isBlank()) {
                                showError("整合包名称不能为空")
                                return@CircleIconButton
                            }
                            if (version.isBlank()) {
                                showError("版本号不能为空")
                                return@CircleIconButton
                            }
                            startUpload(currentPayload, name, version)
                        }
                    }
                }
                Text("正在上传整合包，请耐心等待...")
                Space8h()
                parseProgress?.takeIf { it.isNotBlank() }?.let {
                    Text(it)
                }
            }

            is UploadStep.Done -> {
                val summary = (uploadStep as UploadStep.Done).summary
                TitleRow(
                    title = "上传完成",
                    onBack = ::exitUploadScreen
                ) {
                    CircleIconButton("\uF00C", "完成") {
                        exitUploadScreen()
                    }
                }
                Text(summary)
            }
        }
    }
}

private sealed class UploadStep {
    data object Idle : UploadStep()
    data object Editing : UploadStep()
    data object Uploading : UploadStep()
    data class Done(val summary: String) : UploadStep()
}

private fun updateModSide(
    mods: List<Mod>,
    index: Int,
    modKey: String,
    newSide: Mod.Side
): List<Mod> {
    if (mods.isEmpty()) return mods
    val updated = mods.toMutableList()
    val targetIndex = when {
        index in updated.indices && modStableKey(updated[index]) == modKey -> index
        else -> updated.indexOfFirst { modStableKey(it) == modKey }
    }
    if (targetIndex < 0) return mods
    val origin = updated[targetIndex]
    updated[targetIndex] = origin.copy(side = newSide).apply { vo = origin.vo }
    return updated
}

private fun modStableKey(mod: Mod): String =
    "${mod.platform}:${mod.projectId}:${mod.fileId}:${mod.hash}"

private fun pickModpackFile(
    onError: (String) -> Unit
): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择整合包 (ZIP / MRPACK / 已解压目录)"
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        currentDirectory = File("C:/Users/${System.getProperty("user.name")}/Downloads")
        fileFilter = FileNameExtensionFilter("整合包 (*.zip, *.mrpack)", "zip", "mrpack")
        isAcceptAllFileFilterUsed = true
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile
    if (!file.exists() || !(file.isFile || file.isDirectory)) {
        onError("未找到所选文件")
        return null
    }
    return file
}
