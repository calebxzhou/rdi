package calebxzhou.rdi.client.ui.screen

import android.content.Intent
import androidx.core.content.ContextCompat
import calebxzhou.rdi.client.service.TaskExecutionService
import calebxzhou.rdi.client.ui.AndroidPlatform

internal actual fun ensurePlatformTaskExecutionForegroundService() {
    val context = AndroidPlatform.appContext
    ContextCompat.startForegroundService(
        context,
        Intent(context, TaskExecutionService::class.java).apply {
            action = TaskExecutionService.ACTION_START
        }
    )
}
