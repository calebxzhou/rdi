package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzau.rdi.common.model.FriendRequestStatus
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.MailAction
import calebxzhou.rdi.common.model.MailKind
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.humanDateTimeNow
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.lgr
import calebxzau.rdi.server.friend.FriendService
import calebxzau.rdi.server.infra.PostgresRuntime
import calebxzau.rdi.server.mail.MailRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.types.ObjectId
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.RoutingContext
import calebxzhou.rdi.master.service.MailService.getInbox
import calebxzhou.rdi.master.service.MailService.getMail
import calebxzhou.rdi.master.service.MailService.deleteMail
import calebxzhou.rdi.master.service.MailService.deleteMails
import calebxzhou.rdi.master.service.PlayerService.getPlayerNames
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response

internal const val IN_APP_MAIL_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

object MailService {
    private const val TITLE_MAX_LEN = 120
    private const val CONTENT_MAX_LEN = 4000
    private const val PAGE_SIZE = 100
    @Volatile private var runtime: PostgresRuntime? = null
    private var cleanupJob: Job? = null

    fun configure(value: PostgresRuntime) {
        runtime = value
        cleanupJob?.cancel()
        cleanupJob = ioScope.launch {
            while (isActive) {
                runCatching {
                    value.database.transaction {
                        val now = System.currentTimeMillis()
                        value.friendRepo.expireRequests(now)
                        value.mailRepo.deleteOlderThan(now - IN_APP_MAIL_RETENTION_MILLIS)
                        value.friendRepo.deleteHandledBefore(now - IN_APP_MAIL_RETENTION_MILLIS, value.mailRepo.friendRequestReferences())
                    }
                }
                    .onFailure { lgr.warn(it) { "清理过期应用内邮件失败" } }
                delay(1.hours)
            }
        }
    }
    fun close() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
    private fun db() = runtime ?: error("PostgreSQL mail service has not been configured")

    suspend fun RAccount.getInbox(page: Int = 0): List<Mail.Vo> {
        if (page < 0) throw RequestError("邮件页码无效")
        val cutoff = System.currentTimeMillis() - IN_APP_MAIL_RETENTION_MILLIS
        val records = db().database.transaction {
            db().friendRepo.expireRequests(System.currentTimeMillis())
            db().mailRepo.listInbox(uuid, page.toLong() * PAGE_SIZE, PAGE_SIZE, cutoff)
        }
        val statusByReference = db().database.transaction {
            db().friendRepo.findRequests(records.mapNotNull { it.referenceId })
                .mapValues { it.value.status }
        }
        val senderNames = records.mapNotNull { it.senderId?.objectId }.getPlayerNames()
        return records.map { record ->
            record.toVo(statusByReference[record.referenceId], record.senderId?.objectId?.let { senderNames[it] })
        }
    }

    suspend fun RAccount.getMail(mailId: UUID): Mail.Dto {
        db().database.transaction { db().friendRepo.expireRequests(System.currentTimeMillis()) }
        val record = db().database.transaction {
            val mail = db().mailRepo.findByIdForUpdate(mailId) ?: throw RequestError("未找到对应邮件")
            if (mail.receiverId != uuid) throw RequestError("未找到对应邮件")
            if (mail.createdAt < System.currentTimeMillis() - IN_APP_MAIL_RETENTION_MILLIS) {
                throw RequestError("未找到对应邮件")
            }
            db().mailRepo.markRead(mailId, uuid)
            mail.copy(unread = false)
        }
        val status = record.referenceId?.let { referenceId -> db().database.transaction { db().friendRepo.findRequest(referenceId)?.status } }
        return record.toDto(status, record.senderId?.objectId?.let { PlayerService.getName(it) })
    }

    suspend fun sendMail(senderId: ObjectId, receiverId: ObjectId, title: String, content: String): Mail =
        send(senderId, receiverId, title, content, MailKind.Normal)
    suspend fun sendSystemMail(receiverId: ObjectId, title: String, content: String): Mail =
        send(null, receiverId, title, content, MailKind.System)

    private suspend fun send(senderId: ObjectId?, receiverId: ObjectId, title: String, content: String, kind: MailKind, referenceId: UUID? = null): Mail {
        if (kind == MailKind.Normal && content.length > CONTENT_MAX_LEN) throw ParamError("内容过长")
        val receiver = PlayerService.getById(receiverId) ?: throw RequestError("玩家不存在")
        val senderUuid = senderId?.let { if (PlayerService.getById(it) == null) throw RequestError("玩家不存在"); it.toUUID() }
        val record = db().database.transaction {
            if (db().accountRepo.findById(receiverId.toUUID()) == null) throw RequestError("玩家不存在")
            senderUuid?.let { if (db().accountRepo.findById(it) == null) throw RequestError("玩家不存在") }
            db().mailRepo.create(senderUuid, receiver._id.toUUID(), normalizeTitle(title), content, kind, referenceId)
        }
        return record.toModel()
    }

