package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import calebxzhou.rdi.common.model.execute
import kotlin.math.roundToInt

object BackgroundTaskRunner {
    private val lgr by Loggers

    suspend fun Task.start(onProgress: ((TaskProgress) -> Unit)? = null) {
        val task = this
        lgr.info { "任务开始: ${task.name}" }
        val ctx = TaskContext(
            emitProgress = { progress ->
                onProgress?.invoke(progress)
                val percentText = progress.fraction?.let { fraction ->
                    val pct = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
                    " $pct%"
                } ?: ""
                lgr.info { "[${task.name}] ${progress.message}$percentText" }
            }
        )
        task.execute(ctx)
        lgr.info { "任务完成: ${task.name}" }
    }
}
