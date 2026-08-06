import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import sun.jvmstat.monitor.MonitoredVmUtil.mainClass
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.jar.JarFile

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
    id("org.gradlex.extra-java-module-info") version "1.14.2"
    idea
}

extraJavaModuleInfo {
    failOnMissingModuleInfo.set(false)
    skipLocalJars.set(true)

    module("org.bytedeco:javacv", "org.bytedeco.javacv") {
        patchRealModule()
        exportAllPackages()
        requires("java.desktop")
        requiresTransitive("org.bytedeco.javacpp")
        requires("org.bytedeco.ffmpeg")
    }
}

configurations.configureEach {
    if (name.startsWith("composeHotReloadDev")) {
        attributes.attribute(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
            ArtifactTypeDefinition.JAR_TYPE
        )
        extraJavaModuleInfo.activate(this)
    }
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
    archivesName.set("rdi-ui")
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
    implementation(kotlin("reflect"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.desktop)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.joml)
    implementation(libs.minecraft.auth)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.mykotutils.std)
    implementation(libs.mykotutils.log)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.oshi.core.desktop)
    implementation(libs.logback.classic)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.bundles.ktor.common)
    implementation(libs.ktor.client.auth)
    implementation(libs.bundles.ktor.json.client)
    implementation(libs.jsoup)
    implementation(libs.bundles.mongodb)
    implementation(libs.caffeine)
    implementation(libs.maven.artifact)
    implementation(libs.zstd.jni) {
        artifact { classifier = "win_amd64" }
    }

    implementation(project(":assets"))
    runtimeOnly(project(":assets:fonts"))
    implementation(project(":code-editor"))
    implementation(project(":database"))
    implementation(project(":misc"))
    implementation(project(":model"))
    implementation(project(":net"))
    implementation(project(":archive"))
    implementation(project(":anvilrw"))
    implementation(project(":blessing-skin"))
    implementation(project(":mediaproc"))
    implementation(project(":pack-proc"))
    implementation(project(":mclaunch"))
    implementation(project(":mod-catalog"))
    implementation(project(":webview2"))
    implementation(project(":forgeguard"))
    implementation(project(":local-mc-proxy"))

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
        mainClass = "calebxzau.rdi.client.MainKt"
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
   // "-Drdi.noUpdate=true",
    //"-Drdi.netMetrics=true",
   // "-Dskiko.renderApi=OPENGL",
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
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("jar")
    from(configurations.runtimeClasspath)
    from(tasks.named<Jar>("jar"))
    into(layout.buildDirectory.dir("install/ui/lib"))
}

tasks.named<Jar>("jar") {
    archiveFileName.set("rdi-ui.jar")
}


val uiInstallLibDir = layout.buildDirectory.dir("install/ui/lib")
val uiReleaseOutputDir = layout.buildDirectory.dir("ui-releases/$appVersion")

