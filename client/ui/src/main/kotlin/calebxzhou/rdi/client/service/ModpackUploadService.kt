package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.*
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.client.modcatalog.CatalogSlugRef
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.extractArchiveToDir
import calebxzhou.rdi.common.archive.listArchiveEntries
import calebxzhou.rdi.common.deser
import calebxzhou.rdi.common.exception.ModpackError
import calebxzhou.rdi.common.isExcludedConfigPath
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.CurseForgeService.loadInfoCurseForge
import calebxzhou.rdi.common.service.ModpackModProcessor
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.ModService.readModMeta
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.service.ModrinthService.mapModrinthVersions
import calebxzhou.rdi.common.service.ModrinthService.toCardVo
import calebxzhou.rdi.common.service.runInline
import calebxzhou.rdi.common.util.ok
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.buffered
import org.bson.types.ObjectId
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val lgr by Loggers

// ==================== Upload-only code ====================

data class UploadPayload(
    val sourceType: LocalModpackSourceType,
    val sourceDir: File,
    var mods: MutableList<Mod>,
    val mcVersion: McVersion,
    val modloader: ModLoader,
    val sourceName: String,
    val sourceVersion: String,
    var embeddedModOriginalFileNames: Map<String, String> = emptyMap(),
    val serverExtraFiles: List<ServerExtraFile> = emptyList()
)

data class LoadedServerPackResult(
    val mods: List<Mod>,
    val serverExtraFiles: List<ServerExtraFile>
)

suspend fun loadLocalModpack(
    modCatalog: ModCatalog,
    file: File,
    onProgress: LoadProgressConsumer
): Result<LoadedLocalModpack> = withContext(Dispatchers.IO) {
    var payload: UploadPayload? = null
    runCatching {
        onProgress.phase("开始读取整合包")
        payload = parseUploadPayload(file, onProgress).getOrThrow()
        val parsedPayload = payload
        onProgress.phase(
            buildString {
                append("整合包概要读取完成 ")
                append(parsedPayload.sourceName.ifBlank { "未命名整合包" })
                append(" ")
                append(parsedPayload.sourceVersion.ifBlank { "1.0" })
            }
        )
        onProgress.phase(
            buildString {
                append("开始整理Mod列表 MC")
                append(parsedPayload.mcVersion.mcVer)
                append(" ")
                append(parsedPayload.modloader.name)
            }
        )
        val mods = loadUploadPayloadMods(
            modCatalog = modCatalog,
            payload = parsedPayload,
            onProgress = onProgress
        ).getOrThrow()
        onProgress.phase("Mod列表整理完成，共${mods.size}个，准备进入编辑")
        //forge包可以视作cleanroom试运行
        val loader = if (parsedPayload.modloader == ModLoader.forge && parsedPayload.mcVersion == McVersion.V122) {
            ModLoader.cleanroom
        } else parsedPayload.modloader
        LoadedLocalModpack(
            sourceType = parsedPayload.sourceType,
            sourceDir = parsedPayload.sourceDir,
            packName = parsedPayload.sourceName,
            packVersion = parsedPayload.sourceVersion,
            mcVersion = parsedPayload.mcVersion,
            modloader = loader,
            mods = mods,
            embeddedModOriginalFileNames = parsedPayload.embeddedModOriginalFileNames,
            serverExtraFiles = parsedPayload.serverExtraFiles
        )
    }.fold(
        onSuccess = ::ok,
        onFailure = { error ->
            payload?.sourceDir?.let { runCatching { it.deleteRecursivelyNoSymlink() } }
            Result.failure(error)
        }
    )
}

fun LoadedLocalModpack.toUploadPayload(): UploadPayload = UploadPayload(
    sourceType = sourceType,
    sourceDir = sourceDir,
    mods = mods.toMutableList(),
    mcVersion = mcVersion,
    modloader = modloader,
    sourceName = packName,
    sourceVersion = packVersion,
    embeddedModOriginalFileNames = embeddedModOriginalFileNames,
    serverExtraFiles = serverExtraFiles
)

fun UploadPayload.toLoadedLocalModpack(): LoadedLocalModpack = LoadedLocalModpack(
    sourceType = sourceType,
    sourceDir = sourceDir,
    packName = sourceName,
    packVersion = sourceVersion,
    mcVersion = mcVersion,
    modloader = modloader,
    mods = mods.toList(),
    embeddedModOriginalFileNames = embeddedModOriginalFileNames,
    serverExtraFiles = serverExtraFiles
)

fun parseUploadPayload(
    file: File,
    onProgress: (LoadProgress) -> Unit
): Result<UploadPayload> {
    var prepared: PreparedModpack? = null
    return runCatching {
        onProgress(LoadProgress.Phase("准备解析整合包概要"))
        onProgress(LoadProgress.Phase("快速检查整合包结构"))
        val preflight = preflightPackStructure(file)
        prepared = runCatching {
            prepareModpackSource(file, onProgress)
        }.getOrElse { e ->
            throw ModpackError("读取整合包文件失败", e)
        }
        val preparedPack = prepared
        onProgress(LoadProgress.Phase("整合包结构检查完成"))
        when (preflight.packType) {
            PackType.MODRINTH -> {
                onProgress(LoadProgress.Phase("读取Modrinth概要文件"))
                inspectModrinthUploadPayload(preparedPack.rootDir)
            }

            PackType.CURSEFORGE -> {
                onProgress(LoadProgress.Phase("读取CurseForge概要文件"))
                inspectCurseForgeUploadPayload(preparedPack.rootDir)
            }

            PackType.UNKNOWN -> throw ModpackError("找不到此包的概要文件(manifest.json/modrinth.index.json)")
        }
    }.fold(
        onSuccess = ::ok,
        onFailure = { e ->
            prepared?.rootDir?.let { runCatching { it.deleteRecursivelyNoSymlink() } }
            Result.failure(e)
        }
    )
}

suspend fun loadUploadPayloadMods(
    modCatalog: ModCatalog,
    payload: UploadPayload,
    onProgress: (LoadProgress) -> Unit
): Result<MutableList<Mod>> {
    val sourceDir = payload.sourceDir
    onProgress(LoadProgress.Phase("扫描包内内置mods"))
    val embeddedMods = collectEmbeddedModFiles(sourceDir)
    if (embeddedMods.isEmpty()) {
        onProgress(LoadProgress.Phase("未发现包内内置mods"))
    } else {
        onProgress(LoadProgress.Phase("发现包内内置mods${embeddedMods.size}个"))
    }
    val embeddedMatches = matchLocalModFiles(embeddedMods, onProgress)
    if (embeddedMatches.mods.isNotEmpty()) {
        onProgress(LoadProgress.Phase("已识别内置mods${embeddedMatches.mods.size}个"))
    }
    payload.embeddedModOriginalFileNames = embeddedMatches.mods.mapNotNull { uiMod ->
        val originalName = uiMod.file?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        uiMod.mod.fileName to originalName
    }.toMap()
    persistMatchedEmbeddedMods(embeddedMatches.mods)
    val resolvedMods = when (payload.sourceType) {
        LocalModpackSourceType.MODRINTH -> {
            onProgress(LoadProgress.Phase("解析Modrinth整合包索引"))
            val loaded = ModrinthService.loadModpack(sourceDir) { curseForgeSlugs ->
                val refs = curseForgeSlugs.map { CatalogSlugRef(ModPlatform.CURSEFORGE, it) }.toSet()
                val metadata = modCatalog.getMetadataOrEmpty(refs)
                curseForgeSlugs.mapNotNull { slug ->
                    val local = metadata[CatalogSlugRef(ModPlatform.CURSEFORGE, slug)] ?: return@mapNotNull null
                    local.project(ModPlatform.MODRINTH)?.slug?.let { slug to it }
                }.toMap()
            }.getOrThrow()
            (loaded.mods + embeddedMatches.mods.map { it.withFile(null).toMod() })
                .distinctBy { "${it.platform}:${it.projectId}:${it.fileId}:${it.hash}" }
                .toMutableList()
        }

        LocalModpackSourceType.CURSEFORGE -> {
            onProgress(LoadProgress.Phase("解析CurseForge整合包清单"))
            val modpackData = loadCurseForgeFromDir(sourceDir)
            val baseMods = CurseForgeService.mapManifestEntriesToMods(modpackData.manifest.files)
            (baseMods + embeddedMatches.mods.map { it.withFile(null).toMod() })
                .distinctBy { "${it.platform}:${it.projectId}:${it.fileId}:${it.hash}" }
                .toMutableList()
        }
    }
    if (embeddedMatches.matchedFiles.isNotEmpty()) {
        embeddedMatches.matchedFiles.forEach { it.delete() }
    }
    onProgress(LoadProgress.Phase("整理Mod单双端属性"))
    ModService.run { resolvedMods.postProcessModSides() }
    payload.mods = resolvedMods
    return ok(resolvedMods)
}

