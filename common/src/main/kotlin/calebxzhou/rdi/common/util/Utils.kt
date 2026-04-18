package calebxzhou.rdi.common.util

import calebxzhou.mykotutils.std.displayLength
import calebxzhou.rdi.common.exception.RequestError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.net.URI
import java.nio.ByteBuffer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

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

val UUID.objectId : ObjectId
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
fun String.validateName(): Result<Unit> {
    val trimmed = this.trim()
    val len = trimmed.displayLength
    if (len !in 3..32) throw RequestError("名称长度需在3~32个字符，当前为${len}（一个汉字算两个）")
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
val periodOfDay: String get() = when (LocalDateTime.now().hour) {
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
