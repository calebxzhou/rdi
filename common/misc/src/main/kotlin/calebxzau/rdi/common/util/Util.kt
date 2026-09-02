package calebxzau.rdi.common.util

import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * calebxzhou @ 2026-08-16 20:54
 */
fun uuid7() : Uuid = Uuid.generateV7()
fun uuid7j() : UUID = uuid7().toJavaUuid()