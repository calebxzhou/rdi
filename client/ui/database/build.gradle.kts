import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
}

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

val sqliteWindowsX64 by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.sqlite.driver) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}:without-natives")
    sqliteWindowsX64("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}:natives-windows")

    testImplementation(kotlin("test"))
}

sqldelight {
    databases {
        register("RClientDatabase") {
            packageName.set("calebxzhou.rdi.client.database")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    from({ sqliteWindowsX64.map(::zipTree) }) {
        include("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
