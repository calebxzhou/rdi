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
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.util.toUUID
import com.sun.management.OperatingSystemMXBean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
private const val GTNH_RFB_MAIN_CLASS = "com.gtnewhorizons.retrofuturabootstrap.Main"
private const val GTNH_LWJGL3IFY_VERSION = "2.1.16"
private const val GTNH_SERVER_FORGE_PATCHES_JAR = "lwjgl3ify-forgePatches.jar"
private const val GTNH_LOG4J_VERSION = "2.0-beta9"

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

private data class GtnhRuntimeDownloadPlan(
    val label: String,
    val target: File,
    val artifact: MojangDownloadArtifact,
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
    val isGtnhJava25Launch = mcVer == McVersion.V071
    val gtnhExtensionRoot = if (isGtnhJava25Launch) {
        requireGtnhExtensionRoot(versionDir).also { ensureGtnhRuntimeMods(versionDir, it) }
    } else null
    val loaderManifest = if (gtnhExtensionRoot != null) {
        buildGtnhLoaderManifest(gtnhExtensionRoot, versionId)
    } else {
        resolveLocalLoaderManifest(versionDir, versionId) ?: mcVer.loaderManifest
    }
    val manifest = mcVer.manifest
    val nativesDir = if (isGtnhJava25Launch) {
        mcVer.nativesDir
    } else {
        versionDir.resolve("natives").takeIf(File::exists) ?: mcVer.nativesDir
    }
    val resolvedGameArgs = resolveLaunchGameArguments(manifest, loaderManifest)
    val gameArgs = resolvedGameArgs.map {
        it.replace("\${auth_player_name}", loggedAccount.name)
            .replace("\${version_name}", versionId)
            .replace("\${game_directory}", versionDir.absolutePath)
            .replace("\${assets_root}", ClientDirs.assetsDir.absolutePath)
            .replace("\${assets_index_name}", manifest.assets ?: manifest.assetIndex?.id ?: manifest.id)
            .replace("\${auth_uuid}", loggedAccount._id.toUUID().toString().replace("-", ""))
            .replace("\${auth_access_token}", loggedAccount.jwt ?: "")
            .replace("\${user_type}", "msa")
            .replace("\${version_type}", "RDI")
            .replace("\${user_properties}", "{}")
    }.toMutableList()
    val (physicalWidth, physicalHeight) = resolvePhysicalScreenSize()
    gameArgs += listOf("--width", "$physicalWidth", "--height", "$physicalHeight")
    val resolvedJvmArgs = manifest.resolveJvmArgumentList() + loaderManifest.resolveJvmArgumentList()
    val launchClasspath = buildLaunchClasspath(
        manifest = manifest,
        loaderManifest = loaderManifest,
        versionDir = versionDir,
        versionId = versionId,
        gtnhExtensionRoot = gtnhExtensionRoot
    )
    val classpath = launchClasspath.joinToString(File.pathSeparator)
    val legacyLaunch = resolvedJvmArgs.isEmpty() &&
        (!manifest.minecraftArguments.isNullOrBlank() || !loaderManifest.minecraftArguments.isNullOrBlank())
    val hasClasspathDeclaration = !isGtnhJava25Launch && resolvedJvmArgs.any {
        it == "-cp" || it == "-classpath" || it.contains("\${classpath}")
    }
    val processedJvmArgs = buildList {
        var skipNextClasspathValue = false
        resolvedJvmArgs.forEach { rawArg ->
            val arg = rawArg.replaceLaunchTokens(nativesDir, versionDir, versionId, classpath)
            if (isGtnhJava25Launch) {
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
        if (gtnhExtensionRoot != null) {
            this += gtnhJava25JvmArgs(gtnhExtensionRoot, nativesDir, versionDir, versionId, classpath)
        }
        this += jvmArgs
        if(DEBUG){
            this +=  "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
        }
    }

    lgr.info { "JVM Args: ${processedJvmArgs.joinToString(" ")}" }
    lgr.info { "Game Args: ${gameArgs.joinToString(" ")}" }
    val jrePath = if (isGtnhJava25Launch) resolveJava25Path() else resolveDesktopJavaPath(mcVer)
    val mainClass = if (isGtnhJava25Launch) GTNH_RFB_MAIN_CLASS else loaderManifest.mainClass ?: error("缺少启动主类")
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

private fun GameService.buildLaunchClasspath(
    manifest: MojangVersionManifest,
    loaderManifest: MojangVersionManifest,
    versionDir: File,
    versionId: String,
    gtnhExtensionRoot: File? = null
): List<String> {
    val entries = LinkedHashSet<String>()
    if (gtnhExtensionRoot != null) {
        entries += buildGtnhJava25Classpath(gtnhExtensionRoot)
    }
    val baseLibraries = if (gtnhExtensionRoot != null) {
        manifest.libraries.filterNot(MojangLibrary::isLegacyLwjgl2Library)
    } else {
        manifest.libraries
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

private fun String.replaceLaunchTokens(
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

private fun requireGtnhExtensionRoot(versionDir: File): File {
    val root = versionDir.resolve("gtnh")
    val requiredFiles = listOf(
        root.resolve("patches/net.minecraftforge.json"),
        root.resolve("patches/org.lwjgl3.json"),
        root.resolve("patches/me.eigenraven.lwjgl3ify.forgepatches.json"),
        root.resolve("patches/me.eigenraven.lwjgl3ify.launchargs.json"),
        root.resolve("libraries/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION-forgePatches.jar"),
        root.resolve("libraries/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION.jar"),
    )
    val missing = requiredFiles.filterNot(File::isFile)
    if (missing.isNotEmpty()) {
        throw RequestError("整合包缺少GTNH扩展包，请确认${root.absolutePath}存在且完整，缺少: ${missing.joinToString(", ") { it.absolutePath }}")
    }
    return root
}

private fun ensureGtnhRuntimeMods(versionDir: File, extensionRoot: File) {
    val source = extensionRoot.resolve("libraries/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION.jar")
    val target = versionDir.resolve("mods/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION.jar")
    if (target.isFile) return
    if (!source.isFile) {
        throw RequestError("GTNH扩展包缺少运行时Mod: ${source.absolutePath}")
    }
    target.parentFile?.mkdirs()
    linkOrCopyMod(source, target)
}

private fun buildGtnhLoaderManifest(extensionRoot: File, versionId: String): MojangVersionManifest {
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
        libraries = forgePatch.libraries.map(MavenLibraryRef::toMojangLibrary),
        minecraftArguments = minecraftArguments,
    )
}

private fun buildGtnhJava25Classpath(extensionRoot: File): List<String> {
    val localLibraries = listOf(
        extensionRoot.resolve("libraries/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION-forgePatches.jar"),
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

private fun GameService.collectGtnhGlobalRuntimeDownloadPlans(extensionRoot: File): Result<List<GtnhRuntimeDownloadPlan>> = runCatching {
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

private fun readGtnhPatchLibraries(file: File): List<MavenLibraryRef> = runCatching {
    val patch = serdesJson.decodeFromString<GtnhPrismPatch>(file.readText())
    patch.libraries
}.getOrElse { e ->
    throw RequestError("GTNH Prism patch解析失败: ${file.absolutePath}: ${e.message}")
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

private fun MojangLibrary.isLegacyLwjgl2Library(): Boolean {
    val coords = name.split(':')
    if (coords.size < 2) return false
    return coords[0] == "org.lwjgl.lwjgl" && coords[1] in setOf("lwjgl", "lwjgl_util", "lwjgl-platform")
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

private fun gtnhJava25JvmArgs(
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
}

fun GameService.startServerDesktop(mcVer: McVersion, loaderVer: ModLoader.Version, workDir: File, onLine: (String) -> Unit): Process {
    val hostOs = LibraryOsArch.detectHostOs()
    val isGtnhJava25Launch = mcVer == McVersion.V071
    val jrePath = if (isGtnhJava25Launch) resolveJava25Path() else resolveDesktopJavaPath(mcVer)
    val command = mutableListOf(
        jrePath,
        "-Xmx6G",
        "-Xms6G",
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
                    linkServerRuntimeFile(workDir.resolve(jarFileName), ClientDirs.mcDir.resolve(jarFileName))
                }
            }

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

            McVersion.V071 -> {
                if (loaderVer.loader == ModLoader.forge) {
                    prepareGtnhServerRuntime(workDir, loaderVer)
                    this += "-Xbootclasspath/a:${gtnhServerBootClasspath().joinToString(File.pathSeparator)}"
                    this += "-Dfml.readTimeout=180"
                    this += "@java9args.txt"
                    this += "-jar"
                    this += GTNH_SERVER_FORGE_PATCHES_JAR
                }
            }
            else -> { throw RequestError("不支持的MC版本启动测试服务器")
            }
        }
        this += if (mcVer == McVersion.V071) "nogui" else "--nogui"
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

private fun resolveLegacyForgeServerJar(loaderVer: ModLoader.Version): File {
    val artifactVersion = loaderVer.dirName
        .removePrefix("${McVersion.V071.mcVer}-Forge")
        .takeIf { it != loaderVer.dirName && it.isNotBlank() }
        ?.let { "${McVersion.V071.mcVer}-$it" }
        ?: loaderVer.id
    val jarFileName = "forge-$artifactVersion-universal.jar"
    val candidates = listOf(
        ClientDirs.mcDir.resolve(jarFileName),
        ClientDirs.librariesDir.resolve("net/minecraftforge/forge/$artifactVersion/$jarFileName")
    )
    return candidates.firstOrNull(File::isFile)
        ?: throw RequestError("缺少1.7.10 Forge服务端文件，请先在MC资源页安装1.7.10")
}

private fun prepareGtnhServerRuntime(workDir: File, loaderVer: ModLoader.Version) {
    val extensionRoot = requireGtnhExtensionRoot(workDir)
    val forgeJar = resolveLegacyForgeServerJar(loaderVer)
    val minecraftServerJar = ClientDirs.mcDir.resolve(McVersion.V071.serverJarName)
    val serverForgePatches = extensionRoot.resolve("libraries/lwjgl3ify-$GTNH_LWJGL3IFY_VERSION-forgePatches.jar")
    val missing = (listOf(forgeJar, minecraftServerJar, serverForgePatches) + gtnhServerBootClasspath()).filterNot(File::isFile)
    if (missing.isNotEmpty()) {
        throw RequestError("GTNH服务端测试缺少运行文件: ${missing.joinToString(", ") { it.absolutePath }}")
    }
    linkServerRuntimeFile(workDir.resolve(GTNH_SERVER_FORGE_PATCHES_JAR), serverForgePatches)
    linkServerRuntimeFile(workDir.resolve(forgeJar.name), forgeJar)
    linkServerRuntimeFile(workDir.resolve(McVersion.V071.serverJarName), minecraftServerJar)
    writeGtnhServerJava9Args(workDir.resolve("java9args.txt"), extensionRoot)
}

private fun gtnhServerBootClasspath(): List<File> = listOf(
    ClientDirs.librariesDir.resolve("org/apache/logging/log4j/log4j-api/$GTNH_LOG4J_VERSION/log4j-api-$GTNH_LOG4J_VERSION.jar"),
    ClientDirs.librariesDir.resolve("org/apache/logging/log4j/log4j-core/$GTNH_LOG4J_VERSION/log4j-core-$GTNH_LOG4J_VERSION.jar"),
)

private fun writeGtnhServerJava9Args(file: File, extensionRoot: File) {
    val args = gtnhJava25JvmArgs(
        extensionRoot = extensionRoot,
        nativesDir = McVersion.V071.nativesDir,
        versionDir = file.parentFile,
        versionId = file.parentFile?.name ?: "gtnh-server-test",
        classpath = "",
    )
    file.writeText(args.joinToString(System.lineSeparator()))
}

private fun resolveLocalLoaderManifest(versionDir: File, versionId: String): MojangVersionManifest? {
    val localManifest = versionDir.resolve("${versionId}.json")
    if (!localManifest.isFile) return null
    return runCatching {
        serdesJson.decodeFromString<MojangVersionManifest>(localManifest.readText())
    }.getOrNull()
}

private fun linkServerRuntimeFile(link: File, source: File) {
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

private fun resolveJava25Path(): String {
    CONF.jre25Path?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return javaExePath.takeIf { Runtime.version().feature() == 25 }
        ?: throw RequestError("GTNH Java25版需要在设置中配置Java25路径")
}
