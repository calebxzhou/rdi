package calebxzhou.rdi.common

import org.bson.types.ObjectId

/**
 * calebxzhou @ 2025-12-30 11:58
 */

// Validate name characters:., a-Z, 0-9, _, -, and CJK characters
val VALID_NAME_REGEX =
    Regex("^[.a-zA-Z0-9_\\-\\u4E00-\\u9FFF\\u3400-\\u4DBF\\u20000-\\u2A6DF\\u2A700-\\u2B73F\\u2B740-\\u2B81F\\u2B820-\\u2CEAF\\uF900-\\uFAFF\\u2F800-\\u2FA1F]+$")

val UNKNOWN_PLAYER_ID = ObjectId(ByteArray(12))
var DEBUG = System.getProperty("rdi.debug").toBoolean()
val IGNORE_MODPACK_TEST: Boolean
    get() = System.getProperty("rdi.ignoreModpackTest").toBoolean()

val CONFIG_EXCLUDE_PATH_PREFIX = listOf("yes_steve_model/builtin")

fun String.isExcludedConfigPath(): Boolean {
    val normalizedPath = replace('\\', '/').trim('/').lowercase()
    if (normalizedPath.isEmpty()) return false
    return CONFIG_EXCLUDE_PATH_PREFIX.any { prefix ->
        val normalizedPrefix = prefix.replace('\\', '/').trim('/').lowercase()
        normalizedPath == normalizedPrefix || normalizedPath.startsWith("$normalizedPrefix/")
    }
}
