plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "s-proxy"
include(":model", ":net")
project(":model").projectDir = file("../../common/model")
project(":net").projectDir = file("../../common/net")
