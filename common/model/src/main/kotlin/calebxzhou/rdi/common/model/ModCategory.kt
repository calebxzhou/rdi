package calebxzhou.rdi.common.model

object ModCategory {
    //curseforge 分类
    val CF_MAP = mapOf(
        // Mods
        406 to "世界元素",
        407 to "生物群系",
        410 to "维度",
        408 to "矿物/资源",
        409 to "天然结构",
        412 to "科技",
        415 to "管道/物流",
        4843 to "自动化",
        417 to "能源",
        4558 to "红石",
        436 to "食物/烹饪",
        416 to "农业",
        414 to "运输",
        420 to "仓储",
        419 to "魔法",
        422 to "冒险",
        424 to "装饰",
        411 to "生物",
        434 to "装备",
        423 to "信息显示",
        435 to "服务器",
        5191 to "改良",
        421 to "支持库",

        // Modpacks
        4484 to "多人",
        4479 to "硬核",
        4483 to "战斗",
        4478 to "任务",
        4472 to "科技",
        4473 to "魔法",
        4475 to "冒险",
        4476 to "探索",
        4477 to "小游戏",
        4474 to "科幻",
        4736 to "空岛",
        5128 to "原版改良",
        4487 to "FTB",
        4480 to "基于地图",
        4481 to "轻量",
        4482 to "大型",

        // Resource packs
        403 to "原版风",
        400 to "写实风",
        401 to "现代风",
        402 to "中世纪",
        399 to "蒸汽朋克",
        5244 to "含字体",
        404 to "动态效果",
        4465 to "兼容 Mod",
        393 to "16x",
        394 to "32x",
        395 to "64x",
        396 to "128x",
        397 to "256x",
        398 to "超高清",
        5193 to "数据包",

        // Shaders
        6553 to "写实风",
        6554 to "幻想风",
        6555 to "原版风",

        // Datapacks
        6948 to "冒险",
        6949 to "幻想",
        6950 to "支持库",
        6952 to "魔法",
        6946 to "Mod 相关",
        6951 to "科技",
        6953 to "实用",
    )
    //modrinth分类map

    val MR_MAP = mapOf(
        // Shared
        "technology" to "科技",
        "magic" to "魔法",
        "adventure" to "冒险",
        "utility" to "实用",
        "optimization" to "性能优化",
        "vanilla-like" to "原版风",
        "realistic" to "写实风",

        // Mods / Datapacks
        "worldgen" to "世界元素",
        "food" to "食物/烹饪",
        "game-mechanics" to "游戏机制",
        "transportation" to "运输",
        "storage" to "仓储",
        "decoration" to "装饰",
        "mobs" to "生物",
        "equipment" to "装备",
        "social" to "服务器",
        "library" to "支持库",

        // Modpacks
        "multiplayer" to "多人",
        "challenging" to "硬核",
        "combat" to "战斗",
        "quests" to "任务",
        "kitchen-sink" to "水槽包",
        "lightweight" to "轻量",

        // Resource packs
        "simplistic" to "简洁",
        "tweaks" to "改良",
        "8x-" to "极简",
        "16x" to "16x",
        "32x" to "32x",
        "48x" to "48x",
        "64x" to "64x",
        "128x" to "128x",
        "256x" to "256x",
        "512x+" to "超高清",
        "audio" to "含声音",
        "fonts" to "含字体",
        "models" to "含模型",
        "gui" to "含 UI",
        "locale" to "含语言",
        "core-shaders" to "核心着色器",
        "modded" to "兼容 Mod",

        // Shaders
        "fantasy" to "幻想风",
        "semi-realistic" to "半写实风",
        "cartoon" to "卡通风",
        "colored-lighting" to "彩色光照",
        "path-tracing" to "路径追踪",
        "pbr" to "PBR",
        "reflections" to "反射",
        "iris" to "Iris",
        "optifine" to "OptiFine",
        "vanilla" to "原版可用",
    )
}