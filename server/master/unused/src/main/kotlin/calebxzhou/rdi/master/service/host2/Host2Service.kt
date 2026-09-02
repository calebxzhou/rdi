package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2MemberRole
import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.Host2PackInfo
import calebxzhou.rdi.common.model.Host2ErrorCode
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.PackSource
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.model.isHost2Available
import calebxzhou.rdi.common.service.validateIconUrl
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.PlayerService
import calebxzhou.rdi.model.Role
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.postgresql.util.PSQLException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class Host2Service(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val packSourceService: Host2PackSourceService,
) {
    private val lgr by Loggers
    private val mutationLocks = ConcurrentHashMap<UUID, Mutex>()

    suspend fun create(player: RAccount, rawDto: Host2.CreateDto): Host2.DetailVo {
        val resolvedPack = packSourceService.resolveForCreate(rawDto.packSource)
        val dto = rawDto.normalized().copy(packSource = resolvedPack.source)
        repeat(PORT_ALLOCATION_ATTEMPTS) {
            try {
                val record = database.transaction {
                    lockAccounts(player._id.toUUID())
                    if (repository.ownerCount(player._id.toUUID()) >= OWNER_LIMIT) {
                        throw RequestError("每个玩家最多拥有${OWNER_LIMIT}个新版房间")
                    }
                    repository.insert(
                        ownerId = player._id.toUUID(),
                        dto = dto,
                        port = Random.nextInt(PORT_START, PORT_END_EXCLUSIVE)
                    )
                }
                return record.toDetailVo(Role.OWNER, emptyList(), resolvedPack.packInfo)
            } catch (error: ExposedSQLException) {
                if (error.constraintName() != HOST2_PORT_UNIQUE) throw error
            }
        }
        throw RequestError("暂时无法分配房间端口，请稍后重试")
    }

    suspend fun detail(player: RAccount, id: UUID): Host2.DetailVo {
        val result = database.transaction {
            val record = repository.findById(id) ?: throw RequestError("无此新版房间")
            record to roleOf(record, player)
        }
        val (record, role) = result
        if (role == Role.GUEST && (record.whitelist || record.packStatus != Host2PackStatus.Ok)) {
            throw RequestError("无权查看此新版房间")
        }
        val members = database.transaction { repository.members(id) }
        val pack = packSourceService.resolveExact(record.packSource).getOrNull()?.packInfo
        return record.toDetailVo(role, members, pack)
    }

    suspend fun list(player: RAccount, myOnly: Boolean, page: Int): List<Host2.BriefVo> {
        val playerId = player._id.toUUID()
        val (allRecords, memberRoles) = database.transaction {
            repository.listAll() to repository.memberRoles(playerId)
        }
        val statuses = Host2RuntimeService.statusSnapshot(allRecords.map { it.id })
        val candidates = allRecords.map { record ->
            val role = roleOf(record, playerId, memberRoles)
            Host2ListingCandidate(
                record = record,
                role = role,
                status = statuses.getValue(record.id),
                available = isHost2Available(
                    packStatus = record.packStatus,
                    status = statuses.getValue(record.id),
                    whitelist = record.whitelist,
                    isMember = role != Role.GUEST,
                ),
            )
        }
        val visible = selectHost2Candidates(candidates, myOnly, page, PAGE_SIZE)
        val packInfoBySource = mutableMapOf<PackSource, Host2PackInfo?>()
        suspend fun resolvePackInfo(source: PackSource): Host2PackInfo? {
            if (packInfoBySource.containsKey(source)) return packInfoBySource[source]
            val pack = packSourceService.resolveExact(source).getOrNull()?.packInfo
            packInfoBySource[source] = pack
            return pack
        }
        return visible.map { candidate ->
            val pack = resolvePackInfo(candidate.record.packSource)
            candidate.record.toBriefVo(candidate.role, pack, candidate.status)
        }
    }

    suspend fun options(player: RAccount, id: UUID, rawDto: Host2.OptionsDto) {
        val dto = rawDto.normalized()
        if (rawDto.iconUrl != null) {
            requireAdmin(player, id)
            validateIconUrl(dto.iconUrl).getOrThrow()
        }
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            val role = roleOf(record, player)
            requireAdmin(role, player)
            repository.updateOptions(id, dto)
        }
    }

    suspend fun invite(player: RAccount, id: UUID, qq: String) {
        val normalizedQq = qq.trim()
        val target = PlayerService.getByQQ(normalizedQq) ?: throw RequestError("找不到QQ为${normalizedQq}的玩家")
        val targetId = target._id.toUUID()
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            requireAdmin(roleOf(record, player), player)
            lockAccounts(targetId)
            if (targetId == record.ownerId || repository.member(id, targetId) != null) {
                throw RequestError("该玩家已经在新版房间中")
            }
            if (repository.participantCount(id) >= PARTICIPANT_LIMIT) {
                throw RequestError("新版房间最多${PARTICIPANT_LIMIT}名参与者")
            }
            if (repository.joinedCount(targetId) >= JOINED_LIMIT) {
                throw RequestError("该玩家加入的新版房间已达上限")
            }
            repository.insertMember(id, targetId, Host2MemberRole.MEMBER)
        }
    }

    suspend fun setMemberRole(
        player: RAccount,
        id: UUID,
        targetId: UUID,
        role: Host2MemberRole
    ) {
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            requireOwner(roleOf(record, player), player)
            if (!repository.updateMemberRole(id, targetId, role)) {
                throw RequestError("目标玩家不是此新版房间成员")
            }
        }
    }

    suspend fun removeMember(player: RAccount, id: UUID, targetId: UUID) {
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            requireOwner(roleOf(record, player), player)
            if (targetId == record.ownerId || !repository.deleteMember(id, targetId)) {
                throw RequestError("目标玩家不是可移除成员")
            }
        }
    }

    suspend fun quit(player: RAccount, id: UUID) {
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            if (record.ownerId == player._id.toUUID()) {
                throw RequestError("OWNER必须先转移ownership或删除新版房间")
            }
            if (!repository.deleteMember(id, player._id.toUUID())) {
                throw RequestError("你不是此新版房间成员")
            }
        }
    }

    suspend fun transferOwnership(player: RAccount, id: UUID, targetId: UUID) {
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            requireOwner(roleOf(record, player), player)
            repository.member(id, targetId) ?: throw RequestError("目标玩家必须已经是成员")
            lockAccounts(record.ownerId, targetId)
            if (repository.ownerCount(targetId) >= OWNER_LIMIT) {
                throw RequestError("目标玩家拥有的新版房间已达上限")
            }
            if (repository.joinedCount(record.ownerId) >= JOINED_LIMIT) {
                throw RequestError("转移后你加入的新版房间将超过上限")
            }
            repository.updateOwner(id, targetId)
            repository.deleteMember(id, targetId)
            repository.insertMember(id, record.ownerId, Host2MemberRole.ADMIN)
        }
    }

    suspend fun delete(player: RAccount, id: UUID) = withMutation(id) {
        val operation = database.transaction {
            val record = repository.findById(id) ?: throw RequestError("无此新版房间")
            requireOwner(roleOf(record, player), player)
            repository.insertOperation(
                NewHost2Operation(
                    hostId = id,
                    kind = Host2OperationKind.Delete,
                    phase = "Prepare",
                )
            )
        }
        Host2RuntimeService.forceRemove(id)
        val hostDir = HOST2_DIR.resolve(id.toString())
        val deletingDir = HOST2_DIR.resolve(".deleting").resolve(operation.id.toString())
        var moved = false
        try {
            if (hostDir.exists()) {
                deletingDir.parentFile.mkdirs()
                moveDirectory(hostDir, deletingDir)
                moved = true
            }
            database.transaction {
                val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                requireOwner(roleOf(record, player), player)
                repository.updateOperation(operation.id, "DatabaseCommit", backupPath = deletingDir.absolutePath)
                repository.insertDeleteTombstone(operation.id, id, deletingDir.absolutePath)
                if (!repository.delete(id)) throw RequestError("删除新版房间失败")
            }
        } catch (error: Throwable) {
            if (moved && deletingDir.exists() && !hostDir.exists()) {
                runCatching { moveDirectory(deletingDir, hostDir) }
            }
            database.transaction { repository.deleteOperation(operation.id) }
            throw error
        }
        runCatching {
            if (deletingDir.exists()) deletingDir.deleteRecursivelyNoSymlink()
            if (deletingDir.exists()) throw RequestError("房间删除目录清理失败")
            database.transaction {
                repository.deleteDeleteTombstone(operation.id)
                repository.deleteOperation(operation.id)
            }
        }.onFailure { error ->
            lgr.error(error) { "Host2 ${id}删除后的目录清理失败，启动恢复将继续处理" }
        }
        mutationLocks.remove(id)
    }

    suspend fun requireAdmin(player: RAccount, id: UUID): Host2Record = database.transaction {
        val record = repository.findById(id) ?: throw RequestError("无此新版房间")
        requireAdmin(roleOf(record, player), player)
        record
    }

    suspend fun requireOwner(player: RAccount, id: UUID): Host2Record = database.transaction {
        val record = repository.findById(id) ?: throw RequestError("无此新版房间")
        requireOwner(roleOf(record, player), player)
        record
    }

    suspend fun requireCanStart(player: RAccount, id: UUID): Host2StartContext {
        val record = database.transaction {
            val value = repository.findById(id) ?: throw RequestError("无此新版房间")
            val role = roleOf(value, player)
            if (role == Role.GUEST && !player.isDav) throw RequestError("仅新版房间成员可启动")
            if (value.packStatus != Host2PackStatus.Ok || value.activeContentRevision == 0L) {
                throw RequestError("房间整合包尚未准备完成")
            }
            value
        }
        validateDeploymentMarker(HOST2_DIR.resolve(id.toString()), record)
        val pack = packSourceService.resolveExact(record.packSource).getOrElse {
            throw RequestError("房间整合包来源暂时不可用", it, Host2ErrorCode.SOURCE_UNAVAILABLE)
        }
        return Host2StartContext(record, pack.packInfo.mcVersion, pack.packInfo.modLoader)
    }

    suspend fun <T> withMutation(
        id: UUID,
        block: suspend () -> T
    ): T {
        val mutex = mutationLocks.computeIfAbsent(id) { Mutex() }
        if (!mutex.tryLock()) throw RequestError("新版房间正在执行其他操作")
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }

    private fun roleOf(record: Host2Record, player: RAccount): Role {
        return roleOf(record, player._id.toUUID(), repository.member(record.id, player._id.toUUID())?.role)
    }

    private fun roleOf(record: Host2Record, playerId: UUID, memberRoles: Map<UUID, Host2MemberRole>): Role =
        roleOf(record, playerId, memberRoles[record.id])

    private fun roleOf(record: Host2Record, playerId: UUID, memberRole: Host2MemberRole?): Role {
        if (record.ownerId == playerId) return Role.OWNER
        return memberRole?.toRole() ?: Role.GUEST
    }

    private fun Host2Record.toBriefVo(
        role: Role,
        pack: calebxzhou.rdi.common.model.Host2PackInfo?,
        knownStatus: HostStatus = Host2RuntimeService.status(id),
    ) = Host2.BriefVo(
        id = id,
        name = name,
        intro = intro,
        iconUrl = iconUrl,
        ownerId = ownerId,
        packSource = packSource,
        packStatus = packStatus,
        activeContentRevision = activeContentRevision,
        pendingContentRevision = pendingContentRevision,
        pack = pack,
        port = port,
        whitelist = whitelist,
        status = knownStatus,
        role = role,
        onlinePlayerIds = Host2RuntimeService.onlinePlayerIds(id)
    )

    private fun Host2Record.toDetailVo(
        role: Role,
        members: List<Host2MemberRecord>,
        pack: calebxzhou.rdi.common.model.Host2PackInfo?,
    ) = Host2.DetailVo(
        id = id,
        name = name,
        intro = intro,
        iconUrl = iconUrl,
        ownerId = ownerId,
        packSource = packSource,
        packStatus = packStatus,
        activeContentRevision = activeContentRevision,
        pendingContentRevision = pendingContentRevision,
        pack = pack,
        port = port,
        whitelist = whitelist,
        status = Host2RuntimeService.status(id),
        role = role,
        onlinePlayerIds = Host2RuntimeService.onlinePlayerIds(id),
        members = members.map { Host2.Member(it.playerId, it.role) },
    )

    private fun requireAdmin(role: Role, player: RAccount) {
        if (!player.isDav && role.level > Role.ADMIN.level) throw RequestError("无权限")
    }

    private fun requireOwner(role: Role, player: RAccount) {
        if (!player.isDav && role != Role.OWNER) throw RequestError("无权限")
    }
}