suspend fun loadServerPackMods(
    file: File,
    clientMods: List<Mod>,
    onProgress: LoadProgressConsumer
): Result<LoadedServerPackResult> = withContext(Dispatchers.IO) {
    runCatching {
        onProgress.phase("开始读取服务端目录")
        if (!file.exists() || !file.isDirectory) {
            throw ModpackError("请选择服务端根目录")
        }
        val modsDir = file.resolve("mods")
        if (!modsDir.exists() || !modsDir.isDirectory) {
            throw ModpackError("请选择服务端根目录，目录下应有mods文件夹")
        }
        val modFiles = collectServerPackModFiles(file)
        if (modFiles.isEmpty()) {
            throw ModpackError("请选择服务端根目录，目录下应有mods文件夹")
        }
        val clientModsByModId = buildClientModsByModId(clientMods)
        val matchedByModId = matchServerModsByClientModId(
            files = modFiles,
            clientModsByModId = clientModsByModId,
            onProgress = onProgress
        )
        val matched = matchLocalModFiles(matchedByModId.unmatchedFiles, onProgress)
        val finalMatchedMods = (matched.mods + matchedByModId.mods)
            .distinctBy(::serverModMergeKey)
        val matchedFiles = matched.matchedFiles + matchedByModId.matchedFiles
        val finalUnmatchedFiles = matched.unmatchedFiles
        persistMatchedEmbeddedMods(finalMatchedMods)
        if (finalUnmatchedFiles.isNotEmpty()) {
            val preview = finalUnmatchedFiles.take(5).joinToString("、") { it.nameWithoutExtension }
            val suffix = if (finalUnmatchedFiles.size > 5) "等${finalUnmatchedFiles.size}个" else ""
            onProgress.warn("服务端目录中有${finalUnmatchedFiles.size}个mod未识别，将作为额外服务端文件带上：$preview$suffix")
        }
        val resolvedMods = finalMatchedMods.map { it.withFile(null).toMod() }.toMutableList().also {
            ModService.run { it.postProcessModSides() }
        }
        val serverExtraFiles = collectServerPackExtraFiles(
            rootDir = file,
            matchedModFiles = matchedFiles
        )
        LoadedServerPackResult(
            mods = resolvedMods,
            serverExtraFiles = serverExtraFiles
        )
    }.fold(
        onSuccess = ::ok,
        onFailure = { error -> Result.failure(error) }
    )
}

private data class EmbeddedMatchResult(
    val mods: List<UiMod>,
    val matchedFiles: Set<File>,
    val unmatchedFiles: List<File>
)

private data class EmbeddedMergedResult(
    val mods: List<UiMod>,
    val matchedFiles: Set<File>,
    val unmatchedFiles: List<File>
)

private suspend fun matchEmbeddedModsCF(
    files: List<File>
): EmbeddedMatchResult {
    if (files.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet(), emptyList())
    val result = files.loadInfoCurseForge()
    if (result.matched.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet(), files)
    val matchedFiles = result.matched.mapNotNull { it.file }.toSet()
    val matched = result.matched.map { it.toUiMod() }
    return EmbeddedMatchResult(matched, matchedFiles, result.unmatched)
}

private suspend fun matchEmbeddedModsMR(
    files: List<File>
): EmbeddedMatchResult {
    if (files.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet(), emptyList())
    val hashToVersion = files.mapModrinthVersions()
    if (hashToVersion.isEmpty()) return EmbeddedMatchResult(emptyList(), emptySet(), files)
    val projectIds = hashToVersion.values.map { it.projectId }.distinct()
    val projectMap = ModrinthService.getMultipleProjects(projectIds).associateBy { it.id }
    val matched = mutableListOf<UiMod>()
    val matchedFiles = mutableSetOf<File>()
    files.forEach { file ->
        val sha1 = file.sha1
        val version = hashToVersion[sha1] ?: return@forEach
        val project = projectMap[version.projectId]
        val slug = project?.slug?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension.ifBlank { version.projectId }
        val side = project?.run {
            if (serverSide == "unsupported") {
                return@run Mod.Side.CLIENT
            }
            if (clientSide == "unsupported") {
                return@run Mod.Side.SERVER
            }
            Mod.Side.BOTH
        } ?: Mod.Side.BOTH
        val fileInfo = version.files.firstOrNull { it.hashes["sha1"] == sha1 }
        val downloadUrls = fileInfo?.url?.let { listOf(it) } ?: emptyList()
        val rawMod = Mod(
            platform = "mr",
            projectId = version.projectId,
            slug = slug,
            fileId = version.id,
            hash = sha1,
            side = side,
            downloadUrls = downloadUrls
        )
        val mod = UiMod(
            mod = rawMod,
            card = project?.toCardVo(file)?.copy(side = side),
            file = file
        )
        matched += mod
        matchedFiles += file
    }
    val unmatchedFiles = files.filterNot { it in matchedFiles }
    return EmbeddedMatchResult(matched, matchedFiles, unmatchedFiles)
}

private fun persistMatchedEmbeddedMods(mods: List<UiMod>) {
    if (mods.isEmpty()) return
    DL_MOD_DIR.mkdirs()
    mods.forEach { uiMod ->
        val sourceFile = uiMod.file ?: return@forEach
        if (!sourceFile.exists() || !sourceFile.isFile) return@forEach
        val targetFile = DL_MOD_DIR.resolve(uiMod.mod.fileName)
        if (targetFile.absolutePath == sourceFile.absolutePath) return@forEach
        runCatching {
            sourceFile.copyTo(targetFile, overwrite = true)
        }.onFailure { err ->
            lgr.warn { "复制内置mod到下载目录失败: ${sourceFile.absolutePath} -> ${targetFile.absolutePath}\n$err" }
        }
    }
}

private suspend fun matchLocalModFiles(
    files: List<File>,
    onProgress: (LoadProgress) -> Unit
): EmbeddedMergedResult {
    if (files.isEmpty()) return EmbeddedMergedResult(emptyList(), emptySet(), emptyList())
    onProgress.phase("发现服务端目录中的mod${files.size}个，上网搜索信息中")
    val mrResult = runCatching {
        matchEmbeddedModsMR(files)
    }.getOrElse { e ->
        throw ModpackError("Modrinth匹配内置mod失败", e)
    }
    val remaining = mrResult.unmatchedFiles
    onProgress.phase("Modrinth找到${mrResult.mods.size}个 开始搜索CurseForge")
    val cfResult = runCatching {
        matchEmbeddedModsCF(remaining)
    }.getOrElse { e ->
        throw ModpackError("CurseForge匹配内置mod失败", e)
    }
    onProgress.phase("匹配完成：MR${mrResult.mods.size}个，CF${cfResult.mods.size} 个，处理结果中，请等一分钟...")
    val mergedMods = (mrResult.mods + cfResult.mods)
        .distinctBy(::serverModMergeKey)
    val matchedFiles = mrResult.matchedFiles + cfResult.matchedFiles
    return EmbeddedMergedResult(mergedMods, matchedFiles, cfResult.unmatchedFiles)
}

