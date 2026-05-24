package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import io.fusionauth.http.HTTPMethod
import io.fusionauth.http.server.HTTPRequest
import io.fusionauth.http.server.HTTPResponse
import kotlinx.serialization.KSerializer

/**
 * calebxzhou @ 2026-05-19 0:02
 */

val ROUTES = HANDLERS
    .sortedBy { it.javaClass.simpleName }
    .map { typedHandler ->
        McpRoute(
            method = typedHandler.method,
            path = "/${typedHandler.javaClass.simpleName.removeSuffix("Handler").toKebabCase()}",
            handler = typedHandler,
        )
    }
    .associateBy { it.path }

private fun String.toKebabCase(): String {
    return replace(Regex("(?<=.)(?=\\p{Upper})"), "-").lowercase()
}

data class McpRoute(
    val method: HTTPMethod,
    val path: String,
    val handler: McpTypedHandler,
)

class McpHttpContext(
    val request: HTTPRequest,
    val response: HTTPResponse,
    val game: McpGameInterface,
) {
    inline fun <reified T : Any> ymlBody(): T {
        return ymlBody(T::class.java) as T
    }

    fun ymlBody(type: Class<*>): Any {
        return runCatching {
            if (request.method.`is`(HTTPMethod.GET)) {
                type.getDeclaredConstructor().newInstance()
            } else {
                yml.decodeFromString(type.serializer(), request.bodyBytes.toString(Charsets.UTF_8))
            }
        }.getOrElse {
            throw McpBadRequestError(it.message?:"").apply { cause = it }
        }
    }
    fun param(key: String): String {
        return request.getURLParameter(key) ?: throw McpBadRequestError("lack parameter $key")
    }
    fun paramNull(key: String): String? {
        return request.getURLParameter(key)
    }

    fun intParam(key: String, default: Int): Int {
        val text = paramNull(key) ?: return default
        return text.toIntOrNull() ?: throw McpBadRequestError("$key must be integer")
    }

    fun booleanParam(key: String, default: Boolean): Boolean {
        return when (paramNull(key)?.trim()?.lowercase()) {
            null -> default
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> throw McpBadRequestError("$key must be boolean")
        }
    }

}

@Suppress("UNCHECKED_CAST")
private fun Class<*>.serializer(): KSerializer<Any> {
    val companion = getField("Companion").get(null)
    return companion.javaClass.getMethod("serializer").invoke(companion) as KSerializer<Any>
}

