package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.javaExePath
import calebxzau.rdi.client.CONF
import calebxzhou.rdi.client.Const
import calebxzau.rdi.client.ScreenSize
import calebxzhou.rdi.client.model.*
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.GameService.resolveGameArgumentList
import calebxzhou.rdi.client.service.GameService.resolveJvmArgumentList
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.util.toUUID
import com.sun.management.OperatingSystemMXBean
import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.concurrent.thread

/** Game launching and installer bootstrapper code. */
private val lgr by Loggers
private val utf8LoggingJvmArgs = listOf(
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8"
)

// ---- Installer bootstrapper ----

internal fun GameService.runInstallerBootstrapper(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
) {
    val installBooter = holder.installBooter ?: error("安装引导未准备")
    val installer = holder.installer ?: error("安装器未准备")
    val hostOs = LibraryOsArch.detectHostOs()
    val classpathSeparator = if (hostOs.isWindows) ";" else ":"
    val classpath = listOf(installBooter.absolutePath, installer.absolutePath).joinToString(classpathSeparator)
    val command = listOf(
        javaExePath,
        "-cp",
        classpath,
        "com.bangbang93.ForgeInstaller",
        ClientDirs.mcDir.absolutePath,
    )
    val process = ProcessBuilder(command)
        .directory(ClientDirs.mcDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
        lines.forEach { line ->
            if (line.isNotBlank()) {
                lgr.info { line }
                ctx.emitProgress(TaskProgress(line, null))
            }
        }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw IllegalStateException("mod载入器安装失败: $exitCode")
    } else {
        ctx.emitProgress(TaskProgress("安装成功", 1f))
    }
}

internal fun GameService.runServerInstallerBootstrapper(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
) {
    val installer = holder.installer ?: error("安装器未准备")
    val command = listOf(
        javaExePath,
        "-jar",
        installer.absolutePath,
        "--installServer",
        ClientDirs.mcDir.absolutePath,
    )
    val process = ProcessBuilder(command)
        .directory(ClientDirs.mcDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
        lines.forEach { line ->
            if (line.isNotBlank()) {
                lgr.info { line }
                ctx.emitProgress(TaskProgress(line, null))
            }
        }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw IllegalStateException("mod载入器安装失败 $exitCode")
    } else {
        ctx.emitProgress(TaskProgress("安装成功", 1f))
    }
}

// ---- Game launching ----

fun GameService.startDesktop(mcVer: McVersion, versionId: String, vararg jvmArgs: String, onLine: (String) -> Unit): Process =
    startDesktopInDir(
        mcVer,
        versionId,
        versionListDir.resolve(versionId),
        *jvmArgs,
        onLine = onLine
    )

