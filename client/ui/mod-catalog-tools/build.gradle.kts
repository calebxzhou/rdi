import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
}

dependencies {
    implementation(project(":mod-catalog"))
    implementation(project(":model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.jsoup)
    implementation(libs.pinyin.pro)
    implementation(libs.sqldelight.sqlite.driver) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    implementation("org.xerial:sqlite-jdbc:${libs.versions.sqlite.get()}:without-natives")

    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("buildModCatalogDatabase") {
    group = "mod catalog"
    description = "Fetch, merge, validate and atomically replace mod_catalog.db"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("calebxzhou.rdi.client.modcatalog.tools.ModCatalogToolKt")
    workingDir = layout.projectDirectory.asFile
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
base {
    archivesName.set("rdi-mod-catalog-tools")
}