private fun buildClientModsByModId(clientMods: List<Mod>): Map<String, Mod> {
    if (clientMods.isEmpty()) return emptyMap()
    val clientModsByModId = linkedMapOf<String, Mod>()
    clientMods.forEach { clientMod ->
        val clientFile = clientMod.targetPath.toFile().takeIf { it.exists() && it.isFile } ?: return@forEach
        val modId = readPrimaryModId(clientFile) ?: return@forEach
        val previous = clientModsByModId.putIfAbsent(modId, clientMod)
        if (previous != null && previous != clientMod) {
            lgr.warn { "多个客户端mod共用了同一个modId=$modId，将保留第一个${previous.slug}，忽略${clientMod.slug}" }
        }
    }
    return clientModsByModId
}

private fun matchServerModsByClientModId(
    files: List<File>,
    clientModsByModId: Map<String, Mod>,
    onProgress: LoadProgressConsumer
): EmbeddedMatchResult {
    if (files.isEmpty() || clientModsByModId.isEmpty()) {
        return EmbeddedMatchResult(emptyList(), emptySet(), files)
    }
    onProgress.phase("平台未识别的服务端mod，按modId与客户端已下载mod对比")
    val matchedMods = mutableListOf<UiMod>()
    val matchedFiles = mutableSetOf<File>()
    val ignoredFiles = mutableSetOf<File>()
    val usedServerModIds = mutableSetOf<String>()
    files.forEach { serverFile ->
        val serverModId = readPrimaryModId(serverFile) ?: return@forEach
        if (!usedServerModIds.add(serverModId)) {
            lgr.warn { "服务端目录里有多个jar共用了同一个modId=$serverModId，将保留第一个并忽略后续文件" }
            ignoredFiles += serverFile
            matchedFiles += serverFile
            return@forEach
        }
        val clientMod = clientModsByModId[serverModId] ?: return@forEach
        matchedMods += clientMod.toUiMod()
            .withSide(Mod.Side.BOTH)
            .withFile(serverFile)
        matchedFiles += serverFile
    }
    return EmbeddedMatchResult(
        mods = matchedMods,
        matchedFiles = matchedFiles,
        unmatchedFiles = files.filterNot { it in matchedFiles || it in ignoredFiles }
    )
}

private fun readPrimaryModId(file: File): String? = readPrimaryModConfig(file)?.modId

private fun serverModMergeKey(uiMod: UiMod): String {
    val fileModId = uiMod.file?.let(::readPrimaryModId)
    if (!fileModId.isNullOrBlank()) return "modid:$fileModId"
    if (uiMod.mod.slug.isNotBlank()) return "slug:${uiMod.mod.slug.trim().lowercase()}"
    if (uiMod.mod.projectId.isNotBlank()) return "project:${uiMod.mod.projectId.trim()}"
    return "${uiMod.mod.platform}:${uiMod.mod.projectId}:${uiMod.mod.fileId}:${uiMod.mod.hash}"
}

private data class PreparedModpack(
    val rootDir: File,
    val name: String
)

private data class PackStructurePreflight(
    val packType: PackType
)

private data class AssetProcessInput(
    val relativePath: String,
    val relativeLower: String,
    val rawBytes: ByteArray
)

private fun formatPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"

private fun displayProgressFileName(path: String): String =
    path.substringAfterLast('/').ifBlank { path }

private fun preflightPackStructure(input: File): PackStructurePreflight {
    return runCatching {
        if (!input.exists()) {
            throw ModpackError("找不到整合包文件: ${input.path}")
        }
        val archiveEntryNames = if (input.isDirectory) {
            null
        } else {
            listArchiveEntries(input).map { it.path }
        }
        val packType = archiveEntryNames?.let(::detectPackType) ?: detectPackType(input)
        if (packType == PackType.UNKNOWN) {
            throw ModpackError("找不到此包的概要文件(manifest.json/modrinth.index.json)")
        }
        val hasOverrides = archiveEntryNames?.let { hasOverridesDir(it, packType) } ?: hasOverridesDir(input)
        if (!hasOverrides) {
            throw ModpackError("找不到包中overrides目录")
        }
        PackStructurePreflight(packType)
    }.getOrElse { error ->
        if (error is ModpackError) throw error
        throw ModpackError("读取整合包文件失败", error)
    }
}

private fun prepareModpackSource(input: File, onProgress: (LoadProgress) -> Unit): PreparedModpack {
    val tempDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "pack-").toFile()
    if (input.isDirectory) {
        onProgress(LoadProgress.Phase("正在复制整合包目录"))
        input.copyRecursively(tempDir, overwrite = true)
        onProgress(LoadProgress.Percent("整合包目录复制完成", 1f))
        return PreparedModpack(tempDir, input.name)
    }
    onProgress(LoadProgress.Phase("正在解压整合包"))
    extractArchiveToDir(input, tempDir) { done, total, _ ->
        val fraction = done.toFloat() / total.coerceAtLeast(1)
        onProgress(LoadProgress.Percent("正在解压整合包(${done}/$total)", fraction))
    }
    onProgress(LoadProgress.Percent("整合包解压完成", 1f))
    return PreparedModpack(tempDir, input.nameWithoutExtension)
}

private fun detectPackType(rootDir: File): PackType {
    var hasMrIndex = false
    var hasCfManifest = false
    rootDir.walkTopDown().forEach { file ->
        if (!file.isFile) return@forEach
        when (file.name) {
            "modrinth.index.json" -> hasMrIndex = true
            "manifest.json" -> hasCfManifest = true
        }
        if (hasMrIndex || hasCfManifest) return@forEach
    }
    return when {
        hasMrIndex -> PackType.MODRINTH
        hasCfManifest -> PackType.CURSEFORGE
        else -> PackType.UNKNOWN
    }
}

private fun detectPackType(entryNames: List<String>): PackType {
    var hasMrIndex = false
    var hasCfManifest = false
    entryNames.forEach { entryName ->
        when (entryName.replace('\\', '/').trimStart('/').substringAfterLast('/')) {
            "modrinth.index.json" -> hasMrIndex = true
            "manifest.json" -> hasCfManifest = true
        }
        if (hasMrIndex || hasCfManifest) return@forEach
    }
    return when {
        hasMrIndex -> PackType.MODRINTH
        hasCfManifest -> PackType.CURSEFORGE
        else -> PackType.UNKNOWN
    }
}

private fun findFile(rootDir: File, fileName: String): File? {
    return rootDir.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
}

private fun loadCurseForgeFromDir(rootDir: File): CurseForgeModpackData {
    val manifestFile = findFile(rootDir, "manifest.json")
        ?: throw ModpackError("整合包缺少文件：manifest.json")
    val manifestJson = manifestFile.readText(Charsets.UTF_8)
    val manifest = runCatching {
        serdesJson.decodeFromString<CurseForgePackManifest>(manifestJson)
    }.getOrElse { e ->
        lgr.warn { "manifest.json解析失败: ${manifestFile.absolutePath + "\n" + e}" }
        throw ModpackError("manifest.json 解析失败: ${e.message}")
    }
    return CurseForgeModpackData(
        manifest = manifest,
        file = rootDir
    )
}

private fun inspectModrinthUploadPayload(rootDir: File): UploadPayload {
    val indexFile = findFile(rootDir, "modrinth.index.json")
        ?: throw ModpackError("整合包缺少文件：modrinth.index.json")
    val index = indexFile.readText(Charsets.UTF_8).deser<ModrinthModpackIndex>().getOrElse { err ->
        throw ModpackError("modrinth概要文件解析失败", err)
    }
    val mcVersion = index.dependencies["minecraft"]?.trim().orEmpty().let {
        resolveSupportedMcVersion(it)
    }
    val modloader = (index.dependencies.keys
        .firstOrNull { ModLoader.from(it) != null }
        ?.let { resolveSupportedModLoader(it) }
        ?: throw ModpackError("不支持的Mod加载器: 未知"))
    return UploadPayload(
        sourceType = LocalModpackSourceType.MODRINTH,
        sourceDir = rootDir,
        mods = mutableListOf(),
        mcVersion = mcVersion,
        modloader = modloader,
        sourceName = index.name,
        sourceVersion = index.versionId.ifBlank { "1.0" }
    )
}

