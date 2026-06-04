package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Request
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.CryptoManager
import calebxzhou.rdi.master.CONF
import calebxzhou.rdi.master.ImapConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.FlagTerm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.bson.types.ObjectId
import java.util.Properties
import kotlin.time.Duration.Companion.seconds

/**
 * calebxzhou @ 2026-04-01 12:58
 */
data class ReceivedEmail(
    val uid: Long?,
    val messageNumber: Int,
    val subject: String,
    val from: List<String>,
    val sentAtMillis: Long?,
    val receivedAtMillis: Long?,
    val contentType: String,
    val text: String
)

object EmailService {
    private val cipherFragmentRegex = Regex("[A-Za-z0-9+/=]{24,}")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val htmlBreakRegex = Regex("(?i)<br\\s*/?>|</div>|</p>|</li>|</tr>|</h\\d>")
    private val lgr = KotlinLogging.logger { }
    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerJob: Job? = null
    private val config: ImapConfig
        get() = CONF.email.imap

    fun isEnabled(): Boolean = config.enabled

    fun startListener() {
        if (!config.enabled) {
            lgr.info { "IMAP listener disabled" }
            return
        }
        if (listenerJob?.isActive == true) {
            return
        }
        listenerJob = listenerScope.launch {
            while (isActive) {
                try {
                    pollOperationMails()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    lgr.warn { "IMAP poll failed: ${t.message}\n$t" }
                }
                delay(config.pollIntervalSeconds.coerceAtLeast(5).seconds)
            }
        }
        lgr.info { "IMAP listener started, subject=${config.operationSubject}" }
    }

    fun shutdown() {
        listenerJob?.cancel()
        listenerJob = null
    }

    suspend fun testConnection() {
        withInbox(markAsSeen = false) { _, _ -> }
    }

    suspend fun fetchUnreadEmails(
        limit: Int = 50,
        markAsSeen: Boolean = false
    ): List<ReceivedEmail> = withInbox(markAsSeen) { store, folder ->
        val unreadMessages = folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            .sortedByDescending { it.receivedDate?.time ?: it.sentDate?.time ?: 0L }
            .take(limit.coerceAtLeast(1))

        val mails = unreadMessages.map { it.toReceivedEmail(folder) }
        if (markAsSeen) {
            unreadMessages.forEach { it.setFlag(Flags.Flag.SEEN, true) }
        }

        mails
    }

    private suspend fun pollOperationMails() {
        val imapConfig = requireEnabledConfig()
        val mails = fetchUnreadEmails(
            markAsSeen = false
        ).mapNotNull { email ->
            runCatching {
                validateOperationSender(email)
                parseOperationReceiptId(email.subject)?.let { targetId -> email to targetId }
            }.getOrElse { error ->
                if(DEBUG) error.printStackTrace()
                lgr.warn { "skip operation email uid=${email.uid} subject=${email.subject}: ${error.message}" }
                null
            }
        }

        if (mails.isEmpty()) {
            return
        }
        lgr.info { "received ${mails.size} operation email(s) with prefix=${imapConfig.operationSubject}" }
        mails.forEach { (email, reid) ->

            markAsSeen(email)
            runCatching {
                handleOperationMail(email, reid)
            }.onFailure { error ->
                lgr.warn { " handle operation email failed uid=${email.uid} subject=${email.subject}: ${error.message}\n$error" }
            }
        }
    }

    private suspend fun <T> withInbox(
        markAsSeen: Boolean,
        block: (Store, Folder) -> T
    ): T = withContext(Dispatchers.IO) {
        val imapConfig = requireEnabledConfig()
        val session = Session.getInstance(buildProperties(imapConfig))
        val store = session.getStore("imap")
        try {
            store.connect(imapConfig.host, imapConfig.port, imapConfig.username, imapConfig.password)
            val folder = store.getFolder(imapConfig.folder)
                ?: error("IMAP folder not found: ${imapConfig.folder}")
            folder.open(if (markAsSeen) Folder.READ_WRITE else Folder.READ_ONLY)
            try {
                block(store, folder)
            } finally {
                if (folder.isOpen) {
                    folder.close(markAsSeen)
                }
            }
        } finally {
            if (store.isConnected) {
                store.close()
            }
        }
    }

