package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.lgr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object AutoNodeRefreshService {
    private val refreshTime = LocalTime.of(18, 0)
    private var job: Job? = null
    private var lastRefreshDate: LocalDate? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val delayMillis = millisUntilNextRefresh()
                lgr.info { "下次自动刷新节点将在${Duration.ofMillis(delayMillis).toMinutes()}分钟后执行" }
                delay(delayMillis)

                val today = LocalDate.now()
                if (lastRefreshDate == today) continue
                lastRefreshDate = today

                NodeRefreshCoordinator.refreshFromPrimary("auto-18:00")
                    .onSuccess { entry ->
                        lgr.info { "18:00自动刷新节点成功: ${entry.nodeName} -> ${RServer.currentGameAddr}" }
                    }
                    .onFailure {
                        lgr.warn(it) { "18:00自动刷新节点失败，继续使用当前节点" }
                    }

                delay(60_000)
            }
        }
    }

    private fun millisUntilNextRefresh(now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.toLocalDate().atTime(refreshTime).atZone(now.zone)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(1_000)
    }
}
