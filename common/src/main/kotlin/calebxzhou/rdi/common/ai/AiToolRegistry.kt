package calebxzhou.rdi.common.ai

object AiToolRegistry {
    private val tools = listOf(
        HttpAiTool,
        McmodItemLookupAiTool,
        LocalFileListAiTool,
        LocalTextReadAiTool,
        LocalTextSearchAiTool,
        JarClassSearchAiTool,
        JavapClassAiTool,
        JdepsJarAiTool
    )

    fun availableDefinitions(context: AiToolContext): List<AiToolDefinition> =
        tools.filter { it.isAvailable(context) }.map { it.definition(context) }

    suspend fun execute(name: String, argumentsJson: String, context: AiToolContext): AiToolExecution {
        val tool = tools.firstOrNull { it.name == name && it.isAvailable(context) }
            ?: return AiToolExecution(
                result = AiToolResult(
                    tool = name,
                    error = "不支持的AI工具: $name"
                )
            )
        return tool.execute(argumentsJson, context)
    }
}
