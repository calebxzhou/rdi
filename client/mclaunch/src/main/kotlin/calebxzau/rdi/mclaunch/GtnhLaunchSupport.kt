package calebxzau.rdi.mclaunch

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.LibraryOsArch
import calebxzhou.rdi.common.serdesJson
import calebxzau.rdi.mclaunch.model.MojangDownloadArtifact
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangLibraryDownloads
import calebxzau.rdi.mclaunch.model.MojangRule
import calebxzau.rdi.mclaunch.model.MojangRuleAction
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files

private const val GTNH_RFB_MAIN_CLASS = "com.gtnewhorizons.retrofuturabootstrap.Main"

@Serializable
private data class GtnhPrismPatch(
    val mainClass: String? = null,
    val libraries: List<MavenLibraryRef> = emptyList(),
    @SerialName("+tweakers") val addTweakers: List<String> = emptyList(),
    @SerialName("+jvmArgs") val addJvmArguments: List<String> = emptyList(),
)

@Serializable
private data class MavenLibraryRef(
    val name: String,
    val downloads: MojangLibraryDownloads = MojangLibraryDownloads(),
    val rules: List<MojangRule> = emptyList(),
    @SerialName("MMC-hint") val mmcHint: String? = null,
)

data class GtnhRuntimeDownloadPlan(
    val label: String,
    val target: File,
    val artifact: MojangDownloadArtifact,
)

private data class GtnhLwjgl3ifyFiles(
    val version: String,
    val runtimeMod: File?,
    val forgePatches: File,
)

