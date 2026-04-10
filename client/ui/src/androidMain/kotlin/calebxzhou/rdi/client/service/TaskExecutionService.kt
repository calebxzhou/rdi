package calebxzhou.rdi.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import calebxzhou.rdi.client.R
import calebxzhou.rdi.client.android.MainActivity
import calebxzhou.rdi.client.ui.screen.ClientTaskManager
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class TaskExecutionService : Service() {
    companion object {
        const val ACTION_START = "calebxzhou.rdi.client.task.START"
        const val CHANNEL_ID = "task_execution"
        const val NOTIFICATION_ID = 55668
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val entry = currentForegroundEntry(ClientTaskManager.entries.value)
        if (entry == null) {
            stopSelf()
            return
        }
        startForegroundInternal(entry)
        serviceScope.launch {
            ClientTaskManager.entries.collectLatest { entries ->
                val current = currentForegroundEntry(entries)
                if (current == null) {
                    ServiceCompat.stopForeground(this@TaskExecutionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(current)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entry = currentForegroundEntry(ClientTaskManager.entries.value)
        if (entry == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundInternal(entry)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundInternal(entry: Task2Entry) {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(entry),
            serviceType
        )
    }

    private fun updateNotification(entry: Task2Entry) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(entry))
    }

    private fun buildNotification(entry: Task2Entry): Notification {
        val snapshot = entry.snapshot
        val percent = snapshot.currentFraction?.coerceIn(0f, 1f)
        val percentText = percent?.let {
            String.format(Locale.US, "%.1f%%", it * 100f)
        }
        val contentText = buildString {
            append(snapshot.currentMessage.ifBlank { "任务执行中" })
            if (!percentText.isNullOrBlank()) {
                append(" · ")
                append(percentText)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(snapshot.taskTitle.ifBlank { "RDI任务执行中" })
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(
                100,
                percent?.times(100f)?.roundToInt()?.coerceIn(0, 100) ?: 0,
                percent == null
            )
            .build()
    }

    private fun currentForegroundEntry(entries: List<Task2Entry>): Task2Entry? {
        return entries.firstOrNull { it.status == Task2Status.RUNNING }
            ?: entries.firstOrNull { it.status == Task2Status.QUEUED }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RDI任务执行",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持RDI任务在后台运行"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
