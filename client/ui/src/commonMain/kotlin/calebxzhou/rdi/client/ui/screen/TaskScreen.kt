package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.snapshots.Snapshot
import calebxzhou.mykotutils.std.toFixed
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.hM
import calebxzhou.rdi.client.ui.platformKeepScreenOn
import calebxzhou.rdi.client.ui.wM
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * calebxzhou @ 2026-01-16 11:12
 */
@Composable
fun TaskScreen(
    task: Task,
    autoClose: Boolean = false,
    onBack: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    platformKeepScreenOn(true)
    var currentName by remember { mutableStateOf(task.name) }
    var currentMessage by remember { mutableStateOf("准备中") }
    var currentFraction by remember { mutableStateOf<Float?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var subTaskWindow by remember { mutableStateOf(0) }
    val subTasks = remember { mutableStateListOf<SubTaskState>() }
    val taskProgressStateMap = remember { mutableMapOf<String, androidx.compose.runtime.MutableState<TaskProgress?>>() }
    val taskDoneStateMap = remember { mutableMapOf<String, androidx.compose.runtime.MutableState<Boolean>>() }
    var expandState by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(task) {
        subTasks.clear()
        taskProgressStateMap.clear()
        taskDoneStateMap.clear()
        val expandSeed = mutableMapOf<String, Boolean>()
        initExpandState(task, listOf(task.name), expandSeed, taskProgressStateMap, taskDoneStateMap)
        expandState = expandSeed
        errorMessage = null
        done = false
        currentName = task.name
        currentMessage = "准备中"
        currentFraction = null
        runCatching {
            runRootTask(
                task = task,
                onCurrent = { progress ->
                    currentMessage = progress.message
                    currentFraction = progress.fraction
                },
                subTasks = subTasks,
                onWindow = { subTaskWindow = it },
                onTaskProgress = { path, progress ->
                    val key = pathKey(path)
                    val state = taskProgressStateMap[key] ?: mutableStateOf<TaskProgress?>(null).also {
                        taskProgressStateMap[key] = it
                    }
                    emitOnMain { state.value = progress }
                },
                onTaskDone = { path ->
                    val key = pathKey(path)
                    val doneState = taskDoneStateMap[key] ?: mutableStateOf(false).also {
                        taskDoneStateMap[key] = it
                    }
                    val progressState = taskProgressStateMap[key] ?: mutableStateOf<TaskProgress?>(null).also {
                        taskProgressStateMap[key] = it
                    }
                    emitOnMain {
                        doneState.value = true
                        progressState.value = TaskProgress("完成", 1f)
                    }
                }
            )
        }.onSuccess {
            done = true
            currentMessage = "完成"
            currentFraction = 1f
            onDone()
            if(autoClose) onBack()
        }.onFailure { error ->
            error.printStackTrace()
            if (error is CancellationException) throw error
            errorMessage = error.message ?: "任务失败"
        }
    }

    MainColumn {
        TitleRow(currentName, onBack) {}
        Space8h()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactHeader = maxWidth < 560.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                if (compactHeader) {
                    Text("进度 ${((currentFraction ?: 0f) * 100).toFixed(1)}% · ${currentMessage.truncate(120)}")
                    Space8h()
                    LinearProgressIndicator(
                        progress = currentFraction?.coerceIn(0f, 1f) ?: 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Space8h()
                        Text(it, color = MaterialTheme.colors.error)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentMessage.truncate(80),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(8.wM)
                        LinearProgressIndicator(
                            progress = currentFraction?.coerceIn(0f, 1f) ?: 0f,
                            modifier = Modifier.widthIn(min = 140.dp, max = 320.dp)
                        )
                        Spacer(8.wM)
                        Text("${((currentFraction ?: 0f) * 100).toFixed(1)}%")
                    }
                    errorMessage?.let {
                        Space8h()
                        Text(it, color = MaterialTheme.colors.error)
                    }
                }
            }
        }

        if (done && errorMessage == null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("任务已完成", style = MaterialTheme.typography.h4)
            }
        }
        if (!done) {
            Spacer(8.hM)
            val listState = rememberLazyListState()
            val treeRows by remember(task, expandState) {
                derivedStateOf {
                    buildTaskRows(task, listOf(task.name), 0, expandState)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(treeRows, key = { _, item -> pathKey(item.keyPath) }) { _, item ->
                        val key = pathKey(item.keyPath)
                        val progress = taskProgressStateMap[key]?.value
                        val done = taskDoneStateMap[key]?.value == true
                        TaskTreeRow(
                            row = item,
                            progress = progress,
                            done = done,
                            expanded = expandState[pathKey(item.keyPath)] ?: item.defaultExpanded,
                            onToggle = {
                                val current = expandState[key] ?: item.defaultExpanded
                                expandState = expandState.toMutableMap().apply {
                                    this[key] = !current
                                }
                            }
                        )
                    }
                }

            }
        }
    }
}

