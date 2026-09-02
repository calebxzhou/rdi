package calebxzau.rdi.server.account

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID

sealed interface InsertIfMissingResult {
    data object INSERTED : InsertIfMissingResult
    data object EXISTING : InsertIfMissingResult
    data class CONFLICT(
        val field: String,
        val conflictingAccountId: UUID?
    ) : InsertIfMissingResult
}

class PgAccountRepo {
    fun findById(id: UUID): AccountRecord? =
        AccountTable.selectAll()
            .where { AccountTable.id eq id }
            .singleOrNull()
            ?.toPgAccountRecord()

    fun findByIdForUpdate(id: UUID): AccountRecord? =
        AccountTable.selectAll()
            .where { AccountTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toPgAccountRecord()

    /** Locks an account row for the surrounding transaction. */
    fun lock(id: UUID): Boolean = findByIdForUpdate(id) != null

    fun insertIfMissing(record: AccountRecord): InsertIfMissingResult {
        val statement = AccountTable.insertIgnore {
            it[id] = record.id
            it[name] = record.name
            it[pwd] = record.pwd
            it[qq] = record.qq
            it[msid] = record.msid
            it[isSlim] = record.isSlim
            it[skin] = record.skin
            it[cape] = record.cape
        }
        if (statement.insertedCount > 0) return InsertIfMissingResult.INSERTED
        return if (findById(record.id) != null) {
            InsertIfMissingResult.EXISTING
        } else {
            findConflict(record)
        }
    }

    private fun findConflict(record: AccountRecord): InsertIfMissingResult.CONFLICT {
        AccountTable.selectAll()
            .where { AccountTable.name eq record.name }
            .singleOrNull()
            ?.let { return InsertIfMissingResult.CONFLICT("name", it[AccountTable.id]) }

        AccountTable.selectAll()
            .where { AccountTable.qq eq record.qq }
            .singleOrNull()
            ?.let { return InsertIfMissingResult.CONFLICT("qq", it[AccountTable.id]) }

        record.msid?.let { msidValue ->
            AccountTable.selectAll()
                .where { AccountTable.msid eq msidValue }
                .singleOrNull()
                ?.let { return InsertIfMissingResult.CONFLICT("msid", it[AccountTable.id]) }
        }

        return InsertIfMissingResult.CONFLICT("unknown", null)
    }

    fun upsert(record: AccountRecord) {
        AccountTable.upsert(AccountTable.id) {
            it[id] = record.id
            it[name] = record.name
            it[pwd] = record.pwd
            it[qq] = record.qq
            it[msid] = record.msid
            it[isSlim] = record.isSlim
            it[skin] = record.skin
            it[cape] = record.cape
        }
    }
}

private object AccountTable : Table("account") {
    val id = javaUUID("id")
    val name = text("name")
    val pwd = text("pwd")
    val qq = text("qq")
    val msid = javaUUID("msid").nullable()
    val isSlim = bool("is_slim")
    val skin = text("skin")
    val cape = text("cape").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toPgAccountRecord(): AccountRecord = AccountRecord(
    id = this[AccountTable.id],
    name = this[AccountTable.name],
    pwd = this[AccountTable.pwd],
    qq = this[AccountTable.qq],
    msid = this[AccountTable.msid],
    isSlim = this[AccountTable.isSlim],
    skin = this[AccountTable.skin],
    cape = this[AccountTable.cape]
)
