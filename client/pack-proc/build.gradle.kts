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
    api(project(":model"))
    api(project(":mod-catalog"))
    implementation(project(":net"))
    implementation(project(":archive"))
    implementation(project(":mediaproc"))
    implementation(project(":assets"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.mykotutils.std)
    implementation(libs.mykotutils.log)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
base {
    archivesName.set("rdi-packproc")
}