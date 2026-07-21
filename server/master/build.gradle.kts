import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.testing.Test

plugins {
    application
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

group = "calebxzhou.rdi"
version = "1.0-SNAPSHOT"
idea {
    module {
        excludeDirs = excludeDirs + file("run") + file("logs")
    }
}
repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Github Packages"
        url = uri("https://maven.pkg.github.com/")
    }
}

dependencies {
    implementation(project(":misc"))
    implementation(project(":model"))
    implementation(project(":net"))
    implementation(project(":archive"))
    implementation(project(":anvilrw"))
    implementation(project(":ai"))
    implementation(libs.logback.classic)
    implementation(libs.bundles.ktor.server)
    implementation("io.netty:netty-tcnative-boringssl-static:${libs.versions.netty.tcnative.get()}:linux-x86_64")
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tomlkt)
    implementation(libs.kotlin.logging.jvm)
    // Source: https://mvnrepository.com/artifact/org.mongodb/bson-kotlinx
    implementation(libs.mongodb.bson.kotlinx)
    implementation(libs.mongodb.driver.kotlin.coroutine)
    implementation(libs.knbt)
    implementation(libs.bundles.docker.java)
    implementation(libs.commons.compress)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.jakarta.mail)
    implementation(libs.ip2region)
    implementation(libs.bundles.mykotutils)
    implementation(libs.zstd.jni)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("ihq.jar")
}

tasks.register("buildFatJar") {
    group = "build"
    description = "Builds a combined JAR of project and runtime dependencies."
    dependsOn(shadowJar)
}

application {
    mainClass.set("calebxzhou.rdi.master.RDIKt")
}

kotlin {
    jvmToolchain(25)
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
