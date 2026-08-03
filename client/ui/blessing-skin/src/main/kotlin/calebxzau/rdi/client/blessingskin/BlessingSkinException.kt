package calebxzau.rdi.client.blessingskin

sealed class BlessingSkinException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class HttpFailure(
        val statusCode: Int,
        val responseBody: String
    ) : BlessingSkinException("Blessing Skin请求失败($statusCode): $responseBody")

    class InvalidResponse(
        message: String,
        cause: Throwable? = null
    ) : BlessingSkinException(message, cause)
}
