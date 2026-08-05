package calebxzau.rdi.mclaunch

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.LibraryOsArch
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzau.rdi.mclaunch.model.MojangLibrary
import calebxzau.rdi.mclaunch.model.MojangVersionManifest
import calebxzhou.mykotutils.log.Loggers
import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.LinkedHashSet
import kotlin.concurrent.thread

private val utf8LoggingJvmArgs = listOf(
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
)

private data class LaunchManifests(
    val manifest: MojangVersionManifest,
    val loaderManifest: MojangVersionManifest,
    val gtnhExtensionRoot: File?,
    val baseLibraries: List<MojangLibrary>,
)

class MinecraftLauncher(
    private val environment: MinecraftLaunchEnvironment,
) {
    private val lgr by Loggers
    private val directories = environment.directories
    private val gtnh = GtnhLaunchSupport(
        directories = directories,
        launcherBrand = environment.launcherBrand,
        launcherVersion = environment.launcherVersion,
    )
    private val libraryPreparer = MinecraftLaunchLibraryPreparer(
        librariesDir = directories.librariesDir,
        downloader = environment.artifactDownloader,
    )

    suspend fun prepare(
        request: MinecraftLaunchRequest,
        onProgress: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        val sourceManifests = loadManifests(request)
        val launchManifests = resolveLaunchManifests(request, sourceManifests)
        launchManifests.gtnhExtensionRoot?.let {
            gtnh.ensureRuntime(request.versionDir, sourceManifests.manifest, environment.artifactDownloader, onProgress)
                .getOrThrow()
        }
        libraryPreparer.ensure(
            baseLibraries = launchManifests.baseLibraries,
            overrideLibraries = launchManifests.loaderManifest.libraries,
            onProgress = onProgress,
        ).getOrThrow()
    }

    fun launch(
        request: MinecraftLaunchRequest,
        onLine: (String) -> Unit,
    ): Result<Process> = runCatching {
        val sourceManifests = loadManifests(request)
        val launchManifests = resolveLaunchManifests(request, sourceManifests)
        launchManifests.gtnhExtensionRoot?.let { gtnh.ensureRuntimeMods(request.versionDir, it) }

        val manifest = launchManifests.manifest
        val loaderManifest = launchManifests.loaderManifest
        val launchLibraryIssues = libraryPreparer.validate(
            baseLibraries = launchManifests.baseLibraries,
            overrideLibraries = loaderManifest.libraries,
        )
        check(launchLibraryIssues.isEmpty()) {
            launchLibraryIssues.joinToString("; ") { it.summary }
        }

        val gtnhLaunch = launchManifests.gtnhExtensionRoot != null
        val nativesDir = if (gtnhLaunch) {
            request.mcVersion.nativesDir(directories)
        } else {
            request.versionDir.resolve("natives").takeIf(File::exists)
                ?: request.mcVersion.nativesDir(directories)
        }
        val gameArgs = manifest.resolveLaunchGameArguments(loaderManifest)
            .map { argument ->
                argument
                    .replace("\${auth_player_name}", request.account.name)
                    .replace("\${version_name}", request.versionId)
                    .replace("\${game_directory}", request.versionDir.absolutePath)
                    .replace("\${assets_root}", directories.assetsDir.absolutePath)
                    .replace("\${assets_index_name}", manifest.assets ?: manifest.assetIndex?.id ?: manifest.id)
                    .replace("\${auth_uuid}", request.account.uuid.replace("-", ""))
                    .replace("\${auth_access_token}", request.account.accessToken)
                    .replace("\${user_type}", "msa")
                    .replace("\${version_type}", "RDI")
                    .replace("\${user_properties}", "{}")
            }
            .toMutableList()
            .apply {
                add("--width")
                add(request.windowSize.width.toString())
                add("--height")
                add(request.windowSize.height.toString())
            }

        val resolvedJvmArgs = manifest.resolveJvmArgumentList() + loaderManifest.resolveJvmArgumentList()
        val kotlinClasspath = if (request.mcVersion == McVersion.V201 || request.mcVersion == McVersion.V211) {
            GameKotlinRuntime.prepare(
                mcVersion = request.mcVersion,
                modsDir = request.versionDir.resolve("mods"),
                cacheRoot = directories.toolsDir.resolve("kotlin-runtime"),
            ).getOrElse { error ->
                lgr.error(error) { "Minecraft Kotlin运行库不可用" }
                throw RequestError("Kotlin运行库冲突，无法启动游戏", error)
            }
        } else {
            emptyList()
        }
        val mediaRuntime = if (request.mcVersion == McVersion.V201 || request.mcVersion == McVersion.V211) {
            MediaProcGameClasspath.resolve(
                nativeRoot = directories.toolsDir.resolve("mediaproc-natives")
            ).getOrElse { error ->
                lgr.error(error) { "Minecraft媒体模块不可用" }
                throw RequestError("媒体模块缺失或损坏，请先修复", error)
            }
        } else {
            null
        }
        val launchClasspath = buildLaunchClasspath(
            manifest = manifest,
            loaderManifest = loaderManifest,
            versionDir = request.versionDir,
            versionId = request.versionId,
            baseLibraries = launchManifests.baseLibraries,
            gtnhExtensionRoot = launchManifests.gtnhExtensionRoot,
            additionalClasspath = kotlinClasspath + mediaRuntime?.classpath.orEmpty(),
        )
        val classpath = launchClasspath.joinToString(File.pathSeparator)
        val legacyLaunch = resolvedJvmArgs.isEmpty() &&
            (!manifest.minecraftArguments.isNullOrBlank() || !loaderManifest.minecraftArguments.isNullOrBlank())
        val hasClasspathDeclaration = !gtnhLaunch && resolvedJvmArgs.any {
            it == "-cp" || it == "-classpath" || it.contains("\${classpath}")
        }
        val jvmVersionName = if (loaderManifest.isBootstrapModuleLaunch()) {
            loaderManifest.jar?.takeIf(String::isNotBlank)
                ?: loaderManifest.inheritsFrom?.takeIf(String::isNotBlank)
                ?: manifest.id
        } else {
            request.versionId
        }
        val processedJvmArgs = buildList {
            var skipNextClasspathValue = false
            resolvedJvmArgs.forEach { rawArg ->
                val argument = rawArg.replaceLaunchTokens(
                    nativesDir = nativesDir,
                    versionDir = request.versionDir,
                    librariesDir = directories.librariesDir,
                    launcherBrand = environment.launcherBrand,
                    launcherVersion = environment.launcherVersion,
                    versionId = jvmVersionName,
                    classpath = classpath,
                )
                if (gtnhLaunch) {
                    if (skipNextClasspathValue) {
                        skipNextClasspathValue = false
                        return@forEach
                    }
                    if (argument == "-cp" || argument == "-classpath") {
                        skipNextClasspathValue = true
                        return@forEach
                    }
                }
                add(argument)
            }
        }.toMutableList()
        if (legacyLaunch) {
            processedJvmArgs += listOf(
                "-Djava.library.path=${nativesDir.absolutePath}",
                "-Dminecraft.launcher.brand=${environment.launcherBrand}",
                "-Dminecraft.launcher.version=${environment.launcherVersion}",
            )
        }
        if (launchClasspath.isNotEmpty() && !hasClasspathDeclaration) {
            processedJvmArgs += listOf("-cp", classpath)
        }
        processedJvmArgs += resolveMaxMemory()
        processedJvmArgs += utf8LoggingJvmArgs
        mediaRuntime?.let {
            processedJvmArgs += "-Dorg.bytedeco.javacpp.pathsFirst=true"
            processedJvmArgs += "-Dorg.bytedeco.javacpp.platform.preloadpath=${it.nativeLibraryDir.absolutePath}"
        }
        launchManifests.gtnhExtensionRoot?.let {
            processedJvmArgs += gtnh.java25JvmArgs(
                extensionRoot = it,
                nativesDir = nativesDir,
                versionDir = request.versionDir,
                versionId = request.versionId,
                classpath = classpath,
            )
        }
        processedJvmArgs += request.extraJvmArgs
        if (environment.debug) processedJvmArgs += "-Djavax.net.ssl.trustStoreType=Windows-ROOT"

        val mainClass = loaderManifest.mainClass ?: error("缺少启动主类")
        val command = buildList {
            add(resolveJava25Path())
            addAll(processedJvmArgs)
            add(mainClass)
            addAll(gameArgs)
        }
        lgr.info { "JVM Args: ${processedJvmArgs.joinToString(" ")}" }
        lgr.info { "Game Args: ${gameArgs.joinToString(" ")}" }
        lgr.info { "Launch Command: ${command.joinToString(" ")}" }
        val process = environment.processStarter.start(command, request.versionDir).getOrThrow()
        readProcessOutput(process, onLine, "mc-log-reader")
        process
    }

    fun launchServer(
        mcVersion: McVersion,
        loaderVersion: ModLoader.Version,
        workDir: File,
        onLine: (String) -> Unit,
    ): Result<Process> = runCatching {
        if (mcVersion == McVersion.V071) throw RequestError("不支持GTNH本地测试服务器")
        val hostOs = LibraryOsArch.detectHostOs()
        val command = buildList {
            add(resolveJava25Path())
            add("-Xmx6G")
            add("-Xms6G")
            addAll(utf8LoggingJvmArgs)
            when (mcVersion) {
                McVersion.V201, McVersion.V211 -> {
                    add(loaderVersion.serverArgsPath(hostOs.isUnixLike))
                    add("%*")
                }

                McVersion.V122 -> {
                    if (loaderVersion.loader == ModLoader.forge || loaderVersion.loader == ModLoader.cleanroom) {
                        addAll(mcVersion.plusJvmArgs)
                        val jarFileName = "cleanroom-${loaderVersion.id}.jar"
                        add("-jar")
                        add(jarFileName)
                        linkFile(workDir.resolve(jarFileName), directories.mcDir.resolve(jarFileName)).getOrThrow()
                        val minecraftServerJar = "minecraft_server.1.12.2.jar"
                        linkFile(workDir.resolve(minecraftServerJar), directories.mcDir.resolve(minecraftServerJar)).getOrThrow()
                    }
                }

                else -> throw RequestError("不支持的MC版本启动测试服务器")
            }
            add("--nogui")
        }
        workDir.resolve("eula.txt").writeText("eula=true")
        val process = environment.processStarter.start(command, workDir).getOrThrow()
        readProcessOutput(process, onLine, "mc-server-log-reader")
        process
    }

    private fun loadManifests(request: MinecraftLaunchRequest): MinecraftManifestPair =
        environment.manifestProvider.load(request.mcVersion, request.versionId, request.versionDir).getOrThrow()

    private fun resolveLaunchManifests(
        request: MinecraftLaunchRequest,
        source: MinecraftManifestPair,
    ): LaunchManifests {
        val gtnhExtensionRoot = if (request.mcVersion == McVersion.V071) {
            gtnh.requireExtensionRoot(request.versionDir)
        } else {
            null
        }
        val loaderManifest = if (gtnhExtensionRoot != null) {
            gtnh.buildLoaderManifest(gtnhExtensionRoot, request.versionId)
        } else {
            source.loaderManifest.copy(id = request.versionId)
        }
        val baseLibraries = if (gtnhExtensionRoot != null) {
            source.manifest.libraries.filterNot(MojangLibrary::isLegacyLwjgl2Library)
        } else {
            source.manifest.libraries
        }
        return LaunchManifests(source.manifest, loaderManifest, gtnhExtensionRoot, baseLibraries)
    }

    private fun buildLaunchClasspath(
        manifest: MojangVersionManifest,
        loaderManifest: MojangVersionManifest,
        versionDir: File,
        versionId: String,
        baseLibraries: List<MojangLibrary>,
        gtnhExtensionRoot: File?,
        additionalClasspath: List<File>,
    ): List<String> {
        val entries = LinkedHashSet<String>()
        additionalClasspath.forEach { entries += it.absolutePath }
        if (gtnhExtensionRoot != null) entries += gtnh.buildJava25Classpath(gtnhExtensionRoot)
        entries += buildMinecraftClasspath(baseLibraries, loaderManifest.libraries, directories.librariesDir)
        val versionJarCandidates = resolveLaunchVersionJarCandidates(
            manifest = manifest,
            loaderManifest = loaderManifest,
            versionDir = versionDir,
            versionsDir = directories.versionsDir,
            versionId = versionId,
        )
        val versionJars = versionJarCandidates.filter(File::exists)
        if (loaderManifest.isBootstrapModuleLaunch()) {
            val minecraftVersionName = loaderManifest.jar?.takeIf(String::isNotBlank)
                ?: loaderManifest.inheritsFrom?.takeIf(String::isNotBlank)
                ?: manifest.jar?.takeIf(String::isNotBlank)
                ?: manifest.id
            val minecraftJar = listOf(
                versionDir.resolve("$minecraftVersionName.jar"),
                directories.versionsDir.resolve(minecraftVersionName).resolve("$minecraftVersionName.jar"),
            ).firstOrNull(File::exists)
            check(minecraftJar != null) {
                "缺少Minecraft客户端JAR: $minecraftVersionName"
            }
        }
        versionJars.forEach { entries += it.absolutePath }
        return entries.toList()
    }

    private fun resolveJava25Path(): String {
        environment.java.configuredJava25Path
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }
        if (environment.java.currentJavaMajor == 25) return environment.java.currentJavaPath
        throw RequestError("请前往设置Java25路径")
    }

    private fun resolveMaxMemory(): String = if (environment.java.maxMemoryMb > 0) {
        "-Xmx${environment.java.maxMemoryMb}M"
    } else {
        runCatching {
            val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
            val freeBytes = osBean?.freeMemorySize ?: return@runCatching "-Xmx8G"
            val freeMb = freeBytes / (1024L * 1024)
            lgr.info { "剩余内存${freeBytes.humanFileSize}" }
            "-Xmx${if (freeMb > 8192) freeMb else 8192}M"
        }.getOrDefault("-Xmx8G")
    }

    private fun readProcessOutput(process: Process, onLine: (String) -> Unit, threadName: String) {
        thread(name = threadName, isDaemon = true) {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line -> if (line.isNotBlank()) onLine(line) }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onLine("MC已结束，退出代码: $exitCode")
            } else {
                onLine("已退出")
            }
        }
    }

    private fun McVersion.nativesDir(directories: MinecraftDirectories): File =
        directories.versionsDir.resolve(mcVer).resolve("natives")

    private fun linkFile(source: File, target: File): Result<Unit> = runCatching {
        check(source.isFile) { "源文件不存在: ${source.absolutePath}" }
        target.parentFile?.mkdirs()
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        if (targetPath.toFile().exists() && Files.isSameFile(sourcePath, targetPath)) return@runCatching
        Files.deleteIfExists(targetPath)
        Files.createLink(targetPath, sourcePath)
    }
}
