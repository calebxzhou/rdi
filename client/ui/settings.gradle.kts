pluginManagement {
    repositories {
        google()
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
include(":common")
project(":common").projectDir = file("../../common")
include(":ip2region")
project(":ip2region").projectDir = file("../../ip2region")
