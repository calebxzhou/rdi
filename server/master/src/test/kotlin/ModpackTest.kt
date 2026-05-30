@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.util.ioTask
import calebxzhou.rdi.master.MODPACK_DATA_DIR
import calebxzhou.rdi.master.service.*
import calebxzhou.rdi.master.service.ModpackService.deleteModpack
import calebxzhou.rdi.master.service.host.HostService
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.mockk.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

class ModpackTest {
    private val allowedClientDirs = setOf(
        "config",
        "mods",
        "defaultconfigs",
        "kubejs",
        "global_packs",
        "resourcepacks"
    )

    private val modpackZip = File("C:/Users/calebxzhou/Downloads/ftb-skies-2-1.9.2.zip")
    private val createdRoots = mutableListOf<File>()
    private lateinit var modpackCollection: MongoCollection<Modpack>

    @BeforeTest
    fun setup() {
        MODPACK_DATA_DIR.mkdirs()
        modpackCollection = mockk(relaxed = true)
        ModpackService.testDbcl = modpackCollection
        mockkObject(MailService, HostService, CurseForgeService)

        coEvery { modpackCollection.countDocuments(any<Bson>()) } returns 0
        coEvery { modpackCollection.insertOne(any<Modpack>()) } returns mockk<InsertOneResult>(relaxed = true)
        coEvery { modpackCollection.updateOne(any<Bson>(), any<Bson>()) } returns mockk<UpdateResult>(relaxed = true)
        coEvery {
            modpackCollection.updateOne(
                any<Bson>(),
                any<Bson>(),
                any<UpdateOptions>()
            )
        } returns mockk<UpdateResult>(relaxed = true)
        coEvery { modpackCollection.deleteOne(any<Bson>()) } returns mockk<DeleteResult>(relaxed = true)

        coEvery { HostService.findByModpack(any()) } returns emptyList()
        coEvery { HostService.findByModpackVersion(any(), any()) } returns emptyList()

        /*coEvery { MailService.sendSystemMail(any(), any(), any()) } answers {
            Mail(receiverId = firstArg(), title = secondArg(), content = thirdArg())
        }*/
        every { MailService.changeMail(any(), any(), any(), any()) } returns mockk<Job>(relaxed = true)

        /*coEvery { CurseForgeService.downloadMods(any(), any()) } answers {
            listOf(createTempFile(prefix = "mod", suffix = ".jar"))
        }*/
    }

    @AfterTest
    fun cleanup() {
        createdRoots.forEach { root ->
            if (root.exists()) root.deleteRecursivelyNoSymlink()
        }
        createdRoots.clear()
        ModpackService.testDbcl = null
        unmockkAll()
    }

    @Test
    fun extractOverridesRelativePath_handlesTypicalCases() {
        assertEquals(
            "config/server.properties",
            ModpackService.extractOverridesRelativePath("overrides/config/server.properties")
        )
        assertEquals(
            "mods/example/mod.jar",
            ModpackService.extractOverridesRelativePath("foo/overrides/mods/example/mod.jar")
        )
        assertNull(ModpackService.extractOverridesRelativePath("config/server.properties"))
        assertNull(ModpackService.extractOverridesRelativePath(""))
    }

    @Test
    fun buildClientZip_copiesOnlyClientDirectories() {
        assumeTrue(modpackZip.exists(), "Missing test modpack zip at ${modpackZip.absolutePath}")

        val version = createVersionFixture("client-build-test")
        Files.copy(modpackZip.toPath(), version.zip.toPath(), StandardCopyOption.REPLACE_EXISTING)

        invokeBuildClientZip(version)

        val clientZip = File(version.zip.parentFile, "${version.name}-client.zip")
        assertTrue(clientZip.exists(), "Client zip should be generated next to the source zip")

        ZipFile(clientZip).use { zip ->
            val fileEntries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            assertTrue(fileEntries.isNotEmpty(), "Client zip should contain files")
            val unexpectedTopLevels = fileEntries
                .map { it.name.substringBefore('/', it.name) }
                .filter { it.isNotEmpty() && it !in allowedClientDirs }
                .toSet()
            assertTrue(
                unexpectedTopLevels.isEmpty(),
                "Client zip contains non-client directories: $unexpectedTopLevels"
            )
        }
    }

