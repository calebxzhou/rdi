package calebxzau.rdi.server.friend

import calebxzhou.rdi.common.exception.RequestError
import calebxzau.rdi.common.model.Friend
import calebxzau.rdi.common.model.FriendRequestStatus
import calebxzau.rdi.common.model.FriendTag
import calebxzhou.rdi.common.model.MailAction
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.service.PlayerService
import calebxzau.rdi.server.infra.PostgresRuntime
import java.util.UUID
import org.bson.types.ObjectId
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/** Account-level friend relationships and the private tags owned by each player. */
object FriendService {
    private const val FRIEND_LIMIT = 100
    private const val DAILY_REQUEST_LIMIT = 20
    private const val COOLDOWN = 60L * 60 * 1000
    private const val TAG_LIMIT = 20
    private const val TAGS_PER_FRIEND = 5

    @Volatile
    private var runtime: PostgresRuntime? = null

    fun configure(runtime: PostgresRuntime) {
        this.runtime = runtime
    }

    private fun configured() = runtime ?: error("PostgreSQL friend service has not been configured")

    suspend fun lookup(player: RAccount, rawQq: String): RAccount.Dto {
        val qq = normalizeQq(rawQq)
        val target = PlayerService.getByQQ(qq) ?: throw RequestError("未找到该玩家")
        if (target._id == player._id) throw RequestError("不能添加自己")
        return target.dto
    }

    suspend fun request(player: RAccount, rawQq: String) {
        val qq = normalizeQq(rawQq)
        val target = PlayerService.getByQQ(qq) ?: throw RequestError("未找到该玩家")
        if (target._id == player._id) throw RequestError("不能添加自己")
        val requester = player._id.toUUID()
        val receiver = target._id.toUUID()
        configured().database.transaction {
            lockPair(requester, receiver)
            val now = System.currentTimeMillis()
            configured().friendRepo.expireRequests(now)
            val friendship = configured().friendRepo.findFriendship(requester, receiver)
            if (friendship != null && friendship.removedAt == null) throw RequestError("你们已经是好友")
            if (friendship?.removedAt != null && now - friendship.removedAt < COOLDOWN) {
                throw RequestError("解除好友后1小时内不能再次申请")
            }
            val pending = configured().friendRepo.findPendingRequest(requester, receiver)
            if (pending != null) {
                if (pending.requesterId == requester) throw RequestError("好友申请已发送")
                acceptRequestInTransaction(pending, now)
                return@transaction
            }
            val latestRejected = configured().friendRepo.findLatestRequest(requester, receiver, FriendRequestStatus.Rejected)
            if (latestRejected?.handledAt != null && now - latestRejected.handledAt < COOLDOWN) {
                throw RequestError("拒绝好友申请后1小时内不能再次申请")
            }
            if (configured().friendRepo.countActiveFriendships(requester) >= FRIEND_LIMIT ||
                configured().friendRepo.countActiveFriendships(receiver) >= FRIEND_LIMIT) {
                throw RequestError("好友数量已达上限")
            }
            if (configured().friendRepo.countRequestsSince(requester, now - 24 * 60 * 60 * 1000) >= DAILY_REQUEST_LIMIT) {
                throw RequestError("过去24小时发送好友申请次数已达上限")
            }
            val request = configured().friendRepo.createRequest(requester, receiver)
            configured().mailRepo.create(
                senderId = requester,
                receiverId = receiver,
                title = "好友申请",
                content = "有玩家请求添加你为好友，请在邮件中处理",
                kind = calebxzhou.rdi.common.model.MailKind.FriendRequest,
                referenceId = request.id,
            )
        }
    }

