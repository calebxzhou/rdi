package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState.remove
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.rcmd.tpa.TpaService.removeRelated
import calebxzhou.rdi.mc.firmsection.FirmSectionSetStatus
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112
import calebxzhou.rdi.mc.server.network.RServerNetwork.register
import calebxzhou.rdi.mc.server.network.RServerNetwork.sendFirmSectionsTo
import calebxzhou.rdi.mc.server.network.RServerNetwork.sendLastTo
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * calebxzhou @ 2026-04-18 17:47
 */
@Mod(modid = "rdi", name = "rdi", version = "1", acceptableRemoteVersions = "*")
class RDIMain {
    init {
        register()
        MinecraftForge.EVENT_BUS.register(this)
    }

    @Mod.EventHandler
    fun started(e: FMLServerStartedEvent?) {
        applyGameRules()

        WebSocketClient.start(WsHandler1122(server!!))
    }

    @Mod.EventHandler
    fun starting(e: FMLServerStartingEvent) {
        server = e.getServer() as DedicatedServer?
    }

    @Mod.EventHandler
    fun stopped(e: FMLServerStoppedEvent?) {
        WebSocketClient.stop()
        PlayerChatRangeState.clear()
        TpaService.clear()
        server = null
    }

    @SubscribeEvent
    fun onPlayerJoin(e: PlayerLoggedInEvent) {
        val player = e.player as EntityPlayerMP
        if (RDI.isAllOp()) {
            player.server.getPlayerList().addOp(player.getGameProfile())
        }
        sendLastTo(player)
        sendFirmSectionsTo(player)
    }

    @SubscribeEvent
    fun onPlayerLogout(e: PlayerLoggedOutEvent) {
        remove(e.player.getUniqueID())
        removeRelated(e.player.getUniqueID())
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.PlaceEvent) {
        if (event.isCanceled || event.world.isRemote) {
            return
        }
        val player = event.player as? EntityPlayerMP ?: return
        if (event.world.getTileEntity(event.pos) == null || !FirmSectionService112.isAutoSetEnabled(player)) {
            return
        }
        val result = FirmSectionService112.set(player, event.world, event.pos)
        if (result.status == FirmSectionSetStatus.ADDED) {
            player.sendMessage(TextComponentString("放置方块实体的位置已设为持久子区块"))
        }
    }

    companion object {
        private val lgr: Logger = LogManager.getLogger("rdi")
        private var server: DedicatedServer? = null

        private fun applyGameRules() {
            for (world in DimensionManager.getWorlds()) {
                if (world == null) {
                    continue
                }
                for (key in world.getGameRules().getRules()) {
                    val envValue = System.getenv("GAME_RULE_" + key)
                    if (envValue == null || envValue.isEmpty()) {
                        continue
                    }
                    world.getGameRules().setOrCreateGameRule(key, envValue)
                    lgr.info("SET GAME RULE {}={} dim={}", key, envValue, world.provider.getDimension())
                }
            }
        }
    }
}
