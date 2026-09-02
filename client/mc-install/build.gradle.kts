import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

val javaVersion = libs.versions.java.get()

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion))
}

dependencies {
    api(project(":model"))
    api(project(":mclaunch"))
    implementation(project(":assets"))
    api(project(":database"))
    api(project(":net"))
    implementation(project(":misc"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("rdi-mc-install")
}
