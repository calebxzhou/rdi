package calebxzhou.rdi.common.net

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSink
import okio.BufferedSource
import okio.Buffer
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            assertEquals(1, body.writeCount.toDouble(), encodings)
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

    @Test
    fun `log response text without consuming the original body`() {
        val response = response("{\"ok\":true}".toResponseBody("application/json".toMediaType()))

        assertEquals("{\"ok\":true}", response.debugBodyForLogging())
        assertEquals("{\"ok\":true}", response.body!!.string())
    }

    @Test
    fun `omit response body according to content type and size`() {
        val cases = listOf(
            response(null) to "<empty>",
            response("secret".toResponseBody("application/octet-stream".toMediaType())) to
                "<omitted: binary>",
            response("secret".toResponseBody("multipart/form-data; boundary=test".toMediaType())) to
                "<omitted: multipart>",
            response("event".toResponseBody("text/event-stream".toMediaType())) to
                "<omitted: streaming>",
            response("x".repeat(64 * 1024 + 1).toResponseBody("text/plain".toMediaType())) to
                "<omitted: too large>",
            response(
                "{\"secret\":true}".toResponseBody("application/json".toMediaType()),
                "Content-Encoding" to "gzip",
            ) to "<omitted: encoded>",
        )

        cases.forEach { (response, expected) ->
            assertEquals(expected, response.debugBodyForLogging())
        }
    }

    @Test
    fun `omit unknown length oversized response without consuming it`() {
        val expected = "x".repeat(64 * 1024 + 1)
        val response = response(UnknownLengthResponseBody(expected.toByteArray(), "text/plain".toMediaType()))

        assertEquals("<omitted: too large>", response.debugBodyForLogging())
        assertEquals(expected, response.body!!.string())
    }

    @Test
    fun `log zstd response text without consuming the compressed body`() {
        val expected = "{\"ok\":true}"
        val compressed = zstd(expected.toByteArray(StandardCharsets.UTF_8))
        val response = response(
            compressed.toResponseBody("application/json".toMediaType()),
            "Content-Encoding" to "zstd",
        )

        assertEquals(expected, response.debugBodyForLogging())
        assertTrue(compressed.contentEquals(response.body!!.bytes()))
    }

    @Test
    fun `log zstd response decoded text at the size limit`() {
        val expected = "x".repeat(64 * 1024)
        val compressed = zstd(expected.toByteArray(StandardCharsets.UTF_8))
        val response = response(
            compressed.toResponseBody("text/plain".toMediaType()),
            "Content-Encoding" to "zstd",
        )

        assertEquals(expected, response.debugBodyForLogging())
    }

    @Test
    fun `omit oversized zstd response based on decoded size`() {
        val expected = "x".repeat(64 * 1024 + 1)
        val compressed = zstd(expected.toByteArray(StandardCharsets.UTF_8))
        val response = response(
            compressed.toResponseBody("text/plain".toMediaType()),
            "Content-Encoding" to "zstd",
        )

        assertEquals("<omitted: too large>", response.debugBodyForLogging())
    }

    @Test
    fun `omit malformed zstd response without consuming the compressed body`() {
        val compressed = zstd("payload".toByteArray(StandardCharsets.UTF_8))
        val truncated = compressed.copyOf(4)
        val response = response(
            truncated.toResponseBody("text/plain".toMediaType()),
            "Content-Encoding" to "zstd",
        )

        assertEquals("<omitted: unreadable>", response.debugBodyForLogging())
        assertTrue(truncated.contentEquals(response.body!!.bytes()))
    }

    @Test
    fun `omit zstd event stream before decoding`() {
        val response = response(
            zstd("event".toByteArray(StandardCharsets.UTF_8))
                .toResponseBody("text/event-stream".toMediaType()),
            "Content-Encoding" to "zstd",
        )

        assertEquals("<omitted: streaming>", response.debugBodyForLogging())
    }

    private fun zstd(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        ZstdOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private fun request(body: RequestBody, vararg contentEncodings: String): Request =
        Request.Builder()
            .url("https://example.test/upload")
            .post(body)
            .apply { contentEncodings.forEach { addHeader("Content-Encoding", it) } }
            .build()

    private fun response(body: ResponseBody?, vararg headers: Pair<String, String>): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test/response").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
            .body(body)
            .build()

    private class UnknownLengthResponseBody(
        bytes: ByteArray,
        private val contentType: MediaType,
    ) : ResponseBody() {
        private val bufferedSource: BufferedSource = Buffer().write(bytes)

        override fun contentType(): MediaType = contentType

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = bufferedSource
    }

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