    suspend fun list(player: RAccount): List<Friend> {
        val owner = player._id.toUUID()
        val (friendIds, tagsByFriend) = configured().database.transaction {
            val ids = configured().friendRepo.listActiveFriendships(owner)
                .map { if (it.playerLowId == owner) it.playerHighId else it.playerLowId }
            ids to configured().friendRepo.listTagsForFriends(owner, ids)
        }
        val accounts = PlayerService.getByIds(friendIds.map { it.objectId }).associateBy { it._id }
        return friendIds.mapNotNull { id ->
            val account = accounts[id.objectId] ?: return@mapNotNull null
            Friend(account._id, account.name, account.cloth, tagsByFriend[id].orEmpty().map { FriendTag(it.id, it.name) })
        }.sortedBy { it.name }
    }

    suspend fun remove(player: RAccount, friendId: ObjectId) {
        if (friendId == player._id) throw RequestError("不能删除自己")
        val owner = player._id.toUUID()
        val friend = friendId.toUUID()
        configured().database.transaction {
            lockPair(owner, friend)
            if (!configured().friendRepo.removeFriendship(owner, friend, System.currentTimeMillis())) {
                throw RequestError("不是好友")
            }
            configured().friendRepo.deleteAssignmentsForFriend(owner, friend)
            configured().friendRepo.deleteAssignmentsForFriend(friend, owner)
        }
    }

    suspend fun tags(player: RAccount): List<FriendTag> = configured().database.transaction {
        configured().friendRepo.listTags(player._id.toUUID()).map { FriendTag(it.id, it.name) }
    }

    suspend fun createTag(player: RAccount, rawName: String): FriendTag {
        val name = normalizeTag(rawName)
        return configured().database.transaction {
            val owner = player._id.toUUID()
            if (!configured().accountRepo.lock(owner)) throw RequestError("玩家不存在")
            val tags = configured().friendRepo.listTags(owner)
            if (tags.size >= TAG_LIMIT) throw RequestError("标签数量已达上限")
            if (tags.any { it.name.equals(name, ignoreCase = true) }) {
                throw RequestError("标签名称已存在")
            }
            val tag = configured().friendRepo.createTag(owner, name)
            FriendTag(tag.id, tag.name)
        }
    }

    suspend fun renameTag(player: RAccount, tagId: UUID, rawName: String): FriendTag {
        val name = normalizeTag(rawName)
        return configured().database.transaction {
            val owner = player._id.toUUID()
            if (!configured().accountRepo.lock(owner)) throw RequestError("玩家不存在")
            val tag = configured().friendRepo.findTag(tagId)
                ?.takeIf { it.ownerId == owner } ?: throw RequestError("标签不存在")
            if (configured().friendRepo.listTags(owner).any { it.id != tagId && it.name.equals(name, ignoreCase = true) }) {
                throw RequestError("标签名称已存在")
            }
            if (!configured().friendRepo.renameTag(tagId, owner, name)) throw RequestError("标签不存在")
            FriendTag(tagId, name)
        }
    }

    suspend fun deleteTag(player: RAccount, tagId: UUID) {
        configured().database.transaction {
            val owner = player._id.toUUID()
            if (!configured().accountRepo.lock(owner)) throw RequestError("玩家不存在")
            if (!configured().friendRepo.deleteTag(tagId, owner)) throw RequestError("标签不存在")
        }
    }

    suspend fun replaceTags(player: RAccount, friendId: ObjectId, tagIds: List<UUID>) {
        val owner = player._id.toUUID()
        val friend = friendId.toUUID()
        val ids = tagIds.distinct()
        if (ids.size > TAGS_PER_FRIEND) throw RequestError("单个好友最多设置5个标签")
        configured().database.transaction {
            lockPair(owner, friend)
            val friendship = configured().friendRepo.findFriendship(owner, friend)
            if (friendship == null || friendship.removedAt != null) throw RequestError("不是好友")
            val owned = configured().friendRepo.listTags(owner).map { it.id }.toSet()
            if (!ids.all { it in owned }) throw RequestError("只能使用自己的标签")
            configured().friendRepo.deleteAssignmentsForFriend(owner, friend)
            ids.forEach { configured().friendRepo.assignTag(owner, it, friend) }
        }
    }

