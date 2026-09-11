package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.database.MinecraftInstallationStore
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.serdesJson
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class LocalModpackCandidate(
    val name: String,
    val minecraftInstallation: Path,
    val versionDirectory: Path,
    val mcVersion: McVersion,
    val modLoader: ModLoader,
)

/** Finds usable, previously installed packs without including the launcher's managed instance. */
class LocalModpackScanner(
    private val installationStore: MinecraftInstallationStore,
    private val managedMinecraftDirectory: Path,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun scan(): Result<List<LocalModpackCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            installationStore.list().getOrThrow()
                .asSequence()
                .map { it.path.toAbsolutePath().normalize() }
                .filterNot(::isManagedDirectory)
                .flatMap(::scanInstallation)
                .distinctBy { pathKey(it.versionDirectory) }
                .sortedWith(
                    compareBy<LocalModpackCandidate> { it.minecraftInstallation.toString().lowercase(Locale.ROOT) }
                        .thenBy { it.name.lowercase(Locale.ROOT) },
                )
                .toList()
        }
    }

    private fun scanInstallation(installation: Path): Sequence<LocalModpackCandidate> {
        val versionsDirectory = installation.resolve("versions")
        if (!Files.isDirectory(versionsDirectory)) return emptySequence()
        return runCatching {
            Files.list(versionsDirectory).use { paths ->
                paths.filter(Files::isDirectory)
                    .map(::scanCandidate)
                    .filter { it != null }
                    .map { it!! }
                    .toList()
                    .asSequence()
            }
        }.onFailure { cause ->
            logger.warn(cause) { "扫描外部整合包失败：$versionsDirectory" }
        }.getOrDefault(emptySequence())
    }

    private fun scanCandidate(directory: Path): LocalModpackCandidate? = runCatching {
        val name = directory.fileName.toString()
        val manifestFile = directory.resolve("$name.json")
        require(Files.isRegularFile(manifestFile)) { "缺少版本清单" }
        val modsDirectory = directory.resolve("mods")
        require(Files.isDirectory(modsDirectory) && hasDirectJar(modsDirectory)) { "没有Mod文件" }

        val manifest = serdesJson.decodeFromString<MojangVersionManifest>(Files.readString(manifestFile))
        val minecraftVersion = manifest.clientVersion ?: manifest.inheritsFrom ?: manifest.id
        val mcVersion = McVersion.from(minecraftVersion)
            ?: error("不支持MC版本$minecraftVersion")
        if(!mcVersion.isModern) error("不是现代MC版本${minecraftVersion}")
        val modLoader = inferLoader(manifest.mainClass, mcVersion)
            ?: error("不支持启动类${manifest.mainClass.orEmpty()}")
        require(modLoader in mcVersion.loaderVersions) { "不支持${mcVersion.mcVer}/$modLoader" }

        LocalModpackCandidate(
            name = name,
            minecraftInstallation = directory.parent.parent,
            versionDirectory = directory,
            mcVersion = mcVersion,
            modLoader = modLoader,
        )
    }.onFailure { cause ->
        logger.info { "忽略外部整合包$directory：${cause.message}" }
    }.getOrNull()

    private fun hasDirectJar(directory: Path): Boolean = Files.list(directory).use { paths ->
        paths.anyMatch { path ->
            Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar", ignoreCase = true)
        }
    }

    private fun inferLoader(mainClass: String?, mcVersion: McVersion): ModLoader? = when (mainClass) {
        BOOTSTRAP_LAUNCHER -> if (mcVersion == McVersion.V211) ModLoader.neoforge else ModLoader.forge
        LEGACY_LAUNCHWRAPPER -> ModLoader.forge
        else -> null
    }

    private fun isManagedDirectory(path: Path): Boolean = pathKey(path) == pathKey(managedMinecraftDirectory)

    private fun pathKey(path: Path): String =
        path.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)

    private companion object {
        const val BOOTSTRAP_LAUNCHER = "cpw.mods.bootstraplauncher.BootstrapLauncher"
        const val LEGACY_LAUNCHWRAPPER = "net.minecraft.launchwrapper.Launch"
    }
}
