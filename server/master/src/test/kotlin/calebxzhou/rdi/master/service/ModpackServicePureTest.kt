package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.service.ModpackService.isMcVer
import calebxzhou.rdi.master.service.ModpackService.requireAuthor
import calebxzhou.rdi.master.service.ModpackService.validateVerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModpackServicePureTest {
    @Test
    fun `version names trim and remove leading v`() {
        assertEquals("1.21.1", "  v 1.21.1  ".validate())
        assertEquals("测试版-1", "测试版-1".validate())
    }

    @Test
    fun `version names reject blank and unsafe characters`() {
        assertFailsWith<RequestError> { "v".validate() }
        assertFailsWith<RequestError> { "1.0/../../x".validate() }
        assertFailsWith<RequestError> { "release/1".validate() }
    }

    @Test
    fun `batch ids are distinct and preserve requested order`() {
        val a = org.bson.types.ObjectId()
        val b = org.bson.types.ObjectId()
        assertEquals(listOf(a, b), ModpackService.normalizeInfoBatchIds(listOf(a, b, a)))
        assertFailsWith<Exception> {
            ModpackService.normalizeInfoBatchIds(List(101) { org.bson.types.ObjectId() })
        }
        val first = ModpackServiceTestFixtures.modpack(a, "first")
        val second = ModpackServiceTestFixtures.modpack(b, "second")
        assertEquals(
            listOf(second._id, first._id),
            ModpackService.orderModpacksByIds(
                listOf(second._id, org.bson.types.ObjectId(), first._id),
                listOf(first, second)
            ).map { it._id }
        )
    }

    @Test
    fun `author guard allows owner and dav but rejects other player`() {
        val owner = ModpackServiceTestFixtures.account("owner")
        val other = ModpackServiceTestFixtures.account("other")
        val pack = ModpackServiceTestFixtures.modpack(owner._id)
        val context = ModpackContext(owner, pack, null)
        assertEquals(context, context.requireAuthor())
        assertFailsWith<RequestError> { ModpackContext(other, pack, null).requireAuthor() }
        val dav = other.copy(name = "davickk")
        assertEquals(pack, ModpackContext(dav, pack, null).requireAuthor().modpack)
    }

    @Test
    fun `minecraft version predicate is exact`() {
        val pack = ModpackServiceTestFixtures.modpack(mcVersion = McVersion.V211)
        assertTrue(pack.isMcVer(McVersion.V211))
        assertFalse(pack.isMcVer(McVersion.V201))
        assertEquals(McVersion.V211, McVersion.from("1.21.1"))
    }

    @Test
    fun `archive paths normalize separators and reject missing roots`() {
        assertEquals("config/server.properties", ModpackService.extractOverridesRelativePath("overrides\\config/server.properties"))
        assertEquals("mods/a.jar", ModpackService.extractOverridesRelativePath("foo/overrides/mods/a.jar"))
        assertNull(ModpackService.extractOverridesRelativePath("config/server.properties"))
        assertNull(ModpackService.extractOverridesRelativePath("overrides/"))
        assertEquals("mods/a.jar", ModpackService.extractServerInstallRelativePathForTest("SERVER/mods/a.jar", "server"))
        assertNull(ModpackService.extractServerInstallRelativePathForTest("overrides/mods/a.jar", "server"))
        assertTrue(ModpackService.shouldSkipRootWorld("world/level.dat"))
        assertFalse(ModpackService.shouldSkipRootWorld("world_nether/level.dat"))
    }

    private fun String.validate(): String = ModpackService.run { this@validate.validateVerName().getOrThrow() }
}
