package calebxzau.rdi.server.account

import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzau.rdi.common.logging.Loggers
import kotlinx.coroutines.flow.Flow
import org.bson.types.ObjectId

data class AccountMirrorScanSummary(
    val inserted: Int,
    val existing: Int,
    val conflicts: Int,
    val failed: Int
)

class AccountMirrorService(
    private val database: DatabaseProvider,
    private val repository: PgAccountRepo
) {
    private val lgr by Loggers

    suspend fun upsert(account: RAccount): Result<Unit> = runCatching {
        database.transaction {
            repository.upsert(AccountRecord.from(account))
        }
    }

    suspend fun insertIfMissing(account: RAccount): Result<InsertIfMissingResult> = runCatching {
        database.transaction {
            repository.insertIfMissing(AccountRecord.from(account))
        }
    }

    suspend fun scan(accounts: Flow<RAccount>): Result<AccountMirrorScanSummary> = runCatching {
        var inserted = 0
        var existing = 0
        var conflicts = 0
        var failed = 0
        accounts.collect { account ->
            val result = insertIfMissing(account)
            result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        InsertIfMissingResult.INSERTED -> inserted++
                        InsertIfMissingResult.EXISTING -> existing++
                        is InsertIfMissingResult.CONFLICT -> {
                            conflicts++
                            logConflict("startup-scan", account, outcome)
                        }
                    }
                },
                onFailure = { error ->
                    failed++
                    logFailure("startup-scan", account._id, error)
                }
            )
        }
        AccountMirrorScanSummary(inserted, existing, conflicts, failed)
    }

    private fun logConflict(
        source: String,
        account: RAccount,
        conflict: InsertIfMissingResult.CONFLICT
    ) {
        lgr.warn {
            "account mirror conflict source=$source objectId=${account._id} uuid=${account._id.toUUID()} " +
                "conflictField=${conflict.field} conflictingAccountId=${conflict.conflictingAccountId}"
        }
    }

    private fun logFailure(source: String, accountId: ObjectId, error: Throwable) {
        lgr.error(error) {
            "account mirror failed source=$source objectId=$accountId uuid=${accountId.toUUID()}"
        }
    }
}
