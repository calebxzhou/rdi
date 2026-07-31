import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion = libs.versions.java.get()

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(javaVersion.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }
}

dependencies {
    implementation(project(":mc-proxy"))
    implementation(libs.bundles.netty.desktop)
    implementation(libs.zstd.jni) {
        artifact { classifier = "win_amd64" }
    }

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
