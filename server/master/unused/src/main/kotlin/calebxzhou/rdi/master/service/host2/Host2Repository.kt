package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2MemberRole
import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.PackSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
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
            it[Host2Table.port] = port
            it[whitelist] = dto.whitelist
            it[packStatus] = Host2PackStatus.Busy.name
            it.setPackSource(dto.packSource)
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

    fun listByPackStatus(status: Host2PackStatus): List<Host2Record> =
        Host2Table.selectAll()
            .where { Host2Table.packStatus eq status.name }
            .map { it.toHost2Record() }

    fun listAll(): List<Host2Record> = Host2Table.selectAll()
            .orderBy(Host2Table.id, SortOrder.DESC)
            .map { it.toHost2Record() }

    fun memberRoles(playerId: UUID): Map<UUID, Host2MemberRole> =
        Host2MemberTable.selectAll()
            .where { Host2MemberTable.playerId eq playerId }
            .associate { it[Host2MemberTable.hostId] to Host2MemberRole.valueOf(it[Host2MemberTable.role]) }

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
        }
    }

    fun updatePackStatus(id: UUID, status: Host2PackStatus): Boolean =
        Host2Table.update({ Host2Table.id eq id }) {
            it[packStatus] = status.name
        } == 1

    fun updatePackSource(id: UUID, source: PackSource): Boolean =
        Host2Table.update({ Host2Table.id eq id }) {
            it.setPackSource(source)
        } == 1

    fun updateRevisionPointers(id: UUID, active: Long, pending: Long?): Boolean =
        Host2Table.update({ Host2Table.id eq id }) {
            it[activeContentRevision] = active
            it[pendingContentRevision] = pending
        } == 1

    fun updateOwner(id: UUID, ownerId: UUID) {
        Host2Table.update({ Host2Table.id eq id }) {
            it[Host2Table.ownerId] = ownerId
        }
    }

    fun delete(id: UUID): Boolean = Host2Table.deleteWhere { Host2Table.id eq id } == 1

    fun insertRevision(hostId: UUID, revision: Long, contents: List<ContentVo>) {
        Host2ContentRevisionTable.insert {
            it[Host2ContentRevisionTable.hostId] = hostId
            it[Host2ContentRevisionTable.revision] = revision
        }
        contents.forEach { content ->
            Host2ContentSnapshotTable.insert {
                it[Host2ContentSnapshotTable.hostId] = hostId
                it[Host2ContentSnapshotTable.revision] = revision
                it[origin] = content.origin.name
                it[platform] = content.platform.name
                it[type] = content.type.name
                it[projectId] = content.projectId
                it[fileId] = content.fileId
                it[slug] = content.slug
                it[hash] = content.hash
                it[targetPath] = content.targetPath
                it[side] = content.side.name
                it[required] = content.required
                it[enabled] = content.enabled
                it[fileSize] = content.fileSize
            }
        }
    }

    fun contents(hostId: UUID, revision: Long): List<ContentVo> =
        Host2ContentSnapshotTable.selectAll()
            .where {
                (Host2ContentSnapshotTable.hostId eq hostId) and
                    (Host2ContentSnapshotTable.revision eq revision)
            }
            .orderBy(Host2ContentSnapshotTable.origin, SortOrder.ASC)
            .orderBy(Host2ContentSnapshotTable.platform, SortOrder.ASC)
            .orderBy(Host2ContentSnapshotTable.projectId, SortOrder.ASC)
            .orderBy(Host2ContentSnapshotTable.side, SortOrder.ASC)
            .map { it.toContentVo() }

    fun deleteRevision(hostId: UUID, revision: Long): Boolean =
        Host2ContentRevisionTable.deleteWhere {
            (Host2ContentRevisionTable.hostId eq hostId) and
                (Host2ContentRevisionTable.revision eq revision)
        } == 1

    fun deleteRevisionsExcept(hostId: UUID, retained: Set<Long>) {
        Host2ContentRevisionTable.selectAll()
            .where { Host2ContentRevisionTable.hostId eq hostId }
            .map { it[Host2ContentRevisionTable.revision] }
            .filterNot(retained::contains)
            .forEach { deleteRevision(hostId, it) }
    }

    fun isModpack2VersionReferenced(versionId: UUID): Boolean =
        Host2Table.selectAll()
            .where { Host2Table.modpack2VersionId eq versionId }
            .limit(1)
            .count() > 0 ||
            Host2OperationTable.selectAll()
                .where { Host2OperationTable.targetModpack2VersionId eq versionId }
                .limit(1)
                .count() > 0

    fun insertOperation(operation: NewHost2Operation): Host2OperationRecord =
        Host2OperationTable.insertReturning {
            it[hostId] = operation.hostId
            it[kind] = operation.kind.name
            it[phase] = operation.phase
            it.setTargetSource(operation.targetSource)
            it[targetRevision] = operation.targetRevision
            it[stagingPath] = operation.stagingPath
            it[backupPath] = operation.backupPath
        }.single().toOperationRecord()

    fun activeOperation(hostId: UUID): Host2OperationRecord? =
        Host2OperationTable.selectAll()
            .where { Host2OperationTable.hostId eq hostId }
            .singleOrNull()
            ?.toOperationRecord()

    fun activeOperations(): List<Host2OperationRecord> =
        Host2OperationTable.selectAll().map { it.toOperationRecord() }

    fun updateOperation(
        id: UUID,
        phase: String,
        stagingPath: String? = null,
        backupPath: String? = null,
    ): Boolean = Host2OperationTable.update({ Host2OperationTable.id eq id }) {
        it[Host2OperationTable.phase] = phase
        it[Host2OperationTable.stagingPath] = stagingPath
        it[Host2OperationTable.backupPath] = backupPath
    } == 1

    fun deleteOperation(id: UUID): Boolean =
        Host2OperationTable.deleteWhere { Host2OperationTable.id eq id } == 1

    fun insertDeleteTombstone(operationId: UUID, hostId: UUID, deletingPath: String) {
        Host2DeleteTombstoneTable.insert {
            it[Host2DeleteTombstoneTable.operationId] = operationId
            it[Host2DeleteTombstoneTable.hostId] = hostId
            it[Host2DeleteTombstoneTable.deletingPath] = deletingPath
        }
    }

    fun deleteTombstones(): List<Host2DeleteTombstoneRecord> =
        Host2DeleteTombstoneTable.selectAll().map { it.toDeleteTombstoneRecord() }

    fun deleteDeleteTombstone(operationId: UUID): Boolean =
        Host2DeleteTombstoneTable.deleteWhere {
            Host2DeleteTombstoneTable.operationId eq operationId
        } == 1
}

