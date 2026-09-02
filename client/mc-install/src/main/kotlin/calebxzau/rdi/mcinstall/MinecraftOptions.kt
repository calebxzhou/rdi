package calebxzau.rdi.mcinstall

import calebxzhou.rdi.common.model.McVersion
import java.io.File

fun writeMinecraftOptions(versionDir: File, mcVersion: McVersion): Result<Unit> = runCatching {
    val optionsFile = versionDir.resolve("options.txt")
    val overrides = linkedMapOf<String, String>().apply {
        when (mcVersion) {
            McVersion.V211,
            McVersion.V201,
                -> {
                put("darkMojangStudiosBackground", "true")
                put("lang", "zh_cn")
            }

            McVersion.V122 -> put("lang", "zh_cn")
            McVersion.V071 -> put("lang", "zh_CN")
        }
        put("forceUnicodeFont", "true")
    }
    optionsFile.writeText(
        mergeMinecraftOptions(
            original = optionsFile.takeIf(File::exists)?.readText().orEmpty(),
            overrides = overrides,
        )
    )
}

internal fun mergeMinecraftOptions(
    original: String,
    overrides: Map<String, String>,
): String {
    if (original.isBlank()) {
        return overrides.entries.joinToString("\n") { (key, value) -> "$key:$value" }
    }

    val lineSeparator = if ("\r\n" in original) "\r\n" else "\n"
    val updatedKeys = linkedSetOf<String>()
    val mergedLines = original.lineSequence().map { line ->
        val delimiterIndex = line.indexOf(':')
        if (delimiterIndex <= 0) return@map line
        val key = line.substring(0, delimiterIndex)
        val overrideValue = overrides[key] ?: return@map line
        updatedKeys += key
        "$key:$overrideValue"
    }.toMutableList()

    overrides.forEach { (key, value) ->
        if (key !in updatedKeys) mergedLines += "$key:$value"
    }
    return mergedLines.joinToString(lineSeparator)
}
