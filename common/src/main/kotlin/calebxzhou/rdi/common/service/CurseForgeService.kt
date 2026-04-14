package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.openChineseZip
import calebxzhou.rdi.common.exception.ModpackError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService.briefInfo
import calebxzhou.rdi.common.service.ModService.modDescription
import calebxzhou.rdi.common.service.ModService.modLogo
import calebxzhou.rdi.common.service.ModService.ofMirrorUrl
import calebxzhou.rdi.common.service.ModService.readNeoForgeConfig
import calebxzhou.rdi.common.service.ModService.toVo
import calebxzhou.rdi.common.service.ModrinthService.getMultipleProjects
import calebxzhou.rdi.common.service.ModrinthService.getVersionsFromHashes
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.FileNotFoundException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.util.jar.JarFile


object CurseForgeService {
    val slugBriefInfo: Map<String, ModBriefInfo> by lazy { ModService.buildSlugMap(briefInfo) { it.curseforgeSlugs } }
    private val lgr by Loggers
    const val OFFICIAL_URL = "https://api.curseforge.com/v1"
    //镜像源可能会缺mod  比如McJtyLib - 1.21-9.0.14


    suspend fun List<File>.loadInfoCurseForge(): CurseForgeLocalResult {
        val hashToFile = this.associateBy { it.murmur2 }
        val hashes = hashToFile.keys.toList()
        val fingerprintData = matchFingerprintData(hashes)
        val result = getInfosFromHash(hashToFile, fingerprintData)
        //cache loaded info
        return result
    }


