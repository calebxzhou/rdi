pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":assets",
    ":assets:fonts",
    ":theme",
    ":code-editor",
    ":code-editor-ui",
    ":database",
    ":misc",
    ":model",
    ":net",
    ":archive",
    ":anvilrw",
    ":blessing-skin",
    ":mediaproc",
    ":pack-proc",
    ":mod-catalog",
    ":mod-catalog-tools",
    ":webview2",
    ":forgeguard",
    ":mclaunch",
    ":early-display",
    ":local-mc-proxy",
    ":mc-proxy"
)
project(":assets").projectDir = file("assets")
project(":theme").projectDir = file("theme")
project(":code-editor").projectDir = file("code-editor")
project(":code-editor-ui").projectDir = file("code-editor-ui")
project(":database").projectDir = file("database")
project(":misc").projectDir = file("../../common/misc")
project(":model").projectDir = file("../../common/model")
project(":net").projectDir = file("../../common/net")
project(":archive").projectDir = file("../../common/archive")
project(":anvilrw").projectDir = file("../../common/anvilrw")
project(":blessing-skin").projectDir = file("blessing-skin")
project(":mediaproc").projectDir = file("../mediaproc")
project(":pack-proc").projectDir = file("../pack-proc")
project(":mod-catalog").projectDir = file("mod-catalog")
project(":mod-catalog-tools").projectDir = file("mod-catalog-tools")
project(":webview2").projectDir = file("webview2")
project(":forgeguard").projectDir = file("../../mc/forgeguard")
project(":mclaunch").projectDir = file("../mclaunch")
project(":early-display").projectDir = file("../early-display")
project(":local-mc-proxy").projectDir = file("../local-mc-proxy")
project(":mc-proxy").projectDir = file("../../mc/proxy")
