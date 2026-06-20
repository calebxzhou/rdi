package calebxzhou.rdi.mc.client.mcp.standard

import calebxzhou.rdi.mc.common2.mcp.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object JsonRpc {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    fun parseRequest(line: String): Result<JsonRpcRequest> = runCatching {
        val root = json.parseToJsonElement(line).jsonObject
        val jsonrpc = root["jsonrpc"]?.jsonPrimitive?.content
            ?: throw JsonRpcException(INVALID_REQUEST, "missing jsonrpc")
        if (jsonrpc != "2.0") {
            throw JsonRpcException(INVALID_REQUEST, "jsonrpc must be 2.0")
        }
        val method = root["method"]?.jsonPrimitive?.content
            ?: throw JsonRpcException(INVALID_REQUEST, "missing method")
        JsonRpcRequest(
            id = root["id"],
            method = method,
            params = root["params"],
        )
    }

    fun success(id: JsonElement?, result: JsonElement = JsonObject(emptyMap())): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

    fun error(id: JsonElement?, code: Int, message: String, data: JsonElement? = null): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
            if (data != null) {
                put("data", data)
            }
        })
    }

    fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)
}

data class JsonRpcRequest(
    val id: JsonElement?,
    val method: String,
    val params: JsonElement?,
) {
    val isNotification get() = id == null
}

class JsonRpcException(
    val code: Int,
    override val message: String,
    val errorData: JsonElement? = null,
) : RuntimeException(message)

fun JsonElement?.asObjectOrEmpty(): JsonObject {
    return when (this) {
        null, JsonNull -> JsonObject(emptyMap())
        is JsonObject -> this
        else -> throw JsonRpcException(JsonRpc.INVALID_PARAMS, "params must be object")
    }
}

fun JsonObject.optionalString(name: String): String? {
    val value = this[name] ?: return null
    return value.jsonPrimitive.content
}

fun JsonObject.requiredString(name: String): String {
    return optionalString(name) ?: throw JsonRpcException(JsonRpc.INVALID_PARAMS, "missing parameter $name")
}
