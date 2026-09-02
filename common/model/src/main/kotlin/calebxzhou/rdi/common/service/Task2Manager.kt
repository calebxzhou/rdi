package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2CancelledException
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.Task2Snapshot
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.rdi.common.model.newTask2Id
import calebxzhou.rdi.common.model.task2ChildPathSegment
import calebxzhou.rdi.common.model.task2PathKey
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class Task2Controller(
    val runId: String,
    val task: Task2,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private val _state = MutableStateFlow(
        Task2Snapshot(
            taskId = task.id,
            taskTitle = task.title
        )
    )
    private var job: Job? = null

    val state: StateFlow<Task2Snapshot> = _state.asStateFlow()

    fun start() {
        synchronized(lock) {
            if (job != null || _state.value.status != Task2Status.QUEUED) return
            _state.value = _state.value.copy(
                status = Task2Status.RUNNING,
                currentMessage = "准备中",
                currentFraction = null,
                currentProgress = Task2Progress("准备中"),
                errorMessage = null
            )
            val startedJob = scope.launch {
                execute()
            }
            job = startedJob
            startedJob.invokeOnCompletion {
                synchronized(lock) {
                    if (job === startedJob) job = null
                }
            }
        }
    }

    fun terminate(message: String = "任务已取消") {
        requestTermination(message)
    }

    suspend fun terminateAndJoin(message: String = "任务已取消") {
        requestTermination(message)?.join()
    }

    private fun requestTermination(message: String): Job? = synchronized(lock) {
        val currentJob = job
        currentJob?.cancel(CancellationException(message))
        _state.update {
            it.copy(
                currentMessage = message,
                currentFraction = it.currentFraction,
                currentProgress = it.currentProgress?.copy(message = message)
                    ?: Task2Progress(message, it.currentFraction),
                errorMessage = message,
                status = Task2Status.CANCELLED
            )
        }
        currentJob
    }

    private suspend fun execute() {
        runCatching {
            runTask2WithProgress(
                task = task,
                taskKeyPath = listOf(task.id),
                onProgress = { progress ->
                    _state.update {
                        it.copy(
                            currentMessage = progress.message,
                            currentFraction = progress.fraction,
                            currentProgress = progress
                        )
                    }
                },
                onTaskProgress = { path, progress ->
                    val key = task2PathKey(path)
                    _state.update {
                        val next = HashMap(it.progressByPath)
                        next[key] = progress
                        it.copy(progressByPath = next)
                    }
                },
                onTaskDone = { path ->
                    val key = task2PathKey(path)
                    _state.update {
                        val nextProgress = HashMap(it.progressByPath)
                        val progress = nextProgress[key] ?: Task2Progress("完成", 1f)
                        nextProgress[key] = progress.copy(
                            message = "完成",
                            fraction = 1f,
                            bytesPerSecond = null
                        )
                        val nextDone = HashSet(it.donePaths)
                        nextDone += key
                        it.copy(progressByPath = nextProgress, donePaths = nextDone)
                    }
                },
                isCancelled = { state.value.status == Task2Status.CANCELLED }
            )
        }.onSuccess {
            _state.update {
                if (it.status == Task2Status.CANCELLED) it
                else {
                    val lastProgress = it.currentProgress
                    val completionMessage = lastProgress
                        ?.takeIf { progress -> progress.fraction != null && progress.fraction >= 1f }
                        ?.message
                        ?: "完成"
                    it.copy(
                        currentMessage = completionMessage,
                        currentFraction = 1f,
                        currentProgress = (lastProgress ?: Task2Progress("完成", 1f)).copy(
                            message = completionMessage,
                            fraction = 1f,
                            bytesPerSecond = null
                        ),
                        errorMessage = null,
                        status = Task2Status.DONE
                    )
                }
            }
        }.onFailure { error ->
            if (error is CancellationException || error is Task2CancelledException) return@onFailure
            _state.update {
                it.copy(
                    currentMessage = it.currentMessage.ifBlank { "任务失败" },
                    currentProgress = (it.currentProgress ?: Task2Progress(it.currentMessage)).copy(
                        message = it.currentMessage.ifBlank { "任务失败" },
                        bytesPerSecond = null
                    ),
                    errorMessage = error.message ?: "任务失败",
                    status = Task2Status.FAILED
                )
            }
        }
    }
}

