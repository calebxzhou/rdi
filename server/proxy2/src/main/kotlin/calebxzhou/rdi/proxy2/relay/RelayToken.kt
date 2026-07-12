package calebxzhou.rdi.proxy2.relay

object RelayToken {
    fun acceptDebugToken(sessionId: String, peerId: String): Boolean =
        sessionId.isNotBlank() && peerId.isNotBlank()
}
