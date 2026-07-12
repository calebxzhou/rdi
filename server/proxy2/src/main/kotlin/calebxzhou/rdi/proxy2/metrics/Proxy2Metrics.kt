package calebxzhou.rdi.proxy2.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Proxy2Metrics {
    private val values = ConcurrentHashMap<String, AtomicLong>()

    fun inc(name: String, by: Long = 1) {
        values.computeIfAbsent(name) { AtomicLong() }.addAndGet(by)
    }

    fun set(name: String, value: Long) {
        values.computeIfAbsent(name) { AtomicLong() }.set(value)
    }

    fun snapshot(): Map<String, Long> =
        values.entries.associate { it.key to it.value.get() }.toSortedMap()
}
