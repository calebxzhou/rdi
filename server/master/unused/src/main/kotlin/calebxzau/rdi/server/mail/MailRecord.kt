package calebxzau.rdi.server.mail

import calebxzhou.rdi.common.model.MailKind
import java.util.UUID

data class MailRecord(
    val id: UUID,
    val senderId: UUID?,
    val receiverId: UUID,
    val title: String,
    val content: String,
    val kind: MailKind,
    val referenceId: UUID?,
    val unread: Boolean,
    val createdAt: Long,
)
