package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.sha1
import calebxzhou.mykotutils.std.toFixed
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.PackArchiveFormat
import calebxzhou.rdi.common.archive.forEachArchiveEntry
import calebxzhou.rdi.common.archive.listArchiveEntries
import calebxzhou.rdi.common.VALID_NAME_REGEX
import calebxzhou.rdi.common.archive.detectArchiveFormat
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModpackModProcessor
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.runInline
import calebxzhou.rdi.common.service.validate
import calebxzhou.rdi.common.service.ModService.modId
import calebxzhou.rdi.common.service.ModService.readNeoForgeConfig
import calebxzhou.rdi.common.util.ok
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.DL_MODS_CLIENT_DIR
import calebxzhou.rdi.master.GAME_LIBS_DIR
import calebxzhou.rdi.master.MODPACK_DATA_DIR
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.host.HostControlService.status
import calebxzhou.rdi.master.service.ModpackService.addVersionMod
import calebxzhou.rdi.master.service.ModpackService.addVersionMods
import calebxzhou.rdi.master.service.ModpackService.changeOptions
import calebxzhou.rdi.master.service.ModpackService.createVersion
import calebxzhou.rdi.master.service.ModpackService.createWithVersion
import calebxzhou.rdi.master.service.ModpackService.deleteModpack
import calebxzhou.rdi.master.service.ModpackService.deleteVersion
import calebxzhou.rdi.master.service.ModpackService.modpackGuardContext
import calebxzhou.rdi.master.service.ModpackService.rebuildVersion
import calebxzhou.rdi.master.service.ModpackService.removeVersionMod
import calebxzhou.rdi.master.service.ModpackService.removeVersionMods
import calebxzhou.rdi.master.service.ModpackService.replaceVersionMod
import calebxzhou.rdi.master.service.ModpackService.replaceVersionMods
import calebxzhou.rdi.master.service.ModpackService.requireAuthor
import calebxzhou.rdi.master.service.ModpackService.toBriefVo
import calebxzhou.rdi.master.service.ModpackService.toDetailVo
import calebxzhou.rdi.master.service.ModpackService.validateVerName
import calebxzhou.rdi.master.service.PlayerService.getPlayerNames
import calebxzhou.rdi.master.service.host.HostQueryService
import calebxzhou.rdi.master.service.host.dir
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.http.content.*
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import org.bson.Document
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile

val Modpack.dir
    get() = MODPACK_DATA_DIR.resolve(_id.str)
val Modpack.libsDir
    get() = GAME_LIBS_DIR
        .resolve("${mcVer.mcVer}-${modloader}")
val Modpack.Version.storageDir
    get() = MODPACK_DATA_DIR.resolve(modpackId.str)
fun Modpack.Version.tempDir(buildId: String): File =
    storageDir.resolve(".build-$name-$buildId")
val Modpack.Version.zip
    get() = storageDir.resolve("${name}.zip")
val Modpack.Version.zstdPack
    get() = storageDir.resolve("${name}.tar.zst")
val Modpack.Version.fullPackFile
    get() = zstdPack.takeIf(File::exists) ?: zip
val Modpack.Version.clientZip
    get() = storageDir.resolve("${name}-client.zip")
val Modpack.Version.clientZstdPack
    get() = storageDir.resolve("${name}-client.tar.zst")
val Modpack.Version.clientPackFile
    get() = clientZstdPack.takeIf(File::exists) ?: clientZip

const val CLIENT_ONLY_MARK_PREFIX = "C" + "$$" + "_"
val MAX_PACK_SIZE = 2L * 1024 * 1024 * 1024
private val modpackUploadTempStorage = ModpackUploadTempStorage(MODPACK_DATA_DIR.resolve(".upload-tmp"))
private val legacyModpackUploadTempStorage = ModpackUploadTempStorage(MODPACK_DATA_DIR)
private val parallelUploadService = ModpackParallelUploadService(
    MODPACK_DATA_DIR.resolve(".upload-tmp").resolve("sessions"),
    MAX_PACK_SIZE
)
private val uploadLgr by Loggers

private fun deleteUploadTempFile(file: File, reason: String) {
    modpackUploadTempStorage.deleteTempFile(file)
        .onFailure { error -> uploadLgr.warn(error) { "${reason}时删除上传暂存文件失败: ${file.absolutePath}" } }
}

private suspend inline fun <reified T> ApplicationCall.receiveUploadPayload(
    jsonFieldName: String,
    missingJsonError: String,
    invalidJsonPrefix: String
): Pair<File, T> {
    val multipart = receiveMultipart(formFieldLimit = MAX_PACK_SIZE)
    var uploadedFile: File? = null
    var payloadDto: T? = null
    try {
        while (true) {
            val part = multipart.readPart() ?: break
            when (part) {
                is PartData.FormItem -> if (part.name == jsonFieldName) {
                    payloadDto = runCatching { serdesJson.decodeFromString<T>(part.value) }
                        .getOrElse { throw ParamError("$invalidJsonPrefix: ${it.message}") }
                }

                is PartData.FileItem -> if (part.name == "file") {
                    uploadedFile?.let { deleteUploadTempFile(it, "替换multipart文件") }
                    uploadedFile = receiveUploadFileToTemp(part.provider())
                }

                is PartData.BinaryItem -> if (part.name == "file") {
                    uploadedFile?.let { deleteUploadTempFile(it, "替换multipart文件") }
                    uploadedFile = receiveUploadFileToTemp(part.provider())
                }

                else -> {}
            }
            part.dispose()
        }
        val fileBytes = uploadedFile ?: throw ParamError("缺少文件")
        val dto = payloadDto ?: throw ParamError(missingJsonError)
        uploadedFile = null
        return fileBytes to dto
    } catch (error: Throwable) {
        uploadedFile?.let { deleteUploadTempFile(it, "接收multipart失败") }
        throw error
    }
}

private suspend fun receiveUploadFileToTemp(channel: ByteReadChannel): File {
    val tempFile = modpackUploadTempStorage.createTempFile().getOrThrow()
    val buffer = ByteArray(8192)
    var total = 0L
    return try {
        Files.newOutputStream(
            tempFile.toPath(),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                total += read
                if (total > MAX_PACK_SIZE) {
                    throw RequestError("整合包文件过大，最大允许2GiB")
                }
                output.write(buffer, 0, read)
            }
        }
        tempFile
    } catch (error: Throwable) {
        deleteUploadTempFile(tempFile, "接收上传流失败")
        throw error
    }
}

private fun receiveUploadFileToTemp(source: Source): File {
    val tempFile = modpackUploadTempStorage.createTempFile().getOrThrow()
    return try {
        val bytes = source.buffered().readByteArray()
        if (bytes.size > MAX_PACK_SIZE) {
            throw RequestError("整合包文件过大，最大允许2GiB")
        }
        tempFile.writeBytes(bytes)
        tempFile
    } catch (error: Throwable) {
        deleteUploadTempFile(tempFile, "接收上传数据失败")
        throw error
    }
}

