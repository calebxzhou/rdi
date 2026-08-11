package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.ModpackUploadSessionCreateDto
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModpackParallelUploadServiceTest {
    @Test
    fun `parts can upload out of order and completed file is consumed once`() = runTest {
        val root = createTempDirectory("modpack-parallel-upload-test").toFile()
        try {
            val ownerId = ObjectId()
            val content = "parallel-upload".encodeToByteArray()
            val service = service(root)
            val session = service.create(
                ownerId,
                ModpackUploadSessionCreateDto("pack.zip", content.size.toLong(), content.sha1())
            ).getOrThrow()

            assertEquals(7, session.id.version())
            assertEquals(4, session.partSize)
            assertEquals(4, session.partCount)

            listOf(3, 1, 0, 2).forEach { index ->
                val part = content.part(index, session.partSize)
                service.uploadPart(
                    ownerId = ownerId,
                    id = session.id,
                    index = index,
                    declaredLength = part.size.toLong(),
                    expectedSha1 = part.sha1(),
                    source = ByteReadChannel(part)
                ).getOrThrow()
            }

            assertTrue(service.complete(ownerId, session.id).getOrThrow().ready)
            val consumed = service.withReadyUpload(ownerId, session.id) { it.readBytes() }.getOrThrow()
            assertContentEquals(content, consumed)
            assertTrue(service.status(ownerId, session.id).isFailure)
            assertFalse(root.resolve(session.id.toString()).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `matching create resumes uploaded parts and rejects wrong sha1`() = runTest {
        val root = createTempDirectory("modpack-parallel-resume-test").toFile()
        try {
            val ownerId = ObjectId()
            val content = "resume-me".encodeToByteArray()
            val dto = ModpackUploadSessionCreateDto("pack.zip", content.size.toLong(), content.sha1())
            val service = service(root)
            val first = service.create(ownerId, dto).getOrThrow()
            val part = content.part(0, first.partSize)

            val wrongHashResult = service.uploadPart(
                ownerId,
                first.id,
                0,
                part.size.toLong(),
                ByteArray(20).sha1(),
                ByteReadChannel(part)
            )
            assertTrue(wrongHashResult.isFailure)
            service.uploadPart(
                ownerId,
                first.id,
                0,
                part.size.toLong(),
                part.sha1(),
                ByteReadChannel(part)
            ).getOrThrow()

            val resumedService = service(root)
            val resumed = resumedService.create(ownerId, dto).getOrThrow()
            assertEquals(first.id, resumed.id)
            assertEquals(listOf(0), resumed.uploadedParts)
            assertTrue(resumedService.status(ObjectId(), first.id).isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun service(root: java.io.File) = ModpackParallelUploadService(
        sessionsDir = root,
        maxFileSize = 1024,
        clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC),
        partSize = 4
    )

    private fun ByteArray.part(index: Int, partSize: Int): ByteArray {
        val start = index * partSize
        return copyOfRange(start, minOf(start + partSize, size))
    }

    private fun ByteArray.sha1(): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(this))
}
