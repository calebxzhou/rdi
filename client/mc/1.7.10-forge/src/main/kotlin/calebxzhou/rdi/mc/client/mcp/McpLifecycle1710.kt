package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.RDIClient
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpServer
import calebxzhou.rdi.mc.common.RDI
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent

object McpLifecycle1710 {
    @SubscribeEvent
    fun onClientConnect(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
        Search1710.refreshResourceIndex()
        McpServer.start(McpGameImpl1710, if (RDI.DEBUG) 25565 else null)
            .onFailure { RDIClient.LOG?.warn("Failed to start RMCP", it) }
        StandardMcpServer.start(McpGameImpl1710)
            .onFailure { RDIClient.LOG?.warn("Failed to start standard MCP", it) }
    }

    @SubscribeEvent
    fun onClientDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        McpServer.stop()
        StandardMcpServer.stop()
        RDI.FIRM_CHUNKS.clear()
    }
}