    private fun requireEnabledConfig(): ImapConfig {
        val imapConfig = config
        check(imapConfig.enabled) { "IMAP未启用" }
        check(imapConfig.host.isNotBlank()) { "IMAP host不能为空" }
        check(imapConfig.username.isNotBlank()) { "IMAP username不能为空" }
        check(imapConfig.password.isNotBlank()) { "IMAP password不能为空" }
        check(imapConfig.folder.isNotBlank()) { "IMAP folder不能为空" }
        return imapConfig
    }

    private fun buildProperties(imapConfig: ImapConfig): Properties = Properties().apply {
        put("mail.store.protocol", "imap")
        put("mail.imap.host", imapConfig.host)
        put("mail.imap.port", imapConfig.port.toString())
        put("mail.imap.connectiontimeout", imapConfig.connectionTimeoutMillis.toString())
        put("mail.imap.timeout", imapConfig.timeoutMillis.toString())
        put("mail.imap.ssl.enable", imapConfig.ssl.toString())
        put("mail.imap.starttls.enable", imapConfig.startTls.toString())
    }

    private suspend fun markAsSeen(email: ReceivedEmail) {
        withInbox(markAsSeen = true) { _, folder ->
            val message = when {
                email.uid != null && folder is UIDFolder -> folder.getMessageByUID(email.uid)
                else -> folder.getMessage(email.messageNumber)
            } ?: return@withInbox
            message.setFlag(Flags.Flag.SEEN, true)
        }
    }

    private suspend fun handleOperationMail(email: ReceivedEmail, receiptId: ObjectId) {
        val content = extractOperationPayload(email.text)
        ReceiptService.insertReceipt(receiptId,email.from.joinToString(","),"处理中")
        val req = runCatching {
            serdesJson.decodeFromString<Request<JsonElement>>(CryptoManager.decrypt(content))
        }.getOrElse {
            lgr.error(it) { "无效的邮件操作 from ${email.from}" }
            ReceiptService.changeMsg(receiptId,"无效的操作内容")
            return
        }

        runCatching {
            when (req.opr) {
                "register" -> {
                    val registerDto = runCatching {
                        serdesJson.decodeFromJsonElement<RAccount.RegisterDto>(req.data)
                    }.getOrElse {
                        throw RequestError("注册数据格式错误")
                    }
                    validateRegisterSender(email, registerDto)
                    lgr.info { "received register operation mail targetId=$receiptId from=${email.from}" }
                    PlayerService.addAccount(registerDto)
                    ReceiptService.changeMsg(receiptId,"注册完成")
                }
                "resetPwd" -> {
                    val resetDto = runCatching {
                        serdesJson.decodeFromJsonElement<RAccount.ResetPasswordByQqMailDto>(req.data)
                    }.getOrElse {
                        throw RequestError("重置密码数据格式错误")
                    }
                    val senderQq = extractSenderQq(email.from)
                        ?: throw RequestError("resetPwd邮件发件人必须是QQ邮箱")
                    lgr.info { "received resetPwd operation targetId=$receiptId from=${email.from}" }
                    PlayerService.resetPasswordByQqMail(resetDto, senderQq)
                    ReceiptService.changeMsg(receiptId,"密码重置完成")
                }

                else -> {
                    throw RequestError ( "未知邮件操作 ${req.opr} from ${email.from}" )
                }
            }
        }.getOrElse {
            lgr.error(it){"处理错误"}
            ReceiptService.changeMsg(receiptId,it.message?:"未知错误")
        }
    }

