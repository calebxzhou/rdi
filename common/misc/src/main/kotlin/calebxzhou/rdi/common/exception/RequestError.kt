package calebxzhou.rdi.common.exception

open class RequestError(
    msg: String?,
    cause: Throwable? = null,
    val errorCode: String? = null,
    val currentRevision: Long? = null,
) : Exception(msg, cause)
