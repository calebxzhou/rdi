package calebxzau.rdi.common.model

import kotlinx.serialization.Serializable

@Serializable
enum class ModpackCategory(val label: String) {
    Large("\uDB84\uDFA7 大型"),
    Medium("\uDB84\uDFA5 中型"),
    Small("\uDB84\uDFA4 小型"),
    Magic("\uDB86\uDC44 魔法"),
    Hardcore("\uE646 硬核"),
    Skyblock("\uDB84\uDC4F 空岛"),
    Vanilla("\uDB81\uDD8C 纯净"),
    Tech("\uE266 科技"),
    Story("\uDB84\uDFAD 剧情"),
    Adventure("\uE6A0 探险"),
    Casual("\uE2A2 休闲"),
    Manage("\uF157 经营"),
    Apocalypse("\uDB82\uDEC1 末日"),
    War("\uDB81\uDF03 战争"),
    HeavyMod("\uDB80\uDEA2 魔改"),
    Rpg("\uDB85\uDFDD RPG"),
    Combat("\uDB81\uDF87 战斗"),
    LightMod("\uEDF7 轻量"),
    Optimize("\uF4BC 优化"),
    Other("\uDB82\uDC17 其他");

    companion object {
        val allLabels = entries.map(ModpackCategory::label)
    }
}