internal data class Host2ListingCandidate(
    val record: Host2Record,
    val role: Role,
    val status: HostStatus,
    val available: Boolean,
)

internal fun selectHost2Candidates(
    candidates: List<Host2ListingCandidate>,
    myOnly: Boolean,
    page: Int,
    pageSize: Int,
): List<Host2ListingCandidate> {
    val filtered = candidates.filter { it.available == myOnly }
    if (myOnly) return filtered
    val safePage = page.coerceAtLeast(0)
    val safeSize = pageSize.coerceAtLeast(1)
    return filtered.drop(safePage * safeSize).take(safeSize)
}

data class Host2StartContext(
    val host: Host2Record,
    val mcVersion: calebxzhou.rdi.common.model.McVersion,
    val modLoader: calebxzhou.rdi.common.model.ModLoader,
)

private fun JdbcTransaction.lockAccounts(vararg ids: UUID) {
    ids.distinct().sortedBy(UUID::toString).forEach { id ->
        exec(
            "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            listOf(UUIDColumnType() to id)
        )
    }
}

private fun Host2.CreateDto.normalized() = copy(
    name = normalizeHost2Name(name),
    intro = normalizeHost2Intro(intro)
)

private fun Host2.OptionsDto.normalized() = copy(
    name = name?.let(::normalizeHost2Name),
    intro = intro?.let(::normalizeHost2Intro),
    iconUrl = iconUrl?.trim()
)

