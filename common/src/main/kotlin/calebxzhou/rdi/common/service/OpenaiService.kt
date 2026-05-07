package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.net.DynamicProxySelector
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.util.javaExePath
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

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
    data class ToolFailed(val message: String) : OpenaiChatEvent
    data class ContextMessages(val messages: List<OpenaiChatMessage>) : OpenaiChatEvent
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val billablePromptTokens: Int,
        val billableCompletionTokens: Int
    ) : OpenaiChatEvent
}

object OpenaiService {
    private const val HTTP_TOOL_NAME = "http_request"
    private const val JAR_CLASS_SEARCH_TOOL_NAME = "jar_class_search"
    private const val JAVAP_CLASS_TOOL_NAME = "javap_class"
    private const val JDEPS_JAR_TOOL_NAME = "jdeps_jar"
    private const val LOCAL_FILE_LIST_TOOL_NAME = "local_file_list"
    private const val LOCAL_TEXT_READ_TOOL_NAME = "local_text_read"
    private const val MAX_TOOL_ROUNDS = 32
    private const val MAX_TOOL_RESPONSE_BYTES = 64 * 1024 * 1024
    private const val MAX_TOOL_CONTENT_CHARS = 32 * 1024
    private const val MAX_JAVA_TOOL_OUTPUT_CHARS = 32 * 1024
    private const val MAX_LOCAL_TEXT_BYTES = 1024 * 1024
    private const val MAX_LOCAL_TEXT_OUTPUT_CHARS = 32 * 1024
    private const val JAVA_TOOL_TIMEOUT_MILLIS = 15_000L
    private val allowedToolHosts = setOf("minecraft.wiki", "mcmod.cn", "bilibili.com")

    @OptIn(ExperimentalSerializationApi::class)
    private val openaiJson = Json(serdesJson) {
        explicitNulls = false
        encodeDefaults = true
    }

