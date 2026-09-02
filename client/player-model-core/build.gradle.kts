import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

//project.version = libs.versions.app.get()

repositories { mavenCentral() }

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    implementation(libs.joml)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

base { archivesName.set("rdi-player-model-core") }
