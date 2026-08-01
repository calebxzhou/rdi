import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    mavenLocal()
}

val javaVersion = libs.versions.java.get()

kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion))
}

dependencies {
    implementation("org.bytedeco:javacv:1.5.13") {
        isTransitive = false
    }
    implementation("org.bytedeco:javacpp:1.5.13")
    implementation("org.bytedeco:ffmpeg:8.0.1-1.5.13")
    runtimeOnly("org.bytedeco:ffmpeg:8.0.1-1.5.13:windows-x86_64-gpl")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(project(":assets"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
