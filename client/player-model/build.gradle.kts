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
    implementation(project(":render-core"))
    implementation(project(":player-model-core"))
    api(compose.ui)
    api(libs.kotlinx.coroutines.core)
    implementation(compose.desktop.currentOs)
    implementation(libs.joml)
    implementation(libs.kotlin.logging.jvm)

    val lwjglVersion = libs.versions.lwjgl.get()
    listOf("", "glfw", "opengl").forEach { component ->
        val suffix = component.takeIf(String::isNotEmpty)?.let { "-$it" }.orEmpty()
        implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion")
        implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion:natives-windows")
    }

    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

base {
    archivesName.set("rdi-player-model")
}
