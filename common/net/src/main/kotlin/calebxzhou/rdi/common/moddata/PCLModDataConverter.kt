package calebxzhou.rdi.common.moddata

import calebxzhou.rdi.common.json
import kotlinx.serialization.Serializable
import java.io.File

/**
 * calebxzhou @ 2025-10-14 18:01
 * 读取pcl的mod简要信息
 * https://raw.githubusercontent.com/Meloong-Git/PCL/refs/heads/main/Plain%20Craft%20Launcher%202/Resources/ModData.txt
 */
@Serializable
data class PCLModBriefInfo(
    val mcmodId: Int,
    val nameCn: String?=null,
    // mod通名
    val curseforgeSlugs: List<String>,
    val modrinthSlugs: List<String>,
)
//from PCL2/ModComp.vb
internal fun parsePclModData(raw: String): List<PCLModBriefInfo> {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "")
    val result = mutableListOf<PCLModBriefInfo>()

    var wikiLineIndex = 0
    for (line in normalized.split('\n')) {
        wikiLineIndex += 1
        if (line.isBlank()) continue

        val entries = line.split('¨')
        val curseforgeSlugs = linkedSetOf<String>()
        val modrinthSlugs = linkedSetOf<String>()
        var resolvedNameCn: String? = null

        for (entryData in entries) {
            if (entryData.isBlank()) continue

            val parts = entryData.split('|')
            if (parts.isEmpty()) continue

            val slugToken = parts[0].trim()
            if (slugToken.isEmpty()) continue

            val (curseforgeSlug, modrinthSlug) = parseSlugs(slugToken)

            curseforgeSlug?.let { curseforgeSlugs += it }
            modrinthSlug?.let { modrinthSlugs += it }

            if (parts.size >= 2 && resolvedNameCn == null) {
                var candidate = parts[1].trim().takeIf { it.isNotEmpty() }
                if (candidate != null && candidate.contains('*')) {
                    val fallbackSlug = (curseforgeSlug ?: modrinthSlug ?: curseforgeSlugs.firstOrNull()
                    ?: modrinthSlugs.firstOrNull())?.slugToDisplayName()
                    candidate = if (fallbackSlug != null) {
                        candidate.replace("*", " ($fallbackSlug)")
                    } else {
                        candidate.replace("*", "")
                    }.ifBlank { null }
                }
                resolvedNameCn = candidate
            }
        }

        if (curseforgeSlugs.isNotEmpty() || modrinthSlugs.isNotEmpty() || resolvedNameCn != null) {
            result += PCLModBriefInfo(
                mcmodId = wikiLineIndex,
                nameCn = resolvedNameCn,
                curseforgeSlugs = curseforgeSlugs.toList(),
                modrinthSlugs = modrinthSlugs.toList()
            )
        }
    }

    return result
}

private fun parseSlugs(token: String): Pair<String?, String?> {
    return when {
        token.startsWith('@') -> {
            val modrinth = token.removePrefix("@").ifBlank { null }
            null to modrinth
        }
        token.endsWith('@') -> {
            val slug = token.dropLast(1).ifBlank { null }
            slug to slug
        }
        token.contains('@') -> {
            val split = token.split('@', limit = 2)
            val curseforge = split.getOrNull(0)?.ifBlank { null }
            val modrinth = split.getOrNull(1)?.ifBlank { null }
            curseforge to modrinth
        }
        else -> token.ifBlank { null } to null
    }
}

private fun String.slugToDisplayName(): String {
    return this.replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
}
