package calebxzau.rdi.server.service.baseworld

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class BaseWorldMigrationIntegrationTest {
    @Test
    fun `V2 data survives V3 owner index upgrade`() {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 2
            }
        ).use { dataSource ->
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate()

            val ownerId = UUID.fromString("018f0c44-2d1f-7abc-8def-1234567890ab")
            val worldId = UUID.fromString("018f0c44-2d1f-7abc-8def-1234567890ac")
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO account (id, name, pwd, qq) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setObject(1, ownerId)
                    statement.setString(2, "migration-owner")
                    statement.setString(3, "123456")
                    statement.setString(4, "123456789")
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO base_world (id, owner_id, name, level_type, size) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setObject(1, worldId)
                    statement.setObject(2, ownerId)
                    statement.setString(3, "Migration template")
                    statement.setString(4, "normal")
                    statement.setLong(5, 42)
                    statement.executeUpdate()
                }
            }

            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("3")
                .load()
                .migrate()

            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT name, size FROM base_world WHERE owner_id = ? ORDER BY id DESC").use { statement ->
                    statement.setObject(1, ownerId)
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals("Migration template", result.getString("name"))
                        assertEquals(42, result.getLong("size"))
                    }
                }
                connection.metaData.getIndexInfo(null, null, "base_world", false, false).use { result ->
                    var found = false
                    while (result.next()) {
                        if (result.getString("INDEX_NAME") == "base_world_owner_id_id_idx") {
                            found = true
                            break
                        }
                    }
                    assertTrue(found)
                }
            }
        }
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18")
            .withDatabaseName("rdi")
            .withUsername("rdi")
            .withPassword("rdi")
    }
}
