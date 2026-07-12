package calebxzhou.rdi.mc.server

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.WebSocketClient
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState
import calebxzhou.rdi.mc.rcmd.chat.PlayerChatRangeState.remove
import calebxzhou.rdi.mc.rcmd.tpa.TpaService
import calebxzhou.rdi.mc.rcmd.tpa.TpaService.removeRelated
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112
import calebxzhou.rdi.mc.server.network.RServerNetwork.register
import calebxzhou.rdi.mc.server.network.RServerNetwork.sendFirmSectionsTo
import calebxzhou.rdi.mc.server.network.RServerNetwork.sendLastTo
import calebxzhou.rdi.mc.server.world.TerrainCache112
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
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.UUID

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
        TerrainCache112.closeAll()
        WebSocketClient.stop()
        PlayerChatRangeState.clear()
        TpaService.clear()
        pendingJoinMessages.clear()
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
        pendingJoinMessages[player.uniqueID] = player.server.getTickCounter() + JOIN_MESSAGE_DELAY_TICKS
    }

    @SubscribeEvent
    fun onPlayerLogout(e: PlayerLoggedOutEvent) {
        remove(e.player.getUniqueID())
        removeRelated(e.player.getUniqueID())
        pendingJoinMessages.remove(e.player.getUniqueID())
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }
        val currentServer = server ?: return
        val iterator = pendingJoinMessages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentServer.getTickCounter() < entry.value) {
                continue
            }
            iterator.remove()
            currentServer.playerList.getPlayerByUUID(entry.key)?.let(::sendJoinMessages)
        }
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
        FirmSectionService112.set(player, event.world, event.pos)
    }

    companion object {
        private val lgr: Logger = LogManager.getLogger("rdi")
        private var server: DedicatedServer? = null
        private const val JOIN_MESSAGE_DELAY_TICKS = 100
        private val pendingJoinMessages = mutableMapOf<UUID, Int>()

        private fun sendJoinMessages(player: EntityPlayerMP) {
            val range = PlayerChatRangeState.get(player.uniqueID)
            player.sendMessage(TextComponentString("当前聊天范围：${range.displayName}"))
        }

        private fun applyGameRules() {
            for (world in DimensionManager.getWorlds()) {
                if (world == null) {
                    continue
                }
                for (key in world.gameRules.getRules()) {
                    val envValue = System.getenv("GAME_RULE_$key")
                    if (envValue == null || envValue.isEmpty()) {
                        continue
                    }
                    world.gameRules.setOrCreateGameRule(key, envValue)
                    lgr.info("SET GAME RULE {}={} dim={}", key, envValue, world.provider.dimension)
                }
            }
        }
    }
}
