package calebxzhou.rdi.mc.rcmd.tpa

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TpaService {
    private const val EXPIRE_MS = 60000L
    private val REQUESTS_BY_TARGET: ConcurrentHashMap<UUID, TpaRequest> = ConcurrentHashMap<UUID, TpaRequest>()

    fun request(requester: TpaPlayer, targetName: String, lookup: TpaPlayerLookup): TpaResult {
        val target = lookup.findByName(targetName)
        if (target == null) {
            return TpaResult.Companion.error("玩家" + targetName + "不在线")
        }
        if (requester.id() == target.id()) {
            return TpaResult.Companion.error("不能向自己发送传送请求")
        }
        REQUESTS_BY_TARGET.put(
            target.id(),
            TpaRequest(requester.id(), requester.name(), target.name(), System.currentTimeMillis())
        )
        target.sendMessage(requester.name() + "请求传送到你身边，输入\\tpok接受，60秒内有效")
        return TpaResult.Companion.ok("已向" + target.name() + "发送传送请求，60秒内有效")
    }

    fun accept(target: TpaPlayer, lookup: TpaPlayerLookup): TpaResult {
        val request = REQUESTS_BY_TARGET.remove(target.id())
        if (request == null) {
            return TpaResult.Companion.error("没有待处理的传送请求")
        }
        if (request.isExpired) {
            return TpaResult.Companion.error("传送请求已过期")
        }
        val requester = lookup.findById(request.requesterId)
        if (requester == null) {
            return TpaResult.Companion.error("请求玩家已离线")
        }
        lookup.teleportTo(requester, target)
        requester.sendMessage(request.targetName + "已接受你的传送请求")
        return TpaResult.Companion.ok("已接受" + request.requesterName + "的传送请求")
    }

    @JvmStatic
    fun removeRelated(playerId: UUID) {
        REQUESTS_BY_TARGET.entries.removeIf { entry: MutableMap.MutableEntry<UUID, TpaRequest> -> entry!!.key == playerId || entry.value!!.requesterId == playerId }
    }

    @JvmStatic
    fun clear() {
        REQUESTS_BY_TARGET.clear()
    }

    
    private data class TpaRequest(
        val requesterId: UUID,
        val requesterName: String,
        val targetName: String,
        val createdAt: Long
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - createdAt > EXPIRE_MS
    }
}
