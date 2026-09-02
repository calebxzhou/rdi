package calebxzau.rdi.server.modpack

import calebxzhou.rdi.common.exception.RequestError
import calebxzau.rdi.common.model.ContentPlatform
import calebxzhou.rdi.common.model.ModLoader
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2ContentBindingDto
import calebxzau.rdi.common.model.Modpack2ContentKeyDto
import calebxzau.rdi.common.model.Modpack2ContentSourceDto
import calebxzau.rdi.common.model.Modpack2ContentDto
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2ManifestFormat
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2RawFileRoot
import calebxzhou.rdi.common.model.ModrinthModpackIndex
import calebxzhou.rdi.common.model.CurseForgePackManifest
import calebxzhou.rdi.common.model.McVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.charset.StandardCharsets

/** Pure validation of a submitted Modpack2 installer manifest contract. */
object Modpack2ManifestValidator {
    const val MAX_MANIFEST_BYTES: Int = 4 * 1024 * 1024

    private val strictJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
    private val sha1Pattern = Regex("^[0-9a-f]{40}$")
    private val windowsAbsolutePath = Regex("^[A-Za-z]:/.*")
    private val windowsReservedName = Regex("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?$")

    fun validate(
        manifest: calebxzau.rdi.common.model.Modpack2VersionManifestDto,
        contents: List<Modpack2Content>,
        rawFiles: List<Modpack2RawFile>,
        expectedMc: Int,
        expectedLoader: Modpack2Loader,
        actualManifestJson: String? = null,
    ): calebxzau.rdi.common.model.Modpack2VersionManifestDto {
        val json = requireManifestJson(manifest.manifestJson)
        actualManifestJson?.let { actual ->
            val actualElement = parseJson(actual, "上传归档中的manifest")
            if (actualElement != json) throw RequestError("上传归档中的manifest与提交合同不一致")
        }
        val normalizedContents = contents.map(::normalizeContent)
        val contentKeys = normalizedContents.map(::keyOf)
        if (contentKeys.toSet().size != contentKeys.size) {
            throw RequestError("版本内容存在重复身份")
        }
        val normalizedRaw = rawFiles.map(::normalizeRaw)
        val rawKeys = normalizedRaw.map { it.root to it.path }
        if (rawKeys.toSet().size != rawKeys.size) throw RequestError("原始文件存在重复路径")

        val bindings = manifest.bindings.map(::normalizeBinding)
        if (bindings.map { it.contentKey }.toSet().size != bindings.size) {
            throw RequestError("manifest来源绑定存在重复内容")
        }
        bindings.forEach { binding ->
            val content = normalizedContents.firstOrNull { keyOf(it) == binding.contentKey }
            if (content == null) {
                throw RequestError("manifest来源绑定无法匹配提交内容")
            }
            validateSourceFormat(manifest.format, binding.source)
            when (val source = binding.source) {
                is Modpack2ContentSourceDto.RawSource -> {
                    val shadow = isManifestEntry(manifest.format, json, content)
                    if (content.targetPath != source.path || (!shadow && content.fileSize != source.size)) {
                        throw RequestError("Raw来源必须精确匹配内容目标路径和大小: ${source.path}")
                    }
                    val expectedRoot = when (content.side) {
                        calebxzau.rdi.common.model.ContentSide.Client -> Modpack2RawFileRoot.Client
                        calebxzau.rdi.common.model.ContentSide.Server -> Modpack2RawFileRoot.Server
                        calebxzau.rdi.common.model.ContentSide.Both -> Modpack2RawFileRoot.Shared
                    }
                    if (source.root != expectedRoot) {
                        throw RequestError("Raw来源根目录与内容side不匹配: ${source.path}")
                    }
                    val raw = normalizedRaw.firstOrNull {
                        it.root == source.root && it.path == source.path
                    } ?: throw RequestError("Raw来源没有对应上传文件: ${source.path}")
                    if (!raw.sha1.equals(source.sha1, ignoreCase = true) || raw.size != source.size) {
                        throw RequestError("Raw来源文件校验不匹配: ${source.path}")
                    }
                }
                else -> {
                    val targetPath = content.targetPath
                    if (targetPath != null && rawShadowsContent(normalizedRaw, content)) {
                        throw RequestError("manifest内容被Raw文件覆盖: $targetPath")
                    }
                }
            }
        }
        contentKeys.forEach { key ->
            if (bindings.none { it.contentKey == key }) {
                throw RequestError("提交内容缺少manifest来源绑定: ${key.projectId}/${key.fileId}")
            }
        }

        when (manifest.format) {
            Modpack2ManifestFormat.CurseForge -> validateCurseForge(
                json,
                bindings,
                normalizedContents,
                expectedMc,
                expectedLoader,
            )
            Modpack2ManifestFormat.Modrinth -> validateModrinth(
                json,
                bindings,
                normalizedContents,
                expectedMc,
                expectedLoader,
            )
        }
        return manifest.copy(
            manifestJson = manifest.manifestJson,
            bindings = bindings,
        )
    }

