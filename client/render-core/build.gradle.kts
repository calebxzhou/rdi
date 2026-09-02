import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

//project.version = libs.versions.app.get()

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    implementation("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}")
    implementation("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}:natives-windows")
    implementation(libs.kotlin.logging.jvm)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