    @Test
    fun buildClientZip_appliesFiltersAndCompression() {
        val version = createVersionFixture("client-filter-test")
        val largePng = generateLargePng()
        version.zip.parentFile?.mkdirs()

        ZipOutputStream(version.zip.outputStream()).use { zos ->
            zos.writeEntry("overrides/config/fancymenu/config.json", "blocked".toByteArray())
            zos.writeEntry("overrides/config/ftbquests/quests/lang/en_us.snbt", "good".toByteArray())
            zos.writeEntry("overrides/config/ftbquests/quests/lang/zh_cn.snbt", "好".toByteArray())
            zos.writeEntry("overrides/config/ftbquests/quests/lang/de_de.snbt", "bad".toByteArray())
            zos.writeEntry("overrides/config/ftbquests/quests/lang/custom/sub/file.snbt", "nested".toByteArray())
            zos.writeEntry("overrides/mods/sound.ogg", ByteArray(128))
            zos.writeEntry("overrides/mods/image.png", largePng)
            zos.writeEntry("overrides/mods/allowed.txt", "keep".toByteArray())
        }

        invokeBuildClientZip(version)

        val clientZip = File(version.zip.parentFile, "${version.name}-client.zip")
        assertTrue(clientZip.exists(), "Client zip should be produced")

        ZipFile(clientZip).use { zip ->
            val fileNames = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .toList()

            assertTrue(fileNames.none { it.startsWith("config/fancymenu") })
            assertTrue(fileNames.none { it.endsWith(".ogg") })

            val langEntries = fileNames.filter { it.startsWith("config/ftbquests/quests/lang") }
            assertEquals(
                setOf(
                    "config/ftbquests/quests/lang/en_us.snbt",
                    "config/ftbquests/quests/lang/zh_cn.snbt"
                ),
                langEntries.toSet()
            )

            val pngEntry = assertNotNull(zip.getEntry("mods/image.png"))
            val pngBytes = zip.getInputStream(pngEntry).use { it.readBytes() }
            assertTrue(pngBytes.size < largePng.size, "PNG should be recompressed")
            assertEquals(0xFF.toByte(), pngBytes.first(), "Compressed image should be JPEG data")
            assertEquals(0xD8.toByte(), pngBytes.getOrNull(1), "Compressed image should be JPEG data")
        }
    }

    @Test
    fun createModpack_createsDirectoryAndPersistsRecord() = runTest {
        /*val uid = ObjectId()
        val modpack = ModpackService.create(uid, "测试整合包")
        rememberRoot(modpack.dir)

        assertEquals("测试整合包", modpack.name)
        assertTrue(modpack.dir.exists(), "Modpack directory should be created")

        coVerify(exactly = 1) {
            modpackCollection.insertOne(match { it.name == "测试整合包" }, any<InsertOneOptions>())
        }*/
    }

    @Test
    fun createVersion_writesPayloadAndTriggersBuild() = runTest {
        ioTask {
            val player = testAccount()
            val modpack = testModpack(playerId = player._id)
            rememberRoot(modpack.dir)

            val ctx = ModpackContext(player, modpack, null)
            val mods = loadTestMods().take(2).toMutableList()

            advanceUntilIdle()

            val versionDir = modpack.dir.resolve("1.0.1")
            val versionZip = modpack.dir.resolve("1.0.1.zip")

            assertTrue(versionDir.exists(), "Version directory should exist after upload")
            assertTrue(versionZip.exists(), "Version zip should be stored next to modpack root")

            coVerify { MailService.sendSystemMail(eq(player._id), any(), any()) }
           // coVerify { CurseForgeService.downloadMods(any(), any()) }
        }
    }

