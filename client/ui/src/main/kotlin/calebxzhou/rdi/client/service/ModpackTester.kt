package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.model.toUiMod
import calebxzau.rdi.client.packproc.LoadedLocalModpack
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.McVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.random.Random

enum class TestStatus {
    NOT_RUN, RUNNING, PASSED, FAILED, STOPPED
}

private const val CLIENT_ONLY_MARK_PREFIX = "C" + "$$" + "_"
private const val CLIENT_TEST_VERSION_PREFIX = "_rdi_client_test_"

class ModpackTester(
    private val loadedModpack: LoadedLocalModpack
) {
    private val _status = MutableStateFlow(TestStatus.NOT_RUN)
    val status: StateFlow<TestStatus> = _status.asStateFlow()

    private val _passSeconds = MutableStateFlow<String?>(null)
    val passSeconds: StateFlow<String?> = _passSeconds.asStateFlow()

    private val _testedModsSignature = MutableStateFlow<String?>(null)
    val testedModsSignature: StateFlow<String?> = _testedModsSignature.asStateFlow()

    private var crashTriggered = false
    private var testProcess: ServerTestProcessHandle? = null
    private var testWorkDir: File? = null
    private var autoFixedModKeys: Set<String> = emptySet()
    private var autoRenamedFiles: Set<String> = emptySet()
    private val passRegex = Regex("""Done \((\d+(?:\.\d+)?)s\)! For help""")
    private val crashTriggerKeywords = listOf(
        "Preparing crash report",
        "Failed to start the minecraft server",
        "Minecraft Crash Report",
        "Missing or unsupported mandatory dependencies"
    )

    fun isRunning(): Boolean = testProcess?.isAlive() == true

    fun currentModsSignature(mods: List<Mod>): String =
        mods.asSequence()
            .sortedBy { modStableKey(it) }
            .joinToString("|") { "${modStableKey(it)}:${it.side.name}" }

    fun onModsChangedAfterManualEdit() {
        _testedModsSignature.value = null
        if (_status.value == TestStatus.PASSED) {
            _status.value = TestStatus.NOT_RUN
            _passSeconds.value = null
        }
    }

    fun markPassed(mods: List<Mod>) {
        _passSeconds.value = null
        _status.value = TestStatus.PASSED
        _testedModsSignature.value = currentModsSignature(mods)
    }

    fun dispose(uiScope: CoroutineScope) {
        stop(uiScope, markStopped = false)
        cleanupTestDir()
    }

    fun stop(
        uiScope: CoroutineScope,
        markStopped: Boolean = true,
        appendLog: (String) -> Unit = {}
    ) {
        if (terminateProcessOnly()) {
            if (markStopped && _status.value != TestStatus.PASSED) {
                _status.value = TestStatus.STOPPED
            }
            uiScope.launch {
                appendLog("[RDI] 已发送停止测试服务器指令")
            }
        }
    }

    fun startWithAutoFix(
        uiScope: CoroutineScope,
        getMods: () -> List<Mod>,
        setMods: (List<Mod>) -> Unit,
        onError: (String?) -> Unit,
        appendLog: (String) -> Unit
    ) {
        val loaderVer = loadedModpack.mcVersion.loaderVersions[loadedModpack.modloader]
        if (loaderVer == null) {
            uiScope.launch { onError("缺少加载器版本配置，无法启动测试服务器") }
            return
        }
        stop(uiScope, markStopped = false)
        crashTriggered = false
        _status.value = TestStatus.RUNNING
        _passSeconds.value = null
        _testedModsSignature.value = null
        uiScope.launch {
            onError(null)
            appendLog("[RDI] 启动测试服务器...")
        }

        uiScope.launch {
            runCatching {
                val workDir = createServerTestWorkDir(
                    loadedModpack = loadedModpack,
                    mods = getMods(),
                    clientOnlyMarkedNames = autoRenamedFiles,
                    existingDir = testWorkDir
                )
                testWorkDir = workDir
                val process = withContext(Dispatchers.IO) {
                    GameService.startServerTestProcess(
                        mcVer = loadedModpack.mcVersion,
                        loaderVer = loaderVer,
                        workDir = workDir
                    ) { line ->
                        uiScope.launch {
                            appendLog(line)
                            if (line.contains("Error: could not open")) {
                                appendLog("${loadedModpack.mcVersion.mcVer}-${loadedModpack.modloader.name}缺少测试服务端文件，请在界面上方下载")
                            }
                            val matched = passRegex.find(line)
                            if (matched != null) {
                                terminateProcessWithDelay(1000L)
                                val latestMods = getMods()
                                _passSeconds.value = matched.groupValues.getOrNull(1)
                                _status.value = TestStatus.PASSED
                                _testedModsSignature.value = currentModsSignature(latestMods)
                                stop(uiScope, markStopped = false)
                            } else if (
                                crashTriggerKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
                            ) {
                                if (_status.value != TestStatus.PASSED) {
                                    crashTriggered = true
                                    _status.value = TestStatus.FAILED
                                    terminateProcessWithDelay(1000L)
                                }
                            }
                        }
                    }
                }
                testProcess = process
                val exitCode = process.waitFor()
                uiScope.launch {
                    if (testProcess == process) {
                        testProcess = null
                    }
                    if (_status.value == TestStatus.PASSED) return@launch

/*
                     * Disabled: server tests no longer auto-fix client-only mods or retry automatically.
                     * val fix = autoFixClientSideFromCrashReport(
                     *     mods = getMods(),
                     *     workDir = workDir,
                     *     sourceDir = loadedModpack.sourceDir,
                     *     alreadyFixed = autoFixedModKeys,
                     *     alreadyRenamed = autoRenamedFiles
                     * )
                     * if (fix != null) {
                     *     if (fix.modKey != null) {
                     *         val newMods = updateModSideByKey(getMods(), fix.modKey, Mod.Side.CLIENT)
                     *         autoFixedModKeys = autoFixedModKeys + fix.modKey
                     *         setMods(newMods)
                     *         appendLog("[RDI] 自动修复：将 ${fix.modName} 标记为客户端Mod，重试测试...")
                     *     } else if (fix.renamedFileName != null) {
                     *         autoRenamedFiles = autoRenamedFiles + fix.renamedFileName
                     *         appendLog("[RDI] 自动修复：将 ${fix.renamedFileName} 重命名为客户端专用(${CLIENT_ONLY_MARK_PREFIX}前缀)，重试测试...")
                     *     }
                     *     startWithAutoFix(uiScope, getMods, setMods, onError, appendLog)
                     *     return@launch
                     * }
*/
                    _status.value = if (crashTriggered) TestStatus.FAILED else TestStatus.STOPPED
                    if (exitCode != 0 && crashTriggered) {
                        onError("测试服务器异常退出: $exitCode")
                    }
                }
            }.onFailure {
                uiScope.launch {
                    _status.value = TestStatus.FAILED
                    onError("启动测试服务器失败: ${it.message}")
                    stop(uiScope, markStopped = false)
                }
            }
        }
    }

    private fun terminateProcessOnly(): Boolean {
        val process = testProcess ?: return false
        runCatching {
            if (process.isAlive()) process.destroy()
            if (process.isAlive()) process.destroyForcibly()
        }
        testProcess = null
        return true
    }

    private suspend fun terminateProcessWithDelay(delayMillis: Long) {
        if (delayMillis > 0L) delay(delayMillis)
        terminateProcessOnly()
    }

    private fun cleanupTestDir() {
        val dir = testWorkDir ?: return
        testWorkDir = null
        runCatching { dir.deleteRecursivelyNoSymlink() }
    }
}

