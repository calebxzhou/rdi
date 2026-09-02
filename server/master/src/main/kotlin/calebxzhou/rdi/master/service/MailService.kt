package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.util.humanDateTimeNow
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.SYSTEM_SENDER_ID
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.net.idParam
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.service.MailService.deleteMail
import calebxzhou.rdi.master.service.MailService.deleteMails
import calebxzhou.rdi.master.service.MailService.getInbox
import calebxzhou.rdi.master.service.MailService.getMail
import calebxzhou.rdi.master.service.MailService.mailId
import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.util.ioScope
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

fun Route.mailRoutes() {
    route("/mail"){
        get { response(data=call.player().getInbox()) }
        route("/{mailId}"){
            get {
                response(data=call.player().getMail(mailId()))
            }
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
    private val lgr by Loggers
    private const val TITLE_MAX_LEN = 120
    private const val CONTENT_MAX_LEN = 4000
    suspend fun RoutingContext.mailId() = idParam("mailId")
    private val mailCol = DB.getCollection<Mail>("mail")
    suspend fun getById(mailId: ObjectId): Mail? {
        return mailCol.find(eq("_id", mailId))
            .limit(1)
            .firstOrNull()
    }
    suspend fun RAccount.getMail(mailId: ObjectId): Mail {
        val mail = mailCol.find(
            and(
                eq("_id", mailId),
                eq("receiverId", this._id)
            )
        ).limit(1).firstOrNull() ?: throw RequestError("未找到对应邮件")
        return mail
    }
    suspend fun RAccount.getInbox(): List<Mail.Vo> {
        val mails = mailCol.find(eq("receiverId", _id))
            .sort(Sorts.descending("_id"))
            .toList()
        return mails.map {
            Mail.Vo(
                it._id,
                if (it.senderId == SYSTEM_SENDER_ID) "系统" else PlayerService.getName(it.senderId) ?: "未知",
                it.title,
                it.content.split("\n")[0].take(50).trim(),
                it.unread
            )
        }

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
        val normalizedTitle = normalizeTitle(title)
        val mail = Mail(
            senderId = senderId,
            receiverId = receiverId,
            title = normalizedTitle,
            content = content,
        )
        mailCol.insertOne(mail)
        return mail
    }
    suspend fun RAccount.deleteMail(mailId: ObjectId) {
        val deleteResult = mailCol.deleteOne(
            and(
                eq("_id", mailId),
                eq("receiverId", this._id)
            )
        )
        if (deleteResult.deletedCount == 0L) {
            throw RequestError("未找到对应邮件")
        }
    }
    suspend fun RAccount.deleteMails(mailIds: List<ObjectId>) {
        mailCol.deleteMany(
            and(
                eq("receiverId", this._id),
               Filters.`in`("_id", mailIds)
            )
        )
    }
    suspend fun sendSystemMail(
        receiverId: ObjectId,
        title: String,
        content: String,
    ): Mail {
        ensurePlayerExists(receiverId)
        val normalizedTitle = normalizeTitle(title)
        //系统邮件不需要截断正文长度 因为日志很长
        val normalizedContent = (content)
        val mail = Mail(
            senderId = SYSTEM_SENDER_ID,
            receiverId = receiverId,
            title = normalizedTitle,
            content = normalizedContent,
        )
        mailCol.insertOne(mail)
        return mail
    }

    fun changeMail(
        mailId: ObjectId,
        newTitle: String? = null,
        newContent: String? = null,
        append: Boolean = true
    )= ioScope.launch {
        if (newTitle == null && newContent == null) return@launch

        val mail = mailCol.find(eq("_id", mailId))
            .limit(1)
            .firstOrNull()
            ?: throw RequestError("未找到对应邮件")

        val normalizedTitle = newTitle?.let(::normalizeTitle)
        val normalizedContent = newContent?.let { content ->
            if (append) {
                val existing = mail.content.ifBlank { "" }
                if (existing.isBlank()) content else "$existing\n[${humanDateTimeNow}] $content"
            } else content
        }

        val updateOps = mutableListOf<org.bson.conversions.Bson>()
        normalizedTitle?.let { updateOps += Updates.set("title", it) }
        normalizedContent?.let { updateOps += Updates.set("content", it) }

        if (updateOps.isEmpty()) return@launch
        mailCol.updateOne(eq("_id", mailId), Updates.combine(*updateOps.toTypedArray()))
    }

    suspend fun markAsRead(mailId: ObjectId, ownerId: ObjectId) {
        val updateResult = mailCol.updateOne(
            and(eq("_id", mailId), eq("receiverId", ownerId)),
            Updates.set("unread", false)
        )
        if (updateResult.matchedCount == 0L) {
            throw RequestError("未找到对应邮件")
        }
    }

    private suspend fun ensurePlayerExists(uid: ObjectId) {
        if (uid == SYSTEM_SENDER_ID) return
        PlayerService.getById(uid) ?: throw RequestError("玩家不存在")
    }

    private fun normalizeTitle(title: String): String {
        val normalized = title.trim()
        if (normalized.isEmpty()) throw ParamError("标题不能为空")
        if (normalized.length > TITLE_MAX_LEN) throw ParamError("标题过长")
        return normalized
    }


}