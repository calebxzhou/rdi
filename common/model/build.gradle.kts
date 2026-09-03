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



dependencies {
    api(project(":misc"))
    api(libs.knbt)
    api(libs.tomlkt)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    testImplementation(kotlin("test"))
}
base {
    archivesName.set("rdi-model")
}
