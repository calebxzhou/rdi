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

include(":misc", ":model", ":net", ":archive", ":anvilrw", ":mod-catalog", ":mod-catalog-tools")
project(":misc").projectDir = file("../../common/misc")
project(":model").projectDir = file("../../common/model")
project(":net").projectDir = file("../../common/net")
project(":archive").projectDir = file("../../common/archive")
project(":anvilrw").projectDir = file("../../common/anvilrw")
project(":mod-catalog").projectDir = file("mod-catalog")
project(":mod-catalog-tools").projectDir = file("mod-catalog-tools")
