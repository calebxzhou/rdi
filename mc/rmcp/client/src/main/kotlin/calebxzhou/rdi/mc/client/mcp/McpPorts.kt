package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.McpInternalError
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

object McpPorts {
    private val loopback: InetAddress
        get() = InetAddress.getLoopbackAddress()

    fun selectAvailablePort(): Int {
        try {
            ServerSocket(0, 50, loopback).use { socket ->
                return socket.localPort
            }
        } catch (e: IOException) {
            throw McpInternalError()
        }
    }

    fun selectPersistentPort(file: File): Int {
        val savedPort = file.readPortOrNull()
        if (savedPort != null && savedPort.isAvailable()) {
            return savedPort
        }
        return selectAvailablePort()
    }

    fun Int.isAvailable(): Boolean {
        if (this !in 1..65535) {
            return false
        }
        return try {
            ServerSocket(this, 50, loopback).use { true }
        } catch (e: IOException) {
            false
        }
    }

    private fun File.readPortOrNull(): Int? {
        return runCatching {
            if (!isFile) return null
            readText().trim().toIntOrNull()
        }.getOrNull()
    }
}
