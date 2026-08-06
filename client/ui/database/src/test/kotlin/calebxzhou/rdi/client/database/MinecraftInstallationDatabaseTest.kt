package calebxzhou.rdi.client.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinecraftInstallationDatabaseTest {
    @Test
    fun `new database creates all tables`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(emptyList(), database.store.list().getOrThrow())
            assertEquals(null, database.store.lastFullScanAt().getOrThrow())
        }
    }

    @Test
    fun `upsert preserves first discovery and updates last seen`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val path = file.parent.resolve("MinecraftRoot")

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.store.upsert(path, 10L).getOrThrow()
            database.store.upsert(path, 20L).getOrThrow()

            assertEquals(
                MinecraftInstallationRecord(path.toAbsolutePath().normalize(), 10L, 20L),
                database.store.list().getOrThrow().single()
            )
        }
    }

    @Test
    fun `path key is case insensitive`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val lower = file.parent.resolve("minecraft-root")
        val upper = file.parent.resolve("MINECRAFT-ROOT")

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.store.upsert(lower, 10L).getOrThrow()
            database.store.upsert(upper, 20L).getOrThrow()

            assertEquals(1, database.store.list().getOrThrow().size)
            assertEquals(20L, database.store.list().getOrThrow().single().lastSeenAt)
        }
    }

    @Test
    fun `delete removes only target path`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val first = file.parent.resolve("first")
        val second = file.parent.resolve("second")

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.store.upsert(first, 10L).getOrThrow()
            database.store.upsert(second, 10L).getOrThrow()
            database.store.delete(first).getOrThrow()

            assertEquals(listOf(second.toAbsolutePath().normalize()), database.store.list().getOrThrow().map { it.path })
        }
    }

    @Test
    fun `scan timestamp round trips`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.store.setLastFullScanAt(123L).getOrThrow()
            assertEquals(123L, database.store.lastFullScanAt().getOrThrow())
        }
    }

    @Test
    fun `player info cache round trips and updates`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val first = PlayerInfoRecord(
            playerId = "68b314bbadaf52ddab96b5ed",
            name = "玩家一",
            isSlim = true,
            skinUrl = "https://example.com/skin-one",
            capeUrl = null,
            updatedAt = 10L
        )
        val updated = first.copy(
            name = "玩家一更新",
            isSlim = false,
            capeUrl = "https://example.com/cape-one",
            updatedAt = 20L
        )

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.playerInfoStore.upsertAll(listOf(first)).getOrThrow()
            assertEquals(mapOf(first.playerId to first), database.playerInfoStore.findByIds(listOf(first.playerId)).getOrThrow())

            database.playerInfoStore.upsertAll(listOf(updated)).getOrThrow()
            assertEquals(mapOf(updated.playerId to updated), database.playerInfoStore.findByIds(listOf(updated.playerId)).getOrThrow())
        }
    }

    @Test
    fun `player info cache survives reopening database`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val record = PlayerInfoRecord(
            playerId = "68c901f07c76a32fa7dc270a",
            name = "玩家二",
            isSlim = false,
            skinUrl = "https://example.com/skin-two",
            capeUrl = "https://example.com/cape-two",
            updatedAt = 30L
        )

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            database.playerInfoStore.upsertAll(listOf(record)).getOrThrow()
        }

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(mapOf(record.playerId to record), database.playerInfoStore.findByIds(listOf(record.playerId)).getOrThrow())
        }
    }

    @Test
    fun `modpack launch options round trip and delete`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val record = ModpackLaunchOptionsRecord(
            versionId = "65f1c2e4d7a9b0c1d2e3f456_test",
            javaPath = "C:/Java/custom/bin/java.exe",
            maxMemoryMb = 8192,
            jdwpEnabled = true,
            jdwpParam = "transport=dt_socket,server=y,suspend=y,address=*:5005",
            customJvmParams = "-XX:+UnlockDiagnosticVMOptions\n-Dexample=true",
            forgeguardDisabled = true,
        )

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(null, database.modpackLaunchOptionsStore.find(record.versionId).getOrThrow())
            database.modpackLaunchOptionsStore.upsert(record).getOrThrow()
            assertEquals(record, database.modpackLaunchOptionsStore.find(record.versionId).getOrThrow())
            database.modpackLaunchOptionsStore.delete(record.versionId).getOrThrow()
            assertEquals(null, database.modpackLaunchOptionsStore.find(record.versionId).getOrThrow())
        }
    }

    @Test
    fun `schema version three migrates launch options table`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        MinecraftInstallationDatabase.open(file).getOrThrow().close()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toUri()}")
        try {
            driver.execute(null, "DROP TABLE modpack_launch_options", 0)
            driver.execute(null, "PRAGMA user_version = 3", 0)
        } finally {
            driver.close()
        }

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(null, database.modpackLaunchOptionsStore.find("missing").getOrThrow())
        }
    }

    @Test
    fun `schema version one migrates without losing installations`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toUri()}")
        driver.execute(null, "PRAGMA application_id = ${MinecraftInstallationDatabase.APPLICATION_ID}", 0)
        driver.execute(
            null,
            """
            CREATE TABLE minecraft_installation (
                path TEXT COLLATE NOCASE NOT NULL PRIMARY KEY,
                first_discovered_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
            CREATE TABLE minecraft_scan_state (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                last_full_scan_at INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
        driver.execute(null, "INSERT INTO minecraft_installation VALUES ('C:/Minecraft', 10, 20)", 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)
        driver.close()

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(
                listOf(MinecraftInstallationRecord(Path.of("C:/Minecraft"), 10L, 20L)),
                database.store.list().getOrThrow()
            )
            assertEquals(emptyMap(), database.playerInfoStore.findByIds(listOf("68b314bbadaf52ddab96b5ed")).getOrThrow())
        }
    }

    @Test
    fun `schema version two without player info table migrates`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toUri()}")
        driver.execute(null, "PRAGMA application_id = ${MinecraftInstallationDatabase.APPLICATION_ID}", 0)
        driver.execute(
            null,
            """
            CREATE TABLE minecraft_installation (
                path TEXT COLLATE NOCASE NOT NULL PRIMARY KEY,
                first_discovered_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
            CREATE TABLE minecraft_scan_state (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                last_full_scan_at INTEGER NOT NULL
            )
            """.trimIndent(),
            0
        )
        driver.execute(null, "PRAGMA user_version = 2", 0)
        driver.close()

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertEquals(emptyMap(), database.playerInfoStore.findByIds(listOf("68b314bbadaf52ddab96b5ed")).getOrThrow())
        }
    }

    @Test
    fun `existing database with expected application id opens`() = runBlocking {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")

        MinecraftInstallationDatabase.open(file).getOrThrow().close()

        MinecraftInstallationDatabase.open(file).getOrThrow().use { database ->
            assertNotNull(database.store)
        }
    }

    @Test
    fun `unknown pre existing database is rejected without modification`() {
        val file = Files.createTempDirectory("rdi-database").resolve("data.db")
        val original = byteArrayOf(1, 2, 3, 4)
        Files.write(file, original)

        assertTrue(MinecraftInstallationDatabase.open(file).isFailure)
        assertEquals(original.toList(), Files.readAllBytes(file).toList())
    }
}