private fun inspectCurseForgeUploadPayload(rootDir: File): UploadPayload {
    val modpackData = loadCurseForgeFromDir(rootDir)
    val mcVersion = resolveSupportedMcVersion(modpackData.manifest.minecraft.version)
    val modloader = resolveSupportedModLoader(modpackData.manifest.minecraft.modLoaders.firstOrNull()?.id.orEmpty())

    return UploadPayload(
        sourceType = LocalModpackSourceType.CURSEFORGE,
        sourceDir = rootDir,
        mods = mutableListOf(),
        mcVersion = mcVersion,
        modloader = modloader,
        sourceName = modpackData.manifest.name,
        sourceVersion = modpackData.manifest.version.ifBlank { "1.0" }
    )
}

private fun resolveSupportedMcVersion(
    mcVersionText: String,
): McVersion {
    val mcVersion = McVersion.from(mcVersionText)
    if (mcVersion == null || !mcVersion.enabled) {
        throw ModpackError("暂不支持MC版本${mcVersionText}")
    }
    return mcVersion
}

private fun resolveSupportedModLoader(loaderText: String): ModLoader {
    val normalized = loaderText.trim()
    return ModLoader.from(normalized).takeIf { it != null }
        ?: throw ModpackError("暂不支持Mod加载器${normalized.ifBlank { "未知" }}")
}

private fun collectEmbeddedModFiles(rootDir: File): List<File> {
    val modFiles = rootDir.walkTopDown()
        .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
        .filter { it.invariantSeparatorsPath.contains("/mods/") }
        .toList()
    if (modFiles.size <= 1) return modFiles
    return modFiles
        .groupBy(::embeddedModIdentityKey)
        .values
        .map { grouped -> grouped.maxWithOrNull(::compareEmbeddedModFileVersion) ?: grouped.first() }
}

private fun collectServerPackModFiles(rootDir: File): List<File> {
    val modsDir = rootDir.resolve("mods")
    if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()
    return modsDir.walkTopDown()
        .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
        .toList()
}

private data class EmbeddedModConfigInfo(
    val modId: String?,
    val version: String?
)

private fun embeddedModIdentityKey(file: File): String {
    val config = readPrimaryModConfig(file)
    val modId = config?.modId
    if (!modId.isNullOrBlank()) return "modid:$modId"
    val lowerName = file.nameWithoutExtension.lowercase()
    val slug = lowerName.substringBeforeVersionSuffix()
    if (slug.isNotBlank()) return "slug:$slug"
    return "file:$lowerName"
}

private fun compareEmbeddedModFileVersion(left: File, right: File): Int {
    val leftVersion = readEmbeddedModVersion(left)
    val rightVersion = readEmbeddedModVersion(right)
    val versionCompare = compareLooseVersionStrings(leftVersion, rightVersion)
    if (versionCompare != 0) return versionCompare
    return left.nameWithoutExtension.compareTo(right.nameWithoutExtension, ignoreCase = true)
}

private fun readEmbeddedModVersion(file: File): String {
    val configVersion = readPrimaryModConfig(file)?.version
    if (!configVersion.isNullOrBlank()) return configVersion.trim()
    return extractVersionFromFileName(file.nameWithoutExtension)
}

private fun readPrimaryModConfig(file: File): EmbeddedModConfigInfo? = runCatching {
    ModService.run {
        JarFile(file).use { jar ->
            jar.readModMeta()?.let { meta ->
                EmbeddedModConfigInfo(
                    modId = meta.primaryModId?.trim()?.lowercase()?.ifBlank { null },
                    version = meta.version?.trim()?.ifBlank { null }
                )
            }
        }
    }
}.getOrNull()

private fun collectServerPackExtraFiles(
    rootDir: File,
    matchedModFiles: Set<File>
): List<ServerExtraFile> {
    val canonicalMatchedFiles = matchedModFiles.mapTo(mutableSetOf()) { it.canonicalFile }
    return rootDir.walkTopDown()
        .onEnter { dir -> !shouldSkipServerExtraDir(rootDir, dir) }
        .filter { it.isFile }
        .filterNot { it.canonicalFile in canonicalMatchedFiles }
        .mapNotNull { file ->
            val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
            if (relativePath.isBlank()) return@mapNotNull null
            if (shouldSkipServerExtraFile(relativePath, file)) return@mapNotNull null
            ServerExtraFile(
                sourceFile = file,
                relativePath = relativePath
            )
        }
        .toList()
}

private fun shouldSkipServerExtraDir(rootDir: File, dir: File): Boolean {
    if (dir == rootDir) return false
    val relativePath = dir.relativeTo(rootDir).invariantSeparatorsPath.lowercase()
    val name = dir.name.lowercase()
    if (name == "cache" || name == "logs" || name == "crash-reports") return true
    if (relativePath.startsWith("libraries/")) return true
    val childDirNames = dir.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.map { it.name.lowercase() }
        ?.toSet()
        .orEmpty()
    return setOf("bin", "lib", "jmods").all { it in childDirNames }
}

private fun shouldSkipServerExtraFile(relativePath: String, file: File): Boolean {
    val relativeLower = relativePath.lowercase()
    val fileNameLower = file.name.lowercase()
    if (relativeLower.startsWith("libraries/")) return true
    if (relativeLower.startsWith("logs/") || relativeLower.startsWith("crash-reports/")) return true
    if (fileNameLower == ".ds_store" || fileNameLower == "desktop.ini") return true
    if (file.extension.equals("db", ignoreCase = true)) return true
    if (file.extension.lowercase() in serverExtraMediaExtensions) return true
    val isInsideMods = relativeLower.startsWith("mods/")
    if (!isInsideMods && file.extension.equals("jar", ignoreCase = true)) return true
    if (!isInsideMods && file.extension.equals("exe", ignoreCase = true)) return true
    if (!isInsideMods && file.extension.isBlank()) return true
    return false
}

private fun String.substringBeforeVersionSuffix(): String {
    val match = Regex("""^(.*?)(?:[-_.]?\d.*)$""").matchEntire(this)
    return match?.groupValues?.getOrNull(1)?.trim('-', '_', '.')?.ifBlank { this } ?: this
}

private fun extractVersionFromFileName(nameWithoutExtension: String): String {
    val match = Regex("""(?:^|[-_.])(\d[\w.\-+]*)$""").find(nameWithoutExtension)
    return match?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "0" } ?: "0"
}

private fun compareLooseVersionStrings(left: String, right: String): Int {
    if (left == right) return 0
    val leftTokens = left.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }
    val rightTokens = right.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }
    val maxSize = maxOf(leftTokens.size, rightTokens.size)
    for (index in 0 until maxSize) {
        val leftToken = leftTokens.getOrNull(index)
        val rightToken = rightTokens.getOrNull(index)
        if (leftToken == rightToken) continue
        if (leftToken == null) return -1
        if (rightToken == null) return 1
        val leftNumber = leftToken.toLongOrNull()
        val rightNumber = rightToken.toLongOrNull()
        val cmp = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> 1
            rightNumber != null -> -1
            else -> leftToken.compareTo(rightToken)
        }
        if (cmp != 0) return cmp
    }
    return left.compareTo(right)
}

private val serverExtraMediaExtensions = setOf(
    "psd",
    "png",
    "jpg",
    "jpeg",
    "webp",
    "mp4",
    "ogg",
    "wav"
)

private fun hasOverridesDir(rootDir: File): Boolean {
    return rootDir.walkTopDown().any { it.isDirectory && it.name.equals("overrides", ignoreCase = true) }
}

private fun hasOverridesDir(entryNames: List<String>, packType: PackType): Boolean {
    if (packType == PackType.UNKNOWN) return false
    val metaFileName = when (packType) {
        PackType.MODRINTH -> "modrinth.index.json"
        PackType.CURSEFORGE -> "manifest.json"
        PackType.UNKNOWN -> return false
    }
    val metaPath = entryNames.firstOrNull {
        it.replace('\\', '/').trimStart('/').substringAfterLast('/') == metaFileName
    } ?: return false
    val rootPrefix = metaPath.replace('\\', '/')
        .trimStart('/')
        .substringBeforeLast('/', missingDelimiterValue = "")
        .let { if (it.isBlank()) "" else "$it/" }
    val overridesPath = rootPrefix + "overrides"
    val overridesPrefix = "$overridesPath/"
    return entryNames.any { rawName ->
        val normalized = rawName.replace('\\', '/').trimStart('/')
        normalized == overridesPath || normalized.startsWith(overridesPrefix)
    }
}

