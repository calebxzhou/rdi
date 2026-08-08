import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

project.version = libs.versions.app.get()

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    api(compose.runtime)
    api(compose.ui)
    api(compose.foundation)
    api(libs.compose.material3)
    implementation(compose.desktop.currentOs)
    runtimeOnly(project(":assets:fonts"))
}

base {
    archivesName.set("rdi-theme")
}
