package calebxzau.rdi.server.modpack

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.model.supportLoader
import calebxzhou.rdi.common.service.validateIconUrl
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.common.util.validateHttpUrl
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.master.MODPACK_DATA_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import calebxzhou.rdi.master.service.ModpackService
import calebxzhou.rdi.master.service.host2.Host2Repository
import calebxzhou.rdi.master.service.ServerTaskManager
import calebxzhou.rdi.master.service.modpack2ParallelUploadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2BriefVo
import calebxzau.rdi.common.model.Modpack2Category
import calebxzau.rdi.common.model.Modpack2
import calebxzau.rdi.common.model.Modpack2CreateDto
import calebxzau.rdi.common.model.Modpack2CreateResult
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2Page
import calebxzau.rdi.common.model.Modpack2AppendPreflightDto
import calebxzau.rdi.common.model.Modpack2AppendPreflightVo
import calebxzau.rdi.common.model.Modpack2ClientManifestVo
import calebxzau.rdi.common.model.Modpack2CreateModpackDto
import calebxzau.rdi.common.model.Modpack2InheritedRawFile
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2RawFileRoot
import calebxzau.rdi.common.model.Modpack2ServerMode
import calebxzau.rdi.common.model.Modpack2VersionCreateFromUploadDto
import calebxzau.rdi.common.model.Modpack2VersionDetailVo
import calebxzau.rdi.common.model.Modpack2Version
import calebxzau.rdi.common.model.Modpack2VersionStatus
import calebxzau.rdi.common.model.Modpack2VersionManifestDto
import calebxzau.rdi.common.model.Modpack2OwnedVo
import calebxzau.rdi.common.model.Modpack2ContentSourceDto
import calebxzau.rdi.common.model.ModpackCategory
import calebxzau.rdi.common.logging.Loggers
import kotlin.uuid.toKotlinUuid

