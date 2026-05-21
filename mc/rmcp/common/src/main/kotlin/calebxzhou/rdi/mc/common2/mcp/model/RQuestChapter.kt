package calebxzhou.rdi.mc.common2.mcp.model

@JvmRecord
data class RQuestChapter(
    val id: String,
    val title: String,
    val groupId: String,
    val groupTitle: String,
    val visible: Boolean,
    val started: Boolean,
    val completed: Boolean,
    val progress: Int,
    val questCount: Int,
    val quests: List<Quest>
) {
    @JvmRecord
    data class Quest(
        val id: String,
        val title: String,
        val subtitle: String,
        val x: Double,
        val y: Double,
        val visible: Boolean,
        val started: Boolean,
        val completed: Boolean,
        val startable: Boolean,
        val progress: Int,
        val taskCount: Int,
        val rewardCount: Int,
        val dependencyCount: Int,
        val dependencyIds: List<String>
    )
}
