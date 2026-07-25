package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.Mod

object LocalModCardResolver {
    fun resolve(mods: List<Mod>): Map<String, Mod.CardVo> = mods.mapNotNull { mod ->
        mod.toLocalCardVo()?.let { mod.projectKey() to it }
    }.toMap()
}
