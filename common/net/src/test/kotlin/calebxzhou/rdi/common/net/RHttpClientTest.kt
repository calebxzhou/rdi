package calebxzhou.rdi.common.net

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class RHttpClientTest {
    @Test
    fun `allowlisted text content types are logged`() {
        val cases = listOf(
            "text/plain" to "plain text",
            "application/json" to "{\"ok\":true}",
            "application/problem+json" to "{\"title\":\"bad\"}",
            "application/xml" to "<ok/>",
            "application/problem+xml" to "<problem/>",
            "application/x-www-form-urlencoded" to "name=Steve&level=2",
            "application/javascript" to "const answer = 42;",
            "application/ecmascript" to "let answer = 42;",
        )

        cases.forEach { (contentType, expected) ->
            val body = RecordingBody(expected.toByteArray(), contentType.toMediaType())
            val result = request(body).debugBodyForLogging()

            assertEquals(expected, result, contentType)
            assertEquals(1, body.writeCount, contentType)
        }
    }

    @Test
    fun `decode text using declared charset`() {
        val expected = "caf\u00e9"
        val body = RecordingBody(
            expected.toByteArray(StandardCharsets.ISO_8859_1),
            "text/plain; charset=ISO-8859-1".toMediaType(),
        )

        assertEquals(expected, request(body).debugBodyForLogging())
        assertEquals(1, body.writeCount)
    }

    @Test
    fun `omit binary unknown and multipart bodies without reading them`() {
        val contentTypes = listOf(
            "multipart/form-data; boundary=test",
            "application/zip",
            "application/octet-stream",
            "application/pdf",
            null,
        )

        contentTypes.forEach { contentType ->
            val body = RecordingBody("secret bytes".toByteArray(), contentType?.toMediaType())

            val result = request(body).debugBodyForLogging()

            val expected = if (contentType?.startsWith("multipart") == true) {
                "<omitted: multipart>"
            } else {
                if (contentType == null) "<omitted: unknown>" else "<omitted: binary>"
            }
            assertEquals(expected, result, contentType)
            assertEquals(0, body.writeCount, contentType)
        }
    }

    @Test
    fun `omit streaming unknown length and oversized text bodies without reading them`() {
        val cases = listOf(
            RecordingBody("one shot".toByteArray(), "text/plain".toMediaType(), oneShot = true) to
                "<omitted: streaming>",
            RecordingBody("duplex".toByteArray(), "text/plain".toMediaType(), duplex = true) to
                "<omitted: streaming>",
            RecordingBody("unknown".toByteArray(), "text/plain".toMediaType(), reportedLength = -1L) to
                "<omitted: streaming>",
            RecordingBody("large".toByteArray(), "text/plain".toMediaType(), reportedLength = 64 * 1024L + 1L) to
                "<omitted: too large>",
        )

        cases.forEach { (body, expected) ->
            assertEquals(expected, request(body).debugBodyForLogging())
            assertEquals(0, body.writeCount)
        }
    }

    @Test
    fun `allow text body at exactly the size limit`() {
        val body = RecordingBody(
            bytes = "small fixture".toByteArray(),
            contentType = "text/plain".toMediaType(),
            reportedLength = 64 * 1024L,
        )

        assertEquals("small fixture", request(body).debugBodyForLogging())
        assertEquals(1, body.writeCount)
    }

    @Test
    fun `omit encoded text bodies without reading them`() {
        listOf("gzip", "deflate").forEach { encoding ->
            val body = RecordingBody("{\"secret\":true}".toByteArray(), "application/json".toMediaType())

            assertEquals(
                "<omitted: encoded>",
                request(body, encoding).debugBodyForLogging(),
            )
            assertEquals(0, body.writeCount, encoding)
        }
    }

    @Test
    fun `only identity content encodings allow text body logging`() {
        val allowed = listOf(
            emptyList(),
            listOf("identity"),
            listOf("identity", "IDENTITY"),
        )
        allowed.forEach { encodings ->
            val body = RecordingBody("allowed".toByteArray(), "text/plain".toMediaType())

            assertEquals("allowed", request(body, *encodings.toTypedArray()).debugBodyForLogging())
            assertEquals(1, body.writeCount, encodings)
        }
    }

    @Test
    fun `any non-identity content encoding omits text body without reading it`() {
        val omitted = listOf(
            listOf("gzip", "identity"),
            listOf("identity, gzip"),
        )
        omitted.forEach { encodings ->
            val body = RecordingBody("secret".toByteArray(), "application/json".toMediaType())

            assertEquals(
                "<omitted: encoded>",
                request(body, *encodings.toTypedArray()).debugBodyForLogging(),
            )
            assertEquals(0, body.writeCount, encodings)
        }
    }

    @Test
    fun `empty request has no body to log`() {
        assertEquals(
            "<empty>",
            Request.Builder().url("https://example.test/upload").build().debugBodyForLogging(),
        )
    }

    @Test
    fun `omit unreadable text body without failing logging`() {
        val body = RecordingBody(
            bytes = "not read".toByteArray(),
            contentType = "text/plain".toMediaType(),
            writeFailure = IOException("cannot read body"),
        )

        assertEquals("<omitted: unreadable>", request(body).debugBodyForLogging())
        assertEquals(1, body.writeCount)
    }

    @Test
    fun `omit body when content length cannot be determined`() {
        val body = RecordingBody(
            bytes = "unknown length".toByteArray(),
            contentType = "text/plain".toMediaType(),
            lengthFailure = IOException("cannot determine length"),
        )

        assertEquals("<omitted: streaming>", request(body).debugBodyForLogging())
        assertEquals(0, body.writeCount)
    }

    private fun request(body: RequestBody, vararg contentEncodings: String): Request =
        Request.Builder()
            .url("https://example.test/upload")
            .post(body)
            .apply { contentEncodings.forEach { addHeader("Content-Encoding", it) } }
            .build()

    private class RecordingBody(
        private val bytes: ByteArray,
        private val contentType: MediaType?,
        private val reportedLength: Long = bytes.size.toLong(),
        private val oneShot: Boolean = false,
        private val duplex: Boolean = false,
        private val writeFailure: IOException? = null,
        private val lengthFailure: IOException? = null,
    ) : RequestBody() {
        var writeCount = 0
            private set

        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = lengthFailure?.let { throw it } ?: reportedLength

        override fun isOneShot(): Boolean = oneShot

        override fun isDuplex(): Boolean = duplex

        override fun writeTo(sink: BufferedSink) {
            writeCount += 1
            writeFailure?.let { throw it }
            sink.write(bytes)
        }
    }
}