class ClientModpackTester(
    private val loadedModpack: LoadedLocalModpack
) {
    private val _status = MutableStateFlow(TestStatus.NOT_RUN)
    val status: StateFlow<TestStatus> = _status.asStateFlow()

    private val _passSeconds = MutableStateFlow<String?>(null)
    val passSeconds: StateFlow<String?> = _passSeconds.asStateFlow()

    private val _testedModsSignature = MutableStateFlow<String?>(null)
    val testedModsSignature: StateFlow<String?> = _testedModsSignature.asStateFlow()

    private var crashTriggered = false
    private var testProcess: ClientTestProcessHandle? = null
    private var testVersionDir: File? = null
    private var startedAtMillis: Long = 0L

    fun isRunning(): Boolean = testProcess?.isAlive() == true

    fun currentModsSignature(mods: List<Mod>): String =
        mods.asSequence()
            .sortedBy { modStableKey(it) }
            .joinToString("|") { "${modStableKey(it)}:${it.side.name}" }

    fun onModsChangedAfterManualEdit() {
        _testedModsSignature.value = null
        if (_status.value == TestStatus.PASSED) {
            _status.value = TestStatus.NOT_RUN
            _passSeconds.value = null
        }
    }

    fun markPassed(mods: List<Mod>) {
        _passSeconds.value = null
        _status.value = TestStatus.PASSED
        _testedModsSignature.value = currentModsSignature(mods)
    }

    fun dispose(uiScope: CoroutineScope) {
        stop(uiScope, markStopped = false)
        cleanupTestDir()
    }

    fun stop(
        uiScope: CoroutineScope,
        markStopped: Boolean = true,
        appendLog: (String) -> Unit = {}
    ) {
        if (terminateProcessOnly()) {
            if (markStopped && _status.value != TestStatus.PASSED) {
                _status.value = TestStatus.STOPPED
            }
            uiScope.launch {
                appendLog("[RDI] 已发送停止客户端测试指令")
            }
        }
    }

    fun start(
        uiScope: CoroutineScope,
        getMods: () -> List<Mod>,
        onError: (String?) -> Unit,
        appendLog: (String) -> Unit
    ) {
        stop(uiScope, markStopped = false)
        crashTriggered = false
        _status.value = TestStatus.RUNNING
        _passSeconds.value = null
        _testedModsSignature.value = null
        startedAtMillis = System.currentTimeMillis()
        uiScope.launch {
            onError(null)
            appendLog("[RDI] 启动客户端测试...")
        }

        uiScope.launch {
            runCatching {
                GameService.ensureDesktopLaunchLoader(
                    mcVer = loadedModpack.mcVersion,
                    loader = loadedModpack.modloader,
                    onProgress = { line -> uiScope.launch { appendLog("[RDI] $line") } }
                ).getOrThrow()
                val versionDir = createClientTestVersionDir(
                    loadedModpack = loadedModpack,
                    mods = getMods(),
                    existingDir = testVersionDir
                )
                testVersionDir = versionDir
                GameService.ensureDesktopLaunchLibraries(
                    mcVer = loadedModpack.mcVersion,
                    versionId = versionDir.name,
                    versionDir = versionDir,
                    onProgress = { line -> uiScope.launch { appendLog("[RDI] $line") } }
                ).getOrThrow()
                val process = withContext(Dispatchers.IO) {
                    GameService.startClientTestProcess(
                        mcVer = loadedModpack.mcVersion,
                        versionId = versionDir.name,
                        versionDir = versionDir
                    ) { line ->
                        uiScope.launch {
                            appendLog(line)
                            if (line.contains(CLIENT_TEST_SUCCESS_MARKER)) {
                                markClientTestPassed(getMods(), appendLog)
                                terminateProcessWithDelay(500L)
                                return@launch
                            }
                            if (CLIENT_CRASH_TRIGGER_KEYWORDS.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
                                if (_status.value == TestStatus.RUNNING) {
                                    crashTriggered = true
                                    _status.value = TestStatus.FAILED
                                    terminateProcessWithDelay(1000L)
                                }
                            }
                        }
                    }
                }
                testProcess = process

                val exitCode = process.waitFor()
                uiScope.launch {
                    if (testProcess == process) {
                        testProcess = null
                    }
                    if (_status.value == TestStatus.PASSED) return@launch
                    if (_status.value == TestStatus.RUNNING) {
                        _status.value = if (crashTriggered) TestStatus.FAILED else TestStatus.STOPPED
                    }
                    if (exitCode != 0 && crashTriggered) {
                        onError("客户端测试异常退出: $exitCode")
                    }
                }
            }.onFailure {
                uiScope.launch {
                    _status.value = TestStatus.FAILED
                    onError("启动客户端测试失败: ${it.message}")
                    stop(uiScope, markStopped = false)
                }
            }
        }
    }

    private fun terminateProcessOnly(): Boolean {
        val process = testProcess ?: return false
        runCatching {
            if (process.isAlive()) process.destroy()
            if (process.isAlive()) process.destroyForcibly()
        }
        testProcess = null
        return true
    }

    private suspend fun terminateProcessWithDelay(delayMillis: Long) {
        if (delayMillis > 0L) delay(delayMillis)
        terminateProcessOnly()
    }

    private fun markClientTestPassed(
        mods: List<Mod>,
        appendLog: (String) -> Unit
    ) {
        if (_status.value != TestStatus.RUNNING) return
        val elapsed = ((System.currentTimeMillis() - startedAtMillis) / 1000.0)
        _passSeconds.value = "%.1f".format(elapsed)
        _status.value = TestStatus.PASSED
        _testedModsSignature.value = currentModsSignature(mods)
        appendLog("[RDI] 客户端测试通过")
    }

    private fun cleanupTestDir() {
        testVersionDir?.let { dir ->
            runCatching { dir.deleteRecursivelyNoSymlink() }
        }
        testVersionDir = null
    }
}

