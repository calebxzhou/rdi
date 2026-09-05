package calebxzhou.rdi.master.service.modpack

import calebxzhou.rdi.common.archive.PackArchiveFormat
import calebxzhou.rdi.common.archive.detectArchiveFormat
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.service.ModpackModProcessor
import calebxzhou.rdi.common.service.validate
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.master.MODPACK_DATA_DIR
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.*
import calebxzhou.rdi.master.service.modpack.ModpackServiceKernel.dbcl
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import io.ktor.http.content.*
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.*
import kotlinx.io.buffered
import kotlinx.io.readByteArray

/** Upload staging and Legacy modpack/version creation with rollback semantics. */
object ModpackUploadService {
    private const val MODPACK_UPLOAD_VERSION_ERROR = "目前只支持上传MC1.20.1和MC1.21.1整合包"
    private const val MAX_MODPACK_PER_USER = 10
    private val lgr by calebxzau.rdi.common.logging.Loggers

    internal val modpackUploadTempStorage = ModpackUploadTempStorage(MODPACK_DATA_DIR.resolve(".upload-tmp"))
    internal val legacyModpackUploadTempStorage = ModpackUploadTempStorage(MODPACK_DATA_DIR)
    internal val parallelUploadService = ModpackParallelUploadService(
        MODPACK_DATA_DIR.resolve(".upload-tmp").resolve("sessions"),
        MAX_PACK_SIZE
    )

    internal fun deleteUploadTempFile(file: File, reason: String) {
        modpackUploadTempStorage.deleteTempFile(file)
            .onFailure { error -> lgr.warn(error) { "${reason}时删除上传暂存文件失败: ${file.absolutePath}" } }
    }

    internal suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.receiveUploadPayload(
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
                    is io.ktor.http.content.PartData.FormItem -> if (part.name == jsonFieldName) {
                        payloadDto = runCatching { serdesJson.decodeFromString<T>(part.value) }
                            .getOrElse { throw ParamError("$invalidJsonPrefix: ${it.message}") }
                    }
                    is io.ktor.http.content.PartData.FileItem -> if (part.name == "file") {
                        uploadedFile?.let { deleteUploadTempFile(it, "替换multipart文件") }
                        uploadedFile = receiveUploadFileToTemp(part.provider())
                    }
                    is io.ktor.http.content.PartData.BinaryItem -> if (part.name == "file") {
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

    private suspend fun receiveUploadFileToTemp(channel: io.ktor.utils.io.ByteReadChannel): File {
        val tempFile = modpackUploadTempStorage.createTempFile().getOrThrow()
        val buffer = ByteArray(8192)
        var total = 0L
        return try {
            Files.newOutputStream(
                tempFile.toPath(),
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE
            ).use { output ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read == 0) continue
                    total += read
                    if (total > MAX_PACK_SIZE) throw RequestError("整合包文件过大，最大允许2GiB")
                    output.write(buffer, 0, read)
                }
            }
            tempFile
        } catch (error: Throwable) {
            deleteUploadTempFile(tempFile, "接收上传流失败")
            throw error
        }
    }

    private fun receiveUploadFileToTemp(source: kotlinx.io.Source): File {
        val tempFile = modpackUploadTempStorage.createTempFile().getOrThrow()
        return try {
            val bytes = source.buffered().readByteArray()
            if (bytes.size > MAX_PACK_SIZE) throw RequestError("整合包文件过大，最大允许2GiB")
            tempFile.writeBytes(bytes)
            tempFile
        } catch (error: Throwable) {
            deleteUploadTempFile(tempFile, "接收上传数据失败")
            throw error
        }
    }

    internal fun cleanupStaleUploadsOnStartup() {
        var cleanedCount = 0
        listOf(
            ".upload-tmp" to modpackUploadTempStorage,
            "旧modpack目录" to legacyModpackUploadTempStorage
        ).forEach { (name, storage) ->
            storage.cleanupStaleFiles().onSuccess { cleanedCount += it }
                .onFailure { error -> lgr.warn(error) { "启动清理${name}遗留上传文件失败" } }
        }
        parallelUploadService.cleanupStaleSessions().onSuccess { cleanedCount += it }
            .onFailure { error -> lgr.warn(error) { "启动清理分片上传会话失败" } }
        lgr.info { "启动清理遗留整合包上传文件${cleanedCount}个" }
    }

    internal fun cleanupExpiredUploadSessions() {
        parallelUploadService.cleanupStaleSessions().onSuccess { count ->
            if (count > 0) lgr.info { "清理过期整合包上传会话${count}个" }
        }.onFailure { error -> lgr.warn(error) { "清理过期整合包上传会话失败" } }
    }