suspend fun Task2.runInline(ctx: Task2Context) {
    runTask2WithProgress(
        task = this,
        taskKeyPath = listOf(id),
        onProgress = { progress -> ctx.emit(progress) },
        onTaskProgress = { _, _ -> },
        onTaskDone = { },
        isCancelled = ctx.isCancelled
    )
}

open class Task2Manager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onTaskSubmitted: ((Task2Entry) -> Unit)? = null,
    private val onTaskStarted: ((Task2Entry) -> Unit)? = null,
    private val onTaskUpdated: ((Task2Entry) -> Unit)? = null,
    private val onTaskRemoved: ((String) -> Unit)? = null
) {
    private val lock = Any()
    private val controllers = linkedMapOf<String, Task2Controller>()
    private val watcherJobs = linkedMapOf<String, Job>()
    private val _entries = MutableStateFlow<List<Task2Entry>>(emptyList())

    val entries: StateFlow<List<Task2Entry>> = _entries.asStateFlow()

    fun submit(
        task: Task2,
        dedupeKey: String? = null,
        autoStart: Boolean = true
    ): String {
        synchronized(lock) {
            if (!dedupeKey.isNullOrBlank()) {
                val existing = _entries.value.firstOrNull {
                    it.dedupeKey == dedupeKey && !it.status.isTerminal
                }
                if (existing != null) {
                    if (autoStart && existing.status == Task2Status.QUEUED) {
                        controllers[existing.runId]?.start()
                    }
                    return existing.runId
                }
            }
        }
        val runId = newTask2Id()
        val controller = Task2Controller(runId, task, scope)
        val initialEntry = Task2Entry(
            runId = runId,
            task = task,
            dedupeKey = dedupeKey,
            snapshot = controller.state.value
        )
        synchronized(lock) {
            controllers[runId] = controller
            _entries.value = listOf(initialEntry) + _entries.value
            watcherJobs[runId] = scope.launch {
                controller.state.collectLatest { snapshot ->
                    val updatedEntry = updateEntrySnapshot(runId, snapshot)
                    if (updatedEntry != null) {
                        onTaskUpdated?.invoke(updatedEntry)
                    }
                }
            }
        }
        onTaskSubmitted?.invoke(initialEntry)
        if (autoStart) {
            controller.start()
            updateEntrySnapshot(runId, controller.state.value)?.let { onTaskStarted?.invoke(it) }
        }
        return runId
    }

    fun start(runId: String) {
        val controller = controller(runId) ?: return
        controller.start()
        updateEntrySnapshot(runId, controller.state.value)?.let { onTaskStarted?.invoke(it) }
    }

    fun cancel(runId: String, message: String = "任务已取消") {
        val controller = controller(runId) ?: return
        controller.terminate(message)
        updateEntrySnapshot(runId, controller.state.value)
    }

    suspend fun cancelAndJoin(runId: String, message: String = "任务已取消") {
        val controller = controller(runId) ?: return
        controller.terminateAndJoin(message)
        updateEntrySnapshot(runId, controller.state.value)
    }

    fun remove(runId: String) {
        synchronized(lock) {
            watcherJobs.remove(runId)?.cancel()
            controllers.remove(runId)?.terminate("任务已移除")
            _entries.value = _entries.value.filterNot { it.runId == runId }
        }
        onTaskRemoved?.invoke(runId)
    }

    fun clearFinished() {
        entries.value
            .filter { it.status.isTerminal }
            .map { it.runId }
            .forEach(::remove)
    }

    fun entry(runId: String): Task2Entry? = entries.value.firstOrNull { it.runId == runId }

    fun controller(runId: String): Task2Controller? = synchronized(lock) { controllers[runId] }

    private fun updateEntrySnapshot(runId: String, snapshot: Task2Snapshot): Task2Entry? {
        var updatedEntry: Task2Entry? = null
        _entries.update { current ->
            current.map { entry ->
                if (entry.runId != runId) return@map entry
                val next = entry.copy(
                    snapshot = snapshot,
                    startedAt = entry.startedAt ?: if (snapshot.status == Task2Status.RUNNING) System.currentTimeMillis() else null,
                    finishedAt = when {
                        entry.finishedAt != null -> entry.finishedAt
                        snapshot.status.isTerminal -> System.currentTimeMillis()
                        else -> null
                    }
                )
                updatedEntry = next
                next
            }
        }
        return updatedEntry
    }
}

