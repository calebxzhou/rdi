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
import calebxzhou.rdi.client.service.*
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.ModCard
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.TaskProgress
import calebxzhou.rdi.common.service.BackgroundTaskRunner.start
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
 * 2. After user selects a file/dir, parse manifest → fill basic info immediately,
 *    then load mods info in background. Show 3 tabs: Basic Info, Mods, Test.
 * 3. When mods are loaded, if any need downloading, auto-download them in background
 *    and show progress in title row.
 * 4. "Upload version" mode: modpack name is read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackUploadScreen(
    onBack: () -> Unit,
    updateModpackId: ObjectId? = null,
    updateModpackName: String? = null
) {
    val scope = rememberCoroutineScope()
    val modsGridState = rememberLazyGridState()

    // --- State: file selection & parsing ---
    var payload by remember { mutableStateOf<UploadPayload?>(null) }
    var parseProgress by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var modsLoaded by remember { mutableStateOf(false) }

    // --- State: download progress ---
    var downloadProgress by remember { mutableStateOf<TaskProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadFailed by remember { mutableStateOf(false) }

    // --- State: editable fields (basic info tab) ---
    var modpackName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("") }
    var iconUrl by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }
    var mcVersionText by remember { mutableStateOf("") }
    var modloaderText by remember { mutableStateOf("") }

    // --- State: mods list ---
    var mods by remember { mutableStateOf<List<Mod>>(emptyList()) }

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

    // Cleanup tester on dispose
    DisposableEffect(Unit) {
        onDispose {
            tester?.dispose(scope)
        }
    }

    // Reset error when tab changes
    LaunchedEffect(selectedTab) {
        errorText = null
    }

    // Scroll mods grid to top when switching to mods tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && modsLoaded) {
            modsGridState.scrollToItem(0)
        }
    }

    fun modsNeedDownload(source: List<Mod>): List<Mod> =
        source.filter { mod -> !DL_MOD_DIR.resolve(mod.fileName).exists() }

    fun startModsDownload(targetMods: List<Mod>) {
        if (isDownloading) return
        if (targetMods.isEmpty()) {
            downloadFailed = false
            return
        }
        isDownloading = true
        downloadFailed = false
        errorText = null
        scope.launch(Dispatchers.IO) {
            try {
                val task = ModService.downloadModsTask(targetMods)
                task.start { progress ->
                    scope.launch {
                        downloadProgress = progress
                    }
                }
                val remaining = modsNeedDownload(targetMods)
                scope.launch {
                    if (remaining.isNotEmpty()) {
                        errorText = "下载未完成，仍有${remaining.size}个Mod未下载"
                        downloadFailed = true
                    } else {
                        downloadFailed = false
                    }
                    downloadProgress = null
                    isDownloading = false
                }
            } catch (e: Exception) {
                val remaining = modsNeedDownload(targetMods)
                scope.launch {
                    errorText = "下载失败: ${e.message}"
                    downloadFailed = remaining.isNotEmpty()
                    downloadProgress = null
                    isDownloading = false
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
                onError = { scope.launch { errorText = it } },
                onDone = { summary ->
                    scope.launch { uploadStep = UploadStep.Done(summary) }
                }
            )
        }
    }

    // When mods finish loading, check if download is needed and auto-download in background
    LaunchedEffect(modsLoaded, payload) {
        if (!modsLoaded || payload == null || isDownloading) return@LaunchedEffect
        val pendingMods = modsNeedDownload(mods)
        if (pendingMods.isNotEmpty()) {
            startModsDownload(pendingMods)
        }
    }

    MainColumn {
        when (uploadStep) {
            is UploadStep.Idle -> {
                // === STEP 1: File selection ===
                TitleRow(
                    title = updateModpackName?.let { "为整合包$it 上传新版" } ?: "上传整合包",
                    onBack = onBack
                ) {
                    downloadProgress?.let { progress ->
                        val percentText = progress.fraction?.let { fraction ->
                            val pct = (fraction.coerceIn(0f, 1f) * 100f).toInt()
                            " $pct%"
                        } ?: ""
                        Text("${progress.message}$percentText")
                        Space8w()
                    }
                    errorText?.let {
                        Text(it, color = MaterialTheme.colors.error)
                    }
                    Space8w()
                    if (downloadFailed && !isDownloading) {
                        CircleIconButton(
                            icon = "\uF2F9",
                            tooltip = "重试下载失败的Mod",
                            bgColor = MaterialColor.YELLOW_900.color,
                        ) {
                            startModsDownload(modsNeedDownload(mods))
                        }
                        Space8w()
                    }
                    parseProgress?.let {
                        Text(it)
                        Space8w()
                    }
                    val isProcessing = parseProgress != null || isDownloading
                    CircleIconButton(
                        "\uF07C",
                        "选择整合包文件/目录",
                        enabled = !isProcessing
                    ) {
                        val file = pickModpackFile(
                            onError = { msg -> errorText = msg }
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
                                    scope.launch { errorText = msg; parseProgress = null }
                                }
                            ) ?: return@launch

                            scope.launch {
                                val p = parsed.payload
                                payload = p
                                modpackName = updateModpackName ?: p.sourceName.take(16)
                                versionName = p.sourceVersion
                                mcVersionText = p.mcVersion.mcVer
                                modloaderText = p.modloader.name
                                mods = p.mods
                                modsLoaded = true
                                parseProgress = null
                                tester = ModpackTester(p)
                                uploadStep = UploadStep.Editing
                            }
                        }
                    }
                }
                // Rest of screen is blank (step 1)
                if (payload == null && parseProgress == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("点击右上角按钮选择整合包文件或目录")
                    }
                }
            }

            is UploadStep.Editing -> {
                // === STEP 2: Tabs view ===
                val currentPayload = payload ?: return@MainColumn
                val currentTester = tester

                TitleRow(
                    title = updateModpackName?.let { "为整合包$it 上传新版" } ?: "确认整合包信息",
                    onBack = onBack
                ) {
                    downloadProgress?.let { progress ->
                        val percentText = progress.fraction?.let { fraction ->
                            val pct = (fraction.coerceIn(0f, 1f) * 100f).toInt()
                            " $pct%"
                        } ?: ""
                        Text("${progress.message}$percentText")
                        Space8w()
                    }
                    errorText?.let {
                        Text(it, color = MaterialTheme.colors.error)
                    }
                    Space8w()
                    if (downloadFailed && !isDownloading) {
                        CircleIconButton(
                            icon = "\uF2F9",
                            tooltip = "重试下载失败的Mod",
                            bgColor = MaterialColor.YELLOW_900.color,
                            
                        ) {
                            startModsDownload(modsNeedDownload(mods))
                        }
                        Space8w()
                    }
                    CircleIconButton("\uF058", "确认上传") {
                        //if(!DEBUG){
                            if (currentTester != null) {
                                val signatureNow = currentTester.currentModsSignature(mods)
                                val currentTestStatus = testStatus?.value
                                val currentTestedSig = testedModsSignature?.value
                                if (currentTestStatus != TestStatus.PASSED || currentTestedSig != signatureNow) {
                                    errorText = "请先在 运行测试 页完成测试并通过"
                                    return@CircleIconButton
                                }
                            }
                       // }
                        val name = modpackName.trim()
                        val version = versionName.trim()
                        if (name.isBlank()) {
                            errorText = "整合包名称不能为空"
                            return@CircleIconButton
                        }
                        if (version.isBlank()) {
                            errorText = "版本号不能为空"
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
                        onClick = {
                            if (!isDownloading) {
                                selectedTab = 2
                            } else {
                                errorText = "请等待Mod下载完成后再进行测试"
                            }
                        },
                        enabled = !isDownloading,
                        text = { Text("运行测试${if (isDownloading) "(请先等待Mod下载完成)" else ""}") }
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
                                        errorText = "测试服务器已经在运行中"
                                        return@CircleIconButton
                                    }
                                    testConsoleState.clear()
                                    currentTester.startWithAutoFix(
                                        uiScope = scope,
                                        getMods = { mods },
                                        setMods = { updatedMods -> mods = updatedMods },
                                        onError = { errorText = it },
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
                    onBack = onBack
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
                                errorText = "整合包名称不能为空"
                                return@CircleIconButton
                            }
                            if (version.isBlank()) {
                                errorText = "版本号不能为空"
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
                errorText?.let {
                    Text(it, color = MaterialTheme.colors.error)
                }
            }

            is UploadStep.Done -> {
                val summary = (uploadStep as UploadStep.Done).summary
                TitleRow(
                    title = "上传完成",
                    onBack = onBack
                ) {
                    CircleIconButton("\uF00C", "完成") {
                        onBack()
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
