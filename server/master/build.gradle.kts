import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.testing.Test

val ktorVersion = "3.4.0"

plugins {
    application
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("io.ktor.plugin") version "3.4.0"
}

group = "calebxzhou.rdi"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Github Packages"
        url = uri("https://maven.pkg.github.com/")
    }
}

dependencies {
    implementation(project(":common"))
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-zstd:${ktorVersion}")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.5.0")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.6")
    // Source: https://mvnrepository.com/artifact/org.mongodb/bson-kotlinx
    implementation("org.mongodb:bson-kotlinx:5.6.3")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.5.1")
    implementation("net.benwoodworth.knbt:knbt:0.11.8")
    implementation("com.github.docker-java:docker-java:3.7.0")
    implementation("com.github.docker-java:docker-java-transport-okhttp:3.7.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")

    implementation("calebxzhou.mykotutils:std:0.1")
    implementation("calebxzhou.mykotutils:log:0.1")
    implementation("calebxzhou.mykotutils:curseforge:0.1")
    implementation("calebxzhou.mykotutils:hwspec:0.1")


    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

ktor {
    fatJar {
        archiveFileName.set("ihq.jar")
    }
}

application {
    mainClass.set("calebxzhou.rdi.master.RDIKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.named<Test>("test") {
    enabled = false
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register("出core") {
    // Uses Project APIs in the action; mark as not CC-compatible to avoid serialization errors.
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    dependsOn(tasks.named("buildFatJar"))

    doLast {
        val jarFile = layout.buildDirectory.file("libs/ihq.jar").get().asFile
        if (!jarFile.exists()) {
            throw GradleException("未找到构建产物: $jarFile")
        }
        val targetDir = file("\\\\rdi\\rdi55\\ihq")
        targetDir.mkdirs()
        val destFile = targetDir.resolve(jarFile.name)
        Files.copy(
            jarFile.toPath(),
            destFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        println("已复制 $jarFile 到 $destFile")
    }
}
tasks.test {
    testLogging.showStandardStreams = true
}