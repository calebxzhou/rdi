import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

project.version = libs.versions.app.get()

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    api(project(":database"))
    api(project(":model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("rdi-player-info")
}
