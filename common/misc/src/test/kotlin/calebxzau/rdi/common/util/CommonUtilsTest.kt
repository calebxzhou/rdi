package calebxzau.rdi.common.util

import calebxzhou.rdi.common.util.decodeBase64
import calebxzhou.rdi.common.util.encodeBase64
import calebxzhou.rdi.common.util.getDateTimeNow
import calebxzhou.rdi.common.util.humanDateTimeNow
import calebxzhou.rdi.common.util.humanFileSize
import calebxzhou.rdi.common.util.humanSpeed
import calebxzhou.rdi.common.util.isValidHttpUrl
import calebxzhou.rdi.common.util.isWideCodePoint
import calebxzhou.rdi.common.util.jarResource
import calebxzhou.rdi.common.util.javaExePath
import calebxzhou.rdi.common.util.md5
import calebxzhou.rdi.common.util.normalizedLength
import calebxzhou.rdi.common.util.sha256
import calebxzhou.rdi.common.util.camelToSnakeCase
import calebxzhou.rdi.common.util.toFixed
import calebxzhou.rdi.common.util.toFriendlyDateTime
import calebxzhou.rdi.common.util.urlDecoded
import calebxzhou.rdi.common.util.urlEncoded
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommonUtilsTest {
    @Test
    fun `friendly date time supports date only mode without changing defaults`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-08-30T00:30:00Z").toEpochMilli()
        val today = Instant.parse("2026-08-29T20:05:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-08-29T15:45:00Z").toEpochMilli()
        val weekday = Instant.parse("2026-08-27T10:00:00Z").toEpochMilli()
        val sameYear = Instant.parse("2026-08-20T10:00:00Z").toEpochMilli()
        val differentYear = Instant.parse("2025-08-20T10:00:00Z").toEpochMilli()

        assertEquals("今天4:05", today.toFriendlyDateTime(now, zone))
        assertEquals("昨天23:45", yesterday.toFriendlyDateTime(now, zone))
        assertEquals("今天", today.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("昨天", yesterday.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("周四", weekday.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("8月20日", sameYear.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("2025年8月20日", differentYear.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("--", 0L.toFriendlyDateTime(now, zone, dateOnly = true))
        assertEquals("--", (-1L).toFriendlyDateTime(now, zone, dateOnly = true))
    }

    @Test
    fun `friendly date time keeps JVM overloads for binary compatibility`() {
        val utilsClass = Class.forName("calebxzhou.rdi.common.util.UtilsKt")
        val longType = Long::class.javaPrimitiveType
        val zoneType = ZoneId::class.java

        assertNotNull(utilsClass.getDeclaredMethod("toFriendlyDateTime", longType, longType, zoneType))
        assertNotNull(utilsClass.getDeclaredMethod("toFriendlyDateTime", longType, longType, zoneType, Boolean::class.javaPrimitiveType))
        assertNotNull(utilsClass.getDeclaredMethod("toFriendlyDateTime\$default", longType, longType, zoneType, Int::class.javaPrimitiveType, Any::class.java))
    }

    @Test
    fun `file helpers preserve legacy behavior`() {
        val file = createTempFile(prefix = "rdi-common-utils").toFile()
        try {
            file.writeText("hello")
            assertEquals("5d41402abc4b2a76b9719d911017c592", file.md5)
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                file.sha256,
            )
            file.writeText("a b\n c")
            assertEquals(3u, file.normalizedLength)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `text and number helpers preserve legacy behavior`() {
        val text = "rdi工具"
        assertEquals(text, text.encodeBase64.decodeBase64)
        assertEquals("hello_world", "helloWorld".camelToSnakeCase())
        assertEquals("hello world", "hello world".urlEncoded.urlDecoded)
        assertTrue(0x4E00.isWideCodePoint())
        assertFalse(0x41.isWideCodePoint())
        assertTrue("https://example.com".isValidHttpUrl())
        assertFalse("ftp://example.com".isValidHttpUrl())
        assertEquals("999B", 999L.humanFileSize)
        assertEquals("1.0KB", 1024L.humanFileSize)
        assertEquals("1.0KB/s", 1024.0.humanSpeed)
        assertEquals("3.14", 3.14159f.toFixed(2))
    }

    @Test
    fun `runtime helpers preserve legacy behavior`() {
        val datePattern = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
        assertTrue(getDateTimeNow().matches(datePattern))
        assertTrue(humanDateTimeNow.matches(datePattern))
        assertTrue(javaExePath.isNotBlank())
        assertFailsWith<IllegalArgumentException> {
            this.jarResource("missing-rdi-test-resource")
        }
    }
}
