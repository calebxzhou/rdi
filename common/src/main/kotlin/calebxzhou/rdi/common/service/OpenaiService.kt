package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.ai.AiToolContext
import calebxzhou.rdi.common.ai.AiToolDefinition
import calebxzhou.rdi.common.ai.AiToolDetail
import calebxzhou.rdi.common.ai.AiToolRegistry
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class OpenaiChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenaiToolCall>? = null
)

@Serializable
data class OpenaiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenaiToolFunction
)

@Serializable
data class OpenaiToolFunction(
    val name: String,
    val arguments: String
)

sealed interface OpenaiChatEvent {
    data class Delta(val content: String) : OpenaiChatEvent
    data class ReasoningDelta(val content: String) : OpenaiChatEvent
    data class ToolAccess(val target: String, val action: String = "访问") : OpenaiChatEvent
    data class ToolResult(val target: String, val detail: AiToolDetail, val action: String = "访问") : OpenaiChatEvent
    data class ToolFailed(val message: String) : OpenaiChatEvent
    data class ContextMessages(val messages: List<OpenaiChatMessage>) : OpenaiChatEvent
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val promptCacheHitTokens: Int,
        val promptCacheMissTokens: Int,
        val completionReasoningTokens: Int,
        val billablePromptTokens: Int,
        val billableCompletionTokens: Int,
        val billablePromptCacheHitTokens: Int,
        val billablePromptCacheMissTokens: Int,
        val billableCompletionReasoningTokens: Int
    ) : OpenaiChatEvent
}

object OpenaiService {
    private const val MAX_TOOL_ROUNDS = 512

    @OptIn(ExperimentalSerializationApi::class)
    private val openaiJson = Json(serdesJson) {
        explicitNulls = false
        encodeDefaults = true
    }

    fun chat(
        aiBaseUrl: String,
        apiKey: String,
        model: String,
        messages: List<OpenaiChatMessage>,
        versionDir: String? = null,
        reasoningEffort: String? = null
    ): Flow<OpenaiChatEvent> = flow {
        val baseUrl = aiBaseUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        val modelName = model.trim()
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "AI接口地址必须以http://或https://开头" }
        require(key.isNotBlank()) { "请输入API Key" }
        require(modelName.isNotBlank()) { "AI模型不能为空" }
        require(messages.isNotEmpty()) { "消息不能为空" }
        val effort = reasoningEffort?.trim()?.ifBlank { null }

        val conversation = messages.toMutableList()
        val turnContextMessages = mutableListOf<OpenaiChatMessage>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalPromptCacheHitTokens = 0
        var totalPromptCacheMissTokens = 0
        var totalCompletionReasoningTokens = 0
        var usedTool = false

        repeat(MAX_TOOL_ROUNDS + 1) { roundIndex ->
            val result = requestChatRound(baseUrl, key, modelName, effort, conversation, versionDir)
            result.usage?.let {
                totalPromptTokens += it.promptTokens
                totalCompletionTokens += it.completionTokens
                totalPromptCacheHitTokens += it.resolvedPromptCacheHitTokens()
                totalPromptCacheMissTokens += it.resolvedPromptCacheMissTokens()
                totalCompletionReasoningTokens += it.completionTokensDetails.reasoningTokens
                emit(
                    OpenaiChatEvent.Usage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens,
                        promptCacheHitTokens = it.resolvedPromptCacheHitTokens(),
                        promptCacheMissTokens = it.resolvedPromptCacheMissTokens(),
                        completionReasoningTokens = it.completionTokensDetails.reasoningTokens,
                        billablePromptTokens = totalPromptTokens,
                        billableCompletionTokens = totalCompletionTokens,
                        billablePromptCacheHitTokens = totalPromptCacheHitTokens,
                        billablePromptCacheMissTokens = totalPromptCacheMissTokens,
                        billableCompletionReasoningTokens = totalCompletionReasoningTokens
                    )
                )
            }
            if (result.toolCalls.isEmpty()) {
                turnContextMessages += OpenaiChatMessage(
                    role = "assistant",
                    content = result.content.takeIf(String::isNotBlank),
                    reasoningContent = result.reasoningContent.takeIf { usedTool && it.isNotBlank() }
                )
                emit(OpenaiChatEvent.ContextMessages(turnContextMessages))
                return@flow
            }
            if (roundIndex >= MAX_TOOL_ROUNDS) {
                emit(OpenaiChatEvent.ToolFailed("AI工具调用次数过多，已停止继续访问外部地址"))
                emit(OpenaiChatEvent.ContextMessages(turnContextMessages))
                return@flow
            }

