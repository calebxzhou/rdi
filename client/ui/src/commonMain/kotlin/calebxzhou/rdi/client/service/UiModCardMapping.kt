package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.CurseForgeModInfo
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModBriefInfo
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModService.modDescription
import calebxzhou.rdi.common.service.ModService.modLogo
import calebxzhou.rdi.common.service.ModService.readNeoForgeConfig
import calebxzhou.rdi.common.service.ModrinthService
import java.io.File
import java.util.jar.JarFile

internal fun Mod.toLocalCardVo(): Mod.CardVo? {
    val localFile = file ?: targetPath.toFile().takeIf { it.exists() }
    val briefInfo = when (platform.lowercase()) {
        "cf" -> CurseForgeService.slugBriefInfo[slug.trim().lowercase()]
        "mr" -> ModrinthService.slugBriefInfo[slug.trim().lowercase()]
        else -> null
    }
    val localMeta = localFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = localMeta?.introText
    if (briefInfo == null && iconBytes == null && introText == null) return null

    return briefInfo?.toUiCardVo(localMeta, side)?.copy(
        intro = briefInfo.intro.ifBlank { introText ?: "暂无介绍" }
    ) ?: Mod.CardVo(
        name = slug.ifBlank { projectId },
        intro = introText ?: "暂无介绍",
        iconData = iconBytes,
        side = side
    )
}

internal fun CurseForgeModInfo.toUiCardVo(modFile: File? = null): Mod.CardVo {
    val briefInfo = CurseForgeService.slugBriefInfo[slug.trim().lowercase()]
    val icons = buildList {
        briefInfo?.logoUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        logo?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        logo?.url?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    val resolvedName = name.ifBlank { slug }
    val localMeta = modFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = summary?.takeIf { it.isNotBlank() }?.trim()
        ?: localMeta?.introText
        ?: "暂无介绍"

    return briefInfo?.toUiCardVo(localMeta)?.copy(
        intro = briefInfo.intro.ifBlank { introText }
    ) ?: Mod.CardVo(
        name = resolvedName,
        nameCn = null,
        intro = introText,
        iconData = iconBytes,
        iconUrls = icons,
        side = Mod.Side.BOTH
    )
}

internal fun ModrinthProject.toUiCardVo(modFile: File? = null): Mod.CardVo {
    val briefInfo = ModrinthService.slugBriefInfo[slug.trim().lowercase()]
    val icons = buildList {
        briefInfo?.logoUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        iconUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    val resolvedName = title.ifBlank { slug }
    val localMeta = modFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = description?.takeIf { it.isNotBlank() }?.trim()
        ?: localMeta?.introText
        ?: "暂无介绍"

    return briefInfo?.toUiCardVo(localMeta)?.copy(
        intro = briefInfo.intro.ifBlank { introText }
    ) ?: Mod.CardVo(
        name = resolvedName,
        nameCn = null,
        intro = introText,
        iconData = iconBytes,
        iconUrls = icons,
        side = Mod.Side.BOTH
    )
}

private data class LocalModCardMeta(
    val iconBytes: ByteArray? = null,
    val introText: String? = null
)

private fun File.readLocalModCardMeta(): LocalModCardMeta = JarFile(this).use { jar ->
    LocalModCardMeta(
        iconBytes = jar.modLogo,
        introText = jar.readNeoForgeConfig()?.modDescription?.takeIf { it.isNotBlank() }
    )
}

private fun ModBriefInfo.toUiCardVo(
    localMeta: LocalModCardMeta? = null,
    side: Mod.Side = Mod.Side.BOTH
): Mod.CardVo = Mod.CardVo(
    name = name,
    nameCn = nameCn,
    intro = intro,
    iconData = localMeta?.iconBytes,
    iconUrls = buildList {
        if (logoUrl.isNotBlank()) add(logoUrl)
    },
    side = side
)
