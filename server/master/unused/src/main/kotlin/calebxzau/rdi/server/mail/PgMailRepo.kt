package calebxzau.rdi.server.mail

import calebxzhou.rdi.common.model.MailAction
import calebxzhou.rdi.common.model.MailKind
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Synchronous Exposed operations; callers own DatabaseProvider.transaction. */
class PgMailRepo {
    fun create(
        senderId: UUID?,
        receiverId: UUID,
        title: String,
        content: String,
        kind: MailKind = MailKind.Normal,
        referenceId: UUID? = null,
    ): MailRecord = MailTable.insertReturning {
        it[MailTable.senderId] = senderId
        it[MailTable.receiverId] = receiverId
        it[MailTable.title] = title
        it[MailTable.content] = content
        it[MailTable.kind] = kind.name
        it[MailTable.referenceId] = referenceId
    }.single().toMailRecord()

    fun insert(record: MailRecord): MailRecord {
        MailTable.insert {
            it[MailTable.id] = record.id
            it[MailTable.senderId] = record.senderId
            it[MailTable.receiverId] = record.receiverId
            it[MailTable.title] = record.title
            it[MailTable.content] = record.content
            it[MailTable.kind] = record.kind.name
            it[MailTable.referenceId] = record.referenceId
            it[MailTable.unread] = record.unread
            it[MailTable.createdAt] = record.createdAt
        }
        return record
    }

    fun findById(id: UUID): MailRecord? = MailTable.selectAll()
        .where { MailTable.id eq id }
        .singleOrNull()
        ?.toMailRecord()

    fun findByIdForUpdate(id: UUID): MailRecord? = MailTable.selectAll()
        .where { MailTable.id eq id }
        .forUpdate()
        .singleOrNull()
        ?.toMailRecord()

    fun findByReferenceId(referenceId: UUID): MailRecord? = MailTable.selectAll()
        .where { MailTable.referenceId eq referenceId }
        .singleOrNull()
        ?.toMailRecord()

    fun listInbox(receiverId: UUID, offset: Long = 0, limit: Int = 100, createdAfter: Long? = null): List<MailRecord> {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
        val query = MailTable.selectAll().where {
            if (createdAfter == null) MailTable.receiverId eq receiverId
            else (MailTable.receiverId eq receiverId) and (MailTable.createdAt greaterEq createdAfter)
        }
        return query
            .orderBy(MailTable.createdAt to SortOrder.DESC, MailTable.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toMailRecord() }
    }

    fun markRead(id: UUID, receiverId: UUID): Boolean = MailTable.update({
        (MailTable.id eq id) and (MailTable.receiverId eq receiverId)
    }) {
        it[MailTable.unread] = false
    } == 1

    fun updateContent(id: UUID, title: String?, content: String?): Boolean {
        if (title == null && content == null) return false
        return MailTable.update({ MailTable.id eq id }) {
            title?.let { value -> it[MailTable.title] = value }
            content?.let { value -> it[MailTable.content] = value }
        } == 1
    }

    fun delete(id: UUID, receiverId: UUID): Boolean = MailTable.deleteWhere {
        (MailTable.id eq id) and (MailTable.receiverId eq receiverId)
    } == 1

    fun deleteAll(ids: Collection<UUID>, receiverId: UUID): Int {
        if (ids.isEmpty()) return 0
        return MailTable.deleteWhere {
            (MailTable.receiverId eq receiverId) and (MailTable.id inList ids)
        }
    }

    fun deleteOlderThan(createdBefore: Long): Int = MailTable.deleteWhere {
        MailTable.createdAt less createdBefore
    }

    fun friendRequestReferences(): Set<UUID> = MailTable.selectAll()
        .where { MailTable.kind eq MailKind.FriendRequest.name }
        .mapNotNull { it[MailTable.referenceId] }
        .toSet()
}

internal object MailTable : Table("mail") {
    val id = javaUUID("id").databaseGenerated()
    val senderId = javaUUID("sender_id").nullable()
    val receiverId = javaUUID("receiver_id")
    val title = text("title")
    val content = text("content")
    val kind = text("kind")
    val referenceId = javaUUID("reference_id").nullable()
    val unread = bool("unread")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toMailRecord(): MailRecord = MailRecord(
    id = this[MailTable.id],
    senderId = this[MailTable.senderId],
    receiverId = this[MailTable.receiverId],
    title = this[MailTable.title],
    content = this[MailTable.content],
    kind = MailKind.valueOf(this[MailTable.kind]),
    referenceId = this[MailTable.referenceId],
    unread = this[MailTable.unread],
    createdAt = this[MailTable.createdAt],
)