fun Route.modpackRoutes() {

    route("/modpack") {
        get {
            response(data = ModpackService.listAll())
        }
        get("/list-simple") {
            val hasIconOnly = call.paramNull("hasIconOnly")?.trim()?.toBooleanStrictOrNull() ?: false
            response(data = ModpackService.listSimple(hasIconOnly))
        }
        post("/infos") {
            response(data = ModpackService.toModpackVoList(ModpackService.listByIds(call.receive<List<ObjectId>>())))
        }
        post {
            val (payload, dto) = call.receiveUploadPayload<Modpack.CreateWithVersionDto>(
                jsonFieldName = "dto",
                missingJsonError = "缺少dto",
                invalidJsonPrefix = "格式错误"
            )
            dto.createWithVersion(call.player(), payload)
            ok()
        }
        route("/upload-sessions") {
            post {
                response(
                    data = parallelUploadService.create(
                        call.uid,
                        call.receive<ModpackUploadSessionCreateDto>()
                    ).getOrThrow()
                )
            }
            route("/{uploadId}") {
                get {
                    response(data = parallelUploadService.status(call.uid, uploadSessionId()).getOrThrow())
                }
                delete {
                    parallelUploadService.cancel(call.uid, uploadSessionId()).getOrThrow()
                    ok()
                }
                put("/parts/{index}") {
                    val partSha1 = call.request.headers[PART_SHA1_HEADER]
                        ?: throw ParamError("缺少分片SHA-1")
                    val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    parallelUploadService.uploadPart(
                        ownerId = call.uid,
                        id = uploadSessionId(),
                        index = param("index").toIntOrNull() ?: throw ParamError("分片序号无效"),
                        declaredLength = contentLength,
                        expectedSha1 = partSha1,
                        source = call.receiveChannel()
                    ).getOrThrow()
                    ok()
                }
                post("/complete") {
                    response(data = parallelUploadService.complete(call.uid, uploadSessionId()).getOrThrow())
                }
            }
        }
        post("/from-upload") {
            val dto = call.receive<ModpackCreateFromUploadDto>()
            val player = call.player()
            parallelUploadService.withReadyUpload(player._id, dto.uploadId) { uploadFile ->
                dto.modpack.createWithVersion(player, uploadFile)
            }.getOrThrow()
            ok()
        }
        get("/my") {
            val mods = ModpackService.listByAuthor(call.uid)
            response(data = mods)
        }
        route("/{modpackId}") {
            //旧版接口 保持兼容
            get {
                response(data = call.modpackGuardContext().modpack.toDetailVo())
            }
            get("/brief") {
                response(data = call.modpackGuardContext().modpack.toBriefVo())
            }
            get("/detail") {
                response(data = call.modpackGuardContext().modpack.toDetailVo())
            }
            get("/name") {
                response(data = call.modpackGuardContext().modpack.name)
            }
            post("/play") {
                ModpackService.incrementPlayCount(idParam("modpackId"))
                ok()
            }
            delete {
                call.modpackGuardContext().requireAuthor().deleteModpack()
                ok()
            }
            put("/options") {
                val ctx = call.modpackGuardContext().requireAuthor()
                ctx.changeOptions(call.receive<Modpack.OptionsDto>())
                ok()
            }
            route("/version/{verName}") {
                get {
                    call.modpackGuardContext().versionNull?.let { response(data = it) }
                        ?: throw RequestError("无此版本")
                }
                get("/client") {
                    val ctx = call.modpackGuardContext()
                    val file = ctx.version.clientPackFile
                    DownloadQuotaService.reserve(ctx.player._id, file.length())
                    call.respondFile(file)
                }
                get("/client/hash") {
                    call.modpackGuardContext().version.clientPackFile.let { response(data = it.sha1) }
                }
                route("/mods"){
                    get{
                        call.modpackGuardContext().version.mods.let { response(data = it) }
                    }
                    post{
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.addVersionMod(call.receive<Mod>())
                        ok()
                    }
                    post("/batch") {
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.addVersionMods(call.receive<List<Mod>>())
                        ok()
                    }
                    put{
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.replaceVersionMod(
                            projectId = param("projectId"),
                            fileId = param("fileId"),
                            newMod = call.receive<Mod>()
                        )
                        ok()
                    }
                    put("/batch") {
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.replaceVersionMods(call.receive<List<ModBatchReplaceItem>>())
                        ok()
                    }
                    delete {
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.removeVersionMod(
                            projectId = param("projectId"),
                            fileId = param("fileId")
                        )
                        ok()
                    }
                    delete("/batch") {
                        val ctx = call.modpackGuardContext().requireAuthor()
                        ctx.removeVersionMods(call.receive<List<ModRef>>())
                        ok()
                    }
                }
                delete {
                    call.modpackGuardContext().requireAuthor().deleteVersion()
                    ok()

                }
                post("/rebuild") {
                    call.modpackGuardContext().requireAuthor().rebuildVersion()

                    ok()

                }
                post("/from-upload") {
                    val ctx = call.modpackGuardContext()
                    ctx.requireAuthor()
                    val verName = param("verName").validateVerName().getOrThrow()
                    if (ctx.modpack.versions.any { it.name.equals(verName, ignoreCase = true) }) {
                        throw RequestError("版本 $verName 已存在")
                    }
                    val dto = call.receive<ModpackVersionCreateFromUploadDto>()
                    parallelUploadService.withReadyUpload(call.uid, dto.uploadId) { uploadFile ->
                        ctx.createVersion(verName, uploadFile, dto.mods)
                    }.getOrThrow()
                    ok()
                }
                post {
                    val ctx = call.modpackGuardContext()
                    ctx.requireAuthor()
                    val verName = param("verName").validateVerName().getOrThrow()

                    // Check if version already exists
                    if (ctx.modpack.versions.any { it.name.equals(verName, ignoreCase = true) }) {
                        throw RequestError("版本 $verName 已存在")
                    }

                    val (payload, modList) = call.receiveUploadPayload<MutableList<Mod>>(
                        jsonFieldName = "mods",
                        missingJsonError = "缺少mods列表",
                        invalidJsonPrefix = "mods格式错误"
                    )

                    ctx.createVersion(verName, payload, modList)
                    ok()

                }
            }


        }

        get("/search/{modpackName}") {
            val name = call.parameters["modpackName"]?.trim()
                ?: throw ParamError("缺少参数: modpackName")
            val modpacks = ModpackService.searchByName(name)
            response(data = modpacks)
        }
        get("/search") {
            response(data = ModpackService.search(call))
        }


    }
}

private const val PART_SHA1_HEADER = "X-Part-SHA1"

private suspend fun RoutingContext.uploadSessionId(): java.util.UUID =
    param("uploadId").let { value ->
        runCatching { java.util.UUID.fromString(value) }
            .getOrElse { throw ParamError("上传会话ID无效") }
    }

class ModpackContext(
    val player: RAccount,
    val modpack: Modpack,
    val versionNull: Modpack.Version?
) {
    val version get() = versionNull ?: throw ParamError("缺少版本信息")
}

object ModpackService {
    private val lgr by Loggers
    private const val MODPACK_UPLOAD_VERSION_ERROR = "目前只支持上传MC1.20.1和MC1.21.1整合包"
    private const val MAX_MODPACK_PER_USER = 10
    private const val DEFAULT_SEARCH_LIMIT = 24
    private const val MAX_SEARCH_LIMIT = 60
    private const val MAX_INFO_BATCH_SIZE = 100
    private val STEP_PROGRESS_REGEX = Regex("""^Step\s+(\d+)/(\d+)""")
    private val realDbcl = DB.getCollection<Modpack>("modpack")
    internal var testDbcl: MongoCollection<Modpack>? = null
    val dbcl: MongoCollection<Modpack>
        get() = testDbcl ?: realDbcl

    fun cleanupStaleUploadsOnStartup() {
        var cleanedCount = 0
        listOf(
            ".upload-tmp" to modpackUploadTempStorage,
            "旧modpack目录" to legacyModpackUploadTempStorage
        ).forEach { (name, storage) ->
            storage.cleanupStaleFiles()
                .onSuccess { cleanedCount += it }
                .onFailure { error -> lgr.warn(error) { "启动清理${name}遗留上传文件失败" } }
        }
        parallelUploadService.cleanupStaleSessions()
            .onSuccess { cleanedCount += it }
            .onFailure { error -> lgr.warn(error) { "启动清理分片上传会话失败" } }
        lgr.info { "启动清理遗留整合包上传文件${cleanedCount}个" }
    }

    fun cleanupExpiredUploadSessions() {
        parallelUploadService.cleanupStaleSessions()
            .onSuccess { cleanedCount ->
                if (cleanedCount > 0) lgr.info { "清理过期整合包上传会话${cleanedCount}个" }
            }
            .onFailure { error -> lgr.warn(error) { "清理过期整合包上传会话失败" } }
    }

