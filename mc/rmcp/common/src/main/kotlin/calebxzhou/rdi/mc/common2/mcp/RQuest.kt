package calebxzhou.rdi.mc.common2.mcp

/**
 * calebxzhou @ 2026-05-10 11:23
 */
@JvmRecord
data class RQuest(
    val id: String,
    val title: String,
    val subtitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val groupId: String,
    val groupTitle: String,
    val position: Position,
    val state: State,
    val rules: Rules,
    val description: List<String>,
    val dependencies: List<Dependency>,
    val tasks: List<Task>,
    val rewards: List<Reward>
) {
    @JvmRecord
    data class Position(val x: Double, val y: Double)

    @JvmRecord
    data class State(
        val visible: Boolean,
        val started: Boolean,
        val completed: Boolean,
        val startable: Boolean,
        val progress: Int,
        val cannotStartReason: String?
    )

    @JvmRecord
    data class Rules(
        val progressionMode: String,
        val dependencyRequirement: String,
        val minRequiredDependencies: Int,
        val optional: Boolean,
        val repeatable: Boolean,
        val hideDetailsUntilStartable: Boolean,
        val requireSequentialTasks: Boolean
    )

    @JvmRecord
    data class Dependency(
        val id: String,
        val type: String,
        val title: String,
        val visible: Boolean,
        val started: Boolean,
        val completed: Boolean
    )

    @JvmRecord
    data class Task(
        val id: String,
        val type: String,
        val title: String,
        val completed: Boolean,
        val progress: Long,
        val maxProgress: Long
    )

    @JvmRecord
    data class Reward(
        val id: String,
        val type: String,
        val title: String,
        val claimed: Boolean
    )
}
