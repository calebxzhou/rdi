package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RQuestChapterList(
    val total: Int = 0,
    val visible: Int = 0,
    val chapters: List<Chapter> = listOf()
) {
    @JvmRecord
    data class Chapter(
        val id: String,
        val title: String,
        val groupId: String,
        val groupTitle: String,
        val visible: Boolean,
        val started: Boolean,
        val completed: Boolean,
        val progress: Int,
        val questCount: Int,
        val visibleQuestCount: Int,
        val startableQuestCount: Int,
        val completedQuestCount: Int
    )
}