private fun modStableKey(mod: Mod): String =
    "${mod.platform}:${mod.projectId}:${mod.fileId}:${mod.hash}"

private fun updateModSideByKey(
    mods: List<Mod>,
    modKey: String,
    newSide: Mod.Side
): List<Mod> {
    if (mods.isEmpty()) return mods
    val updated = mods.toMutableList()
    val idx = updated.indexOfFirst { modStableKey(it) == modKey }
    if (idx < 0) return mods
    val origin = updated[idx]
    updated[idx] = origin.toUiMod().withSide(newSide).toMod()
    return updated
}

private suspend fun createServerTestWorkDir(
    loadedModpack: LoadedLocalModpack,
    mods: List<Mod>,
    clientOnlyMarkedNames: Set<String> = emptySet(),
    existingDir: File? = null
) = withContext(Dispatchers.IO) {
    val testDir = existingDir
        ?.takeIf { it.exists() && it.isDirectory }
        ?: createServerTestBaseDir(loadedModpack)
    prepareServerTestRunContent(
        testDir = testDir,
        sourceDir = loadedModpack.sourceDir,
        mods = mods,
        clientOnlyMarkedNames = clientOnlyMarkedNames
    )
    testDir
}

private fun createServerTestBaseDir(loadedModpack: LoadedLocalModpack): File {
    val testDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "servertest-").toFile()
    val libsSource = ClientDirs.librariesDir
    if (libsSource.exists()) {
        val libsTarget = testDir.resolve("libraries")
        hardLinkDirectory(libsSource, libsTarget).getOrElse {
            throw IllegalStateException("创建测试目录libraries硬链接失败: ${it.message}")
        }
    }
    copyTestPackBaseContent(
        sourceDir = loadedModpack.sourceDir,
        targetDir = testDir,
        skipRootChild = ::isClientOnlyMarkedModFile,
        skipModsDirectories = true
    )
    return testDir
}

private fun prepareServerTestRunContent(
    testDir: File,
    sourceDir: File,
    mods: List<Mod>,
    clientOnlyMarkedNames: Set<String>
) {
    val excludedOriginalNames = clientOnlyMarkedNames
        .mapNotNull { marked ->
            if (isClientOnlyMarkedModName(marked)) marked.removePrefix(CLIENT_ONLY_MARK_PREFIX) else null
        }
        .toSet()

    cleanServerTestRuntimeOutput(testDir)
    val modsDir = testDir.resolve("mods")
    if (modsDir.exists()) {
        modsDir.deleteRecursivelyNoSymlink()
    }
    modsDir.mkdirs()
    stageSourceModFiles(modsDir, sourceDir, mods, excludedOriginalNames) { mod ->
        mod.side != Mod.Side.CLIENT && mod.side != Mod.Side.UNKNOWN
    }
    stageDownloadedMods(modsDir, mods) { mod ->
        mod.side != Mod.Side.CLIENT &&
            mod.side != Mod.Side.UNKNOWN &&
            !isClientOnlyMarkedModName(mod.fileName) &&
            mod.fileName !in excludedOriginalNames
    }
    modsDir.listFiles()?.forEach { file ->
        if (!file.isFile) return@forEach
        if (isClientOnlyMarkedModName(file.name) || file.name in excludedOriginalNames) {
            runCatching { Files.deleteIfExists(file.toPath()) }
        }
    }
}