internal fun GameService.startDesktopInDir(
    mcVer: McVersion,
    versionId: String,
    versionDir: File,
    vararg jvmArgs: String,
    onLine: (String) -> Unit
): Process {
    val gtnhLaunch = mcVer == McVersion.V071
    val gtnhLibRoot = if (gtnhLaunch) {
        requireGtnhExtensionRoot(versionDir).also { ensureGtnhRuntimeMods(versionDir, it) }
    } else null
    val loaderManifest = if (gtnhLibRoot != null) {
        buildGtnhLoaderManifest(gtnhLibRoot, versionId)
    } else {
        mcVer.loaderManifest.copy(id = versionId)
    }
    val manifest = mcVer.manifest
    val launchBaseLibraries = launchBaseLibraries(manifest, gtnhLibRoot)
    val launchLibraryIssues = validateLaunchLibraries(launchBaseLibraries, loaderManifest.libraries)
    if (launchLibraryIssues.isNotEmpty()) {
        launchLibraryIssues.forEach { issue ->
            lgr.warn { "启动被阻止，运行库不完整: ${issue.summary} path=${issue.file.absolutePath}" }
        }
        throw IllegalStateException("运行库缺失或损坏，请先修复: ${launchLibraryIssues.first().summary}")
    }
    val nativesDir = if (gtnhLaunch) {
        mcVer.nativesDir
    } else {
        versionDir.resolve("natives").takeIf(File::exists) ?: mcVer.nativesDir
    }
    val resolvedGameArgs = resolveLaunchGameArguments(manifest, loaderManifest)
    val gameArgs = resolvedGameArgs.map {
        it.replace($$"${auth_player_name}", loggedAccount.name)
            .replace($$"${version_name}", versionId)
            .replace($$"${game_directory}", versionDir.absolutePath)
            .replace($$"${assets_root}", ClientDirs.assetsDir.absolutePath)
            .replace($$"${assets_index_name}", manifest.assets ?: manifest.assetIndex?.id ?: manifest.id)
            .replace($$"${auth_uuid}", loggedAccount._id.toUUID().toString().replace("-", ""))
            .replace($$"${auth_access_token}", loggedAccount.jwt ?: "")
            .replace($$"${user_type}", "msa")
            .replace($$"${version_type}", "RDI")
            .replace($$"${user_properties}", "{}")
    }.toMutableList()
    val (physicalWidth, physicalHeight) = resolvePhysicalScreenSize()
    gameArgs += listOf("--width", "$physicalWidth", "--height", "$physicalHeight")
    val resolvedJvmArgs = manifest.resolveJvmArgumentList() + loaderManifest.resolveJvmArgumentList()
    val launchClasspath = buildLaunchClasspath(
        manifest = manifest,
        loaderManifest = loaderManifest,
        versionDir = versionDir,
        versionId = versionId,
        baseLibraries = launchBaseLibraries,
        gtnhExtensionRoot = gtnhLibRoot
    )
    val classpath = launchClasspath.joinToString(File.pathSeparator)
    val legacyLaunch = resolvedJvmArgs.isEmpty() &&
        (!manifest.minecraftArguments.isNullOrBlank() || !loaderManifest.minecraftArguments.isNullOrBlank())
    val hasClasspathDeclaration = !gtnhLaunch && resolvedJvmArgs.any {
        it == "-cp" || it == "-classpath" || it.contains("\${classpath}")
    }
    val jvmVersionName = if (loaderManifest.isBootstrapModuleLaunch()) {
        loaderManifest.jar
            ?.takeIf(String::isNotBlank)
            ?: loaderManifest.inheritsFrom?.takeIf(String::isNotBlank)
            ?: manifest.id
    } else {
        versionId
    }
    val processedJvmArgs = buildList {
        var skipNextClasspathValue = false
        resolvedJvmArgs.forEach { rawArg ->
            val arg = rawArg.replaceLaunchTokens(nativesDir, versionDir, jvmVersionName, classpath)
            if (gtnhLaunch) {
                if (skipNextClasspathValue) {
                    skipNextClasspathValue = false
                    return@forEach
                }
                if (arg == "-cp" || arg == "-classpath") {
                    skipNextClasspathValue = true
                    return@forEach
                }
            }
            add(arg)
        }
    }.toMutableList()
    if (legacyLaunch) {
        processedJvmArgs += listOf(
            "-Djava.library.path=${nativesDir.absolutePath}",
            "-Dminecraft.launcher.brand=rdi",
            "-Dminecraft.launcher.version=${Const.VERSION_NUMBER}"
        )
    }
    if (launchClasspath.isNotEmpty() && !hasClasspathDeclaration) {
        processedJvmArgs += listOf("-cp", classpath)
    }
    val useMemStr = runCatching {
        if (CONF.maxMemory > 0) {
            "-Xmx${CONF.maxMemory}M"
        } else {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val freeBytes = (osBean as? OperatingSystemMXBean)
                ?.freeMemorySize
                ?: return@runCatching "-Xmx8G"
            val freeMb = freeBytes / (1024L * 1024)
            lgr.info { "剩余内存${freeBytes.humanFileSize}" }
            val mem = if (freeMb > 8192) freeMb else 8192
            "-Xmx${mem}M"
        }
    }.getOrDefault("-Xmx8G")
    processedJvmArgs.apply {
        this += useMemStr
        this += utf8LoggingJvmArgs
        if (gtnhLibRoot != null) {
            this += gtnhJava25JvmArgs(gtnhLibRoot, nativesDir, versionDir, versionId, classpath)
        }
        this += jvmArgs
        if(DEBUG){
            this +=  "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
        }
    }

    lgr.info { "JVM Args: ${processedJvmArgs.joinToString(" ")}" }
    lgr.info { "Game Args: ${gameArgs.joinToString(" ")}" }
    val jrePath = if (gtnhLaunch) resolveJava25Path() else resolveDesktopJavaPath(mcVer)
    val mainClass = loaderManifest.mainClass ?: error("缺少启动主类")
    val command = listOf(
        jrePath,
        *processedJvmArgs.toTypedArray(),
        mainClass,
        *gameArgs.toTypedArray(),
    )
    lgr.info { "Launch Command: ${command.joinToString(" ")}" }
    val process = ProcessBuilder(command)
        .directory(versionDir)
        .redirectErrorStream(true)
        .start()
    thread(name = "mc-log-reader", isDaemon = true) {
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    onLine(line)
                }
            }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            onLine("MC已结束，退出代码: $exitCode")
        } else {
            onLine("已退出")
        }
    }
    return process
}

