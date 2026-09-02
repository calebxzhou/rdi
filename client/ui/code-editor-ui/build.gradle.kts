import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

//project.version = libs.versions.app.get()

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
    api(project(":code-editor"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":theme"))
}

base {
    archivesName.set("rdi-code-editor-ui")
}