/** PostgreSQL-backed Modpack2 create/build/delete lifecycle. */
class Modpack2Service(
    private val database: DatabaseProvider,
    private val repository: Modpack2Repo,
    private val storage: Modpack2VersionStorage = Modpack2VersionStorage(MODPACK_DATA_DIR),
    private val host2Repository: Host2Repository = Host2Repository(),
) {
    private val lgr by Loggers
    private val modpackLocks = ConcurrentHashMap<UUID, Mutex>()
    private val versionRuns = ConcurrentHashMap<UUID, String>()

    suspend fun listPublic(page: Int): List<Modpack2BriefVo> {
        val offset = page.toLong() * MODPACK2_PUBLIC_PAGE_SIZE
        return database.transaction {
            repository.listPublic(offset = offset, limit = MODPACK2_PUBLIC_PAGE_SIZE)
        }.map { detail ->
            Modpack2BriefVo(
                id = detail.modpack.id.toKotlinUuid(),
                name = detail.modpack.name,
                intro = detail.modpack.intro,
                iconUrl = detail.modpack.iconUrl,
                playTimeSec = detail.stats.playTimeSec,
                categories = detail.categories.map { it.category },
            )
        }
    }

    /** Lists only the authenticated player's owned Modpack2 packs. */
    suspend fun listOwned(player: RAccount, offset: Long = 0, limit: Int = 50): Modpack2Page<Modpack2OwnedVo> =
        database.transaction {
            repository.listOwned(player._id.toUUID(), offset, limit)
        }.let { page ->
            Modpack2Page(
                items = page.items.map { detail ->
                    Modpack2OwnedVo(
                        id = detail.modpack.id,
                        name = detail.modpack.name,
                        intro = detail.modpack.intro,
                        mc = detail.modpack.mc,
                        loader = detail.modpack.loader,
                        iconUrl = detail.modpack.iconUrl,
                        categories = detail.categories.map { it.category },
                        currentVersionId = detail.currentVersion?.id,
                        currentVersionName = detail.currentVersion?.name,
                        currentVersionStatus = detail.currentVersion?.status,
                    )
                },
                total = page.total,
                offset = page.offset,
                limit = page.limit,
                hasMore = page.hasMore,
            )
        }

    /**
     * Checks the exact base selected by an append workspace. A stale base is
     * rejected before the client spends time freezing or uploading a source.
     * Non-owners receive canAppend=false because their publish will fork a new
     * Modpack2 rather than mutate the URL parent.
     */
    suspend fun appendPreflight(
        player: RAccount,
        modpackId: UUID,
        dto: Modpack2AppendPreflightDto,
    ): Modpack2AppendPreflightVo = database.transaction {
        val modpack = repository.findModpack(modpackId)
            ?: throw RequestError("无此整合包")
        val base = repository.findVersion(dto.baseVersionId)
            ?: throw RequestError("无此整合包版本")
        if (base.modpackId != modpackId) throw RequestError("版本不属于此整合包")
        if (base.status != Modpack2VersionStatus.Ok) {
            throw RequestError("只能从已完成的整合包版本发布")
        }
        val current = repository.findCurrentVersion(modpackId)
        val owner = modpack.ownerId == player._id.toUUID() || player.isDav
        if (owner && current?.id != base.id) {
            throw RequestError("整合包基础版本已过期，请刷新后重新复制")
        }
        Modpack2AppendPreflightVo(
            modpackId = modpackId,
            baseVersionId = base.id,
            currentVersionId = current?.id,
            canAppend = owner,
        )
    }

    /** Lists all versions for an owner, or only Ok versions for other players. */
    suspend fun listVersions(player: RAccount, modpackId: UUID): List<Modpack2VersionDetailVo> =
        database.transaction {
            val modpack = repository.findModpack(modpackId)
                ?: throw RequestError("无此整合包")
            val owner = modpack.ownerId == player._id.toUUID() || player.isDav
            repository.listVersions(modpackId)
                .asSequence()
                .filter { owner || it.status == Modpack2VersionStatus.Ok }
                .map { version ->
                    version.toVersionDetail(
                        repository.listContents(version.id),
                        repository.listRawFiles(version.id),
                        repository.findVersionManifest(version.id),
                    )
                }
                .toList()
        }

    /**
     * Publishes an uploaded source as a version under the URL parent. The
     * owner appends in place; a non-owner gets a new pack copied from the
     * parent metadata. The upload result is bound to the URL parent for
     * response-loss idempotency.
     */
    suspend fun createVersion(
        player: RAccount,
        parentId: UUID,
        rawDto: Modpack2VersionCreateFromUploadDto,
    ): Modpack2CreateResult {
        val dto = rawDto.normalizedVersionCreate()
        val ownerId = player._id.toUUID()
        val now = System.currentTimeMillis()
        val cached = database.transaction {
            repository.findModpack(parentId) ?: throw RequestError("无此整合包")
            repository.findUploadResult(rawDto.uploadId, ownerId, now)
        }
        cached?.let {
            if (it.requestModpackId != parentId) throw RequestError("上传会话已绑定其他整合包")
            return it.toCreateResult()
        }

        return ModpackService.modpack2ParallelUploadService
            .withReadyUpload(player._id, dto.uploadId) { uploadFile ->
                // The source archive may omit server/ when Provided processing
                // found no server-only raw bytes. Provided mode never inherits
                // a base source; it only records the uploaded bytes/content.
                Modpack2ArchiveIO.validateSourceArchive(uploadFile)
                val uploadedRawFiles = reconcileRawFiles(
                    declared = dto.rawFiles,
                    actual = Modpack2ArchiveIO.rawFileManifest(uploadFile),
                )
                val archiveManifest = Modpack2ArchiveIO.readManifest(uploadFile)
                if (archiveManifest.format != dto.manifest.format) {
                    throw RequestError("上传归档manifest格式与提交合同不一致")
                }
                val parent = database.transaction {
                    repository.findModpack(parentId) ?: throw RequestError("无此整合包")
                }
                val verifiedManifest = Modpack2ManifestValidator.validate(
                    manifest = dto.manifest,
                    contents = dto.contents,
                    rawFiles = uploadedRawFiles,
                    expectedMc = parent.mc,
                    expectedLoader = parent.loader,
                    actualManifestJson = archiveManifest.json,
                )
                val created = database.transaction {
                    repository.deleteExpiredUploadResults(now)
                    repository.findUploadResult(rawDto.uploadId, ownerId, now)?.let { cached ->
                        if (cached.requestModpackId != parentId) {
                            throw RequestError("上传会话已绑定其他整合包")
                        }
                        return@transaction PublishedVersion(
                            modpackId = cached.modpackId,
                            versionId = cached.versionId,
                            newlyCreatedModpack = false,
                            newlyCreatedVersion = false,
                        )
                    }
                    val parent = repository.findModpackForUpdate(parentId)
                        ?: throw RequestError("无此整合包")
                    val requesterOwnsParent = parent.ownerId == ownerId || player.isDav
                    val base = dto.baseVersionId?.let { baseId ->
                        repository.findVersion(baseId)
                            ?: throw RequestError("无此整合包版本")
                    }
                    if (base != null && base.modpackId != parentId) {
                        throw RequestError("基础版本不属于此整合包")
                    }
                    if (base != null && base.status != Modpack2VersionStatus.Ok) {
                        throw RequestError("只能从已完成的整合包版本发布")
                    }
                    if (requesterOwnsParent && base == null && repository.findCurrentVersion(parentId) != null) {
                        throw RequestError("向现有整合包追加版本必须指定基础版本")
                    }
                    if (requesterOwnsParent && base != null) {
                        if (repository.findCurrentVersion(parentId)?.id != base.id) {
                            throw RequestError("整合包基础版本已过期，请刷新后重新复制")
                        }
                    }
                    val inheritedContents = if (dto.serverMode == Modpack2ServerMode.Generated && base != null) {
                        val currentKeys = dto.contents.map(::contentIdentity).toSet()
                        repository.listContents(base.id).filter {
                            it.side == ContentSide.Server && contentIdentity(it) !in currentKeys
                        }
                    } else {
                        emptyList()
                    }
                    val inheritedRawFiles = if (dto.serverMode == Modpack2ServerMode.Generated && base != null) {
                        val baseFiles = repository.listRawFiles(base.id)
                            .filter { it.root == Modpack2RawFileRoot.Server }
                            .associateBy { it.path }
                        dto.inheritedServerFiles.map { declaration ->
                            val baseFile = baseFiles[declaration.path]
                                ?: throw RequestError("基础版本缺少要继承的server文件")
                            if (!baseFile.sha1.equals(declaration.sha1, ignoreCase = true)) {
                                throw RequestError("基础版本server文件已变化，请刷新后重新复制")
                            }
                            baseFile
                        }
                    } else {
                        if (dto.inheritedServerFiles.isNotEmpty()) {
                            throw RequestError("没有基础版本可继承server文件")
                        }
                        emptyList()
                    }
                    val target = if (requesterOwnsParent) {
                        parent
                    } else {
                        if (!player.isDav && repository.countByOwner(ownerId) >= MAX_MODPACK_PER_USER) {
                            throw RequestError("每个玩家最多拥有${MAX_MODPACK_PER_USER}个整合包")
                        }
                        val copied = dto.modpack ?: parent.toCreateModpackDto(repository.listCategories(parentId))
                        val targetName = copied.name.trim()
                        if (repository.findModpackByNameIgnoreCase(targetName) != null) {
                            throw RequestError("同名整合包已存在")
                        }
                        val normalized = copied.normalized()
                        val inserted = repository.insertModpack(
                            ownerId = ownerId,
                            name = normalized.name,
                            intro = normalized.intro,
                            mc = normalized.mc,
                            loader = normalized.loader,
                            iconUrl = normalized.iconUrl,
                            sourceUrl = normalized.sourceUrl,
                        )
                        repository.replaceCategories(inserted.id, normalized.categories)
                        inserted
                    }
                    val version = repository.insertVersion(
                        modpackId = target.id,
                        uploaderId = ownerId,
                        name = dto.name,
                        changelog = dto.changelog,
                        status = Modpack2VersionStatus.Building,
                        serverMode = dto.serverMode,
                        baseVersionId = dto.baseVersionId,
                    )
                    repository.replaceContents(
                        version.id,
                        (dto.contents + inheritedContents.map { it.copy(versionId = version.id) })
                            .map { it.copy(versionId = version.id) },
                    )
                    repository.replaceRawFiles(
                        version.id,
                        mergeRawFiles(uploadedRawFiles, inheritedRawFiles),
                    )
                    repository.insertVersionManifest(version.id, verifiedManifest)
                    if (!repository.insertUploadResult(
                            Modpack2UploadResult(
                                uploadId = dto.uploadId,
                                ownerId = ownerId,
                                modpackId = target.id,
                                versionId = version.id,
                                expiresAt = now + IDEMPOTENCY_TTL_MILLIS,
                                requestModpackId = parentId,
                            )
                        )
                    ) {
                        throw RequestError("上传会话已被使用")
                    }
                    PublishedVersion(
                        modpackId = target.id,
                        versionId = version.id,
                        newlyCreatedModpack = !requesterOwnsParent,
                        newlyCreatedVersion = true,
                    )
                }
                if (!created.newlyCreatedVersion) return@withReadyUpload created.toCreateResult()
                try {
                    val sourceToAdopt = materializeGeneratedSource(
                        uploadFile = uploadFile,
                        parentId = parentId,
                        versionId = dto.baseVersionId,
                        targetModpackId = created.modpackId,
                        targetVersionId = created.versionId,
                        serverMode = dto.serverMode,
                        inheritedServerFiles = dto.inheritedServerFiles,
                    )
                    storage.adoptSource(sourceToAdopt, created.modpackId, created.versionId)
                    startBuild(player._id, created.modpackId, created.versionId)
                } catch (error: Throwable) {
                    compensatePublished(created, dto.uploadId)
                    throw error
                }
                created.toCreateResult()
            }
            .getOrThrow()
    }

    /** Retries exactly the frozen source retained for a failed version. */
    suspend fun retryVersion(player: RAccount, modpackId: UUID, versionId: UUID): Modpack2CreateResult =
        withModpackLock(modpackId) {
            val owner = database.transaction {
                repository.findVersionWithModpackForUpdate(versionId)
                    ?: throw RequestError("无此整合包版本")
            }
            if (owner.modpack.id != modpackId) throw RequestError("版本不属于此整合包")
            requireOwner(owner.modpack.ownerId, player._id.toUUID(), player)
            if (owner.version.status != Modpack2VersionStatus.Fail) {
                throw RequestError("只有失败版本可以重试")
            }
            val paths = storage.paths(modpackId, versionId)
            if (!Files.isRegularFile(paths.sourceArchive)) {
                throw RequestError("失败版本的冻结源文件已不可用，请复制后重新发布")
            }
            clearFailedBuildArtifact(paths.clientArchive)
            if (!database.transaction { repository.transitionFailToBuilding(versionId) }) {
                throw RequestError("整合包版本状态已改变")
            }
            try {
                startBuild(player._id, modpackId, versionId)
            } catch (error: Throwable) {
                database.transaction { repository.transitionBuildingToFail(versionId) }
                throw error
            }
            Modpack2CreateResult(modpackId, versionId, Modpack2VersionStatus.Building)
        }

    private fun clearFailedBuildArtifact(path: java.nio.file.Path) {
        if (Files.isSymbolicLink(path) || Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw RequestError("失败版本的客户端归档路径无效")
        }
        Files.deleteIfExists(path)
    }

    suspend fun clientPackHash(player: RAccount, modpackId: UUID, versionId: UUID): String {
        val file = publicClientPack(player, modpackId, versionId)
        return file.sha1.lowercase()
    }

    suspend fun clientPack(player: RAccount, modpackId: UUID, versionId: UUID): java.io.File =
        publicClientPack(player, modpackId, versionId)

    suspend fun clientManifest(
        player: RAccount,
        modpackId: UUID,
        versionId: UUID,
    ): Modpack2ClientManifestVo = database.transaction {
        val owner = repository.findVersionWithModpack(versionId)
            ?: throw RequestError("无此整合包版本")
        if (owner.modpack.id != modpackId) throw RequestError("版本不属于此整合包")
        if (owner.version.status != Modpack2VersionStatus.Ok) {
            throw RequestError("整合包版本尚未准备完成")
        }
        val rawBoundKeys = repository.findVersionManifest(versionId)
            ?.bindings
            ?.filter { it.source is Modpack2ContentSourceDto.RawSource }
            ?.map { it.contentKey }
            ?.toSet()
            .orEmpty()
        val contents = repository.listContents(versionId)
            .filter { it.side != ContentSide.Server }
            .filter { content ->
                rawBoundKeys.none {
                    it.platform == content.platform &&
                        it.type == content.type &&
                        it.projectId == content.projectId &&
                        it.fileId == content.fileId &&
                        it.hash.equals(content.hash, ignoreCase = true) &&
                        it.targetPath == content.targetPath &&
                        it.side == content.side
                }
            }
        Modpack2ClientManifestVo(
            modpackId = modpackId,
            versionId = versionId,
            mc = owner.modpack.mc,
            loader = owner.modpack.loader,
            contents = contents,
        )
    }

    /** Creates the modpack and its first Building version from a ready upload. */
    suspend fun create(player: RAccount, rawDto: Modpack2CreateDto): Modpack2CreateResult {
        val dto = rawDto.normalized()
        validateIconUrl(dto.modpack.iconUrl).getOrThrow()
        val ownerId = player._id.toUUID()
        val now = System.currentTimeMillis()

        findUploadResult(ownerId, dto.uploadId, now)?.let { return it.toCreateResult() }

        return ModpackService.modpack2ParallelUploadService
            .withReadyUpload(player._id, dto.uploadId) { uploadFile ->
                // The source archive may omit server/ when Provided processing
                // found no server-only raw bytes. Provided mode never inherits
                // a base source; it only records the uploaded bytes/content.
                Modpack2ArchiveIO.validateSourceArchive(uploadFile)
                val uploadedRawFiles = reconcileRawFiles(
                    declared = dto.version.rawFiles,
                    actual = Modpack2ArchiveIO.rawFileManifest(uploadFile),
                )
                val archiveManifest = Modpack2ArchiveIO.readManifest(uploadFile)
                if (archiveManifest.format != dto.version.manifest.format) {
                    throw RequestError("上传归档manifest格式与提交合同不一致")
                }
                val verifiedManifest = Modpack2ManifestValidator.validate(
                    manifest = dto.version.manifest,
                    contents = dto.version.contents,
                    rawFiles = uploadedRawFiles,
                    expectedMc = dto.modpack.mc,
                    expectedLoader = dto.modpack.loader,
                    actualManifestJson = archiveManifest.json,
                )
                val created = database.transaction {
                    repository.deleteExpiredUploadResults(now)
                    repository.findUploadResult(dto.uploadId, ownerId, now)?.let {
                        return@transaction CreatedVersion(
                            modpackId = it.modpackId,
                            versionId = it.versionId,
                            newlyCreated = false,
                        )
                    }
                    if (!player.isDav && repository.countByOwner(ownerId) >= MAX_MODPACK_PER_USER) {
                        throw RequestError("每个玩家最多拥有${MAX_MODPACK_PER_USER}个整合包")
                    }
                    if (repository.findModpackByNameIgnoreCase(dto.modpack.name) != null) {
                        throw RequestError("同名整合包已存在")
                    }

                    val modpack = repository.insertModpack(
                        ownerId = ownerId,
                        name = dto.modpack.name,
                        intro = dto.modpack.intro,
                        mc = dto.modpack.mc,
                        loader = dto.modpack.loader,
                        iconUrl = dto.modpack.iconUrl,
                        sourceUrl = dto.modpack.sourceUrl,
                    )
                    repository.replaceCategories(modpack.id, dto.modpack.categories)
                    val version = repository.insertVersion(
                        modpackId = modpack.id,
                        uploaderId = ownerId,
                        name = dto.version.name,
                        changelog = dto.version.changelog,
                        status = Modpack2VersionStatus.Building,
                        serverMode = dto.version.serverMode,
                    )
                    repository.replaceContents(
                        version.id,
                        dto.version.contents.map { it.copy(versionId = version.id) },
                    )
                    repository.replaceRawFiles(version.id, uploadedRawFiles)
                    repository.insertVersionManifest(version.id, verifiedManifest)
                    if (!repository.insertUploadResult(
                            Modpack2UploadResult(
                                uploadId = dto.uploadId,
                                ownerId = ownerId,
                                modpackId = modpack.id,
                                versionId = version.id,
                                expiresAt = now + IDEMPOTENCY_TTL_MILLIS,
                            )
                        )
                    ) {
                        throw RequestError("上传会话已被使用")
                    }
                    CreatedVersion(modpack.id, version.id, newlyCreated = true)
                }

                if (!created.newlyCreated) return@withReadyUpload created.toCreateResult()

                try {
                    storage.adoptSource(uploadFile, created.modpackId, created.versionId)
                    startBuild(player._id, created.modpackId, created.versionId)
                } catch (error: Throwable) {
                    compensateCreated(created, dto.uploadId)
                    throw error
                }
                created.toCreateResult()
            }
            .getOrThrow()
    }

    /** Deletes all versions and the parent row after moving files aside. */
    suspend fun deleteModpack(player: RAccount, modpackId: UUID) = withModpackLock(modpackId) {
        val ownerId = player._id.toUUID()
        val versions = database.transaction {
            val modpack = repository.findModpackForUpdate(modpackId)
                ?: throw RequestError("无此整合包")
            requireOwner(modpack.ownerId, ownerId, player)
            repository.listVersions(modpackId)
        }
        val referenced = database.transaction {
            versions.any { host2Repository.isModpack2VersionReferenced(it.id) }
        }
        if (referenced) throw RequestError("此整合包仍被新版房间使用")
        cancelBuilding(versions)

        val moved = mutableListOf<Modpack2DeletingVersion>()
        try {
            versions.forEach { version ->
                storage.moveToDeleting(modpackId, version.id)?.let(moved::add)
            }
            database.transaction {
                val modpack = repository.findModpackForUpdate(modpackId)
                    ?: throw RequestError("无此整合包")
                requireOwner(modpack.ownerId, ownerId, player)
                if (!repository.deleteModpack(modpackId)) {
                    throw RequestError("无此整合包")
                }
            }
        } catch (error: Throwable) {
            moved.asReversed().forEach(storage::restore)
            throw error
        }
        moved.forEach(storage::postCommitCleanup)
    }

    /** Deletes one version under its parent modpack lock. */
    suspend fun deleteVersion(player: RAccount, versionId: UUID) {
        val parentId = database.transaction {
            repository.findVersionWithModpack(versionId)?.modpack?.id
        } ?: throw RequestError("无此整合包版本")

        withModpackLock(parentId) {
            val ownerId = player._id.toUUID()
            val owner = database.transaction {
                repository.findVersionWithModpackForUpdate(versionId)
                    ?: throw RequestError("无此整合包版本")
            }
            if (database.transaction { host2Repository.isModpack2VersionReferenced(versionId) }) {
                throw RequestError("此整合包版本仍被新版房间使用")
            }
            requireOwner(owner.modpack.ownerId, ownerId, player)
            if (owner.version.status == Modpack2VersionStatus.Building) {
                versionRuns[versionId]?.let { ServerTaskManager.cancelAndJoin(it) }
            }

            val moved = storage.moveToDeleting(parentId, versionId)
            try {
                database.transaction {
                    val locked = repository.findVersionWithModpackForUpdate(versionId)
                        ?: throw RequestError("无此整合包版本")
                    requireOwner(locked.modpack.ownerId, ownerId, player)
                    if (!repository.deleteVersion(versionId)) {
                        throw RequestError("无此整合包版本")
                    }
                }
            } catch (error: Throwable) {
                moved?.let(storage::restore)
                throw error
            }
            storage.postCommitCleanup(moved)
            versionRuns.remove(versionId)
        }
    }

    /** Converts versions left in Building by a process restart to Fail. */
    suspend fun recover() {
        val failures = database.transaction { repository.markAllBuildingFailed() }
        failures.forEach { failure ->
            runCatching {
                MailService.sendSystemMail(
                    failure.ownerId.objectId,
                    "整合包版本构建中断",
                    "整合包版本${failure.versionId}因服务器重启中断，请重新提交构建。",
                )
            }.onFailure { error ->
                lgr.error(error) { "整合包版本${failure.versionId}中断邮件发送失败" }
            }
        }
    }

    fun close() {
        modpackLocks.clear()
        versionRuns.clear()
    }

    private suspend fun findUploadResult(
        ownerId: UUID,
        uploadId: UUID,
        now: Long,
    ): Modpack2UploadResult? = database.transaction {
        repository.deleteExpiredUploadResults(now)
        repository.findUploadResult(uploadId, ownerId, now)
    }

    private suspend fun publicClientPack(
        player: RAccount,
        modpackId: UUID,
        versionId: UUID,
    ): java.io.File {
        database.transaction {
            val owner = repository.findVersionWithModpack(versionId)
                ?: throw RequestError("无此整合包版本")
            if (owner.modpack.id != modpackId) throw RequestError("版本不属于此整合包")
            if (owner.version.status != Modpack2VersionStatus.Ok) {
                throw RequestError("整合包版本尚未准备完成")
            }
        }
        val file = storage.paths(modpackId, versionId).clientArchive.toFile()
        if (!file.isFile) throw RequestError("整合包客户端归档暂时不可用")
        return file
    }

    private fun Modpack2Version.toVersionDetail(
        contents: List<Modpack2Content>,
        rawFiles: List<Modpack2RawFile>,
        manifest: Modpack2VersionManifestDto? = null,
    ): Modpack2VersionDetailVo = Modpack2VersionDetailVo(
        id = id,
        modpackId = modpackId,
        uploaderId = uploaderId,
        name = name,
        changelog = changelog,
        status = status,
        totalSize = totalSize,
        serverMode = serverMode,
        baseVersionId = baseVersionId,
        contents = contents,
        rawFiles = rawFiles,
        manifest = manifest,
    )

    private fun Modpack2.toCreateModpackDto(categories: List<Modpack2Category>): Modpack2CreateModpackDto =
        Modpack2CreateModpackDto(
            name = name,
            intro = intro,
            mc = mc,
            loader = loader,
            iconUrl = iconUrl,
            sourceUrl = sourceUrl,
            categories = categories.map { it.category },
        )

    private fun Modpack2VersionCreateFromUploadDto.normalizedVersionCreate(): NormalizedVersionCreateDto {
        val normalizedName = normalizeVersionName(name)
        val normalizedChangelog = changelog.trim()
        if (normalizedChangelog.length > MAX_CHANGELOG_LENGTH) {
            throw RequestError("版本更新说明过长")
        }
        val normalizedContents = contents.map { it.normalized() }
        if (normalizedContents.map(::contentIdentity).toSet().size != normalizedContents.size) {
            throw RequestError("版本内容存在重复身份")
        }
        val normalizedRawFiles = rawFiles.map(::normalizeRawFile)
        if (normalizedRawFiles.map { it.root to it.path }.toSet().size != normalizedRawFiles.size) {
            throw RequestError("原始文件存在重复路径")
        }
        val inherited = inheritedServerFiles.map { declaration ->
            val path = normalizeInheritedServerPath(declaration.path)
            validateRawPath(path)
            val hash = declaration.sha1.trim().lowercase()
            if (!SHA1_PATTERN.matches(hash)) throw RequestError("继承文件SHA-1格式不正确")
            Modpack2InheritedRawFile(path, hash)
        }
        if (inherited.map { it.path }.toSet().size != inherited.size) {
            throw RequestError("继承文件存在重复路径")
        }
        return NormalizedVersionCreateDto(
            uploadId = uploadId,
            baseVersionId = baseVersionId,
            name = normalizedName,
            changelog = normalizedChangelog,
            serverMode = serverMode,
            contents = normalizedContents,
            rawFiles = normalizedRawFiles,
            inheritedServerFiles = inherited,
            modpack = modpack,
            manifest = manifest,
        )
    }

    private fun normalizeInheritedServerPath(raw: String): String {
        val normalized = raw.trim().replace('\\', '/')
        val firstSlash = normalized.indexOf('/')
        return if (firstSlash > 0 && normalized.substring(0, firstSlash).equals("server", ignoreCase = true)) {
            normalized.substring(firstSlash + 1)
        } else {
            normalized
        }
    }

    private fun normalizeRawFile(file: Modpack2RawFile): Modpack2RawFile {
        val path = file.path.trim().replace('\\', '/')
        validateRawPath(path)
        val hash = file.sha1.trim().lowercase()
        if (!SHA1_PATTERN.matches(hash)) throw RequestError("原始文件SHA-1格式不正确")
        if (file.size < 0) throw RequestError("原始文件大小无效")
        return file.copy(path = path, sha1 = hash)
    }

    /**
     * The archive is the source of truth for raw bytes.  A non-empty client
     * declaration is still accepted for compatibility, but it must describe
     * exactly the extracted root/path/hash/size manifest.  An omitted
     * declaration is filled from the archive so old create payloads continue
     * to work while the persisted projection remains truthful.
     */
    private fun reconcileRawFiles(
        declared: List<Modpack2RawFile>,
        actual: List<Modpack2RawFile>,
    ): List<Modpack2RawFile> {
        val normalizedActual = actual.map(::normalizeRawFile)
            .sortedWith(compareBy<Modpack2RawFile>({ it.root.name }, { it.path }))
        val normalizedDeclared = declared.map(::normalizeRawFile)
        if (normalizedDeclared.map { it.root to it.path }.toSet().size != normalizedDeclared.size) {
            throw RequestError("原始文件存在重复路径")
        }
        if (normalizedDeclared.isNotEmpty() && normalizedDeclared.toSet() != normalizedActual.toSet()) {
            throw RequestError("原始文件声明与上传归档不匹配")
        }
        return normalizedActual
    }

    /** Merges source and inherited server manifests without hiding conflicts. */
    private fun mergeRawFiles(
        source: List<Modpack2RawFile>,
        inherited: List<Modpack2RawFile>,
    ): List<Modpack2RawFile> {
        val merged = linkedMapOf<Pair<Modpack2RawFileRoot, String>, Modpack2RawFile>()
        (source + inherited).forEach { raw ->
            val normalized = normalizeRawFile(raw)
            val key = normalized.root to normalized.path
            val previous = merged[key]
            if (previous != null && (previous.sha1 != normalized.sha1 || previous.size != normalized.size)) {
                throw RequestError("上传source与继承文件冲突: ${normalized.path}")
            }
            merged.putIfAbsent(key, normalized)
        }
        return merged.values.sortedWith(compareBy<Modpack2RawFile>({ it.root.name }, { it.path }))
    }

    private fun materializeGeneratedSource(
        uploadFile: java.io.File,
        parentId: UUID,
        versionId: UUID?,
        targetModpackId: UUID,
        targetVersionId: UUID,
        serverMode: Modpack2ServerMode,
        inheritedServerFiles: List<Modpack2InheritedRawFile>,
    ): java.io.File {
        if (serverMode != Modpack2ServerMode.Generated ||
            versionId == null ||
            inheritedServerFiles.isEmpty()
        ) {
            return uploadFile
        }
        val baseSource = storage.paths(parentId, versionId).sourceArchive.toFile()
        if (!baseSource.isFile) {
            throw RequestError("基础版本冻结源文件暂时不可用，请复制后重新发布")
        }
        val target = storage.paths(targetModpackId, targetVersionId).directory
            .resolve(".generated-source-${UUID.randomUUID()}.tar.zst")
            .toFile()
        try {
            Modpack2ArchiveIO.materializeGeneratedAppend(
                sourceArchive = uploadFile,
                baseSourceArchive = baseSource,
                inheritedServerFiles = inheritedServerFiles,
                targetArchive = target,
            )
            return target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Files.deleteIfExists(target.toPath())
            throw RequestError("无法合并基础版本server文件", error)
        }
    }

    private fun validateRawPath(path: String) {
        if (path.isBlank() || path.length > MAX_RAW_PATH_LENGTH ||
            path.startsWith('/') || path.startsWith('\\') || WINDOWS_ABSOLUTE_PATH.matches(path)
        ) {
            throw RequestError("原始文件路径无效")
        }
        if (path.split('/').any { invalidServerPathSegment(it) }) {
            throw RequestError("原始文件路径无效")
        }
    }

    private suspend fun startBuild(owner: ObjectId, modpackId: UUID, versionId: UUID) {
        val runId = ServerTaskManager.submit(
            task = buildTask(owner, modpackId, versionId),
            dedupeKey = "modpack2-version:$versionId",
            autoStart = false,
        )
        versionRuns[versionId] = runId
        try {
            ServerTaskManager.start(runId)
        } catch (error: Throwable) {
            versionRuns.remove(versionId, runId)
            ServerTaskManager.cancelAndJoin(runId)
            ServerTaskManager.remove(runId)
            throw error
        }
    }

    private fun buildTask(owner: ObjectId, modpackId: UUID, versionId: UUID): Task2 =
        Task2.Leaf(title = "构建整合包版本") { context ->
            var mailId: UUID? = null
            try {
                mailId = runCatching {
                    MailService.sendSystemMail(
                        owner,
                        "整合包版本构建中",
                        "开始构建整合包版本${versionId}。",
                    ).id
                }.getOrElse { error ->
                    lgr.error(error) { "整合包版本${versionId}进度邮件发送失败" }
                    null
                }
                val persistedContract = database.transaction {
                    repository.findVersionManifest(versionId) to repository.listRawFiles(versionId)
                }
                val persistedPack = database.transaction { repository.findModpack(modpackId) }
                val contents = database.transaction { repository.listContents(versionId) }
                updateProgress(context, mailId, "验证整合包内容", 0.1f)
                Modpack2ContentValidator.validateAll(
                    contents,
                    manifest = persistedContract.first,
                    rawFiles = persistedContract.second,
                    expectedMc = persistedPack?.mc,
                    expectedLoader = persistedPack?.loader,
                )
                updateProgress(context, mailId, "验证整合包归档", 0.15f)
                val paths = storage.paths(modpackId, versionId)
                val temporaryServerArchive = paths.directory.resolve(
                    ".server-${UUID.randomUUID()}.tar.zst"
                )
                val result = withContext(Dispatchers.IO) {
                    try {
                        Modpack2ArchiveIO.buildMergedArchives(
                            sourceArchive = paths.sourceArchive.toFile(),
                            clientArchive = paths.clientArchive.toFile(),
                            serverArchive = temporaryServerArchive.toFile(),
                            requireMetadata = persistedContract.first != null,
                        )
                    } finally {
                        Files.deleteIfExists(temporaryServerArchive)
                    }
                }
                context.ensureActive()
                database.transaction {
                    if (!repository.transitionBuildingToOk(versionId, result.clientArchiveBytes)) {
                        throw RequestError("整合包版本状态已改变")
                    }
                }
                updateProgress(context, mailId, "整合包版本构建完成", 1f, append = false)
            } catch (cancelled: CancellationException) {
                database.transaction { repository.transitionBuildingToFail(versionId) }
                mailId?.let {
                    runCatching {
                        MailService.changeMailAndWait(
                            it,
                            newTitle = "整合包版本构建已取消",
                            newContent = "整合包版本${versionId}的构建已取消。",
                            append = false,
                        )
                    }.onFailure { error ->
                        lgr.error(error) { "整合包版本${versionId}取消邮件更新失败" }
                    }
                }
                throw cancelled
            } catch (error: Throwable) {
                database.transaction { repository.transitionBuildingToFail(versionId) }
                mailId?.let {
                    runCatching {
                        MailService.changeMailAndWait(
                            it,
                            newTitle = "整合包版本构建失败",
                            newContent = "无法构建整合包版本${versionId}：${error.message}",
                            append = false,
                        )
                    }.onFailure { mailError ->
                        lgr.error(mailError) { "整合包版本${versionId}失败邮件更新失败" }
                    }
                }
                throw error
            } finally {
                versionRuns.remove(versionId)
            }
        }

    private suspend fun updateProgress(
        context: Task2Context,
        mailId: UUID?,
        message: String,
        fraction: Float,
        append: Boolean = true,
    ) {
        context.ensureActive()
        context.emit(Task2Progress(message, fraction))
        mailId?.let {
            runCatching {
                MailService.changeMailAndWait(it, newContent = message, append = append)
            }.onFailure { error ->
                lgr.error(error) { "整合包版本进度邮件更新失败" }
            }
        }
    }

    private suspend fun cancelBuilding(versions: List<Modpack2Version>) {
        versions.asSequence()
            .filter { it.status == Modpack2VersionStatus.Building }
            .mapNotNull { versionRuns[it.id] }
            .forEach { ServerTaskManager.cancelAndJoin(it) }
    }

    private suspend fun compensateCreated(
        created: CreatedVersion,
        uploadId: UUID,
    ) {
        val moved = storage.moveToDeleting(created.modpackId, created.versionId)
        try {
            database.transaction {
                repository.deleteUploadResult(uploadId)
                if (!repository.deleteModpack(created.modpackId)) {
                    throw RequestError("创建整合包失败，清理数据库记录失败")
                }
            }
        } catch (error: Throwable) {
            moved?.let(storage::restore)
            throw error
        }
        moved?.let(storage::postCommitCleanup)
    }

    private suspend fun compensatePublished(
        created: PublishedVersion,
        uploadId: UUID,
    ) {
        val moved = storage.moveToDeleting(created.modpackId, created.versionId)
        try {
            database.transaction {
                repository.deleteUploadResult(uploadId)
                if (created.newlyCreatedModpack) {
                    if (!repository.deleteModpack(created.modpackId)) {
                        throw RequestError("发布整合包失败，清理数据库记录失败")
                    }
                } else if (!repository.deleteVersion(created.versionId)) {
                    throw RequestError("发布整合包失败，清理版本记录失败")
                }
            }
        } catch (error: Throwable) {
            moved?.let(storage::restore)
            throw error
        }
        moved?.let(storage::postCommitCleanup)
    }

    private suspend fun <T> withModpackLock(
        modpackId: UUID,
        block: suspend () -> T,
    ): T {
        val lock = modpackLocks.computeIfAbsent(modpackId) { Mutex() }
        return lock.withLock { block() }
    }

    private fun requireOwner(ownerId: UUID, requesterId: UUID, player: RAccount) {
        if (ownerId != requesterId && !player.isDav) throw RequestError("无权限")
    }

    private fun Modpack2CreateDto.normalized(): NormalizedCreateDto {
        val modpack = modpack.normalized()
        val version = version.normalized()
        return NormalizedCreateDto(
            uploadId = uploadId,
            modpack = modpack,
            version = version,
        )
    }

    private fun calebxzau.rdi.common.model.Modpack2CreateModpackDto.normalized(): NormalizedModpackDto {
        val normalizedName = name.trim()
        requireText(normalizedName, 1..64, "整合包名称")
        val normalizedIntro = intro.trim()
        requireText(normalizedIntro, 10..100, "整合包简介")
        val normalizedIcon = iconUrl.trim()
        requireText(normalizedIcon, 1..2048, "图标链接")
        val normalizedSource = sourceUrl?.trim()?.ifBlank { null }
        normalizedSource?.validateHttpUrl()?.getOrThrow()
        val mcVersion = McVersion.fromMinor(mc.toString())
            ?: throw RequestError("不支持的MC版本")
        val modLoader = loader.toModLoader()
        if (!mcVersion.supportLoader(modLoader)) {
            throw RequestError("不支持此MC版本与ModLoader组合")
        }
        val normalizedCategories = categories.distinct()
        if (normalizedCategories.size !in 1..MAX_CATEGORIES) {
            throw RequestError("分类数量须在1到${MAX_CATEGORIES}个")
        }
        return NormalizedModpackDto(
            name = normalizedName,
            intro = normalizedIntro,
            mc = mc,
            loader = loader,
            iconUrl = normalizedIcon,
            sourceUrl = normalizedSource,
            categories = normalizedCategories,
        )
    }

    private fun calebxzau.rdi.common.model.Modpack2CreateVersionDto.normalized(): NormalizedVersionDto {
        val normalizedName = normalizeVersionName(name)
        val normalizedChangelog = changelog.trim()
        if (normalizedChangelog.length > MAX_CHANGELOG_LENGTH) {
            throw RequestError("版本更新说明过长")
        }
        val normalizedContents = contents.orEmpty().map { it.normalized() }
        if (normalizedContents.map(::contentIdentity).toSet().size != normalizedContents.size) {
            throw RequestError("版本内容存在重复身份")
        }
        if (inheritedServerFiles.isNotEmpty()) {
            throw RequestError("新建整合包没有基础版本可继承server文件")
        }
        val normalizedRawFiles = rawFiles.map(::normalizeRawFile)
        if (normalizedRawFiles.map { it.root to it.path }.toSet().size != normalizedRawFiles.size) {
            throw RequestError("原始文件存在重复路径")
        }
        return NormalizedVersionDto(
            name = normalizedName,
            changelog = normalizedChangelog,
            contents = normalizedContents,
            serverMode = serverMode,
            rawFiles = normalizedRawFiles,
            manifest = manifest,
        )
    }

    private fun calebxzau.rdi.common.model.Modpack2ContentDto.normalized(): Modpack2Content {
        val normalizedProject = projectId.trim()
        val normalizedFile = fileId.trim()
        val normalizedSlug = slug.trim()
        val normalizedHash = hash.trim().lowercase()
        if (normalizedProject.isBlank() || normalizedFile.isBlank() || normalizedSlug.isBlank()) {
            throw RequestError("版本内容字段不能为空")
        }
        when (platform) {
            ContentPlatform.CurseForge -> {
                Modpack2ContentValidator.parseCurseForgeIds(normalizedProject, normalizedFile)
            }
            ContentPlatform.Modrinth -> {
                requireContentToken(normalizedProject, "projectId")
                requireContentToken(normalizedFile, "fileId")
            }
            ContentPlatform.GitHub -> {
                Modpack2ContentValidator.parseGitHubProjectId(normalizedProject)
                Modpack2ContentValidator.parseGitHubFileId(normalizedFile)
            }
        }
        requireContentToken(normalizedSlug, "slug")
        validateHash(platform, normalizedHash)
        if (fileSize < 0) throw RequestError("版本内容文件大小无效")

        val normalizedTarget = targetPath?.trim()?.ifBlank { null }
        val target = when (type) {
            ContentType.Mod, ContentType.ShaderPack, ContentType.ResPack ->
                normalizedTarget?.also(::validateTargetPath)
            ContentType.DataPack, ContentType.Other ->
                normalizedTarget?.also(::validateTargetPath)
                    ?: throw RequestError("此内容类型必须指定目标路径")
        }
        if (!required && side != ContentSide.Client) {
            throw RequestError("非必选内容只能用于客户端")
        }
        return Modpack2Content(
            versionId = UUID(0L, 0L),
            platform = platform,
            type = type,
            projectId = normalizedProject,
            fileId = normalizedFile,
            slug = normalizedSlug,
            hash = normalizedHash,
            targetPath = target,
            side = side,
            required = required,
            fileSize = fileSize,
        )
    }

    private fun validateHash(platform: ContentPlatform, hash: String) {
        when (platform) {
            ContentPlatform.CurseForge -> {
                val fingerprint = hash.toLongOrNull()
                if (fingerprint == null || fingerprint !in 0..MURMUR2_MAX) {
                    throw RequestError("CurseForge指纹格式不正确")
                }
            }
            ContentPlatform.Modrinth -> if (!SHA1_PATTERN.matches(hash)) {
                throw RequestError("Modrinth SHA-1格式不正确")
            }
            ContentPlatform.GitHub -> if (!SHA256_PATTERN.matches(hash)) {
                throw RequestError("GitHub SHA-256格式不正确")
            }
        }
    }

    private fun requireContentToken(value: String, field: String) {
        if (value.length > MAX_CONTENT_TOKEN_LENGTH || value.any(Char::isISOControl) || value.any(Char::isWhitespace)) {
            throw RequestError("${field}格式不正确")
        }
    }

    private fun validateTargetPath(path: String) {
        if (path.length > MAX_TARGET_PATH_LENGTH ||
            path.startsWith('/') ||
            path.startsWith('\\') ||
            WINDOWS_ABSOLUTE_PATH.matches(path) ||
            path.indexOf('\u0000') >= 0
        ) {
            throw RequestError("内容目标路径无效")
        }
        val segments = path.replace('\\', '/').split('/')
        if (segments.any(::invalidServerPathSegment)) {
            throw RequestError("内容目标路径无效")
        }
    }

    private fun contentIdentity(content: Modpack2Content): List<Any?> = listOf(
        content.platform,
        content.type,
        content.projectId,
        content.fileId,
        content.hash.lowercase(),
        content.targetPath,
        content.side,
    )

    private fun invalidServerPathSegment(segment: String): Boolean =
        segment.isBlank() || segment == "." || segment == ".." || segment.endsWith('.') ||
            segment.endsWith(' ') || segment.contains(':') || WINDOWS_RESERVED_SEGMENT.matches(segment)

    private fun normalizeVersionName(raw: String): String {
        val trimmed = raw.trim()
        val normalized = if (trimmed.startsWith('v', ignoreCase = true)) {
            trimmed.drop(1).trimStart()
        } else {
            trimmed
        }
        requireText(normalized, 1..32, "版本名")
        return normalized
    }

    private fun requireText(value: String, range: IntRange, field: String) {
        val length = value.codePointCount(0, value.length)
        if (length !in range || value.any(Char::isISOControl)) {
            throw RequestError("${field}长度无效")
        }
    }

    private fun Modpack2Loader.toModLoader(): ModLoader = when (this) {
        Modpack2Loader.Forge -> ModLoader.forge
        Modpack2Loader.NeoForge -> ModLoader.neoforge
    }

    private data class NormalizedCreateDto(
        val uploadId: UUID,
        val modpack: NormalizedModpackDto,
        val version: NormalizedVersionDto,
    )

    private data class NormalizedModpackDto(
        val name: String,
        val intro: String,
        val mc: Int,
        val loader: Modpack2Loader,
        val iconUrl: String,
        val sourceUrl: String?,
        val categories: List<ModpackCategory>,
    )

    private data class NormalizedVersionDto(
        val name: String,
        val changelog: String,
        val contents: List<Modpack2Content>,
        val serverMode: Modpack2ServerMode,
        val rawFiles: List<Modpack2RawFile>,
        val manifest: Modpack2VersionManifestDto,
    )

    private data class NormalizedVersionCreateDto(
        val uploadId: UUID,
        val baseVersionId: UUID?,
        val name: String,
        val changelog: String,
        val serverMode: Modpack2ServerMode,
        val contents: List<Modpack2Content>,
        val rawFiles: List<Modpack2RawFile>,
        val inheritedServerFiles: List<calebxzau.rdi.common.model.Modpack2InheritedRawFile>,
        val modpack: calebxzau.rdi.common.model.Modpack2CreateModpackDto?,
        val manifest: Modpack2VersionManifestDto,
    )

    private data class CreatedVersion(
        val modpackId: UUID,
        val versionId: UUID,
        val newlyCreated: Boolean,
    ) {
        fun toCreateResult() = Modpack2CreateResult(
            modpackId = modpackId,
            versionId = versionId,
            status = Modpack2VersionStatus.Building,
        )
    }

    private data class PublishedVersion(
        val modpackId: UUID,
        val versionId: UUID,
        val newlyCreatedModpack: Boolean,
        val newlyCreatedVersion: Boolean,
    ) {
        fun toCreateResult() = Modpack2CreateResult(
            modpackId = modpackId,
            versionId = versionId,
            status = Modpack2VersionStatus.Building,
        )
    }

    private companion object {
        const val MAX_MODPACK_PER_USER = 10L
        const val MAX_CATEGORIES = 4
        const val MAX_CHANGELOG_LENGTH = 10_000
        const val IDEMPOTENCY_TTL_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_TARGET_PATH_LENGTH = 512
        const val MAX_RAW_PATH_LENGTH = 1024
        const val MAX_CONTENT_TOKEN_LENGTH = 512
        const val MURMUR2_MAX = 4_294_967_295L
        val SHA1_PATTERN = Regex("^[0-9a-f]{40}$")
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
        val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/.*")
        val WINDOWS_RESERVED_SEGMENT = Regex("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?$")
    }
}

private const val MODPACK2_PUBLIC_PAGE_SIZE = 100

private fun Modpack2UploadResult.toCreateResult() = Modpack2CreateResult(
    modpackId = modpackId,
    versionId = versionId,
    status = Modpack2VersionStatus.Building,
)