internal suspend fun GameService.ensureDesktopLaunchLibraries(
    mcVer: McVersion,
    versionId: String,
    versionDir: File = versionListDir.resolve(versionId),
    onProgress: (String) -> Unit
): Result<Unit> = runCatching {
    val gtnhLaunch = mcVer == McVersion.V071
    val gtnhExtensionRoot = if (gtnhLaunch) {
        requireGtnhExtensionRoot(versionDir)
    } else {
        null
    }
    val loaderManifest = if (gtnhExtensionRoot != null) {
        buildGtnhLoaderManifest(gtnhExtensionRoot, versionId)
    } else {
        mcVer.loaderManifest.copy(id = versionId)
    }
    ensureLaunchLibraries(
        baseLibraries = launchBaseLibraries(mcVer.manifest, gtnhExtensionRoot),
        overrideLibraries = loaderManifest.libraries,
        onProgress = onProgress
    ).getOrThrow()
}

private fun GameService.buildLaunchClasspath(
    manifest: MojangVersionManifest,
    loaderManifest: MojangVersionManifest,
    versionDir: File,
    versionId: String,
    gtnhExtensionRoot: File? = null,
    baseLibraries: List<MojangLibrary> = launchBaseLibraries(manifest, gtnhExtensionRoot)
): List<String> {
    val entries = LinkedHashSet<String>()
    if (gtnhExtensionRoot != null) {
        entries += buildGtnhJava25Classpath(gtnhExtensionRoot)
    }
    entries += buildClasspath(
        baseLibraries = baseLibraries,
        overrideLibraries = loaderManifest.libraries
    )
    resolveLaunchVersionJarCandidates(
        manifest = manifest,
        loaderManifest = loaderManifest,
        versionDir = versionDir,
        versionId = versionId
    ).filter(File::exists)
        .forEach { entries += it.absolutePath }
    return entries.toList()
}

private fun launchBaseLibraries(
    manifest: MojangVersionManifest,
    gtnhExtensionRoot: File?
): List<MojangLibrary> {
    return if (gtnhExtensionRoot != null) {
        manifest.libraries.filterNot(MojangLibrary::isLegacyLwjgl2Library)
    } else {
        manifest.libraries
    }
}

private fun GameService.resolveLaunchVersionJarCandidates(
    manifest: MojangVersionManifest,
    loaderManifest: MojangVersionManifest,
    versionDir: File,
    versionId: String
): List<File> {
    val isBootstrapModuleLaunch = loaderManifest.isBootstrapModuleLaunch()
    val versionNames = linkedSetOf<String>()
    versionNames += versionId
    loaderManifest.id.takeIf(String::isNotBlank)?.let(versionNames::add)
    loaderManifest.jar?.takeIf(String::isNotBlank)?.let(versionNames::add)
    if (!isBootstrapModuleLaunch) {
        loaderManifest.inheritsFrom?.takeIf(String::isNotBlank)?.let(versionNames::add)
        manifest.jar?.takeIf(String::isNotBlank)?.let(versionNames::add)
        manifest.id.takeIf(String::isNotBlank)?.let(versionNames::add)
    }
    return versionNames.flatMap { name ->
        listOf(
            versionDir.resolve("$name.jar"),
            versionListDir.resolve(name).resolve("$name.jar")
        )
    }.distinctBy{it.absolutePath}
}

private fun MojangVersionManifest.isBootstrapModuleLaunch(): Boolean {
    if (mainClass != "cpw.mods.bootstraplauncher.BootstrapLauncher") {
        return false
    }
    val jvmArgs = resolveJvmArgumentList()
    return "-p" in jvmArgs && "ALL-MODULE-PATH" in jvmArgs
}

private fun resolveLaunchGameArguments(
    manifest: MojangVersionManifest,
    loaderManifest: MojangVersionManifest
): List<String> {
    if (!loaderManifest.minecraftArguments.isNullOrBlank()) {
        return loaderManifest.resolveGameArgumentList()
    }
    return manifest.resolveGameArgumentList() + loaderManifest.resolveGameArgumentList()
}

internal fun String.replaceLaunchTokens(
    nativesDir: File,
    versionDir: File,
    versionId: String,
    classpath: String,
): String = replace("\${natives_directory}", nativesDir.absolutePath)
    .replace("\${game_directory}", versionDir.absolutePath)
    .replace("\${library_directory}", ClientDirs.librariesDir.absolutePath)
    .replace("\${libraries_directory}", ClientDirs.librariesDir.absolutePath)
    .replace("\${launcher_name}", "rdi")
    .replace("\${launcher_version}", Const.VERSION_NUMBER)
    .replace("\${version_name}", versionId)
    .replace("\${classpath}", classpath)
    .replace("\${classpath_separator}", File.pathSeparator)

