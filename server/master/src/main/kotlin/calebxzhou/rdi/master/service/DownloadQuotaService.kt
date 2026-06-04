package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.DownloadQuota
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.net.uid
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId
import java.time.LocalDate
import java.time.ZoneId

fun Route.downloadQuotaRoutes() = route("/download") {
    get("/quota") {
        response(data = DownloadQuotaService.getQuota(uid))
    }
}

object DownloadQuotaService {
    private const val DAILY_LIMIT_BYTES = 2L * 1024 * 1024 * 1024
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val quotaCol: MongoCollection<DownloadQuota> = DB.getCollection("dl_quota")

    suspend fun ensureIndexes() {
        quotaCol.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending(DownloadQuota::uid.name),
                Indexes.ascending(DownloadQuota::day.name)
            ),
            IndexOptions().unique(true).name("uk_uid_day")
        )
    }

    suspend fun reserve(uid: ObjectId, bytes: Long) {
        if (bytes <= 0) return
        if (bytes > DAILY_LIMIT_BYTES) {
            throw RequestError("文件尺寸${bytes.humanFileSize}超过今日额度，还剩${DAILY_LIMIT_BYTES.humanFileSize}")
        }

        val day = LocalDate.now(zoneId).toString()
        val remainingBeforeDownload = DAILY_LIMIT_BYTES - bytes
        val now = System.currentTimeMillis()
        val filter = and(
            eq(DownloadQuota::uid.name, uid),
            eq(DownloadQuota::day.name, day),
            lte(DownloadQuota::usedBytes.name, remainingBeforeDownload)
        )
        val update = Updates.combine(
            Updates.inc(DownloadQuota::usedBytes.name, bytes),
            Updates.set(DownloadQuota::updatedAt.name, now),
            Updates.setOnInsert(DownloadQuota::uid.name, uid),
            Updates.setOnInsert(DownloadQuota::day.name, day)
        )

        val result = runCatching {
            quotaCol.updateOne(filter, update, UpdateOptions().upsert(true))
        }.getOrElse { error ->
            if (error is MongoWriteException && ErrorCategory.fromErrorCode(error.code) == ErrorCategory.DUPLICATE_KEY) {
                quotaCol.updateOne(filter, update)
            } else {
                throw error
            }
        }
        if (result.matchedCount > 0 || result.upsertedId != null) return

        val usedBytes = quotaCol.find(
            and(
                eq(DownloadQuota::uid.name, uid),
                eq(DownloadQuota::day.name, day)
            )
        ).firstOrNull()?.usedBytes ?: 0
        val remaining = (DAILY_LIMIT_BYTES - usedBytes).coerceAtLeast(0)
        throw RequestError("今日下载额度不足，剩余${remaining.humanFileSize}")
    }

    suspend fun getQuota(uid: ObjectId): DownloadQuota.Vo {
        val day = LocalDate.now(zoneId).toString()
        val usedBytes = quotaCol.find(
            and(
                eq(DownloadQuota::uid.name, uid),
                eq(DownloadQuota::day.name, day)
            )
        ).firstOrNull()?.usedBytes ?: 0
        return DownloadQuota.Vo(
            limitBytes = DAILY_LIMIT_BYTES,
            usedBytes = usedBytes,
            remainingBytes = (DAILY_LIMIT_BYTES - usedBytes).coerceAtLeast(0),
            day = day
        )
    }
}
