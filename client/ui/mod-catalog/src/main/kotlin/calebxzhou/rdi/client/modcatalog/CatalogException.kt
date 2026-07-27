package calebxzhou.rdi.client.modcatalog

sealed class CatalogException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Unauthorized(val platform: ModPlatform) : CatalogException("$platform rejected the credentials")

    class RateLimited(
        val platform: ModPlatform,
        val retryAfterMillis: Long?
    ) : CatalogException("$platform rate limit exceeded")

    class Network(val platform: ModPlatform, cause: Throwable) :
        CatalogException("$platform request failed", cause)

    class InvalidResponse(val platform: ModPlatform, cause: Throwable? = null) :
        CatalogException("$platform returned an invalid response", cause)

    class AllSourcesFailed(val failures: Map<ModPlatform, Throwable>) :
        CatalogException("All mod catalog sources failed")

    class DownloadUnavailable(val file: CatalogFileRef) :
        CatalogException("Download is unavailable for $file")

    class IdentityDatabase(message: String, cause: Throwable? = null) : CatalogException(message, cause)
}