    suspend fun RAccount.deleteMail(mailId: UUID) {
        db().database.transaction mailTx@{
            val mail = db().mailRepo.findByIdForUpdate(mailId) ?: throw RequestError("未找到对应邮件")
            if (mail.receiverId != uuid) throw RequestError("未找到对应邮件")
            if (mail.kind == MailKind.FriendRequest && mail.referenceId != null)
                FriendService.run { this@mailTx.rejectRequestInTransaction(mail.referenceId, uuid, System.currentTimeMillis()) }
            if (!db().mailRepo.delete(mailId, uuid)) throw RequestError("未找到对应邮件")
        }
    }
    suspend fun RAccount.deleteMails(mailIds: List<UUID>) {
        db().database.transaction mailTx@{
            mailIds.distinct().forEach { id ->
                val mail = db().mailRepo.findByIdForUpdate(id) ?: return@forEach
                if (mail.receiverId != uuid) return@forEach
                if (mail.kind == MailKind.FriendRequest && mail.referenceId != null)
                    FriendService.run { this@mailTx.rejectRequestInTransaction(mail.referenceId, uuid, System.currentTimeMillis()) }
                db().mailRepo.delete(id, uuid)
            }
        }
    }

    fun changeMail(mailId: UUID, newTitle: String? = null, newContent: String? = null, append: Boolean = true): Job =
        ioScope.launch {
            runCatching { changeMailAndWait(mailId, newTitle, newContent, append) }
                .onFailure { lgr.error(it) { "更新应用内邮件失败: mailId=$mailId" } }
        }
    suspend fun changeMailAndWait(mailId: UUID, newTitle: String? = null, newContent: String? = null, append: Boolean = true) {
        if (newTitle == null && newContent == null) return
        db().database.transaction {
            val mail = db().mailRepo.findByIdForUpdate(mailId) ?: throw RequestError("未找到对应邮件")
            val content = newContent?.let { value -> if (!append) value else mail.content.ifBlank { "" }.let { old -> if (old.isBlank()) value else "$old\n[${humanDateTimeNow}] $value" } }
            if (content != null && mail.kind == MailKind.Normal && content.length > CONTENT_MAX_LEN) throw ParamError("内容过长")
            if (!db().mailRepo.updateContent(mailId, newTitle?.let(::normalizeTitle), content)) {
                throw RequestError("未找到对应邮件")
            }
        }
    }
    suspend fun action(mailId: UUID, ownerId: ObjectId, action: MailAction) = FriendService.handleMailAction(mailId, ownerId, action)

    private fun MailRecord.toModel() = Mail(id, senderId?.objectId, receiverId.objectId, title, content, kind, referenceId, null, unread, createdAt)
    private fun MailRecord.toVo(status: FriendRequestStatus?, senderName: String?): Mail.Vo {
        val actions = if (kind == MailKind.FriendRequest && status == FriendRequestStatus.Pending)
            listOf(MailAction.Accept, MailAction.Reject) else emptyList()
        return Mail.Vo(id, senderName ?: if (kind == MailKind.System) "系统" else "未知", title, content.lineSequence().firstOrNull().orEmpty().take(50).trim(), unread, kind, status, createdAt, actions)
    }
    private fun MailRecord.toDto(status: FriendRequestStatus?, senderName: String?): Mail.Dto {
        val sender = senderId?.objectId
        return Mail.Dto(id, sender, senderName ?: if (kind == MailKind.System) "系统" else "未知", title, content, unread, kind, referenceId, status, createdAt,
            if (kind == MailKind.FriendRequest && status == FriendRequestStatus.Pending) listOf(MailAction.Accept, MailAction.Reject) else emptyList())
    }
    private fun normalizeTitle(title: String): String { val value = title.trim(); if (value.isEmpty()) throw ParamError("标题不能为空"); if (value.length > TITLE_MAX_LEN) throw ParamError("标题过长"); return value }
}

fun Route.mailRoutes() {
    route("/mail") {
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull()
                ?: if (call.request.queryParameters["page"] == null) 0 else throw ParamError("邮件页码无效")
            response(data = call.player().getInbox(page))
        }
        route("/{mailId}") {
            get { response(data = call.player().getMail(mailUuid())) }
            delete { call.player().deleteMail(mailUuid()); ok() }
            post("/action/{action}") {
                val action = runCatching { MailAction.valueOf(call.parameters["action"].orEmpty()) }
                    .getOrElse { throw ParamError("邮件操作无效") }
                MailService.action(mailUuid(), call.player()._id, action)
                ok()
            }
        }
        delete {
            call.player().deleteMails(call.receive())
            ok()
        }
    }
}

private suspend fun RoutingContext.mailUuid(): UUID =
    call.parameters["mailId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ParamError("邮件ID格式错误")