private suspend fun buildZipFromDir(
    rootDir: File,
    baseName: String,
    serverExtraFiles: List<ServerExtraFile> = emptyList(),
    onProgress: LoadProgressConsumer = {}
): File {
    val safeName = baseName.ifBlank { "modpack" }
    val target = ClientDirs.packProcDir.resolve("${safeName}_${System.currentTimeMillis()}.tar.zst")
    val oggWorkDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "ogg-batch-").toFile()
    try {
        val walkEntries = rootDir.walkTopDown().toList()
        val fileEntries = walkEntries.filter { it != rootDir }
        onProgress.phase("正在预处理整合包资源文件")
        val processedAssets = preprocessAssetInputsInParallel(
            inputs = walkEntries.asSequence()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val relative = file.relativeTo(rootDir).invariantSeparatorsPath
                    if (relative.isBlank()) return@mapNotNull null
                    val relativeLower = relative.lowercase()
                    if (!shouldPreprocessAsset(relativeLower)) return@mapNotNull null
                    AssetProcessInput(relative, relativeLower, file.readBytes())
                }
                .toList(),
            oggWorkDir = oggWorkDir,
            onProgress = { done, total, currentPath ->
                val fraction = if (total <= 0) 1f else done.toFloat() / total.toFloat()
                onProgress(
                    LoadProgress.Percent(
                        "正在预处理资源文件${displayProgressFileName(currentPath)}($done/$total) ${formatPercent(fraction)}",
                        fraction * 0.5f
                    )
                )
            }
        )
        val addedDirs = mutableSetOf<String>()
        TarZstArchiveWriter(target).use { out ->
            val totalWriteEntries = (
                fileEntries.size +
                    serverExtraFiles.size
                ).coerceAtLeast(1)
            var writtenEntries = 0
            for (file in fileEntries) {
                if (file == rootDir) continue
                val relative = file.relativeTo(rootDir).invariantSeparatorsPath
                if (relative.isBlank()) continue
                val relativeLower = relative.lowercase()
                val topLevel = relative.substringBefore('/', relative)
                writeProcessedEntry(
                    relative = relative,
                    relativeLower = relativeLower,
                    isDirectory = file.isDirectory,
                    lastModified = file.lastModified(),
                    topLevel = topLevel,
                    out = out,
                    addedDirs = addedDirs,
                    readAllBytes = { file.readBytes() },
                    resourcepackBytes = { readResourcepackFile(file, relativeLower, processedAssets[relative]) },
                    nestedZipBytes = if (relativeLower.endsWith(".zip") || relativeLower.endsWith(".jar")) {
                        { processNestedZip(file) }
                    } else null,
                    preprocessedBytes = processedAssets[relative]
                )
                writtenEntries++
                val fraction = writtenEntries.toFloat() / totalWriteEntries.toFloat()
                onProgress(
                    LoadProgress.Percent(
                        "正在写入整合包文件${displayProgressFileName(relative)}($writtenEntries/$totalWriteEntries) ${formatPercent(fraction)}",
                        0.5f + fraction * 0.5f
                    )
                )
            }
            for (extraFile in serverExtraFiles) {
                val relative = "server/${extraFile.relativePath.trimStart('/')}"
                val sourceFile = extraFile.sourceFile
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    throw ModpackError("服务端额外文件不存在: ${sourceFile.absolutePath}")
                }
                ensureArchiveParents(relative, out, addedDirs)
                out.addFile(relative, sourceFile.readBytes(), sourceFile.lastModified())
                writtenEntries++
                val fraction = writtenEntries.toFloat() / totalWriteEntries.toFloat()
                onProgress(
                    LoadProgress.Percent(
                        "正在写入整合包文件${displayProgressFileName(relative)}($writtenEntries/$totalWriteEntries) ${formatPercent(fraction)}",
                        0.5f + fraction * 0.5f
                    )
                )
            }
        }
        onProgress(LoadProgress.Percent("整合包打包完成", 1f))
    } finally {
        runCatching { oggWorkDir.deleteRecursivelyNoSymlink() }
    }
    return target
}

private suspend fun processNestedZip(zipFile: File): ByteArray {
    return ByteArrayOutputStream().use { baos ->
        ZipOutputStream(baos).use { out ->
            val addedDirs = mutableSetOf<String>()
            zipFile.openChineseZip().use { zip ->
                val oggWorkDir = Files.createTempDirectory(ClientDirs.packProcDir.toPath(), "ogg-nested-").toFile()
                try {
                    val entries = zip.entries().asSequence().toList()
                    val processedAssets = preprocessAssetInputsInParallel(
                        inputs = entries.asSequence()
                            .filter { !it.isDirectory }
                            .mapNotNull { entry ->
                                val relative = entry.name.replace('\\', '/').trimStart('/')
                                if (relative.isBlank()) return@mapNotNull null
                                val relativeLower = relative.lowercase()
                                if (!shouldPreprocessAsset(relativeLower)) return@mapNotNull null
                                AssetProcessInput(
                                    relative,
                                    relativeLower,
                                    zip.getInputStream(entry).use { it.readBytes() }
                                )
                            }
                            .toList(),
                        oggWorkDir = oggWorkDir
                    )
                    for (entry in entries) {
                        val relative = entry.name.replace('\\', '/').trimStart('/')
                        if (relative.isBlank()) continue
                        val relativeLower = relative.lowercase()
                        val topLevel = relative.substringBefore('/', relative)
                        val isNestedJarEntry = zipFile.extension.equals("jar", ignoreCase = true)
                        writeProcessedNestedZipEntry(
                            relative = relative,
                            relativeLower = relativeLower,
                            isDirectory = entry.isDirectory,
                            lastModified = entry.time,
                            topLevel = topLevel,
                            out = out,
                            addedDirs = addedDirs,
                            readAllBytes = { zip.getInputStream(entry).use { it.readBytes() } },
                            resourcepackBytes = {
                                readResourcepackEntry(zip, entry, relativeLower, processedAssets[relative])
                            },
                            nestedZipBytes = null,
                            skipCacheDirectory = !isNestedJarEntry,
                            preprocessedBytes = processedAssets[relative]
                        )
                    }
                } finally {
                    runCatching { oggWorkDir.deleteRecursivelyNoSymlink() }
                }
            }
        }
        baos.toByteArray()
    }
}

private fun shouldSkipEntry(
    relativeLower: String,
    isDirectory: Boolean,
    skipCacheDirectory: Boolean = true
): Boolean {
    val relativeLower = relativeLower.replace("overrides/","")
    if (disallowedClientPathPrefixes.any { relativeLower.startsWith(it) }) return true
    if (skipCacheDirectory && containsCacheDirectory(relativeLower)) return true
    if (relativeLower.startsWith("config/") && relativeLower.removePrefix("config/").isExcludedConfigPath()) return true
    if (disallowedClientPathKeywords.any { relativeLower.contains(it) }) return true
    if (relativeLower.endsWith(".mp4") || relativeLower.endsWith(".mov")) return true
    if (relativeLower.endsWith(".mca") && relativeLower.contains("/saves/")) return true
    if (isQuestLangEntryDisallowed(relativeLower, isDirectory)) return true
    return false
}

private fun containsCacheDirectory(relativeLower: String): Boolean {
    val normalized = relativeLower.replace('\\', '/').trim('/')
    if (normalized.isEmpty()) return false
    return normalized.split('/').any { it.equals("cache", ignoreCase = true) }
}