    suspend fun handleMailAction(mailId: UUID, ownerId: ObjectId, action: MailAction) {
        val expired = configured().database.transaction {
            val mail = configured().mailRepo.findById(mailId)
                ?: throw RequestError("未找到对应邮件")
            val owner = ownerId.toUUID()
            if (mail.receiverId != owner || mail.kind != calebxzhou.rdi.common.model.MailKind.FriendRequest || mail.referenceId == null) {
                throw RequestError("邮件不可执行此操作")
            }
            val requestPreview = configured().friendRepo.findRequest(mail.referenceId)
                ?: throw RequestError("好友申请不存在")
            if (requestPreview.receiverId != owner) throw RequestError("无权处理此申请")
            lockPair(requestPreview.requesterId, requestPreview.receiverId)
            val now = System.currentTimeMillis()
            val lockedMail = configured().mailRepo.findByIdForUpdate(mailId)
                ?: throw RequestError("未找到对应邮件")
            if (lockedMail.receiverId != owner || lockedMail.referenceId != mail.referenceId) throw RequestError("邮件不可执行此操作")
            val request = configured().friendRepo.findRequestForUpdate(mail.referenceId)
                ?: throw RequestError("好友申请不存在")
            if (request.receiverId != owner) throw RequestError("无权处理此申请")
            if (request.status != FriendRequestStatus.Pending) throw RequestError("好友申请已处理")
            if (request.isExpired(now)) {
                configured().friendRepo.updateRequestStatus(request.id, FriendRequestStatus.Pending, FriendRequestStatus.Expired, now)
                return@transaction true
            }
            when (action) {
                MailAction.Accept -> acceptRequestInTransaction(request, now)
                MailAction.Reject -> {
                    configured().friendRepo.updateRequestStatus(request.id, FriendRequestStatus.Pending, FriendRequestStatus.Rejected, now)
                    configured().mailRepo.delete(mailId, owner)
                }
            }
            false
        }
        if (expired) throw RequestError("好友申请已过期")
    }

    internal fun JdbcTransaction.rejectRequestInTransaction(requestId: UUID, receiverId: UUID, now: Long) {
        val request = configured().friendRepo.findRequestForUpdate(requestId) ?: return
        if (request.receiverId != receiverId || request.status != FriendRequestStatus.Pending) return
        val target = if (request.isExpired(now)) FriendRequestStatus.Expired else FriendRequestStatus.Rejected
        configured().friendRepo.updateRequestStatus(requestId, FriendRequestStatus.Pending, target, now)
    }

    private fun JdbcTransaction.acceptRequestInTransaction(request: FriendRequestRecord, now: Long) {
        if (configured().friendRepo.countActiveFriendships(request.requesterId) >= FRIEND_LIMIT ||
            configured().friendRepo.countActiveFriendships(request.receiverId) >= FRIEND_LIMIT) {
            throw RequestError("好友数量已达上限")
        }
        if (!configured().friendRepo.updateRequestStatus(request.id, FriendRequestStatus.Pending, FriendRequestStatus.Accepted, now)) {
            throw RequestError("好友申请已处理")
        }
        configured().friendRepo.establishFriendship(request.requesterId, request.receiverId, now)
        configured().friendRepo.deleteAssignmentsForFriend(request.requesterId, request.receiverId)
        configured().mailRepo.create(
            senderId = null,
            receiverId = request.requesterId,
            title = "好友申请已通过",
            content = "你们已经成为好友",
            kind = calebxzhou.rdi.common.model.MailKind.System,
        )
    }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.lockPair(first: UUID, second: UUID) {
        val (low, high) = normalizedFriendPair(first, second)
        if (!configured().accountRepo.lock(low) || !configured().accountRepo.lock(high)) throw RequestError("玩家不存在")
    }

    private fun normalizeQq(raw: String): String {
        val qq = raw.trim()
        if (!qq.matches(Regex("\\d{5,10}"))) throw ParamError("QQ号格式无效")
        return qq
    }

    private fun normalizeTag(raw: String): String {
        val value = raw.trim()
        if (value.length !in 1..12) throw ParamError("标签名称长度需为1~12个字符")
        return value
    }
}