    private fun rawShadowsContent(rawFiles: List<Modpack2RawFile>, content: Modpack2Content): Boolean {
        val roots = when (content.side) {
            calebxzau.rdi.common.model.ContentSide.Client -> setOf(Modpack2RawFileRoot.Client, Modpack2RawFileRoot.Shared)
            calebxzau.rdi.common.model.ContentSide.Server -> setOf(Modpack2RawFileRoot.Server, Modpack2RawFileRoot.Shared)
            calebxzau.rdi.common.model.ContentSide.Both -> setOf(
                Modpack2RawFileRoot.Client,
                Modpack2RawFileRoot.Server,
                Modpack2RawFileRoot.Shared,
            )
        }
        return content.targetPath != null && rawFiles.any { it.root in roots && it.path == content.targetPath }
    }

    private fun isManifestEntry(
        format: Modpack2ManifestFormat,
        json: JsonElement,
        content: Modpack2Content,
    ): Boolean = when (format) {
        Modpack2ManifestFormat.CurseForge -> content.platform == ContentPlatform.CurseForge &&
            decode<CurseForgePackManifest>(json, "CurseForge manifest").files.any {
                it.projectId.toString() == content.projectId && it.fileId.toString() == content.fileId
            }
        Modpack2ManifestFormat.Modrinth -> content.platform == ContentPlatform.Modrinth &&
            decode<ModrinthModpackIndex>(json, "Modrinth manifest").files.any {
                it.env?.client != ModrinthModpackIndex.EnvSide.unsupported &&
                    normalizePath(it.path) == content.targetPath &&
                    it.hashes.sha1.equals(content.hash, ignoreCase = true)
            }
    }

    @JvmName("validateDto")
    fun validate(
        manifest: calebxzau.rdi.common.model.Modpack2VersionManifestDto,
        contents: List<Modpack2ContentDto>,
        rawFiles: List<Modpack2RawFile>,
        expectedMc: Int,
        expectedLoader: Modpack2Loader,
        actualManifestJson: String? = null,
    ): calebxzau.rdi.common.model.Modpack2VersionManifestDto = validate(
        manifest = manifest,
        contents = contents.map { it.toContent() },
        rawFiles = rawFiles,
        expectedMc = expectedMc,
        expectedLoader = expectedLoader,
        actualManifestJson = actualManifestJson,
    )

    private fun validateCurseForge(
        json: JsonElement,
        bindings: List<Modpack2ContentBindingDto>,
        contents: List<Modpack2Content>,
        expectedMc: Int,
        expectedLoader: Modpack2Loader,
    ) {
        val parsed = decode<CurseForgePackManifest>(json, "CurseForge manifest")
        val expectedVersion = McVersion.fromMinor(expectedMc.toString())?.mcVer
            ?: throw RequestError("不支持的MC版本")
        if (parsed.minecraft.version != expectedVersion) throw RequestError("manifest的MC版本与整合包不一致")
        val loaderPrefix = expectedLoader.name.lowercase() + "-"
        if (parsed.minecraft.modLoaders.none { it.id.lowercase().startsWith(loaderPrefix) }) {
            throw RequestError("manifest的ModLoader与整合包不一致")
        }
        val entries = parsed.files
        val entryKeys = entries.map { it.projectId.toString() to it.fileId.toString() }
        if (entryKeys.toSet().size != entryKeys.size) throw RequestError("CurseForge manifest存在重复文件")
        val sourceBindings = bindings.mapNotNull { binding ->
            val source = binding.source as? Modpack2ContentSourceDto.CurseForgeManifestEntry ?: return@mapNotNull null
            (source.projectId to source.fileId) to binding
        }.toMap()
        if (sourceBindings.size != bindings.count { it.source is Modpack2ContentSourceDto.CurseForgeManifestEntry }) {
            throw RequestError("CurseForge manifest来源存在重复文件")
        }
        entries.forEach { entry ->
            val key = entry.projectId.toString() to entry.fileId.toString()
            val binding = bindings.firstOrNull { candidate ->
                candidate.contentKey.platform == ContentPlatform.CurseForge &&
                    candidate.contentKey.projectId == key.first && candidate.contentKey.fileId == key.second
            }
            if (entry.required && binding == null) throw RequestError("CurseForge manifest缺少必选文件绑定")
            binding?.let {
                val content = contents.single { keyOf(it) == binding.contentKey }
                if (content.platform != ContentPlatform.CurseForge ||
                    content.projectId != key.first || content.fileId != key.second ||
                    content.required != entry.required
                ) throw RequestError("CurseForge manifest来源与提交内容不一致")
                if (binding.source !is Modpack2ContentSourceDto.RawSource &&
                    binding.source !is Modpack2ContentSourceDto.CurseForgeManifestEntry
                ) throw RequestError("CurseForge manifest来源格式不正确")
            }
        }
        sourceBindings.forEach { (key, binding) ->
            if (key !in entryKeys) throw RequestError("绑定了manifest之外的CurseForge文件")
            if (binding.contentKey.platform != ContentPlatform.CurseForge) {
                throw RequestError("CurseForge来源绑定的平台不正确")
            }
        }
    }

