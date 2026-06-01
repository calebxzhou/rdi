package calebxzhou.rdi.common.ai

import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

object LocalFileListAiTool : AiTool {
    override val name = "local_file_list"

    override fun isAvailable(context: AiToolContext): Boolean = context.rdiDir != null

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "List files under the RDI directory. Use this to discover configs, scripts, logs, modpacks, downloads, and mod jars. The path may be relative to the RDI directory or an absolute path inside it.",
        parameters = AiToolParameters(
            properties = mapOf(
                "path" to AiToolProperty(type = "string"),
                "recursive" to AiToolProperty(type = "boolean"),
                "limit" to AiToolProperty(type = "integer")
            ),
            required = listOf("path")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<LocalFileListToolArgs>(argumentsJson) ?: return argError(name)
        val root = context.rdiDir ?: return missingRdiDir(name)
        val access = AiToolAccess(args.path.ifBlank { root.name }, "读取")
        val result = TextTool.list(root, args.path, args.recursive, args.limit).fold(
            onSuccess = {
                AiToolResult(
                    tool = name,
                    target = it.target,
                    output = it.output,
                    resultCount = it.resultCount,
                    truncated = it.truncated
                )
            },
            onFailure = {
                AiToolResult(tool = name, target = args.path, error = it.message ?: "列目录失败")
            }
        )
        return AiToolExecution(access, result)
    }
}

object LocalTextReadAiTool : AiTool {
    override val name = "local_text_read"

    override fun isAvailable(context: AiToolContext): Boolean = context.rdiDir != null

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "Read a UTF-8 text file line range under the RDI directory. Use search first for large files. Do not use for binary jars; use jar tools instead.",
        parameters = AiToolParameters(
            properties = mapOf(
                "path" to AiToolProperty(type = "string"),
                "startLine" to AiToolProperty(type = "integer", description = "1-based start line, default 1"),
                "maxLines" to AiToolProperty(type = "integer", description = "Maximum lines to return, default 200, max 500")
            ),
            required = listOf("path")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<LocalTextReadToolArgs>(argumentsJson) ?: return argError(name)
        val root = context.rdiDir ?: return missingRdiDir(name)
        val access = AiToolAccess(args.path, "读取")
        val result = TextTool.read(root, args.path, args.startLine, args.maxLines).fold(
            onSuccess = {
                AiToolResult(
                    tool = name,
                    target = it.target,
                    output = it.output,
                    outputBytes = it.outputBytes.toInt(),
                    resultCount = it.resultCount,
                    truncated = it.truncated
                )
            },
            onFailure = {
                AiToolResult(tool = name, target = args.path, error = it.message ?: "读取文本失败")
            }
        )
        return AiToolExecution(access, result)
    }
}

object LocalTextSearchAiTool : AiTool {
    override val name = "local_text_search"

    override fun isAvailable(context: AiToolContext): Boolean = context.rdiDir != null

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "Search text files under the RDI directory using Kotlin regex, like a safe rg subset. Use this before reading files. It skips binary files and files over 1MB.",
        parameters = AiToolParameters(
            properties = mapOf(
                "path" to AiToolProperty(type = "string", description = "Relative path or absolute path inside the RDI directory. Use . for the RDI directory."),
                "pattern" to AiToolProperty(type = "string", description = "Kotlin regex pattern"),
                "glob" to AiToolProperty(type = "string", description = "Optional glob matched against relative path, e.g. kubejs/**/*.js or *.toml"),
                "maxMatches" to AiToolProperty(type = "integer", description = "Default 50, max 200"),
                "contextLines" to AiToolProperty(type = "integer", description = "Context lines before and after each match, max 5")
            ),
            required = listOf("path", "pattern")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<LocalTextSearchToolArgs>(argumentsJson) ?: return argError(name)
        val root = context.rdiDir ?: return missingRdiDir(name)
        val access = AiToolAccess(args.path.ifBlank { root.name }, "搜索")
        val result = TextTool.search(root, args.path, args.pattern, args.glob, args.maxMatches, args.contextLines).fold(
            onSuccess = {
                AiToolResult(
                    tool = name,
                    target = it.target,
                    output = it.output,
                    resultCount = it.resultCount,
                    truncated = it.truncated
                )
            },
            onFailure = {
                AiToolResult(tool = name, target = args.path, error = it.message ?: "搜索文本失败")
            }
        )
        return AiToolExecution(access, result)
    }
}

internal inline fun <reified T> decodeArgs(argumentsJson: String): T? =
    runCatching { serdesJson.decodeFromString<T>(argumentsJson) }.getOrNull()

internal fun argError(toolName: String): AiToolExecution =
    AiToolExecution(result = AiToolResult(tool = toolName, error = "AI工具参数解析失败"))

private fun missingRdiDir(toolName: String): AiToolExecution =
    AiToolExecution(result = AiToolResult(tool = toolName, error = "未提供RDI目录"))

@Serializable
private data class LocalFileListToolArgs(
    val path: String = "",
    val recursive: Boolean = false,
    val limit: Int = 200
)

@Serializable
private data class LocalTextReadToolArgs(
    val path: String,
    val startLine: Int = 1,
    val maxLines: Int = 200
)

@Serializable
private data class LocalTextSearchToolArgs(
    val path: String = "",
    val pattern: String,
    val glob: String? = null,
    val maxMatches: Int = 50,
    val contextLines: Int = 2
)
