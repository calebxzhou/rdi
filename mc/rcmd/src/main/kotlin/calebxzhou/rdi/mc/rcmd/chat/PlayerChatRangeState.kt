package calebxzhou.rdi.mc.rcmd.chat

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerChatRangeState {
    val DEFAULT_RANGE: ChatRange = ChatRange.GLOBAL
    private val CHAT_RANGES = ConcurrentHashMap<UUID, ChatRange>()

    fun set(playerId: UUID, range: ChatRange) {
        CHAT_RANGES[playerId] = range
    }

    @JvmStatic
    fun get(playerId: UUID?): ChatRange {
        return CHAT_RANGES.getOrDefault(playerId, DEFAULT_RANGE)
    }

    @JvmStatic
    fun isGlobal(playerId: UUID?): Boolean {
        return get(playerId) == ChatRange.GLOBAL
    }

    @JvmStatic
    fun remove(playerId: UUID) {
        CHAT_RANGES.remove(playerId)
    }

    @JvmStatic
    fun clear() {
        CHAT_RANGES.clear()
    }
}
