package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.DEBUG

//https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json
enum class McVersion(
    val mcVer: String,
    val icon: String,
    val jreSupport: Int,
    val alterJreVer: Int = jreSupport,
    //预留多loader支持
    val loaderVersions: Map<ModLoader, ModLoader.Version>,
    val plusJvmArgs: List<String> = listOf(),
    val enabled: Boolean = true,
) {

    V211(
        "1.21.1",
        "assets/icons/mace.png",
        25,
        25,
        // "https://piston-meta.mojang.com/v1/packages/a56257b4bc475ecac33571b51b68b33ac046fc72/1.21.1.json",
        mapOf(
            ModLoader.neoforge to ModLoader.Version(
                ModLoader.neoforge,
                "neoforge-21.1.228",
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.228/neoforge-21.1.228-installer.jar",
                "1c96a584cdbbc00e99f905099352ac87c4d40926"
            )
        )
    ),
    V201(
        "1.20.1",
        "assets/icons/brush.png", 25,25,
        // "https://piston-meta.mojang.com/v1/packages/9318a951bbc903b54a21463a7eb8c4d451f7b132/1.20.1.json",
        mapOf(
            ModLoader.forge to ModLoader.Version(
                ModLoader.forge,
                "1.20.1-forge-47.4.20",
                "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-installer.jar",
                "237c5a17d941bfe793ff5780a4f330914d2c9f57"
            )
        )
    ),
    V192(
        "1.19.2",
        "assets/icons/frog.png", 21,21,
        mapOf(
            ModLoader.forge to ModLoader.Version(
                ModLoader.forge,
                "1.19.2-forge-43.5.2",
                "https://maven.minecraftforge.net/net/minecraftforge/forge/1.19.2-43.5.2/forge-1.19.2-43.5.2-installer.jar",
                "d242b6786039d4acb9ea7579624772b6809bda91"
            )
        )

    ),
    V182(
        "1.18.2",
        "assets/icons/copper.png", 21,21,
        //https://piston-meta.mojang.com/v1/packages/334b33fcba3c9be4b7514624c965256535bd7eba/1.18.2.json
        mapOf(
            ModLoader.forge to ModLoader.Version(
                ModLoader.forge,
                "1.18.2-forge-40.3.12",
                "https://maven.minecraftforge.net/net/minecraftforge/forge/1.18.2-40.3.12/forge-1.18.2-40.3.12-installer.jar",
                "d7f759dec5b52ddb342c3e12511da1674d0401bf"
            )
        )
    ),
    V165(
        "1.16.5",
        "assets/icons/zoglin.webp", 8,8,
        mapOf(
            ModLoader.forge to ModLoader.Version(
                ModLoader.forge,
                "1.16.5-forge-36.2.42",
                "https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.5-36.2.42/forge-1.16.5-36.2.42-installer.jar",
                "e09ecf910e4d5eae12fb3564d9b7de212c1958b2"
            )
        ),
        //jdk21+运行j8mc用
        /*"""--add-exports=java.base/sun.security.util=ALL-UNNAMED
--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming
--add-opens=java.base/java.util.jar=ALL-UNNAMED""".trimIndent().split("\n"),*/
        enabled = true,
    ),
    V122(
        "1.12.2",
        "assets/icons/terracotta.png", 25,25,
        mapOf(
            ModLoader.cleanroom to ModLoader.Version(
                ModLoader.cleanroom,
                "cleanroom-0.5.11-alpha",
                "https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/0.5.11-alpha/cleanroom-0.5.11-alpha-installer.jar",
                "d4c23d77d222e7896d6a8f6a266544119ee3e90b"
            )
        ),enabled = true
    ),
    //GTNH only
    V071(
        "1.7.10",
        "assets/icons/acacia_log.webp", 25,25,
        //https://piston-meta.mojang.com/v1/packages/334b33fcba3c9be4b7514624c965256535bd7eba/1.18.2.json
        mapOf(
            ModLoader.forge to ModLoader.Version(
                ModLoader.forge,
                "1.7.10-Forge10.13.4.1614-1.7.10",
                "https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-installer.jar",
                "fccafccf8ad4ce6d9f008e786b48ff53172bf9de"
            )
        )
        ,enabled = true
    ),


    ;

    //1.16及以下 服务端核心
    val serverJarName get() = "minecraft_server.${mcVer}.jar"
    val supportedJreVers get() = buildList {
        add(jreSupport)
        if (alterJreVer != jreSupport) {
            add(alterJreVer)
        }
    }
    val vMajor get() = mcVer.split(".")[0]
    val vMinor get() = mcVer.split(".")[1]
    val vPatch get() = mcVer.split(".")[2]
    val simpleVer get() = "$vMajor.$vMinor"
    fun supportsConfiguredJava(major: Int): Boolean = major in supportedJreVers

    fun supportsCurrentJava(major: Int): Boolean = supportsConfiguredJava(major)

    companion object {
        fun from(mcVer: String): McVersion? = entries.firstOrNull { it.mcVer == mcVer }
    }
}
