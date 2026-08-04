package calebxzhou.rdi.client.database

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinecraftInstallationDatabaseTest {
    @Test
    fun `new database creates both tables`() = runBlocking {
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
