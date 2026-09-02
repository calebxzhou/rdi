package calebxzau.rdi.mcinstall

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.model.LibraryOsArch
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import java.nio.charset.StandardCharsets

private val lgr by Loggers

internal fun McInstall.runInstallerBootstrapper(
    holder: McInstall.LoaderInstallHolder,
    ctx: TaskContext,
) {
    val installBooter = holder.installBooter ?: error("安装引导未准备")
    val installer = holder.installer ?: error("安装器未准备")
    val classpathSeparator = if (LibraryOsArch.detectHostOs().isWindows) ";" else ":"
    val classpath = listOf(installBooter.absolutePath, installer.absolutePath).joinToString(classpathSeparator)
    val mcDir = environment.directories.mcDir
    val command = listOf(
        environment.javaPath(),
        "-cp",
        classpath,
        "com.bangbang93.ForgeInstaller",
        mcDir.absolutePath,
    )
    val process = ProcessBuilder(command)
        .directory(mcDir)
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

internal fun McInstall.runServerInstallerBootstrapper(
    holder: McInstall.LoaderInstallHolder,
    ctx: TaskContext,
) {
    val installer = holder.installer ?: error("安装器未准备")
    val mcDir = environment.directories.mcDir
    val command = listOf(
        environment.javaPath(),
        "-jar",
        installer.absolutePath,
        "--installServer",
        mcDir.absolutePath,
    )
    val process = ProcessBuilder(command)
        .directory(mcDir)
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