    private fun extractOperationPayload(rawContent: String): String {
        val normalized = rawContent.trim()
        val lines = htmlToText(normalized)
            .lineSequence()
            .map(::sanitizeCipherLine)
            .toList()

        val candidates = mutableListOf<String>()
        val current = StringBuilder()

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                candidates += current.toString()
                current.setLength(0)
            }
        }

        lines.forEach { line ->
            if (line.isCipherFragment()) {
                current.append(line)
            } else {
                flushCurrent()
            }
        }
        flushCurrent()

        return candidates
            .maxByOrNull { it.length }
            ?.takeIf { it.isNotBlank() }
            ?: normalized
    }

    private fun parseOperationReceiptId(subject: String): ObjectId? {
        val normalized = subject.trim()
        val prefix = "${config.operationSubject}-"
        if (!normalized.startsWith(prefix)) {
            return null
        }
        val idText = normalized.removePrefix(prefix)
        if (!ObjectId.isValid(idText)) {
            lgr.warn { "skip mail with invalid operation id: $subject" }
            return null
        }
        return ObjectId(idText)
    }

    private fun validateOperationSender(email: ReceivedEmail) {
        val senderEmails = extractSenderEmails(email.from)
        if (senderEmails.isEmpty()) {
            throw RequestError("operation邮件缺少发件人地址")
        }
        if (senderEmails.any { !it.endsWith("@qq.com") }) {
            throw RequestError("只允许@qq.com发件人发送operation邮件")
        }
    }

    private fun validateRegisterSender(email: ReceivedEmail, registerDto: RAccount.RegisterDto) {
        val senderQq = extractSenderQq(email.from)
            ?: throw RequestError("没有在发件人地址${email.from}中找到有效QQ号")
        if (senderQq != registerDto.qq) {
            throw RequestError("注册邮件发件人QQ与注册QQ不一致")
        }
    }

    private fun extractSenderQq(from: List<String>): String? {
        return extractSenderEmails(from).asSequence()
            .mapNotNull { address ->
                val parts = address.split('@')
                if (parts.size != 2) return@mapNotNull null
                val localPart = parts[0]
                val domain = parts[1]
                if (domain != "qq.com" || localPart.isBlank() || !localPart.all(Char::isDigit)) {
                    return@mapNotNull null
                }
                localPart
            }
            .firstOrNull()
    }

    private fun extractSenderEmails(from: List<String>): List<String> {
        return from.asSequence()
            .flatMap { raw ->
                runCatching { InternetAddress.parse(raw) }
                    .getOrDefault(emptyArray())
                    .asSequence()
            }
            .mapNotNull { address ->
                address.address?.trim()?.lowercase()
            }
            .toList()
    }

    private fun Message.toReceivedEmail(folder: Folder): ReceivedEmail {
        val uid = (folder as? UIDFolder)?.getUID(this)
        return ReceivedEmail(
            uid = uid,
            messageNumber = messageNumber,
            subject = subject.orEmpty(),
            from = from?.map { address ->
                (address as? InternetAddress)?.toUnicodeString() ?: address.toString()
            }.orEmpty(),
            sentAtMillis = sentDate?.time,
            receivedAtMillis = receivedDate?.time,
            contentType = contentType.orEmpty(),
            text = extractText(this).trim()
        )
    }

    private fun extractText(part: Part): String {
        if (part.isMimeType("text/plain")) {
            return part.content?.toString().orEmpty()
        }
        if (part.isMimeType("text/html")) {
            return htmlToText(part.content?.toString().orEmpty())
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as? Multipart ?: return ""
            val plainTexts = buildList {
                for (index in 0 until multipart.count) {
                    val bodyPart = multipart.getBodyPart(index)
                    if (bodyPart.isMimeType("text/plain")) {
                        val text = extractText(bodyPart).trim()
                        if (text.isNotEmpty()) {
                            add(text)
                        }
                    }
                }
            }
            if (plainTexts.isNotEmpty()) {
                return plainTexts.joinToString("\n\n")
            }
            val htmlTexts = buildList {
                for (index in 0 until multipart.count) {
                    val bodyPart = multipart.getBodyPart(index)
                    val text = extractText(bodyPart).trim()
                    if (text.isNotEmpty()) {
                        add(text)
                    }
                }
            }
            return htmlTexts.joinToString("\n\n")
        }
        return ""
    }

    private fun htmlToText(content: String): String {
        return content
            .replace("\r", "")
            .replace(htmlBreakRegex, "\n")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .replace(htmlTagRegex, "\n")
    }

    private fun sanitizeCipherLine(line: String): String {
        return line.trim()
            .trim('"', '\'', '>', '|')
            .replace(" ", "")
    }

    private fun String.isCipherFragment(): Boolean =
        length >= 24 && cipherFragmentRegex.matches(this)
}
