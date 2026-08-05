package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.common.model.LibraryOsArch
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** Forge/NeoForge installer bootstrapper and the client-side launcher adapter. */
private val lgr by Loggers

internal fun GameService.runInstallerBootstrapper(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext,
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
    }
    ctx.emitProgress(TaskProgress("安装成功", 1f))
}

internal fun GameService.runServerInstallerBootstrapper(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext,
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
    }
    ctx.emitProgress(TaskProgress("安装成功", 1f))
}

fun GameService.startDesktop(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = startDesktopInDir(
    mcVer,
    versionId,
    versionListDir.resolve(versionId),
    *jvmArgs,
    onLine = onLine,
)

internal fun GameService.startDesktopInDir(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    versionDir: File,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = createMinecraftLauncher()
    .launch(
        request = minecraftLaunchRequest(
            mcVersion = mcVer,
            versionId = versionId,
            versionDir = versionDir,
            extraJvmArgs = jvmArgs.toList(),
        ),
        onLine = onLine,
    )
    .getOrThrow()

internal suspend fun GameService.ensureDesktopLaunchLibraries(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    versionDir: File = versionListDir.resolve(versionId),
    onProgress: (String) -> Unit,
): Result<Unit> = createMinecraftLauncher().prepare(
    request = minecraftLaunchRequest(
        mcVersion = mcVer,
        versionId = versionId,
        versionDir = versionDir,
    ),
    onProgress = onProgress,
)

fun GameService.startServerDesktop(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    loaderVer: calebxzhou.rdi.common.model.ModLoader.Version,
    workDir: File,
    onLine: (String) -> Unit,
): Process {
    val process = createMinecraftLauncher()
        .launchServer(mcVer, loaderVer, workDir, onLine)
        .getOrThrow()
    serverStarted = true
    thread(name = "mc-server-state", isDaemon = true) {
        process.waitFor()
        serverStarted = false
    }
    return process
}

internal fun linkServerRuntimeFile(link: File, source: File) {
    link.parentFile?.mkdirs()
    hardLinkFile(source, link).getOrThrow()
}
