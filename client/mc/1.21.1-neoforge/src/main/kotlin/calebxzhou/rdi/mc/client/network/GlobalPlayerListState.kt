package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList
import kotlin.concurrent.Volatile

object GlobalPlayerListState {
    @Volatile
    @JvmStatic
    var current = RGlobalPlayerList(0L, emptyList())
    private set



    fun update(playerList: RGlobalPlayerList) {
        current = playerList
    }
}
