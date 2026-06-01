import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "calebxzhou.rdi.common"
version = "0.1"

val commonJavaSdkVersion = 25

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(commonJavaSdkVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(commonJavaSdkVersion))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(commonJavaSdkVersion)
}

dependencies {
    testImplementation(kotlin("test"))
}
