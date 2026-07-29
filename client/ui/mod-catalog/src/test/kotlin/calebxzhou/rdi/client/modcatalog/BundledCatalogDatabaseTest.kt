package calebxzhou.rdi.client.modcatalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledCatalogDatabaseTest {
    @Test
    fun `bundled database resolves strict cross platform identity`() = runBlocking {
        val directory = Files.createTempDirectory("rdi-catalog-db")
        val index = SqliteCatalogIdentityIndex.openBundled(directory, Dispatchers.IO).getOrThrow()
        try {
            val modrinth = index.find(ModPlatform.MODRINTH, "jei")
            val curseForge = index.find(ModPlatform.CURSEFORGE, "jei")
            assertEquals(459, modrinth?.mcmodId)
            assertEquals(modrinth?.mcmodId, curseForge?.mcmodId)
            assertTrue(modrinth!!.projects.map { it.platform }.containsAll(ModPlatform.entries))
            assertEquals(
                2,
                index.findAll(
                    setOf(
                        CatalogSlugRef(ModPlatform.MODRINTH, "jei"),
                        CatalogSlugRef(ModPlatform.CURSEFORGE, "jei")
                    )
                ).size
            )
            assertEquals(2021, index.search("机械动力", 0, 10).first().mcmodId)
        } finally {
            index.close()
            Files.deleteIfExists(directory.resolve("mod_catalog.db"))
            Files.deleteIfExists(directory)
        }
    }
}
