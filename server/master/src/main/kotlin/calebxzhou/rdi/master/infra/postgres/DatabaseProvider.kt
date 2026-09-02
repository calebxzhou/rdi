package calebxzhou.rdi.master.infra.postgres

import calebxzhou.rdi.master.PostgresConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction as exposedTransaction
import java.sql.Connection
import java.util.concurrent.Executors

class DatabaseProvider(config: PostgresConfig) : AutoCloseable {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maximumPoolSize
            poolName = "rdi-master-postgres"
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            connectionInitSql = "SET TIME ZONE 'UTC'"
        }
    )
    private val dispatcher = Executors
        .newFixedThreadPool(config.maximumPoolSize)
        .asCoroutineDispatcher()
    private val database: Database

    init {
        try {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
            flyway.migrate()
            database = Database.connect(dataSource)
        } catch (error: Throwable) {
            dispatcher.close()
            dataSource.close()
            throw error
        }
    }

    suspend fun <T> transaction(block: JdbcTransaction.() -> T): T =
        withContext(dispatcher) {
            exposedTransaction(
                db = database,
                transactionIsolation = Connection.TRANSACTION_READ_COMMITTED,
                readOnly = false
            ) {
                maxAttempts = 1
                block()
            }
    }

    override fun close() {
        dispatcher.close()
        dataSource.close()
    }
}
