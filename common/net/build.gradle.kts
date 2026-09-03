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
//version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}
val runDir = layout.projectDirectory.dir("run")

tasks.withType<JavaExec>().configureEach {
    workingDir = runDir.asFile
    doFirst {
        workingDir.mkdirs()
    }
}
dependencies {
    api(project(":misc"))
    api(project(":model"))
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.encoding)
    implementation(libs.ktor.encoding.zstd) {
        exclude(group = "com.github.luben", module = "zstd-jni")
    }
    api(libs.ktor.sse)
    api(libs.ktor.http)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.tomlkt)
    implementation(libs.kotlinx.io.core)
    implementation(libs.annotations)
    compileOnly(libs.zstd.jni)
    testImplementation(kotlin("test"))
    testImplementation(libs.zstd.jni)
}
base {
    archivesName.set("rdi-net")
}
