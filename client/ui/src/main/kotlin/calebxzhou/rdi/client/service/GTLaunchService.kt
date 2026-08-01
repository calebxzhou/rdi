package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.javaExePath
import calebxzau.rdi.client.CONF
import calebxzhou.rdi.client.model.*
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.LibraryOsArch
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.*

internal const val GTNH_RFB_MAIN_CLASS = "com.gtnewhorizons.retrofuturabootstrap.Main"

@Serializable
private data class GtnhPrismPatch(
    val mainClass: String? = null,
    val libraries: List<MavenLibraryRef> = emptyList(),
    @SerialName("+tweakers") val addTweakers: List<String> = emptyList(),
    @SerialName("+jvmArgs") val addnJvmArguments: List<String> = emptyList(),
)

@Serializable
private data class MavenLibraryRef(
    val name: String,
    val downloads: MojangLibraryDownloads = MojangLibraryDownloads(),
    val rules: List<MojangRule> = emptyList(),
    @SerialName("MMC-hint") val mmcHint: String? = null,
)

internal data class GtnhRuntimeDownloadPlan(
    val label: String,
    val target: File,
    val artifact: MojangDownloadArtifact,
)

private data class GtnhLwjgl3ifyFiles(
    val version: String,
    val runtimeMod: File?,
    val forgePatches: File,
)

internal fun requireGtnhExtensionRoot(versionDir: File): File {
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
    val missingLibraries = listOf(lwjgl3ifyFiles.forgePatches).filterNot(File::isFile)
    if (missingLibraries.isNotEmpty()) {
        throw RequestError("整合包缺少GTNH lwjgl3ify ${lwjgl3ifyFiles.version}运行库: ${missingLibraries.joinToString(", ") { it.absolutePath }}")
    }
    return root
}

internal fun ensureGtnhRuntimeMods(versionDir: File, extensionRoot: File) {
    val source = resolveGtnhLwjgl3ifyFiles(extensionRoot).runtimeMod ?: return
    val target = versionDir.resolve("mods").resolve(source.name)
    if (target.isFile) return
    target.parentFile?.mkdirs()
    hardLinkFile(source, target).getOrThrow()
}

internal fun buildGtnhLoaderManifest(extensionRoot: File, versionId: String): MojangVersionManifest {
    val forgePatch = extensionRoot.resolve("patches/net.minecraftforge.json")
        .let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
    val forgeTweakers = forgePatch.addTweakers.flatMap { listOf("--tweakClass", it) }
    val launchArgsPatch = extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.launchargs.json")
        .let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
    val minecraftArguments = (listOf(
        "--username", $$"${auth_player_name}",
        "--version", $$"${version_name}",
        "--gameDir", $$"${game_directory}",
        "--assetsDir", $$"${assets_root}",
        "--assetIndex", $$"${assets_index_name}",
        "--uuid", $$"${auth_uuid}",
        "--accessToken", $$"${auth_access_token}",
        "--userProperties", $$"${user_properties}",
        "--userType", $$"${user_type}",
    ) + forgeTweakers).joinToString(" ")
    return MojangVersionManifest(
        id = versionId,
        mainClass = launchArgsPatch.mainClass ?: GTNH_RFB_MAIN_CLASS,
        libraries = forgePatch.libraries.map(MavenLibraryRef::toMojangLibrary),
        minecraftArguments = minecraftArguments,
    )
}

internal fun buildGtnhJava25Classpath(extensionRoot: File): List<String> {
    val lwjgl3ifyFiles = resolveGtnhLwjgl3ifyFiles(extensionRoot)
    val localLibraries = listOf(
        lwjgl3ifyFiles.forgePatches,
    )
    val extensionLibraries = listOf(
        extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json"),
    ).flatMap { patchFile ->
        readGtnhPatchLibraries(patchFile).mapNotNull { gtnhLocalLibraryFile(extensionRoot, it) }
    }
    val globalLibraries = listOf(
        extensionRoot.resolve("patches/org.lwjgl3.json"),
        extensionRoot.resolve("patches/net.minecraftforge.json"),
    ).flatMap { patchFile ->
        readGtnhPatchLibraries(patchFile)
            .mapNotNull { gtnhLibraryFile(extensionRoot, it) }
    }
    val minecraftJar = ClientDirs.versionsDir.resolve("1.7.10").resolve("1.7.10.jar")
    val missingLocalLibraries = (localLibraries + extensionLibraries).filterNot(File::isFile)
    val missingGlobalLibraries = (globalLibraries + minecraftJar).filterNot(File::isFile)
    val missing = missingLocalLibraries + missingGlobalLibraries
    if (missing.isNotEmpty()) {
        throw RequestError(
            buildString {
                append("GTNH Java25启动缺少运行库: ")
                append(missing.joinToString(", ") { it.absolutePath })
                if (missingGlobalLibraries.isNotEmpty()) {
                    append("。请先在MC资源页安装1.7.10并下载GTNH扩展包需要的全局运行库")
                }
                if (missingLocalLibraries.isNotEmpty()) {
                    append("。请确认GTNH扩展包已完整安装到${extensionRoot.absolutePath}")
                }
            }
        )
    }
    return (localLibraries + extensionLibraries + globalLibraries + minecraftJar)
        .map { it.absolutePath }
        .distinct()
}

