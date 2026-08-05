package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.client.Const
import calebxzau.rdi.client.CONF
import calebxzau.rdi.client.ScreenSize
import calebxzhou.rdi.client.model.loaderManifest
import calebxzhou.rdi.client.model.manifest
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.McVersion
import calebxzau.rdi.mclaunch.MinecraftAccount
import calebxzau.rdi.mclaunch.MinecraftArtifactDownloader
import calebxzau.rdi.mclaunch.MinecraftDirectories
import calebxzau.rdi.mclaunch.MinecraftDownloadProgress
import calebxzau.rdi.mclaunch.MinecraftJava25Config
import calebxzau.rdi.mclaunch.MinecraftLaunchEnvironment
import calebxzau.rdi.mclaunch.MinecraftLaunchRequest
import calebxzau.rdi.mclaunch.MinecraftLauncher
import calebxzau.rdi.mclaunch.MinecraftManifestPair
import calebxzau.rdi.mclaunch.MinecraftManifestProvider
import calebxzau.rdi.mclaunch.MinecraftWindowSize
import java.awt.GraphicsEnvironment
import java.io.File

internal fun createMinecraftLauncher(): MinecraftLauncher {
    val directories = MinecraftDirectories(
        mcDir = ClientDirs.mcDir,
        versionsDir = ClientDirs.versionsDir,
        librariesDir = ClientDirs.librariesDir,
        assetsDir = ClientDirs.assetsDir,
        toolsDir = ClientDirs.toolsDir,
    )
    return MinecraftLauncher(
        MinecraftLaunchEnvironment(
            directories = directories,
            java = MinecraftJava25Config(
                configuredJava25Path = CONF.jre25Path,
                currentJavaPath = javaExePath,
                currentJavaMajor = Runtime.version().feature(),
                maxMemoryMb = CONF.maxMemory,
            ),
            launcherBrand = "rdi",
            launcherVersion = Const.VERSION_NUMBER,
            debug = DEBUG,
            manifestProvider = { mcVersion, _, _ ->
                runCatching { MinecraftManifestPair(mcVersion.manifest, mcVersion.loaderManifest) }
            },
            artifactDownloader = { label, artifact, target, onProgress ->
                GameService.downloadArtifact(label, artifact, target) { progress ->
                    onProgress(
                        MinecraftDownloadProgress(
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                            fraction = progress.fraction,
                        )
                    )
                }
            },
        )
    )
}

internal fun minecraftLaunchRequest(
    mcVersion: McVersion,
    versionId: String,
    versionDir: File,
    extraJvmArgs: List<String> = emptyList(),
): MinecraftLaunchRequest = MinecraftLaunchRequest(
    mcVersion = mcVersion,
    versionId = versionId,
    versionDir = versionDir,
    account = MinecraftAccount(
        name = loggedAccount.name,
        uuid = loggedAccount.uuid.toString(),
        accessToken = loggedAccount.jwt.orEmpty(),
    ),
    windowSize = resolveMinecraftWindowSize(),
    extraJvmArgs = extraJvmArgs,
)

private fun resolveMinecraftWindowSize(): MinecraftWindowSize {
    val logicalWidth = ScreenSize.first.value.toInt()
    val logicalHeight = ScreenSize.second.value.toInt()
    return runCatching {
        val transform = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
        val scaleX = transform.scaleX.takeIf { it > 0.0 } ?: 1.0
        val scaleY = transform.scaleY.takeIf { it > 0.0 } ?: 1.0
        MinecraftWindowSize(
            width = (logicalWidth * scaleX).toInt().coerceAtLeast(logicalWidth),
            height = (logicalHeight * scaleY).toInt().coerceAtLeast(logicalHeight),
        )
    }.getOrDefault(MinecraftWindowSize(logicalWidth, logicalHeight))
}