    private fun validateModrinth(
        json: JsonElement,
        bindings: List<Modpack2ContentBindingDto>,
        contents: List<Modpack2Content>,
        expectedMc: Int,
        expectedLoader: Modpack2Loader,
    ) {
        val parsed = decode<ModrinthModpackIndex>(json, "Modrinth manifest")
        if (!parsed.game.equals("minecraft", ignoreCase = true)) throw RequestError("Modrinth manifest不是Minecraft整合包")
        if (parsed.formatVersion != 1) throw RequestError("不支持的Modrinth manifest格式版本")
        val expectedVersion = McVersion.fromMinor(expectedMc.toString())?.mcVer
            ?: throw RequestError("不支持的MC版本")
        if (parsed.dependencies["minecraft"] != expectedVersion) throw RequestError("manifest的MC版本与整合包不一致")
        val loaderKeys = parsed.dependencies.keys.filter { ModLoader.from(it) != null }
        if (loaderKeys.size != 1 || ModLoader.from(loaderKeys.single()) != expectedLoader.toModLoader()) {
            throw RequestError("manifest的ModLoader与整合包不一致")
        }
        val entries = parsed.files.filter { it.env?.client != ModrinthModpackIndex.EnvSide.unsupported }
        val paths = entries.map { normalizePath(it.path) }
        if (paths.toSet().size != paths.size) throw RequestError("Modrinth manifest存在重复目标路径")
        val sourceBindings = bindings.mapNotNull { binding ->
            val source = binding.source as? Modpack2ContentSourceDto.ModrinthManifestEntry ?: return@mapNotNull null
            (normalizePath(source.path) to source.sha1.lowercase()) to binding
        }
        if (sourceBindings.map { it.first }.toSet().size != sourceBindings.size) {
            throw RequestError("Modrinth manifest来源存在重复文件")
        }
        entries.forEach { entry ->
            val path = normalizePath(entry.path)
            val sha1 = entry.hashes.sha1.lowercase()
            val required = entry.env?.client != ModrinthModpackIndex.EnvSide.optional
            val binding = bindings.firstOrNull { candidate ->
                candidate.contentKey.platform == ContentPlatform.Modrinth &&
                    candidate.contentKey.targetPath == path &&
                    candidate.contentKey.hash.equals(sha1, ignoreCase = true)
            }
            if (required && binding == null) throw RequestError("Modrinth manifest缺少必选文件绑定: $path")
            binding?.let {
                val content = contents.single { keyOf(it) == binding.contentKey }
                if (content.platform != ContentPlatform.Modrinth ||
                    content.targetPath != path || !content.hash.equals(sha1, ignoreCase = true) ||
                    content.required != required
                ) throw RequestError("Modrinth manifest来源与提交内容不一致: $path")
            }
        }
        sourceBindings.forEach { (identity, binding) ->
            if (entries.none { normalizePath(it.path) == identity.first && it.hashes.sha1.equals(identity.second, true) }) {
                throw RequestError("绑定了manifest之外的Modrinth文件")
            }
            if (binding.contentKey.platform != ContentPlatform.Modrinth) {
                throw RequestError("Modrinth来源绑定的平台不正确")
            }
        }
        bindings.filter { it.source is Modpack2ContentSourceDto.RawSource }.forEach { binding ->
            val content = contents.single { keyOf(it) == binding.contentKey }
            val isManifestShadow = entries.any {
                normalizePath(it.path) == content.targetPath &&
                    it.hashes.sha1.equals(content.hash, ignoreCase = true)
            }
            if (content.platform == ContentPlatform.Modrinth && !isManifestShadow &&
                !content.hash.equals((binding.source as Modpack2ContentSourceDto.RawSource).sha1, ignoreCase = true)
            ) throw RequestError("Modrinth Raw来源SHA-1与内容不匹配: ${content.targetPath}")
        }
    }

