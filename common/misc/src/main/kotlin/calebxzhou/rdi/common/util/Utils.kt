package calebxzhou.rdi.common.util

import calebxzhou.rdi.common.util.DEFAULT_DATE_TIME_PATTERN
import calebxzhou.rdi.common.util.digest
import calebxzhou.rdi.common.util.displayLength
import calebxzhou.rdi.common.VALID_NAME_REGEX
import calebxzhou.rdi.common.VALID_PLAYER_NAME_REGEX
import calebxzhou.rdi.common.exception.RequestError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.inputStream

fun ObjectId.toUUID(): UUID {
    val objectIdBytes = this.toByteArray()
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.put(objectIdBytes)
    return UUID(bb.getLong(0), bb.getLong(8))
}

fun UUID.toBytes(): ByteArray {
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.putLong(this.mostSignificantBits)
    bb.putLong(this.leastSignificantBits)
    return bb.array()
}

val UUID.objectId: ObjectId
    get() {
        val uuidBytes = this.toBytes()
        val objectIdBytes = uuidBytes.sliceArray(0..11)
        return ObjectId(objectIdBytes)

    }
val ioScope: CoroutineScope
    get() = CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }
    )

fun ioTask(handler: suspend () -> Unit) = ioScope.launch { handler() }
fun err(reason: String): Result<Unit> {
    return Result.failure<Unit>(RequestError(reason))
}

inline fun <reified T> ok(obj: T): Result<T> {
    return Result.success(obj)
}

fun ok(): Result<Unit> {
    return Result.success(Unit)
}

val ObjectId.str get() = toHexString()
fun String.validateName(): Result<Unit> = runCatching {
    if (!matches(VALID_NAME_REGEX)) throw RequestError("昵称只能包含字母数字汉字或_-")
    val trimmed = this.trim()
    val len = trimmed.displayLength
    if (len !in 3..32) throw RequestError("名称长度需在3~32个字符，当前为${len}（一个汉字算两个）")
    return Result.success(Unit)
}

fun String.validatePlayerName(): Result<Unit> = runCatching {
    if (!matches(VALID_PLAYER_NAME_REGEX)) {
        return Result.failure(RequestError("昵称只能包含字母数字汉字_"))
    }
    val len = displayLength
    if (len !in 3..24) {
        return Result.failure(RequestError("昵称长度应在3~24，当前为${len}（一个汉字算两个）"))
    }
    return Result.success(Unit)
}

fun String.validateHttpUrl(): Result<URI> {
    val uri = runCatching { URI(this) }.getOrElse {
        throw RequestError("链接无效")
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw RequestError("链接必须以http或https开头")
    }
    return ok(uri)
}

val periodOfDay: String
    get() = when (LocalDateTime.now().hour) {
        in 0..5 -> "凌晨"
        in 6..8 -> "早上"
        in 9..10 -> "上午"
        in 11..12 -> "中午"
        in 13..17 -> "下午"
        in 18..23 -> "晚上"
        else -> ""
    }

fun Long.toFriendlyDateTime(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    if (this <= 0L) return "--"
    val target = Instant.ofEpochMilli(this).atZone(zoneId)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val targetDate = target.toLocalDate()
    val nowDate = now.toLocalDate()
    val dayDiff = ChronoUnit.DAYS.between(targetDate, nowDate)
    val timeText = target.toFriendlyClockText()
    return when {
        dayDiff == 0L -> "今天$timeText"
        dayDiff == 1L -> "昨天$timeText"
        dayDiff == 2L -> "前天$timeText"
        dayDiff == -1L -> "明天$timeText"
        dayDiff == -2L -> "后天$timeText"
        dayDiff in -6L..6L -> "${target.dayOfWeek.toFriendlyWeekdayText()}$timeText"
        target.year == now.year -> "${target.monthValue}月${target.dayOfMonth}日$timeText"
        else -> "${target.year}年${target.monthValue}月${target.dayOfMonth}日$timeText"
    }
}

private fun ZonedDateTime.toFriendlyClockText(): String =
    "${hour}:${minute.toString().padStart(2, '0')}"