    private val toolHttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    followRedirects(false)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    proxySelector(DynamicProxySelector())
                }
            }
            BrowserUserAgent()
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
                identity()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    fun chat(
        aiBaseUrl: String,
        apiKey: String,
        model: String,
        messages: List<OpenaiChatMessage>,
        versionDir: String? = null
    ): Flow<OpenaiChatEvent> = flow {
        val baseUrl = aiBaseUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        val modelName = model.trim()
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "AI接口地址必须以http://或https://开头" }
        require(key.isNotBlank()) { "请输入API Key" }
        require(modelName.isNotBlank()) { "AI模型不能为空" }
        require(messages.isNotEmpty()) { "消息不能为空" }

        val conversation = messages.toMutableList()
        val turnContextMessages = mutableListOf<OpenaiChatMessage>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var usedTool = false

        repeat(MAX_TOOL_ROUNDS + 1) { roundIndex ->
            val result = requestChatRound(baseUrl, key, modelName, conversation, versionDir)
            result.usage?.let {
                totalPromptTokens += it.promptTokens
                totalCompletionTokens += it.completionTokens
                emit(
                    OpenaiChatEvent.Usage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens,
                        billablePromptTokens = totalPromptTokens,
                        billableCompletionTokens = totalCompletionTokens
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
        messages: List<OpenaiChatMessage>,
        versionDir: String?
    ): ChatRoundResult {
        val tools = if (versionDir.isNullOrBlank()) {
            listOf(httpRequestTool, jarClassSearchTool, javapClassTool, jdepsJarTool)
        } else {
            listOf(httpRequestTool, localFileListTool, localTextReadTool, jarClassSearchTool, javapClassTool, jdepsJarTool)
        }
        val response = ktorClient.request {
            url("$baseUrl/chat/completions")
            method = HttpMethod.Post
            json()
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(
                openaiJson.encodeToString(
                    ChatCompletionRequest(
                        model = model,
                        messages = messages,
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
        return when (toolCall.function.name) {
            HTTP_TOOL_NAME -> executeHttpTool(toolCall)
            LOCAL_FILE_LIST_TOOL_NAME -> executeLocalFileListTool(toolCall, versionDir)
            LOCAL_TEXT_READ_TOOL_NAME -> executeLocalTextReadTool(toolCall, versionDir)
            JAR_CLASS_SEARCH_TOOL_NAME -> executeJarClassSearchTool(toolCall)
            JAVAP_CLASS_TOOL_NAME -> executeJavapClassTool(toolCall)
            JDEPS_JAR_TOOL_NAME -> executeJdepsJarTool(toolCall)
            else -> {
                val message = "不支持的AI工具: ${toolCall.function.name}"
                emit(OpenaiChatEvent.ToolFailed(message))
                openaiJson.encodeToString(ToolResult(tool = toolCall.function.name, error = message))
            }
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeHttpTool(toolCall: OpenaiToolCall): String {
        val args = runCatching {
            openaiJson.decodeFromString<HttpRequestToolArgs>(toolCall.function.arguments)
        }.getOrElse {
            val message = "AI工具参数解析失败"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = HTTP_TOOL_NAME, error = message))
        }

        val uri = runCatching { validateToolUrl(args.url) }.getOrElse {
            val message = it.message ?: "URL不在允许访问范围内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = HTTP_TOOL_NAME, target = sanitizeToolTarget(args.url), error = message))
        }
        val visibleTarget = sanitizeToolTarget(uri)
        val method = runCatching { parseToolMethod(args.method) }.getOrElse {
            val message = it.message ?: "只允许GET和POST请求"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = HTTP_TOOL_NAME, target = visibleTarget, error = message))
        }

        emit(OpenaiChatEvent.ToolAccess(visibleTarget))
        return runCatching {
            val response = toolHttpClient.request {
                url(uri.toString())
                this.method = method
                header(HttpHeaders.Accept, "text/html,application/json,text/plain,*/*")
                if (method == HttpMethod.Post) {
                    contentType(args.contentType.toContentType())
                    setBody(args.body.orEmpty())
                }
            }
            val body = readLimitedResponse(response.body())
            val text = String(body, Charsets.UTF_8)
            val truncatedForModel = text.length > MAX_TOOL_CONTENT_CHARS
            openaiJson.encodeToString(
                ToolResult(
                    tool = HTTP_TOOL_NAME,
                    target = visibleTarget,
                    status = response.status.value,
                    contentType = response.headers[HttpHeaders.ContentType],
                    output = text.take(MAX_TOOL_CONTENT_CHARS),
                    outputBytes = body.size,
                    truncated = truncatedForModel
                )
            )
        }.getOrElse {
            val message = it.message ?: "HTTP工具请求失败"
            emit(OpenaiChatEvent.ToolFailed(message))
            openaiJson.encodeToString(ToolResult(tool = HTTP_TOOL_NAME, target = visibleTarget, error = message))
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeLocalFileListTool(
        toolCall: OpenaiToolCall,
        versionDir: String?
    ): String {
        val args = decodeToolArgs<LocalFileListToolArgs>(toolCall)
            ?: return toolArgError(toolCall)
        val root = resolveVersionDir(versionDir).getOrElse {
            val message = it.message ?: "未提供整合包目录"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = LOCAL_FILE_LIST_TOOL_NAME, error = message))
        }
        val target = resolveVersionChild(root, args.path).getOrElse {
            val message = it.message ?: "路径不在整合包目录内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = LOCAL_FILE_LIST_TOOL_NAME, target = args.path, error = message))
        }
        emit(OpenaiChatEvent.ToolAccess(target.relativeToOrSelf(root).path.ifBlank { root.name }, "读取"))
        return runCatching {
            require(target.isDirectory) { "只能列目录: ${target.absolutePath}" }
            val limit = args.limit.coerceIn(1, 1000)
            val files = withContext(Dispatchers.IO) {
                val sequence = if (args.recursive) target.walkTopDown().drop(1) else target.listFiles().orEmpty().asSequence()
                sequence.take(limit).map { file ->
                    val rel = file.relativeTo(root).invariantSeparatorsPath
                    val suffix = when {
                        file.isDirectory -> "/"
                        file.isFile -> " ${file.length()}B"
                        else -> ""
                    }
                    rel + suffix
                }.toList()
            }
            openaiJson.encodeToString(
                ToolResult(
                    tool = LOCAL_FILE_LIST_TOOL_NAME,
                    target = target.absolutePath,
                    output = files.joinToString("\n"),
                    resultCount = files.size,
                    truncated = files.size == limit
                )
            )
        }.getOrElse {
            val message = it.message ?: "列目录失败"
            emit(OpenaiChatEvent.ToolFailed(message))
            openaiJson.encodeToString(ToolResult(tool = LOCAL_FILE_LIST_TOOL_NAME, target = target.absolutePath, error = message))
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeLocalTextReadTool(
        toolCall: OpenaiToolCall,
        versionDir: String?
    ): String {
        val args = decodeToolArgs<LocalTextReadToolArgs>(toolCall)
            ?: return toolArgError(toolCall)
        val root = resolveVersionDir(versionDir).getOrElse {
            val message = it.message ?: "未提供整合包目录"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = LOCAL_TEXT_READ_TOOL_NAME, error = message))
        }
        val target = resolveVersionChild(root, args.path).getOrElse {
            val message = it.message ?: "路径不在整合包目录内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = LOCAL_TEXT_READ_TOOL_NAME, target = args.path, error = message))
        }
        emit(OpenaiChatEvent.ToolAccess(target.relativeToOrSelf(root).path, "读取"))
        return runCatching {
            require(target.isFile) { "只能读取文件: ${target.absolutePath}" }
            require(target.length() <= MAX_LOCAL_TEXT_BYTES) { "文件超过1MB，请缩小目标文件" }
            val bytes = withContext(Dispatchers.IO) { target.readBytes() }
            val text = bytes.toString(Charsets.UTF_8)
            val truncated = text.length > MAX_LOCAL_TEXT_OUTPUT_CHARS
            openaiJson.encodeToString(
                ToolResult(
                    tool = LOCAL_TEXT_READ_TOOL_NAME,
                    target = target.absolutePath,
                    output = text.take(MAX_LOCAL_TEXT_OUTPUT_CHARS),
                    outputBytes = bytes.size,
                    truncated = truncated
                )
            )
        }.getOrElse {
            val message = it.message ?: "读取文本失败"
            emit(OpenaiChatEvent.ToolFailed(message))
            openaiJson.encodeToString(ToolResult(tool = LOCAL_TEXT_READ_TOOL_NAME, target = target.absolutePath, error = message))
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeJarClassSearchTool(toolCall: OpenaiToolCall): String {
        val args = decodeToolArgs<JarClassSearchToolArgs>(toolCall)
            ?: return toolArgError(toolCall)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            val message = it.message ?: "Jar不在允许分析范围内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JAR_CLASS_SEARCH_TOOL_NAME, target = args.jarPath, error = message))
        }
        emit(OpenaiChatEvent.ToolAccess(jar.name, "分析"))
        return runCatching {
            val query = args.query?.trim()?.takeIf(String::isNotBlank)
            val limit = args.limit.coerceIn(1, 500)
            val classes = withContext(Dispatchers.IO) {
                JarFile(jar).use { jarFile ->
                    jarFile.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".class") && !it.endsWith("module-info.class") }
                        .map { it.removeSuffix(".class").replace('/', '.') }
                        .filter { className -> query == null || className.contains(query, ignoreCase = true) }
                        .take(limit)
                        .toList()
                }
            }
            openaiJson.encodeToString(
                ToolResult(
                    tool = JAR_CLASS_SEARCH_TOOL_NAME,
                    target = jar.absolutePath,
                    output = classes.joinToString("\n"),
                    resultCount = classes.size,
                    truncated = classes.size == limit
                )
            )
        }.getOrElse {
            val message = it.message ?: "Jar类列表读取失败"
            emit(OpenaiChatEvent.ToolFailed(message))
            openaiJson.encodeToString(ToolResult(tool = JAR_CLASS_SEARCH_TOOL_NAME, target = jar.absolutePath, error = message))
        }
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeJavapClassTool(toolCall: OpenaiToolCall): String {
        val args = decodeToolArgs<JavapClassToolArgs>(toolCall)
            ?: return toolArgError(toolCall)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            val message = it.message ?: "Jar不在允许分析范围内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JAVAP_CLASS_TOOL_NAME, target = args.jarPath, error = message))
        }
        val className = args.className.trim()
        if (!className.matches(Regex("""[A-Za-z0-9_.$]+"""))) {
            val message = "类名格式不合法: $className"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JAVAP_CLASS_TOOL_NAME, target = jar.absolutePath, error = message))
        }
        val javap = resolveJavaTool("javap").getOrElse {
            val message = it.message ?: "找不到javap"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JAVAP_CLASS_TOOL_NAME, target = jar.absolutePath, error = message))
        }
        val command = buildList {
            add(javap.absolutePath)
            add(if (args.visibility == "private") "-private" else "-public")
            if (args.bytecode) add("-c")
            if (args.constants) add("-constants")
            add("-classpath")
            add(jar.absolutePath)
            add(className)
        }
        emit(OpenaiChatEvent.ToolAccess("${jar.name}::$className", "分析"))
        return executeJavaCommandTool(JAVAP_CLASS_TOOL_NAME, jar.absolutePath, command)
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeJdepsJarTool(toolCall: OpenaiToolCall): String {
        val args = decodeToolArgs<JdepsJarToolArgs>(toolCall)
            ?: return toolArgError(toolCall)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            val message = it.message ?: "Jar不在允许分析范围内"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JDEPS_JAR_TOOL_NAME, target = args.jarPath, error = message))
        }
        val jdeps = resolveJavaTool("jdeps").getOrElse {
            val message = it.message ?: "找不到jdeps"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JDEPS_JAR_TOOL_NAME, target = jar.absolutePath, error = message))
        }
        val packageFilter = args.packageFilter?.trim()?.takeIf(String::isNotBlank)
        if (packageFilter != null && !packageFilter.matches(Regex("""[A-Za-z0-9_.$*]+"""))) {
            val message = "包名过滤格式不合法: $packageFilter"
            emit(OpenaiChatEvent.ToolFailed(message))
            return openaiJson.encodeToString(ToolResult(tool = JDEPS_JAR_TOOL_NAME, target = jar.absolutePath, error = message))
        }
        val command = buildList {
            add(jdeps.absolutePath)
            add("-q")
            if (args.verbose) add("-verbose:class")
            packageFilter?.let {
                add("-p")
                add(it)
            }
            add(jar.absolutePath)
        }
        emit(OpenaiChatEvent.ToolAccess(jar.name, "分析"))
        return executeJavaCommandTool(JDEPS_JAR_TOOL_NAME, jar.absolutePath, command)
    }

    private inline fun <reified T> decodeToolArgs(toolCall: OpenaiToolCall): T? {
        return runCatching {
            openaiJson.decodeFromString<T>(toolCall.function.arguments)
        }.getOrNull()
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.toolArgError(toolCall: OpenaiToolCall): String {
        val message = "AI工具参数解析失败"
        emit(OpenaiChatEvent.ToolFailed(message))
        return openaiJson.encodeToString(ToolResult(tool = toolCall.function.name, error = message))
    }

    private fun resolveVersionDir(versionDir: String?): Result<File> = runCatching {
        val root = File(versionDir?.trim().orEmpty()).canonicalFile
        require(root.isDirectory) { "整合包目录不存在: ${versionDir.orEmpty()}" }
        root
    }

    private fun resolveVersionChild(root: File, rawPath: String): Result<File> = runCatching {
        val candidate = File(rawPath.trim()).let { path ->
            if (path.isAbsolute) path else root.resolve(path.path)
        }.canonicalFile
        require(candidate.toPath().startsWith(root.toPath())) { "路径不在整合包目录内: $rawPath" }
        candidate
    }

    private fun resolveAllowedJar(rawPath: String): Result<File> = runCatching {
        val jar = File(rawPath.trim()).canonicalFile
        require(jar.isFile) { "Jar不存在: $rawPath" }
        require(jar.extension.equals("jar", ignoreCase = true)) { "只能分析.jar文件: $rawPath" }
        val allowedRoots = listOf(
            DIR.resolve("mc"),
            DIR.resolve("dl-mods"),
            DIR.resolve("pack-proc"),
            DL_MOD_DIR
        ).map { it.canonicalFile }
        require(allowedRoots.any { root -> jar.toPath().startsWith(root.toPath()) }) {
            "Jar不在RDI允许目录内: $rawPath"
        }
        jar
    }

    private fun resolveJavaTool(name: String): Result<File> = runCatching {
        val javaExe = File(javaExePath).canonicalFile
        val binDir = javaExe.parentFile ?: throw IllegalStateException("无法定位Java bin目录: $javaExe")
        val exeName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "$name.exe" else name
        val tool = binDir.resolve(exeName).canonicalFile
        require(tool.isFile) { "当前Java缺少$name，请使用完整JDK运行启动器: $tool" }
        tool
    }

    private suspend fun FlowCollector<OpenaiChatEvent>.executeJavaCommandTool(toolName: String, target: String, command: List<String>): String {
        return runCatching {
            val output = withContext(Dispatchers.IO) {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                val outputFuture = CompletableFuture.supplyAsync {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (!process.waitFor(JAVA_TOOL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    throw IllegalStateException("Java工具执行超时")
                }
                process.exitValue() to outputFuture.get(2, TimeUnit.SECONDS)
            }
            val text = output.second
            val truncated = text.length > MAX_JAVA_TOOL_OUTPUT_CHARS
            openaiJson.encodeToString(
                ToolResult(
                    tool = toolName,
                    target = target,
                    command = command.joinToString(" "),
                    exitCode = output.first,
                    output = text.take(MAX_JAVA_TOOL_OUTPUT_CHARS),
                    truncated = truncated
                )
            )
        }.getOrElse {
            emit(OpenaiChatEvent.ToolFailed(it.message ?: "Java工具执行失败"))
            openaiJson.encodeToString(
                ToolResult(
                    tool = toolName,
                    target = target,
                    command = command.joinToString(" "),
                    error = it.message ?: "Java工具执行失败"
                )
            )
        }
    }

    private suspend fun readLimitedResponse(channel: ByteReadChannel): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
            if (bytesRead == -1) break
            if (bytesRead == 0) continue
            if (output.size() + bytesRead > MAX_TOOL_RESPONSE_BYTES) {
                throw IllegalStateException("HTTP工具响应超过64MB")
            }
            output.write(buffer, 0, bytesRead)
        }
        return output.toByteArray()
    }

    private fun validateToolUrl(rawUrl: String): URI {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "AI工具只允许访问http/https URL" }
        val host = uri.host?.trimEnd('.')?.lowercase()
        require(!host.isNullOrBlank()) { "AI工具URL缺少host" }
        require(isAllowedToolHost(host)) { "AI工具不允许访问该URL" }
        return uri
    }

    private fun isAllowedToolHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        return allowedToolHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    private fun sanitizeToolTarget(rawTarget: String): String {
        val uri = runCatching { URI(rawTarget.trim()) }.getOrNull() ?: return rawTarget
        return sanitizeToolTarget(uri)
    }

    private fun sanitizeToolTarget(uri: URI): String {
        val host = uri.host?.trimEnd('.')?.lowercase()
        return if (!DEBUG && (host == "localhost" || host == "127.0.0.1" || host == "::1")) "R-MCP" else uri.toString()
    }

    private fun parseToolMethod(method: String): HttpMethod {
        return when (method.trim().uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            else -> throw IllegalArgumentException("AI工具只允许GET和POST请求")
        }
    }

    private fun String?.toContentType(): ContentType {
        val value = this?.trim().takeUnless { it.isNullOrBlank() } ?: return ContentType.Application.Json
        return runCatching { ContentType.parse(value) }.getOrDefault(ContentType.Application.Json)
    }

    private val httpRequestTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = HTTP_TOOL_NAME,
            description = "Send a GET or POST HTTP request to whitelisted Minecraft-related websites or localhost. DEBUG=$DEBUG. ${if (DEBUG) "Full localhost/R-MCP URLs may be shown in visible thinking or response text." else "Never expose full localhost/R-MCP URLs in visible thinking or response text; refer to them as R-MCP."}",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "method" to ChatToolProperty(type = "string", enum = listOf("GET", "POST")),
                    "url" to ChatToolProperty(type = "string"),
                    "body" to ChatToolProperty(type = "string"),
                    "contentType" to ChatToolProperty(type = "string")
                ),
                required = listOf("method", "url")
            )
        )
    )

    private val jarClassSearchTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = JAR_CLASS_SEARCH_TOOL_NAME,
            description = "List and search class names inside an allowed local Minecraft mod jar before using javap. The jar must be under the RDI mc, dl-mods, or pack-proc directories, including the current modpack versionDir when it is under RDI mc.",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "jarPath" to ChatToolProperty(type = "string"),
                    "query" to ChatToolProperty(type = "string"),
                    "limit" to ChatToolProperty(type = "integer")
                ),
                required = listOf("jarPath")
            )
        )
    )

    private val localFileListTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = LOCAL_FILE_LIST_TOOL_NAME,
            description = "List files under the current modpack versionDir only. Use this to discover configs, scripts, logs, and mod jars. The path may be relative to versionDir or an absolute path inside versionDir.",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "path" to ChatToolProperty(type = "string"),
                    "recursive" to ChatToolProperty(type = "boolean"),
                    "limit" to ChatToolProperty(type = "integer")
                ),
                required = listOf("path")
            )
        )
    )

    private val localTextReadTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = LOCAL_TEXT_READ_TOOL_NAME,
            description = "Read a UTF-8 text file under the current modpack versionDir only. Use for configs, scripts, logs, json, toml, yaml, properties, and other text files. Do not use for binary jars; use jar tools instead.",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "path" to ChatToolProperty(type = "string")
                ),
                required = listOf("path")
            )
        )
    )

    private val javapClassTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = JAVAP_CLASS_TOOL_NAME,
            description = "Run javap from the current Java bin directory against a class in an allowed local Minecraft mod jar to inspect methods, fields, constants, and bytecode.",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "jarPath" to ChatToolProperty(type = "string"),
                    "className" to ChatToolProperty(type = "string"),
                    "visibility" to ChatToolProperty(type = "string", enum = listOf("public", "private")),
                    "bytecode" to ChatToolProperty(type = "boolean"),
                    "constants" to ChatToolProperty(type = "boolean")
                ),
                required = listOf("jarPath", "className")
            )
        )
    )

    private val jdepsJarTool = ChatTool(
        type = "function",
        function = ChatToolFunction(
            name = JDEPS_JAR_TOOL_NAME,
            description = "Run jdeps from the current Java bin directory against an allowed local Minecraft mod jar to inspect Java package and class dependencies.",
            parameters = ChatToolParameters(
                properties = mapOf(
                    "jarPath" to ChatToolProperty(type = "string"),
                    "verbose" to ChatToolProperty(type = "boolean"),
                    "packageFilter" to ChatToolProperty(type = "string")
                ),
                required = listOf("jarPath")
            )
        )
    )

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<OpenaiChatMessage>,
        val stream: Boolean,
        @SerialName("stream_options")
        val streamOptions: ChatStreamOptions,
        val tools: List<ChatTool>
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
        val totalTokens: Int = 0
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

    @Serializable
    private data class ChatTool(
        val type: String,
        val function: ChatToolFunction
    )

    @Serializable
    private data class ChatToolFunction(
        val name: String,
        val description: String,
        val parameters: ChatToolParameters
    )

    @Serializable
    private data class ChatToolParameters(
        val type: String = "object",
        val properties: Map<String, ChatToolProperty>,
        val required: List<String>
    )

    @Serializable
    private data class ChatToolProperty(
        val type: String,
        val description: String? = null,
        val enum: List<String>? = null
    )

    @Serializable
    private data class HttpRequestToolArgs(
        val method: String,
        val url: String,
        val body: String? = null,
        val contentType: String? = null
    )

    @Serializable
    private data class LocalFileListToolArgs(
        val path: String = "",
        val recursive: Boolean = false,
        val limit: Int = 200
    )

    @Serializable
    private data class LocalTextReadToolArgs(
        val path: String
    )

    @Serializable
    private data class JarClassSearchToolArgs(
        val jarPath: String,
        val query: String? = null,
        val limit: Int = 200
    )

    @Serializable
    private data class JavapClassToolArgs(
        val jarPath: String,
        val className: String,
        val visibility: String = "public",
        val bytecode: Boolean = true,
        val constants: Boolean = false
    )

    @Serializable
    private data class JdepsJarToolArgs(
        val jarPath: String,
        val verbose: Boolean = false,
        val packageFilter: String? = null
    )

    @Serializable
    private data class ToolResult(
        val tool: String,
        val target: String? = null,
        val command: String? = null,
        val exitCode: Int? = null,
        val status: Int? = null,
        val contentType: String? = null,
        val output: String? = null,
        val outputBytes: Int? = null,
        val resultCount: Int? = null,
        val truncated: Boolean = false,
        val error: String? = null
    )
}
