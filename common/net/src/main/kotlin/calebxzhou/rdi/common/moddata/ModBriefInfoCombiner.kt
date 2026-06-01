package calebxzhou.rdi.common.moddata

import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.ModBriefInfo
import calebxzhou.rdi.common.serdesJson
import java.io.File

/**
 * calebxzhou @ 2025-10-14 23:28
 */

fun main() {
    // Read all JSON files from mcmod-data directory and combine into one list
    val mcmodData = mcmodDataDir.listFiles { f -> f.extension == "json" }
        ?.flatMap { file ->
            runCatching {
                serdesJson.decodeFromString<List<McmodModBriefInfo>>(file.readText())
            }.getOrElse {
                println("Failed to parse ${file.name}: ${it.message}")
                emptyList()
            }
        }
        ?.distinctBy { it.id } // Remove duplicates by mcmod id
        ?: emptyList()
    
    println("Loaded ${mcmodData.size} mods from mcmod-data")
    
    val pclData = parsePclModData(File("ModData.txt").readText())

    val combined = combineModBriefInfo(mcmodData, pclData)

    File("mod_brief_info.json").writeText(combined.json)
    println("Combined ${combined.size} mods")
}

fun combineModBriefInfo(
    mcmodData: List<McmodModBriefInfo>,
    pclData: List<PCLModBriefInfo>
): List<ModBriefInfo> {
    val pclByMcmodId = pclData.associateBy { it.mcmodId }

    return mcmodData.map { mcmodInfo ->
        val pclInfo = pclByMcmodId[mcmodInfo.id]

        val nameCn = mcmodInfo.nameCn ?: pclInfo?.nameCn
        val curseforgeSlugs = pclInfo?.curseforgeSlugs ?: emptyList()
        val modrinthSlugs = pclInfo?.modrinthSlugs ?: emptyList()

        ModBriefInfo(
            mcmodId = mcmodInfo.id,
            logoUrl = mcmodInfo.logoUrl,
            name = mcmodInfo.name,
            nameCn = nameCn,
            intro = mcmodInfo.intro,
            curseforgeSlugs = curseforgeSlugs,
            modrinthSlugs = modrinthSlugs
        )
    }
}
