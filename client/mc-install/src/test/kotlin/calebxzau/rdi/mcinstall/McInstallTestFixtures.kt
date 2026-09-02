package calebxzau.rdi.mcinstall

import java.nio.file.Files
import java.nio.file.Path

internal fun createTestMcInstall(
    root: Path = Files.createTempDirectory("mc-install-test"),
): McInstall {
    val mcDir = root.resolve("mc").toFile().apply { mkdirs() }
    val assetsDir = mcDir.resolve("assets").apply { mkdirs() }
    return McInstall(
        McInstallEnvironment(
            directories = McInstallDirectories(
                mcDir = mcDir,
                versionsDir = mcDir.resolve("versions").apply { mkdirs() },
                librariesDir = mcDir.resolve("libraries").apply { mkdirs() },
                assetsDir = assetsDir,
                assetIndexesDir = assetsDir.resolve("indexes").apply { mkdirs() },
                assetObjectsDir = assetsDir.resolve("objects").apply { mkdirs() },
            ),
            preferMirror = { false },
            javaPath = { "java" },
            resourceLoader = { name ->
                runCatching {
                    checkNotNull(McInstall::class.java.classLoader.getResourceAsStream(name)) {
                        "缺少测试资源:$name"
                    }
                }
            },
            launchPreparer = McLaunchPreparer { _, _, _, _ -> Result.success(Unit) },
        )
    )
}
