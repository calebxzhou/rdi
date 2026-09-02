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
kotlin{
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}
dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.mongodb.bson)
    api(libs.mongodb.bson.kotlinx)
    api(libs.tomlkt)
    api(libs.kotlin.logging.jvm)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("rdi-misc")
}
