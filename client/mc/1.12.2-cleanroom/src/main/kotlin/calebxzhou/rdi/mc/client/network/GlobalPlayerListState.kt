package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList
import kotlin.concurrent.Volatile

object GlobalPlayerListState {
    @Volatile
    private var current = RGlobalPlayerList(0L, emptyList())

    @JvmStatic
    fun current(): RGlobalPlayerList = current

    fun update(playerList: RGlobalPlayerList?) {
        playerList?.let { current = it }
    }
}