private suspend fun writeProcessedEntry(
    relative: String,
    relativeLower: String,
    isDirectory: Boolean,
    lastModified: Long,
    topLevel: String,
    out: TarZstArchiveWriter,
    addedDirs: MutableSet<String>,
    readAllBytes: () -> ByteArray,
    resourcepackBytes: (() -> ByteArray?)?,
    nestedZipBytes: (suspend () -> ByteArray)?,
    skipCacheDirectory: Boolean = true,
    preprocessedBytes: ByteArray? = null
) {
    if (shouldSkipEntry(relativeLower, isDirectory, skipCacheDirectory = skipCacheDirectory)) return

    if (isDirectory) {
        addDirectoryEntry(relative, out, addedDirs)
        return
    }

    if (topLevel == "resourcepacks") {
        val bytes = resourcepackBytes?.invoke() ?: return
        ensureArchiveParents(relative, out, addedDirs)
        out.addFile(relative, bytes, lastModified)
        return
    }

    ensureArchiveParents(relative, out, addedDirs)
    val bytes = when {
        nestedZipBytes != null -> nestedZipBytes()
        relativeLower.endsWith(".png") -> preprocessedBytes ?: compressPngIfNeeded(readAllBytes())
        relativeLower.endsWith(".ogg") -> preprocessedBytes ?: processOggBytes(readAllBytes(), relative)
        else -> readAllBytes()
    }
    out.addFile(relative, bytes, lastModified)
}

private suspend fun writeProcessedNestedZipEntry(
    relative: String,
    relativeLower: String,
    isDirectory: Boolean,
    lastModified: Long,
    topLevel: String,
    out: ZipOutputStream,
    addedDirs: MutableSet<String>,
    readAllBytes: () -> ByteArray,
    resourcepackBytes: (() -> ByteArray?)?,
    nestedZipBytes: (suspend () -> ByteArray)?,
    skipCacheDirectory: Boolean = true,
    preprocessedBytes: ByteArray? = null
) {
    if (shouldSkipEntry(relativeLower, isDirectory, skipCacheDirectory = skipCacheDirectory)) return

    if (isDirectory) {
        addZipDirectoryEntry(relative, out, addedDirs)
        return
    }

    val bytes = if (topLevel == "resourcepacks") {
        resourcepackBytes?.invoke() ?: return
    } else {
        when {
            nestedZipBytes != null -> nestedZipBytes()
            relativeLower.endsWith(".png") -> preprocessedBytes ?: compressPngIfNeeded(readAllBytes())
            relativeLower.endsWith(".ogg") -> preprocessedBytes ?: processOggBytes(readAllBytes(), relative)
            else -> readAllBytes()
        }
    }
    ensureZipParents(relative, out, addedDirs)
    val zipEntry = ZipEntry(relative).apply { time = lastModified }
    out.putNextEntry(zipEntry)
    out.write(bytes)
    out.closeEntry()
}

private fun readResourcepackEntry(
    source: java.util.zip.ZipFile,
    entry: ZipEntry,
    relativeLower: String,
    preprocessedBytes: ByteArray? = null
): ByteArray? {
    val isOgg = relativeLower.endsWith(".ogg")
    if (!isOgg && entry.size != -1L && entry.size > RESOURCEPACK_MAX_SIZE_BYTES) {
        return null
    }
    val processed = if (preprocessedBytes != null) {
        preprocessedBytes
    } else {
        val rawBytes = source.getInputStream(entry).use { input ->
            when {
                entry.size == -1L -> input.readBytes()
                entry.size > Int.MAX_VALUE -> return null
                !isOgg && entry.size > RESOURCEPACK_MAX_SIZE_BYTES -> return null
                else -> input.readNBytes(entry.size.toInt())
            }
        }
        when {
            isOgg -> processOggBytes(rawBytes, entry.name)
            relativeLower.endsWith(".png") -> compressPngIfNeeded(rawBytes)
            else -> rawBytes
        }
    }
    if (processed.size > RESOURCEPACK_MAX_SIZE_BYTES) return null
    return processed
}

private val disallowedClientPathPrefixes = setOf(
    "config/fancymenu/",
    "packmenu",
    "shaderpacks/",
    "kubejs/probe/"
)
private val disallowedClientPathKeywords = setOf(
    "yes_steve_model",
    "史蒂夫模型",
    "touhou_little_maid"
)
private val allowedQuestLangFiles = setOf("en_us.snbt", "zh_cn.snbt")
private const val QUEST_LANG_PREFIX = "config/ftbquests/quests/lang/"
private const val RESOURCEPACK_MAX_SIZE_BYTES = 1*1024L * 1024
private const val OGG_MAX_DURATION_SECONDS = 5
private const val OGG_OUTPUT_SAMPLE_RATE = 16_000

private fun isQuestLangEntryDisallowed(relativeLower: String, isDirectory: Boolean): Boolean {
    if (!relativeLower.startsWith(QUEST_LANG_PREFIX)) return false
    val remainder = relativeLower.removePrefix(QUEST_LANG_PREFIX)
    if (remainder.isEmpty()) return false
    if (isDirectory) return true
    if (remainder.contains('/')) return true
    return remainder !in allowedQuestLangFiles
}

private fun readResourcepackFile(file: File, relativeLower: String, preprocessedBytes: ByteArray? = null): ByteArray? {
    val isOgg = relativeLower.endsWith(".ogg")
    //不接受>1M资源包
    if (!isOgg && file.length() > RESOURCEPACK_MAX_SIZE_BYTES) return null
    val processed = if (preprocessedBytes != null) {
        preprocessedBytes
    } else {
        val rawBytes = file.inputStream().use { input ->
            when {
                file.length() > Int.MAX_VALUE -> return null
                else -> input.readBytes()
            }
        }
        when {
            isOgg -> processOggBytes(rawBytes, file.name)
            relativeLower.endsWith(".png") -> compressPngIfNeeded(rawBytes)
            else -> rawBytes
        }
    }
    if (processed.size > RESOURCEPACK_MAX_SIZE_BYTES) return null
    return processed
}

private fun shouldPreprocessAsset(relativeLower: String): Boolean {
    return relativeLower.endsWith(".png") || relativeLower.endsWith(".ogg")
}

private suspend fun preprocessAssetInputsInParallel(
    inputs: List<AssetProcessInput>,
    oggWorkDir: File,
    onProgress: (done: Int, total: Int, currentPath: String) -> Unit = { _, _, _ -> }
): Map<String, ByteArray> {
    if (inputs.isEmpty()) return emptyMap()
    val semaphore = Semaphore(4)
    val doneCount = AtomicInteger(0)
    val total = inputs.size
    return coroutineScope {
        inputs.map { input ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val processed = when {
                        input.relativeLower.endsWith(".png") -> compressPngIfNeeded(input.rawBytes)
                        input.relativeLower.endsWith(".ogg") -> processOggBytes(input.rawBytes, input.relativePath, oggWorkDir)
                        else -> input.rawBytes
                    }
                    onProgress(doneCount.incrementAndGet(), total, input.relativePath)
                    input.relativePath to processed
                }
            }
        }.awaitAll().toMap()
    }
}

private fun processOggBytes(rawBytes: ByteArray, entryName: String, workDir: File = ClientDirs.packProcDir): ByteArray {
    val ffmpeg = resolveFfmpegExecutable()
    val inputFile = Files.createTempFile(workDir.toPath(), "ogg-", ".input.ogg").toFile()
    val outputFile = Files.createTempFile(workDir.toPath(), "ogg-", ".output.ogg").toFile()
    return runCatching {
        inputFile.writeBytes(rawBytes)
        transcodeOggWithFfmpeg(ffmpeg, inputFile, outputFile)
        outputFile.readBytes()
    }.onFailure { err ->
        lgr.warn { "ffmpeg处理ogg失败，保留原文件: $entryName\n$err" }
    }.getOrElse { rawBytes }
        .also {
            runCatching { inputFile.delete() }
            runCatching { outputFile.delete() }
        }
}

private fun resolveFfmpegExecutable(): File {
    return sequenceOf(
        ClientDirs.toolsDir.resolve("ffmpeg/ffmpeg.exe"),
        ClientDirs.toolsDir.resolve("ffmpeg/ffmpeg")
    ).firstOrNull { it.exists() && it.isFile }
        ?: throw ModpackError("未找到“传包工具包”，请查看群文档整合包上传章节，进行安装")
}

fun ensureUploadFfmpegReady() {
    resolveFfmpegExecutable()
}