class GtnhLaunchSupport(
    private val directories: MinecraftDirectories,
    private val launcherBrand: String,
    private val launcherVersion: String,
) {
    fun requireExtensionRoot(versionDir: File): File {
        val root = versionDir.resolve("gtnh")
        val requiredFiles = listOf(
            root.resolve("patches/net.minecraftforge.json"),
            root.resolve("patches/org.lwjgl3.json"),
            root.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json"),
            root.resolve("patches/me.eigenraven.lwjgl3ify.launchargs.json"),
        )
        val missing = requiredFiles.filterNot(File::isFile)
        if (missing.isNotEmpty()) {
            throw RequestError("整合包缺少GTNH扩展包，请确认${root.absolutePath}存在且完整，缺少: ${missing.joinToString(", ") { it.absolutePath }}")
        }
        val lwjgl3ifyFiles = resolveGtnhLwjgl3ifyFiles(root)
        if (!lwjgl3ifyFiles.forgePatches.isFile) {
            throw RequestError("整合包缺少GTNH lwjgl3ify ${lwjgl3ifyFiles.version}运行库: ${lwjgl3ifyFiles.forgePatches.absolutePath}")
        }
        return root
    }

    fun ensureRuntimeMods(versionDir: File, extensionRoot: File) {
        val source = resolveGtnhLwjgl3ifyFiles(extensionRoot).runtimeMod ?: return
        val target = versionDir.resolve("mods").resolve(source.name)
        if (target.isFile) return
        target.parentFile?.mkdirs()
        linkFile(source, target).getOrThrow()
    }

    fun buildLoaderManifest(extensionRoot: File, versionId: String): MojangVersionManifest {
        val forgePatch = extensionRoot.resolve("patches/net.minecraftforge.json")
            .let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
        val forgeTweakers = forgePatch.addTweakers.flatMap { listOf("--tweakClass", it) }
        val launchArgsPatch = extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.launchargs.json")
            .let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
        val minecraftArguments = (listOf(
            "--username", "\${auth_player_name}",
            "--version", "\${version_name}",
            "--gameDir", "\${game_directory}",
            "--assetsDir", "\${assets_root}",
            "--assetIndex", "\${assets_index_name}",
            "--uuid", "\${auth_uuid}",
            "--accessToken", "\${auth_access_token}",
            "--userProperties", "\${user_properties}",
            "--userType", "\${user_type}",
        ) + forgeTweakers).joinToString(" ")
        return MojangVersionManifest(
            id = versionId,
            mainClass = launchArgsPatch.mainClass ?: GTNH_RFB_MAIN_CLASS,
            libraries = forgePatch.libraries.map { it.toMojangLibrary() },
            minecraftArguments = minecraftArguments,
        )
    }

    fun buildJava25Classpath(extensionRoot: File): List<String> {
        val lwjgl3ifyFiles = resolveGtnhLwjgl3ifyFiles(extensionRoot)
        val localLibraries = listOf(lwjgl3ifyFiles.forgePatches)
        val extensionLibraries = listOf(
            extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json")
        ).flatMap { patchFile ->
            readGtnhPatchLibraries(patchFile).mapNotNull { gtnhLocalLibraryFile(extensionRoot, it) }
        }
        val globalLibraries = listOf(
            extensionRoot.resolve("patches/org.lwjgl3.json"),
            extensionRoot.resolve("patches/net.minecraftforge.json"),
        ).flatMap { patchFile ->
            readGtnhPatchLibraries(patchFile).mapNotNull { gtnhLibraryFile(extensionRoot, it) }
        }
        val minecraftJar = directories.versionsDir.resolve("1.7.10").resolve("1.7.10.jar")
        val missingLocal = (localLibraries + extensionLibraries).filterNot(File::isFile)
        val missingGlobal = (globalLibraries + minecraftJar).filterNot(File::isFile)
        val missing = missingLocal + missingGlobal
        if (missing.isNotEmpty()) {
            throw RequestError(
                buildString {
                    append("GTNH Java25启动缺少运行库: ")
                    append(missing.joinToString(", ") { it.absolutePath })
                    if (missingGlobal.isNotEmpty()) append("。请先在MC资源页安装1.7.10并下载GTNH扩展包需要的全局运行库")
                    if (missingLocal.isNotEmpty()) append("。请确认GTNH扩展包已完整安装到${extensionRoot.absolutePath}")
                }
            )
        }
        return (localLibraries + extensionLibraries + globalLibraries + minecraftJar)
            .map(File::getAbsolutePath)
            .distinct()
    }

    fun java25JvmArgs(
        extensionRoot: File,
        nativesDir: File,
        versionDir: File,
        versionId: String,
        classpath: String,
    ): List<String> = buildList {
        extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json")
            .takeIf(File::isFile)
            ?.let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
            ?.addJvmArguments
            ?.map {
                it.replaceLaunchTokens(
                    nativesDir = nativesDir,
                    versionDir = versionDir,
                    librariesDir = directories.librariesDir,
                    launcherBrand = launcherBrand,
                    launcherVersion = launcherVersion,
                    versionId = versionId,
                    classpath = classpath,
                )
            }
            ?.let(::addAll)
    }

    suspend fun ensureRuntime(
        versionDir: File,
        vanillaManifest: MojangVersionManifest,
        downloader: MinecraftArtifactDownloader,
        onProgress: (String) -> Unit,
    ): Result<Unit> = runCatching {
        val extensionRoot = requireExtensionRoot(versionDir)
        val plans = collectGlobalRuntimeDownloadPlans(extensionRoot, vanillaManifest).getOrThrow()
        if (plans.isEmpty()) {
            onProgress("GTNH运行库已齐全")
            return@runCatching
        }
        onProgress("开始自动下载${plans.size}个GTNH运行库")
        plans.forEachIndexed { index, plan ->
            onProgress("下载${index + 1}/${plans.size}: ${plan.label}")
            var lastProgressBucket = -1
            downloader.download(plan.label, plan.artifact, plan.target) { progress ->
                val bucket = when {
                    progress.totalBytes > 0 -> ((progress.fraction.coerceAtLeast(0f) * 100).toInt() / 10) * 10
                    progress.bytesDownloaded > 0 -> Int.MAX_VALUE
                    else -> 0
                }
                if (bucket == lastProgressBucket) return@download
                lastProgressBucket = bucket
                val downloaded = progress.bytesDownloaded.humanFileSize
                val total = progress.totalBytes.takeIf { it > 0 }?.humanFileSize ?: "未知"
                val suffix = if (progress.totalBytes > 0) " ${bucket.coerceAtMost(100)}%" else ""
                onProgress("${plan.label} $downloaded/$total$suffix")
            }.getOrThrow()
            onProgress("${plan.label}下载完成")
        }
    }

    private fun collectGlobalRuntimeDownloadPlans(
        extensionRoot: File,
        vanillaManifest: MojangVersionManifest,
    ): Result<List<GtnhRuntimeDownloadPlan>> = runCatching {
        buildList {
            listOf(
                extensionRoot.resolve("patches/org.lwjgl3.json"),
                extensionRoot.resolve("patches/net.minecraftforge.json"),
            ).forEach { patchFile ->
                readGtnhPatchLibraries(patchFile).forEach { library ->
                    val resolved = gtnhLibraryFile(extensionRoot, library) ?: return@forEach
                    if (resolved.isFile) return@forEach
                    val artifact = library.toResolvedArtifact() ?: return@forEach
                    val path = artifact.path ?: return@forEach
                    add(GtnhRuntimeDownloadPlan(library.name, directories.librariesDir.resolve(path), artifact))
                }
            }
            val minecraftJar = directories.versionsDir.resolve("1.7.10").resolve("1.7.10.jar")
            if (!minecraftJar.isFile) {
                val artifact = vanillaManifest.downloads?.client
                    ?: throw RequestError("1.7.10缺少客户端下载信息")
                add(GtnhRuntimeDownloadPlan("Minecraft客户端1.7.10", minecraftJar, artifact))
            }
        }.distinctBy { it.target.absolutePath.lowercase() }
    }

    private fun resolveGtnhLwjgl3ifyFiles(extensionRoot: File): GtnhLwjgl3ifyFiles {
        val patchFile = extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json")
        val library = readGtnhPatchLibraries(patchFile)
            .firstOrNull { it.isLwjgl3ifyForgePatches() }
            ?: throw RequestError("GTNH Prism patch缺少lwjgl3ify forgePatches库: ${patchFile.absolutePath}")
        val version = library.name.split(':')[2]
        val forgePatches = gtnhLocalLibraryFile(extensionRoot, library)
            ?: extensionRoot.resolve("libraries/lwjgl3ify-$version-forgePatches.jar")
        val runtimeMod = extensionRoot.resolve("libraries/lwjgl3ify-$version.jar").takeIf(File::isFile)
        return GtnhLwjgl3ifyFiles(version, runtimeMod, forgePatches)
    }

    private fun readGtnhPatchLibraries(file: File): List<MavenLibraryRef> = runCatching {
        serdesJson.decodeFromString<GtnhPrismPatch>(file.readText()).libraries
    }.getOrElse { error ->
        throw RequestError("GTNH Prism patch解析失败: ${file.absolutePath}: ${error.message}")
    }

    private fun gtnhLibraryPath(library: MavenLibraryRef): String? {
        if (!library.allowsCurrentOs()) return null
        return library.downloads.artifact?.path?.takeIf(String::isNotBlank)
            ?: library.downloads.artifact?.url?.mavenRelativePath()
            ?: library.name.split(':').takeIf { it.size >= 3 }?.let { parts ->
                val group = parts[0].replace('.', '/')
                val artifact = parts[1]
                val version = parts[2]
                val classifier = parts.getOrNull(3)?.takeIf(String::isNotBlank)
                buildString {
                    append(group).append('/').append(artifact).append('/').append(version).append('/')
                    append(artifact).append('-').append(version)
                    if (classifier != null) append('-').append(classifier)
                    append(".jar")
                }
            }
    }

    private fun gtnhLibraryFile(extensionRoot: File, library: MavenLibraryRef): File? {
        val path = gtnhLibraryPath(library) ?: return null
        val fileName = path.substringAfterLast('/')
        return extensionRoot.resolve("libraries").resolve(fileName).takeIf(File::isFile)
            ?: directories.librariesDir.resolve(path)
    }

    private fun gtnhLocalLibraryFile(extensionRoot: File, library: MavenLibraryRef): File? {
        if (library.mmcHint != "local" || !library.allowsCurrentOs()) return null
        val fileName = gtnhLibraryPath(library)?.substringAfterLast('/') ?: return null
        return extensionRoot.resolve("libraries").resolve(fileName)
    }

    private fun MavenLibraryRef.toMojangLibrary(): MojangLibrary = MojangLibrary(name, downloads, rules)

    private fun MavenLibraryRef.toResolvedArtifact(): MojangDownloadArtifact? {
        val artifact = toMojangLibrary().mainArtifact() ?: return null
        val path = gtnhLibraryPath(this) ?: artifact.path ?: return null
        return artifact.copy(path = path)
    }

    private fun MavenLibraryRef.isLwjgl3ifyForgePatches(): Boolean {
        val parts = name.split(':')
        return parts.size >= 4 &&
            parts[0] == "com.github.GTNewHorizons" &&
            parts[1] == "lwjgl3ify" &&
            parts[2].isNotBlank() &&
            parts[3] == "forgePatches"
    }

    private fun MavenLibraryRef.allowsCurrentOs(): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        val host = LibraryOsArch.detectHostOs()
        rules.forEach { rule ->
            if (rule.os == null || rule.os.name.matchesGtnhHostOs(host)) {
                allowed = rule.action == MojangRuleAction.allow
            }
        }
        return allowed
    }

    private fun String?.matchesGtnhHostOs(host: LibraryOsArch): Boolean = when {
        this == null -> true
        equals(host.ruleOsName, true) -> true
        equals("windows-arm64", true) -> host == LibraryOsArch.WIN_ARM64
        equals("osx-arm64", true) -> host == LibraryOsArch.MAC_ARM64
        equals("linux-arm64", true) -> host == LibraryOsArch.LINUX_ARM64
        else -> false
    }

    private fun String?.mavenRelativePath(): String? {
        val normalized = this?.trim()
        val knownBases = listOf(
            "https://libraries.minecraft.net/",
            "https://maven.minecraftforge.net/",
            "https://files.prismlauncher.org/maven/",
        )
        return knownBases.firstNotNullOfOrNull { base ->
            normalized?.removePrefix(base).takeIf { it != normalized && it?.isNotBlank() == true }
        }
    }

    private fun linkFile(source: File, target: File): Result<Unit> = runCatching {
        check(source.isFile) { "源文件不存在: ${source.absolutePath}" }
        target.parentFile?.mkdirs()
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        if (Files.exists(targetPath) && Files.isSameFile(sourcePath, targetPath)) return@runCatching
        Files.deleteIfExists(targetPath)
        Files.createLink(targetPath, sourcePath)
    }
}

fun MojangLibrary.isLegacyLwjgl2Library(): Boolean {
    val coordinates = name.split(':')
    return coordinates.size >= 2 &&
        coordinates[0] == "org.lwjgl.lwjgl" &&
        coordinates[1] in setOf("lwjgl", "lwjgl_util", "lwjgl-platform")
}
