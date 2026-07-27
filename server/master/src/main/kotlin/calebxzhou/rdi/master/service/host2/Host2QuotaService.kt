package calebxzhou.rdi.master.service.host2

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.service.McServerPinger
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

class Host2QuotaService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository
) {
    private val lgr by Loggers
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notified = ConcurrentHashMap.newKeySet<UUID>()
    private val idleCounts = ConcurrentHashMap<UUID, Int>()

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { checkRunningHosts() }
                    .onFailure { lgr.error(it) { "Host2 quota检查失败" } }
                delay(1.minutes)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun checkRunningHosts() {
        val hosts = database.transaction { repository.listBySetupStatus(Host2SetupStatus.READY) }
        hosts.forEach { host ->
            val running = Host2RuntimeService.status(host.id) in setOf(HostStatus.STARTED, HostStatus.PLAYABLE)
            if (!running) {
                notified.remove(host.id)
                idleCounts.remove(host.id)
                return@forEach
            }
            if (Host2RuntimeService.sizeBytes(host.id) > HOST2_QUOTA_BYTES) {
                runCatching { Host2RuntimeService.stop(host.id) }
                    .onFailure { lgr.error(it) { "Host2 ${host.id}超过quota后停止失败" } }
                if (notified.add(host.id)) {
                    runCatching {
                        MailService.sendSystemMail(
                            host.ownerId.objectId,
                            "新版房间已停止",
                            "${host.name}的文件已超过8GiB，请删除不必要文件后再启动。"
                        )
                    }.onFailure { lgr.error(it) { "Host2 ${host.id}发送quota邮件失败" } }
                }
                idleCounts.remove(host.id)
                return@forEach
            }
            val online = runCatching { McServerPinger.ping(host.port).players?.online }
                .getOrElse {
                    lgr.warn { "Host2 ${host.id}空闲检查ping失败: ${it.message}" }
                    return@forEach
                } ?: return@forEach
            if (online > 0) {
                idleCounts.remove(host.id)
            } else {
                val idleCount = idleCounts.merge(host.id, 1, Int::plus) ?: 1
                if (idleCount >= IDLE_STOP_MINUTES) {
                    runCatching { Host2RuntimeService.stop(host.id) }
                        .onFailure { lgr.error(it) { "Host2 ${host.id}空闲停止失败" } }
                    idleCounts.remove(host.id)
                }
            }
        }
    }
}

private const val IDLE_STOP_MINUTES = 10
