package calebxzhou.rdi.mc.server.firmsection

import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = "rdi")
object FirmSectionEvents {
    @SubscribeEvent
    @JvmStatic
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        if (event.isCanceled) {
            return
        }
        val player = event.entity as? ServerPlayer ?: return
        val level = event.level as? ServerLevel ?: return
        if (!event.placedBlock.hasBlockEntity() || !FirmSectionService.isAutoSetEnabled(player)) {
            return
        }
        if (FirmSectionService.set(player, level, event.pos).status == FirmSectionSetStatus.ADDED) {
            player.sendSystemMessage(Component.literal("放置方块实体的位置已设为持久子区块"))
        }
    }
}