    private fun requireModpackUploadVersion(mcVersion: McVersion) {
        if (!mcVersion.supportsModpackUpload()) throw RequestError(MODPACK_UPLOAD_VERSION_ERROR)
    }

    suspend fun Modpack.CreateWithVersionDto.createWithVersion(player: RAccount, uploadFile: File) {
        requireModpackUploadVersion(mcVer)
        val normalizedVerName = ModpackQueryService.run { verName.validateVerName() }.getOrThrow()
        val normalizedCategories = Modpack.normalizeCategories(categories)
        if (!player.hasMsid) throw RequestError("必须有微软账号才能传包")
        if (dbcl.countDocuments(eq(Modpack::authorId.name, player._id)) >= MAX_MODPACK_PER_USER && !player.isDav) {
            throw RequestError("一个人最多传${MAX_MODPACK_PER_USER}个包")
        }
        if (dbcl.countDocuments(eq(Modpack::name.name, name)) > 0) {
            throw RequestError("同名整合包已存在")
        }
        validateRequiredModpackUploadMetadata(info, iconUrl, categories).getOrElse {
            throw RequestError(it.message ?: "整合包简介、图标和分类不能为空")
        }
        Modpack.OptionsDto(name, iconUrl, info, sourceUrl, normalizedCategories).validate()
        val modpack = Modpack(
            name = name,
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
                ModpackBuildService.enqueueVersionBuild(player, modpack, version)
            }.getOrElse { error ->
                cleanupUploadedVersionArtifacts(version)
                runCatching { dbcl.deleteOne(eq(Modpack::_id.name, modpack._id)) }
                runCatching { modpack.dir.deleteRecursivelyNoSymlink() }
                if (error.isDuplicateKeyError()) throw RequestError("同名整合包已存在")
                throw error
            }
        } finally { deleteUploadTempFile(uploadFile, "创建整合包结束") }
    }

    suspend fun ModpackContext.createVersion(verName: String, uploadFile: File, mods: MutableList<Mod>) {
        requireModpackUploadVersion(modpack.mcVer)
        try {
            val version = prepareVersionUpload(modpack, verName, uploadFile, mods)
            runCatching {
                val result = dbcl.updateOne(
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
                if (result.modifiedCount <= 0L) throw RequestError("版本 ${version.name} 已存在")
                ModpackBuildService.enqueueVersionBuild(player, modpack, version)
            }.getOrElse { error ->
                cleanupUploadedVersionArtifacts(version)
                runCatching {
                    dbcl.updateOne(
                        eq(Modpack::_id.name, modpack._id),
                        Updates.pull(
                            Modpack::versions.name,
                            eq(Modpack.Version::name.name, version.name)
                        )
                    )
                }
                throw error
            }
        } finally { deleteUploadTempFile(uploadFile, "创建整合包版本结束") }
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
        val target = when (uploadFile.detectArchiveFormat()) {
            PackArchiveFormat.TAR_ZST -> version.zstdPack
            PackArchiveFormat.ZIP -> throw RequestError("现在只支持tar.zst格式传包，请更新客户端后重试")
        }
        if (version.zip.exists()) version.zip.delete()
        if (version.zstdPack.exists()) version.zstdPack.delete()
        Files.move(uploadFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    internal fun cleanupUploadedVersionArtifacts(version: Modpack.Version) {
        version.zip.delete()
        version.zstdPack.delete()
        version.clientZip.delete()
        version.clientZstdPack.delete()
        ModpackBuildService.cleanupVersionBuildDirs(version)
    }

    private fun Throwable.isDuplicateKeyError() =
        this is MongoWriteException && ErrorCategory.fromErrorCode(code) == ErrorCategory.DUPLICATE_KEY
}

internal const val PART_SHA1_HEADER = "X-Part-SHA1"
internal val parallelUploadService get() = ModpackUploadService.parallelUploadService
internal suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.receiveUploadPayload(
    jsonFieldName: String,
    missingJsonError: String,
    invalidJsonPrefix: String
): Pair<File, T> = ModpackUploadService.run {
    this@receiveUploadPayload.receiveUploadPayload(jsonFieldName, missingJsonError, invalidJsonPrefix)
}
internal suspend fun io.ktor.server.routing.RoutingContext.uploadSessionId(): java.util.UUID =
    param("uploadId").let { runCatching { java.util.UUID.fromString(it) }.getOrElse { throw ParamError("上传会话ID无效") } }
