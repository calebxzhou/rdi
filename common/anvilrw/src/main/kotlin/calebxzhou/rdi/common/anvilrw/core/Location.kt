package calebxzhou.rdi.common.anvilrw.core

import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.SECTOR_SIZE_BYTES
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Location(locationBytes: ByteArray) {
    var offset: Int
    var sectorCount: Int

    init {
        require(locationBytes.size == 4) { "Location bytes must be 4 bytes!" }
        offset = readInt(locationBytes.copyOfRange(0, 3), ByteOrder.BIG_ENDIAN)
        sectorCount = readInt(locationBytes.copyOfRange(3, 4), ByteOrder.BIG_ENDIAN)
    }

    override fun toString(): String = "Location{offset=$offset, sectorCount=$sectorCount}"

    companion object {
        fun createEmpty(): Location = Location(byteArrayOf(0, 0, 0, 0))

        fun readInt(data: ByteArray, order: ByteOrder): Int {
            val buffer = ByteBuffer.allocate(4).order(order)
            buffer.position(4 - data.size)
            buffer.put(data)
            buffer.rewind()
            return buffer.int
        }
    }
}
