import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.internal.builtins.StandardNames.FqNames.target
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val ktorVersion = "3.4.2"

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20" apply false
    id("com.gradleup.shadow") version "9.2.0"
}

group = "calebxzhou.rdi"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":misc"))
    implementation(project(":model"))
    implementation(project(":net"))
    implementation(kotlin("reflect"))
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("io.netty:netty-all:4.2.13.Final")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "calebxzhou.rdi.prox.MainKt")
    }
}
tasks.named<Test>("test") {
    enabled = false
}
tasks.test {
    useJUnitPlatform()
}
base {
    archivesName.set("prox")
}
tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}
kotlin {
    jvmToolchain(21)
}
tasks.register("出core") {
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    dependsOn(tasks.named("build"))
    val artifact = layout.buildDirectory.file("libs/prox-all.jar")

    doLast {
        val jarFile = artifact.get().asFile
        if (!jarFile.exists()) {
            throw GradleException("未找到构建产物: $jarFile")
        }
            val targetDir = layout.projectDirectory.dir("\\\\rdi\\rdi55\\prox\\").asFile
            val destFile = targetDir.resolve(jarFile.name)
            Files.copy(
                jarFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

    }
}
