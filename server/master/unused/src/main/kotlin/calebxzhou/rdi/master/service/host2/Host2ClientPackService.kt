package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.logging.Loggers
import calebxzau.rdi.common.model.ClientContentVo
import calebxzau.rdi.common.model.ContentSide
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2ErrorCode
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Host2ClientPackService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val hostService: Host2Service,
    private val packSourceService: Host2PackSourceService,
) {
    private val lgr by Loggers
    private val notifiedUnavailable = ConcurrentHashMap.newKeySet<UUID>()

    suspend fun manifest(player: RAccount, id: UUID): Host2.ClientManifest {
        hostService.detail(player, id)
        val host = database.transaction {
            repository.findById(id) ?: throw RequestError("无此新版房间")
        }
        val contents = database.transaction {
            if (host.activeContentRevision == 0L) emptyList()
            else repository.contents(id, host.activeContentRevision)
        }
        val live = packSourceService.resolveExact(host.packSource).getOrNull()
        return Host2.ClientManifest(
            packSource = host.packSource,
            activeContentRevision = host.activeContentRevision,
            mcVersion = live?.packInfo?.mcVersion,
            modLoader = live?.packInfo?.modLoader,
            contents = effectiveHost2Contents(contents).asSequence()
                .filter { it.enabled && it.side != ContentSide.Server }
                .map { content ->
                    ClientContentVo(
                        origin = content.origin,
                        platform = content.platform,
                        type = content.type,
                        projectId = content.projectId,
                        fileId = content.fileId,
                        slug = content.slug,
                        hash = content.hash,
                        targetPath = content.targetPath,
                        side = content.side,
                        required = content.required,
                        fileSize = content.fileSize,
                    )
                }
                .toList(),
        )
    }

    suspend fun clientPack(player: RAccount, id: UUID): File {
        hostService.detail(player, id)
        val host = database.transaction {
            repository.findById(id) ?: throw RequestError("无此新版房间")
        }
        val resolved = packSourceService.resolveExact(host.packSource)
            .getOrElse { error ->
                notifyUnavailableOnce(host)
                throw Host2ClientPackUnavailable(
                    msg = "房间整合包归档暂时不可用",
                    cause = error,
                )
            }
        if (!resolved.clientArchive.isFile) {
            notifyUnavailableOnce(host)
            throw Host2ClientPackUnavailable(
                msg = "房间整合包归档暂时不可用",
            )
        }
        notifiedUnavailable.remove(id)
        return resolved.clientArchive
    }

    suspend fun clientPackSha1(player: RAccount, id: UUID): String {
        val archive = clientPack(player, id)
        val sidecar = File("${archive.path}.sha1")
        sidecar.takeIf(File::isFile)?.readText()?.trim()?.takeIf(SHA1::matches)?.let { return it }
        val hash = archive.sha1.lowercase()
        val temporary = File("${sidecar.path}.${UUID.randomUUID()}.tmp")
        Files.writeString(temporary.toPath(), hash, StandardOpenOption.CREATE_NEW)
        try {
            Files.move(
                temporary.toPath(),
                sidecar.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
        return hash
    }

    private suspend fun notifyUnavailableOnce(host: Host2Record) {
        if (!notifiedUnavailable.add(host.id)) return
        runCatching {
            MailService.sendSystemMail(
                host.ownerId.objectId,
                "新版房间整合包暂时不可用",
                "${host.name}的客户端整合包归档当前无法读取。",
            )
        }.onFailure { lgr.error(it) { "Host2 ${host.id}发送归档不可用邮件失败" } }
    }
}

private val SHA1 = Regex("[A-Fa-f0-9]{40}")

class Host2ClientPackUnavailable(msg: String, cause: Throwable? = null) : RequestError(
    msg = msg,
    cause = cause,
    errorCode = Host2ErrorCode.SOURCE_UNAVAILABLE,
)