fun jsonQuote(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun sha1(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun sha1(file: File): String = file.inputStream().use { sha1(it) }

fun readUiManifest(file: File): Map<String, String> {
    val parsed = JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("UI库manifest不是JSON对象: $file")
    return parsed.entries.associate { entry ->
        val name = entry.key as? String
            ?: throw GradleException("UI库manifest包含非法文件名: $file")
        val hash = entry.value as? String
            ?: throw GradleException("UI库manifest包含非法hash: $file")
        name to hash
    }
}

fun validateUiReleaseArchive(archive: File, expectedHashes: Map<String, String>) {
    ZipFile(archive).use { zip ->
        val entries = mutableListOf<ZipEntry>()
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) entries += enumeration.nextElement()

        if (entries.isEmpty() || entries.none { it.name.endsWith(".jar", ignoreCase = true) }) {
            throw GradleException("UI库ZIP缺少JAR: $archive")
        }
        if (entries.any { entry ->
                entry.isDirectory || entry.name.isBlank() || entry.name == "." || entry.name == ".." ||
                    entry.name.contains('/') || entry.name.contains('\\') || entry.name.contains(':')
            }) {
            throw GradleException("UI库ZIP包含非法entry: $archive")
        }
        val entryNames = entries.map { it.name }
        if (entryNames.size != entryNames.toSet().size || entryNames.toSet() != expectedHashes.keys) {
            throw GradleException("UI库ZIP和manifest文件列表不一致: $archive")
        }
        entries.forEach { entry ->
            val actualHash = zip.getInputStream(entry).use { sha1(it) }
            if (!actualHash.equals(expectedHashes[entry.name], ignoreCase = true)) {
                throw GradleException("UI库ZIP文件hash不匹配: ${entry.name}")
            }
        }
    }
}

val packageUiRelease = tasks.register("packageUiRelease") {
    dependsOn("desktopInstallLibs")
    notCompatibleWithConfigurationCache("invokes 7z and validates the generated ZIP")
    group = "distribution"
    description = "Generate the versioned UI library ZIP, manifest and latest pointer."

    doLast {
        val sourceDir = uiInstallLibDir.get().asFile
        if (!sourceDir.isDirectory) throw GradleException("未找到UI运行库目录: $sourceDir")
        val files = sourceDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty() || files.none { it.name.endsWith(".jar", ignoreCase = true) }) {
            throw GradleException("UI运行库目录缺少JAR: $sourceDir")
        }
        val uiJar = sourceDir.resolve("rdi-ui.jar")
        if (!uiJar.isFile) throw GradleException("UI运行库目录缺少rdi-ui.jar: $sourceDir")
        val implementationVersion = JarFile(uiJar).use {
            it.manifest?.mainAttributes?.getValue("Implementation-Version")
        }
        if (implementationVersion != appVersion) {
            throw GradleException(
                "rdi-ui.jar的Implementation-Version不匹配，期望$appVersion，实际${implementationVersion ?: "未知"}"
            )
        }

        val hashes = files.associate { it.name to sha1(it) }
        val outputDir = uiReleaseOutputDir.get().asFile
        outputDir.mkdirs()
        val archive = outputDir.resolve("$appVersion.zip")
        val manifest = outputDir.resolve("$appVersion.json")
        val latest = outputDir.resolve("latest.txt")

        manifest.writeText(
            hashes.entries.joinToString(",\n", "{\n", "\n}\n") { (name, hash) ->
                "    ${jsonQuote(name)}: ${jsonQuote(hash)}"
            }
        )
        if (archive.exists() && !archive.delete()) {
            throw GradleException("无法覆盖旧UI库ZIP: $archive")
        }

        val sevenZipExecutable = sequenceOf(
            File("C:/Program Files/7-Zip/7z.exe"),
            File("C:/Program Files (x86)/7-Zip/7z.exe")
        ).firstOrNull { it.isFile }?.absolutePath ?: "7z"
        val process = ProcessBuilder(
            sevenZipExecutable,
            "a",
            "-tzip",
            "-mx=0",
            archive.absolutePath,
            *files.map { it.name }.toTypedArray()
        ).directory(sourceDir)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw GradleException("7z生成UI库ZIP失败，退出码:$exitCode")

        val parsedHashes = readUiManifest(manifest)
        if (parsedHashes != hashes) throw GradleException("UI库manifest内容校验失败: $manifest")
        validateUiReleaseArchive(archive, parsedHashes)
        latest.writeText("$appVersion\n")
        logger.lifecycle("已生成UI库发布包: $outputDir")
    }
}


fun registerCopyTask(name: String, extraDestinationRoots: List<String> = emptyList()) {
    val baseDestinationRoots = listOf(
        file("../../server/master/run/client-libs"),
        //     File(System.getProperty("user.home"), "Documents/rdi5ship/lib")
    )
    val destinationRoots = baseDestinationRoots + extraDestinationRoots.map { file(it) }
    val copyTaskNames = destinationRoots.flatMapIndexed { index, destinationRoot ->
        //val targetDir = destinationRoot.resolve("lib")
        val releaseDir = destinationRoot.resolve("releases")
        val syncTaskName = "${name}Sync$index"
        /*val libSync = tasks.register<Sync>(syncTaskName) {
            dependsOn("desktopInstallLibs")
            from(uiInstallLibDir)
            into(targetDir)
        }*/
        val releaseFilesSync = tasks.register<Copy>("${name}Releases$index") {
            dependsOn(packageUiRelease)
            from(uiReleaseOutputDir) {
                include("$appVersion.zip", "$appVersion.json")
            }
            into(releaseDir)
        }
        val latestSync = tasks.register<Copy>("${name}Latest$index") {
            dependsOn(releaseFilesSync)
            from(uiReleaseOutputDir) {
                include("latest.txt")
            }
            into(releaseDir)
        }
        listOf(releaseFilesSync, latestSync)
    }

    tasks.register(name) {
        dependsOn(copyTaskNames)
    }
}