    //private val clientNeedDirs = listOf("config", "mods", "defaultconfigs", "kubejs", "global_packs", "resourcepacks")
    private val disallowedClientPaths = setOf("shaderpacks")
    private val hostSkippedAssetExtensions = setOf("ogg", "jpg", "png")

    private fun versionBuildTaskKey(modpackId: ObjectId, versionName: String): String =
        "server-modpack-build:${modpackId.toHexString()}:$versionName"

    private fun requireModpackUploadVersion(mcVersion: McVersion) {
        if (!mcVersion.supportsModpackUpload()) {
            throw RequestError(MODPACK_UPLOAD_VERSION_ERROR)
        }
    }


    fun ModpackContext.requireAuthor(): ModpackContext {
        if (modpack.authorId != player._id && !player.isDav) throw RequestError("不是你的整合包")
        return this
    }

    private fun ModRef.normalizedVersionModRef(): ModRef = copy(
        projectId = projectId.trim(),
        fileId = fileId.trim()
    )

    private fun Mod.versionModRef(): ModRef = ModRef(projectId, fileId).normalizedVersionModRef()

    private fun Mod.normalizeVersionMutationMod(): Mod = copy(
        platform = platform.trim().lowercase(),
        projectId = projectId.trim(),
        slug = slug.trim(),
        fileId = fileId.trim(),
        hash = hash.trim(),
        downloadUrls = downloadUrls.map(String::trim).filter(String::isNotBlank)
    )

    private fun Mod.requireVersionMutationMod(): Mod {
        val normalized = normalizeVersionMutationMod()
        if (normalized.projectId.isBlank() || normalized.fileId.isBlank()) {
            throw RequestError("Mod的projectId和fileId不能为空")
        }
        return normalized
    }

    private fun ModRef.requireVersionMutationRef(): ModRef {
        val normalized = normalizedVersionModRef()
        if (normalized.projectId.isBlank() || normalized.fileId.isBlank()) {
            throw RequestError("projectId和fileId不能为空")
        }
        return normalized
    }

    private fun Mod.matchesVersionModRef(ref: ModRef): Boolean = versionModRef() == ref.normalizedVersionModRef()

    private fun MutableList<Mod>.findVersionModIndex(ref: ModRef): Int {
        val normalizedRef = ref.normalizedVersionModRef()
        return indexOfFirst { it.matchesVersionModRef(normalizedRef) }
    }

    private fun MutableList<Mod>.findVersionModIndexOrThrow(ref: ModRef): Int {
        return findVersionModIndex(ref)
            .takeIf { it >= 0 }
            ?: throw RequestError("版本内无此Mod")
    }

