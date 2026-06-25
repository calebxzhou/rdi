plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "s-proxy"
include(":misc", ":model", )
project(":misc").projectDir = file("../../common/misc")
project(":model").projectDir = file("../../common/model")