private data class SubTaskState(
    val slotId: Int,
    val name: String,
    val message: String = "等待中",
    val fraction: Float? = null,
    val error: String? = null,
    val active: Boolean = false,
    val done: Boolean = false
){}

@Composable
private fun SubTaskRow(item: SubTaskState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, style = MaterialTheme.typography.subtitle2)
            Spacer(modifier = Modifier.weight(1f))
            item.error?.let {
                Text("失败", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
            }
            Text(item.message, style = MaterialTheme.typography.body2)
            Spacer(8.wM)
            LinearProgressIndicator(
                progress = item.fraction?.coerceIn(0f, 1f)?:0f,
                modifier = 200.wM
            )
            Spacer(8.wM)
            Text("${((item.fraction?:0f)*100).toFixed(1)}%")
        }


    }
}

private data class TaskTreeRowState(
    val keyPath: List<String>,
    val name: String,
    val level: Int,
    val isGroup: Boolean,
    val defaultExpanded: Boolean
)

private fun buildTaskRows(
    task: Task,
    keyPath: List<String>,
    level: Int,
    expandState: Map<String, Boolean>,
    rows: MutableList<TaskTreeRowState> = mutableListOf()
): List<TaskTreeRowState> {
    val isGroup = task is Task.Group || task is Task.Sequence
    rows += TaskTreeRowState(
        keyPath = keyPath,
        name = task.name,
        level = level,
        isGroup = isGroup,
        defaultExpanded = task.defaultExpandedInTaskTree()
    )
    if (isGroup) {
        val key = pathKey(keyPath)
        val expanded = expandState[key] ?: task.defaultExpandedInTaskTree()
        if (expanded) {
            val children = when (task) {
                is Task.Group -> task.subTasks
                is Task.Sequence -> task.subTasks
                else -> emptyList()
            }
            children.forEachIndexed { index, sub ->
                buildTaskRows(
                    sub,
                    keyPath + childKeySegment(sub.name, index),
                    level + 1,
                    expandState,
                    rows
                )
            }
        }
    }
    return rows
}

@Composable
private fun TaskTreeRow(
    row: TaskTreeRowState,
    progress: TaskProgress?,
    done: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val message = when {
        done -> "完成"
        progress != null -> progress.message
        else -> "等待中"
    }
    val fraction = progress?.fraction ?: if (done) 1f else 0f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactRow = maxWidth < 720.dp
        val indent = (row.level * if (compactRow) 14 else 24).coerceAtMost(240).dp
        if (compactRow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent)
                    .then(if (row.isGroup) Modifier.clickable { onToggle() } else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.isGroup) {
                        Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.subtitle2)
                        Spacer(8.wM)
                    } else {
                        Spacer(12.wM)
                    }
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${(fraction * 100).toFixed(1)}%")
                }
                Text(message.truncate(60), style = MaterialTheme.typography.body2)
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent)
                    .then(if (row.isGroup) Modifier.clickable { onToggle() } else Modifier)
            ) {
                if (row.isGroup) {
                    Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.subtitle2)
                    Spacer(8.wM)
                } else {
                    Spacer(12.wM)
                }
                Text(row.name, style = MaterialTheme.typography.subtitle2)
                Spacer(modifier = Modifier.weight(1f))
                Text(message.truncate(60), style = MaterialTheme.typography.body2)
                Spacer(8.wM)
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.widthIn(min = 120.dp, max = 200.dp)
                )
                Spacer(8.wM)
                Text("${(fraction * 100).toFixed(1)}%")
            }
        }
    }
}

private suspend fun runRootTask(
    task: Task,
    onCurrent: (TaskProgress) -> Unit,
    subTasks: MutableList<SubTaskState>,
    onWindow: (Int) -> Unit,
    onTaskProgress: (List<String>, TaskProgress) -> Unit,
    onTaskDone: (List<String>) -> Unit
) {
    when (task) {
        is Task.Leaf -> {
            subTasks.clear()
            onWindow(0)
            runTaskWithProgress(
                task = task,
                onProgress = onCurrent,
                taskKeyPath = listOf(task.name),
                onTaskProgress = onTaskProgress,
                onTaskDone = onTaskDone
            )
        }
        is Task.Group -> {
            subTasks.clear()
            val slotCount = if (task.subTasks.isEmpty()) {
                0
            } else {
                task.parallelism.coerceAtLeast(1).coerceAtMost(task.subTasks.size)
            }
            subTasks.addAll(List(slotCount) { index -> SubTaskState(slotId = index, name = "") })
            onWindow(slotCount)
            runGroup(
                task = task,
                onProgress = onCurrent,
                subTasks = subTasks,
                onTaskProgress = onTaskProgress,
                onTaskDone = onTaskDone
            )
        }
        is Task.Sequence -> {
            subTasks.clear()
            onWindow(0)
            runTaskWithProgress(
                task = task,
                onProgress = onCurrent,
                taskKeyPath = listOf(task.name),
                onTaskProgress = onTaskProgress,
                onTaskDone = onTaskDone
            )
        }
    }
}

