package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.ChatRangeStore
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.Locale

class PlayerNbtChatRangeStore(private val player: ServerPlayer) : ChatRangeStore {
    override fun load(): ChatRange? =
        ChatRange.fromStoredValue(rdiTag().getString(CHAT_RANGE_TAG))

    override fun save(range: ChatRange) {
        val persistedTag = player.persistentData.getCompound(Player.PERSISTED_NBT_TAG)
        val rdiTag = persistedTag.getCompound(RDI_TAG)
        rdiTag.putString(CHAT_RANGE_TAG, range.name.lowercase(Locale.ROOT))
        persistedTag.put(RDI_TAG, rdiTag)
        player.persistentData.put(Player.PERSISTED_NBT_TAG, persistedTag)
    }

    private fun rdiTag() =
        player.persistentData
            .getCompound(Player.PERSISTED_NBT_TAG)
            .getCompound(RDI_TAG)

    private companion object {
        const val RDI_TAG = "rdi"
        const val CHAT_RANGE_TAG = "chatRange"
    }
}