private fun cleanServerTestRuntimeOutput(testDir: File) {
    listOf("logs", "crash-reports", "world").forEach { name ->
        val file = testDir.resolve(name)
        if (file.exists()) file.deleteRecursivelyNoSymlink()
    }
}

private fun copyDirectoryContent(
    source: File,
    target: File,
    skipModsDirectories: Boolean = false
) {
    source.listFiles()?.forEach { child ->
        if (skipModsDirectories && child.isDirectory && child.name.equals("mods", ignoreCase = true)) return@forEach
        if (isClientOnlyMarkedModFile(child)) return@forEach
        val dest = target.resolve(child.name)
        copyFileOrDirectory(child, dest, skipModsDirectories)
    }
}

private fun isClientOnlyMarkedModFile(file: File): Boolean {
    return file.isFile &&
            isClientOnlyMarkedModName(file.name) &&
            file.extension.equals("jar", ignoreCase = true)
}

private val CLIENT_CRASH_TRIGGER_KEYWORDS = listOf(
    "Preparing crash report",
    "MixinTransformerError",
    "Mod Loading has failed",
    "Missing mandatory dependencies"
)

const val CLIENT_TEST_SUCCESS_MARKER = "开始运行客户端测试"

private suspend fun createClientTestVersionDir(
    loadedModpack: LoadedLocalModpack,
    mods: List<Mod>,
    existingDir: File? = null
) = withContext(Dispatchers.IO) {
    val versionDir = existingDir
        ?.takeIf { it.exists() && it.isDirectory }
        ?: createClientTestBaseDir(loadedModpack)
    prepareClientTestRunContent(
        versionDir = versionDir,
        sourceDir = loadedModpack.sourceDir,
        mods = mods,
        mcVersion = loadedModpack.mcVersion
    )
    versionDir
}

private fun createClientTestBaseDir(loadedModpack: LoadedLocalModpack): File {
    val versionId = CLIENT_TEST_VERSION_PREFIX + System.currentTimeMillis() + "_" + Random.nextInt(1000, 9999)
    val versionDir = ClientDirs.packProcDir.resolve(versionId).apply {
        if (exists()) deleteRecursivelyNoSymlink()
        mkdirs()
    }
    copyTestPackBaseContent(
        sourceDir = loadedModpack.sourceDir,
        targetDir = versionDir,
        skipModsDirectories = true
    )
    ModpackService.writeOptions(versionDir, loadedModpack.mcVersion)
    return versionDir
}

private fun prepareClientTestRunContent(
    versionDir: File,
    sourceDir: File,
    mods: List<Mod>,
    mcVersion: McVersion
) {
    cleanClientTestRuntimeOutput(versionDir)
    ModpackService.writeOptions(versionDir, mcVersion)
    val modsDir = versionDir.resolve("mods")
    if (modsDir.exists()) {
        modsDir.deleteRecursivelyNoSymlink()
    }
    modsDir.mkdirs()
    stageSourceModDirectories(modsDir, sourceDir)
    stageSourceModFiles(modsDir, sourceDir, mods, emptySet()) {
        it.side != Mod.Side.SERVER && it.side != Mod.Side.UNKNOWN
    }
    stageDownloadedMods(modsDir, mods) { it.side != Mod.Side.SERVER && it.side != Mod.Side.UNKNOWN }
}

private fun cleanClientTestRuntimeOutput(versionDir: File) {
    listOf("logs", "crash-reports", "saves").forEach { name ->
        val file = versionDir.resolve(name)
        if (file.exists()) file.deleteRecursivelyNoSymlink()
    }
}

private fun isClientOnlyMarkedModName(fileName: String): Boolean =
    fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)

private fun copyTestPackBaseContent(
    sourceDir: File,
    targetDir: File,
    skipRootChild: (File) -> Boolean = { false },
    skipModsDirectories: Boolean = false
) {
    val overridesDir = sourceDir.resolve("overrides")
    if (overridesDir.exists() && overridesDir.isDirectory) {
        copyDirectoryContent(overridesDir, targetDir, skipModsDirectories)
        return
    }
    sourceDir.listFiles()?.forEach { child ->
        if (skipRootChild(child)) return@forEach
        if (child.name.equals("mods", ignoreCase = true)) return@forEach
        if (child.name.equals("manifest.json", ignoreCase = true)) return@forEach
        if (child.name.equals("modrinth.index.json", ignoreCase = true)) return@forEach
        copyFileOrDirectory(child, targetDir.resolve(child.name), skipModsDirectories)
    }
}

