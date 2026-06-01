import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    api(project(":misc"))
    api(project(":model"))
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.encoding)
    api(libs.ktor.sse)
    api(libs.ktor.http)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.mykotutils.std)
    implementation(libs.mykotutils.log)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.tomlkt)
    implementation(libs.kotlinx.io.core)
    implementation(libs.annotations)
}
