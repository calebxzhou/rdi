package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import kotlinx.coroutines.Job
import org.bson.types.ObjectId
import java.io.File
import kotlin.io.path.createTempDirectory

internal object ModpackServiceTestFixtures {
    fun account(name: String = "tester") =
        RAccount(ObjectId(), name, "password", "10086", msid = ObjectId().toString().let {
            java.util.UUID.nameUUIDFromBytes(it.toByteArray())
        })

    fun modpack(
        authorId: ObjectId = ObjectId(),
        name: String = "Pack-${ObjectId().toHexString()}",
        mcVersion: McVersion = McVersion.V211,
        versions: MutableList<Modpack.Version> = mutableListOf()
    ) = Modpack(
        name = name,
        authorId = authorId,
        mcVer = mcVersion,
        modloader = mcVersion.loaderVersions.keys.firstOrNull() ?: ModLoader.neoforge,
        versions = versions
    )

    fun version(
        modpack: Modpack,
        name: String = "1.0.0",
        status: Modpack.Status = Modpack.Status.OK,
        mods: MutableList<Mod> = mutableListOf()
    ) = Modpack.Version(
        time = 1_000L,
        modpackId = modpack._id,
        name = name,
        changelog = "test",
        totalSize = 123L,
        status = status,
        mods = mods
    )

    fun mod(
        slug: String,
        projectId: String = "project-$slug",
        fileId: String = "file-$slug",
        side: Mod.Side = Mod.Side.BOTH,
        platform: String = "mr"
    ) = Mod(platform, projectId, slug, fileId, "hash-$slug", side, listOf("https://example.invalid/$slug.jar"))

    fun tempRoot(prefix: String = "modpack-service-test"): File = createTempDirectory(prefix).toFile()

    fun writeTarZst(root: File, vararg files: Pair<String, ByteArray>): File {
        val archive = root.resolve("pack.tar.zst")
        TarZstArchiveWriter(archive).use { writer ->
            files.forEach { (path, bytes) -> writer.addFile(path, bytes) }
        }
        return archive
    }

    fun Job?.cancelQuietly() {
        this?.cancel()
    }
}
