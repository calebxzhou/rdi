import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

val appVersion = libs.versions.app.get()
val javaVersion = libs.versions.java.get()
val javaVersionInt = javaVersion.toInt()
val jvmTargetVersion = JvmTarget.fromTarget(javaVersion)
project.version = appVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    idea
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven(url = "https://jitpack.io")
}

base {
    archivesName.set("rdi-5-ui")
}

kotlin {
    jvmToolchain(javaVersionInt)
    compilerOptions {
        jvmTarget.set(jvmTargetVersion)
    }
    sourceSets.all {
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.desktop)
    implementation(libs.navigation.compose)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.joml)
    implementation(libs.minecraft.auth)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.mykotutils.std)
    implementation(libs.mykotutils.log)
    implementation(libs.tomlkt)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.bundles.netty.desktop)
    implementation(libs.oshi.core.desktop)
    implementation(libs.logback.classic)
    implementation(libs.snakeyaml)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.bundles.ktor.common)
    implementation(libs.ktor.client.auth)
    implementation(libs.bundles.ktor.json.client)
    implementation(libs.jsoup)
    implementation(libs.bundles.mongodb)
    implementation(libs.caffeine)
    implementation(libs.maven.artifact)
    implementation(libs.zstd.jni)

    implementation(project(":misc"))
    implementation(project(":model"))
    implementation(project(":net"))
    implementation(project(":archive"))
    implementation(project(":anvilrw"))

    val lwjglVersion = libs.versions.lwjgl.get()
    listOf("", "glfw", "opengl").forEach { component ->
        val suffix = component.takeIf(String::isNotEmpty)?.let { "-$it" }.orEmpty()
        implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion")
        implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion:natives-windows")
    }

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "calebxzhou.rdi.client.MainKt"
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.slf4j:slf4j-api:${libs.versions.slf4j.api.get()}")
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("com.ibm.icu:icu4j:${libs.versions.icu4j.get()}")
        force("it.unimi.dsi:fastutil:${libs.versions.fastutil.get()}")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (!name.endsWith("JavaWithJavac")) {
        options.release.set(javaVersionInt)
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Removed custom upToDateWhen — it was breaking Kotlin's incremental compilation,
// causing full recompilation on every hot reload instead of only changed files.



idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

val runDir = layout.projectDirectory.dir("run").asFile
val hotRunBaseJvmArgs = listOf(
    "-Drdi.debug=true",
    "-Drdi.noHttps=true",
    "-Drdi.noUpdate=true",
    //"-Drdi.netMetrics=true",
    "-Drdi.account=eyJfaWQiOiI2OGIzMTRiYmFkYWY1MmRkYWI5NmI1ZWQiLCJuYW1lIjoiMTIzMTIzIiwicHdkIjoiMTIzQEBAIiwicXEiOiIxMjMxMjMifQ=="
)

tasks.withType<JavaExec>().configureEach {
    workingDir = runDir
    doFirst {
        runDir.mkdirs()
    }
}

tasks.matching { it.name == "hotRun" || it.name == "hotDev" }.configureEach {
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    if (this is JavaExec) {
        systemProperties = System.getProperties().filter { it.key.toString().startsWith("rdi.") } as MutableMap<String, Any?>
        jvmArgs(hotRunBaseJvmArgs)
    }
}
tasks.register<Sync>("desktopInstallLibs") {
    dependsOn("jar")
    from(configurations.runtimeClasspath)
    from(tasks.named<Jar>("jar"))
    into(layout.buildDirectory.dir("install/ui/lib"))
}

tasks.named<Jar>("jar") {
    archiveFileName.set("rdi-5-ui.jar")
}



fun registerCopyTask(name: String, extraDestinations: List<String> = emptyList()) {
    val baseDestinations = listOf(
        file("../../server/master/run/client-libs/lib"),
        //     File(System.getProperty("user.home"), "Documents/rdi5ship/lib")
    )
    val destinationDirs = baseDestinations + extraDestinations.map { file(it) }
    val syncTaskNames = destinationDirs.mapIndexed { index, targetDir ->
        val syncTaskName = "${name}Sync$index"
        tasks.register<Sync>(syncTaskName) {
            dependsOn("desktopInstallLibs")
            from(layout.buildDirectory.dir("install/ui/lib"))
            into(targetDir)
        }
        syncTaskName
    }

    tasks.register(name) {
        dependsOn(syncTaskNames)
    }
}

registerCopyTask("出core2-local")
registerCopyTask("出core2-release", listOf("\\\\rdi\\rdi55\\ihq\\client-libs\\lib"))

tasks.register("makeShipPack") {
    notCompatibleWithConfigurationCache("uses project file operations and external process execution at execution time")
    val shipDir = File(System.getProperty("user.home"), "Documents/rdi5ship")
    val filesNeed = listOf("lib", "启动.exe", "备用启动.exe","fonts")
    val archiveFile = File(shipDir, "rdi-${version}.7z")
    group = "distribution"
    description = "Create shipping 7z archive in Documents/rdi5ship with LZMA2 multi-thread compression."

    doFirst {
        if (!shipDir.exists()) throw GradleException("未找到 ship 目录: $shipDir")
        filesNeed.forEach { name ->
            val target = File(shipDir, name)
            if (!target.exists()) throw GradleException("缺少文件或目录: $target")
        }
    }
    doLast {
        val sevenZipExecutable = sequenceOf(
            File("C:/Program Files/7-Zip/7z.exe"),
            File("C:/Program Files (x86)/7-Zip/7z.exe")
        ).firstOrNull { it.exists() && it.isFile }?.absolutePath ?: "7z"

        if (archiveFile.exists() && !archiveFile.delete()) {
            throw GradleException("无法删除旧压缩包: $archiveFile")
        }

        val process = ProcessBuilder(
            sevenZipExecutable,
            "a",
            "-t7z",
            "-m0=lzma2",
            "-mx=9",
            "-mmt=on",
            archiveFile.absolutePath,
            "lib",
            "fonts",
            "启动.exe",
            "备用启动.exe",
        ).directory(shipDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("7z打包失败，退出码: $exitCode")
        }
    }
}

