package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzau.util.netty.writeUtf8String
import calebxzau.util.netty.writeVarInt
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Response
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

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
    private var mcVersion: McVersion? = null

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
        withTimeoutOrNull(2000L.milliseconds) {
            ktorClient.get("$MASTER_URL/host/status?port=$port").body<Response<HostStatus?>>().run {
                data ?: run {
                    lgr.info { "host $port status fail: ${msg}" }
                    throw RequestError("无法获取房间状态：$msg")
                }
            }
        } ?: throw RequestError("无法获取房间状态：请求超时")
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        // Buffer contains [Length + PacketID + Protocol + Host + Port + State]
        // We need to parse this non-destructively to extract the port
        val parser = buffer.slice()

        // 1. Skip Packet Length (VarInt)
        parser.readVarInt()

        // 2. Read Packet ID (VarInt) - must be 0x00 for Handshake
        val packetId = parser.readVarInt()

        if (packetId == 0) {
            // 3. Protocol Version (VarInt)
            val protocolVersion = parser.readVarInt()

            // 4. Skip Hostname (String = VarInt Len + Bytes)
            val hostLen = parser.readVarInt()
            parser.skipBytes(hostLen)

            // 5. Read Port (UShort)
            val port = parser.readUnsignedShort()
            val nextState = parser.readVarInt()
            handshakeReceived = true
            ctx.channel().attr(ATTR_PROTOCOL_VER).set(protocolVersion)
            mcVersion = McVersion.fromProtocolVer(protocolVersion)
            lgr.info {
                "Handshake received: Port $port Version $protocolVersion " +
                        "McVersion ${mcVersion?.mcVer ?: "unsupported"} NextState $nextState"
            }

            // Handle status request (Server List Ping)
            if (nextState == 1) {
                isStatusRequest = true
                ctx.channel().disableZstdFrameEncoding()
                lgr.info { "Status request detected, will respond locally" }
                // Keep the frame decoder for status packets
                return
            }

            // nextState == 2: Login flow - determine backend based on port
            if (!ctx.channel().isZstdFrameEnabled()) {
                lgr.info { "Client login without zstd frame support, disconnecting" }
                disconnectPlayerWithReason(ctx.channel(), "you must update client")
                return
            }
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
                            HostStatus.STOPPED -> "请前往房间后台，点击启动按钮"
                            HostStatus.STARTED -> "房间启动中，请稍等"
                            else -> "无法连接房间，请稍后再试"
                        }
                    )
                    return
                }
                lgr.info { "Connecting to backend $currentBackendHost:$port " }
                currentBackendHost = "127.0.0.1"
                currentBackendPort = port
                installBandwidthLimiter(ctx, port)
                connectToBackend(ctx)

                // Forward the handshake packet
                forwardToBackend(ctx, buffer.retain())
            } else {
                val reason = "Invalid port $port (must be 50000-59999)"
                lgr.info { "$reason, closing connection" }
                ctx.channel().attr(ATTR_PROTOCOL_VER).set(protocolVersion)
                disconnectPlayerWithReason(ctx.channel(), reason)
            }
        } else {
            lgr.info { "Expected Handshake (0x00) but got $packetId, closing" }
            ctx.channel().close()
        }
    }

    private fun installBandwidthLimiter(ctx: ChannelHandlerContext, port: Int) {
        val limiterName = "host-bandwidth-limiter-$port"
        if (ctx.pipeline().get(limiterName) != null) return
        ctx.pipeline().addBefore(
            ctx.name(),
            limiterName,
            ProxyBandwidthLimiter.forHostPort(port)
        )
        lgr.info { "Installed bandwidth limiter for host port $port: 10Mbps tx/rx" }
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
            .handler(object : ChannelInitializer<NioSocketChannel>() {
                override fun initChannel(ch: NioSocketChannel) {
                    ch.pipeline().addLast(
                        MinecraftFrameDecoder(),
                        ProxyBackendHandler(frontendChannel, mcVersion)
                    )
                }
            })

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
            dataBuffer.writeVarInt(packetId)
            dataBuffer.writeUtf8String(reasonJson)

            buffer.writeVarInt(dataBuffer.readableBytes())
            buffer.writeBytes(dataBuffer)

            ch.writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE)
        } finally {
            dataBuffer.release()
        }
    }

    /**
     * Handle status request packets (Server List Ping)
     * Packet 0x00 = Status Request, Packet 0x01 = Ping Request
     */
    private fun handleStatusPacket(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        val parser = buffer.slice()
        
        // Read packet length (VarInt)
        parser.readVarInt()
        
        // Read packet ID
        val packetId = parser.readVarInt()
        
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
            dataBuffer.writeVarInt(0x00)  // Packet ID for Status Response
            dataBuffer.writeUtf8String(statusJson)

            buffer.writeVarInt(dataBuffer.readableBytes())
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
            dataBuffer.writeVarInt(0x01)  // Packet ID for Ping Response
            dataBuffer.writeLong(payload)

            buffer.writeVarInt(dataBuffer.readableBytes())
            buffer.writeBytes(dataBuffer)
            
            ctx.writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE)
        } finally {
            dataBuffer.release()
        }
    }
}