private suspend fun runGroup(
    task: Task.Group,
    onProgress: (TaskProgress) -> Unit,
    subTasks: MutableList<SubTaskState>,
    onTaskProgress: (List<String>, TaskProgress) -> Unit,
    onTaskDone: (List<String>) -> Unit
) = coroutineScope {
    val groupPath = listOf(task.name)
    if (task.subTasks.isEmpty() || subTasks.isEmpty()) {
        emitOnMain {
            val progress = TaskProgress("没有子任务", 1f)
            onProgress(progress)
            onTaskProgress(groupPath, progress)
        }
        onTaskDone(groupPath)
        return@coroutineScope
    }
    val total = task.subTasks.size.coerceAtLeast(1)
    val completed = AtomicInteger(0)
    val errors = Collections.synchronizedList(mutableListOf<String>())
    val semaphore = Semaphore(task.parallelism.coerceAtLeast(1))
    val pending = task.subTasks.mapIndexed { index, subTask -> IndexedValue(index, subTask) }.toMutableList()
    val pendingLock = ReentrantLock()

    emitOnMain {
        val progress = TaskProgress("准备子任务 ${task.subTasks.size} 个", 0f)
        onProgress(progress)
        onTaskProgress(groupPath, progress)
    }

    subTasks.indices.map { index ->
        async(Dispatchers.IO) {
            while (true) {
                val nextTask = pendingLock.withLock {
                    if (pending.isNotEmpty()) pending.removeAt(0) else null
                }
                if (nextTask == null) {
                    emitOnMain {
                        subTasks[index] = subTasks[index].copy(
                            active = false,
                            done = true,
                            message = "完成",
                            fraction = 1f
                        )
                    }
                    break
                }
                semaphore.withPermit {
                    emitOnMain {
                        subTasks[index] = subTasks[index].copy(
                            name = nextTask.value.name,
                            message = "等待中",
                            fraction = null,
                            error = null,
                            active = true,
                            done = false
                        )
                    }
                    runCatching {
                        val keyPath = groupPath + childKeySegment(nextTask.value.name, nextTask.index)
                        runTaskWithProgress(
                            task = nextTask.value,
                            onProgress = { progress ->
                                emitOnMain {
                                    subTasks[index] = subTasks[index].copy(
                                        message = progress.message,
                                        fraction = progress.fraction
                                    )
                                }
                            },
                            taskKeyPath = keyPath,
                            onTaskProgress = onTaskProgress,
                            onTaskDone = onTaskDone
                        )
                    }.onFailure { error ->
                        error.printStackTrace()
                        val message = error.message ?: "任务失败"
                        emitOnMain {
                            subTasks[index] = subTasks[index].copy(
                                message = message,
                                fraction = subTasks[index].fraction,
                                error = message,
                                active = true,
                                done = false
                            )
                        }
                        errors += "${nextTask.value.name}: $message"
                    }
                    val done = completed.incrementAndGet()
                    val fraction = done.toFloat() / total
                    emitOnMain {
                        val progress = TaskProgress("已完成 $done/$total", fraction)
                        onProgress(progress)
                        onTaskProgress(groupPath, progress)
                    }
                }
            }
        }
    }.awaitAll()

    onTaskDone(groupPath)
    if (errors.isNotEmpty()) {
        val preview = errors.take(3).joinToString()
        throw IllegalStateException("子任务失败 ${errors.size} 个，例如: $preview")
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
            emitOnMain {
                val progress = TaskProgress("开始子任务 ${task.subTasks.size} 个", 0f)
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            val total = task.subTasks.size.coerceAtLeast(1)
            val completed = AtomicInteger(0)
            val parallelism = task.parallelism.coerceAtLeast(1)
            if (parallelism == 1) {
                task.subTasks.forEachIndexed { index, subTask ->
                    withContext(Dispatchers.IO) {
                        runTaskWithProgress(
                            task = subTask,
                            onProgress = onProgress,
                            taskKeyPath = taskKeyPath + childKeySegment(subTask.name, index),
                            onTaskProgress = onTaskProgress,
                            onTaskDone = onTaskDone
                        )
                    }
                    val done = completed.incrementAndGet()
                    emitOnMain {
                        val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                        onProgress(progress)
                        onTaskProgress(taskKeyPath, progress)
                    }
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
                                    taskKeyPath = taskKeyPath + childKeySegment(subTask.name, index),
                                    onTaskProgress = onTaskProgress,
                                    onTaskDone = onTaskDone
                                )
                            }
                            val done = completed.incrementAndGet()
                            emitOnMain {
                                val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                                onProgress(progress)
                                onTaskProgress(taskKeyPath, progress)
                            }
                        }
                    }.awaitAll()
                }
            }
            onTaskDone(taskKeyPath)
        }
        is Task.Sequence -> {
            emitOnMain {
                val progress = TaskProgress("开始子任务 ${task.subTasks.size} 个", 0f)
                onProgress(progress)
                onTaskProgress(taskKeyPath, progress)
            }
            val total = task.subTasks.size.coerceAtLeast(1)
            val completed = AtomicInteger(0)
            task.subTasks.forEachIndexed { index, subTask ->
                withContext(Dispatchers.IO) {
                    runTaskWithProgress(
                        task = subTask,
                        onProgress = onProgress,
                        taskKeyPath = taskKeyPath + childKeySegment(subTask.name, index),
                        onTaskProgress = onTaskProgress,
                        onTaskDone = onTaskDone
                    )
                }
                val done = completed.incrementAndGet()
                emitOnMain {
                    val progress = TaskProgress("已完成 $done/$total", done.toFloat() / total)
                    onProgress(progress)
                    onTaskProgress(taskKeyPath, progress)
                }
            }
            onTaskDone(taskKeyPath)
        }
    }
}

