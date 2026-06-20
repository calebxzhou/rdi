package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.QuestOfChapterQ
import kotlinx.serialization.json.JsonObject

object QuestTool {
    val all: List<StandardMcpTool> = listOf(
        QuestChapterListTool,
        QuestOfChapterTool,
    )
}

private object QuestChapterListTool : StandardMcpTool {
    override val description = """
        List FTB Quests chapters visible to the local player/team data.
        Response is one chapter per line with chapter id, title, group, completion state, total quest count, and completed quest count.
        Use a chapter id from this response to query quests inside that chapter.
    """.trimIndent()

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return game.questChapterList().map { text -> StandardMcpToolResult.text(text) }
    }
}

private object QuestOfChapterTool : TypedMcpTool<QuestOfChapterQ>(
    QuestOfChapterQ.serializer(),
    QuestOfChapterQ::class,
) {
    override val description = """
        List quests inside one FTB Quests chapter.
        Response is one quest per line with quest id, title, completion state, optional/repeatable flags, task count, reward count, and dependency ids.
        This is a quest list/index, not full quest detail.
    """.trimIndent()

    override fun callTyped(req: QuestOfChapterQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val id = req.id.trim().takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("empty quest chapter id")
            StandardMcpToolResult.text(game.questsOfChapter(id).getOrThrow())
        }
    }
}
