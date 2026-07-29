package calebxzhou.rdi.master.service.host2

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2MemberRole
import calebxzhou.rdi.common.model.Host2Operation
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.isDav
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class Host2Service(
    private val database: DatabaseProvider,
    private val repository: Host2Repository
) {
    private val mutationLocks = ConcurrentHashMap<UUID, Mutex>()
    private val operations = ConcurrentHashMap<UUID, Host2Operation>()

    suspend fun create(player: RAccount, rawDto: Host2.CreateDto): Host2.DetailVo {
        val dto = rawDto.normalized()
        validateVersionPair(dto.mcVersion, dto.modLoader)
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
                return record.toDetailVo(Role.OWNER, emptyList())
            } catch (error: ExposedSQLException) {
                if (error.constraintName() != HOST2_PORT_UNIQUE) throw error
            }
        }
        throw RequestError("暂时无法分配房间端口，请稍后重试")
    }

    suspend fun detail(player: RAccount, id: UUID): Host2.DetailVo = database.transaction {
        val record = repository.findById(id) ?: throw RequestError("无此新版房间")
        val role = roleOf(record, player)
        if (role == Role.GUEST && (record.whitelist || record.setupStatus != Host2SetupStatus.READY)) {
            throw RequestError("无权查看此新版房间")
        }
        record.toDetailVo(role, repository.members(id))
    }

    suspend fun list(player: RAccount, myOnly: Boolean, page: Int): List<Host2.BriefVo> =
        database.transaction {
            repository.listForPlayer(
                playerId = player._id.toUUID(),
                myOnly = myOnly,
                page = page.coerceAtLeast(0),
                pageSize = PAGE_SIZE
            ).map { record ->
                record.toBriefVo(roleOf(record, player))
            }
        }

    suspend fun options(player: RAccount, id: UUID, rawDto: Host2.OptionsDto) {
        if (rawDto.mcVersion == null != (rawDto.modLoader == null)) {
            throw RequestError("MC版本与ModLoader必须同时修改")
        }
        val dto = rawDto.normalized()
        if (rawDto.iconUrl != null) {
            requireAdmin(player, id)
            validateIconUrl(dto.iconUrl).getOrThrow()
        }
        database.transaction {
            val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
            val role = roleOf(record, player)
            requireAdmin(role, player)
            val mcVersion = dto.mcVersion
            val modLoader = dto.modLoader
            if (mcVersion != null && modLoader != null) {
                requireOwner(role, player)
                if (record.setupStatus !in VERSION_MUTABLE_STATUSES) {
                    throw RequestError("当前配置状态不能修改MC版本或ModLoader")
                }
                validateVersionPair(mcVersion, modLoader)
            }
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
        database.transaction {
            val record = repository.findById(id) ?: throw RequestError("无此新版房间")
            requireOwner(roleOf(record, player), player)
        }
        Host2RuntimeService.forceRemove(id)
        val hostDir = HOST2_DIR.resolve(id.toString())
        val deletingDir = HOST2_DIR.resolve(".deleting").resolve(id.toString())
        var moved = false
        try {
            database.transaction {
                val record = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                requireOwner(roleOf(record, player), player)
                if (hostDir.exists()) {
                    deletingDir.parentFile.mkdirs()
                    moveDirectory(hostDir, deletingDir)
                    moved = true
                }
                if (!repository.delete(id)) throw RequestError("删除新版房间失败")
            }
        } catch (error: Throwable) {
            if (moved && deletingDir.exists() && !hostDir.exists()) {
                runCatching { moveDirectory(deletingDir, hostDir) }
            }
            throw error
        }
        if (deletingDir.exists()) deletingDir.deleteRecursivelyNoSymlink()
        mutationLocks.remove(id)
    }

    fun operation(id: UUID): Host2Operation? = operations[id]

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

    suspend fun requireCanStart(player: RAccount, id: UUID): Host2Record = database.transaction {
        val record = repository.findById(id) ?: throw RequestError("无此新版房间")
        val role = roleOf(record, player)
        if (role == Role.GUEST && !player.isDav) throw RequestError("仅新版房间成员可启动")
        record
    }

    suspend fun <T> withMutation(
        id: UUID,
        operation: Host2Operation? = null,
        block: suspend () -> T
    ): T {
        val mutex = mutationLocks.computeIfAbsent(id) { Mutex() }
        if (!mutex.tryLock()) throw RequestError("新版房间正在执行其他操作")
        operation?.let { operations[id] = it }
        return try {
            block()
        } finally {
            operation?.let { operations.remove(id, it) }
            mutex.unlock()
        }
    }

    private fun roleOf(record: Host2Record, player: RAccount): Role {
        if (record.ownerId == player._id.toUUID()) return Role.OWNER
        return repository.member(record.id, player._id.toUUID())?.role?.toRole() ?: Role.GUEST
    }

    private fun Host2Record.toBriefVo(role: Role) = Host2.BriefVo(
        id = id,
        name = name,
        intro = intro,
        iconUrl = iconUrl,
        ownerId = ownerId,
        mcVersion = mcVersion,
        modLoader = modLoader,
        port = port,
        whitelist = whitelist,
        setupStatus = setupStatus,
        status = Host2RuntimeService.status(id),
        role = role,
        onlinePlayerIds = Host2RuntimeService.onlinePlayerIds(id)
    )

    private fun Host2Record.toDetailVo(role: Role, members: List<Host2MemberRecord>) = Host2.DetailVo(
        id = id,
        name = name,
        intro = intro,
        iconUrl = iconUrl,
        ownerId = ownerId,
        mcVersion = mcVersion,
        modLoader = modLoader,
        port = port,
        whitelist = whitelist,
        setupStatus = setupStatus,
        status = Host2RuntimeService.status(id),
        role = role,
        onlinePlayerIds = Host2RuntimeService.onlinePlayerIds(id),
        members = members.map { Host2.Member(it.playerId, it.role) },
        operation = operation(id)
    )

    private fun requireAdmin(role: Role, player: RAccount) {
        if (!player.isDav && role.level > Role.ADMIN.level) throw RequestError("无权限")
    }

    private fun requireOwner(role: Role, player: RAccount) {
        if (!player.isDav && role != Role.OWNER) throw RequestError("无权限")
    }
}

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

private fun validateVersionPair(mcVersion: calebxzhou.rdi.common.model.McVersion, modLoader: calebxzhou.rdi.common.model.ModLoader) {
    if (modLoader !in mcVersion.loaderVersions) throw RequestError("不支持此MC版本与ModLoader组合")
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
    try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath())
    }
}

private const val PORT_START = 30000
private const val PORT_END_EXCLUSIVE = 40000
private const val PORT_ALLOCATION_ATTEMPTS = 32
private const val PAGE_SIZE = 100
private const val OWNER_LIMIT = 3L
private const val JOINED_LIMIT = 10L
private const val PARTICIPANT_LIMIT = 10L
private const val HOST2_PORT_UNIQUE = "host2_port_key"
private val VERSION_MUTABLE_STATUSES = setOf(Host2SetupStatus.AWAITING_UPLOAD, Host2SetupStatus.FAILED)
