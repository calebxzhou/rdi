package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.*
import calebxzau.rdi.client.packproc.*
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.exception.ModpackError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.runInline
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.io.buffered
import org.bson.types.ObjectId
import java.io.File

private val lgr by Loggers

suspend fun uploadModpack(
    processor: ModpackProcessor,
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
    requireModpackUploadVersion(payload.mcVersion)
    onProgress("正在打包整合包...请等一两分钟")
    val uploadZip = try {
        processor.buildUploadArchive(
            payload = payload,
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
    processor: ModpackProcessor,
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
    requireModpackUploadVersion(payload.mcVersion)
    var uploadedModpackId: ObjectId? = updateModpackId
    var builtClientZip: File? = null
    val processedMods = processor.processUploadMods(mods)
    val uploadTask = Task2.Leaf("上传整合包") { ctx ->
        var errorMessage: String? = null
        var doneSummary: String? = null
        ctx.emit(Task2Progress("开始上传整合包", 0f))
        try {
            builtClientZip = processor.buildUploadArchive(
                payload = payload,
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
    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "dto",
                value = serdesJson.encodeToString(dto),
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
    var lastUpdate = lastProgressUpdate
    val multipartContent = MultiPartFormDataContent(
        formData {
            append(
                key = "mods",
                value = serdesJson.encodeToString(mods.toMutableList()),
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
