import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    api(project(":model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.sqldelight.sqlite.driver) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}:without-natives")
    sqliteWindowsX64("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}:natives-windows")

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.okhttp)
}

sqldelight {
    databases {
        register("ModCatalogDatabase") {
            packageName.set("calebxzhou.rdi.client.modcatalog.database")
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
base {
    archivesName.set("rdi-mod-catalog")
}