import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
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
    api(project(":model"))
    api(project(":net"))
    implementation(project(":misc"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(kotlin("test"))
}

base {
    archivesName.set("rdi-client-store")
}

val runDir = rootProject.layout.projectDirectory.dir("run").asFile
runDir.mkdirs()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = runDir
}

tasks.named<Jar>("jar") {
    archiveFileName.set("rdi-client-store.jar")
}
