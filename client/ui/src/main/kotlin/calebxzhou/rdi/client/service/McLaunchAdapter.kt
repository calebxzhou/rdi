package calebxzhou.rdi.client.service

import calebxzau.rdi.mcinstall.McInstall
import calebxzau.rdi.mclaunch.MinecraftLaunchOverrides
import calebxzhou.rdi.common.util.hardLinkFile
import java.io.File

fun McInstall.startDesktop(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = startDesktopInDir(
    mcVer,
    versionId,
    versionListDir.resolve(versionId),
    MinecraftLaunchOverrides(),
    *jvmArgs,
    onLine = onLine,
)

fun McInstall.startDesktop(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    launchOverrides: MinecraftLaunchOverrides,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = startDesktopInDir(
    mcVer,
    versionId,
    versionListDir.resolve(versionId),
    launchOverrides,
    *jvmArgs,
    onLine = onLine,
)

internal fun McInstall.startDesktopInDir(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    versionDir: File,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = startDesktopInDir(
    mcVer,
    versionId,
    versionDir,
    MinecraftLaunchOverrides(),
    *jvmArgs,
    onLine = onLine,
)

internal fun McInstall.startDesktopInDir(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    versionId: String,
    versionDir: File,
    launchOverrides: MinecraftLaunchOverrides,
    vararg jvmArgs: String,
    onLine: (String) -> Unit,
): Process = createMinecraftLauncher()
    .launch(
        request = minecraftLaunchRequest(
            mcVersion = mcVer,
            versionId = versionId,
            versionDir = versionDir,
            launchOverrides = launchOverrides,
            extraJvmArgs = jvmArgs.toList(),
        ),
        onLine = onLine,
    )
    .getOrThrow()

internal suspend fun McInstall.ensureDesktopLaunchLibraries(
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

fun McInstall.startServerDesktop(
    mcVer: calebxzhou.rdi.common.model.McVersion,
    loaderVer: calebxzhou.rdi.common.model.ModLoader.Version,
    workDir: File,
    onLine: (String) -> Unit,
): Process {
    return createMinecraftLauncher()
        .launchServer(mcVer, loaderVer, workDir, onLine)
        .getOrThrow()
}

internal fun linkServerRuntimeFile(link: File, source: File) {
    link.parentFile?.mkdirs()
    hardLinkFile(source, link).getOrThrow()
}
