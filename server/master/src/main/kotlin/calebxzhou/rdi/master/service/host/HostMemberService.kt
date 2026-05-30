package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.master.service.PlayerService
import calebxzhou.rdi.model.Role
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import java.util.concurrent.ConcurrentHashMap

object HostMemberService {
    private val dbcl get() = HostService.dbcl
    private val memberMutationLocks = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> withMemberMutationLocks(
        vararg keys: String,
        block: suspend () -> T
    ): T {
        val locks = keys.distinct()
            .sorted()
            .map { memberMutationLocks.computeIfAbsent(it) { Mutex() } }

        suspend fun acquire(index: Int): T {
            if (index >= locks.size) return block()
            return locks[index].withLock {
                acquire(index + 1)
            }
        }

        return acquire(0)
    }

    suspend fun HostContext.delMember() {
        if (targetMember.role.level <= Role.ADMIN.level && member.role != Role.OWNER) {
            throw RequestError("无法踢出管理员")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.pull(Host::members.name, eq("id", targetMember.id))
        )
    }

    suspend fun HostContext.transferOwnership() {
        val current = HostQueryService.getById(host._id) ?: throw RequestError("无此房间")
        val recipient = targetMember
        if (current.ownerId == recipient.id) throw RequestError("不能转给自己")
        if (!getTargetPlayer().hasMsid) throw RequestError("找不到对方的微软账号")
        val previousOwner = current.members.find { it.id == current.ownerId }
            ?: throw RequestError("当前拥有者不在成员列表")
        val hasRecipient = current.members.any { it.id == recipient.id }
        if (!hasRecipient) throw RequestError("目标成员不在主机成员列表中")

        val updatedMembers = current.members.map { member ->
            when (member.id) {
                previousOwner.id -> member.copy(role = Role.ADMIN)
                recipient.id -> member.copy(role = Role.OWNER)
                else -> member
            }
        }

        dbcl.updateOne(
            eq("_id", current._id),
            combine(
                set(Host::ownerId.name, recipient.id),
                set(Host::members.name, updatedMembers)
            )
        )
    }

    suspend fun HostContext.addMember(qq: String) {
        val target = PlayerService.getByQQ(qq) ?: throw RequestError("无此账号")
        withMemberMutationLocks("host:${host._id}", "player:${target._id}") {
            val current = HostQueryService.getById(host._id) ?: throw RequestError("无此房间")
            if (current.members.any { it.id == target._id }) {
                throw RequestError("该用户已是成员")
            }
            if (!current.isPublic && current.members.size >= 10) {
                throw RequestError("该房间最多只能有10名成员")
            }
            val joinedCount = dbcl.countDocuments(eq("${Host::members.name}.${Host.Member::id.name}", target._id))
            if (joinedCount >= 10) {
                throw RequestError("该用户已加入9张房间，无法继续加入")
            }
            dbcl.updateOne(
                eq("_id", current._id),
                Updates.push(Host::members.name, Host.Member(target._id, Role.MEMBER))
            )
        }
    }

    suspend fun HostContext.setRole(role: Role) {
        if (targetMember.role == role) {
            throw RequestError("角色未更改")
        }
        if (targetMember.role == Role.OWNER) {
            throw RequestError("无法更改拥有者角色")
        }
        if (role == Role.OWNER) {
            throw RequestError("请使用转移拥有者来指定新的拥有者")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            combine(
                set("${Host::members.name}.$[elem].${Host.Member::role.name}", role),
            ),
            UpdateOptions().arrayFilters(
                listOf(
                    Document("elem.id", targetMember.id),
                )
            )
        )
    }

    suspend fun HostContext.quit() {
        if (host.members.none { it.id == player._id }) {
            throw RequestError("你不是此房间成员")
        }
        if (host.ownerId == player._id) {
            throw RequestError("拥有者无法退出房间")
        }
        dbcl.updateOne(
            eq("_id", host._id),
            Updates.pull(Host::members.name, Document(Host.Member::id.name, player._id))
        )
    }
}
