package calebxzhou.rdi.common.model

import java.util.UUID

data class Task2Progress(
    val message: String,
    val fraction: Float? = null
)

class Task2Context(
    val isCancelled: () -> Boolean = { false },
    val emitProgress: (Task2Progress) -> Unit
) {
    fun emit(progress: Task2Progress) = emitProgress(progress)

    fun emit(progress: LoadProgress) = emitProgress(progress.toTask2Progress())

    fun ensureActive() {
        if (isCancelled()) throw Task2CancelledException()
    }
}

enum class Task2Status {
    QUEUED, RUNNING, DONE, FAILED, CANCELLED;

    val isTerminal: Boolean
        get() = this == DONE || this == FAILED || this == CANCELLED
}

data class Task2Snapshot(
    val taskId: String = "",
    val taskTitle: String = "",
    val currentMessage: String = "准备中",
    val currentFraction: Float? = null,
    val errorMessage: String? = null,
    val status: Task2Status = Task2Status.QUEUED,
    val progressByPath: Map<String, Task2Progress> = emptyMap(),
    val donePaths: Set<String> = emptySet()
) {
    val running: Boolean
        get() = status == Task2Status.RUNNING
    val done: Boolean
        get() = status == Task2Status.DONE
}

data class Task2Entry(
    val runId: String,
    val task: Task2,
    val dedupeKey: String? = null,
    val snapshot: Task2Snapshot,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) {
    val status: Task2Status
        get() = snapshot.status
}

sealed interface Task2 {
    val id: String
    val title: String

    data class Leaf(
        override val title: String,
        override val id: String = newTask2Id(),
        val action: suspend (Task2Context) -> Unit
    ) : Task2

    data class Group(
        override val title: String,
        val children: List<Task2> = emptyList(),
        val parallelism: Int = Runtime.getRuntime().availableProcessors(),
        val defaultExpanded: Boolean = children.size <= 10,
        override val id: String = newTask2Id()
    ) : Task2

    data class Sequence(
        override val title: String,
        val children: List<Task2> = emptyList(),
        val defaultExpanded: Boolean = children.size <= 10,
        override val id: String = newTask2Id()
    ) : Task2
}

class Task2CancelledException(message: String = "任务已取消") : RuntimeException(message)

fun newTask2Id(): String = UUID.randomUUID().toString()

fun task2PathKey(path: List<String>): String = path.joinToString(" / ")

fun task2ChildPathSegment(task: Task2, index: Int): String = "${task.id}#$index"

fun LoadProgress.toTask2Progress(): Task2Progress = when (this) {
    is LoadProgress.Phase -> Task2Progress(text, null)
    is LoadProgress.Percent -> Task2Progress(text, fraction)
    is LoadProgress.Warn -> Task2Progress(text, null)
}
