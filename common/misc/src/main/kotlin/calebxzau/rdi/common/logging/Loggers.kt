package calebxzau.rdi.common.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object Loggers {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<Any?, KLogger> {
        val logger = when (thisRef) {
            null -> KotlinLogging.logger(prop.name)
            is Class<*> -> KotlinLogging.logger(thisRef.name)
            else -> KotlinLogging.logger(thisRef::class.java.name)
        }
        return ReadOnlyProperty { _, _ -> logger }
    }
}
