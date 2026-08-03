package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import kotlin.concurrent.Volatile

object GlobalPlayerListState {
    @Volatile
    private var current = RGlobalPlayerList(0L, emptyList())

    @JvmStatic
    fun current(): RGlobalPlayerList = current

    fun update(playerList: RGlobalPlayerList?) {
        if (playerList == null) {
            return
        }
        current = playerList
    }
}