private fun transcodeOggWithFfmpeg(ffmpeg: File, inputFile: File, outputFile: File) {
    val process = ProcessBuilder(
        ffmpeg.absolutePath,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        inputFile.absolutePath,
        "-ar",
        OGG_OUTPUT_SAMPLE_RATE.toString(),
        "-t",
        OGG_MAX_DURATION_SECONDS.toString(),
        "-c:a",
        "libvorbis",
        "-b:a",
        "96k",
        outputFile.absolutePath
    ).redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw ModpackError("音频转码失败(code=$exitCode): $output")
    }
    if (!outputFile.exists() || outputFile.length() <= 0L) {
        throw ModpackError("未生成有效音频文件")
    }
}

private fun addDirectoryEntry(rawPath: String, output: TarZstArchiveWriter, addedDirs: MutableSet<String>) {
    val sanitized = rawPath.trim('/').ifEmpty { return }
    ensureArchiveParents(sanitized, output, addedDirs)
    val dirEntry = "$sanitized/"
    if (addedDirs.add(dirEntry)) output.addDirectory(sanitized)
}

private fun addZipDirectoryEntry(rawPath: String, output: ZipOutputStream, addedDirs: MutableSet<String>) {
    val sanitized = rawPath.trim('/').ifEmpty { return }
    ensureZipParents(sanitized, output, addedDirs)
    val dirEntry = "$sanitized/"
    if (!addedDirs.add(dirEntry)) return
    output.putNextEntry(ZipEntry(dirEntry))
    output.closeEntry()
}

private fun ensureArchiveParents(path: String, output: TarZstArchiveWriter, addedDirs: MutableSet<String>) {
    val normalized = path.trim('/').ifEmpty { return }
    val parts = normalized.split('/')
    if (parts.size <= 1) return
    var current = ""
    for (i in 0 until parts.size - 1) {
        val part = parts[i]
        if (part.isEmpty()) continue
        current = if (current.isEmpty()) part else "$current/$part"
        val dirEntry = "$current/"
        if (addedDirs.add(dirEntry)) output.addDirectory(current)
    }
}

private fun ensureZipParents(path: String, output: ZipOutputStream, addedDirs: MutableSet<String>) {
    val normalized = path.trim('/').ifEmpty { return }
    val parts = normalized.split('/')
    if (parts.size <= 1) return
    var current = ""
    for (i in 0 until parts.size - 1) {
        val part = parts[i]
        if (part.isEmpty()) continue
        current = if (current.isEmpty()) part else "$current/$part"
        val dirEntry = "$current/"
        if (!addedDirs.add(dirEntry)) continue
        output.putNextEntry(ZipEntry(dirEntry))
        output.closeEntry()
    }
}

suspend fun uploadModpack(
    payload: UploadPayload,
    mods: List<Mod>,
    modpackName: String,
    versionName: String,
    categories: List<Modpack.Category>,
    iconUrl: String?,
    sourceUrl: String?,
    info: String?,
    updateModpackId: ObjectId?,
    onProgress: (String) -> Unit,
    onPackProcessProgress: LoadProgressConsumer = {},
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("正在打包整合包...请等一两分钟")
    val uploadZip = try {
        buildZipFromDir(
            rootDir = payload.sourceDir,
            baseName = payload.sourceName,
            serverExtraFiles = payload.serverExtraFiles,
            onProgress = onPackProcessProgress
        )
    } catch (e: Exception) {
        lgr.warn { "打包整合包失败: ${payload.sourceDir.absolutePath + "\n" + e}" }
        payload.sourceDir.deleteRecursivelyNoSymlink()
        onError("打包失败: ${e.message}")
        return
    }

    val totalBytes = uploadZip.length()
    val startTime = System.nanoTime()
    var lastProgressUpdate = 0L

    try {
        if (updateModpackId != null) {
            uploadNewVersion(
                modpackId = updateModpackId,
                versionName = versionName,
                mods = mods,
                uploadZip = uploadZip,
                totalBytes = totalBytes,
                startTime = startTime,
                lastProgressUpdate = lastProgressUpdate,
                onProgress = onProgress,
                onError = onError,
                onDone = onDone
            )
        } else {
            uploadNewModpack(
                modpackName = modpackName,
                versionName = versionName,
                mcVersion = payload.mcVersion,
                modloader = payload.modloader,
                mods = mods,
                categories = categories,
                iconUrl = iconUrl,
                sourceUrl = sourceUrl,
                info = info,
                uploadZip = uploadZip,
                totalBytes = totalBytes,
                startTime = startTime,
                lastProgressUpdate = lastProgressUpdate,
                onProgress = onProgress,
                onError = onError,
                onDone = onDone
            )
        }
    } catch (e: Exception) {
        lgr.warn { "上传整合包失败: ${payload.sourceName + "\n" + e} $versionName" }
        onError("上传失败: ${e.message ?: "未知错误"}")
    } finally {
        uploadZip.delete()
        payload.sourceDir.deleteRecursivelyNoSymlink()
    }
}

fun createUploadModpackTask2(
    payload: UploadPayload,
    mods: List<Mod>,
    modpackName: String,
    versionName: String,
    categories: List<Modpack.Category>,
    iconUrl: String?,
    sourceUrl: String?,
    info: String?,
    updateModpackId: ObjectId?
): Task2 {
    var uploadedModpackId: ObjectId? = updateModpackId
    var builtClientZip: File? = null
    val processedMods = ModpackModProcessor.processMods(mods)
    val uploadTask = Task2.Leaf("上传整合包") { ctx ->
        var errorMessage: String? = null
        var doneSummary: String? = null
        ctx.emit(Task2Progress("开始上传整合包", 0f))
        try {
                builtClientZip = buildZipFromDir(
                    rootDir = payload.sourceDir,
                    baseName = payload.sourceName,
                    serverExtraFiles = payload.serverExtraFiles,
                    onProgress = { progress -> ctx.emit(progress) }
                )
            val uploadZip = builtClientZip ?: throw ModpackError("整合包打包失败")
            val totalBytes = uploadZip.length()
            val startTime = System.nanoTime()
            val lastProgressUpdate = 0L
            if (updateModpackId != null) {
                uploadNewVersion(
                    modpackId = updateModpackId,
                    versionName = versionName,
                    mods = processedMods,
                    uploadZip = uploadZip,
                    totalBytes = totalBytes,
                    startTime = startTime,
                    lastProgressUpdate = lastProgressUpdate,
                    onProgress = { text -> ctx.emit(Task2Progress(text)) },
                    onError = { msg -> errorMessage = msg },
                    onDone = { summary ->
                        doneSummary = summary
                        ctx.emit(Task2Progress(summary, 1f))
                    }
                )
            } else {
                uploadNewModpack(
                    modpackName = modpackName,
                    versionName = versionName,
                    mcVersion = payload.mcVersion,
                    modloader = payload.modloader,
                    mods = processedMods,
                    categories = categories,
                    iconUrl = iconUrl,
                    sourceUrl = sourceUrl,
                    info = info,
                    uploadZip = uploadZip,
                    totalBytes = totalBytes,
                    startTime = startTime,
                    lastProgressUpdate = lastProgressUpdate,
                    onProgress = { text -> ctx.emit(Task2Progress(text)) },
                    onError = { msg -> errorMessage = msg },
                    onDone = { summary ->
                        doneSummary = summary
                        ctx.emit(Task2Progress(summary, 1f))
                    }
                )
            }
            errorMessage?.let { throw ModpackError(it) }
            uploadedModpackId = resolveUploadedModpackId(
                updateModpackId = updateModpackId,
                modpackName = modpackName,
                versionName = versionName
            )
            if (doneSummary == null) {
                throw ModpackError("上传任务未返回结果")
            }
        } catch (e: Throwable) {
            builtClientZip?.let { runCatching { it.delete() } }
            runCatching { payload.sourceDir.deleteRecursivelyNoSymlink() }
            throw e
        }
    }
    val downloadMissingModsTask = Task2.Leaf("下载剩余客户端Mod") { ctx ->
        val missingMods = withContext(Dispatchers.IO) {
            processedMods
                .filter(::isUploadClientInstallableMod)
                .filterNot(ModService::isDownloadedModFileValid)
        }
        if (missingMods.isEmpty()) {
            ctx.emit(Task2Progress("客户端Mod已齐全", 1f))
        } else {
            ctx.emit(Task2Progress("开始下载剩余客户端Mod，共${missingMods.size}个", 0f))
            ModService.downloadModsTask2(missingMods).runInline(ctx)
        }
    }
    val installTask = Task2.Leaf("本地安装整合包") { ctx ->
        val modpackId = uploadedModpackId ?: throw ModpackError("上传后未找到整合包")
        val uploadZip = builtClientZip ?: throw ModpackError("本地客户端包不存在")
        try {
            ModpackService.installBuiltClientZipTask2(
                mcVersion = payload.mcVersion,
                modLoader = payload.modloader,
                modpackId = modpackId,
                verName = versionName,
                mods = processedMods,
                clientPackFile = uploadZip,
                modpackName = modpackName,
                embeddedModOriginalFileNames = payload.embeddedModOriginalFileNames
            ).runInline(ctx)
        } finally {
            runCatching { uploadZip.delete() }
            runCatching { payload.sourceDir.deleteRecursivelyNoSymlink() }
        }
    }
    return Task2.Sequence(
        title = "上传并安装整合包 $modpackName $versionName",
        children = listOf(uploadTask, downloadMissingModsTask, installTask)
    )
}

