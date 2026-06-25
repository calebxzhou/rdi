import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.internal.builtins.StandardNames.FqNames.target
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
//    implementation(project(":net"))
    implementation(kotlin("reflect"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.netty.buffer)
    implementation(libs.netty.codec)
    implementation(libs.netty.common)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)
    implementation(libs.zstd.jni)
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
tasks.register("出core") {
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    dependsOn(tasks.named("build"))
    val artifact = layout.buildDirectory.file("libs/prox-all.jar")

    doLast {
        val jarFile = artifact.get().asFile
        if (!jarFile.exists()) {
            throw GradleException("未找到构建产物: $jarFile")
        }
            val targetDir = layout.projectDirectory.dir("\\\\rdi\\rdi55\\prox2\\").asFile
            val destFile = targetDir.resolve(jarFile.name)
            Files.copy(
                jarFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

    }
}