private fun DayOfWeek.toFriendlyWeekdayText(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

//保留小数点后x位
fun Float.toFixed(decPlaces: Int): String {
    return String.format("%.${decPlaces}f", this)
}

fun Double.toFixed(decPlaces: Int): String {
    return this.toFloat().toFixed(decPlaces)
}

val Long.humanFileSize: String
    get() {
        val bytes = this
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1fKB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1fMB".format(mb)
        val gb = mb / 1024.0
        return "%.1fGB".format(gb)
    }
val Int.humanSize: String
    get() = toLong().humanFileSize
val Double.humanSpeed: String
    get() {
        val bytesPerSecond = this
        if (bytesPerSecond < 1024) return "%.0fB/s".format(bytesPerSecond)
        val kbps = bytesPerSecond / 1024.0
        if (kbps < 1024) return "%.1fKB/s".format(kbps)
        val mbps = kbps / 1024.0
        if (mbps < 1024) return "%.1fMB/s".format(mbps)
        val gbps = mbps / 1024.0
        return "%.1fGB/s".format(gbps)
    }

fun String.camelToSnakeCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace("-", "_")
        .lowercase()

val String.urlEncoded
    get() = URLEncoder.encode(this, Charsets.UTF_8)
val String.urlDecoded
    get() = URLDecoder.decode(this, Charsets.UTF_8)
val String.decodeBase64
    get() = String(Base64.getDecoder().decode(this), Charsets.UTF_8)
val String.encodeBase64
    get() = Base64.getEncoder().encodeToString(this.toByteArray(Charsets.UTF_8))

/**
 * Display length where CJK (Chinese/Japanese/Korean) full‑width characters and common emoji count as 2 cells,
 * others count as 1. Useful for monospace alignment / padding.
 */
val String.displayLength: Int
    get() {
        var len = 0
        var i = 0
        while (i < length) {
            val cp = codePointAt(i)
            val count = Character.charCount(cp)
            len += if (cp.isWideCodePoint()) 2 else 1
            i += count
        }
        return len
    }

/*
Heuristic for wide code points. Covers:
- CJK Unified Ideographs & Extensions
- Hangul syllables & Jamo
- Hiragana, Katakana, Bopomofo
- Fullwidth and Halfwidth forms (treat fullwidth as wide)
- Enclosed CJK, Compatibility Ideographs
- Common emoji blocks (Emoticons, Misc Symbols & Pictographs, Supplemental Symbols & Pictographs, etc.)
*/
fun Int.isWideCodePoint(): Boolean {
    // Fast path ranges
    return when {
        // CJK Unified Ideographs & Ext
        this in 0x4E00..0x9FFF || this in 0x3400..0x4DBF || this in 0x20000..0x2A6DF || this in 0x2A700..0x2B73F || this in 0x2B740..0x2B81F || this in 0x2B820..0x2CEAF -> true
        // Hangul
        this in 0xAC00..0xD7A3 || this in 0x1100..0x11FF || this in 0x3130..0x318F -> true
        // Hiragana / Katakana / Phonetic extensions
        this in 0x3040..0x309F || this in 0x30A0..0x30FF || this in 0x31F0..0x31FF || this in 0x1B000..0x1B0FF -> true
        // Bopomofo
        this in 0x3100..0x312F || this in 0x31A0..0x31BF -> true
        // Fullwidth forms
        this in 0xFF01..0xFF60 || this in 0xFFE0..0xFFE6 -> true
        // Enclosed / compatibility
        this in 0x3200..0x32FF || this in 0x3300..0x33FF || this in 0xF900..0xFAFF || this in 0x2F800..0x2FA1F -> true
        // Common emoji (approximate). Treat them as wide for alignment.
        this in 0x1F300..0x1F64F || this in 0x1F680..0x1F6FF || this in 0x1F900..0x1F9FF || this in 0x1FA70..0x1FAFF || this in 0x2600..0x26FF || this in 0x2700..0x27BF -> true
        else -> false
    }
}

fun String?.isValidHttpUrl(): Boolean {
    if (this == null)
        return false
    val urlRegex = "^(http://|https://).+".toRegex()
    return this.matches(urlRegex)
}

fun InputStream.readAllString(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}

val javaExePath
    get() = ProcessHandle.current()
        .info()
        .command().orElseThrow { IllegalArgumentException("Can't find java process path ") }

fun File.digest(algo: String): String {
    if (!this.exists()) return "0"
    val digest = MessageDigest.getInstance(algo)
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun Path.digest(algo: String): String {
    if (!this.exists()) return "0"
    val digest = MessageDigest.getInstance(algo)
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val File.sha1: String
    get() = digest("SHA-1")

val Path.sha1: String
    get() = digest("SHA-1")
val File.sha256: String
    get() = digest("SHA-256")
val File.md5: String
    get() = digest("MD5")
val File.sha512: String
    get() = digest("SHA-512")

/*val Path.murmur2 get() = runCatching { this.inputStream().murmur2 }
    .getOrElse { if (it is java.io.FileNotFoundException) 0 else throw it }
val File.murmur2 get() = runCatching { this.inputStream().murmur2 }
    .getOrElse { if (it is java.io.FileNotFoundException) 0 else throw it }
val InputStream.murmur2: Long
    get() {
        val multiplex = 1540483477u

        val data = use { it.readBytes() }
        if (data.isEmpty()) return 0
        val normalizedLength = data.count { !it.isWhitespaceCharacter }.toUInt()

        var num2 = 1u xor normalizedLength
        var num3 = 0u
        var num4 = 0

        for (byte in data) {
            if (byte.isWhitespaceCharacter) continue

            val value = (byte.toInt() and 0xFF).toUInt()
            num3 = num3 or (value shl num4)
            num4 += 8

            if (num4 == 32) {
                val num6 = num3 * multiplex
                val num7 = (num6 xor (num6 shr 24)) * multiplex
                num2 = num2 * multiplex xor num7
                num3 = 0u
                num4 = 0
            }
        }

        if (num4 > 0) {
            num2 = (num2 xor num3) * multiplex
        }

        var num6 = (num2 xor (num2 shr 13)) * multiplex
        num6 = num6 xor (num6 shr 15)
        return num6.toLong()
    }*/
val InputStream.normalizedLength: UInt
    get() {
        var count = 0u
        val buffer = ByteArray(8192)
        use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break

                for (i in 0 until read) {
                    if (!buffer[i].isWhitespaceCharacter) {
                        count += 1u
                    }
                }
            }
        }
        return count
    }
val File.normalizedLength: UInt
    get() = inputStream().use { it.normalizedLength }
val Byte.isWhitespaceCharacter: Boolean
    get() = when (this.toInt() and 0xFF) {
        9, 10, 13, 32 -> true
        else -> false

    }

fun Any.jarResource(path: String): InputStream {
    val cl = Thread.currentThread().contextClassLoader
        ?: this::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
    return cl?.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Resource not found: $path")
}

fun File.exportFromJarResource(path: String): File {
    jarResource(path).use { input ->
        this.outputStream().use { output ->
            input.copyTo(output)
        }
        return this
    }
}

fun File.openChineseZip(): ZipFile {
    var lastError: Throwable = IOException("Unable to open zip file with tried charsets")
    val attempted = mutableListOf<String>()

    fun tryOpen(charset: Charset?): ZipFile? {
        return try {
            if (charset == null) ZipFile(this) else ZipFile(this, charset)
        } catch (ex: Exception) {
            lastError = ex
            attempted += charset?.name() ?: "system-default"
            null
        }
    }

    tryOpen(null)?.let { return it }
    buildList {
        add(StandardCharsets.UTF_8)
        add(Charset.defaultCharset())
        runCatching { add(Charset.forName("GB18030")) }.getOrNull()
        runCatching { add(Charset.forName("GBK")) }.getOrNull()
        add(StandardCharsets.ISO_8859_1)
    }.filterNotNull().distinct().forEach { charset ->
        tryOpen(charset)?.let { return it }
    }
    throw lastError
}

/**
 * Recursively delete a directory and all its contents, but when encountering a symbolic link,
 * only delete the link itself, not the target it points to.
 */
fun File.deleteRecursivelyNoSymlink() {
    val path = this.toPath()

    if (!this.exists()) {
        return
    }

    // If this is a symbolic link, just delete the link itself
    if (Files.isSymbolicLink(path)) {
        Files.delete(path)
        return
    }

    // If it's a directory, recursively delete its contents first
    if (this.isDirectory) {
        this.listFiles()?.forEach { child ->
            child.deleteRecursivelyNoSymlink()
        }
    }

    // Finally delete this file/directory
    this.delete()
}

fun canCreateSymlink(): Boolean {
    val tempDir = File(System.getProperty("java.io.tmpdir")).toPath()
    val target = runCatching { Files.createTempFile(tempDir, "symlink-test-target", ".tmp") }.getOrNull()
        ?: return true
    val link: Path = tempDir.resolve("symlink-test-link-${System.currentTimeMillis()}")
    return runCatching {
        Files.deleteIfExists(link)
        Files.createSymbolicLink(link, target)
        true
    }.onFailure { err ->
        err.printStackTrace()
    }.getOrElse { false }.also {
        runCatching { Files.deleteIfExists(link) }
        runCatching { Files.deleteIfExists(target) }
    }
}

const val DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
private fun getDateTimeFormatter(pattern: String = DEFAULT_DATE_TIME_PATTERN) = DateTimeFormatter.ofPattern(pattern)
fun getDateTimeNow(pattern: String = DEFAULT_DATE_TIME_PATTERN) =
    LocalDateTime.now().format(getDateTimeFormatter(pattern))

val humanDateTimeNow
    get() = getDateTimeNow(DEFAULT_DATE_TIME_PATTERN)
val Int.secondsToHumanDateTime: String
    get() = Instant.ofEpochSecond(this.toLong()).atZone(ZoneId.systemDefault())
        .format(getDateTimeFormatter(DEFAULT_DATE_TIME_PATTERN))
val Long.millisToHumanDateTime: String
    get() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
        .format(getDateTimeFormatter(DEFAULT_DATE_TIME_PATTERN))

val Long.compactedCnCount
    get(): String = when {
        this >= 100_000_000 -> "${(this / 100_000_000.0).toFixed(2)}亿"
        this >= 10_000 -> "${(this / 10_000.0).toFixed(2)}万"
        else -> toString()
    }
