import org.gradle.jvm.tasks.Jar

val ktorVersion = "3.4.2"

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.gradleup.shadow") version "9.2.0"
}

group = "calebxzhou.rdi"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "calebxzhou.rdi.proxy2.Proxy2MainKt")
    }
    archiveClassifier.set("plain")
}

tasks.named<Test>("test") {
    enabled = false
}

base {
    archivesName.set("proxy2")
}

kotlin {
    jvmToolchain(21)
}
