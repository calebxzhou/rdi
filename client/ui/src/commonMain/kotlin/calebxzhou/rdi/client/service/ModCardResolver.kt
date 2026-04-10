package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModrinthProject

data class ModCardResolveContext(
    val modrinthProjects: List<ModrinthProject>? = null
)

interface ModCardResolver {
    val platform: String
    suspend fun resolve(mods: List<Mod>, context: ModCardResolveContext = ModCardResolveContext()): Map<String, Mod.CardVo>
}

// RDI invariant:
// - CurseForge projectId is numeric
// - Modrinth projectId/slug is non-numeric
// so projectId alone is enough as a resolver hydration key.
fun Mod.projectKey(): String = projectId.trim()
