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
/*
val javaVersion = 21

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
}*/

dependencies {
    implementation("org.bytedeco:javacv:1.5.13") {
        isTransitive = false
    }
    implementation("org.bytedeco:javacpp:1.5.13")
    implementation("org.bytedeco:ffmpeg:8.0.1-1.5.13")
    runtimeOnly("org.bytedeco:ffmpeg:8.0.1-1.5.13:windows-x86_64-gpl")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    rootProject.findProject(":assets")?.let { testImplementation(it) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
