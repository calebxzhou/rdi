import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "calebxzhou.rdi"
version = libs.versions.app.get()

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    implementation(project(":misc"))
    api(project(":model"))
    api(project(":mod-catalog"))
    implementation(project(":net"))
    implementation(project(":archive"))
    implementation(project(":mediaproc"))
    implementation(project(":assets"))
    // Modpack2ArchiveBuilder writes tar.zst directly. The archive module keeps
    // zstd compile-only for consumers that do not need writing, so this module
    // must carry the runtime dependency for its own archive and test paths.
    implementation(libs.zstd.jni)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
base {
    archivesName.set("rdi-packproc")
}
