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
import calebxzhou.rdi.mc.server.world.TerrainCache112
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.network.play.server.SPacketTitle
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.event.ClickEvent
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
        val result = FirmSectionService112.set(player, event.world, event.pos)
        if (result.status == FirmSectionSetStatus.ADDED) {
            player.sendMessage(TextComponentString("放置方块实体的位置已设为持久子区块"))
        }
    }

    companion object {
        private val lgr: Logger = LogManager.getLogger("rdi")
        private var server: DedicatedServer? = null
        private const val MANUAL_URL = "https://craftrdi.feishu.cn/wiki/U8LRwMpUliuxW5kZLvCcxonNnkd"
        private const val JOIN_MESSAGE_DELAY_TICKS = 100
        private val pendingJoinMessages = mutableMapOf<UUID, Int>()

        private fun sendJoinMessages(player: EntityPlayerMP) {
            val range = PlayerChatRangeState.get(player.uniqueID)
            val result = FirmSectionService112.list(player)
            player.connection.sendPacket(SPacketTitle(10, 200, 20))
            player.connection.sendPacket(
                SPacketTitle(
                    SPacketTitle.Type.SUBTITLE,
                    TextComponentString("设定“持久子区块” 否则丢数据 见说明书")
                )
            )
            player.connection.sendPacket(SPacketTitle(SPacketTitle.Type.TITLE, TextComponentString("")))
            player.sendMessage(TextComponentString("当前聊天范围：${range.displayName}"))
            player.sendMessage(
                TextComponentString(
                    "6月22日开始 只有“持久子区块”会永久保存 其余区域有随时被清除的可能\n" +
                        "你设定了${result.playerCount}个 本存档已设定${result.total}个 详情阅读说明书"
                )
            )
            player.sendMessage(TextComponentString("点此打开RDI说明书").also {
                it.style.setUnderlined(true)
                    .setColor(TextFormatting.AQUA)
                    .setClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, MANUAL_URL))
            })
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
