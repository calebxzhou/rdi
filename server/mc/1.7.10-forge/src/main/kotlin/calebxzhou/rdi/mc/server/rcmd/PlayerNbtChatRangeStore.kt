package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.chat.ChatRange
import calebxzhou.rdi.mc.rcmd.chat.ChatRangeStore
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import java.util.Locale

class PlayerNbtChatRangeStore(private val player: EntityPlayerMP) : ChatRangeStore {
    override fun load(): ChatRange? =
        ChatRange.fromStoredValue(rdiTag().getString(CHAT_RANGE_TAG))

    override fun save(range: ChatRange) {
        val persistedTag = player.entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
        val rdiTag = persistedTag.getCompoundTag(RDI_TAG)
        rdiTag.setString(CHAT_RANGE_TAG, range.name.lowercase(Locale.ROOT))
        persistedTag.setTag(RDI_TAG, rdiTag)
        player.entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedTag)
    }

    private fun rdiTag() =
        player.entityData
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getCompoundTag(RDI_TAG)

    private companion object {
        const val RDI_TAG = "rdi"
        const val CHAT_RANGE_TAG = "chatRange"
    }
}
