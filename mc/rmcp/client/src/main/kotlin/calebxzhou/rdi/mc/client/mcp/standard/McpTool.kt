package calebxzhou.rdi.mc.client.mcp.standard

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpParam
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.reflect.KClass

interface StandardMcpTool {
    val name: String
        get() = javaClass.simpleName.removeSuffix("Tool").toSnakeCase()
    val title: String
        get() = name.toMcpTitle()
    val description: String
    val inputSchema: JsonObject
        get() = emptyObjectSchema()
    val readOnly: Boolean
        get() = true
    val destructive: Boolean
        get() = false

    fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult>
}

abstract class TypedMcpTool<Q : Any>(
    protected val serializer: KSerializer<Q>,
    protected val reqClass: KClass<Q>,
) : StandardMcpTool {
    override val inputSchema: JsonObject = inputSchema(serializer)

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            json.decodeFromJsonElement(serializer, params)
        }.mapCatching { req ->
            callTyped(req, game).getOrThrow()
        }
    }

    protected open fun callTyped(req: Q, game: McpGameInterface): Result<StandardMcpToolResult> {
        return sendTyped(req, game)
    }

    protected fun sendTyped(req: Q, game: McpGameInterface): Result<StandardMcpToolResult> {
        val packet = McpC2SNetPacket(
            reqId = UUID.randomUUID().toString(),
            className = reqClass.java.name,
            reqJson = json.encodeToString(serializer, req),
        )
        return game.send(packet).map { text -> StandardMcpToolResult.text(text) }
    }
}

private fun String.toMcpTitle(): String {
    return split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
}

private fun String.toSnakeCase(): String {
    return foldIndexed(StringBuilder()) { index, builder, ch ->
        if (ch.isUpperCase() && index > 0) {
            builder.append('_')
        }
        builder.append(ch.lowercaseChar())
    }.toString()
}

data class StandardMcpToolResult(
    val content: List<JsonObject>,
    val isError: Boolean = false,
) {
    companion object {
        fun text(text: String, isError: Boolean = false): StandardMcpToolResult {
            return StandardMcpToolResult(
                content = listOf(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }),
                isError = isError,
            )
        }

        fun imagePng(base64Data: String): StandardMcpToolResult {
            return StandardMcpToolResult(
                content = listOf(buildJsonObject {
                    put("type", "image")
                    put("mimeType", "image/png")
                    put("data", base64Data)
                }),
            )
        }
    }
}

fun emptyObjectSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", JsonObject(emptyMap()))
    put("additionalProperties", false)
}

fun objectSchema(required: List<String>, vararg properties: Pair<String, JsonElement>): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        properties.forEach { (name, schema) -> put(name, schema) }
    })
    if (required.isNotEmpty()) {
        put("required", buildJsonArray {
            required.forEach { add(JsonPrimitive(it)) }
        })
    }
    put("additionalProperties", false)
}

fun stringSchema(description: String? = null): JsonObject = buildJsonObject {
    put("type", "string")
    if (description != null) put("description", description)
}

fun posSchema(description: String? = null): JsonObject = stringSchema(description ?: "Block position in \"x y z\" format.")

fun stringMapSchema(description: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("description", description)
    put("additionalProperties", stringSchema())
}

fun integerMapSchema(description: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("description", description)
    put("additionalProperties", integerSchema("Map value."))
}

fun integerSchema(description: String, minimum: Int? = null): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
    if (minimum != null) put("minimum", minimum)
}

fun numberSchema(description: String): JsonObject = buildJsonObject {
    put("type", "number")
    put("description", description)
}

fun booleanSchema(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

fun enumSchema(values: List<String>, description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    put("enum", buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    })
}

fun arraySchema(items: JsonElement, description: String, minItems: Int? = null): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    put("items", items)
    if (minItems != null) put("minItems", minItems)
}

@OptIn(ExperimentalSerializationApi::class)
fun inputSchema(serializer: KSerializer<*>): JsonObject {
    return objectSchema(serializer.descriptor)
}

