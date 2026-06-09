package calebxzhou.rdi.mc.rcmd.home

import java.util.*
import java.util.stream.Collectors

object HomeService {
    private const val MAX_HOME_NAME_LENGTH = 32
    private const val MAX_HOMES = 10

    fun setHome(player: HomePlayer, name: String): HomeResult {
        val normalizedName = normalizeName(name)
        if (normalizedName.length > MAX_HOME_NAME_LENGTH) {
            return HomeResult.error("家名称最多" + MAX_HOME_NAME_LENGTH + "个字符")
        }
        if (normalizedName.contains("/") || normalizedName.contains("\\")) {
            return HomeResult.error("家名称不能包含/或\\")
        }

        val homes = LinkedHashMap(player.loadHomes())
        if (!homes.containsKey(normalizedName) && homes.size >= MAX_HOMES) {
            return HomeResult.Companion.error("最多只能设置" + MAX_HOMES + "个家")
        }
        homes[normalizedName] = player.currentLocation()
        player.saveHomes(homes)
        return HomeResult.ok("已设置家" + normalizedName)
    }

    fun goHome(player: HomePlayer, name: String): HomeResult {
        val normalizedName = normalizeName(name)

        val location = player.loadHomes().get(normalizedName)
            ?: return HomeResult.Companion.error("没有名为" + normalizedName + "的家")

        val teleportResult = player.teleportTo(location)
        if (!teleportResult.success) {
            return teleportResult
        }
        return HomeResult.ok("已传送到家" + normalizedName)
    }

    fun listHomes(player: HomePlayer): HomeResult {
        val homes = player.loadHomes()
        if (homes.isEmpty()) {
            return HomeResult.Companion.ok("还没有设置任何家")
        }
        val message = homes.entries.stream()
            .map<String> { entry: MutableMap.MutableEntry<String, HomeLocation> ->
                HomeService.formatHome(
                    entry!!.key,
                    entry.value!!
                )
            }
            .collect(Collectors.joining("\n"))
        return HomeResult.Companion.ok(message)
    }

    fun deleteHome(player: HomePlayer, name: String): HomeResult {
        val normalizedName = normalizeName(name)
        if (normalizedName == null) {
            return HomeResult.Companion.error("家名称不能为空")
        }

        val homes = LinkedHashMap<String, HomeLocation>(player.loadHomes())
        if (homes.remove(normalizedName) == null) {
            return HomeResult.Companion.error("没有名为" + normalizedName + "的家")
        }
        player.saveHomes(homes)
        return HomeResult.Companion.ok("已删除家" + normalizedName)
    }

    private fun normalizeName(name: String): String {
        val normalized = name.trim { it <= ' ' }
        return normalized
    }

    private fun formatHome(name: String, location: HomeLocation): String {
        return String.format(
            Locale.ROOT,
            "%s：%s x=%.1f y=%.1f z=%.1f",
            name,
            location.dimension,
            location.x,
            location.y,
            location.z
        )
    }
}