fun GameService.startServerDesktop(mcVer: McVersion, loaderVer: ModLoader.Version, workDir: File, onLine: (String) -> Unit): Process {
    if (mcVer == McVersion.V071) {
        throw RequestError("不支持GTNH本地测试服务器")
    }
    val hostOs = LibraryOsArch.detectHostOs()
    val jrePath = resolveDesktopJavaPath(mcVer)
    val command = mutableListOf(
        jrePath,
        "-Xmx6G",
        "-Xms6G",
        *utf8LoggingJvmArgs.toTypedArray(),
    ).apply {
        when (mcVer) {
            //McVersion.V182,
            McVersion.V192,
            McVersion.V201,
            McVersion.V211 -> {
                this += loaderVer.serverArgsPath(hostOs.isUnixLike)
                this += "%*"
            }

            /*McVersion.V165 -> {
                if (loaderVer.loader == ModLoader.forge) {
                    McVersion.V165.plusJvmArgs.forEach { this += it }
                    val jarFileName = "forge-${loaderVer.id}.jar"
                    this += "-jar"
                    this += jarFileName
                    linkServerRuntimeFile(workDir.resolve(jarFileName), ClientDirs.mcDir.resolve(jarFileName))
                }
            }*/

            McVersion.V122 -> {
                if (loaderVer.loader == ModLoader.forge || loaderVer.loader == ModLoader.cleanroom) {
                    McVersion.V122.plusJvmArgs.forEach { this += it }
                    val jarFileName = "cleanroom-${loaderVer.id}.jar"
                    this += "-jar"
                    this += jarFileName
                    linkServerRuntimeFile(workDir.resolve(jarFileName), ClientDirs.mcDir.resolve(jarFileName))
                    //mc server jar
                    val mcServerJar = "minecraft_server.1.12.2.jar"
                    linkServerRuntimeFile(workDir.resolve(mcServerJar), ClientDirs.mcDir.resolve(mcServerJar))
                }
            }

            else -> { throw RequestError("不支持的MC版本启动测试服务器")
            }
        }
        this += "--nogui"
    }
    workDir.resolve("eula.txt").writeText("eula=true")
    val process = ProcessBuilder(command)
        .directory(workDir)
        .redirectErrorStream(true)
        .start()
    serverStarted = true
    thread(name = "mc-server-log-reader", isDaemon = true) {
        try {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {

                        onLine(line)
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onLine("启动结束，退出代码: $exitCode")
            } else {
                onLine("已退出")
            }
        } finally {
            serverStarted = false
        }
    }
    return process
}

internal fun linkServerRuntimeFile(link: File, source: File) {
    if (!source.isFile) {
        throw RequestError("缺少服务端文件: ${source.absolutePath}")
    }
    if (link.exists()) return
    link.parentFile?.mkdirs()
    runCatching {
        Files.createSymbolicLink(link.toPath(), source.toPath())
    }.getOrElse {
        Files.copy(source.toPath(), link.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun resolvePhysicalScreenSize(): Pair<Int, Int> {
    val logicalWidth = ScreenSize.first.value.toInt()
    val logicalHeight = ScreenSize.second.value.toInt()
    return runCatching {
        val transform = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
        val scaleX = transform.scaleX.takeIf { it > 0.0 } ?: 1.0
        val scaleY = transform.scaleY.takeIf { it > 0.0 } ?: 1.0
        val width = (logicalWidth * scaleX).toInt().coerceAtLeast(logicalWidth)
        val height = (logicalHeight * scaleY).toInt().coerceAtLeast(logicalHeight)
        width to height
    }.getOrDefault(logicalWidth to logicalHeight)
}

private fun resolveDesktopJavaPath(mcVersion: McVersion): String {
    fun currentJavaIfMatches(): String? =
        javaExePath.takeIf { mcVersion.supportsCurrentJava(Runtime.version().feature()) }

    fun configuredJavaPath(major: Int): String? = when (major) {
        21 -> CONF.jre21Path
        25 -> CONF.jre25Path
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    return mcVersion.supportedJreVers
        .firstNotNullOfOrNull(::configuredJavaPath)
        ?: currentJavaIfMatches()
        ?: throw RequestError("请前往设置${mcVersion.supportedJreVers.joinToString("或") { "Java$it" }}路径")
}