registerCopyTask("出core2-local")
registerCopyTask("出core2-release", listOf("\\\\rdi\\rdi55\\ihq\\client-libs"))
val dotnetReleaseCmd = listOf(
    "dotnet",
    "publish",
    "-c",
    "Release",
    "-r",
    "win-x64",
    "-p:PublishAot=true",
    "-p:StripSymbols=true",
)
fun registerUpdaterTask(name: String, destinationDirs: List<File>) {

    tasks.register(name) {
        notCompatibleWithConfigurationCache("invokes dotnet and zstd, then copies the updater")
        group = "distribution"
        description = "Build, compress and publish the Windows updater."

        val updaterDir = layout.projectDirectory.dir("updater").asFile
        val updaterExe = updaterDir.resolve("bin/Release/net10.0/win-x64/native/updater.exe")
        val compressedUpdater = updaterExe.parentFile.resolve("updater.exe.zst")
        val updaterSha1File = updaterExe.parentFile.resolve("updater.exe.sha1")

        doLast {
            fun runProcess(command: List<String>, workingDirectory: File? = null): Int {
                logger.lifecycle("> ${command.joinToString(" ")}")
                val process = ProcessBuilder(command).apply {
                    workingDirectory?.let { directory(it) }
                }.start()
                val stdoutThread = Thread {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { logger.lifecycle("[stdout] $it") }
                    }
                }.apply { start() }
                val stderrThread = Thread {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { logger.lifecycle("[stderr] $it") }
                    }
                }.apply { start() }
                val exitCode = process.waitFor()
                stdoutThread.join()
                stderrThread.join()
                return exitCode
            }

            val publishExitCode = runProcess(
                dotnetReleaseCmd,
                updaterDir
            )
            if (publishExitCode != 0) {
                throw GradleException("dotnet publish updater失败，退出码: $publishExitCode")
            }

            if (!updaterExe.isFile) {
                throw GradleException("未找到updater: $updaterExe")
            }

            val updaterSha1 = MessageDigest.getInstance("SHA-1")
                .digest(updaterExe.readBytes())
                .joinToString("") { "%02x".format(it) }
            updaterSha1File.writeText(updaterSha1)

            val compressExitCode = runProcess(
                listOf(
                    "zstd",
                    "-f",
                    updaterExe.absolutePath,
                    "-o",
                    compressedUpdater.absolutePath,
                )
            )
            if (compressExitCode != 0) {
                throw GradleException("zstd压缩updater失败，退出码: $compressExitCode")
            }

            destinationDirs.forEach { destinationDir ->
                destinationDir.mkdirs()
                compressedUpdater.copyTo(destinationDir.resolve(compressedUpdater.name), overwrite = true)
                updaterSha1File.copyTo(destinationDir.resolve(updaterSha1File.name), overwrite = true)
                logger.lifecycle("已发布updater到$destinationDir")
            }
        }
    }
}

val localUpdaterDestination = file("../../server/master/run/client-libs/updaters")
registerUpdaterTask("出updater-local", listOf(localUpdaterDestination))
registerUpdaterTask(
    "出updater-release",
    listOf(
        localUpdaterDestination,
        file("\\\\rdi\\rdi55\\ihq\\client-libs\\updaters"),
    )
)
/*

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
*/

tasks.register("makeShipPackZst") {
    notCompatibleWithConfigurationCache("uses project file operations and external process execution at execution time")
    val shipDir = File(System.getProperty("user.home"), "Documents/rdi5ship")
    val filesNeed = listOf( "lib", "start.exe")
    val temporaryTar = layout.buildDirectory.file("installer/assets/client.tar").get().asFile
    val archiveFile = layout.projectDirectory.file("installer/assets/client.tar.zst").asFile
    group = "distribution"
    description = "Create the installer client.tar.zst from Documents/rdi5ship."

    doLast {
        filesNeed.forEach { name ->
            val target = shipDir.resolve(name)
            if (!target.exists()) throw GradleException("缺少文件或目录:$target")
        }

        temporaryTar.parentFile.mkdirs()
        archiveFile.parentFile.mkdirs()
        try {
            val tarExitCode = ProcessBuilder(
                "tar",
                "-cf",
                temporaryTar.absolutePath,
                "-C",
                shipDir.absolutePath,
                *filesNeed.toTypedArray(),
            ).inheritIO()
                .start()
                .waitFor()
            if (tarExitCode != 0) throw GradleException("tar打包失败，退出码:$tarExitCode")

            val zstdExitCode = ProcessBuilder(
                "zstd",
                "-T0",
                "-22",
                "-f",
                temporaryTar.absolutePath,
                "-o",
                archiveFile.absolutePath,
            ).inheritIO()
                .start()
                .waitFor()
            if (zstdExitCode != 0) throw GradleException("zstd压缩失败，退出码:$zstdExitCode")
        } finally {
            temporaryTar.delete()
        }
    }
}

tasks.register("makeShipInstaller") {
    dependsOn("makeShipPackZst")
    notCompatibleWithConfigurationCache("invokes dotnet in the installer directory")
    group = "distribution"
    description = "Create the shipping pack and publish the Windows installer."

    doLast {
        val exitCode = ProcessBuilder(dotnetReleaseCmd)
            .directory(layout.projectDirectory.dir("installer").asFile)
            .inheritIO()
            .start()
            .waitFor()
        if (exitCode != 0) throw GradleException("dotnet publish installer失败，退出码:$exitCode")
    }
}