data class Host2Record(
    val id: UUID,
    val name: String,
    val intro: String,
    val iconUrl: String?,
    val ownerId: UUID,
    val packSource: PackSource,
    val packStatus: Host2PackStatus,
    val activeContentRevision: Long,
    val pendingContentRevision: Long?,
    val port: Int,
    val whitelist: Boolean,
)

data class Host2MemberRecord(
    val hostId: UUID,
    val playerId: UUID,
    val role: Host2MemberRole,
)

enum class Host2OperationKind {
    InitialInstall,
    Switch,
    Apply,
    Delete,
}

data class NewHost2Operation(
    val hostId: UUID,
    val kind: Host2OperationKind,
    val phase: String,
    val targetSource: PackSource? = null,
    val targetRevision: Long? = null,
    val stagingPath: String? = null,
    val backupPath: String? = null,
)

data class Host2OperationRecord(
    val id: UUID,
    val hostId: UUID,
    val kind: Host2OperationKind,
    val phase: String,
    val targetSource: PackSource?,
    val targetRevision: Long?,
    val stagingPath: String?,
    val backupPath: String?,
)

data class Host2DeleteTombstoneRecord(
    val operationId: UUID,
    val hostId: UUID,
    val deletingPath: String,
)

private object Host2Table : Table("host2") {
    val id = javaUUID("id").databaseGenerated()
    val name = text("name")
    val intro = text("intro")
    val iconUrl = text("icon_url").nullable()
    val ownerId = javaUUID("owner_id")
    val port = integer("port")
    val whitelist = bool("whitelist")
    val packStatus = text("pack_status")
    val modpack2VersionId = javaUUID("modpack2_version_id")
    val activeContentRevision = long("active_content_revision")
    val pendingContentRevision = long("pending_content_revision").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object Host2MemberTable : Table("host2_member") {
    val hostId = javaUUID("host_id")
    val playerId = javaUUID("player_id")
    val role = text("role")

    override val primaryKey = PrimaryKey(hostId, playerId)
}

private object Host2ContentRevisionTable : Table("host2_content_revision") {
    val hostId = javaUUID("host_id")
    val revision = long("revision")