private suspend fun runTask2WithProgress(
    task: Task2,
    taskKeyPath: List<String>,
    onProgress: (Task2Progress) -> Unit,
    onTaskProgress: (List<String>, Task2Progress) -> Unit,
    onTaskDone: (List<String>) -> Unit,
    isCancelled: () -> Boolean
) {
    when (task) {
        is Task2.Leaf -> {
            val throttled = throttleTask2Progress { progress ->
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            val ctx = Task2Context(
                emitProgress = throttled,
                isCancelled = isCancelled
            )
            ctx.ensureActive()
            task.action(ctx)
            onTaskDone(taskKeyPath)
        }

        is Task2.Group -> {
            val start = Task2Progress("开始子任务${task.children.size}个", 0f)
            onProgress(start)
            onTaskProgress(taskKeyPath, start)
            val total = task.children.size.coerceAtLeast(1)
            val completed = AtomicInteger(0)
            val parallelism = task.parallelism.coerceAtLeast(1)
            if (parallelism == 1) {
                task.children.forEachIndexed { index, child ->
                    runTask2WithProgress(
                        task = child,
                        taskKeyPath = taskKeyPath + task2ChildPathSegment(child, index),
                        onProgress = onProgress,
                        onTaskProgress = onTaskProgress,
                        onTaskDone = onTaskDone,
                        isCancelled = isCancelled
                    )
                    val done = completed.incrementAndGet()
                    val progress = Task2Progress("已完成$done/$total", done.toFloat() / total)
                    onProgress(progress)
                    onTaskProgress(taskKeyPath, progress)
                }
            } else {
                val semaphore = Semaphore(parallelism)
                coroutineScope {
                    task.children.mapIndexed { index, child ->
                        async {
                            semaphore.withPermit {
                                runTask2WithProgress(
                                    task = child,
                                    taskKeyPath = taskKeyPath + task2ChildPathSegment(child, index),
                                    onProgress = onProgress,
                                    onTaskProgress = onTaskProgress,
                                    onTaskDone = onTaskDone,
                                    isCancelled = isCancelled
                                )
                            }
                            val done = completed.incrementAndGet()
                            val progress = Task2Progress("已完成$done/$total", done.toFloat() / total)
                            onProgress(progress)
                            onTaskProgress(taskKeyPath, progress)
                        }
                    }.awaitAll()
                }
            }
            onTaskDone(taskKeyPath)
        }

        is Task2.Sequence -> {
            val start = Task2Progress("开始子任务${task.children.size}个", 0f)
            onProgress(start)
            onTaskProgress(taskKeyPath, start)
            val total = task.children.size.coerceAtLeast(1)
            task.children.forEachIndexed { index, child ->
                runTask2WithProgress(
                    task = child,
                    taskKeyPath = taskKeyPath + task2ChildPathSegment(child, index),
                    onProgress = onProgress,
                    onTaskProgress = onTaskProgress,
                    onTaskDone = onTaskDone,
                    isCancelled = isCancelled
                )
                val done = index + 1
                val progress = Task2Progress("已完成$done/$total", done.toFloat() / total)
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            onTaskDone(taskKeyPath)
        }
    }
}

private fun throttleTask2Progress(
    minIntervalMs: Long = 80L,
    onProgress: (Task2Progress) -> Unit
): (Task2Progress) -> Unit {
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
