package calebxzhou.rdi.prox

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.common.net.ktorClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced handler for frontend connections with simplified port-based routing
 * Client sends 2 bytes (big-endian port) at connection start, then all data is forwarded
 */
class DynamicProxyFrontendHandler(
    private val defaultBackendHost: String,
    private val defaultBackendPort: Int,
    private val backendGroup: EventLoopGroup
) : ChannelInboundHandlerAdapter() {

    companion object {
        val ATTR_PROTOCOL_VER = AttributeKey.valueOf<Int>("protocolVer")
        // Track all active client connections for status response
        val activeConnections: MutableSet<Channel> = ConcurrentHashMap.newKeySet()
        
        // Cached favicon as base64 data URI (loaded once at startup)
        val faviconDataUri: String? by lazy {
            loadFavicon()
        }
        
        private fun loadFavicon(): String? {
            val faviconFile = File("favicon.png")
            return if (faviconFile.exists() && faviconFile.isFile) {
                try {
                    val bytes = faviconFile.readBytes()
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    "data:image/png;base64,$base64".also {
                        lgr.debug { "Loaded favicon.png (${bytes.size} bytes)" }
                    }
                } catch (e: Exception) {
                    lgr.warn { "Failed to load favicon.png: ${e.message}" }
                    null
                }
            } else {
                lgr.debug { "No favicon.png found in working directory" }
                null
            }
        }
    }

    private var backendChannel: Channel? = null
    private var currentBackendHost: String = defaultBackendHost
    private var currentBackendPort: Int = defaultBackendPort
    private val pendingBuffer = mutableListOf<Any>()
    private var handshakeReceived = false
    private var isStatusRequest = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        activeConnections.add(ctx.channel())
        lgr.info { "Client connected, waiting for Minecraft handshake. Active connections: ${activeConnections.size}" }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (!handshakeReceived) {
            val buffer = msg as ByteBuf
            try {
                handleHandshake(ctx, buffer)
            } catch (e: Exception) {
                lgr.error { "Error parsing handshake" + "\n" + e }
                ctx.channel().close()
            } finally {
                ReferenceCountUtil.release(msg)
            }
            return
        }

        // Handle status request packets locally
        if (isStatusRequest) {
            val buffer = msg as ByteBuf
            try {
                handleStatusPacket(ctx, buffer)
            } catch (e: Exception) {
                lgr.error { "Error handling status packet" + "\n" + e }
                ctx.channel().close()
            } finally {
                ReferenceCountUtil.release(msg)
            }
            return
        }

        // After handshake (login), forward all data normally
        forwardToBackend(ctx, msg)
    }

    private suspend fun getHostStatus(port: Int): Result<HostStatus> = runCatching {
        //test
        if(port==25565){
            return@runCatching HostStatus.PLAYABLE
        }
        withTimeoutOrNull(1000L) {
            ktorClient.get("$MASTER_URL/host/status?port=$port").body<Response<HostStatus?>>().run {
                data ?: run {
                    lgr.info { "host $port status fail: ${msg}" }
                    throw RequestError("无法获取地图状态：$msg")
                }
            }
        } ?: throw RequestError("无法获取地图状态：请求超时")
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        // Buffer contains [Length + PacketID + Protocol + Host + Port + State]
        // We need to parse this non-destructively to extract the port
        val parser = buffer.slice()

        // 1. Skip Packet Length (VarInt)
        readVarInt(parser)

        // 2. Read Packet ID (VarInt) - must be 0x00 for Handshake
        val packetId = readVarInt(parser)

        if (packetId == 0) {
            // 3. Protocol Version (VarInt)
            val protocolVersion = readVarInt(parser)

            // 4. Skip Hostname (String = VarInt Len + Bytes)
            val hostLen = readVarInt(parser)
            parser.skipBytes(hostLen)

            // 5. Read Port (UShort)
            val port = parser.readUnsignedShort()
            val nextState = readVarInt(parser)
            handshakeReceived = true
            ctx.channel().attr(ATTR_PROTOCOL_VER).set(protocolVersion)
            lgr.info { "Handshake received: Port $port Version $protocolVersion NextState $nextState" }

            // Handle status request (Server List Ping)
            if (nextState == 1) {
                isStatusRequest = true
                lgr.info { "Status request detected, will respond locally" }
                // Keep the frame decoder for status packets
                return
            }

            // nextState == 2: Login flow - determine backend based on port
            if (port in 50000..59999 || port == 25565) {
                val status = runBlocking {
                    getHostStatus(port).getOrElse {
                        disconnectPlayerWithReason(
                            ctx.channel(),
                            it.message ?: ""
                        ); HostStatus.UNKNOWN
                    }
                }
                if (status != HostStatus.PLAYABLE) {
                    disconnectPlayerWithReason(
                        ctx.channel(), when (status) {
                            HostStatus.STOPPED -> "地图未启动，请先启动地图"
                            HostStatus.STARTED -> "地图尚未准备好，请稍等一会"
                            else -> "无法连接地图，请稍后再试"
                        }
                    )
                    return
                }
                lgr.info { "Connecting to backend 127.0.0.1:$port " }
                currentBackendHost = "127.0.0.1"
                currentBackendPort = port
                connectToBackend(ctx)

                // Forward the handshake packet
                forwardToBackend(ctx, buffer.retain())

                // Remove the frame decoder so subsequent encrypted/compressed packets flow raw
                ctx.pipeline().remove(MinecraftFrameDecoder::class.java)
                lgr.info { "Removed MinecraftFrameDecoder, switching to raw forwarding" }
            } else {
                val reason = "Invalid port $port (must be 50000-59999)"
                lgr.info { reason + ", closing connection" }
                ctx.channel().attr(ATTR_PROTOCOL_VER).set(protocolVersion)
                disconnectPlayerWithReason(ctx.channel(), reason)
            }
        } else {
            lgr.info { "Expected Handshake (0x00) but got $packetId, closing" }
            ctx.channel().close()
        }
    }


    private fun readVarInt(buffer: ByteBuf): Int {
        var value = 0
        var position = 0
        var currentByte: Byte

        while (true) {
            currentByte = buffer.readByte()
            value = value or ((currentByte.toInt() and 0x7F) shl position)

            if ((currentByte.toInt() and 0x80) == 0) break

            position += 7

            if (position >= 32) throw RuntimeException("VarInt is too big")
        }

        return value
    }


    private fun connectToBackend(ctx: ChannelHandlerContext) {
        val frontendChannel = ctx.channel()

        // Don't attempt backend connection if frontend is already closed
        if (!frontendChannel.isActive) {
            lgr.info { "Frontend channel closed, aborting backend connection" }
            return
        }

        // Create connection to backend server
        val bootstrap = Bootstrap()
        bootstrap.group(backendGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(ProxyBackendHandler(frontendChannel))

        val future = bootstrap.connect(currentBackendHost, currentBackendPort)
        backendChannel = future.channel()

        future.addListener { connectFuture ->
            // Check if frontend is still active before proceeding
            if (!frontendChannel.isActive) {
                lgr.info { "Frontend disconnected during backend connection, closing backend" }
                if (connectFuture.isSuccess) {
                    future.channel().close()
                }
                // Release any pending buffered data
                pendingBuffer.forEach { bufferedMsg ->
                    ReferenceCountUtil.release(bufferedMsg)
                }
                pendingBuffer.clear()
                return@addListener
            }

            if (connectFuture.isSuccess) {
                lgr.info { "Connected to backend: $currentBackendHost:$currentBackendPort" }

                // Send any pending buffered data immediately
                if (pendingBuffer.isNotEmpty()) {
                    val compositeBuf = ctx.alloc().compositeBuffer(pendingBuffer.size)
                    pendingBuffer.forEach { bufferedMsg ->
                        if (bufferedMsg is ByteBuf) {
                            compositeBuf.addComponent(true, bufferedMsg)
                        }
                    }
                    pendingBuffer.clear()
                    if (compositeBuf.isReadable) {
                        future.channel().writeAndFlush(compositeBuf)
                    } else {
                        compositeBuf.release()
                    }
                }
            } else {
                // Connection failed, close frontend
                lgr.info { "Failed to connect to backend: ${connectFuture.cause()?.message}" }

                // Release any pending buffered data
                pendingBuffer.forEach { bufferedMsg ->
                    ReferenceCountUtil.release(bufferedMsg)
                }
                pendingBuffer.clear()

                frontendChannel.close()
            }
        }
    }

    private fun forwardToBackend(ctx: ChannelHandlerContext, msg: Any) {
        val frontendChannel = ctx.channel()

        // Check if frontend is still active
        if (!frontendChannel.isActive) {
            ReferenceCountUtil.release(msg)
            return
        }

        if (backendChannel?.isActive == true) {
            // Forward data to backend immediately with flush
            backendChannel?.writeAndFlush(msg)?.addListener { future ->
                if (!future.isSuccess) {
                    // Write failed, close connection
                    lgr.info { "Failed to write to backend: ${future.cause()?.message}" }
                    ctx.channel().close()
                }
            }
        } else {
            // Backend not available or not yet chosen: buffer the message
            pendingBuffer.add(msg)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        activeConnections.remove(ctx.channel())
        lgr.info { "Client disconnected. Active connections: ${activeConnections.size}" }
        if (backendChannel?.isActive == true) {
            closeOnFlush(backendChannel!!)
        }
        // Release any pending buffered data
        pendingBuffer.forEach { bufferedMsg ->
            ReferenceCountUtil.release(bufferedMsg)
        }
        pendingBuffer.clear()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        lgr.info { "Frontend exception: ${cause.message}" }
        closeOnFlush(ctx.channel())
    }

    private fun closeOnFlush(ch: Channel) {
        if (ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun disconnectPlayerWithReason(ch: Channel, reason: String) {
        val protocolVersion = ch.attr(ATTR_PROTOCOL_VER).get() ?: 0
        val packetId = 0//if (protocolVersion > 385) 0x01 else 0x00
        val reasonJson = "{\"text\":\"$reason\"}"

        val buffer = ch.alloc().buffer()
        val dataBuffer = ch.alloc().buffer()

        try {
            writeVarInt(packetId, dataBuffer)
            writeString(reasonJson, dataBuffer)

            writeVarInt(dataBuffer.readableBytes(), buffer)
            buffer.writeBytes(dataBuffer)

            ch.writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE)
        } finally {
            dataBuffer.release()
        }
    }

    private fun writeVarInt(value: Int, buffer: ByteBuf) {
        var v = value
        while ((v and -128) != 0) {
            buffer.writeByte(v and 127 or 128)
            v = v ushr 7
        }
        buffer.writeByte(v)
    }

    private fun writeString(value: String, buffer: ByteBuf) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size, buffer)
        buffer.writeBytes(bytes)
    }

    /**
     * Handle status request packets (Server List Ping)
     * Packet 0x00 = Status Request, Packet 0x01 = Ping Request
     */
    private fun handleStatusPacket(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        val parser = buffer.slice()
        
        // Read packet length (VarInt)
        readVarInt(parser)
        
        // Read packet ID
        val packetId = readVarInt(parser)
        
        when (packetId) {
            0x00 -> {
                // Status Request - respond with server status JSON
                lgr.info { "Received status request, sending response" }
                sendStatusResponse(ctx)
            }
            0x01 -> {
                // Ping Request - echo back the payload
                val payload = parser.readLong()
                lgr.info { "Received ping request with payload $payload" }
                sendPingResponse(ctx, payload)
            }
            else -> {
                lgr.info { "Unknown status packet ID: $packetId" }
                ctx.channel().close()
            }
        }
    }

    /**
     * Send the server status response JSON
     * Format: https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#Status_Response
     */
    private fun sendStatusResponse(ctx: ChannelHandlerContext) {
        val protocolVersion = ctx.channel().attr(ATTR_PROTOCOL_VER).get() ?: 0
        val onlinePlayers = activeConnections.size
        
        // Build player sample from active connections
        val playerSamples = activeConnections.mapNotNull { channel ->
            val remoteAddress = channel.remoteAddress()
            if (remoteAddress is InetSocketAddress) {
                val ip = remoteAddress.address.hostAddress
                val port = remoteAddress.port
                val maskedName = maskIpAddress(ip, port)
                val uuid = UUID.nameUUIDFromBytes("$ip:$port".toByteArray(Charsets.UTF_8))
                """{"name":"$maskedName","id":"$uuid"}"""
            } else null
        }.joinToString(",")
        
        // Build favicon field if available
        val faviconField = faviconDataUri?.let { """"favicon":"$it",""" } ?: ""
        
        val statusJson = """{${faviconField}"version":{"name":"RDI Proxy","protocol":$protocolVersion},"players":{"max":88888,"online":$onlinePlayers,"sample":[$playerSamples]},"description":{"text":"RDI Universal Proxy Server"}}"""
        
        lgr.debug { "Sending status response: $statusJson" }
        
        val buffer = ctx.alloc().buffer()
        val dataBuffer = ctx.alloc().buffer()
        
        try {
            writeVarInt(0x00, dataBuffer)  // Packet ID for Status Response
            writeString(statusJson, dataBuffer)
            
            writeVarInt(dataBuffer.readableBytes(), buffer)
            buffer.writeBytes(dataBuffer)
            
            ctx.writeAndFlush(buffer.retain())
        } finally {
            dataBuffer.release()
            buffer.release()
        }
    }

    /**
     * Mask IP address to show only first octet: 192.168.1.100:12345 -> 192.*.*.*:12345
     */
    private fun maskIpAddress(ip: String, port: Int): String {
        val parts = ip.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.*.*.*:$port"
        } else {
            // IPv6 or other format - just show first segment
            val firstSegment = ip.split(":").firstOrNull() ?: ip
            "$firstSegment:***:$port"
        }
    }

    /**
     * Send ping response with the same payload (echo)
     */
    private fun sendPingResponse(ctx: ChannelHandlerContext, payload: Long) {
        val buffer = ctx.alloc().buffer()
        val dataBuffer = ctx.alloc().buffer()
        
        try {
            writeVarInt(0x01, dataBuffer)  // Packet ID for Ping Response
            dataBuffer.writeLong(payload)
            
            writeVarInt(dataBuffer.readableBytes(), buffer)
            buffer.writeBytes(dataBuffer)
            
            ctx.writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE)
        } finally {
            dataBuffer.release()
        }
    }
}
