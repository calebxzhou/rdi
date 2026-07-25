package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.service.CurseForgeService
import calebxzhou.rdi.common.service.ModrinthService

object RemoteModLocalization {
    fun titleByModrinthSlug(slug: String?, fallback: String): String {
        val briefInfo = slug.modrinthBriefInfo()
        return briefInfo?.nameCn?.takeIf(String::isNotBlank)
            ?: briefInfo?.name?.takeIf(String::isNotBlank)
            ?: fallback
    }

    fun introByModrinthSlug(slug: String?, fallback: String): String =
        slug.modrinthBriefInfo()?.intro?.takeIf(String::isNotBlank) ?: fallback

    fun mcmodIdByModrinthSlug(slug: String?): Int? =
        slug.modrinthBriefInfo()?.mcmodId

    fun titleByCurseForgeSlug(slug: String?, fallback: String): String {
        val briefInfo = slug.curseForgeBriefInfo()
        return briefInfo?.nameCn?.takeIf(String::isNotBlank)
            ?: briefInfo?.name?.takeIf(String::isNotBlank)
            ?: fallback
    }

    fun introByCurseForgeSlug(slug: String?, fallback: String): String =
        slug.curseForgeBriefInfo()?.intro?.takeIf(String::isNotBlank) ?: fallback

    fun mcmodIdByCurseForgeSlug(slug: String?): Int? =
        slug.curseForgeBriefInfo()?.mcmodId

    private fun String?.modrinthBriefInfo() =
        this?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let(ModrinthService.slugBriefInfo::get)

    private fun String?.curseForgeBriefInfo() =
        this?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let(CurseForgeService.slugBriefInfo::get)
}