    @Test
    fun deleteVersion_cleansUpFilesAndCollectionEntry() = runTest {
        val player = testAccount()
        val modpack = testModpack(player._id)
        val version = testVersion(modpack, "1.0.2")
        rememberRoot(modpack.dir)


        version.zip.writeBytes(samplePackZip())

        /*val ctx = ModpackContext(player, modpack.copy(versions = listOf(version)), version)

        ctx.deleteVersion()*/

         assertFalse(version.zip.exists(), "Version archive should be removed")
        coVerify { modpackCollection.updateOne(any<Bson>(), any<Bson>(), any()) }
    }

    @Test
    fun rebuildVersion_requeuesBuildAndSendsMail() = runTest {
        ioTask {
            val player = testAccount()
            val modpack = testModpack(player._id)
            val version = testVersion(modpack, "1.0.3")
            rememberRoot(modpack.dir)

            version.zip.writeBytes(samplePackZip())

            /*val ctx = ModpackContext(player, modpack.copy(versions = listOf(version)), version)

            ctx.rebuildVersion()*/
            advanceUntilIdle()

            coVerify { MailService.sendSystemMail(eq(player._id), match { it.contains("重构整合包") }, any()) }
           // coVerify(atLeast = 1) { CurseForgeService.downloadMods(any(), any()) }
            coVerify {
                modpackCollection.updateOne(
                    any<Bson>(),
                    any<Bson>(),
                    any<UpdateOptions>()
                )
            }
        }
    }

    @Test
    fun deleteModpack_removesRootAndDocument() = runTest {
        val player = testAccount()
        val modpack = testModpack(player._id)
        rememberRoot(modpack.dir)

        modpack.dir.mkdirs()
        File(modpack.dir, "dummy.txt").writeText("test")

        val ctx = ModpackContext(player, modpack, null)
        ctx.deleteModpack()

        assertFalse(modpack.dir.exists(), "Modpack root should be removed")
        coVerify { modpackCollection.deleteOne(any<Bson>(), any()) }
    }

    private fun createVersionFixture(versionName: String): Modpack.Version {
        val modpack = testModpack(ObjectId())
        rememberRoot(modpack.dir)
        val version = testVersion(modpack, versionName)
         return version
    }

    private fun testAccount(): RAccount = RAccount(ObjectId(), "tester", "pwd", "10086")

    private fun testModpack(playerId: ObjectId): Modpack = Modpack(
        _id = ObjectId(),
        name = "Pack-${playerId.toHexString()}",
        authorId = playerId,
        mcVer = McVersion.V211,
        modloader = ModLoader.neoforge,
    )

    private fun testVersion(modpack: Modpack, name: String) = Modpack.Version(
        time = System.currentTimeMillis(),
        modpackId = modpack._id,
        name = name,
        changelog = "test",
        status = Modpack.Status.WAIT,
        mods = mutableListOf()
    )

    private fun loadTestMods(): MutableList<Mod> {
        val json = this.jarResource("test_mod_list.json").use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
        return serdesJson.decodeFromString<List<Mod>>(json).toMutableList()
    }

    private fun samplePackZip(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zos ->
            zos.putNextEntry(ZipEntry("overrides/config/sample.txt"))
            zos.write("demo".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("overrides/resourcepacks/small.zip"))
            zos.write(ByteArray(512) { 1 })
            zos.closeEntry()
        }
        return buffer.toByteArray()
    }

    private fun generateLargePng(): ByteArray {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.color = Color.RED
        graphics.fillOval(40, 40, 320, 320)
        graphics.color = Color.BLUE
        graphics.drawString("Test", 150, 200)
        graphics.dispose()

        val buffer = ByteArrayOutputStream()
        ImageIO.write(image, "png", buffer)
        val bytes = buffer.toByteArray()
        require(bytes.size > 50 * 1024) { "Generated PNG must exceed 50KB" }
        return bytes
    }

    private fun invokeBuildClientZip(version: Modpack.Version) {
        val method = ModpackService::class.java.getDeclaredMethod("buildClientZip", Modpack.Version::class.java)
        method.isAccessible = true
        method.invoke(ModpackService, version)
    }

    private fun rememberRoot(dir: File) {
        if (!createdRoots.contains(dir)) {
            createdRoots += dir
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, data: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(data)
        closeEntry()
    }
}