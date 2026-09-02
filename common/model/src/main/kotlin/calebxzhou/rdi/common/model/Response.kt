package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
data class Response<T>(
    val code: Int,
    val msg: String,
    val data: T? = null,
    /** Stable machine-readable error identifier; null preserves old payloads. */
    val errorCode: String? = null,
    /** Current revision returned when an optimistic-concurrency check fails. */
    val currentRevision: Long? = null,
) {
    val ok = code == 0
}

/** Stable error identifiers used by Host2 routes. */
object Host2ErrorCode {
    const val REVISION_CONFLICT = "revision_conflict"
    const val DUPLICATE = "duplicate"
    const val NOT_FOUND = "not_found"
    const val INVALID = "invalid"
    const val FORBIDDEN = "forbidden"
    const val STATE_INVALID = "state_invalid"
    const val SOURCE_UNAVAILABLE = "source_unavailable"
}