    //从完整的cf mod信息取得card vo
    private fun CurseForgeModInfo.toCardVo(modFile: File? = null): Mod.CardVo {
        val briefInfo = slugBriefInfo[slug]
        val icons = buildList {
            briefInfo?.logoUrl?.let { add(it) }
            logo?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            logo?.url?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val resolvedName = (name ?: slug).ifBlank { slug }
        val iconBytes = modFile?.let {
            runCatching { JarFile(it).use { jar -> jar.modLogo } }.getOrNull()
        }
        val introText = summary?.takeIf { it.isNotBlank() }?.trim()
            ?: modFile?.let { JarFile(it).readNeoForgeConfig()?.modDescription }
            ?: "暂无介绍"

        return Mod.CardVo(
            name = resolvedName,
            nameCn = briefInfo?.nameCn,
            intro = briefInfo?.intro ?: introText,
            iconData = iconBytes,
            iconUrls = icons,
            side = Mod.Side.BOTH
        )
    }


    suspend fun getInfosFromHash(
        hashToFile: Map<Long, File>,
        fingerprintData: CurseForgeFingerprintData
    ): CurseForgeLocalResult {

        data class MatchRecord(val projectId: Int, val fileId: String, val fingerprint: String, val file: File)

        val matchRecords = fingerprintData.exactMatches.mapNotNull { match ->
            val projectId = match.id.takeIf { it > 0 } ?: return@mapNotNull null
            val fingerprint = match.file.fileFingerprint
            val localFile = hashToFile[fingerprint] ?: return@mapNotNull null
            MatchRecord(
                projectId = projectId,
                fileId = match.file.id.toString(),
                fingerprint = fingerprint.toString(),
                file = localFile
            )
        }.groupBy { it.projectId }

        val modIds = matchRecords.keys.toList()
        if (modIds.isEmpty()) {
            lgr.info { "mod id 是空的" }
            return CurseForgeLocalResult(
                matched = emptyList(),
                unmatched = hashToFile.values.toList()
            )
        }

        val cfMods = getModsInfo(modIds)

        data class CfModMeta(
            val projectId: Int,
            val canonicalSlug: String,
            val normalizedSlug: String,
            val files: List<MatchRecord>,
            val mod: CurseForgeModInfo
        )

        val cfModMeta = cfMods.mapNotNull { mod ->
            val projectId = mod.id
            val files = matchRecords[projectId].orEmpty()
            if (files.isEmpty()) return@mapNotNull null
            val rawSlug = mod.slug.trim()
            val canonicalSlug = when {
                rawSlug.isNotEmpty() -> rawSlug
                !mod.name.isNullOrBlank() -> mod.name.trim()
                else -> projectId.toString()
            }
            CfModMeta(
                projectId = projectId,
                canonicalSlug = canonicalSlug,
                normalizedSlug = canonicalSlug.lowercase(),
                files = files,
                mod = mod
            )
        }

        val matchedFiles = cfModMeta.flatMap { meta -> meta.files.map { it.file } }.toList()

        val matched = cfModMeta.flatMap { meta ->
            meta.files.map { record ->
                Mod(
                    platform = "cf",
                    projectId = meta.projectId.toString(),
                    slug = meta.canonicalSlug,
                    fileId = record.fileId,
                    hash = record.fingerprint,
                    ).apply {
                    file = record.file
                    vo = (slugBriefInfo[meta.normalizedSlug]?.toVo(record.file)
                        ?: meta.mod.toCardVo(record.file)).copy(side = side)
                }
            }
        }
        val unmatched = hashToFile.values.filterNot { it in matchedFiles }
        lgr.info { "curseforge没找到这些mod：${unmatched}" }
        return CurseForgeLocalResult(matched, unmatched)

    }

    /**
     * Fill ModCardVo for CurseForge mods that don't have vo yet
     * @return List of mods with vo filled
     */
    @Deprecated("")
    suspend fun List<Mod>.fillCurseForgeVo(): List<Mod> {
        // Filter mods that need vo and are from CurseForge
        val modsNeedingVo = filter { it.vo == null && it.platform.equals("cf", ignoreCase = true) }
        if (modsNeedingVo.isEmpty()) return this

        // Batch fetch mod info for all mods needing vo
        val projectIdToMod = modsNeedingVo.associateBy { it.projectId.toInt() }
        val modInfos = getModsInfo(projectIdToMod.keys.toList())
        val projectIdToVo = modInfos.associate { it.id to it.toCardVo() }
        forEach { mod ->
            if (mod.vo == null && mod.platform.equals("cf", ignoreCase = true)) {
                mod.apply { vo = projectIdToVo[mod.projectId.toInt()]?.copy(side = side) }
            }
        }
        return this
    }
    suspend fun mapManifestEntriesToMods(files: List<CurseForgePackManifest.File>): List<Mod> {
        val modInfoMap = getModsInfo(files.map { it.projectId }).associateBy { it.id }
        val fileInfoMap = getModFilesInfo(files.map { it.fileId }).associateBy { it.id }
        val fileSha1Map = fileInfoMap.mapValues { (_, fileInfo) ->
            fileInfo.hashes.firstOrNull { it.algo == 1 }?.value?.trim()?.lowercase().orEmpty()
        }
        val allSha1 = fileSha1Map.values.filter { it.isNotBlank() }.distinct()
        val sha1ToMrVersion = if (allSha1.isEmpty()) {
            emptyMap()
        } else {
            getVersionsFromHashes(allSha1)
        }
        val mrProjectMap = sha1ToMrVersion.values
            .map { it.projectId }
            .distinct()
            .let { ids ->
                if (ids.isEmpty()) emptyMap() else getMultipleProjects(ids).associateBy { it.id }
            }
        val libraryModSlugs = arrayListOf<String>()
        return files.mapNotNull { curseFile ->
            val modInfo = modInfoMap[curseFile.projectId] ?: let {
                lgr.warn { "mod ${curseFile.projectId}/${curseFile.fileId} 在mod info map没有信息" }
                return@mapNotNull null
            }
            val cfSlug = modInfo.slug
            val fileInfo = fileInfoMap[curseFile.fileId] ?: let {
                lgr.error{"mod ${curseFile.projectId}/${curseFile.fileId} file info map没有信息"}
                return@mapNotNull null
            }
            val mrProject = fileSha1Map[curseFile.fileId]
                ?.takeIf { it.isNotBlank() }
                ?.let { sha1ToMrVersion[it] }
                ?.let { mrProjectMap[it.projectId] }
            val side = mrProject?.run {
                if(categories.contains("library")) {
                    libraryModSlugs+=cfSlug
                    return@run Mod.Side.BOTH
                }
                if (serverSide == "unsupported") {
                    return@run Mod.Side.CLIENT
                }
                if (clientSide == "unsupported") {
                    return@run Mod.Side.SERVER
                } else return@run Mod.Side.BOTH
            } ?: Mod.Side.UNKNOWN
            Mod(
                platform = "cf",
                projectId = modInfo.id.toString(),
                slug = cfSlug,
                fileId = fileInfo.id.toString(),
                hash = fileInfo.fileFingerprint.toString(),
                side = side
            ).apply {
                vo = modInfo.toCardVo()
            }
        }.also { mod ->

            lgr.info { "lib mod: ${libraryModSlugs.joinToString(",")}" }
            lgr.info { "server mod：${mod.filter { it.side == Mod.Side.SERVER }.map { it.slug }}" }
            lgr.info { "client mod：${mod.filter { it.side == Mod.Side.CLIENT }.map { it.slug }}" }
            lgr.info { "both  mod：${mod.filter { it.side == Mod.Side.BOTH }.map { it.slug }}" }
        }
    }

    /**
     * Load and validate a CurseForge modpack from a ZIP file
     * @param zipPath Path to the modpack ZIP file
     * @return Parsed manifest and list of override entries with their root prefix
     * @throws ModpackError if validation fails
     */
    suspend fun loadModpack(zipPath: String): CurseForgeModpackData {
        val zipFile = File(zipPath)
        if (!zipFile.exists() || !zipFile.isFile) {
            throw ModpackError("找不到整合包文件: ${zipFile.path}")
        }

        val zip = zipFile.openChineseZip()

        try {
            val entries = zip.entries().asSequence().toList()
            entries.find { it.name == ".minecraft" }
                ?.let { throw ModpackError("你应该选整合包 而不是客户端\n你可以用PCL的导出功能 将客户端转换为整合包") }
            //玩不了gto
            val hasGtoCore = entries.any { it.name.startsWith("overrides/mods/gtocore") }
            val hasGtoNativeLib = entries.any { it.name.startsWith("overrides/mods/gtonativelib") }
            if (hasGtoCore && hasGtoNativeLib) {
                throw ModpackError("无法识别具有单机反作弊与代码加密的整合包")
            }
            val manifestEntry = entries.firstOrNull {
                !it.isDirectory && it.name.substringAfterLast('/') == "manifest.json"
            } ?: throw ModpackError("整合包缺少文件：manifest.json")

            val rootPrefix = manifestEntry.name.substringBeforeLast('/', missingDelimiterValue = "")
                .let { if (it.isBlank()) "" else "$it/" }
            val overridesFolder = rootPrefix + "overrides/"
            val overrideEntries = entries.filter { !it.isDirectory && it.name.startsWith(overridesFolder) }

            if (overrideEntries.isEmpty()) {
                throw ModpackError("整合包缺少目录：overrides")
            }

            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val manifest = runCatching {
                serdesJson.decodeFromString<CurseForgePackManifest>(manifestJson)
            }.getOrElse {
                throw ModpackError("manifest.json 解析失败: ${it.message}")
            }

            val supportedVersion = McVersion.from(manifest.minecraft.version)
            if (supportedVersion == null || !supportedVersion.enabled) {
                val supportedList = McVersion.entries.joinToString(", ") { it.mcVer }
                throw ModpackError("不支持的 MC 版本: ${manifest.minecraft.version}，当前只支持: $supportedList")
            }

            val loaderId = manifest.minecraft.modLoaders.firstOrNull { it.primary }?.id
                ?: manifest.minecraft.modLoaders.firstOrNull()?.id
            val loaderSupported = loaderId?.startsWith("neoforge", ignoreCase = true) == true ||
                loaderId?.startsWith("forge", ignoreCase = true) == true
            if (!loaderSupported) {
                throw ModpackError("不支持的 Mod 加载器: ${loaderId ?: "未知"}，当前只支持 Forge/NeoForge")
            }

            val modpackName = manifest.name.trim()
            if (modpackName.isEmpty()) {
                throw ModpackError("manifest.json 中缺少整合包名称")
            }

            var versionName = manifest.version.trim()
            if (versionName.isEmpty()) {
                lgr.warn { "manifest.json 中缺少版本号" }
                versionName = "1.0"
            }

            return CurseForgeModpackData(
                manifest = manifest,
                file = zipFile,
            )
        } catch (e: ModpackError) {
            throw e
        } catch (e: Exception) {
            throw ModpackError("处理整合包时出错: ${e.message}")
        } finally {
            withContext(Dispatchers.IO) {
                zip.close()
            }
        }
    }


    //cf - mr
    val String.cf2MrSlug: String
        get() {
            if (isBlank()) return this
            val info = slugBriefInfo[trim().lowercase()] ?: return this
            return info.modrinthSlugs.firstOrNull { it.isNotBlank() }?.trim() ?: this
        }

    @VisibleForTesting
    private suspend fun makeRequest(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        body: Any? = null,
        ignoreMirror: Boolean = false
    ): HttpResponse {
        suspend fun doRequest(base: String) = ktorClient.request {
            url("${base}/${path}")
            json()
            header(
                "x-api-key", byteArrayOf(
                    36, 50, 97, 36, 49, 48, 36, 55, 87, 87, 86, 49, 87, 69, 76, 99, 119, 88, 56, 88,
                    112, 55, 100, 54, 56, 77, 72, 115, 46, 53, 103, 114, 84, 121, 90, 86, 97, 54,
                    83, 121, 110, 121, 101, 83, 121, 77, 104, 49, 114, 115, 69, 56, 57, 110, 73,
                    97, 48, 57, 122, 79
                ).let { String(it) })
            body?.let { setBody(it) }
            this.method = method
        }
        if (ignoreMirror || !ModService.preferMirror) {
            return doRequest(OFFICIAL_URL)
        }

        val mirrorResult = runCatching { doRequest(OFFICIAL_URL.ofMirrorUrl) }
        mirrorResult.getOrNull()?.let { response ->
            if (response.status.isSuccess()) return response

            val body = response.bodyAsText()
            lgr.warn { "CurseForge mirror request failed, falling back to official API: $body" }
        }

        mirrorResult.exceptionOrNull()?.let { ex ->
            lgr.warn { "CurseForge mirror request with exception, falling back to official API: ${ex.message + "\n" + ex }" }
        }

        return doRequest(OFFICIAL_URL)
    }

    /**
     * matches a list of murmur2 fingerprints against CurseForge's database
     * @param hashes List of Long fingerprints in murmur2 format
     * @return CurseForgeFingerprintData containing exact matches, partial matches, and unmatched fingerprints
     */
    suspend fun matchFingerprintData(hashes: List<Long>): CurseForgeFingerprintData {
        @Serializable
        data class CurseForgeFingerprintRequest(val fingerprints: List<Long>)

        val response = makeRequest(
            //432 for minecraft
            "fingerprints/432",
            HttpMethod.Post,
            CurseForgeFingerprintRequest(fingerprints = hashes)
        ).body<CurseForgeFingerprintResponse>()
        val data = response.data ?: CurseForgeFingerprintData()
        lgr.debug {
            "CurseForge: ${data.exactMatches.size} exact matches, ${data.partialMatches.size} partial matches, ${data.unmatchedFingerprints.size} unmatched"
        }

        return data
    }

    private suspend fun requestModFiles(fileIds: List<Int>, official: Boolean = false): List<CurseForgeFile> {
        @Serializable
        data class CFFileIdsRequest(val fileIds: List<Int>)

        return makeRequest(
            "mods/files",
            HttpMethod.Post,
            CFFileIdsRequest(fileIds),
            ignoreMirror = official,
        ).body<CurseForgeFileListResponse>().data
    }

    private suspend fun requestMods(modIds: List<Int>, official: Boolean = false): List<CurseForgeModInfo> {
        @Serializable
        data class CFModsRequest(val modIds: List<Int>, val filterPcOnly: Boolean = true)

        @Serializable
        data class CFModsResponse(val data: List<CurseForgeModInfo>? = null)
        return makeRequest(
            "mods",
            HttpMethod.Post,
            CFModsRequest(modIds),
            official
        ).body<CFModsResponse>().data!!.filter { it.isMod }

    }

    //从mod project id列表获取cf mod信息
    suspend fun getModsInfo(modIds: List<Int>): List<CurseForgeModInfo> {

        if (modIds.isEmpty()) return emptyList()

        val mods = requestMods(modIds).toMutableList()
        val foundIds = mods.mapTo(mutableSetOf()) { it.id }
        val missingIds = modIds.filterNot { foundIds.contains(it) }
        if (missingIds.isNotEmpty()) {
            lgr.warn { "not found ids：${missingIds}, retry official api" }
            mods += requestMods(missingIds, true)
        }

        return mods
    }

    suspend fun getModFileInfo(modId: Int, fileId: Int): CurseForgeFile? {
        return makeRequest("mods/${modId}/files/${fileId}").body<CurseForgeFileResponse>().data
    }

    suspend fun getModFilesInfo(fileIds: List<Int>): List<CurseForgeFile> {
        if (fileIds.isEmpty()) return emptyList()

        val files = requestModFiles(fileIds).toMutableList()
        val foundIds = files.mapTo(mutableSetOf()) { it.id }
        val missingIds = fileIds.filterNot { foundIds.contains(it) }
        if (missingIds.isNotEmpty()) {
            lgr.warn { "not found ids：${missingIds}, retry official api" }
            files += requestModFiles(missingIds, true)
        }
        return files
    }

    fun parseModpack(zipFile: File): Result<CurseForgePackManifest> {
        zipFile.openChineseZip().use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
                ?: throw FileNotFoundException("Invalid modpack zip: manifest.json not found")
            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val manifest = runCatching {
                Json.decodeFromString<CurseForgePackManifest>(manifestJson)
            }.getOrElse {
                throw SerializationException("manifest.json parse failed: ${it.message}", it)
            }
            return Result.success(manifest)
        }
    }
}



