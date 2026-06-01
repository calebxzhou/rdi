package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * A generic palette-compressed container for fixed-size indexed data.
 *
 * Storage model (same idea as Minecraft paletted storage):
 * 1. Keep a palette list [id -> value].
 * 2. Keep packed integer ids in a bit-packed long array.
 * 3. Grow bits automatically when palette expands.
 *
 * This is optimized for "many duplicates, few unique values".
 */
class PalettedContainer<T>(
    val size: Int,
    defaultValue: T,
    private val options: Options = Options()
) {
    data class Options(
        /**
         * For very small palettes, linear search is usually faster and lighter than a HashMap.
         * Once palette size exceeds this value, a HashMap index is built for O(1) writes.
         */
        val hashMapSwitchThreshold: Int = 16
    ) {
        init {
            require(hashMapSwitchThreshold >= 1) { "hashMapSwitchThreshold must be >= 1" }
        }
    }

    @Serializable
    data class Snapshot<T>(
        val size: Int,
        val palette: List<T>,
        val bits: Int,
        val data: LongArray
    )

    private val palette = ArrayList<T>(4)
    private var indexByValue: HashMap<T, Int>? = null
    private var bitsPerEntry: Int = 0
    private var storage: PackedStorage? = null

    init {
        require(size > 0) { "size must be > 0" }
        palette += defaultValue
    }

    fun get(index: Int): T {
        checkIndex(index)
        val paletteId = storage?.get(index) ?: 0
        return palette[paletteId]
    }

    fun set(index: Int, value: T): T {
        checkIndex(index)
        val newPaletteId = idFor(value)
        val s = storage
        val oldPaletteId = if (s == null) 0 else s.get(index)
        if (s != null && oldPaletteId != newPaletteId) {
            s.set(index, newPaletteId)
        }
        return palette[oldPaletteId]
    }

    fun getAndSet(index: Int, value: T): T = set(index, value)

    fun fill(value: T) {
        val paletteId = idFor(value)
        val s = storage
        if (s != null) {
            s.fill(paletteId)
        }
    }

    fun clear(defaultValue: T) {
        palette.clear()
        palette += defaultValue
        indexByValue = null
        bitsPerEntry = 0
        storage = null
    }

    fun paletteSize(): Int = palette.size

    fun paletteValues(): List<T> = palette.toList()

    fun maybeHas(predicate: (T) -> Boolean): Boolean = palette.any(predicate)

    /**
     * Rebuilds palette and storage using only currently referenced entries.
     * Useful after heavy edits to reduce memory footprint.
     */
    fun compact() {
        val oldStorage = storage ?: run {
            if (palette.size > 1) {
                val single = palette[0]
                palette.clear()
                palette += single
                indexByValue = null
            }
            return
        }

        val remap = IntArray(palette.size) { -1 }
        val remappedIds = IntArray(size)
        val newPalette = ArrayList<T>(palette.size)
        var nextId = 0

        for (i in 0 until size) {
            val oldId = oldStorage.get(i)
            var newId = remap[oldId]
            if (newId == -1) {
                newId = nextId++
                remap[oldId] = newId
                newPalette += palette[oldId]
            }
            remappedIds[i] = newId
        }

        palette.clear()
        palette.addAll(newPalette)
        rebuildValueIndexIfNeeded()

        val newBits = bitsForPaletteSize(palette.size)
        bitsPerEntry = newBits
        storage = if (newBits == 0) {
            null
        } else {
            PackedStorage(size = size, bits = newBits).also { packed ->
                for (i in 0 until size) {
                    packed.set(i, remappedIds[i])
                }
            }
        }
    }

    fun countValues(): Map<T, Int> {
        if (storage == null) {
            return mapOf(palette[0] to size)
        }
        val counts = IntArray(palette.size)
        for (i in 0 until size) {
            counts[storage!!.get(i)]++
        }
        return buildMap {
            for (id in counts.indices) {
                val count = counts[id]
                if (count > 0) {
                    put(palette[id], count)
                }
            }
        }
    }

    fun toSnapshot(): Snapshot<T> {
        val payload = storage?.raw()?.clone() ?: LongArray(0)
        return Snapshot(
            size = size,
            palette = palette.toList(),
            bits = bitsPerEntry,
            data = payload
        )
    }

    private fun idFor(value: T): Int {
        if (palette.size == 1 && palette[0] == value) {
            return 0
        }

        val threshold = options.hashMapSwitchThreshold
        if (palette.size <= threshold) {
            for (i in palette.indices) {
                if (palette[i] == value) {
                    return i
                }
            }
            return addPaletteValue(value)
        }

        val map = indexByValue ?: buildValueIndex()
        val existing = map[value]
        if (existing != null) return existing
        val newId = addPaletteValue(value)
        map[value] = newId
        return newId
    }

    private fun addPaletteValue(value: T): Int {
        val newId = palette.size
        palette += value
        ensureBitsForPaletteSize(palette.size)
        if (palette.size > options.hashMapSwitchThreshold) {
            if (indexByValue == null) {
                buildValueIndex()
            } else {
                indexByValue!![value] = newId
            }
        }
        return newId
    }

    private fun ensureBitsForPaletteSize(paletteSize: Int) {
        val requiredBits = bitsForPaletteSize(paletteSize)
        if (requiredBits <= bitsPerEntry) return

        val oldStorage = storage
        val newStorage = PackedStorage(size = size, bits = requiredBits)
        if (oldStorage != null) {
            for (i in 0 until size) {
                newStorage.set(i, oldStorage.get(i))
            }
        }
        storage = newStorage
        bitsPerEntry = requiredBits
    }

    private fun bitsForPaletteSize(paletteSize: Int): Int {
        if (paletteSize <= 1) return 0
        return max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1))
    }

    private fun buildValueIndex(): HashMap<T, Int> {
        val map = HashMap<T, Int>(palette.size * 2)
        for (i in palette.indices) {
            map[palette[i]] = i
        }
        indexByValue = map
        return map
    }

    private fun rebuildValueIndexIfNeeded() {
        if (palette.size > options.hashMapSwitchThreshold) {
            buildValueIndex()
        } else {
            indexByValue = null
        }
    }

    private fun checkIndex(index: Int) {
        require(index in 0 until size) { "index $index out of range [0, $size)" }
    }

    private class PackedStorage(
        private val size: Int,
        val bits: Int
    ) {
        private val mask = (1L shl bits) - 1L
        private val valuesPerLong = 64 / bits
        private val words = LongArray((size + valuesPerLong - 1) / valuesPerLong)

        init {
            require(bits in 1..32) { "bits must be in 1..32, got $bits" }
        }

        fun get(index: Int): Int {
            val cell = index / valuesPerLong
            val bitIndex = (index % valuesPerLong) * bits
            return ((words[cell] ushr bitIndex) and mask).toInt()
        }

        fun set(index: Int, value: Int) {
            val unsigned = value.toLong()
            require(unsigned and mask == unsigned) { "value $value does not fit in $bits bits" }
            val cell = index / valuesPerLong
            val bitIndex = (index % valuesPerLong) * bits
            val clearMask = mask shl bitIndex
            words[cell] = (words[cell] and clearMask.inv()) or ((unsigned and mask) shl bitIndex)
        }

        fun fill(value: Int) {
            val unsigned = value.toLong()
            require(unsigned and mask == unsigned) { "value $value does not fit in $bits bits" }
            var pattern = 0L
            for (i in 0 until valuesPerLong) {
                pattern = pattern or ((unsigned and mask) shl (i * bits))
            }
            words.fill(pattern)
        }

        fun raw(): LongArray = words
    }

    companion object {
        fun <T> fromSnapshot(snapshot: Snapshot<T>, options: Options = Options()): PalettedContainer<T> {
            require(snapshot.size > 0) { "size must be > 0" }
            require(snapshot.palette.isNotEmpty()) { "palette cannot be empty" }

            val container = PalettedContainer(
                size = snapshot.size,
                defaultValue = snapshot.palette.first(),
                options = options
            )

            container.palette.clear()
            container.palette.addAll(snapshot.palette)
            container.bitsPerEntry = snapshot.bits
            container.rebuildValueIndexIfNeeded()

            if (snapshot.bits == 0) {
                container.storage = null
                return container
            }

            val requiredLongs = (snapshot.size + (64 / snapshot.bits) - 1) / (64 / snapshot.bits)
            require(snapshot.data.size == requiredLongs) {
                "snapshot data length mismatch: got ${snapshot.data.size}, expected $requiredLongs"
            }

            val packed = PackedStorage(size = snapshot.size, bits = snapshot.bits)
            snapshot.data.copyInto(packed.raw())
            container.storage = packed
            return container
        }
    }
}