private fun copyFileOrDirectory(
    source: File,
    target: File,
    skipModsDirectories: Boolean = false
) {
    if (source.isDirectory) {
        if (!target.exists()) target.mkdirs()
        copyDirectoryContent(source, target, skipModsDirectories)
        return
    }
    target.parentFile?.mkdirs()
    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

private fun stageSourceModFiles(
    modsDir: File,
    sourceDir: File,
    mods: List<Mod>,
    excludedFileNames: Set<String>,
    includeMod: (Mod) -> Boolean
) {
    listOf(
        sourceDir.resolve("mods"),
        sourceDir.resolve("overrides").resolve("mods")
    ).forEach { sourceModsDir ->
        if (!sourceModsDir.exists() || !sourceModsDir.isDirectory) return@forEach
        sourceModsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            ?.filterNot { isClientOnlyMarkedModName(it.name) || it.name in excludedFileNames }
            ?.forEach { source ->
                val matchedMod = findMatchedSourceMod(source, mods)
                if (matchedMod != null && !includeMod(matchedMod)) return@forEach
                val targetName = matchedMod?.fileName ?: source.name
                hardLinkFile(source, modsDir.resolve(targetName)).getOrThrow()
            }
    }
}

private fun stageSourceModDirectories(
    modsDir: File,
    sourceDir: File
) {
    listOf(
        sourceDir.resolve("mods"),
        sourceDir.resolve("overrides").resolve("mods")
    ).forEach { sourceModsDir ->
        if (!sourceModsDir.exists() || !sourceModsDir.isDirectory) return@forEach
        sourceModsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.forEach { source ->
                copyFileOrDirectory(source, modsDir.resolve(source.name))
            }
    }
}

private fun findMatchedSourceMod(source: File, mods: List<Mod>): Mod? {
    mods.firstOrNull { mod ->
        mod.fileNames.any { it.equals(source.name, ignoreCase = true) }
    }?.let { return it }
    val sourceHash = runCatching { source.sha1 }.getOrNull()
    if (!sourceHash.isNullOrBlank()) {
        mods.firstOrNull { it.hash.equals(sourceHash, ignoreCase = true) }?.let { return it }
    }
    val sourceName = source.name.lowercase()
    return mods.firstOrNull { mod ->
        mod.hash.isNotBlank() && sourceName.contains(mod.hash.lowercase()) ||
                mod.slug.isNotBlank() && sourceName.contains(mod.slug.lowercase())
    }
}

private fun stageDownloadedMods(
    modsDir: File,
    mods: List<Mod>,
    includeMod: (Mod) -> Boolean
) {
    mods.asSequence()
        .filter(includeMod)
        .forEach { mod ->
            stageDownloadedModFile(modsDir, mod)
        }
}

private fun stageDownloadedModFile(modsDir: File, mod: Mod) {
    val source = mod.candidateFiles.firstOrNull(File::exists) ?: return
    hardLinkFile(source, modsDir.resolve(mod.fileName)).getOrThrow()
}

/*
private data class CrashAutoFixMatch(
    val modKey: String?,
    val modName: String,
    val renamedFileName: String? = null
)

private fun autoFixClientSideFromCrashReport(
    mods: List<Mod>,
    workDir: File,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    val crashFile = workDir.resolve("crash-reports")
        .listFiles()
        ?.filter { it.isFile && it.name.startsWith("crash-") && it.extension.equals("txt", true) }
        ?.maxByOrNull { it.lastModified() }
        ?: return null
    val lines = runCatching { crashFile.readLines() }.getOrNull() ?: return null

    val sectionFix = findClientNoClassDefFailureFix(mods, lines, workDir, sourceDir, alreadyFixed, alreadyRenamed)
    if (sectionFix != null) return sectionFix

    val modFiles = linkedSetOf<String>()
    val modSlugs = linkedSetOf<String>()
    val invalidDistRegex =
        Regex("""Attempted to load class .* invalid dist\s+DEDICATED_SERVER""", RegexOption.IGNORE_CASE)
    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("Mod File:", ignoreCase = true)) {
            modFiles += trimmed.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
        }
        if (trimmed.startsWith("-- MOD ")) {
            modSlugs += trimmed.removePrefix("-- MOD ").removeSuffix(" --").trim().lowercase()
        }
        if (trimmed.startsWith("-- Mod loading issue for:", ignoreCase = true)) {
            modSlugs += trimmed.substringAfter(":").trim().lowercase()
        }
        if (invalidDistRegex.containsMatchIn(trimmed)) {
            for (i in index + 1 until minOf(lines.size, index + 40)) {
                val nearby = lines[i].trim()
                if (nearby.startsWith("Mod file:", ignoreCase = true)) {
                    modFiles += nearby.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
                    break
                }
            }
        }
    }
    val candidate = mods.firstOrNull { mod ->
        if (mod.side == Mod.Side.CLIENT || mod.side == Mod.Side.UNKNOWN) return@firstOrNull false
        val key = modStableKey(mod)
        if (key in alreadyFixed) return@firstOrNull false
        val byFile = modFiles.any {
            mod.fileNames.any { fileName -> it.equals(fileName, true) } ||
                it.contains(mod.hash, ignoreCase = true) ||
                it.contains(mod.slug, ignoreCase = true)
        }
        val bySlug = modSlugs.contains(mod.slug.lowercase())
        byFile || bySlug
    } ?: return null

    return CrashAutoFixMatch(
        modKey = modStableKey(candidate),
        modName = candidate.displaySlugOrProject
    )
}
*/
/*
private data class CrashModSection(
    val slug: String?,
    val modFileName: String?,
    val hasClientNoClassDef: Boolean
)

private fun isClientSideClassCrashLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.contains("java.lang.NoClassDefFoundError: net/minecraft/client", ignoreCase = true)) {
        return true
    }
    return trimmed.contains("Attempted to load class", ignoreCase = true) &&
        trimmed.contains("invalid side SERVER", ignoreCase = true)
}

private fun normalizeCrashModToken(value: String): String =
    value.lowercase().filter(Char::isLetterOrDigit)

private fun parseCrashReportModSourceMap(lines: List<String>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    lines.forEach { line ->
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return@forEach
        val cols = trimmed.split('|').map(String::trim).filter(String::isNotBlank)
        if (cols.size < 4) return@forEach
        val modId = cols.getOrNull(1).orEmpty()
        val source = cols.getOrNull(3).orEmpty()
        if (modId.isBlank() || source.isBlank()) return@forEach
        if (modId.equals("id", ignoreCase = true) || source.equals("source", ignoreCase = true)) return@forEach
        if (!source.endsWith(".jar", ignoreCase = true)) return@forEach
        result.putIfAbsent(modId.lowercase(), source)
    }
    return result
}*/

/*

private fun findClientNoClassDefFailureFix(
    mods: List<Mod>,
    lines: List<String>,
    workDir: File,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    val modSourceMap = parseCrashReportModSourceMap(lines)
    val sections = mutableListOf<CrashModSection>()
    var sectionSlug: String? = null
    var sectionFile: String? = null
    var sectionClientNoClassDef = false

    fun flushSection() {
        if (sectionSlug != null || sectionFile != null || sectionClientNoClassDef) {
            sections += CrashModSection(sectionSlug, sectionFile, sectionClientNoClassDef)
        }
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("-- MOD ")) {
            flushSection()
            sectionSlug = trimmed.removePrefix("-- MOD ").removeSuffix(" --").trim().lowercase()
            sectionFile = null
            sectionClientNoClassDef = false
            return@forEach
        }
        if (trimmed.startsWith("Mod File:", ignoreCase = true)) {
            sectionFile = trimmed.substringAfter(":").trim().substringAfterLast('/').substringAfterLast('\\')
        }
        if (isClientSideClassCrashLine(trimmed)) {
            sectionClientNoClassDef = true
        }
    }
    flushSection()

    sections.filter { it.hasClientNoClassDef }.forEach { section ->
        val matchedMod = findMatchedCrashMod(
            mods = mods,
            reportedId = section.slug,
            reportedFileName = section.modFileName ?: section.slug?.let(modSourceMap::get),
            alreadyFixed = alreadyFixed
        )
        if (matchedMod != null) {
            return CrashAutoFixMatch(
                modKey = modStableKey(matchedMod),
                modName = matchedMod.displaySlugOrProject
            )
        }

        val rawFileName = section.modFileName
        if (!rawFileName.isNullOrBlank() && rawFileName.endsWith(".jar", ignoreCase = true)) {
            val renamed = renameUnknownClientOnlyJar(
                workDir = workDir,
                sourceDir = sourceDir,
                rawModFileName = rawFileName
            )
            if (renamed != null && renamed !in alreadyRenamed) {
                return CrashAutoFixMatch(
                    modKey = null,
                    modName = rawFileName,
                    renamedFileName = renamed
                )
            }
        }
    }
    findClientNoClassDefFailureFixFromLoaderException(
        mods = mods,
        crashLines = lines,
        modSourceMap = modSourceMap,
        workDir = workDir,
        sourceDir = sourceDir,
        alreadyFixed = alreadyFixed,
        alreadyRenamed = alreadyRenamed
    )?.let { return it }
    findClientNoClassDefFailureFixFromSuspectedMods(
        mods = mods,
        crashLines = lines,
        modSourceMap = modSourceMap,
        workDir = workDir,
        sourceDir = sourceDir,
        alreadyFixed = alreadyFixed,
        alreadyRenamed = alreadyRenamed
    )?.let { return it }
    findClientNoClassDefFailureFixFromDebugLog(
        mods = mods,
        crashLines = lines,
        workDir = workDir,
        modSourceMap = modSourceMap,
        sourceDir = sourceDir,
        alreadyFixed = alreadyFixed,
        alreadyRenamed = alreadyRenamed
    )?.let { return it }
    findClientNoClassDefFailureFixFromMissingClassReference(
        mods = mods,
        crashLines = lines,
        workDir = workDir,
        sourceDir = sourceDir,
        modSourceMap = modSourceMap,
        alreadyFixed = alreadyFixed,
        alreadyRenamed = alreadyRenamed
    )?.let { return it }
    return null
}

private fun findClientNoClassDefFailureFixFromLoaderException(
    mods: List<Mod>,
    crashLines: List<String>,
    modSourceMap: Map<String, String>,
    workDir: File,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    if (crashLines.none(::isClientSideClassCrashLine)) {
        return null
    }
    val modCrashRegex = Regex(
        """LoaderExceptionModCrash:\s+Caught exception from .* \(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )
    val loadClassRegex = Regex(
        """LoaderException:\s+([^\s]+)\s+Failed load class:""",
        RegexOption.IGNORE_CASE
    )
    val reportedId = crashLines
        .asSequence()
        .mapNotNull { line ->
            modCrashRegex.find(line)?.groupValues?.getOrNull(1)
                ?: loadClassRegex.find(line)?.groupValues?.getOrNull(1)
        }
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: return null

    val matchedMod = findMatchedCrashMod(
        mods = mods,
        reportedId = reportedId,
        reportedFileName = modSourceMap[reportedId.lowercase()],
        alreadyFixed = alreadyFixed
    )
    if (matchedMod != null) {
        return CrashAutoFixMatch(
            modKey = modStableKey(matchedMod),
            modName = matchedMod.displaySlugOrProject
        )
    }

    val rawFileName = modSourceMap[reportedId.lowercase()]
    if (!rawFileName.isNullOrBlank() && rawFileName.endsWith(".jar", ignoreCase = true)) {
        val renamed = renameUnknownClientOnlyJar(
            workDir = workDir,
            sourceDir = sourceDir,
            rawModFileName = rawFileName
        )
        if (renamed != null && renamed !in alreadyRenamed) {
            return CrashAutoFixMatch(
                modKey = null,
                modName = rawFileName,
                renamedFileName = renamed
            )
        }
    }
    return null
}

private fun findClientNoClassDefFailureFixFromSuspectedMods(
    mods: List<Mod>,
    crashLines: List<String>,
    modSourceMap: Map<String, String>,
    workDir: File,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    if (crashLines.none(::isClientSideClassCrashLine)) {
        return null
    }
    val ignoredModIds = setOf(
        "minecraft",
        "mcp",
        "cleanroom",
        "mixinbooter",
        "configanytime",
        "kirino_engine",
        "kirino_ecs",
        "kirino_gl",
        "fml",
        "forge"
    )
    val suspectedLine = crashLines.firstOrNull { it.contains("Suspected Mods:", ignoreCase = true) } ?: return null
    val regex = Regex("""\(([^)]+)\)""")
    val reportedId = regex.findAll(suspectedLine)
        .map { it.groupValues[1].trim() }
        .firstOrNull { it.isNotBlank() && it.lowercase() !in ignoredModIds }
        ?: return null

    val matchedMod = findMatchedCrashMod(
        mods = mods,
        reportedId = reportedId,
        reportedFileName = modSourceMap[reportedId.lowercase()],
        alreadyFixed = alreadyFixed
    )
    if (matchedMod != null) {
        return CrashAutoFixMatch(
            modKey = modStableKey(matchedMod),
            modName = matchedMod.displaySlugOrProject
        )
    }

    val rawFileName = modSourceMap[reportedId.lowercase()]
    if (!rawFileName.isNullOrBlank() && rawFileName.endsWith(".jar", ignoreCase = true)) {
        val renamed = renameUnknownClientOnlyJar(
            workDir = workDir,
            sourceDir = sourceDir,
            rawModFileName = rawFileName
        )
        if (renamed != null && renamed !in alreadyRenamed) {
            return CrashAutoFixMatch(
                modKey = null,
                modName = rawFileName,
                renamedFileName = renamed
            )
        }
    }
    return null
}

private fun findClientNoClassDefFailureFixFromDebugLog(
    mods: List<Mod>,
    crashLines: List<String>,
    workDir: File,
    modSourceMap: Map<String, String>,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    if (crashLines.none(::isClientSideClassCrashLine)) {
        return null
    }
    val debugLog = workDir.resolve("logs").resolve("debug.log")
    val lines = runCatching { debugLog.readLines() }.getOrNull() ?: return null
    val crashIndex = lines.indexOfFirst { line ->
        isClientSideClassCrashLine(line) ||
            line.contains("Encountered an unexpected exception", ignoreCase = true)
    }
    if (crashIndex == -1) return null

    val eventRegex = Regex("""Sending event \S+ to mod ([^\s]+)""")
    val slug = (crashIndex - 1 downTo maxOf(0, crashIndex - 200))
        .asSequence()
        .mapNotNull { index -> eventRegex.find(lines[index])?.groupValues?.getOrNull(1) }
        .firstOrNull()
        ?: return null

    val matchedMod = findMatchedCrashMod(
        mods = mods,
        reportedId = slug,
        reportedFileName = modSourceMap[slug.lowercase()],
        alreadyFixed = alreadyFixed
    )
    if (matchedMod != null) {
        return CrashAutoFixMatch(
            modKey = modStableKey(matchedMod),
            modName = matchedMod.displaySlugOrProject
        )
    }

    val rawFileName = modSourceMap[slug.lowercase()]
    if (!rawFileName.isNullOrBlank() && rawFileName.endsWith(".jar", ignoreCase = true)) {
        val renamed = renameUnknownClientOnlyJar(
            workDir = workDir,
            sourceDir = sourceDir,
            rawModFileName = rawFileName
        )
        if (renamed != null && renamed !in alreadyRenamed) {
            return CrashAutoFixMatch(
                modKey = null,
                modName = rawFileName,
                renamedFileName = renamed
            )
        }
    }
    return null
}

private fun findClientNoClassDefFailureFixFromMissingClassReference(
    mods: List<Mod>,
    crashLines: List<String>,
    workDir: File,
    sourceDir: File,
    modSourceMap: Map<String, String>,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
    if (crashLines.none(::isClientSideClassCrashLine)) {
        return null
    }

    val missingClassNames = extractInvalidSideClassNames(crashLines)
    if (missingClassNames.isEmpty()) {
        return null
    }

    val knownSources = modSourceMap.values
        .asSequence()
        .map { it.substringAfterLast('/').substringAfterLast('\\').lowercase() }
        .toSet()
    val mentionedJars = parseCrashReportJarMentions(crashLines)
    val candidateFiles = linkedSetOf<File>().apply {
        listOf(
            workDir.resolve("mods"),
            sourceDir.resolve("mods"),
            sourceDir.resolve("overrides").resolve("mods")
        ).forEach { dir ->
            dir.listFiles()
                ?.filterTo(this) { file ->
                    file.isFile &&
                        file.extension.equals("jar", ignoreCase = true) &&
                        !isClientOnlyMarkedModName(file.name)
                }
        }
    }
    val orderedCandidates = buildList {
        addAll(candidateFiles.filter { it.name.lowercase() in mentionedJars && it.name.lowercase() !in knownSources })
        addAll(candidateFiles.filter { it.name.lowercase() !in mentionedJars && it.name.lowercase() !in knownSources })
        addAll(candidateFiles.filter { it.name.lowercase() in mentionedJars && it.name.lowercase() in knownSources })
        addAll(candidateFiles.filter { it.name.lowercase() !in mentionedJars && it.name.lowercase() in knownSources })
    }

    val matchedFile = orderedCandidates.firstOrNull { file ->
        missingClassNames.any { missingClassName ->
            jarContainsClassReference(file, missingClassName)
        }
    } ?: return null

    val matchedMod = findMatchedCrashMod(
        mods = mods,
        reportedId = null,
        reportedFileName = matchedFile.name,
        alreadyFixed = alreadyFixed
    )
    if (matchedMod != null) {
        return CrashAutoFixMatch(
            modKey = modStableKey(matchedMod),
            modName = matchedMod.displaySlugOrProject
        )
    }

    val renamed = renameUnknownClientOnlyJar(
        workDir = workDir,
        sourceDir = sourceDir,
        rawModFileName = matchedFile.name
    )
    if (renamed != null && renamed !in alreadyRenamed) {
        return CrashAutoFixMatch(
            modKey = null,
            modName = matchedFile.name,
            renamedFileName = renamed
        )
    }
    return null
}
*/
/*

private fun extractInvalidSideClassNames(lines: List<String>): List<String> {
    val result = linkedSetOf<String>()
    val patterns = listOf(
        Regex("""NoClassDefFoundError:\s+([A-Za-z0-9_/$.\-]+)""", RegexOption.IGNORE_CASE),
        Regex("""ClassNotFoundException:\s+([A-Za-z0-9_/$.\-]+)""", RegexOption.IGNORE_CASE),
        Regex("""Attempted to load class\s+([A-Za-z0-9_/$.\-]+)\s+for invalid side SERVER""", RegexOption.IGNORE_CASE)
    )
    lines.forEach { line ->
        patterns.forEach { pattern ->
            pattern.find(line)?.groupValues?.getOrNull(1)
                ?.let(::normalizeMissingClassReference)
                ?.let(result::add)
        }
    }
    return result.toList()
}

private fun normalizeMissingClassReference(raw: String): String? {
    val trimmed = raw.trim().removePrefix("L").removeSuffix(";")
    if (trimmed.isBlank()) return null
    val withoutObfPrefix = if ('/' in trimmed && '.' in trimmed) {
        trimmed.substringAfter('/')
    } else {
        trimmed
    }
    return withoutObfPrefix
        .replace('.', '/')
        .trim()
        .takeIf { it.contains('/') }
}

private fun parseCrashReportJarMentions(lines: List<String>): Set<String> {
    val regex = Regex("""\(([^)]+\.jar)\)""", RegexOption.IGNORE_CASE)
    return lines.asSequence()
        .mapNotNull { line -> regex.find(line)?.groupValues?.getOrNull(1) }
        .map { it.substringAfterLast('/').substringAfterLast('\\').lowercase() }
        .toSet()
}

private fun jarContainsClassReference(file: File, internalClassName: String): Boolean {
    val internalNameBytes = internalClassName.toByteArray()
    val dottedNameBytes = internalClassName.replace('/', '.').toByteArray()
    return runCatching {
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class", ignoreCase = true)) continue
                val matched = zip.getInputStream(entry).use { input ->
                    val bytes = input.readBytes()
                    bytes.containsSubsequence(internalNameBytes) || bytes.containsSubsequence(dottedNameBytes)
                }
                if (matched) return@use true
            }
            false
        }
    }.getOrDefault(false)
}
*//*


private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
    if (needle.isEmpty() || size < needle.size) return false
    for (start in 0..size - needle.size) {
        var matched = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matched = false
                break
            }
        }
        if (matched) return true
    }
    return false
}

private fun renameUnknownClientOnlyJar(
    workDir: File,
    sourceDir: File,
    rawModFileName: String
): String? {
    val fileName = rawModFileName.substringAfterLast('/').substringAfterLast('\\')
    if (fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)) return fileName
    if (!fileName.endsWith(".jar", ignoreCase = true)) return null
    val newName = CLIENT_ONLY_MARK_PREFIX + fileName

    val candidates = linkedSetOf<File>().apply {
        val raw = File(rawModFileName)
        if (raw.exists()) add(raw)

        if (rawModFileName.startsWith("/") && rawModFileName.length > 3 && rawModFileName[2] == ':') {
            val winPath = File(rawModFileName.removePrefix("/"))
            if (winPath.exists()) add(winPath)
        }

        add(workDir.resolve("mods").resolve(fileName))
        add(sourceDir.resolve("mods").resolve(fileName))
        add(sourceDir.resolve("overrides").resolve("mods").resolve(fileName))
    }

    var renamedAny = false
    candidates.forEach { file ->
        if (!file.exists()) return@forEach
        val target = file.resolveSibling(newName)
        runCatching {
            if (target.exists()) {
                file.delete()
            } else {
                file.renameTo(target)
            }
            renamedAny = true
        }
    }
    return if (renamedAny) newName else null
}
*/