private fun isUploadClientInstallableMod(mod: Mod): Boolean =
    mod.side != Mod.Side.SERVER && mod.side != Mod.Side.UNKNOWN

fun modpackUploadTaskKey(
    updateModpackId: ObjectId?,
    modpackName: String,
    versionName: String
): String = buildString {
    append("modpack-upload:")
    append(updateModpackId?.toHexString() ?: "new")
    append(':')
    append(modpackName.trim().lowercase())
    append(':')
    append(versionName.trim().lowercase())
}

private suspend fun resolveUploadedModpackId(
    updateModpackId: ObjectId?,
    modpackName: String,
    versionName: String
): ObjectId {
    if (updateModpackId != null) return updateModpackId
    val myModpacks = server.makeRequest<List<Modpack>>("modpack/my").data.orEmpty()
    return myModpacks
        .filter { it.name == modpackName && it.versions.any { version -> version.name == versionName } }
        .maxByOrNull { pack -> pack.versions.maxOfOrNull { it.time } ?: 0L }
        ?._id
        ?: throw ModpackError("上传成功，但未能定位到刚创建的整合包")
}

private suspend fun uploadNewModpack(
    modpackName: String,
    versionName: String,
    mcVersion: McVersion,
    modloader: ModLoader,
    mods: List<Mod>,
    categories: List<Modpack.Category>,
    iconUrl: String?,
    sourceUrl: String?,
    info: String?,
    uploadZip: File,
    totalBytes: Long,
    startTime: Long,
    lastProgressUpdate: Long,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("创建新整合包 $modpackName...")

    val dto = Modpack.CreateWithVersionDto(
        name = modpackName,
        verName = versionName,
        mcVer = mcVersion,
        modLoader = modloader,
        iconUrl = iconUrl?.trim()?.ifBlank { null },
        sourceUrl = sourceUrl?.trim()?.ifBlank { null },
        info = info?.trim()?.ifBlank { null },
        categories = Modpack.normalizeCategories(categories),
        mods = mods.toMutableList()
    )
    val dtoJson = serdesJson.encodeToString(dto)

    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "dto",
                value = dtoJson,
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            )
            append(
                key = "file",
                value = InputProvider { uploadZip.inputStream().asInput().buffered() },
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${uploadZip.name}\"")
                }
            )
        }
    )

    val createResp = server.makeRequest<Unit>(
        path = "modpack",
        method = HttpMethod.Post,
    ) {
        timeout {
            requestTimeoutMillis = 60 * 60 * 1000L
            socketTimeoutMillis = 60 * 60 * 1000L
        }
        setBody(multipartContent)
        onUpload { bytesSentTotal, contentLength ->
            val now = System.nanoTime()
            val shouldUpdate = contentLength != null && bytesSentTotal == contentLength ||
                    now - lastUpdate > 75_000_000L
            if (shouldUpdate) {
                lastUpdate = now
                val elapsedSeconds = (now - startTime) / 1_000_000_000.0
                val total = contentLength?.takeIf { it > 0 } ?: totalBytes
                val percent = if (total <= 0) 100 else ((bytesSentTotal * 100) / total).toInt()
                val speed = if (elapsedSeconds <= 0) 0.0 else bytesSentTotal / elapsedSeconds
                onProgress(
                    buildString {
                        appendLine("正在上传整合包 $modpackName...")
                        appendLine(
                            "进度：${
                                percent.coerceIn(0, 100)
                            }% (${bytesSentTotal.humanFileSize}/${total.humanFileSize})"
                        )
                        appendLine("速度：${speed.humanSpeed}")
                    }
                )
            }
        }
    }

    if (!createResp.ok) {
        onError(createResp.msg)
        return
    }

    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
    val speed = if (elapsedSeconds <= 0) 0.0 else totalBytes / elapsedSeconds
    onDone(
        buildString {
            appendLine("文件大小: ${totalBytes.humanFileSize}")
            appendLine("平均速度: ${speed.humanSpeed}")
            appendLine("耗时: ${"%.1f".format(elapsedSeconds)}秒")
            appendLine("上传完成，正在继续本地安装")
        }
    )
}

private suspend fun uploadNewVersion(
    modpackId: ObjectId,
    versionName: String,
    mods: List<Mod>,
    uploadZip: File,
    totalBytes: Long,
    startTime: Long,
    lastProgressUpdate: Long,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: (String) -> Unit
) {
    onProgress("上传新版本 $versionName...")

    val modpackIdStr = modpackId.toHexString()
    val versionEncoded = versionName.urlEncoded
    val modsJson = serdesJson.encodeToString(mods.toMutableList())

    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "mods",
                value = modsJson,
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            )
            append(
                key = "file",
                value = InputProvider { uploadZip.inputStream().asInput().buffered() },
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${uploadZip.name}\"")
                }
            )
        }
    )

    val createVersionResp = server.makeRequest<Unit>(
        path = "modpack/$modpackIdStr/version/$versionEncoded",
        method = HttpMethod.Post,
    ) {
        timeout {
            requestTimeoutMillis = 60 * 60 * 1000L
            socketTimeoutMillis = 60 * 60 * 1000L
        }
        setBody(multipartContent)
        onUpload { bytesSentTotal, contentLength ->
            val now = System.nanoTime()
            val shouldUpdate = contentLength != null && bytesSentTotal == contentLength ||
                    now - lastUpdate > 75_000_000L
            if (shouldUpdate) {
                lastUpdate = now
                val elapsedSeconds = (now - startTime) / 1_000_000_000.0
                val total = contentLength?.takeIf { it > 0 } ?: totalBytes
                val percent = if (total <= 0) 100 else ((bytesSentTotal * 100) / total).toInt()
                val speed = if (elapsedSeconds <= 0) 0.0 else bytesSentTotal / elapsedSeconds
                onProgress(
                    buildString {
                        appendLine("正在上传版本 ${versionName}...")
                        appendLine(
                            "进度：${
                                percent.coerceIn(0, 100)
                            }% (${bytesSentTotal.humanFileSize}/${total.humanFileSize})"
                        )
                        appendLine("速度：${speed.humanSpeed}")
                    }
                )
            }
        }
    }

    if (!createVersionResp.ok) {
        onError(createVersionResp.msg)
        return
    }

    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
    val speed = if (elapsedSeconds <= 0) 0.0 else totalBytes / elapsedSeconds
    onDone(
        buildString {
            appendLine("文件大小: ${totalBytes.humanFileSize}")
            appendLine("平均速度: ${speed.humanSpeed}")
            appendLine("耗时: ${"%.1f".format(elapsedSeconds)}秒")
            appendLine("上传完成，正在继续本地安装")
        }
    )
}

private enum class PackType { MODRINTH, CURSEFORGE, UNKNOWN }

