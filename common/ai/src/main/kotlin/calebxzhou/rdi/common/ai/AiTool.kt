package calebxzhou.rdi.common.ai

import kotlinx.serialization.Serializable
import java.io.File

data class AiToolContext(
    val rdiDir: File?,
    val versionDir: File?,
    val debug: Boolean
)

data class AiToolAccess(
    val target: String,
    val action: String = "访问"
)

data class AiToolExecution(
    val access: AiToolAccess? = null,
    val result: AiToolResult,
    val detail: AiToolDetail? = null
)

data class AiToolDetail(
    val method: String = "",
    val path: String = "",
    val payload: String = "",
    val response: String = "",
    val status: Int? = null
)

interface AiTool {
    val name: String
    fun isAvailable(context: AiToolContext): Boolean = true
    fun definition(context: AiToolContext): AiToolDefinition
    suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution
}

@Serializable
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: AiToolParameters
)

@Serializable
data class AiToolParameters(
    val type: String = "object",
    val properties: Map<String, AiToolProperty>,
    val required: List<String>
)

@Serializable
data class AiToolProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

@Serializable
data class AiToolResult(
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