internal fun GameService.collectGtnhGlobalRuntimeDownloadPlans(extensionRoot: File): Result<List<GtnhRuntimeDownloadPlan>> = runCatching {
    val plans = buildList {
        listOf(
            extensionRoot.resolve("patches/org.lwjgl3.json"),
            extensionRoot.resolve("patches/net.minecraftforge.json"),
        ).forEach { patchFile ->
            readGtnhPatchLibraries(patchFile).forEach { library ->
                val resolvedForClasspath = gtnhLibraryFile(extensionRoot, library) ?: return@forEach
                if (resolvedForClasspath.isFile) return@forEach
                val artifact = toResolvedGtnhArtifact(library) ?: return@forEach
                val path = artifact.path ?: return@forEach
                add(
                    GtnhRuntimeDownloadPlan(
                        label = library.name,
                        target = ClientDirs.librariesDir.resolve(path),
                        artifact = artifact
                    )
                )
            }
        }
        val minecraftJar = ClientDirs.versionsDir.resolve("1.7.10").resolve("1.7.10.jar")
        if (!minecraftJar.isFile) {
            val artifact = McVersion.V071.manifest.downloads?.client
                ?: throw RequestError("1.7.10缺少客户端下载信息")
            add(
                GtnhRuntimeDownloadPlan(
                    label = "Minecraft客户端1.7.10",
                    target = minecraftJar,
                    artifact = artifact
                )
            )
        }
    }
    plans.distinctBy { it.target.absolutePath.lowercase(Locale.ROOT) }
}

internal suspend fun GameService.ensureGtnhRuntime(
    versionDir: File,
    onProgress: (String) -> Unit
): Result<Unit> = runCatching {
    val extensionRoot = requireGtnhExtensionRoot(versionDir)
    val plans = collectGtnhGlobalRuntimeDownloadPlans(extensionRoot).getOrThrow()
    if (plans.isEmpty()) {
        onProgress("GTNH运行库已齐全")
        return@runCatching
    }
    onProgress("开始自动下载${plans.size}个GTNH运行库")
    plans.forEachIndexed { index, plan ->
        onProgress("下载${index + 1}/${plans.size}: ${plan.label}")
        var lastProgressBucket = -1
        downloadArtifact(plan.label, plan.artifact, plan.target) { progress ->
            val bucket = when {
                progress.totalBytes > 0 -> ((progress.fraction.coerceAtLeast(0f) * 100).toInt() / 10) * 10
                progress.bytesDownloaded > 0 -> Int.MAX_VALUE
                else -> 0
            }
            if (bucket == lastProgressBucket) return@downloadArtifact
            lastProgressBucket = bucket
            val downloaded = progress.bytesDownloaded.humanFileSize
            val total = progress.totalBytes.takeIf { it > 0 }?.humanFileSize ?: "未知"
            val suffix = if (progress.totalBytes > 0) " ${bucket.coerceAtMost(100)}%" else ""
            onProgress("${plan.label} $downloaded/$total$suffix")
        }.getOrThrow()
        onProgress("${plan.label}下载完成")
    }
}

internal fun MojangLibrary.isLegacyLwjgl2Library(): Boolean {
    val coords = name.split(':')
    if (coords.size < 2) return false
    return coords[0] == "org.lwjgl.lwjgl" && coords[1] in setOf("lwjgl", "lwjgl_util", "lwjgl-platform")
}