    private fun validateSourceFormat(format: Modpack2ManifestFormat, source: Modpack2ContentSourceDto) {
        when (source) {
            is Modpack2ContentSourceDto.CurseForgeManifestEntry -> if (format != Modpack2ManifestFormat.CurseForge) {
                throw RequestError("manifest来源格式混用")
            }
            is Modpack2ContentSourceDto.ModrinthManifestEntry -> if (format != Modpack2ManifestFormat.Modrinth) {
                throw RequestError("manifest来源格式混用")
            }
            is Modpack2ContentSourceDto.RawSource -> Unit
        }
    }

    private fun requireManifestJson(value: String): JsonElement {
        if (value.isEmpty() || value.toByteArray(StandardCharsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            throw RequestError("manifest大小无效")
        }
        return parseJson(value, "manifest")
    }

    private fun parseJson(value: String, label: String): JsonElement = try {
        strictJson.parseToJsonElement(value)
    } catch (error: Throwable) {
        throw RequestError("${label}格式错误", error)
    }

    private inline fun <reified T> decode(element: JsonElement, label: String): T = try {
        strictJson.decodeFromJsonElement(element)
    } catch (error: Throwable) {
        throw RequestError("${label}格式错误", error)
    }

    private fun normalizeBinding(binding: Modpack2ContentBindingDto): Modpack2ContentBindingDto {
        val key = binding.contentKey.copy(
            projectId = binding.contentKey.projectId.trim(),
            fileId = binding.contentKey.fileId.trim(),
            hash = binding.contentKey.hash.trim().lowercase(),
            targetPath = binding.contentKey.targetPath?.let(::normalizePath),
        )
        val source = when (val value = binding.source) {
            is Modpack2ContentSourceDto.CurseForgeManifestEntry -> value.copy(
                projectId = value.projectId.trim(), fileId = value.fileId.trim()
            )
            is Modpack2ContentSourceDto.ModrinthManifestEntry -> {
                val hash = value.sha1.trim().lowercase()
                if (!sha1Pattern.matches(hash)) throw RequestError("Modrinth来源SHA-1格式不正确")
                value.copy(path = normalizePath(value.path), sha1 = hash)
            }
            is Modpack2ContentSourceDto.RawSource -> {
                val hash = value.sha1.trim().lowercase()
                if (!sha1Pattern.matches(hash) || value.size < 0) throw RequestError("Raw来源校验字段无效")
                value.copy(path = normalizePath(value.path), sha1 = hash)
            }
        }
        return Modpack2ContentBindingDto(key, source)
    }

    private fun normalizeContent(content: Modpack2Content): Modpack2Content = content.copy(
        projectId = content.projectId.trim(),
        fileId = content.fileId.trim(),
        hash = content.hash.trim().lowercase(),
        targetPath = content.targetPath?.let(::normalizePath),
    )

    private fun normalizeRaw(file: Modpack2RawFile): Modpack2RawFile {
        val hash = file.sha1.trim().lowercase()
        if (!sha1Pattern.matches(hash) || file.size < 0) throw RequestError("原始文件校验字段无效")
        return file.copy(path = normalizePath(file.path), sha1 = hash)
    }

    private fun keyOf(content: Modpack2Content): Modpack2ContentKeyDto = Modpack2ContentKeyDto(
        platform = content.platform,
        type = content.type,
        projectId = content.projectId,
        fileId = content.fileId,
        hash = content.hash,
        targetPath = content.targetPath,
        side = content.side,
    )

    private fun Modpack2ContentDto.toContent() = Modpack2Content(
        versionId = java.util.UUID(0L, 0L), platform = platform, type = type,
        projectId = projectId, fileId = fileId, slug = slug, hash = hash,
        targetPath = targetPath, side = side, required = required, fileSize = fileSize,
    )

    private fun Modpack2Loader.toModLoader() = when (this) {
        Modpack2Loader.Forge -> ModLoader.forge
        Modpack2Loader.NeoForge -> ModLoader.neoforge
    }

    private fun normalizePath(value: String): String {
        val path = value.trim().replace('\\', '/')
        if (path.isBlank() || path.startsWith('/') || windowsAbsolutePath.matches(path) ||
            path.split('/').any {
                it.isBlank() || it == "." || it == ".." || it.endsWith('.') || it.endsWith(' ') ||
                    it.contains(':') || windowsReservedName.matches(it)
            }
        ) throw RequestError("manifest目标路径无效")
        return path
    }
}