            usedTool = true
            val assistantToolMessage = OpenaiChatMessage(
                role = "assistant",
                content = result.content.takeIf(String::isNotBlank),
                reasoningContent = result.reasoningContent.takeIf(String::isNotBlank),
                toolCalls = result.toolCalls
            )
            conversation += assistantToolMessage
            turnContextMessages += assistantToolMessage
            result.toolCalls.forEach { toolCall ->
                val toolContent = executeToolCall(toolCall, versionDir)
                val toolMessage = OpenaiChatMessage(
                    role = "tool",
                    content = toolContent,
                    toolCallId = toolCall.id
                )
                conversation += toolMessage
                turnContextMessages += toolMessage
            }
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.requestChatRound(
        baseUrl: String,
        apiKey: String,
        model: String,
        reasoningEffort: String?,
        messages: List<OpenaiChatMessage>,
        versionDir: String?
    ): ChatRoundResult {
        val toolContext = createToolContext(versionDir)
        val tools = AiToolRegistry.availableDefinitions(toolContext).map { OpenaiChatTool(function = it) }
        val response = ktorClient.request {
            url("$baseUrl/chat/completions")
            method = HttpMethod.Post
            timeout {
                requestTimeoutMillis = 5 * 60 * 1000L
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 5 * 60 * 1000L
            }
            json()
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(
                openaiJson.encodeToString(
                    ChatCompletionRequest(
                        model = model,
                        messages = messages,
                        reasoningEffort = reasoningEffort,
                        stream = true,
                        streamOptions = ChatStreamOptions(includeUsage = true),
                        tools = tools
                    )
                )
            )
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw IllegalStateException(response.toUserFriendlyChatError(errorBody))
        }

        val content = StringBuilder()
        val reasoningContent = StringBuilder()
        val toolCallBuilders = linkedMapOf<Int, ToolCallBuilder>()
        var usage: ChatCompletionUsage? = null
        val channel: ByteReadChannel = response.body()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val payload = line.trim()
                .takeIf { it.startsWith("data:") }
                ?.removePrefix("data:")
                ?.trim()
                ?: continue
            if (payload == "[DONE]") break

            val chunk = runCatching {
                serdesJson.decodeFromString<ChatCompletionChunk>(payload)
            }.getOrNull() ?: continue
            chunk.usage?.let { usage = it }
            chunk.choices.forEach { choice ->
                choice.delta.content?.takeIf(String::isNotEmpty)?.let {
                    content.append(it)
                    emit(OpenaiChatEvent.Delta(it))
                }
                choice.delta.reasoningContent?.takeIf(String::isNotEmpty)?.let {
                    reasoningContent.append(it)
                    emit(OpenaiChatEvent.ReasoningDelta(it))
                }
                choice.delta.toolCalls.forEach { toolCall ->
                    val builder = toolCallBuilders.getOrPut(toolCall.index) { ToolCallBuilder(toolCall.index) }
                    toolCall.id?.let { builder.id = it }
                    toolCall.type?.let { builder.type = it }
                    toolCall.function?.name?.let { builder.name = it }
                    toolCall.function?.arguments?.let { builder.arguments.append(it) }
                }
            }
        }
        return ChatRoundResult(
            content = content.toString(),
            reasoningContent = reasoningContent.toString(),
            toolCalls = toolCallBuilders.values.mapIndexed { fallbackIndex, builder -> builder.build(fallbackIndex) },
            usage = usage
        )
    }

    private fun io.ktor.client.statement.HttpResponse.toUserFriendlyChatError(body: String): String {
        val detail = body.extractAiErrorMessage()
        return when (status.value) {
            401 -> "API Key错误，请检查AI设置"
            402 -> "余额不足请充值"
            429 -> "请求太频繁，请稍后再试"
            500 -> "AI服务暂时故障，请稍后再试"
            503 -> "AI服务繁忙，请稍后再试"
            else -> detail?.let { "AI聊天请求失败：$it" } ?: "AI聊天请求失败：${status.value} ${status.description}"
        }
    }

    private fun String.extractAiErrorMessage(): String? =
        runCatching {
            val root = serdesJson.parseToJsonElement(this).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf(String::isNotBlank)

    private suspend fun FlowCollector<OpenaiChatEvent>.executeToolCall(toolCall: OpenaiToolCall, versionDir: String?): String {
        val execution = AiToolRegistry.execute(
            name = toolCall.function.name,
            argumentsJson = toolCall.function.arguments,
            context = createToolContext(versionDir)
        )
        execution.access?.let { emit(OpenaiChatEvent.ToolAccess(it.target, it.action)) }
        execution.detail?.let { detail ->
            val access = execution.access
            emit(OpenaiChatEvent.ToolResult(access?.target ?: execution.result.target.orEmpty(), detail, access?.action ?: "访问"))
        }
        execution.result.error?.let { emit(OpenaiChatEvent.ToolFailed(it)) }
        return openaiJson.encodeToString(execution.result)
    }

    private fun createToolContext(versionDir: String?): AiToolContext {
        val rdiRoot = runCatching { DIR.canonicalFile.takeIf(File::isDirectory) }.getOrNull()
        val root = runCatching {
            versionDir?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { File(it).canonicalFile }
                ?.takeIf(File::isDirectory)
        }.getOrNull()
        return AiToolContext(rdiDir = rdiRoot, versionDir = root, debug = DEBUG)
    }

    @Serializable
    private data class OpenaiChatTool(
        val type: String = "function",
        val function: AiToolDefinition
    )

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<OpenaiChatMessage>,
        @SerialName("reasoning_effort")
        val reasoningEffort: String? = null,
        val stream: Boolean,
        @SerialName("stream_options")
        val streamOptions: ChatStreamOptions,
        val tools: List<OpenaiChatTool>
    )

    @Serializable
    private data class ChatStreamOptions(
        @SerialName("include_usage")
        val includeUsage: Boolean
    )

    @Serializable
    private data class ChatCompletionChunk(
        val choices: List<ChatCompletionChoice> = emptyList(),
        val usage: ChatCompletionUsage? = null
    )

    @Serializable
    private data class ChatCompletionChoice(
        val delta: ChatCompletionDelta = ChatCompletionDelta()
    )

    @Serializable
    private data class ChatCompletionDelta(
        val content: String? = null,
        @SerialName("reasoning_content")
        val reasoningContent: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<OpenaiToolCallDelta> = emptyList()
    )

    @Serializable
    private data class ChatCompletionUsage(
        @SerialName("prompt_tokens")
        val promptTokens: Int = 0,
        @SerialName("completion_tokens")
        val completionTokens: Int = 0,
        @SerialName("total_tokens")
        val totalTokens: Int = 0,
        @SerialName("prompt_cache_hit_tokens")
        val promptCacheHitTokens: Int = 0,
        @SerialName("prompt_cache_miss_tokens")
        val promptCacheMissTokens: Int = 0,
        @SerialName("completion_tokens_details")
        val completionTokensDetails: ChatCompletionTokenDetails = ChatCompletionTokenDetails()
    ) {
        fun resolvedPromptCacheHitTokens(): Int = promptCacheHitTokens.coerceIn(0, promptTokens)

        fun resolvedPromptCacheMissTokens(): Int {
            if (promptCacheHitTokens > 0 || promptCacheMissTokens > 0) {
                return promptCacheMissTokens.coerceIn(0, promptTokens)
            }
            return promptTokens
        }
    }

    @Serializable
    private data class ChatCompletionTokenDetails(
        @SerialName("reasoning_tokens")
        val reasoningTokens: Int = 0
    )

    @Serializable
    private data class OpenaiToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val type: String? = null,
        val function: OpenaiToolFunctionDelta? = null
    )

    @Serializable
    private data class OpenaiToolFunctionDelta(
        val name: String? = null,
        val arguments: String? = null
    )

    private data class ToolCallBuilder(
        val index: Int,
        var id: String = "",
        var type: String = "function",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun build(fallbackIndex: Int): OpenaiToolCall {
            return OpenaiToolCall(
                id = id.ifBlank { "call_${System.currentTimeMillis()}_$fallbackIndex" },
                type = type.ifBlank { "function" },
                function = OpenaiToolFunction(
                    name = name,
                    arguments = arguments.toString()
                )
            )
        }
    }

    private data class ChatRoundResult(
        val content: String,
        val reasoningContent: String,
        val toolCalls: List<OpenaiToolCall>,
        val usage: ChatCompletionUsage?
    )

}
