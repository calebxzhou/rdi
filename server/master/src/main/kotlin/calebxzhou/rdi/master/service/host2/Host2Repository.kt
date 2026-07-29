package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2MemberRole
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class Host2Repository {
    fun insert(ownerId: UUID, dto: Host2.CreateDto, port: Int): Host2Record =
        Host2Table.insertReturning {
            it[name] = dto.name
            it[intro] = dto.intro
            it[Host2Table.ownerId] = ownerId
            it[mcVersion] = dto.mcVersion.name
            it[modLoader] = dto.modLoader.name
            it[Host2Table.port] = port
            it[whitelist] = dto.whitelist
            it[setupStatus] = Host2SetupStatus.AWAITING_UPLOAD.name
        }.single().toHost2Record()

    fun findById(id: UUID): Host2Record? =
        Host2Table.selectAll()
            .where { Host2Table.id eq id }
            .singleOrNull()
            ?.toHost2Record()

    fun findByIdForUpdate(id: UUID): Host2Record? =
        Host2Table.selectAll()
            .where { Host2Table.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toHost2Record()

    fun findByPort(port: Int): Host2Record? =
        Host2Table.selectAll()
            .where { Host2Table.port eq port }
            .singleOrNull()
            ?.toHost2Record()

    fun listBySetupStatus(status: Host2SetupStatus): List<Host2Record> =
        Host2Table.selectAll()
            .where { Host2Table.setupStatus eq status.name }
            .map { it.toHost2Record() }

    fun listForPlayer(playerId: UUID, myOnly: Boolean, page: Int, pageSize: Int): List<Host2Record> {
        val memberHostIds = Host2MemberTable.selectAll()
            .where { Host2MemberTable.playerId eq playerId }
            .map { it[Host2MemberTable.hostId] }
        val memberExpression = if (memberHostIds.isEmpty()) {
            Host2Table.ownerId eq playerId
        } else {
            (Host2Table.ownerId eq playerId) or (Host2Table.id inList memberHostIds)
        }
        val visibleExpression = if (myOnly) {
            memberExpression
        } else {
            memberExpression or (
                (Host2Table.setupStatus eq Host2SetupStatus.READY.name) and
                    (Host2Table.whitelist eq false)
                )
        }
        return Host2Table.selectAll()
            .where { visibleExpression }
            .orderBy(Host2Table.id, SortOrder.DESC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { it.toHost2Record() }
    }

    fun ownerCount(ownerId: UUID): Long =
        Host2Table.selectAll().where { Host2Table.ownerId eq ownerId }.count()

    fun joinedCount(playerId: UUID): Long =
        Host2MemberTable.selectAll().where { Host2MemberTable.playerId eq playerId }.count()

    fun members(hostId: UUID): List<Host2MemberRecord> =
        Host2MemberTable.selectAll()
            .where { Host2MemberTable.hostId eq hostId }
            .map { it.toHost2MemberRecord() }

    fun member(hostId: UUID, playerId: UUID): Host2MemberRecord? =
        Host2MemberTable.selectAll()
            .where {
                (Host2MemberTable.hostId eq hostId) and
                    (Host2MemberTable.playerId eq playerId)
            }
            .singleOrNull()
            ?.toHost2MemberRecord()

    fun participantCount(hostId: UUID): Long =
        Host2MemberTable.selectAll().where { Host2MemberTable.hostId eq hostId }.count() + 1

    fun insertMember(hostId: UUID, playerId: UUID, role: Host2MemberRole) {
        Host2MemberTable.insert {
            it[Host2MemberTable.hostId] = hostId
            it[Host2MemberTable.playerId] = playerId
            it[Host2MemberTable.role] = role.name
        }
    }

    fun updateMemberRole(hostId: UUID, playerId: UUID, role: Host2MemberRole): Boolean =
        Host2MemberTable.update({
            (Host2MemberTable.hostId eq hostId) and
                (Host2MemberTable.playerId eq playerId)
        }) {
            it[Host2MemberTable.role] = role.name
        } == 1

    fun deleteMember(hostId: UUID, playerId: UUID): Boolean =
        Host2MemberTable.deleteWhere {
            (Host2MemberTable.hostId eq hostId) and
                (Host2MemberTable.playerId eq playerId)
        } == 1

    fun updateOptions(id: UUID, dto: Host2.OptionsDto) {
        Host2Table.update({ Host2Table.id eq id }) {
            dto.name?.let { value -> it[name] = value }
            dto.intro?.let { value -> it[intro] = value }
            dto.iconUrl?.let { value -> it[iconUrl] = value.ifBlank { null } }
            dto.whitelist?.let { value -> it[whitelist] = value }
            dto.mcVersion?.let { value -> it[mcVersion] = value.name }
            dto.modLoader?.let { value -> it[modLoader] = value.name }
        }
    }

    fun updateSetupStatus(id: UUID, status: Host2SetupStatus): Boolean =
        Host2Table.update({ Host2Table.id eq id }) {
            it[setupStatus] = status.name
        } == 1

    fun updateOwner(id: UUID, ownerId: UUID) {
        Host2Table.update({ Host2Table.id eq id }) {
            it[Host2Table.ownerId] = ownerId
        }
    }

    fun delete(id: UUID): Boolean = Host2Table.deleteWhere { Host2Table.id eq id } == 1

    fun mods(hostId: UUID): List<Host2ModRecord> =
        Host2ModTable.selectAll()
            .where { Host2ModTable.hostId eq hostId }
            .orderBy(Host2ModTable.slug, SortOrder.ASC)
            .map { it.toHost2ModRecord() }

    fun insertMod(hostId: UUID, mod: Mod) {
        Host2ModTable.insert {
            it[Host2ModTable.hostId] = hostId
            it[platform] = mod.platform.lowercase()
            it[projectId] = mod.projectId
            it[fileId] = mod.fileId
            it[slug] = mod.slug
            it[hash] = mod.hash.lowercase()
            it[side] = mod.side.name
        }
    }

    fun deleteMod(hostId: UUID, key: Host2.ModKey): Boolean =
        Host2ModTable.deleteWhere {
            (Host2ModTable.hostId eq hostId) and
                (Host2ModTable.platform eq key.platform.lowercase()) and
                (Host2ModTable.projectId eq key.projectId)
        } == 1
}

data class Host2Record(
    val id: UUID,
    val name: String,
    val intro: String,
    val iconUrl: String?,
    val ownerId: UUID,
    val mcVersion: McVersion,
    val modLoader: ModLoader,
    val port: Int,
    val whitelist: Boolean,
    val setupStatus: Host2SetupStatus
)

data class Host2MemberRecord(
    val hostId: UUID,
    val playerId: UUID,
    val role: Host2MemberRole
)

data class Host2ModRecord(
    val hostId: UUID,
    val mod: Mod
)

private object Host2Table : Table("host2") {
    val id = javaUUID("id").databaseGenerated()
    val name = text("name")
    val intro = text("intro")
    val iconUrl = text("icon_url").nullable()
    val ownerId = javaUUID("owner_id")
    val mcVersion = text("mc_version")
    val modLoader = text("mod_loader")
    val port = integer("port")
    val whitelist = bool("whitelist")
    val setupStatus = text("setup_status")

    override val primaryKey = PrimaryKey(id)
}

private object Host2MemberTable : Table("host2_member") {
    val hostId = javaUUID("host_id")
    val playerId = javaUUID("player_id")
    val role = text("role")

    override val primaryKey = PrimaryKey(hostId, playerId)
}

private object Host2ModTable : Table("host2_mod") {
    val hostId = javaUUID("host_id")
    val platform = text("platform")
    val projectId = text("project_id")
    val fileId = text("file_id")
    val slug = text("slug")
    val hash = text("hash")
    val side = text("side")

    override val primaryKey = PrimaryKey(hostId, platform, projectId)
}

private fun ResultRow.toHost2Record(): Host2Record = Host2Record(
    id = this[Host2Table.id],
    name = this[Host2Table.name],
    intro = this[Host2Table.intro],
    iconUrl = this[Host2Table.iconUrl],
    ownerId = this[Host2Table.ownerId],
    mcVersion = McVersion.valueOf(this[Host2Table.mcVersion]),
    modLoader = ModLoader.valueOf(this[Host2Table.modLoader]),
    port = this[Host2Table.port],
    whitelist = this[Host2Table.whitelist],
    setupStatus = Host2SetupStatus.valueOf(this[Host2Table.setupStatus])
)

private fun ResultRow.toHost2MemberRecord(): Host2MemberRecord = Host2MemberRecord(
    hostId = this[Host2MemberTable.hostId],
    playerId = this[Host2MemberTable.playerId],
    role = Host2MemberRole.valueOf(this[Host2MemberTable.role])
)

private fun ResultRow.toHost2ModRecord(): Host2ModRecord = Host2ModRecord(
    hostId = this[Host2ModTable.hostId],
    mod = Mod(
        platform = this[Host2ModTable.platform],
        projectId = this[Host2ModTable.projectId],
        fileId = this[Host2ModTable.fileId],
        slug = this[Host2ModTable.slug],
        hash = this[Host2ModTable.hash],
        side = Mod.Side.valueOf(this[Host2ModTable.side])
    )
)
