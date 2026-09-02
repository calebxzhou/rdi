package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.humanDateTimeNow
import calebxzhou.rdi.common.util.ioScope
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.SYSTEM_SENDER_ID
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.idParam
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.service.MailService.deleteMail
import calebxzhou.rdi.master.service.MailService.deleteMails
import calebxzhou.rdi.master.service.MailService.getInbox
import calebxzhou.rdi.master.service.MailService.getMail
import calebxzhou.rdi.master.service.MailService.mailId
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

fun Route.mailRoutes() {
    route("/mail") {
        get { response(data = call.player().getInbox()) }
        route("/{mailId}") {
            get { response(data = call.player().getMail(mailId())) }
            delete {
                call.player().deleteMail(mailId())
                ok()
            }
        }
        delete {
            call.player().deleteMails(call.receive())
            ok()
        }
    }
}

object MailService {
    private const val TITLE_MAX_LEN = 120
    private const val CONTENT_MAX_LEN = 4000
    private val mailCol = DB.getCollection<Mail>("mail")

    suspend fun RoutingContext.mailId(): ObjectId = idParam("mailId")

    suspend fun getById(mailId: ObjectId): Mail? =
        mailCol.find(eq("_id", mailId)).limit(1).firstOrNull()

    suspend fun RAccount.getMail(mailId: ObjectId): Mail =
        mailCol.find(
            and(
                eq("_id", mailId),
                eq("receiverId", _id)
            )
        ).limit(1).firstOrNull() ?: throw RequestError("未找到对应邮件")

    suspend fun RAccount.getInbox(): List<Mail.Vo> =
        mailCol.find(eq("receiverId", _id))
            .sort(Sorts.descending("_id"))
            .toList()
            .map { mail ->
                Mail.Vo(
                    mail._id,
                    if (mail.senderId == SYSTEM_SENDER_ID) "系统" else PlayerService.getName(mail.senderId) ?: "未知",
                    mail.title,
                    mail.content.lineSequence().firstOrNull().orEmpty().take(50).trim(),
                    mail.unread
                )
            }

    suspend fun sendMail(
        senderId: ObjectId,
        receiverId: ObjectId,
        title: String,
        content: String,
    ): Mail {
        if (content.length > CONTENT_MAX_LEN) throw ParamError("内容过长")
        ensurePlayerExists(senderId)
        ensurePlayerExists(receiverId)
        val mail = Mail(
            senderId = senderId,
            receiverId = receiverId,
            title = normalizeTitle(title),
            content = content,
        )
        mailCol.insertOne(mail)
        return mail
    }

    suspend fun RAccount.deleteMail(mailId: ObjectId) {
        val result = mailCol.deleteOne(and(eq("_id", mailId), eq("receiverId", _id)))
        if (result.deletedCount == 0L) throw RequestError("未找到对应邮件")
    }

    suspend fun RAccount.deleteMails(mailIds: List<ObjectId>) {
        mailCol.deleteMany(and(eq("receiverId", _id), Filters.`in`("_id", mailIds)))
    }

    suspend fun sendSystemMail(
        receiverId: ObjectId,
        title: String,
        content: String,
    ): Mail {
        ensurePlayerExists(receiverId)
        val mail = Mail(
            senderId = SYSTEM_SENDER_ID,
            receiverId = receiverId,
            title = normalizeTitle(title),
            content = content,
        )
        mailCol.insertOne(mail)
        return mail
    }

    fun changeMail(
        mailId: ObjectId,
        newTitle: String? = null,
        newContent: String? = null,
        append: Boolean = true,
    ) = ioScope.launch {
        if (newTitle == null && newContent == null) return@launch
        val mail = mailCol.find(eq("_id", mailId)).limit(1).firstOrNull()
            ?: throw RequestError("未找到对应邮件")
        val normalizedTitle = newTitle?.let(::normalizeTitle)
        val normalizedContent = newContent?.let { content ->
            if (!append) content else {
                val existing = mail.content.ifBlank { "" }
                if (existing.isBlank()) content else "$existing\n[${humanDateTimeNow}] $content"
            }
        }
        val updates = buildList {
            normalizedTitle?.let { add(Updates.set("title", it)) }
            normalizedContent?.let { add(Updates.set("content", it)) }
        }
        if (updates.isNotEmpty()) {
            mailCol.updateOne(eq("_id", mailId), Updates.combine(*updates.toTypedArray()))
        }
    }

    suspend fun markAsRead(mailId: ObjectId, ownerId: ObjectId) {
        val result = mailCol.updateOne(
            and(eq("_id", mailId), eq("receiverId", ownerId)),
            Updates.set("unread", false)
        )
        if (result.matchedCount == 0L) throw RequestError("未找到对应邮件")
    }

    private suspend fun ensurePlayerExists(uid: ObjectId) {
        if (uid != SYSTEM_SENDER_ID && PlayerService.getById(uid) == null) {
            throw RequestError("玩家不存在")
        }
    }

    private fun normalizeTitle(title: String): String {
        val normalized = title.trim()
        if (normalized.isEmpty()) throw ParamError("标题不能为空")
        if (normalized.length > TITLE_MAX_LEN) throw ParamError("标题过长")
        return normalized
    }
}