private fun pathKey(path: List<String>): String = path.joinToString(" / ")
private fun childKeySegment(name: String, index: Int): String = "$name#$index"

private fun String.truncate(maxChars: Int): String {
    if (length <= maxChars) return this
    if (maxChars <= 1) return "…"
    return substring(0, maxChars - 1) + "…"
}

private fun initExpandState(
    task: Task,
    path: List<String>,
    expandState: MutableMap<String, Boolean>,
    progressStates: MutableMap<String, androidx.compose.runtime.MutableState<TaskProgress?>>,
    doneStates: MutableMap<String, androidx.compose.runtime.MutableState<Boolean>>
) {
    if (task is Task.Group || task is Task.Sequence) {
        val key = pathKey(path)
        expandState[key] = task.defaultExpandedInTaskTree()
        if (!progressStates.containsKey(key)) {
            progressStates[key] = mutableStateOf(null)
        }
        if (!doneStates.containsKey(key)) {
            doneStates[key] = mutableStateOf(false)
        }
        val children = when (task) {
            is Task.Group -> task.subTasks
            is Task.Sequence -> task.subTasks
            else -> emptyList()
        }
        children.forEachIndexed { index, sub ->
            initExpandState(sub, path + childKeySegment(sub.name, index), expandState, progressStates, doneStates)
        }
    } else {
        val key = pathKey(path)
        if (!progressStates.containsKey(key)) {
            progressStates[key] = mutableStateOf(null)
        }
        if (!doneStates.containsKey(key)) {
            doneStates[key] = mutableStateOf(false)
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
            emitOnMain { onProgress(progress) }
        }
    }
}

private val snapshotLock = ReentrantLock()

private fun emitOnMain(block: () -> Unit) {
    UiUpdateBatcher.post(block)
}

private fun Task.defaultExpandedInTaskTree(): Boolean = when (this) {
    is Task.Group -> subTasks.size <= 10
    is Task.Sequence -> subTasks.size <= 10
    else -> true
}

//批量更新ui 一秒60次防止卡住
private object UiUpdateBatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val scheduled = AtomicBoolean(false)

    fun post(block: () -> Unit) {
        queue.add(block)
        if (scheduled.compareAndSet(false, true)) {
            scope.launch { flushLoop() }
        }
    }

    private suspend fun flushLoop() {
        while (true) {
            val batch = ArrayList<() -> Unit>(64)
            var item = queue.poll()
            while (item != null && batch.size < 200) {
                batch.add(item)
                item = queue.poll()
            }
            if (batch.isNotEmpty()) {
                snapshotLock.withLock {
                    applySnapshot { batch.forEach { it() } }
                }
            }
            if (queue.isEmpty()) {
                scheduled.set(false)
                if (queue.isEmpty()) return
                if (!scheduled.compareAndSet(false, true)) return
            }
            delay(16)
        }
    }
}

private fun applySnapshot( attempts: Int = 3,block: () -> Unit,) {
    var lastError: Exception? = null
    repeat(attempts) {
        try {
            Snapshot.withMutableSnapshot { block() }
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    if (lastError != null) throw lastError
    throw IllegalStateException("Snapshot apply failed")
}
