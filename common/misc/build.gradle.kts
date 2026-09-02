plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "calebxzhou.rdi.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.mongodb.bson)
    api(libs.mongodb.bson.kotlinx)
    api(libs.tomlkt)
    api(libs.kotlin.logging.jvm)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
}
base {
    archivesName.set("rdi-misc")
}