    private fun Collection<ModRef>.requireDistinctVersionMutationRefs(fieldName: String): List<ModRef> {
        val normalizedRefs = map { it.requireVersionMutationRef() }
        val duplicates = normalizedRefs.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw RequestError("$fieldName 里有重复Mod")
        }
        return normalizedRefs
    }

    private fun Collection<Mod>.requireDistinctVersionMutationMods(fieldName: String): List<Mod> {
        val normalizedMods = map { it.requireVersionMutationMod() }
        val duplicates = normalizedMods.groupingBy { it.versionModRef() }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw RequestError("$fieldName 里有重复Mod")
        }
        return normalizedMods
    }

    private fun ModpackContext.ensureVersionModsEditable() {
        if (version.status == Modpack.Status.WAIT || version.status == Modpack.Status.BUILDING) {
            throw RequestError("版本${version.name}正在构建中，暂时不能修改Mod列表")
        }
    }

    private suspend fun ModpackContext.mutateVersionMods(mutator: (MutableList<Mod>) -> Unit) {
        ensureVersionModsEditable()
        val updatedMods = version.mods
            .map { it.copy(downloadUrls = it.downloadUrls.toList()) }
            .toMutableList()
        mutator(updatedMods)
        updatedMods.sortBy { it.slug.lowercase() }
        val updatedVersion = version.copy(
            mods = ModpackModProcessor.processMods(updatedMods),
            time = System.currentTimeMillis(),
            status = Modpack.Status.WAIT
        )
        updatedVersion.mods.sortBy { it.slug.lowercase() }
        dbcl.updateOne(
            eq(Modpack::_id.name, modpack._id),
            Updates.combine(
                Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::mods.name}", updatedVersion.mods),
                Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::time.name}", updatedVersion.time),
                Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::status.name}", updatedVersion.status)
            ),
            UpdateOptions().arrayFilters(
                listOf(
                    Document("elem.name", version.name)
                )
            )
        )
        enqueueVersionBuild(player, modpack, updatedVersion)
    }

    suspend fun ModpackContext.addVersionMod(newMod: Mod) {
        addVersionMods(listOf(newMod))
    }

    suspend fun ModpackContext.addVersionMods(newMods: List<Mod>) {
        if (newMods.isEmpty()) throw RequestError("缺少要添加的Mod")
        val normalizedMods = newMods.requireDistinctVersionMutationMods("新增Mod")
        mutateVersionMods { mods ->
            val existingRefs = mods.map { it.versionModRef() }.toSet()
            normalizedMods.forEach { newMod ->
                if (newMod.versionModRef() in existingRefs) {
                    throw RequestError("版本内已存在Mod ${newMod.displaySlugOrProject}")
                }
            }
            mods += normalizedMods
        }
    }

    suspend fun ModpackContext.replaceVersionMod(projectId: String, fileId: String, newMod: Mod) {
        replaceVersionMods(listOf(ModBatchReplaceItem(projectId, fileId, newMod)))
    }

    suspend fun ModpackContext.replaceVersionMods(items: List<ModBatchReplaceItem>) {
        if (items.isEmpty()) throw RequestError("缺少要修改的Mod")
        val normalizedItems = items.map {
            ModBatchReplaceItem(
                projectId = it.projectId.trim(),
                fileId = it.fileId.trim(),
                mod = it.mod.requireVersionMutationMod()
            )
        }
        normalizedItems.map { ModRef(it.projectId, it.fileId) }.requireDistinctVersionMutationRefs("待修改Mod")
        mutateVersionMods { mods ->
            val targetIndices = normalizedItems.map { item ->
                mods.findVersionModIndexOrThrow(ModRef(item.projectId, item.fileId))
            }
            if (targetIndices.size != targetIndices.toSet().size) {
                throw RequestError("待修改Mod里有重复目标")
            }
            val replacedMods = mods.toMutableList()
            normalizedItems.forEachIndexed { index, item ->
                replacedMods[targetIndices[index]] = item.mod
            }
            val duplicates = replacedMods.groupingBy { it.versionModRef() }.eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) {
                throw RequestError("批量修改后版本内存在重复Mod")
            }
            mods.clear()
            mods += replacedMods
        }
    }

    suspend fun ModpackContext.removeVersionMod(projectId: String, fileId: String) {
        removeVersionMods(listOf(ModRef(projectId, fileId)))
    }

    suspend fun ModpackContext.removeVersionMods(refs: List<ModRef>) {
        if (refs.isEmpty()) throw RequestError("缺少要删除的Mod")
        val normalizedRefs = refs.requireDistinctVersionMutationRefs("待删除Mod")
        mutateVersionMods { mods ->
            val targetIndices = normalizedRefs.map { ref -> mods.findVersionModIndexOrThrow(ref) }
                .sortedDescending()
            targetIndices.forEach(mods::removeAt)
        }
    }

    suspend fun ModpackContext.changeOptions(payload: Modpack.OptionsDto) {
        payload.validate()
        val normalizedCategories = payload.categories?.let(Modpack::normalizeCategories) ?: modpack.categories
        val update = Updates.combine(
            Updates.set(Modpack::name.name, payload.name ?: modpack.name),
            Updates.set(Modpack::iconUrl.name, payload.iconUrl),
            Updates.set(Modpack::info.name, payload.info),
            Updates.set(Modpack::sourceUrl.name, payload.sourceUrl),
            Updates.set(Modpack::categories.name, normalizedCategories)
        )
        dbcl.updateOne(eq("_id", modpack._id), update)
    }

    fun Modpack.isMcVer(ver: McVersion): Boolean {
        return mcVer == ver
    }

    suspend fun ApplicationCall.modpackGuardContext(): ModpackContext {
        val requesterId = uid
        val player = PlayerService.getById(requesterId) ?: throw RequestError("用户不存在")
        val modpack = ModpackService.dbcl.find(eq("_id", idPathParam("modpackId"))).firstOrNull()
            ?: throw RequestError("整合包不存在")
        val verName = pathParamNull("verName")?.trim()?.takeIf { it.isNotEmpty() }
        val version = verName?.let { name ->
            modpack.versions.firstOrNull { it.name == name }
        }
        return ModpackContext(player, modpack, version)
    }

    suspend fun listByAuthor(uid: ObjectId): List<Modpack> = dbcl.find(eq("authorId", uid)).toList()

    private suspend fun hasModpack(name: String): Boolean = dbcl.countDocuments(eq(Modpack::name.name, name)) > 0

    private suspend fun getModpackCount(uid: ObjectId): Int =
        dbcl.countDocuments(eq(Modpack::authorId.name, uid)).toInt()

    suspend fun getById(id: ObjectId): Modpack? = dbcl.find(eq("_id", id)).firstOrNull()

    suspend fun incrementPlayCount(modpackId: ObjectId) {
        dbcl.updateOne(
            eq(Modpack::_id.name, modpackId),
            Updates.inc(Modpack::playCount.name, 1)
        )
    }

    suspend fun searchByName(name: String): List<Modpack> {
        if (name.isBlank()) return emptyList()
        //Uses MongoDB's regex filter with case-insensitive flag ("i")
        return dbcl.find(regex(Modpack::name.name, name, "i")).toList()
    }

    suspend fun search(call: ApplicationCall): Modpack.SearchResultVo {
        val keyword = call.paramNull("q")?.trim().orEmpty()
        val category = call.paramNull("category")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { catStr ->
                runCatching { Modpack.Category.valueOf(catStr) }.getOrElse {
                    throw ParamError("category无效: $catStr")
                }
            }
        val mcVer = call.paramNull("mcVer")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let {
                McVersion.from(it) ?: throw ParamError("mcVer无效")
            }
        val sort = call.paramNull("sort")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.uppercase()
            ?.let {
                runCatching { Modpack.SearchSort.valueOf(it) }.getOrElse {
                    throw ParamError("sort无效: $it")
                }
            } ?: Modpack.SearchSort.RELEVANCE
        val onlyMine = call.paramNull("mine")?.trim()?.toBooleanStrictOrNull() ?: false
        val limit = call.paramNull("limit")?.toIntOrNull()
            ?.coerceIn(1, MAX_SEARCH_LIMIT)
            ?: DEFAULT_SEARCH_LIMIT
        val offset = call.paramNull("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        val filters = buildList {
            if (onlyMine) {
                add(eq(Modpack::authorId.name, call.uid))
            }
            if (keyword.isNotBlank()) {
                add(or(
                    regex(Modpack::name.name, keyword, "i"),
                    regex(Modpack::info.name, keyword, "i")
                ))
            }
            category?.let {
                add(eq(Modpack::categories.name, it))
            }
            mcVer?.let {
                add(eq(Modpack::mcVer.name, it))
            }
        }

        val filter = when (filters.size) {
            0 -> Document()
            1 -> filters.first()
            else -> and(filters)
        }

        val sortDef = when (sort) {
            Modpack.SearchSort.RELEVANCE -> when {
                keyword.isNotBlank() -> Sorts.orderBy(
                    Sorts.descending(Modpack::playCount.name),
                    Sorts.descending("${Modpack::versions.name}.${Modpack.Version::time.name}"),
                    Sorts.ascending(Modpack::name.name)
                )

                else -> Sorts.descending("${Modpack::versions.name}.${Modpack.Version::time.name}")
            }

            Modpack.SearchSort.UPDATED -> Sorts.descending("${Modpack::versions.name}.${Modpack.Version::time.name}")
            Modpack.SearchSort.POPULAR -> Sorts.descending(Modpack::playCount.name)
            //Modpack.SearchSort.NAME -> Sorts.ascending(Modpack::name.name)
        }

        val total = dbcl.countDocuments(filter).toInt()
        val modpacks = dbcl.find(filter)
            .sort(sortDef)
            .skip(offset)
            .limit(limit)
            .toList()
        val items = toModpackVoList(modpacks)

        return Modpack.SearchResultVo(
            items = items,
            total = total,
            offset = offset,
            limit = limit,
            hasMore = offset + items.size < total
        )
    }

    suspend fun listAll(): List<Modpack.BriefVo> {
        val modpacks = dbcl.find().toList()
        return toModpackVoList(modpacks)
    }

    suspend fun listSimple(hasIconOnly: Boolean = false): List<Modpack.ListSimpleVo> {
        val filter = if (hasIconOnly) {
            ne(Modpack::iconUrl.name, null)
        } else {
            Document()
        }
        return dbcl.find(filter)
            .sort(Sorts.descending("${Modpack::versions.name}.${Modpack.Version::time.name}"))
            .toList()
            .map { modpack ->
                Modpack.ListSimpleVo(
                    id = modpack._id,
                    name = modpack.name,
                    iconUrl = modpack.iconUrl
                )
            }
    }

    suspend fun listByIds(ids: List<ObjectId>): List<Modpack> {
        val orderedIds = normalizeInfoBatchIds(ids)
        if (orderedIds.isEmpty()) return emptyList()
        return orderModpacksByIds(orderedIds, dbcl.find(`in`("_id", orderedIds)).toList())
    }

    internal fun normalizeInfoBatchIds(ids: List<ObjectId>): List<ObjectId> {
        if (ids.size > MAX_INFO_BATCH_SIZE) {
            throw ParamError("批量查询整合包最多支持${MAX_INFO_BATCH_SIZE}个ID")
        }
        return ids.distinct()
    }

    internal fun orderModpacksByIds(ids: List<ObjectId>, modpacks: List<Modpack>): List<Modpack> {
        val modpacksById = modpacks.associateBy { it._id }
        return ids.mapNotNull(modpacksById::get)
    }

    fun String.validateVerName(): Result<String> {
        val trimmed = this.trim()
        val normalized = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.drop(1).trimStart()
        } else {
            trimmed
        }

        if (normalized.isBlank()) {
            throw RequestError("版本名不能为空")
        }

        if (!normalized.matches(VALID_NAME_REGEX)) {
            throw RequestError("版本名只能包含字母 数字 汉字")
        }
        return ok(normalized)
    }

    suspend fun toModpackVoList(modpacks: List<Modpack>): List<Modpack.BriefVo> {
        if (modpacks.isEmpty()) return emptyList()

        val authorNames = modpacks.map { it.authorId }.getPlayerNames()

        return modpacks.map { pack ->
            Modpack.BriefVo(
                pack._id,
                name = pack.name,
                authorId = pack.authorId,
                authorName = authorNames[pack.authorId] ?: "未知作者",
                modCount = pack.versions.maxOfOrNull { it.mods.size } ?: 0,
                fileSize = pack.versions.lastOrNull()?.totalSize ?: 0L,
                playCount = pack.playCount,
                lastUpdatedTime = pack.versions.lastOrNull()?.time ?: 0L,
                icon = pack.iconUrl,
                mcVer = pack.mcVer,
                modloader = pack.modloader,
                info = pack.info,
                categories = pack.categories
            )
        }
    }

    suspend fun Modpack.toBriefVo(): Modpack.BriefVo {
        val authorName = PlayerService.getName(authorId) ?: "未知作者"
        return Modpack.BriefVo(
            _id,
            name = name,
            authorId = authorId,
            authorName = authorName,
            mcVer = mcVer,
            modCount = versions.maxOfOrNull { it.mods.size } ?: 0,
            fileSize = versions.lastOrNull()?.totalSize ?: 0L,
            playCount = playCount,
            lastUpdatedTime = versions.lastOrNull()?.time ?: 0L,
            icon = iconUrl,
            modloader = modloader,
            info = info,
            categories = categories
        )
    }

    //单个整合包的详细信息
    suspend fun Modpack.toDetailVo(): Modpack.DetailVo {
        val authorName = PlayerService.getName(authorId) ?: "未知作者"
        return Modpack.DetailVo(
            _id = _id,
            name = name,
            authorId = authorId,
            authorName = authorName,
            modCount = versions.maxOfOrNull { it.mods.size } ?: 0,
            playCount = playCount,
            sourceUrl = sourceUrl,
            icon = iconUrl,
            info = info,
            modloader = modloader,
            mcVer = mcVer,
            categories = categories,
            versions = versions
        )
    }

    suspend fun Modpack.CreateWithVersionDto.createWithVersion(player: RAccount, uploadFile: File) {
        requireModpackUploadVersion(mcVer)
        val normalizedVerName = verName.validateVerName().getOrThrow()
        val normalizedCategories = Modpack.normalizeCategories(categories)
        if (!player.hasMsid) throw RequestError("必须有微软账号才能传包")
        if (getModpackCount(player._id) >= MAX_MODPACK_PER_USER && !player.isDav) {
            throw RequestError("一个人最多传${MAX_MODPACK_PER_USER}个包")
        }
        if (hasModpack(name)) throw RequestError("同名整合包已存在")
        validateRequiredModpackUploadMetadata(
            info = info,
            iconUrl = iconUrl,
            categories = categories,
        ).getOrElse { error ->
            throw RequestError(error.message ?: "整合包简介、图标和分类不能为空")
        }
        Modpack.OptionsDto(name, iconUrl, info, sourceUrl, normalizedCategories).validate()
        val modpack = Modpack(
            name = this.name,
            authorId = player._id,
            iconUrl = iconUrl?.trim()?.ifBlank { null },
            info = info?.trim()?.ifBlank { null },
            mcVer = mcVer,
            modloader = modLoader,
            sourceUrl = sourceUrl?.trim()?.ifBlank { null },
            playCount = 0,
            categories = normalizedCategories
        )
        modpack.dir.mkdirs()
        try {
            val version = prepareVersionUpload(modpack, normalizedVerName, uploadFile, mods)
            runCatching {
                modpack.versions += version
                dbcl.insertOne(modpack)
                enqueueVersionBuild(player, modpack, version)
            }.getOrElse { error ->
                cleanupUploadedVersionArtifacts(version)
                runCatching { dbcl.deleteOne(eq(Modpack::_id.name, modpack._id)) }
                runCatching { modpack.dir.deleteRecursivelyNoSymlink() }
                if (error.isDuplicateKeyError()) {
                    throw RequestError("同名整合包已存在")
                }
                throw error
            }
        } finally {
            deleteUploadTempFile(uploadFile, "创建整合包结束")
        }
    }

    fun ModpackContext.rebuildVersion() {
        ServerTaskManager.submit(
            task = createVersionBuildTask(
                player = player,
                modpack = modpack,
                version = version,
                reprocessMods = true
            ),
            dedupeKey = versionBuildTaskKey(modpack._id, version.name)
        )
    }

    suspend fun ModpackContext.createVersion(verName: String, uploadFile: File, mods: MutableList<Mod>) {
        requireModpackUploadVersion(modpack.mcVer)
        try {
            val version = prepareVersionUpload(modpack, verName, uploadFile, mods)
            runCatching {
                // Add version to modpack
                val updateResult = dbcl.updateOne(
                    and(
                        eq(Modpack::_id.name, modpack._id),
                        not(
                            elemMatch(
                                Modpack::versions.name,
                                eq(Modpack.Version::name.name, version.name)
                            )
                        )
                    ),
                    Updates.push(Modpack::versions.name, version)
                )
                if (updateResult.modifiedCount <= 0L) {
                    throw RequestError("版本 ${version.name} 已存在")
                }
                enqueueVersionBuild(player, modpack, version)
            }.getOrElse { error ->
                cleanupUploadedVersionArtifacts(version)
                runCatching {
                    dbcl.updateOne(
                        eq(Modpack::_id.name, modpack._id),
                        Updates.pull(Modpack::versions.name, eq(Modpack.Version::name.name, version.name))
                    )
                }
                throw error
            }
        } finally {
            deleteUploadTempFile(uploadFile, "创建整合包版本结束")
        }
    }

    private fun prepareVersionUpload(
        modpack: Modpack,
        verName: String,
        uploadFile: File,
        mods: MutableList<Mod>
    ): Modpack.Version {
        mods.sortBy { it.slug.lowercase() }
        val version = Modpack.Version(
            modpackId = modpack._id,
            name = verName,
            changelog = "新上传",
            status = Modpack.Status.WAIT,
            mods = ModpackModProcessor.processMods(mods),
            time = System.currentTimeMillis()
        )
        try {
            moveUploadedArchiveToVersion(uploadFile, version)
            version.mods.sortBy { it.slug.lowercase() }
        } catch (error: Throwable) {
            cleanupUploadedVersionArtifacts(version)
            throw error
        }
        return version
    }

    private fun moveUploadedArchiveToVersion(uploadFile: File, version: Modpack.Version) {
        version.storageDir.mkdirs()
        val targetFile = when (uploadFile.detectArchiveFormat()) {
            PackArchiveFormat.TAR_ZST -> version.zstdPack
            PackArchiveFormat.ZIP -> throw RequestError("现在只支持tar.zst格式传包，请更新客户端后重试")
        }
        if (version.zip.exists()) version.zip.delete()
        if (version.zstdPack.exists()) version.zstdPack.delete()
        Files.move(
            uploadFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun cleanupUploadedVersionArtifacts(version: Modpack.Version) {
        version.zip.delete()
        version.zstdPack.delete()
        version.clientZip.delete()
        version.clientZstdPack.delete()
        cleanupVersionBuildDirs(version)
    }

    private fun Throwable.isDuplicateKeyError(): Boolean {
        val writeError = this as? MongoWriteException ?: return false
        return ErrorCategory.fromErrorCode(writeError.code) == ErrorCategory.DUPLICATE_KEY
    }

    suspend fun recoverUnfinishedVersionBuildsOnStartup() {
        val affected = mutableListOf<Pair<ObjectId, String>>()
        dbcl.find(
            or(
                elemMatch(Modpack::versions.name, eq(Modpack.Version::status.name, Modpack.Status.WAIT)),
                elemMatch(Modpack::versions.name, eq(Modpack.Version::status.name, Modpack.Status.BUILDING))
            )
        ).toList().forEach { modpack ->
            modpack.versions
                .filter { it.status == Modpack.Status.WAIT || it.status == Modpack.Status.BUILDING }
                .forEach { version ->
                    affected += modpack._id to version.name
                }
        }
        affected.forEach { (modpackId, versionName) ->
            dbcl.updateOne(
                eq(Modpack::_id.name, modpackId),
                Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::status.name}", Modpack.Status.FAIL),
                UpdateOptions().arrayFilters(listOf(Document("elem.name", versionName)))
            )
        }
        if (affected.isNotEmpty()) {
            lgr.warn { "启动恢复：已将${affected.size}个卡在WAIT/BUILDING的整合包版本标记为FAIL" }
        }
    }

    private fun enqueueVersionBuild(player: RAccount, modpack: Modpack, version: Modpack.Version) {
        ServerTaskManager.submit(
            task = createVersionBuildTask(
                player = player,
                modpack = modpack,
                version = version,
                reprocessMods = false
            ),
            dedupeKey = versionBuildTaskKey(modpack._id, version.name)
        )
    }

    private fun createVersionBuildTask(
        player: RAccount,
        modpack: Modpack,
        version: Modpack.Version,
        reprocessMods: Boolean
    ): Task2 = Task2.Sequence(
        title = buildString {
            append(if (reprocessMods) "重构整合包版本" else "构建整合包版本")
            append(" ")
            append(modpack.name)
            append(" V")
            append(version.name)
        },
        children = buildList {
            var mailId: ObjectId? = null
            val failureHandled = AtomicBoolean(false)

            suspend fun updateMailProgress(message: String) {
                lgr.info { message }
                mailId?.let { MailService.changeMail(it, newContent = message) }
            }

            suspend fun handleBuildFailure(error: Throwable) {
                if (!failureHandled.compareAndSet(false, true)) return
                lgr.error(error) { "${if (reprocessMods) "重构" else "构建"}整合包 ${modpack._id}:${version.name} 失败" }
                version.setStatus(Modpack.Status.FAIL)
                mailId?.let {
                    MailService.changeMail(
                        it,
                        if (reprocessMods) "重构整合包失败：${modpack.name}" else "整合包构建失败：${modpack.name}",
                        "无法构建整合包，错误原因：${error.message}"
                    )
                }
            }

            fun guardedLeaf(title: String, action: suspend (Task2Context) -> Unit): Task2.Leaf = Task2.Leaf(title) { ctx ->
                runCatching {
                    action(ctx)
                }.onFailure { error ->
                    handleBuildFailure(error)
                    throw error
                }
            }

            add(
                guardedLeaf("准备构建") { ctx ->
                    val mail = MailService.sendSystemMail(
                        player._id,
                        if (reprocessMods) "重构整合包：${modpack.name} V${version.name}" else "整合包${version.name}构建中",
                        "开始构建整合包 ${modpack.name} 版本${version.name}\n"
                    )
                    mailId = mail._id
                    version.setStatus(Modpack.Status.BUILDING)
                    version.setTotalSize(version.fullPackFile.length())
                    val msg = "开始构建 ${modpack.name} V${version.name}"
                    updateMailProgress(msg)
                    ctx.emit(LoadProgress.Phase(msg))
                }
            )

            if (reprocessMods) {
                add(
                    guardedLeaf("重新处理版本Mod信息") { ctx ->
                        val msg = "重新处理版本Mod信息"
                        updateMailProgress(msg)
                        ctx.emit(LoadProgress.Phase(msg))
                        val processedMods = ModpackModProcessor.processMods(version.mods)
                        version.mods.clear()
                        version.mods += processedMods
                        version.mods.sortBy { it.slug.lowercase() }
                        dbcl.updateOne(
                            eq(Modpack::_id.name, modpack._id),
                            Updates.set(
                                "${Modpack::versions.name}.$[elem].${Modpack.Version::mods.name}",
                                version.mods
                            ),
                            UpdateOptions().arrayFilters(
                                listOf(
                                    Document("elem.name", version.name)
                                )
                            )
                        )
                        ctx.emit(LoadProgress.Percent("版本Mod信息重处理完成", 1f))
                    }
                )
            }

            add(
                guardedLeaf("校验整合包归档") { ctx ->
                    val entries = listArchiveEntries(version.fullPackFile)
                    val archiveRoot = resolveServerInstallArchiveRoot(entries.map { it.path })
                    val msg = "校验整合包归档(${archiveRoot.dirName}/)"
                    updateMailProgress(msg)
                    ctx.emit(LoadProgress.Phase(msg))
                    val total = entries.size.coerceAtLeast(1)
                    entries.forEachIndexed { index, entry ->
                        extractServerInstallRelativePath(entry.path, archiveRoot)
                        val fraction = (index + 1).toFloat() / total
                        ctx.emit(
                            LoadProgress.Percent(
                                "校验整合包归档(${archiveRoot.dirName}/ ${index + 1}/$total)",
                                fraction
                            )
                        )
                    }
                    ctx.emit(LoadProgress.Percent("整合包归档校验完成(${archiveRoot.dirName}/)", 1f))
                }
            )

            val serverMods = version.mods.filter {
                it.side != Mod.Side.CLIENT &&
                    it.side != Mod.Side.UNKNOWN &&
                    !it.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)
            }
            val clientMods = version.mods.filter { it.side == Mod.Side.CLIENT }

            add(
                Task2.Group(
                    title = "下载Mod",
                    children = listOf(
                        Task2.Sequence(
                            title = "下载服务端Mod",
                            children = listOf(
                                guardedLeaf("准备下载服务端Mod") { ctx ->
                                    val msg = "开始下载服务端Mod，共${serverMods.size}个"
                                    updateMailProgress(msg)
                                    ctx.emit(LoadProgress.Phase(msg))
                                },
                                ModService.downloadModsTask2(serverMods)
                                    .withFailureHandler(::handleBuildFailure)
                            )
                        ),
                        ClientModCacheService(DL_MODS_CLIENT_DIR).downloadTask(clientMods)
                    )
                )
            )

            add(
                guardedLeaf("构建客户端版") { ctx ->
                    val msg = "构建客户端版"
                    updateMailProgress(msg)
                    ctx.emit(LoadProgress.Phase(msg))
                    buildClientPack(version)
                    ctx.emit(LoadProgress.Percent("客户端版本构建完成", 1f))
                }
            )

            add(
                guardedLeaf("迁移归档到tar.zst") { ctx ->
                    if (!version.zstdPack.exists() && version.zip.exists()) {
                        val msg = "迁移整合包归档到tar.zst"
                        updateMailProgress(msg)
                        ctx.emit(LoadProgress.Phase(msg))
                        upgradeFullPackArchive(version)
                    } else {
                        ctx.emit(LoadProgress.Percent("归档已是tar.zst，无需迁移", 1f))
                    }
                }
            )

            add(
                guardedLeaf("完成构建") { ctx ->
                    val msg = "整合包构建完成"
                    updateMailProgress(msg)
                    version.setStatus(Modpack.Status.OK)
                    mailId?.let {
                        MailService.changeMail(
                            it,
                            if (reprocessMods) "重构整合包成功：${modpack.name}" else "整合包构建成功：${modpack.name}",
                            "${modpack.name} V${version.name} 已构建完成"
                        )
                    }
                    ctx.emit(LoadProgress.Percent(msg, 1f))
                }
            )
        }
    )

    private fun Task2.withFailureHandler(
        onFailure: suspend (Throwable) -> Unit
    ): Task2 = when (this) {
        is Task2.Leaf -> copy(action = { ctx ->
            runCatching { action(ctx) }.getOrElse { error ->
                onFailure(error)
                throw error
            }
        })

        is Task2.Group -> copy(children = children.map { it.withFailureHandler(onFailure) })
        is Task2.Sequence -> copy(children = children.map { it.withFailureHandler(onFailure) })
    }

    suspend fun Modpack.getVersion(verName: String): Modpack.Version? {
        return versions.find { it.name == verName }
    }

    suspend fun getVersion(modpackId: ObjectId, verName: String): Modpack.Version? {
        return dbcl.find(
            and(
                eq("_id", modpackId),
                elemMatch(Modpack::versions.name, eq(Modpack.Version::name.name, verName))
            )
        )
            .projection(
                Projections
                    .elemMatch(
                        Modpack::versions.name,
                        eq(Modpack.Version::name.name, verName)
                    )
            )
            .first().versions.firstOrNull()
    }

    suspend fun Modpack.installToHost(verName: String, host: Host, onProgress: (String) -> Unit) {
        val version = getVersion(verName)
            ?: throw RequestError("整合包版本不存在: $verName")
        if (version.status != Modpack.Status.OK) {
            throw RequestError("此版本未准备好或构建失败")
        }
        val hostDir = host.dir.canonicalFile.apply { mkdirs() }
        if (!version.fullPackFile.exists()) {
            throw RequestError("版本压缩文件不存在: ${version.fullPackFile}")
        }
        onProgress("开始安装整合包..")
        unzipOverrides(
            version.fullPackFile,
            hostDir,
            includeClientOnlyMarkedMods = false,
            skipHostAssetFiles = true
        )
        hostDir.resolve("mods").listFiles()
            ?.filter { it.isFile && it.name.startsWith(CLIENT_ONLY_MARK_PREFIX) && it.extension.equals("jar", true) }
            ?.forEach { runCatching { it.delete() } }
        //删掉i18n
        val i18nUpdateMod = hostDir.resolve("mods").resolve("I18nUpdateMod.jar")
        if (i18nUpdateMod.exists()) {
            i18nUpdateMod.delete()
        }
        cleanupDisabledInstalledMods(host, version, hostDir.resolve("mods"))

        libsDir.canonicalFile.also {
            if (!it.exists() || !it.isDirectory) {
                throw RequestError("整合包依赖目录缺失: $it")
            }
        }
        onProgress("安装成功")
    }

    private fun createOrReplaceSymlink(link: Path, target: Path) {
        runCatching {
            Files.deleteIfExists(link)
        }
        link.parent?.let { Files.createDirectories(it) }
        try {
            Files.createSymbolicLink(link, target)
        } catch (err: Exception) {
            throw RequestError("创建软链接失败: ${link.toAbsolutePath()}")
        }
    }

    private fun unzipOverrides(
        archiveFile: File,
        targetDir: File,
        includeClientOnlyMarkedMods: Boolean = true,
        skipHostAssetFiles: Boolean = false
    ) {
        val archiveRoot = resolveServerInstallArchiveRoot(archiveFile)
        val versionDirPath = targetDir.toPath()
        forEachArchiveEntry(archiveFile) { entry ->
            val relativePath = extractServerInstallRelativePath(entry.path, archiveRoot) ?: return@forEachArchiveEntry
            if (!includeClientOnlyMarkedMods && isClientOnlyMarkedModPath(relativePath)) {
                return@forEachArchiveEntry
            }
            if (shouldSkipHostClientOnlyJar(relativePath)) {
                return@forEachArchiveEntry
            }
            if (skipHostAssetFiles && shouldSkipHostAssetFile(relativePath)) {
                return@forEachArchiveEntry
            }
            val resolvedPath = versionDirPath.resolve(relativePath).normalize()
            if (!resolvedPath.startsWith(versionDirPath)) {
                throw RequestError("非法文件路径: ${entry.path}")
            }

            if (entry.isDirectory) {
                Files.createDirectories(resolvedPath)
            } else {
                resolvedPath.parent?.let { Files.createDirectories(it) }
                Files.newOutputStream(
                    resolvedPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                ).use { output ->
                    output.write(entry.bytes ?: byteArrayOf())
                }
            }
        }
    }

    private fun shouldSkipHostAssetFile(relativePath: String): Boolean {
        val extension = relativePath.substringAfterLast('.', "").lowercase()
        return extension in hostSkippedAssetExtensions
    }

    private fun cleanupDisabledInstalledMods(host: Host, version: Modpack.Version, modsDir: File) {
        if (!modsDir.exists() || !modsDir.isDirectory || host.disabledMods.isEmpty()) return
        val disabledBaseMods = version.mods
            .filter {
                it.side != Mod.Side.CLIENT &&
                    it.side != Mod.Side.UNKNOWN &&
                    !it.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)
            }
            .filter { versionMod -> host.disabledMods.any { sameMod(it, versionMod) } }
        if (disabledBaseMods.isEmpty()) return

        val disabledFileNames = disabledBaseMods.map { it.fileName.lowercase() }.toSet()
        val disabledSlugs = disabledBaseMods.map { it.slug.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val disabledHashes = disabledBaseMods.map { it.hash.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val disabledModIds = disabledBaseMods.mapNotNull { disabledMod ->
            DL_MOD_DIR.resolve(disabledMod.fileName)
                .takeIf(File::exists)
                ?.let(::readPrimaryJarModId)
        }.toSet()

        modsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("jar", true) }
            ?.forEach { file ->
                val lowerName = file.name.lowercase()
                val directNameMatch = lowerName in disabledFileNames ||
                    disabledSlugs.any(lowerName::contains) ||
                    disabledHashes.any(lowerName::contains)
                val modIdMatch = readPrimaryJarModId(file)?.let { it in disabledModIds } ?: false
                if (!directNameMatch && !modIdMatch) return@forEach
                runCatching { file.delete() }
                    .onSuccess { deleted ->
                        if (deleted) {
                            lgr.info { "Host ${host._id} 删除已禁用整合包Mod文件: ${file.name}" }
                        }
                    }
                    .onFailure { err ->
                        lgr.warn { "Host ${host._id} 删除已禁用整合包Mod文件失败 ${file.name}: ${err.message}" }
                    }
            }
    }

    private fun readPrimaryJarModId(file: File): String? {
        return runCatching {
            JarFile(file).use { jar ->
                jar.readNeoForgeConfig()
                    ?.modId
                    ?.trim()
                    ?.lowercase()
                    ?.ifBlank { null }
            }
        }.getOrNull()
    }
    suspend fun Modpack.buildVersion(version: Modpack.Version, onProgress: (String) -> Unit) {
        if (!version.fullPackFile.exists()) {
            throw RequestError("版本压缩文件不存在 请重新上传")
        }
        // if (version.hostsUsing().isNotEmpty()) throw RequestError("有主机正在使用此版本，无法重构")
        version.setTotalSize(version.fullPackFile.length())
        val buildId = ObjectId().toHexString()
        val buildDir = createVersionBuildDir(version, buildId)
        try {
            onProgress("解压整合包文件..")
            unzipOverrides(version.fullPackFile, buildDir, includeClientOnlyMarkedMods = false)
            val serverMods = version.mods.filter {
                it.side != Mod.Side.CLIENT &&
                    it.side != Mod.Side.UNKNOWN &&
                    !it.fileName.startsWith(CLIENT_ONLY_MARK_PREFIX)
            }
            val clientMods = version.mods.filter { it.side == Mod.Side.CLIENT }
            Task2.Group(
                title = "下载Mod",
                children = listOf(
                    ModService.downloadModsTask2(serverMods),
                    ClientModCacheService(DL_MODS_CLIENT_DIR).downloadTask(clientMods)
                )
            ).runInline(
                Task2Context{ progress ->
                    val msg = progress.fraction?.let { frac ->
                        val pct = (frac * 100f).toFixed(2)
                        "${progress.message} $pct%"
                    } ?: progress.message
                    onProgress("mod下载中：$msg")
                }
            )
            onProgress("所有mod下载完成 开始安装。。${serverMods.size}个mod")
            onProgress("构建客户端版。。")
            buildClientPack(version)
            onProgress("客户端版本构建完成")
            if (!version.zstdPack.exists() && version.zip.exists()) {
                onProgress("迁移整合包归档到tar.zst..")
                upgradeFullPackArchive(version)
            }
            onProgress("整合包构建完成！")
            version.setStatus(Modpack.Status.OK)

        } catch (e: Exception) {
            version.setStatus(Modpack.Status.FAIL)
            throw e
        } finally {
            if (buildDir.exists()) {
                buildDir.deleteRecursivelyNoSymlink()
            }
        }
    }

    private fun buildClientPack(version: Modpack.Version) {
        val sourceArchive = version.fullPackFile
        if (!sourceArchive.exists()) return
        val clientArchive = version.clientZstdPack
        clientArchive.parentFile?.mkdirs()
        if (clientArchive.exists()) clientArchive.delete()
        if (version.clientZip.exists()) version.clientZip.delete()

        var entriesCopied = 0

        TarZstArchiveWriter(clientArchive).use { output ->
            val addedDirs = mutableSetOf<String>()
            forEachArchiveEntry(sourceArchive) { entry ->
                val relative = extractClientPackRelativePath(entry.path) ?: return@forEachArchiveEntry
                val relativeLower = relative.lowercase()
                if (disallowedClientPaths.any { relativeLower.startsWith(it) }) {
                    return@forEachArchiveEntry
                }
                if (relativeLower.endsWith(".mca")) {
                    return@forEachArchiveEntry
                }
                if (entry.isDirectory) {
                    if (addDirectoryEntry(relative, output, addedDirs)) {
                        entriesCopied++
                    }
                    return@forEachArchiveEntry
                }
                ensureArchiveParents(relative, output, addedDirs)
                output.addFile(relative, entry.bytes ?: byteArrayOf(), entry.time)
                entriesCopied++
            }
        }

        if (entriesCopied == 0) {
            clientArchive.delete()
        }
    }

    private fun upgradeFullPackArchive(version: Modpack.Version) {
        val sourceZip = version.zip
        if (!sourceZip.exists() || version.zstdPack.exists()) return
        version.zstdPack.parentFile?.mkdirs()
        val tempArchive = version.storageDir.resolve("${version.name}.tar.zst.tmp")
        if (tempArchive.exists()) tempArchive.delete()
        TarZstArchiveWriter(tempArchive).use { output ->
            val addedDirs = mutableSetOf<String>()
            forEachArchiveEntry(sourceZip) { entry ->
                if (entry.isDirectory) {
                    addDirectoryEntry(entry.path, output, addedDirs)
                } else {
                    ensureArchiveParents(entry.path, output, addedDirs)
                    output.addFile(entry.path, entry.bytes ?: byteArrayOf(), entry.time)
                }
            }
        }
        if (!tempArchive.exists() || tempArchive.length() <= 0L) {
            tempArchive.delete()
            throw RequestError("迁移整合包归档失败")
        }
        if (version.zstdPack.exists()) version.zstdPack.delete()
        tempArchive.renameTo(version.zstdPack)
        sourceZip.delete()
    }

    private fun addDirectoryEntry(
        rawPath: String,
        output: TarZstArchiveWriter,
        addedDirs: MutableSet<String>
    ): Boolean {
        val sanitized = rawPath.trim('/').ifEmpty { return false }
        ensureArchiveParents(sanitized, output, addedDirs)
        val dirEntry = "$sanitized/"
        if (addedDirs.add(dirEntry)) {
            output.addDirectory(sanitized)
            return true
        }
        return false
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
            if (addedDirs.add(dirEntry)) {
                output.addDirectory(current)
            }
        }
    }

    suspend fun Modpack.Version.setStatus(status: Modpack.Status) {
        dbcl.updateOne(
            eq(Modpack::_id.name, modpackId),
            Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::status.name}", status),
            UpdateOptions().arrayFilters(
                listOf(
                    Document("elem.name", name)
                )
            )
        )
    }

    suspend fun Modpack.Version.setTotalSize(size: Long) {
        dbcl.updateOne(
            eq(Modpack::_id.name, modpackId),
            Updates.set("${Modpack::versions.name}.$[elem].${Modpack.Version::totalSize.name}", size),
            UpdateOptions().arrayFilters(
                listOf(
                    Document("elem.name", name)
                )
            )
        )
    }

    suspend fun Modpack.Version.hostsUsing(): List<Host> {
        return HostQueryService.findByModpackVersion(modpackId, name).filter { it.status != HostStatus.STOPPED }
    }

    suspend fun Modpack.hostsUsing(): List<Host> {
        return HostQueryService.findByModpack(_id).filter { it.status != HostStatus.STOPPED }
    }

    suspend fun ModpackContext.deleteVersion() {
        if (versionNull == null) throw RequestError("无此版本")
        val hostsUsing = version.hostsUsing()
        if (hostsUsing.isNotEmpty()) throw RequestError("以下主机用了此版本整合包，且正在运行，无法删除：${hostsUsing.map { it.name }}")
        versionNull.zip.delete()
        versionNull.zstdPack.delete()
        versionNull.clientZip.delete()
        versionNull.clientZstdPack.delete()
        cleanupVersionBuildDirs(versionNull)
        dbcl.updateOne(
            eq("_id", modpack._id),
            Updates.pull(Modpack::versions.name, eq(Modpack.Version::name.name, versionNull.name))
        )
    }

    suspend fun ModpackContext.deleteModpack() {
        val hostsUsing = modpack.hostsUsing()
        if (hostsUsing.isNotEmpty()) throw RequestError("以下主机用了此整合包，且正在运行，无法删除：${hostsUsing.map { it.name }}")
        modpack.dir.deleteRecursivelyNoSymlink()
        dbcl.deleteOne(eq("_id", modpack._id))
    }

    private fun resolveServerInstallArchiveRoot(archiveFile: File): ServerInstallArchiveRoot {
        return resolveServerInstallArchiveRoot(listArchiveEntries(archiveFile).map { it.path })
    }

    private fun resolveServerInstallArchiveRoot(entryPaths: List<String>): ServerInstallArchiveRoot {
        val hasServerDir = entryPaths.any { entryPath ->
            hasArchiveRootDir(entryPath, ServerInstallArchiveRoot.SERVER.dirName)
        }
        return if (hasServerDir) {
            ServerInstallArchiveRoot.SERVER
        } else {
            ServerInstallArchiveRoot.OVERRIDES
        }
    }

    private fun extractServerInstallRelativePath(
        entryName: String,
        archiveRoot: ServerInstallArchiveRoot
    ): String? = extractArchiveRootRelativePath(entryName, archiveRoot.dirName)

    private fun hasArchiveRootDir(entryName: String, rootDirName: String): Boolean {
        val normalized = entryName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return false
        val prefix = "$rootDirName/"
        return normalized.startsWith(prefix, ignoreCase = true)
    }

    private fun extractArchiveRootRelativePath(entryName: String, rootDirName: String): String? {
        if (entryName.isBlank()) return null
        val normalized = entryName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        val prefix = "$rootDirName/"
        if (!normalized.startsWith(prefix, ignoreCase = true)) return null
        return normalized.substring(prefix.length).takeIf { it.isNotBlank() }
    }

    fun extractOverridesRelativePath(entryName: String): String? {
        if (entryName.isBlank()) return null
        val normalized = entryName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        val overridesIndex = segments.indexOf("overrides")
        if (overridesIndex == -1) return null
        val relativeSegments = segments.drop(overridesIndex + 1)
        if (relativeSegments.isEmpty()) return null
        return relativeSegments.joinToString("/")
    }

    private fun extractClientPackRelativePath(entryName: String): String? {
        extractOverridesRelativePath(entryName)?.let { return it }
        if (entryName.isBlank()) return null
        val normalized = entryName.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        return normalized.takeIf {
            it.equals("gtnh", ignoreCase = true) || it.startsWith("gtnh/", ignoreCase = true)
        }
    }

    private fun isClientOnlyMarkedModPath(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        if (!normalized.startsWith("mods/")) return false
        val fileName = normalized.substringAfterLast('/')
        return fileName.startsWith(CLIENT_ONLY_MARK_PREFIX) && fileName.endsWith(".jar", ignoreCase = true)
    }

    private fun shouldSkipHostClientOnlyJar(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        if (!normalized.startsWith("mods/")) return false
        val fileName = normalized.substringAfterLast('/').lowercase()
        return fileName.endsWith(".jar") && fileName.contains("rgp-client")
    }

    private enum class ServerInstallArchiveRoot(val dirName: String) {
        SERVER("server"),
        OVERRIDES("overrides")
    }

    private fun createVersionBuildDir(version: Modpack.Version, buildId: String): File {
        version.storageDir.mkdirs()
        val buildDir = version.tempDir(buildId)
        if (buildDir.exists()) {
            buildDir.deleteRecursivelyNoSymlink()
        }
        buildDir.mkdirs()
        return buildDir
    }

    private fun cleanupVersionBuildDirs(version: Modpack.Version) {
        val prefix = ".build-${version.name}-"
        version.storageDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(prefix) }
            ?.forEach { it.deleteRecursivelyNoSymlink() }
    }

}
