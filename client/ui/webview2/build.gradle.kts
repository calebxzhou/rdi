import org.gradle.jvm.tasks.Jar
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
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(kotlin("test"))
}

tasks.named<Jar>("jar") {
    archiveFileName.set("rdi-webview2.jar")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    }
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.matching { it.name == "hotRun" || it.name == "hotDev" }.configureEach {
    enabled = false
}
