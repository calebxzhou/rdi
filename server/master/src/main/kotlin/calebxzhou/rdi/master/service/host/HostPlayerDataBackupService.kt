package calebxzhou.rdi.master.service.host

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.common.util.str
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.WORLD_BACKUP_DIR
import calebxzhou.rdi.master.service.WorldService
import calebxzhou.rdi.master.service.host.HostControlService.status
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException as KxCancellationException

object HostPlayerDataBackupService {
    private val lgr by Loggers
    private const val MAX_BACKUPS_PER_PLAYER = 128
    private val lastBackedHashes = ConcurrentHashMap<String, String>()
    private var backupJob: Job? = null

    fun start() {
        if (backupJob?.isActive == true) return
        backupJob = HostService.idleMonitorScope.launch {
            while (isActive) {
                runCatching { runBackupTick() }
                    .onFailure { error ->
                        if (error is KxCancellationException) throw error
                        lgr.warn { "玩家数据自动备份失败: ${error.message}" }
                    }
                delay(1.minutes)
            }
        }
    }

    fun shutdown() {
        backupJob?.cancel()
        backupJob = null
        lastBackedHashes.clear()
    }

    internal suspend fun runBackupTick() {
        HostPresenceService.getPlayables()
            .filter { it.status == HostStatus.PLAYABLE }
            .forEach { host -> host.backupOnlinePlayerData() }
    }

    private suspend fun Host.backupOnlinePlayerData() {
        val host = this
        val worldId = worldId ?: return
        val playerIds = with(HostPresenceService) { host.fetchOnlinePlayersNow() }
        if (playerIds.isEmpty()) return
        playerIds.forEach { playerId ->
            backupPlayerData(worldId, playerId).onFailure { error ->
                if (error is KxCancellationException) throw error
                lgr.warn { "备份玩家数据失败: host=${_id.str}, world=${worldId.str}, player=${playerId.str}, ${error.message}" }
            }
        }
    }

    private fun backupPlayerData(worldId: ObjectId, playerId: ObjectId): Result<Unit> = runCatching {
        val playerUuid = playerId.toUUID().toString()
        val source = WorldService.getLevelDir(worldId)
            .resolve("playerdata")
            .resolve("$playerUuid.dat")
        if (!source.isFile || source.length() <= 0) return@runCatching

        val backupDir = WorldService.getBackupDir(worldId)
            .resolve("playerdata")
            .resolve(playerId.str)
        backupDir.mkdirs()

        val sourceHash = source.sha1
        val hashKey = "${worldId.str}:${playerId.str}"
        if (lastBackedHashes[hashKey] == sourceHash) return@runCatching

        latestBackup(backupDir)?.let { latest ->
            if (latest.sha1 == sourceHash) {
                lastBackedHashes[hashKey] = sourceHash
                return@runCatching
            }
        }

        val target = backupDir.resolve("${System.currentTimeMillis()}.dat")
        val temp = backupDir.resolve("${target.name}.tmp")
        source.copyTo(temp, overwrite = true)
        if (temp.length() != source.length() || temp.length() <= 0 || temp.sha1 != sourceHash) {
            temp.delete()
            return@runCatching
        }
        moveBackupFile(temp, target)
        lastBackedHashes[hashKey] = sourceHash
        trimBackups(backupDir)
    }

    private fun latestBackup(backupDir: File): File? =
        backupDir.listFiles { file -> file.isFile && file.extension == "dat" }
            ?.maxByOrNull { it.name }

    private fun trimBackups(backupDir: File) {
        val backups = backupDir.listFiles { file -> file.isFile && file.extension == "dat" }
            ?.sortedByDescending { it.name }
            ?: return
        backups.drop(MAX_BACKUPS_PER_PLAYER).forEach { it.delete() }
    }

    private fun moveBackupFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
