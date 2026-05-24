package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import io.fusionauth.http.HTTPMethod

object QuestChapterListHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        List FTB Quests chapters visible to the local player/team data.
        No query params.
        Response is one chapter per line with chapter id, title, group, completion state, total quest count, and completed quest count.
        Use a chapter id from this response to query quests inside that chapter.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.questChapterList()
    }
}

object QuestOfChapterHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        List quests inside one FTB Quests chapter.
        Query params:
        id: required chapter id from the chapter list response.
        Response is one quest per line with quest id, title, completion state, optional/repeatable flags, task count, reward count, and dependency ids.
        This is a quest list/index, not full quest detail.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val id = ctx.param("id").trim()
        if (id.isEmpty()) {
            throw McpBadRequestError("empty quest chapter id")
        }
        return ctx.game.questsOfChapter(id)
    }
}
