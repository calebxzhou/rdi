package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import calebxzhou.rdi.common.model.execute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class TaskExecutionSnapshot(
    val runId: Long = 0L,
    val taskName: String = "",
    val currentMessage: String = "准备中",
    val currentFraction: Float? = null,
    val errorMessage: String? = null,
    val running: Boolean = false,
    val done: Boolean = false,
    val progressByPath: Map<String, TaskProgress> = emptyMap(),
    val donePaths: Set<String> = emptySet()
)

object TaskExecutionRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val runCounter = AtomicLong(0L)
    private val _state = MutableStateFlow(TaskExecutionSnapshot())

    private var activeTask: Task? = null
    private var activeJob: Job? = null

    val state: StateFlow<TaskExecutionSnapshot> = _state.asStateFlow()

    fun isCurrentTask(task: Task): Boolean = synchronized(lock) {
        activeTask === task
    }

    fun start(task: Task) {
        val runId: Long
        synchronized(lock) {
            if (activeTask === task) return
            activeJob?.cancel()
            activeTask = task
            runId = runCounter.incrementAndGet()
            _state.value = TaskExecutionSnapshot(
                runId = runId,
                taskName = task.name,
                currentMessage = "准备中",
                currentFraction = null,
                running = true,
                done = false
            )
            activeJob = scope.launch {
                executeTask(runId, task)
            }
        }
        ensurePlatformTaskExecutionForegroundService()
    }

    private suspend fun executeTask(runId: Long, task: Task) {
        runCatching {
            runTaskWithProgress(
                task = task,
                onProgress = { progress ->
                    updateSnapshot(runId) {
                        it.copy(
                            currentMessage = progress.message,
                            currentFraction = progress.fraction
                        )
                    }
                },
                taskKeyPath = listOf(task.name),
                onTaskProgress = { path, progress ->
                    val key = taskPathKey(path)
                    updateSnapshot(runId) {
                        val nextProgress = HashMap(it.progressByPath)
                        nextProgress[key] = progress
                        it.copy(progressByPath = nextProgress)
                    }
                },
                onTaskDone = { path ->
                    val key = taskPathKey(path)
                    updateSnapshot(runId) {
                        val nextProgress = HashMap(it.progressByPath)
                        nextProgress[key] = TaskProgress("完成", 1f)
                        val nextDone = HashSet(it.donePaths)
                        nextDone += key
                        it.copy(
                            progressByPath = nextProgress,
                            donePaths = nextDone
                        )
                    }
                }
            )
        }.onSuccess {
            updateSnapshot(runId) {
                it.copy(
                    currentMessage = "完成",
                    currentFraction = 1f,
                    running = false,
                    done = true,
                    errorMessage = null
                )
            }
        }.onFailure { error ->
            if (error is CancellationException) return
            error.printStackTrace()
            updateSnapshot(runId) {
                it.copy(
                    currentMessage = it.currentMessage.ifBlank { "任务失败" },
                    errorMessage = error.message ?: "任务失败",
                    running = false,
                    done = false
                )
            }
        }
    }

    private fun updateSnapshot(
        runId: Long,
        transform: (TaskExecutionSnapshot) -> TaskExecutionSnapshot
    ) {
        _state.update { snapshot ->
            if (snapshot.runId != runId) snapshot else transform(snapshot)
        }
    }
}

private suspend fun runTaskWithProgress(
    task: Task,
    onProgress: (TaskProgress) -> Unit,
    taskKeyPath: List<String> = emptyList(),
    onTaskProgress: (List<String>, TaskProgress) -> Unit,
    onTaskDone: (List<String>) -> Unit
) {
    when (task) {
        is Task.Leaf -> {
            val throttled = throttleProgress { progress ->
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            val ctx = TaskContext(emitProgress = { progress ->
                throttled(progress)
            })
            withContext(Dispatchers.IO) {
                task.execute(ctx)
            }
            onTaskDone(taskKeyPath)
        }

        is Task.Group -> {
            val startProgress = TaskProgress("开始子任务 ${task.subTasks.size} 个", 0f)
            onProgress(startProgress)
            onTaskProgress(taskKeyPath, startProgress)
            val total = task.subTasks.size.coerceAtLeast(1)
            val completed = AtomicInteger(0)
            val parallelism = task.parallelism.coerceAtLeast(1)
            if (parallelism == 1) {
                task.subTasks.forEachIndexed { index, subTask ->
                    withContext(Dispatchers.IO) {
                        runTaskWithProgress(
                            task = subTask,
                            onProgress = onProgress,
                            taskKeyPath = taskKeyPath + taskChildKeySegment(subTask.name, index),
                            onTaskProgress = onTaskProgress,
                            onTaskDone = onTaskDone
                        )
                    }
                    val done = completed.incrementAndGet()
                    val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                    onProgress(progress)
                    onTaskProgress(taskKeyPath, progress)
                }
            } else {
                val semaphore = Semaphore(parallelism)
                coroutineScope {
                    task.subTasks.mapIndexed { index, subTask ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                runTaskWithProgress(
                                    task = subTask,
                                    onProgress = onProgress,
                                    taskKeyPath = taskKeyPath + taskChildKeySegment(subTask.name, index),
                                    onTaskProgress = onTaskProgress,
                                    onTaskDone = onTaskDone
                                )
                            }
                            val done = completed.incrementAndGet()
                            val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                            onProgress(progress)
                            onTaskProgress(taskKeyPath, progress)
                        }
                    }.awaitAll()
                }
            }
            onTaskDone(taskKeyPath)
        }

        is Task.Sequence -> {
            val startProgress = TaskProgress("开始子任务 ${task.subTasks.size} 个", 0f)
            onProgress(startProgress)
            onTaskProgress(taskKeyPath, startProgress)
            val total = task.subTasks.size.coerceAtLeast(1)
            val completed = AtomicInteger(0)
            task.subTasks.forEachIndexed { index, subTask ->
                withContext(Dispatchers.IO) {
                    runTaskWithProgress(
                        task = subTask,
                        onProgress = onProgress,
                        taskKeyPath = taskKeyPath + taskChildKeySegment(subTask.name, index),
                        onTaskProgress = onTaskProgress,
                        onTaskDone = onTaskDone
                    )
                }
                val done = completed.incrementAndGet()
                val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            onTaskDone(taskKeyPath)
        }
    }
}

private fun throttleProgress(
    minIntervalMs: Long = 80L,
    onProgress: (TaskProgress) -> Unit
): (TaskProgress) -> Unit {
    val lastEmit = AtomicLong(0L)
    val lastMessage = AtomicReference<String?>(null)
    return { progress ->
        val now = System.currentTimeMillis()
        val prev = lastEmit.get()
        val messageChanged = lastMessage.getAndSet(progress.message) != progress.message
        val isTerminal = progress.fraction?.let { it >= 1f || it <= 0f } == true
        val shouldEmit = messageChanged || isTerminal || (now - prev) >= minIntervalMs
        if (shouldEmit && lastEmit.compareAndSet(prev, now)) {
            onProgress(progress)
        }
    }
}

internal fun taskPathKey(path: List<String>): String = path.joinToString(" / ")

internal fun taskChildKeySegment(name: String, index: Int): String = "$name#$index"

internal expect fun ensurePlatformTaskExecutionForegroundService()
