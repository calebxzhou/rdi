package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.ScreenSize
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
import java.util.*
import kotlin.concurrent.thread

/**
 * Desktop-only game launching and installer bootstrapper code.
 * Download logic is now in commonMain GameService.
 */
private val lgr by Loggers
private val utf8LoggingJvmArgs = listOf(
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8"
)

// ---- Installer Bootstrapper (desktop actual) ----

internal actual fun GameService.runInstallerBootstrapperDesktop(
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

internal actual fun GameService.runServerInstallerBootstrapperDesktop(
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

// ---- Desktop-only game launching ----

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
    val loaderManifest = mcVer.loaderManifest
    val manifest = mcVer.manifest
    val nativesDir = mcVer.nativesDir
    val resolvedGameArgs = resolveLaunchGameArguments(manifest, loaderManifest)
    val gameArgs = resolvedGameArgs.map {
        it.replace("\${auth_player_name}", loggedAccount.name)
            .replace("\${version_name}", versionId)
            .replace("\${game_directory}", versionDir.absolutePath)
            .replace("\${assets_root}", ClientDirs.assetsDir.absolutePath)
            .replace("\${assets_index_name}", manifest.assets!!)
            .replace("\${auth_uuid}", loggedAccount._id.toUUID().toString().replace("-", ""))
            .replace("\${auth_access_token}", loggedAccount.jwt ?: "")
            .replace("\${user_type}", "msa")
            .replace("\${version_type}", "RDI")
    }.toMutableList()
    val (physicalWidth, physicalHeight) = resolvePhysicalScreenSize()
    gameArgs += listOf("--width", "$physicalWidth", "--height", "$physicalHeight")
    val resolvedJvmArgs = manifest.resolveJvmArgumentList() + loaderManifest.resolveJvmArgumentList()
    val launchClasspath = buildLaunchClasspath(
        manifest = manifest,
        loaderManifest = loaderManifest,
        versionDir = versionDir,
        versionId = versionId
    )
    val classpath = launchClasspath.joinToString(File.pathSeparator)
    val legacyLaunch = resolvedJvmArgs.isEmpty() &&
        (!manifest.minecraftArguments.isNullOrBlank() || !loaderManifest.minecraftArguments.isNullOrBlank())
    val hasClasspathDeclaration = resolvedJvmArgs.any {
        it == "-cp" || it == "-classpath" || it.contains("\${classpath}")
    }
    val processedJvmArgs = resolvedJvmArgs.map { arg ->
        arg.replace("\${natives_directory}", nativesDir.absolutePath)
            .replace("\${library_directory}", ClientDirs.librariesDir.absolutePath)
            .replace("\${launcher_name}", "rdi")
            .replace("\${launcher_version}", Const.VERSION_NUMBER)
            .replace("\${classpath}", classpath)
            .replace("\${classpath_separator}", File.pathSeparator)
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
        this += jvmArgs
        if(DEBUG){
            this +=  "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
        }
    }

    lgr.info { "JVM Args: ${processedJvmArgs.joinToString(" ")}" }
    lgr.info { "Game Args: ${gameArgs.joinToString(" ")}" }
    val jrePath = resolveDesktopJavaPath(mcVer)
    val command = listOf(
        jrePath,
        *processedJvmArgs.toTypedArray(),
        loaderManifest.mainClass,
        *gameArgs.toTypedArray(),
    )
    val process = ProcessBuilder(command)
        .directory(versionDir)
        .redirectErrorStream(true)
        .start()
    started = true
    thread(name = "mc-log-reader", isDaemon = true) {
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
                onLine("MC已结束，退出代码: $exitCode")
            } else {
                onLine("已退出")
            }
        } finally {
            started = false
        }
    }
    return process
}

private fun GameService.buildLaunchClasspath(
    manifest: MojangVersionManifest,
    loaderManifest: MojangVersionManifest,
    versionDir: File,
    versionId: String
): List<String> {
    val entries = LinkedHashSet<String>()
    entries += buildClasspath(
        baseLibraries = manifest.libraries,
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

fun GameService.startServerDesktop(mcVer: McVersion, loaderVer: ModLoader.Version, workDir: File, onLine: (String) -> Unit): Process {
    val hostOs = LibraryOsArch.detectHostOs()
    val jrePath = resolveDesktopJavaPath(mcVer)
    val command = mutableListOf(
        jrePath,
        "-Xmx6G",
        *utf8LoggingJvmArgs.toTypedArray(),
    ).apply {
        when (mcVer) {
            McVersion.V182,
            McVersion.V192,
            McVersion.V201,
            McVersion.V211 -> {
                this += loaderVer.serverArgsPath(hostOs.isUnixLike)
                this += "%*"
            }

            McVersion.V165 -> {
                if (loaderVer.loader == ModLoader.forge) {
                    McVersion.V165.plusJvmArgs.forEach { this += it }
                    val jarFileName = "forge-${loaderVer.id}.jar"
                    this += "-jar"
                    this += jarFileName
                    Files.createSymbolicLink(
                        workDir.resolve(jarFileName).toPath(),
                        ClientDirs.mcDir.resolve(jarFileName).toPath()
                    )
                }
            }

            McVersion.V122 -> {
                if (loaderVer.loader == ModLoader.forge || loaderVer.loader == ModLoader.cleanroom) {
                    McVersion.V122.plusJvmArgs.forEach { this += it }
                    val jarFileName = "cleanroom-${loaderVer.id}.jar"
                    this += "-jar"
                    this += jarFileName
                    Files.createSymbolicLink(
                        workDir.resolve(jarFileName).toPath(),
                        ClientDirs.mcDir.resolve(jarFileName).toPath()
                    )
                    //mc server jar
                    val mcServerJar = "minecraft_server.1.12.2.jar"
                    Files.createSymbolicLink(
                        workDir.resolve(mcServerJar).toPath(),
                        ClientDirs.mcDir.resolve(mcServerJar).toPath()
                    )
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
        8 -> CONF.jre8Path
        21 -> CONF.jre21Path
        25 -> CONF.jre25Path
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    return mcVersion.supportedJreVers
        .firstNotNullOfOrNull(::configuredJavaPath)
        ?: currentJavaIfMatches()
        ?: throw RequestError("请前往设置${mcVersion.supportedJreVers.joinToString("或") { "Java$it" }}路径")
}
