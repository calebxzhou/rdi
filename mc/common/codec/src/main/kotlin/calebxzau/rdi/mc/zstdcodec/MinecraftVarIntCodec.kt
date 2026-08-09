package calebxzau.rdi.mc.zstdcodec

import io.netty.buffer.ByteBuf

/**
 * The version-specific Minecraft VarInt bridge used by the shared codec.
 *
 * Minecraft owns the wire-format implementation. Each 1.21 module supplies
 * an adapter backed by its own generated Minecraft sources.
 */
interface MinecraftVarIntCodec {
    fun read(buffer: ByteBuf): Int

    fun write(buffer: ByteBuf, value: Int)
}
