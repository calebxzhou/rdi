package calebxzhou.rdi.mc.common2.mcp.model

/**
 * calebxzhou @ 2026-05-10 11:23
 */
data class QuestChapter(
    val id: String,
    val title: String,
    val groupId: String,
    val groupTitle: String,
    val completed: Boolean,
    val questCount: Int,
    val completedQuestCount: Int
)
data class Quest(
    val id: String,
    val title: String,
    val subtitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val groupId: String,
    val groupTitle: String,
    val state: State,
    val rules: Rules,
    val description: List<String>,
    val dependencies: List<Dependency>,
    val tasks: List<Task>,
    val rewards: List<Reward>
) {

    
    data class State(
        val completed: Boolean,
    )

    
    data class Rules(
        val dependencyRequirement: String,
        val optional: Boolean,
        val repeatable: Boolean,
    )

    
    data class Dependency(
        val id: String,
        val type: String,
        val title: String,
        val started: Boolean,
        val completed: Boolean
    )

    
    data class Task(
        val id: String,
        val type: String,
        val title: String,
        val completed: Boolean,
    )

    
    data class Reward(
        val id: String,
        val type: String,
        val title: String,
        val claimed: Boolean
    )
}