    override val primaryKey = PrimaryKey(hostId, revision)
}

private object Host2ContentSnapshotTable : Table("host2_content_snapshot") {
    val hostId = javaUUID("host_id")
    val revision = long("revision")
    val origin = text("origin")
    val platform = text("platform")
    val type = text("type")
    val projectId = text("project_id")
    val fileId = text("file_id")
    val slug = text("slug")
    val hash = text("hash")
    val targetPath = text("target_path").nullable()
    val side = text("side")
    val required = bool("required")
    val enabled = bool("enabled")
    val fileSize = long("file_size")

    override val primaryKey = PrimaryKey(hostId, revision, origin, platform, projectId, side)
}

private object Host2OperationTable : Table("host2_operation") {
    val id = javaUUID("id").databaseGenerated()
    val hostId = javaUUID("host_id")
    val kind = text("kind")
    val phase = text("phase")
    val targetModpack2VersionId = javaUUID("target_modpack2_version_id").nullable()
    val targetRevision = long("target_revision").nullable()
    val stagingPath = text("staging_path").nullable()
    val backupPath = text("backup_path").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object Host2DeleteTombstoneTable : Table("host2_delete_tombstone") {
    val operationId = javaUUID("operation_id")
    val hostId = javaUUID("host_id")
    val deletingPath = text("deleting_path")

    override val primaryKey = PrimaryKey(operationId)
}

private fun ResultRow.toHost2Record(): Host2Record = Host2Record(
    id = this[Host2Table.id],
    name = this[Host2Table.name],
    intro = this[Host2Table.intro],
    iconUrl = this[Host2Table.iconUrl],
    ownerId = this[Host2Table.ownerId],
    packSource = PackSource.Modpack2(this[Host2Table.modpack2VersionId]),
    packStatus = Host2PackStatus.valueOf(this[Host2Table.packStatus]),
    activeContentRevision = this[Host2Table.activeContentRevision],
    pendingContentRevision = this[Host2Table.pendingContentRevision],
    port = this[Host2Table.port],
    whitelist = this[Host2Table.whitelist],
)

private fun ResultRow.toHost2MemberRecord(): Host2MemberRecord = Host2MemberRecord(
    hostId = this[Host2MemberTable.hostId],
    playerId = this[Host2MemberTable.playerId],
    role = Host2MemberRole.valueOf(this[Host2MemberTable.role]),
)

private fun ResultRow.toContentVo(): ContentVo = ContentVo(
    origin = ContentOrigin.valueOf(this[Host2ContentSnapshotTable.origin]),
    platform = ContentPlatform.valueOf(this[Host2ContentSnapshotTable.platform]),
    type = ContentType.valueOf(this[Host2ContentSnapshotTable.type]),
    projectId = this[Host2ContentSnapshotTable.projectId],
    fileId = this[Host2ContentSnapshotTable.fileId],
    slug = this[Host2ContentSnapshotTable.slug],
    hash = this[Host2ContentSnapshotTable.hash],
    targetPath = this[Host2ContentSnapshotTable.targetPath],
    side = ContentSide.valueOf(this[Host2ContentSnapshotTable.side]),
    required = this[Host2ContentSnapshotTable.required],
    enabled = this[Host2ContentSnapshotTable.enabled],
    fileSize = this[Host2ContentSnapshotTable.fileSize],
)

private fun ResultRow.toOperationRecord(): Host2OperationRecord = Host2OperationRecord(
    id = this[Host2OperationTable.id],
    hostId = this[Host2OperationTable.hostId],
    kind = Host2OperationKind.valueOf(this[Host2OperationTable.kind]),
    phase = this[Host2OperationTable.phase],
    targetSource = this[Host2OperationTable.targetModpack2VersionId]
        ?.let(PackSource::Modpack2),
    targetRevision = this[Host2OperationTable.targetRevision],
    stagingPath = this[Host2OperationTable.stagingPath],
    backupPath = this[Host2OperationTable.backupPath],
)

private fun ResultRow.toDeleteTombstoneRecord() = Host2DeleteTombstoneRecord(
    operationId = this[Host2DeleteTombstoneTable.operationId],
    hostId = this[Host2DeleteTombstoneTable.hostId],
    deletingPath = this[Host2DeleteTombstoneTable.deletingPath],
)

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.setPackSource(source: PackSource) {
    this[Host2Table.modpack2VersionId] = source.versionId
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.setTargetSource(source: PackSource?) {
    this[Host2OperationTable.targetModpack2VersionId] = source?.versionId
}