internal fun gtnhJava25JvmArgs(
    extensionRoot: File,
    nativesDir: File,
    versionDir: File,
    versionId: String,
    classpath: String,
): List<String> = buildList {
    extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json")
        .takeIf(File::isFile)
        ?.let { serdesJson.decodeFromString<GtnhPrismPatch>(it.readText()) }
        ?.addnJvmArguments
        ?.map { it.replaceLaunchTokens(nativesDir, versionDir, versionId, classpath) }
        ?.let(::addAll)
    //下载太慢没用
   // this += "-DassetDirector.downloadRedirectBaseUrl=https://bmclapi2.bangbang93.com"
}

internal fun resolveJava25Path(): String {
    CONF.jre25Path?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return javaExePath.takeIf { Runtime.version().feature() == 25 }
        ?: throw RequestError("GTNH Java25版需要在设置中配置Java25路径")
}

private fun readGtnhPatchLibraries(file: File): List<MavenLibraryRef> = runCatching {
    val patch = serdesJson.decodeFromString<GtnhPrismPatch>(file.readText())
    patch.libraries
}.getOrElse { e ->
    throw RequestError("GTNH Prism patch解析失败: ${file.absolutePath}: ${e.message}")
}

private fun resolveGtnhLwjgl3ifyFiles(extensionRoot: File): GtnhLwjgl3ifyFiles {
    val patchFile = extensionRoot.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json")
    val library = readGtnhPatchLibraries(patchFile)
        .firstOrNull(MavenLibraryRef::isLwjgl3ifyForgePatches)
        ?: throw RequestError("GTNH Prism patch缺少lwjgl3ify forgePatches库: ${patchFile.absolutePath}")
    val version = library.name.split(':')[2]
    val forgePatches = gtnhLocalLibraryFile(extensionRoot, library)
        ?: extensionRoot.resolve("libraries/lwjgl3ify-$version-forgePatches.jar")
    val runtimeMod = extensionRoot.resolve("libraries/lwjgl3ify-$version.jar").takeIf(File::isFile)
    return GtnhLwjgl3ifyFiles(
        version = version,
        runtimeMod = runtimeMod,
        forgePatches = forgePatches,
    )
}

private fun MavenLibraryRef.isLwjgl3ifyForgePatches(): Boolean {
    val parts = name.split(':')
    return parts.size >= 4 &&
        parts[0] == "com.github.GTNewHorizons" &&
        parts[1] == "lwjgl3ify" &&
        parts[2].isNotBlank() &&
        parts[3] == "forgePatches"
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
            val fileName = buildString {
                append(artifact).append('-').append(version)
                if (classifier != null) append('-').append(classifier)
                append(".jar")
            }
            "$group/$artifact/$version/$fileName"
        }
}

private fun gtnhLibraryFile(extensionRoot: File, library: MavenLibraryRef): File? {
    if (!library.allowsCurrentOs()) return null
    val path = gtnhLibraryPath(library) ?: return null
    val fileName = path.substringAfterLast('/')
    return extensionRoot.resolve("libraries").resolve(fileName)
        .takeIf(File::isFile)
        ?: ClientDirs.librariesDir.resolve(path)
}

private fun gtnhLocalLibraryFile(extensionRoot: File, library: MavenLibraryRef): File? {
    if (library.mmcHint != "local" || !library.allowsCurrentOs()) return null
    val fileName = gtnhLibraryPath(library)?.substringAfterLast('/') ?: return null
    return extensionRoot.resolve("libraries").resolve(fileName)
}

private fun MavenLibraryRef.toMojangLibrary(): MojangLibrary = MojangLibrary(
    name = name,
    downloads = downloads,
    rules = rules,
)

private fun GameService.toResolvedGtnhArtifact(libraryRef: MavenLibraryRef): MojangDownloadArtifact? {
    val library = libraryRef.toMojangLibrary()
    val artifact = library.mainArtifact() ?: return null
    val path = gtnhLibraryPath(libraryRef) ?: artifact.path ?: return null
    return artifact.copy(path = path)
}

private fun String.mavenRelativePath(): String? {
    val normalized = trim()
    val knownBases = listOf(
        "https://libraries.minecraft.net/",
        "https://maven.minecraftforge.net/",
        "https://files.prismlauncher.org/maven/",
    )
    return knownBases.firstNotNullOfOrNull { base ->
        normalized.removePrefix(base).takeIf { it != normalized && it.isNotBlank() }
    }
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
    equals("linux-arm32", true) -> false
    else -> false
}
