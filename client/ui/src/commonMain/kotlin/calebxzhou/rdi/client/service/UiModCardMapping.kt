package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.CurseForgeModInfo
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModService.modDescription
import calebxzhou.rdi.common.service.ModService.modLogo
import calebxzhou.rdi.common.service.ModService.readNeoForgeConfig
import calebxzhou.rdi.common.service.ModService.toVo
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
    val iconBytes = localFile?.let {
        runCatching { JarFile(it).use { jar -> jar.modLogo } }.getOrNull()
    }
    val introText = localFile?.let {
        runCatching { JarFile(it).readNeoForgeConfig()?.modDescription }.getOrNull()
    }?.takeIf { it.isNotBlank() }
    if (briefInfo == null && iconBytes == null && introText == null) return null

    return (briefInfo?.toVo(localFile) ?: Mod.CardVo(
        name = slug.ifBlank { projectId },
        intro = introText ?: "暂无介绍",
        iconData = iconBytes,
        side = side
    )).copy(
        intro = briefInfo?.intro?.ifBlank { introText ?: "暂无介绍" } ?: (introText ?: "暂无介绍"),
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
    val iconBytes = modFile?.let {
        runCatching { JarFile(it).use { jar -> jar.modLogo } }.getOrNull()
    }
    val introText = summary?.takeIf { it.isNotBlank() }?.trim()
        ?: modFile?.let { JarFile(it).readNeoForgeConfig()?.modDescription }
        ?: "暂无介绍"

    return briefInfo?.toVo(modFile)?.copy(
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
    val iconBytes = modFile?.let {
        runCatching { JarFile(it).use { jar -> jar.modLogo } }.getOrNull()
    }
    val introText = description?.takeIf { it.isNotBlank() }?.trim()
        ?: modFile?.let { JarFile(it).readNeoForgeConfig()?.modDescription }
        ?: "暂无介绍"

    return briefInfo?.toVo(modFile)?.copy(
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
