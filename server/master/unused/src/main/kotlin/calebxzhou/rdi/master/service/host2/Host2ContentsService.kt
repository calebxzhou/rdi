package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.logging.Loggers
import calebxzau.rdi.common.model.ContentKey
import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2ErrorCode
import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

class Host2ContentsService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val hostService: Host2Service,
    private val packSourceService: Host2PackSourceService,
    private val downloadService: Host2ModDownloadService,
    private val setupService: Host2SetupService,
) {
    private val lgr by Loggers

    suspend fun list(player: RAccount, id: UUID): Host2.ContentsVo {
        hostService.requireAdmin(player, id)
        return database.transaction {
            val host = repository.findById(id) ?: throw RequestError("无此新版房间")
            Host2.ContentsVo(
                activeRevision = host.activeContentRevision,
                pendingRevision = host.pendingContentRevision,
                active = snapshot(id, host.activeContentRevision),
                pending = host.pendingContentRevision?.let { repository.contents(id, it) },
            )
        }
    }

    suspend fun add(player: RAccount, id: UUID, dto: Host2.AddContentsDto): Host2.ContentRevisionVo {
        val host = hostService.requireAdmin(player, id)
        requireReady(host)
        if (dto.contents.isEmpty()) throw RequestError("请选择要添加的内容")
        if (dto.contents.map { it.platform to it.projectId }.distinct().size != dto.contents.size) {
            throw RequestError("请求包含重复内容")
        }
        val pack = packSourceService.resolveExact(host.packSource).getOrElse {
            throw RequestError("整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        val resolved = coroutineScope {
            dto.contents.map { input ->
                async { downloadService.resolveExtra(input, pack.packInfo.mcVersion, pack.packInfo.modLoader) }
            }.awaitAll()
        }
        return mutate(player, id, dto.expectedRevision, sourceVerified = true) { desired ->
            val keys = desired.mapTo(mutableSetOf(), ContentVo::key)
            resolved.forEach { content ->
                if (!keys.add(content.key)) throw RequestError("内容已经存在: ${content.projectId}")
            }
            desired + resolved
        }
    }

    suspend fun delete(player: RAccount, id: UUID, dto: Host2.DeleteContentsDto): Host2.ContentRevisionVo =
        mutate(player, id, dto.expectedRevision) { desired ->
            val keys = dto.keys.toSet()
            if (keys.isEmpty()) throw RequestError("请选择要删除的内容")
            if (keys.any { it.origin != ContentOrigin.Extra }) throw RequestError("整合包内容不能删除")
            val existing = desired.map(ContentVo::key).toSet()
            if (!existing.containsAll(keys)) throw RequestError("所选内容不存在")
            desired.filterNot { it.key in keys }
        }

    suspend fun setEnabled(
        player: RAccount,
        id: UUID,
        dto: Host2.SetContentsEnabledDto,
    ): Host2.ContentRevisionVo = mutate(player, id, dto.expectedRevision) { desired ->
        val keys = dto.keys.toSet()
        if (keys.isEmpty()) throw RequestError("请选择要修改的内容")
        val existing = desired.map(ContentVo::key).toSet()
        if (!existing.containsAll(keys)) throw RequestError("所选内容不存在")
        desired.map { content ->
            if (content.key in keys) content.copy(enabled = dto.enabled) else content
        }
    }

    suspend fun apply(player: RAccount, id: UUID, dto: Host2.ApplyContentsDto): Host2.ContentRevisionVo {
        hostService.requireAdmin(player, id)
        val sourceHost = database.transaction { repository.findById(id) } ?: throw RequestError("无此新版房间")
        packSourceService.resolveExact(sourceHost.packSource).getOrElse {
            throw RequestError("整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        if (Host2RuntimeService.status(id) != HostStatus.STOPPED) throw RequestError("请先停止房间")
        return hostService.withMutation(id) {
            val state = database.transaction {
                val host = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                requireReady(host)
                val pending = host.pendingContentRevision ?: return@transaction null
                dto.expectedRevision?.let { expected -> requireRevision(host, expected) }
                Triple(host, repository.contents(id, host.activeContentRevision), repository.contents(id, pending))
            } ?: return@withMutation currentRevision(id)
            publishAndActivate(
                state.first,
                state.second,
                state.third,
                state.first.pendingContentRevision!!,
                revisionPersisted = true,
            )
        }
    }

    suspend fun applyPendingForStart(host: Host2Record) {
        val pending = host.pendingContentRevision ?: return
        packSourceService.resolveExact(host.packSource).getOrElse {
            throw RequestError("整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        val snapshots = database.transaction {
            repository.contents(host.id, host.activeContentRevision) to repository.contents(host.id, pending)
        }
        publishAndActivate(host, snapshots.first, snapshots.second, pending, revisionPersisted = true)
    }

    private suspend fun mutate(
        player: RAccount,
        id: UUID,
        expectedRevision: Long,
        sourceVerified: Boolean = false,
        transform: (List<ContentVo>) -> List<ContentVo>,
    ): Host2.ContentRevisionVo {
        val sourceHost = hostService.requireAdmin(player, id)
        if (!sourceVerified) {
            packSourceService.resolveExact(sourceHost.packSource).getOrElse {
                throw RequestError("整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
            }
        }
        return hostService.withMutation(id) {
            val prepared = database.transaction {
                val host = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                requireReady(host)
                requireRevision(host, expectedRevision)
                val desiredRevision = host.pendingContentRevision ?: host.activeContentRevision
                val oldDesired = snapshot(id, desiredRevision)
                val newDesired = transform(oldDesired).normalizedSnapshot()
                if (newDesired == oldDesired.normalizedSnapshot()) return@transaction null
                validateSnapshot(newDesired)
                preserveLastKotlinForForge(oldDesired, newDesired)
                val nextRevision = maxOf(host.activeContentRevision, host.pendingContentRevision ?: 0) + 1
                PreparedMutation(host, snapshot(id, host.activeContentRevision), newDesired, nextRevision)
            } ?: return@withMutation currentRevision(id)

            when (Host2RuntimeService.status(id)) {
                HostStatus.STOPPED -> publishAndActivate(
                    prepared.host,
                    prepared.active,
                    prepared.desired,
                    prepared.revision,
                )

                HostStatus.STARTED, HostStatus.PLAYABLE, HostStatus.PAUSED -> database.transaction {
                    val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                    requireRevision(locked, expectedRevision)
                    repository.insertRevision(id, prepared.revision, prepared.desired)
                    repository.updateRevisionPointers(id, locked.activeContentRevision, prepared.revision)
                    repository.deleteRevisionsExcept(id, setOf(locked.activeContentRevision, prepared.revision))
                    Host2.ContentRevisionVo(expectedRevision, prepared.revision)
                }

                HostStatus.UNKNOWN -> throw RequestError("房间运行状态未知")
            }
        }
    }

    private suspend fun publishAndActivate(
        host: Host2Record,
        active: List<ContentVo>,
        desired: List<ContentVo>,
        revision: Long,
        revisionPersisted: Boolean = false,
    ): Host2.ContentRevisionVo {
        val operation = database.transaction {
            repository.updatePackStatus(host.id, Host2PackStatus.Busy)
            repository.insertOperation(
                NewHost2Operation(
                    hostId = host.id,
                    kind = Host2OperationKind.Apply,
                    phase = "Build",
                    targetRevision = revision,
                )
            )
        }
        val root = HOST2_DIR.resolve(host.id.toString())
        val staging = HOST2_DIR.resolve(".staging").resolve(operation.id.toString())
        val content = staging.resolve("content")
        val backup = HOST2_DIR.resolve(".backup").resolve(operation.id.toString())
        var published = false
        try {
            copyTree(root.toPath(), content.toPath())
            applyManagedContents(content, active, desired)
            validateKotlinForForge(content)
            writeDeploymentMarker(content, host.id, operation.id, revision, host.packSource)
            if (directorySize(content) > HOST2_QUOTA_BYTES) throw RequestError("内容变更后超过8GiB")
            backup.parentFile.mkdirs()
            database.transaction {
                repository.updateOperation(operation.id, "Publish", staging.absolutePath, backup.absolutePath)
            }
            Files.move(root.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(content.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE)
                published = true
            } catch (error: Throwable) {
                Files.move(backup.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE)
                throw error
            }
            val result = database.transaction {
                val locked = repository.findByIdForUpdate(host.id) ?: throw RequestError("无此新版房间")
                if (!revisionPersisted) repository.insertRevision(host.id, revision, desired)
                repository.updateRevisionPointers(host.id, revision, null)
                repository.updatePackStatus(host.id, Host2PackStatus.Ok)
                repository.deleteRevisionsExcept(host.id, setOf(revision))
                repository.updateOperation(operation.id, "Cleanup", staging.absolutePath, backup.absolutePath)
                Host2.ContentRevisionVo(locked.pendingContentRevision ?: locked.activeContentRevision, revision)
            }
            published = false
            runCatching {
                backup.deleteRecursivelyNoSymlink()
                if (backup.exists()) throw RequestError("旧内容备份清理失败")
                database.transaction { repository.deleteOperation(operation.id) }
            }.onFailure { error ->
                lgr.error(error) { "Host2 ${host.id}内容发布后的备份清理失败" }
                setupService.retryCleanup(host.id, operation.id, staging.absolutePath, backup.absolutePath)
            }
            notifyOverrides(host, shadowedHost2PackContents(desired).mapNotNull(ContentVo::targetPath))
            Host2RuntimeService.notifyMissingKotlinForForge(host.copy(activeContentRevision = revision))
            return result
        } catch (error: Throwable) {
            if (published && backup.exists()) {
                root.deleteRecursivelyNoSymlink()
                Files.move(backup.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            database.transaction {
                repository.findById(host.id)?.let { repository.updatePackStatus(host.id, Host2PackStatus.Ok) }
                repository.deleteOperation(operation.id)
            }
            runCatching {
                MailService.sendSystemMail(
                    host.ownerId.objectId,
                    "新版房间内容应用失败",
                    "${host.name}仍保留原内容版本，待应用内容也已保留。",
                )
            }.onFailure { mailError ->
                lgr.error(mailError) { "Host2 ${host.id}发送内容应用失败邮件失败" }
            }
            throw error
        } finally {
            staging.deleteRecursivelyNoSymlink()
        }
    }

    private suspend fun applyManagedContents(
        root: File,
        active: List<ContentVo>,
        desired: List<ContentVo>,
    ) {
        effectiveHost2Contents(active).asSequence()
            .filter { it.side != ContentSide.Client }
            .mapNotNull(ContentVo::targetPath)
            .distinct()
            .forEach { path ->
                root.resolve(path).delete()
                root.resolve("${path}.disabled").delete()
            }
        effectivePhysicalContents(desired).forEach { content ->
            val enabledTarget = root.resolve(content.targetPath!!)
            val disabledTarget = root.resolve("${content.targetPath}.disabled")
            if (enabledTarget.exists() || disabledTarget.exists()) {
                throw RequestError("内容目标路径已被其他文件占用: ${content.targetPath}")
            }
            val target = root.resolve(content.targetPath!! + if (content.enabled) "" else ".disabled")
            downloadService.downloadContent(content, target)
        }
    }

    private fun effectivePhysicalContents(contents: List<ContentVo>): List<ContentVo> = effectiveHost2Contents(contents)
        .filter { it.side != ContentSide.Client }
        .groupBy { it.targetPath!!.lowercase() }
        .map { (_, samePath) ->
            if (samePath.size != 1) throw RequestError("多个内容使用同一目标路径: ${samePath.first().targetPath}")
            samePath.single()
        }

    private fun validateSnapshot(contents: List<ContentVo>) {
        validateHost2SnapshotIdentity(contents)
        validateHost2LayerPathConflicts(contents)
        effectivePhysicalContents(contents)
    }

    private fun preserveLastKotlinForForge(old: List<ContentVo>, new: List<ContentVo>) {
        val oldKff = effectivePhysicalContents(old).filter(ContentVo::isEnabledKotlinForForge)
        if (oldKff.size == 1 && oldKff.single().origin == ContentOrigin.Extra &&
            effectivePhysicalContents(new).none(ContentVo::isEnabledKotlinForForge)
        ) {
            throw RequestError("不能删除或停用最后1个KotlinForForge附加内容")
        }
    }

    private fun requireReady(host: Host2Record) {
        if (host.packStatus != Host2PackStatus.Ok || host.activeContentRevision == 0L) {
            throw RequestError("房间整合包尚未准备完成")
        }
    }

    private fun requireRevision(host: Host2Record, expected: Long) {
        val current = host.pendingContentRevision ?: host.activeContentRevision
        if (expected != current) throw Host2RevisionConflict(current)
    }

    private suspend fun currentRevision(id: UUID): Host2.ContentRevisionVo = database.transaction {
        val host = repository.findById(id) ?: throw RequestError("无此新版房间")
        val current = host.pendingContentRevision ?: host.activeContentRevision
        Host2.ContentRevisionVo(current, current)
    }

    private fun snapshot(id: UUID, revision: Long): List<ContentVo> =
        if (revision == 0L) emptyList() else repository.contents(id, revision)

    private suspend fun notifyOverrides(host: Host2Record, paths: List<String>) {
        if (paths.isEmpty()) return
        paths.forEach { path -> lgr.warn { "Host2 ${host.id}附加内容覆盖Pack内容: $path" } }
        runCatching {
            MailService.sendSystemMail(
                host.ownerId.objectId,
                "新版房间内容覆盖提醒",
                "${host.name}有${paths.size}个Pack内容被附加内容覆盖：${paths.take(20).joinToString()}。",
            )
        }.onFailure { error ->
            lgr.error(error) { "Host2 ${host.id}发送内容覆盖邮件失败" }
        }
    }
}

internal fun effectiveHost2Contents(contents: List<ContentVo>): List<ContentVo> {
    val extras = contents.filter { it.origin == ContentOrigin.Extra }
    val extraIdentities = extras.mapTo(mutableSetOf()) { it.platform to it.projectId }
    val extraPaths = extras.mapNotNullTo(mutableSetOf()) { it.targetPath?.lowercase() }
    return contents.filter { content ->
        content.origin == ContentOrigin.Extra ||
            content.platform to content.projectId !in extraIdentities &&
            content.targetPath?.lowercase() !in extraPaths
    }
}

internal fun shadowedHost2PackContents(contents: List<ContentVo>): List<ContentVo> {
    val extras = contents.filter { it.origin == ContentOrigin.Extra }
    val extraIdentities = extras.mapTo(mutableSetOf()) { it.platform to it.projectId }
    val extraPaths = extras.mapNotNullTo(mutableSetOf()) { it.targetPath?.lowercase() }
    return contents.filter { content ->
        content.origin == ContentOrigin.Pack &&
            (content.platform to content.projectId in extraIdentities || content.targetPath?.lowercase() in extraPaths)
    }
}

internal fun validateHost2LayerPathConflicts(contents: List<ContentVo>) {
    contents.asSequence()
        .filter { it.side != ContentSide.Client }
        .groupBy { it.origin to it.targetPath!!.lowercase() }
        .values
        .firstOrNull { it.size > 1 }
        ?.let { conflict -> throw RequestError("同层多个内容使用同一目标路径: ${conflict.first().targetPath}") }
}

private fun ContentVo.isEnabledKotlinForForge(): Boolean =
    enabled && targetPath?.substringAfterLast('/')?.let { name ->
        name.contains("kotlinforforge", true) || name.contains("kotlin-for-forge", true)
    } == true

class Host2RevisionConflict(revision: Long) : RequestError(
    msg = "内容版本已改变",
    errorCode = Host2ErrorCode.REVISION_CONFLICT,
    currentRevision = revision,
)

private data class PreparedMutation(
    val host: Host2Record,
    val active: List<ContentVo>,
    val desired: List<ContentVo>,
    val revision: Long,
)

private val ContentVo.key: ContentKey
    get() = ContentKey(origin, platform, projectId)

private fun List<ContentVo>.normalizedSnapshot(): List<ContentVo> = sortedWith(
    compareBy<ContentVo>({ it.origin.name }, { it.platform.name }, { it.projectId }, { it.side.name })
)

private data class Host2SnapshotKey(
    val origin: ContentOrigin,
    val platform: ContentPlatform,
    val projectId: String,
    val side: ContentSide,
)

/**
 * Snapshot storage distinguishes client and server artifacts, while mutation requests keep
 * the stable project identity from [ContentKey]. A key therefore addresses both side rows, but
 * duplicate validation must only reject two rows on the same side.
 */
internal fun validateHost2SnapshotIdentity(contents: List<ContentVo>) {
    if (contents.map { Host2SnapshotKey(it.origin, it.platform, it.projectId, it.side) }
            .distinct().size != contents.size
    ) {
        throw RequestError("内容项目重复")
    }
}

internal fun copyTree(source: Path, target: Path) {
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
        throw RequestError("房间目录不可用")
    }
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            if (Files.isSymbolicLink(path)) throw RequestError("房间目录包含软链接")
            val destination = target.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination)
            else Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }
}

private fun directorySize(root: File): Long =
    root.walkTopDown().filter(File::isFile).sumOf(File::length)
