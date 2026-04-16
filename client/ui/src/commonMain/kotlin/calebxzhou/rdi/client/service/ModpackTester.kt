package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.client.model.firstLoaderDir
import calebxzhou.rdi.client.model.loaderManifest
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.displaySlugOrProject
import calebxzhou.rdi.common.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
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

    fun dispose(uiScope: CoroutineScope) {
        stop(uiScope, markStopped = false)
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
                    clientOnlyMarkedNames = autoRenamedFiles
                )
                testWorkDir = workDir
                val process = GameService.startServerTestProcess(
                    mcVer = loadedModpack.mcVersion,
                    loaderVer = loaderVer,
                    workDir = workDir
                ) { line ->
                    uiScope.launch {
                        appendLog(line)
                        if (line.contains("Error: could not open")) {
                            appendLog("${loadedModpack.mcVersion.mcVer}-${loadedModpack.modloader.name}文件不完整，请前往mc资源界面重新下载")
                        }
                        val matched = passRegex.find(line)
                        if (matched != null) {
                            terminateProcessWithDelay(1000L)
                            val latestMods = getMods()
                            val normalizedMods = latestMods.map { mod ->
                                if (mod.side == Mod.Side.UNKNOWN) {
                                    mod.toUiMod().withSide(Mod.Side.BOTH).toMod()
                                } else mod
                            }
                            val changedUnknown = latestMods.count { it.side == Mod.Side.UNKNOWN }
                            if (changedUnknown > 0) {
                                setMods(normalizedMods)
                                appendLog("[RDI] 测试通过，已将 $changedUnknown 个未识别运行侧Mod标记为BOTH")
                            }
                            _passSeconds.value = matched.groupValues.getOrNull(1)
                            _status.value = TestStatus.PASSED
                            _testedModsSignature.value = currentModsSignature(
                                if (changedUnknown > 0) normalizedMods else latestMods
                            )
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
                testProcess = process
                val exitCode = process.waitFor()
                uiScope.launch {
                    if (testProcess == process) {
                        testProcess = null
                    }
                    if (_status.value == TestStatus.PASSED) return@launch

                    val fix = autoFixClientSideFromCrashReport(
                        mods = getMods(),
                        workDir = workDir,
                        sourceDir = loadedModpack.sourceDir,
                        alreadyFixed = autoFixedModKeys,
                        alreadyRenamed = autoRenamedFiles
                    )
                    if (fix != null) {
                        if (fix.modKey != null) {
                            val newMods = updateModSideByKey(getMods(), fix.modKey, Mod.Side.CLIENT)
                            autoFixedModKeys = autoFixedModKeys + fix.modKey
                            setMods(newMods)
                            appendLog("[RDI] 自动修复：将 ${fix.modName} 标记为客户端Mod，重试测试...")
                        } else if (fix.renamedFileName != null) {
                            autoRenamedFiles = autoRenamedFiles + fix.renamedFileName
                            appendLog("[RDI] 自动修复：将 ${fix.renamedFileName} 重命名为客户端专用(${CLIENT_ONLY_MARK_PREFIX}前缀)，重试测试...")
                        }
                        startWithAutoFix(uiScope, getMods, setMods, onError, appendLog)
                        return@launch
                    }
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
        if (!loadedModpack.mcVersion.firstLoaderDir.exists()) {
            uiScope.launch { onError("缺少${loadedModpack.mcVersion.mcVer}客户端资源，请先在MC资源页安装") }
            return
        }
        stop(uiScope, markStopped = false)
        cleanupTestDir()
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
                val versionDir = createClientTestVersionDir(
                    loadedModpack = loadedModpack,
                    mods = getMods()
                )
                testVersionDir = versionDir
                val process = GameService.startClientTestProcess(
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

internal expect class ServerTestProcessHandle {
    fun isAlive(): Boolean
    fun destroy()
    fun destroyForcibly()
    suspend fun waitFor(): Int
}

internal expect class ClientTestProcessHandle {
    fun isAlive(): Boolean
    fun destroy()
    fun destroyForcibly()
    suspend fun waitFor(): Int
}

internal expect fun GameService.startServerTestProcess(
    mcVer: McVersion,
    loaderVer: ModLoader.Version,
    workDir: File,
    onLine: (String) -> Unit
): ServerTestProcessHandle

internal expect fun GameService.startClientTestProcess(
    mcVer: McVersion,
    versionId: String,
    versionDir: File,
    onLine: (String) -> Unit
): ClientTestProcessHandle

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
    clientOnlyMarkedNames: Set<String> = emptySet()
) = withContext(Dispatchers.IO) {
    val testDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "servertest-").toFile()
    val excludedOriginalNames = clientOnlyMarkedNames
        .mapNotNull { marked ->
            if (isClientOnlyMarkedModName(marked)) marked.removePrefix(CLIENT_ONLY_MARK_PREFIX) else null
        }
        .toSet()

    val libsSource = ClientDirs.librariesDir
    if (libsSource.exists()) {
        val libsTarget = testDir.resolve("libraries")
        runCatching {
            Files.deleteIfExists(libsTarget.toPath())
            Files.createSymbolicLink(libsTarget.toPath(), libsSource.toPath())
        }.getOrElse {
            throw IllegalStateException("创建测试目录libraries软链接失败: ${it.message}")
        }
    }
    val sourceDir = loadedModpack.sourceDir
    copyTestPackBaseContent(
        sourceDir = sourceDir,
        targetDir = testDir,
        skipRootChild = ::isClientOnlyMarkedModFile
    )
    val modsDir = testDir.resolve("mods").apply { mkdirs() }
    stageDownloadedMods(modsDir, mods) { mod ->
        mod.side != Mod.Side.CLIENT &&
            !isClientOnlyMarkedModName(mod.fileName) &&
            mod.fileName !in excludedOriginalNames
    }
    modsDir.listFiles()?.forEach { file ->
        if (!file.isFile) return@forEach
        if (isClientOnlyMarkedModName(file.name) || file.name in excludedOriginalNames) {
            runCatching { Files.deleteIfExists(file.toPath()) }
        }
    }
    testDir
}

private fun copyDirectoryContent(source: File, target: File) {
    source.listFiles()?.forEach { child ->
        if (isClientOnlyMarkedModFile(child)) return@forEach
        val dest = target.resolve(child.name)
        copyFileOrDirectory(child, dest)
    }
}

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
        if (mod.side == Mod.Side.CLIENT) return@firstOrNull false
        val key = modStableKey(mod)
        if (key in alreadyFixed) return@firstOrNull false
        val byFile = modFiles.any {
            it.equals(mod.fileName, true) ||
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

private data class CrashModSection(
    val slug: String?,
    val modFileName: String?,
    val hasClientNoClassDef: Boolean
)

private fun findClientNoClassDefFailureFix(
    mods: List<Mod>,
    lines: List<String>,
    workDir: File,
    sourceDir: File,
    alreadyFixed: Set<String>,
    alreadyRenamed: Set<String>
): CrashAutoFixMatch? {
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
        if (trimmed.contains("java.lang.NoClassDefFoundError: net/minecraft/client", ignoreCase = true)) {
            sectionClientNoClassDef = true
        }
    }
    flushSection()

    sections.filter { it.hasClientNoClassDef }.forEach { section ->
        val matchedMod = mods.firstOrNull { mod ->
            if (mod.side == Mod.Side.CLIENT) return@firstOrNull false
            val key = modStableKey(mod)
            if (key in alreadyFixed) return@firstOrNull false
            val bySlug = section.slug?.let { slug -> slug == mod.slug.lowercase() } == true
            val byFile = section.modFileName?.let { fileName ->
                fileName.equals(mod.fileName, true) ||
                    fileName.contains(mod.hash, ignoreCase = true) ||
                    fileName.contains(mod.slug, ignoreCase = true)
            } == true
            bySlug || byFile
        }
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
    return null
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

private fun isClientOnlyMarkedModFile(file: File): Boolean {
    return file.isFile &&
        isClientOnlyMarkedModName(file.name) &&
        file.extension.equals("jar", ignoreCase = true)
}

private val CLIENT_CRASH_TRIGGER_KEYWORDS = listOf(
    "Preparing crash report",
    "MixinTransformerError",
    "Failed to create mod instance",
    "Mod Loading has failed",
    "NoClassDefFoundError",
    "ClassNotFoundException",
    "Missing mandatory dependencies"
)

const val CLIENT_TEST_SUCCESS_MARKER = "开始运行客户端测试"

private suspend fun createClientTestVersionDir(
    loadedModpack: LoadedLocalModpack,
    mods: List<Mod>
) = withContext(Dispatchers.IO) {
    val versionId = CLIENT_TEST_VERSION_PREFIX + System.currentTimeMillis() + "_" + Random.nextInt(1000, 9999)
    val versionDir = ClientDirs.packProcDir.resolve(versionId).apply {
        if (exists()) deleteRecursivelyNoSymlink()
        mkdirs()
    }
    val sourceDir = loadedModpack.sourceDir
    copyTestPackBaseContent(sourceDir, versionDir)
    val modsDir = versionDir.resolve("mods").apply { mkdirs() }
    stageDownloadedMods(modsDir, mods) { it.side != Mod.Side.SERVER }
    ModpackService.writeOptions(versionDir)
    //ModpackService.installRdiCore(loadedModpack.mcVersion, loadedModpack.modloader, modsDir)
    try {
        versionDir.resolve("$versionId.json")
            .writeText(loadedModpack.mcVersion.loaderManifest.copy(id = versionId).json)
    } catch (e: FileNotFoundException) {
        throw IllegalStateException("没有找到${loadedModpack.mcVersion.mcVer}版本的${loadedModpack.modloader.name}，请先安装")
    }
    versionDir
}

private fun isClientOnlyMarkedModName(fileName: String): Boolean =
    fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)

private fun copyTestPackBaseContent(
    sourceDir: File,
    targetDir: File,
    skipRootChild: (File) -> Boolean = { false }
) {
    val overridesDir = sourceDir.resolve("overrides")
    if (overridesDir.exists() && overridesDir.isDirectory) {
        copyDirectoryContent(overridesDir, targetDir)
        return
    }
    sourceDir.listFiles()?.forEach { child ->
        if (skipRootChild(child)) return@forEach
        if (child.name.equals("mods", ignoreCase = true)) return@forEach
        if (child.name.equals("manifest.json", ignoreCase = true)) return@forEach
        if (child.name.equals("modrinth.index.json", ignoreCase = true)) return@forEach
        copyFileOrDirectory(child, targetDir.resolve(child.name))
    }
}

private fun copyFileOrDirectory(source: File, target: File) {
    if (source.isDirectory) {
        if (!target.exists()) target.mkdirs()
        copyDirectoryContent(source, target)
        return
    }
    target.parentFile?.mkdirs()
    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

private fun stageDownloadedMods(
    modsDir: File,
    mods: List<Mod>,
    includeMod: (Mod) -> Boolean
) {
    mods.asSequence()
        .filter(includeMod)
        .forEach { mod ->
            stageDownloadedModFile(modsDir, mod.fileName)
        }
}

private fun stageDownloadedModFile(modsDir: File, fileName: String) {
    val source = DL_MOD_DIR.resolve(fileName)
    if (!source.exists()) return
    linkOrCopyFile(source, modsDir.resolve(source.name))
}

private fun linkOrCopyFile(source: File, target: File) {
    runCatching {
        Files.deleteIfExists(target.toPath())
        Files.createSymbolicLink(target.toPath(), source.toPath())
    }.onFailure {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