@OptIn(ExperimentalSerializationApi::class)
private fun objectSchema(descriptor: SerialDescriptor, param: McpParam? = null): JsonObject = buildJsonObject {
    put("type", "object")
    putDescription(param)
    put("properties", buildJsonObject {
        repeat(descriptor.elementsCount) { index ->
            val param = descriptor.getElementAnnotations(index).filterIsInstance<McpParam>().firstOrNull()
            put(descriptor.getElementName(index), jsonSchema(descriptor.getElementDescriptor(index), param))
        }
    })
    val required = (0 until descriptor.elementsCount)
        .filterNot { descriptor.isElementOptional(it) }
        .map { descriptor.getElementName(it) }
    if (required.isNotEmpty()) {
        put("required", buildJsonArray {
            required.forEach { add(JsonPrimitive(it)) }
        })
    }
    put("additionalProperties", false)
}

@OptIn(ExperimentalSerializationApi::class)
private fun jsonSchema(descriptor: SerialDescriptor, param: McpParam?): JsonObject {
    return when (descriptor.kind) {
        PrimitiveKind.STRING -> primitiveSchema("string", param)
        PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> primitiveSchema("integer", param)
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> primitiveSchema("number", param)
        PrimitiveKind.BOOLEAN -> primitiveSchema("boolean", param)
        StructureKind.LIST -> buildJsonObject {
            put("type", "array")
            putDescription(param)
            put("items", jsonSchema(descriptor.getElementDescriptor(0), null))
            if (param != null && param.minItems != Int.MIN_VALUE) {
                put("minItems", param.minItems)
            }
        }
        StructureKind.MAP -> buildJsonObject {
            put("type", "object")
            putDescription(param)
            put("additionalProperties", jsonSchema(descriptor.getElementDescriptor(1), null))
        }
        StructureKind.CLASS, StructureKind.OBJECT -> objectSchema(descriptor, param)
        else -> {
            if (descriptor.kind == SerialKind.ENUM) enumDescriptorSchema(descriptor, param) else primitiveSchema("string", param)
        }
    }
}

private fun primitiveSchema(type: String, param: McpParam?): JsonObject = buildJsonObject {
    put("type", type)
    putDescription(param)
    if (type == "string" && param != null && param.enumValues.isNotEmpty()) {
        put("enum", buildJsonArray {
            param.enumValues.forEach { add(JsonPrimitive(it)) }
        })
    }
    if (type == "integer" && param != null && param.minimum != Int.MIN_VALUE) {
        put("minimum", param.minimum)
    }
    if (type == "integer" && param != null && param.maximum != Int.MAX_VALUE) {
        put("maximum", param.maximum)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun enumDescriptorSchema(descriptor: SerialDescriptor, param: McpParam?): JsonObject = buildJsonObject {
    put("type", "string")
    putDescription(param)
    put("enum", buildJsonArray {
        repeat(descriptor.elementsCount) { index ->
            add(JsonPrimitive(descriptor.getElementName(index)))
        }
    })
}

private fun JsonObjectBuilder.putDescription(param: McpParam?) {
    if (param != null) {
        put("description", param.description)
    }
}

fun <Q : Any> callGameSend(
    params: JsonObject,
    game: McpGameInterface,
    serializer: KSerializer<Q>,
    reqClass: KClass<Q>,
): Result<StandardMcpToolResult> {
    return runCatching {
        json.decodeFromJsonElement(serializer, params)
    }.mapCatching { req ->
        val packet = McpC2SNetPacket(
            reqId = UUID.randomUUID().toString(),
            className = reqClass.java.name,
            reqJson = json.encodeToString(serializer, req),
        )
        StandardMcpToolResult.text(game.send(packet).getOrThrow())
    }
}

fun StandardMcpTool.toProtocolJson(): JsonObject = buildJsonObject {
    put("name", name)
    put("title", title)
    put("description", description)
    put("inputSchema", inputSchema)
    put("execution", buildJsonObject {
        put("taskSupport", "forbidden")
    })
    put("annotations", buildJsonObject {
        put("title", title)
        put("readOnlyHint", readOnly)
        put("destructiveHint", destructive)
        put("openWorldHint", true)
    })
}

fun StandardMcpToolResult.toProtocolJson(): JsonObject = buildJsonObject {
    put("content", JsonArray(content))
    if (isError) {
        put("isError", true)
    }
}
