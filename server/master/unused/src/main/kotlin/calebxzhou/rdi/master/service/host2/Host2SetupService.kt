package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.logging.Loggers
import calebxzau.rdi.server.modpack.Modpack2ArchiveIO
import calebxzau.rdi.server.modpack.Modpack2MergeRoot
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.Host2ErrorCode
import calebxzhou.rdi.common.model.HostStatus
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.PackSource
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Host2SetupService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val hostService: Host2Service,
    private val packSourceService: Host2PackSourceService,
    private val downloadService: Host2ModDownloadService,
) {
    private val lgr by Loggers
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<UUID, Job>()
    private val cleanupJobs = ConcurrentHashMap<UUID, Job>()

    suspend fun startInstall(player: RAccount, id: UUID) {
        val host = hostService.requireOwner(player, id)
        if (host.activeContentRevision != 0L) throw RequestError("房间已经安装整合包")
        val resolved = packSourceService.resolveExact(host.packSource).getOrElse { error ->
            database.transaction {
                repository.findByIdForUpdate(id)?.takeIf { it.activeContentRevision == 0L }?.let {
                    repository.updatePackStatus(id, Host2PackStatus.Fail)
                }
            }
            throw RequestError("整合包来源暂时不可用", error, Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        if (!resolved.sourceArchive.isFile || !resolved.clientArchive.isFile) {
            database.transaction {
                repository.findByIdForUpdate(id)?.takeIf { it.activeContentRevision == 0L }?.let {
                    repository.updatePackStatus(id, Host2PackStatus.Fail)
                }
            }
            throw RequestError("整合包归档暂时不可用", errorCode = Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        val operation = hostService.withMutation(id) {
            database.transaction {
                val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                if (locked.activeContentRevision != 0L) throw RequestError("房间已经安装整合包")
                repository.updatePackStatus(id, Host2PackStatus.Busy)
                repository.insertOperation(
                    NewHost2Operation(
                        hostId = id,
                        kind = Host2OperationKind.InitialInstall,
                        phase = PHASE_PREPARE,
                        targetSource = resolved.source,
                        targetRevision = 1,
                    )
                )
            }
        }
        jobs[id] = scope.launch {
            install(host, resolved, operation)
        }.also { job -> job.invokeOnCompletion { jobs.remove(id, job) } }
    }

    suspend fun switchPack(player: RAccount, id: UUID, dto: calebxzhou.rdi.common.model.Host2.PackSourceDto) {
        val host = hostService.requireOwner(player, id)
        if (Host2RuntimeService.status(id) != HostStatus.STOPPED) throw RequestError("请先停止房间")
        val currentRevision = host.pendingContentRevision ?: host.activeContentRevision
        if (dto.expectedRevision != currentRevision) {
            throw Host2RevisionConflict(currentRevision)
        }
        if (host.activeContentRevision > 0L) {
            packSourceService.resolveExact(host.packSource).getOrElse {
                throw RequestError("当前整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
            }
        }
        val resolved = packSourceService.resolveForCreate(dto.packSource)
        if (host.activeContentRevision == 0L) {
            hostService.withMutation(id) {
                database.transaction {
                    val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                    if (locked.activeContentRevision != 0L) throw RequestError("房间状态已改变")
                    repository.updatePackSource(id, resolved.source)
                    repository.updatePackStatus(id, Host2PackStatus.Fail)
                }
            }
            startInstall(player, id)
            return
        }
        val operation = hostService.withMutation(id) {
            database.transaction {
                val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                if (locked.packStatus != Host2PackStatus.Ok) throw RequestError("房间正在执行其他操作")
                repository.updatePackStatus(id, Host2PackStatus.Busy)
                repository.insertOperation(
                    NewHost2Operation(
                        hostId = id,
                        kind = Host2OperationKind.Switch,
                        phase = PHASE_PREPARE,
                        targetSource = resolved.source,
                        targetRevision = maxOf(locked.activeContentRevision, locked.pendingContentRevision ?: 0) + 1,
                    )
                )
            }
        }
        jobs[id] = scope.launch {
            switch(host, resolved, operation)
        }.also { job -> job.invokeOnCompletion { jobs.remove(id, job) } }
    }

    suspend fun cancel(id: UUID) {
        jobs.remove(id)?.cancelAndJoin()
        val state = database.transaction {
            repository.findById(id) to repository.activeOperation(id)
        }
        val (host, operation) = state
        if (operation != null && operation.kind != Host2OperationKind.Delete) {
            operation.stagingPath?.let(::deleteInternalDirectory)
            if (operation.phase == PHASE_CLEANUP) {
                operation.backupPath?.let(::deleteInternalDirectory)
            } else {
                operation.backupPath?.let(::safeInternalDirectory)?.takeIf(File::exists)?.let { backup ->
                    val target = HOST2_DIR.resolve(id.toString())
                    target.deleteRecursivelyNoSymlink()
                    Files.move(backup.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
                if (host?.activeContentRevision == 0L) {
                    HOST2_DIR.resolve(id.toString()).deleteRecursivelyNoSymlink()
                }
            }
        }
        database.transaction {
            val locked = repository.findByIdForUpdate(id) ?: return@transaction
            repository.activeOperation(id)?.let { active ->
                if (active.kind != Host2OperationKind.Delete) repository.deleteOperation(active.id)
            }
            repository.updatePackStatus(
                id,
                if (locked.activeContentRevision == 0L) Host2PackStatus.Fail else Host2PackStatus.Ok,
            )
        }
    }

    suspend fun recover() {
        val tombstones = database.transaction { repository.deleteTombstones() }
            .associateBy(Host2DeleteTombstoneRecord::operationId)
        val operations = database.transaction { repository.activeOperations() }
        operations.forEach { operation ->
            if (operation.kind == Host2OperationKind.Delete) {
                if (operation.id !in tombstones) recoverUncommittedDelete(operation)
                return@forEach
            }
            if (operation.phase == PHASE_CLEANUP) {
                runCatching {
                    operation.stagingPath?.let(::deleteInternalDirectory)
                    operation.backupPath?.let(::deleteInternalDirectory)
                    database.transaction { repository.deleteOperation(operation.id) }
                }.onFailure { error ->
                    lgr.error(error) { "Host2 ${operation.hostId}清理发布备份失败" }
                    retryCleanup(
                        operation.hostId,
                        operation.id,
                        operation.stagingPath,
                        operation.backupPath,
                    )
                }
                return@forEach
            }
            val host = database.transaction { repository.findById(operation.hostId) }
            if (Host2RuntimeService.status(operation.hostId) != HostStatus.STOPPED) {
                Host2RuntimeService.stop(operation.hostId)
            }
            operation.stagingPath?.let(::deleteInternalDirectory)
            operation.backupPath?.let(::safeInternalDirectory)?.let { backup ->
                val target = HOST2_DIR.resolve(operation.hostId.toString())
                if (backup.exists()) {
                    if (target.exists()) target.deleteRecursivelyNoSymlink()
                    Files.move(backup.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
            }
            if (host?.activeContentRevision == 0L) {
                HOST2_DIR.resolve(operation.hostId.toString()).deleteRecursivelyNoSymlink()
            }
            database.transaction {
                if (host != null) {
                    repository.updatePackStatus(
                        host.id,
                        if (host.activeContentRevision == 0L) Host2PackStatus.Fail else Host2PackStatus.Ok,
                    )
                }
                repository.deleteOperation(operation.id)
            }
        }
        recoverDeleteTombstones()
    }

    fun close() {
        scope.coroutineContext[Job]?.cancel()
    }

    fun retryCleanup(
        hostId: UUID,
        operationId: UUID,
        stagingPath: String?,
        backupPath: String?,
    ) {
        cleanupJobs.computeIfAbsent(operationId) {
            scope.launch {
                var failures = 0
                while (true) {
                    kotlinx.coroutines.delay(CLEANUP_RETRY_DELAY_MS)
                    val operationExists = database.transaction {
                        repository.activeOperation(hostId)?.id == operationId
                    }
                    if (!operationExists) return@launch
                    val cleaned = runCatching {
                        stagingPath?.let(::deleteInternalDirectory)
                        backupPath?.let(::deleteInternalDirectory)
                        database.transaction { repository.deleteOperation(operationId) }
                    }
                    if (cleaned.isSuccess) return@launch
                    failures++
                    cleaned.exceptionOrNull()?.let { error ->
                        lgr.error(error) { "Host2 ${hostId}后台清理发布备份失败" }
                    }
                    if (failures == CLEANUP_FAILURE_MAIL_THRESHOLD) {
                        database.transaction { repository.findById(hostId) }?.let { host ->
                            notifyOwner(host, "新版房间备份清理失败", "${host.name}的旧发布备份仍在重试清理。")
                        }
                    }
                }
            }.also { job -> job.invokeOnCompletion { cleanupJobs.remove(operationId, job) } }
        }
    }

    private suspend fun install(
        host: Host2Record,
        resolved: ResolvedHost2PackSource,
        operation: Host2OperationRecord,
    ) {
        val staging = HOST2_DIR.resolve(".staging").resolve(operation.id.toString())
        val content = staging.resolve("content")
        val target = HOST2_DIR.resolve(host.id.toString())
        var published = false
        try {
            staging.mkdirs()
            database.transaction {
                repository.updateOperation(operation.id, PHASE_BUILD, stagingPath = staging.absolutePath)
            }
            val build = buildServerPack(resolved, content, emptyList(), operation)
            if (directorySize(content) > HOST2_QUOTA_BYTES) throw RequestError("整合包安装后超过8GiB")
            if (target.exists()) throw RequestError("房间目录已经存在")
            database.transaction {
                repository.updateOperation(operation.id, PHASE_PUBLISH, staging.absolutePath)
            }
            Files.move(content.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            published = true
            database.transaction {
                val locked = repository.findByIdForUpdate(host.id) ?: throw RequestError("无此新版房间")
                if (locked.activeContentRevision != 0L) throw RequestError("房间安装状态已改变")
                repository.insertRevision(host.id, 1, build.contents)
                repository.updateRevisionPointers(host.id, active = 1, pending = null)
                repository.updatePackStatus(host.id, Host2PackStatus.Ok)
                repository.deleteOperation(operation.id)
            }
            notifyOverrides(host, build.overriddenPaths)
            Host2RuntimeService.notifyMissingKotlinForForge(host.copy(activeContentRevision = 1))
            notifyOwner(host, "新版房间安装完成", "${host.name}已准备完成。")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lgr.error(error) { "Host2 ${host.id}安装失败" }
            val cleanupPending = published && runCatching {
                target.deleteRecursivelyNoSymlink()
                target.exists()
            }.getOrElse { true }
            database.transaction {
                repository.findById(host.id)?.let {
                    repository.updatePackStatus(
                        host.id,
                        if (it.activeContentRevision == 0L) Host2PackStatus.Fail else Host2PackStatus.Ok,
                    )
                }
                if (!cleanupPending) repository.deleteOperation(operation.id)
            }
            notifyOwner(host, "新版房间安装失败", "${host.name}安装失败，请稍后重试。")
        } finally {
            staging.deleteRecursivelyNoSymlink()
        }
    }

    private suspend fun switch(
        host: Host2Record,
        resolved: ResolvedHost2PackSource,
        operation: Host2OperationRecord,
    ) {
        val staging = HOST2_DIR.resolve(".staging").resolve(operation.id.toString())
        val content = staging.resolve("content")
        val target = HOST2_DIR.resolve(host.id.toString())
        val backup = HOST2_DIR.resolve(".backup").resolve(operation.id.toString())
        var oldMoved = false
        try {
            staging.mkdirs()
            database.transaction {
                repository.updateOperation(operation.id, PHASE_BUILD, staging.absolutePath, backup.absolutePath)
            }
            val (active, desired) = database.transaction {
                val current = repository.findById(host.id) ?: throw RequestError("无此新版房间")
                val revision = current.pendingContentRevision ?: current.activeContentRevision
                repository.contents(host.id, current.activeContentRevision) to repository.contents(host.id, revision)
            }
            val oldWorld = target.resolve("world")
            val build = buildServerPack(
                resolved,
                content,
                desired,
                operation,
                preserveWorld = true,
                preservedWorld = oldWorld.takeIf(File::isDirectory),
                replaceableManagedPaths = effectiveHost2Contents(active).asSequence()
                    .filter { it.side != ContentSide.Client }
                    .mapNotNullTo(mutableSetOf()) { it.targetPath?.lowercase() },
            )
            if (directorySize(content) > HOST2_QUOTA_BYTES) throw RequestError("整合包切换后超过8GiB")
            backup.parentFile.mkdirs()
            database.transaction {
                repository.updateOperation(operation.id, PHASE_PUBLISH, staging.absolutePath, backup.absolutePath)
            }
            Files.move(target.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
            oldMoved = true
            try {
                Files.move(content.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (error: Throwable) {
                Files.move(backup.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                oldMoved = false
                throw error
            }
            database.transaction {
                val locked = repository.findByIdForUpdate(host.id) ?: throw RequestError("无此新版房间")
                val revision = operation.targetRevision!!
                repository.insertRevision(host.id, revision, build.contents)
                repository.updatePackSource(host.id, resolved.source)
                repository.updateRevisionPointers(host.id, revision, null)
                repository.updatePackStatus(host.id, Host2PackStatus.Ok)
                repository.deleteRevisionsExcept(host.id, setOf(revision))
                repository.updateOperation(operation.id, PHASE_CLEANUP, staging.absolutePath, backup.absolutePath)
            }
            oldMoved = false
            runCatching {
                backup.deleteRecursivelyNoSymlink()
                if (backup.exists()) throw RequestError("旧整合包备份清理失败")
                database.transaction { repository.deleteOperation(operation.id) }
            }.onFailure { error ->
                lgr.error(error) { "Host2 ${host.id}切换后的备份清理失败" }
                retryCleanup(host.id, operation.id, staging.absolutePath, backup.absolutePath)
            }
            notifyOverrides(host, build.overriddenPaths)
            Host2RuntimeService.notifyMissingKotlinForForge(
                host.copy(activeContentRevision = operation.targetRevision!!)
            )
            notifyOwner(host, "新版房间整合包切换完成", "${host.name}已切换到${resolved.packInfo.versionName}。")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lgr.error(error) { "Host2 ${host.id}切换整合包失败" }
            if (oldMoved && backup.exists()) {
                target.deleteRecursivelyNoSymlink()
                Files.move(backup.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            database.transaction {
                repository.findById(host.id)?.let { repository.updatePackStatus(host.id, Host2PackStatus.Ok) }
                repository.deleteOperation(operation.id)
            }
            notifyOwner(host, "新版房间整合包切换失败", "${host.name}仍保留原整合包和内容设置。")
        } finally {
            staging.deleteRecursivelyNoSymlink()
        }
    }

    private suspend fun buildServerPack(
        resolved: ResolvedHost2PackSource,
        content: File,
        previous: List<ContentVo>,
        operation: Host2OperationRecord,
        preserveWorld: Boolean = false,
        preservedWorld: File? = null,
        replaceableManagedPaths: Set<String> = emptySet(),
    ): Host2PackBuildResult {
        Modpack2ArchiveIO.mergeToDirectory(
            resolved.sourceArchive,
            content,
            Modpack2MergeRoot.SERVER,
            requireMetadata = resolved.requireMetadata,
        )
        filterHost2ServerPack(content)
        if (preserveWorld) {
            content.resolve("world").deleteRecursivelyNoSymlink()
            preservedWorld?.let { copyTree(it.toPath(), content.resolve("world").toPath()) }
        }
        val previousPackStates = previous.asSequence()
            .filter { it.origin == ContentOrigin.Pack }
            .associate { (it.platform to it.projectId) to it.enabled }
        val all = resolved.contents.map { item ->
            item.copy(enabled = previousPackStates[item.platform to item.projectId] ?: item.enabled)
        } + previous.filter { it.origin == ContentOrigin.Extra }
        val canonical = coroutineScope {
            all.map { item ->
                async {
                    downloadService.resolveContent(
                        item,
                        resolved.packInfo.mcVersion,
                        resolved.packInfo.modLoader,
                    )
                }
            }.awaitAll()
        }
        validateHost2LayerPathConflicts(canonical)
        val overriddenPaths = shadowedHost2PackContents(canonical)
            .mapNotNullTo(linkedSetOf(), ContentVo::targetPath)
        shadowedHost2PackContents(canonical).forEach { shadowed ->
            shadowed.targetPath?.let { path ->
                val enabled = content.resolve(path)
                val disabled = content.resolve("${path}.disabled")
                if (preserveWorld && path.lowercase().startsWith("world/datapacks/") &&
                    (enabled.exists() || disabled.exists()) && path.lowercase() !in replaceableManagedPaths
                ) throw RequestError("世界中存在同路径的非受管DataPack: $path")
                enabled.delete()
                disabled.delete()
            }
        }
        val physical = effectiveHost2Contents(canonical).filter { it.side != ContentSide.Client }
            .groupBy { it.targetPath!!.lowercase() }
            .map { (path, samePath) ->
                if (samePath.size == 1) samePath.single()
                else throw RequestError("整合包内容目标路径冲突: $path")
            }
        physical.forEach { item ->
            val targetPath = item.targetPath!!
            val enabledTarget = content.resolve(targetPath)
            val disabledTarget = content.resolve("${targetPath}.disabled")
            if (enabledTarget.isDirectory || disabledTarget.isDirectory) {
                throw RequestError("整合包内容目标与目录冲突: $targetPath")
            }
            if (preserveWorld && targetPath.lowercase().startsWith("world/datapacks/") &&
                (enabledTarget.exists() || disabledTarget.exists()) &&
                targetPath.lowercase() !in replaceableManagedPaths
            ) throw RequestError("世界中存在同路径的非受管DataPack: $targetPath")
            if (enabledTarget.isFile || disabledTarget.isFile) overriddenPaths.add(targetPath)
            enabledTarget.delete()
            disabledTarget.delete()
            val targetFile = content.resolve(targetPath + if (item.enabled) "" else ".disabled")
            downloadService.downloadContent(item, targetFile)
        }
        content.resolve("mods").mkdirs()
        content.resolve("config").mkdirs()
        content.resolve("logs").mkdirs()
        content.resolve("eula.txt").writeText("eula=true\n")
        validateKotlinForForge(content)
        writeDeploymentMarker(
            content,
            operation.hostId,
            operation.id,
            operation.targetRevision ?: 0,
            resolved.source,
        )
        overriddenPaths.forEach { path -> lgr.warn { "Host2 ${operation.hostId}内容覆盖Pack文件: $path" } }
        return Host2PackBuildResult(canonical, overriddenPaths.toList())
    }

    private suspend fun recoverDeleteTombstones() {
        database.transaction { repository.deleteTombstones() }.forEach { tombstone ->
            runCatching {
                deleteInternalDirectory(tombstone.deletingPath)
                database.transaction {
                    repository.deleteDeleteTombstone(tombstone.operationId)
                    repository.deleteOperation(tombstone.operationId)
                }
            }.onFailure { error ->
                lgr.error(error) { "Host2 ${tombstone.hostId}恢复删除清理失败" }
            }
        }
    }

    private suspend fun recoverUncommittedDelete(operation: Host2OperationRecord) {
        val target = HOST2_DIR.resolve(operation.hostId.toString())
        val deleting = HOST2_DIR.resolve(".deleting").resolve(operation.id.toString())
        runCatching {
            if (deleting.exists() && !target.exists()) {
                Files.move(deleting.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } else if (deleting.exists()) {
                deleting.deleteRecursivelyNoSymlink()
            }
            database.transaction { repository.deleteOperation(operation.id) }
        }.onFailure { error ->
            lgr.error(error) { "Host2 ${operation.hostId}恢复未提交删除失败" }
        }
    }

    private fun safeInternalDirectory(path: String): File {
        val root = HOST2_DIR.toPath().toAbsolutePath().normalize()
        val target = File(path).toPath().toAbsolutePath().normalize()
        if (!target.startsWith(root) || target == root) throw RequestError("Host2恢复路径无效")
        return target.toFile()
    }

    private fun deleteInternalDirectory(path: String) {
        val directory = safeInternalDirectory(path)
        directory.deleteRecursivelyNoSymlink()
        if (directory.exists()) throw RequestError("Host2内部目录清理失败")
    }

    private suspend fun notifyOwner(host: Host2Record, title: String, content: String) {
        runCatching { MailService.sendSystemMail(host.ownerId.objectId, title, content) }
            .onFailure { lgr.error(it) { "Host2 ${host.id}发送系统邮件失败" } }
    }

    private suspend fun notifyOverrides(host: Host2Record, paths: List<String>) {
        if (paths.isEmpty()) return
        notifyOwner(
            host,
            "新版房间内容覆盖提醒",
            "${host.name}有${paths.size}个整合包文件被外部或附加内容覆盖：${paths.take(20).joinToString()}。",
        )
    }
}

private data class Host2PackBuildResult(
    val contents: List<ContentVo>,
    val overriddenPaths: List<String>,
)

private fun directorySize(root: File): Long =
    root.walkTopDown().filter(File::isFile).sumOf(File::length)

internal fun filterHost2ServerPack(root: File) {
    root.walkBottomUp().filter { it != root }.forEach { file ->
        val relative = file.relativeTo(root).invariantSeparatorsPath
        val segments = relative.split('/')
        val first = segments.first().lowercase()
        val extension = file.extension.lowercase()
        val remove = first in FILTERED_SERVER_DIRECTORIES ||
            relative.equals("world/session.lock", true) ||
            file.isFile && (
                extension == "db" ||
                    extension in FILTERED_SERVER_MEDIA_EXTENSIONS ||
                    extension in setOf("jar", "exe") && first != "mods" &&
                    !file.name.contains("lwjgl3ify", true)
                )
        if (remove) file.deleteRecursivelyNoSymlink()
    }
}

internal fun validateKotlinForForge(root: File) {
    val count = root.resolve("mods").listFiles().orEmpty().count { file ->
        file.isFile && !file.name.endsWith(".disabled", true) &&
            (file.name.contains("kotlinforforge", true) || file.name.contains("kotlin-for-forge", true))
    }
    if (count > 1) throw RequestError("KotlinForForge只能保留1个启用文件")
}

internal fun writeDeploymentMarker(
    root: File,
    hostId: UUID,
    operationId: UUID,
    revision: Long,
    packSource: PackSource,
) {
    root.resolve(DEPLOYMENT_MARKER).writeText(
        serdesJson.encodeToString(DeploymentMarker(hostId.toString(), operationId.toString(), revision, packSource))
    )
}

internal fun validateDeploymentMarker(root: File, host: Host2Record) {
    val marker = runCatching {
        serdesJson.decodeFromString<DeploymentMarker>(root.resolve(DEPLOYMENT_MARKER).readText())
    }.getOrElse { throw RequestError("房间部署标记不可用", it) }
    if (marker.hostId != host.id.toString() || marker.revision != host.activeContentRevision ||
        marker.packSource != host.packSource
    ) throw RequestError("房间部署标记与当前内容版本不一致")
}

@Serializable
internal data class DeploymentMarker(
    val hostId: String,
    val operationId: String,
    val revision: Long,
    val packSource: PackSource,
)

private const val PHASE_PREPARE = "Prepare"
private const val PHASE_BUILD = "Build"
private const val PHASE_PUBLISH = "Publish"
private const val PHASE_CLEANUP = "Cleanup"
private const val DEPLOYMENT_MARKER = ".rdi-host2-deployment.json"
private const val CLEANUP_RETRY_DELAY_MS = 60_000L
private const val CLEANUP_FAILURE_MAIL_THRESHOLD = 5
private val FILTERED_SERVER_DIRECTORIES = setOf("libraries", "cache", "logs", "crash-reports")
private val FILTERED_SERVER_MEDIA_EXTENSIONS = setOf("psd", "png", "jpg", "jpeg", "webp", "mp4", "ogg", "wav")
