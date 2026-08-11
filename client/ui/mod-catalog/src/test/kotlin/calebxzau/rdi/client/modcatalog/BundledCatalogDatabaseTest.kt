package calebxzau.rdi.client.modcatalog

import calebxzau.rdi.client.modcatalog.CatalogSlugRef
import calebxzau.rdi.client.modcatalog.ModPlatform
import calebxzau.rdi.client.modcatalog.SqliteCatalogIdentityIndex
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
            assertEquals(2, index.search("gongyeshidai2", 0, 10).first().mcmodId)
            assertEquals(2, index.search("gysd2", 0, 10).first().mcmodId)
            assertEquals(459, index.search("Just Enough Items", 0, 10).first().mcmodId)
            assertEquals(459, index.search("jei", 0, 10).first().mcmodId)
            val firstPage = index.search("j", 0, 1)
            val secondPage = index.search("j", 1, 1)
            assertEquals(1, firstPage.size)
            assertEquals(1, secondPage.size)
            assertTrue(firstPage.single().mcmodId != secondPage.single().mcmodId)
        } finally {
            index.close()
            Files.deleteIfExists(directory.resolve("mod_catalog.db"))
            Files.deleteIfExists(directory)
        }
    }
}
