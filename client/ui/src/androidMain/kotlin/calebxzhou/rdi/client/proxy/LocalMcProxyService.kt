package calebxzhou.rdi.client.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import calebxzhou.rdi.client.R
import calebxzhou.rdi.client.android.AndroidBootstrap
import calebxzhou.rdi.client.android.MainActivity

class LocalMcProxyService : Service() {
    companion object {
        const val ACTION_START = "calebxzhou.rdi.client.proxy.START"
        const val ACTION_STOP = "calebxzhou.rdi.client.proxy.STOP"

        const val PREFS_NAME = "local_mc_proxy"
        const val KEY_LISTEN_PORT = "listen_port"

        const val CHANNEL_ID = "local_mc_proxy"
        const val NOTIFICATION_ID = 55667
        const val WAKE_LOCK_TAG_SUFFIX = ":LocalMcProxy"
    }

    private val server = LocalMcProxyServer(
        onListenPortChanged = { port ->
            /*if (port == null) {
                LocalMcProxy.clearListenPort()
            } else {
                LocalMcProxy.persistListenPort(port)
            }*/
        }
    )
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var stoppingExplicitly = false

    override fun onCreate() {
        super.onCreate()
        AndroidBootstrap.initialize(this)
        createNotificationChannel()
        if (!startForegroundInternal()) {
            stopSelf()
            return
        }
        acquireWakeLock()
        ensureProxyServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stoppingExplicitly = true
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START,
            null -> {
                stoppingExplicitly = false
                if (!startForegroundInternal()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                acquireWakeLock()
                ensureProxyServer()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LocalMcProxy.reportLog("task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        server.stop()
        if (!stoppingExplicitly) {
            LocalMcProxy.reportLog("service destroyed unexpectedly")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureProxyServer() {
        server.start()
        updateNotification()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName$WAKE_LOCK_TAG_SUFFIX"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        LocalMcProxy.reportLog("wakelock acquired")
    }

    private fun releaseWakeLock() {
        val currentWakeLock = wakeLock ?: return
        wakeLock = null
        runCatching {
            if (currentWakeLock.isHeld) {
                currentWakeLock.release()
            }
        }
        LocalMcProxy.reportLog("wakelock released")
    }

    private fun startForegroundInternal(): Boolean {
        return true
        /*val notification = buildNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            LocalMcProxy.reportLog("foreground start not allowed")
            false
        }*/
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
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
            .setContentTitle("RDI联机服务运行中")
            .setContentText("RDI联机服务运行中")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "RDI联机服务运行中"
                )
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RDI联机服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持RDI联机服务在后台运行"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