private fun normalizeHost2Name(raw: String): String {
    val value = raw.trim()
    val length = value.codePointCount(0, value.length)
    if (length !in 1..32) throw RequestError("新版房间名称长度须在1~32个字符")
    if (value.any { it.isISOControl() }) throw RequestError("新版房间名称不能包含控制字符")
    return value
}

private fun normalizeHost2Intro(raw: String): String {
    val value = raw.trim().ifBlank { "暂无简介" }
    if (value.codePointCount(0, value.length) > 200) throw RequestError("新版房间简介最多200个字符")
    if (value.any { it.isISOControl() }) throw RequestError("新版房间简介不能包含换行或控制字符")
    return value
}

private fun Host2MemberRole.toRole(): Role = when (this) {
    Host2MemberRole.ADMIN -> Role.ADMIN
    Host2MemberRole.MEMBER -> Role.MEMBER
}

private fun Throwable.constraintName(): String? =
    generateSequence(this) { it.cause }
        .filterIsInstance<PSQLException>()
        .firstOrNull()
        ?.serverErrorMessage
        ?.constraint

private fun moveDirectory(source: java.io.File, target: java.io.File) {
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
}

private const val PORT_START = 30000
private const val PORT_END_EXCLUSIVE = 40000
private const val PORT_ALLOCATION_ATTEMPTS = 32
private const val PAGE_SIZE = 100
private const val OWNER_LIMIT = 3L
private const val JOINED_LIMIT = 10L
private const val PARTICIPANT_LIMIT = 10L
private const val HOST2_PORT_UNIQUE = "host2_port_key"
