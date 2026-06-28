import org.gradle.api.GradleException
import org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule.module
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.compileOnly
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

val appVersion = libs.versions.app.get()
val appVersionCode = libs.versions.version.code.get().toInt()
val javaVersion = libs.versions.java.get()
val javaVersionInt = javaVersion.toInt()
val jvmTargetVersion = JvmTarget.fromTarget(javaVersion)
val devMode = providers.gradleProperty("rdi.devMode")
    .map(String::toBoolean)
    .orElse(true)
project.version = appVersion

val javaToolchainService = project.extensions.getByType<JavaToolchainService>()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
    idea
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
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

    sourceSets.all {
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.m3)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":misc"))
                implementation(project(":model"))
                implementation(project(":net"))
                implementation(project(":archive"))
                implementation(project(":ai"))
                // Source: https://mvnrepository.com/artifact/org.joml/joml
                implementation(libs.joml)
                implementation(libs.minecraft.auth)
                implementation(libs.bundles.ktor.common)
                implementation(libs.bundles.ktor.json.client)
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.mykotutils.std)
                implementation(libs.mykotutils.log)
                implementation(libs.bundles.mongodb)
                implementation(libs.navigation.compose)
                implementation(libs.compose.material3)
                implementation(libs.tomlkt)
                implementation("com.github.oshi:oshi-core:${libs.versions.oshi.common.get()}") {
                    exclude(group = "net.java.dev.jna")
                }
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.navigation.compose)
                implementation(libs.compose.material3.desktop)
               // implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}:win_amd64")
    
                // JNA/Oshi dependencies (JNA excluded from commonMain)
                implementation(libs.jna)
                implementation(libs.jna.platform)


                val lwjglVersion = libs.versions.lwjgl.get()
                val components = listOf("", "glfw", "opengl")
                components.forEach { component ->
                    val suffix = if (component.isNotEmpty()) "-$component" else ""
                    implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion")
                    implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion:natives-windows")
                }

                implementation(libs.bundles.netty.desktop)
                implementation(project(":misc"))
                implementation(project(":model"))
                implementation(project(":net"))
                implementation(project(":archive"))
                implementation(project(":anvilrw"))
                implementation(project(":ai"))


                //runtimeOnly(libs.hotswap.agent.core)
                implementation(libs.oshi.core.desktop)
                implementation(libs.logback.classic)
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.snakeyaml)


                implementation(libs.mykotutils.std)
                implementation(libs.mykotutils.log)

                implementation(libs.ktor.client.okhttp)
                implementation(libs.bundles.ktor.common)
                implementation(libs.ktor.client.auth)
                implementation(libs.jsoup)
                implementation(libs.bundles.mongodb)
                implementation(libs.caffeine)

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.bundles.ktor.json.client)
                implementation(libs.maven.artifact)
                implementation(libs.zstd.jni)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":misc"))
                implementation(project(":model"))
                implementation(project(":net"))
                implementation(project(":archive"))
                implementation(project(":ai"))
                implementation(libs.ktor.client.android)
                implementation(libs.bundles.ktor.json.client)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.activity.compose)
                // Use slf4j-simple on Android instead of logback (logback uses Class.getModule() which doesn't exist on Android)
                implementation(libs.slf4j.simple)
                implementation(libs.snakeyaml)
                // JNA AAR includes Android native .so files (regular JAR only has desktop natives)
                implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
                // JNA Platform JAR (interfaces only, safe for Android if excluded JNA JAR)
                implementation("net.java.dev.jna:jna-platform:${libs.versions.jna.get()}") {
                    exclude(module = "jna")
                }
                // oshi with JNA JAR excluded (AAR above replaces it)
                implementation("com.github.oshi:oshi-core:${libs.versions.oshi.android.get()}") {
                    exclude(group = "net.java.dev.jna")
                }
                implementation("com.github.luben:zstd-jni:${libs.versions.zstd.get()}@aar")
            }
            // Exclude logback from all transitive dependencies in Android
            configurations.all {
                if (name.contains("android", ignoreCase = true) || name.contains("Android")) {
                    exclude(group = "ch.qos.logback", module = "logback-classic")
                    exclude(group = "ch.qos.logback", module = "logback-core")
                    exclude(group = "io.netty", module = "netty-codec-native-quic")
                    exclude(group = "io.netty", module = "netty-transport-native-epoll")
                    exclude(group = "io.netty", module = "netty-transport-native-io_uring")
                    exclude(group = "io.netty", module = "netty-transport-native-kqueue")
                    exclude(group = "io.netty", module = "netty-resolver-dns-native-macos")
                }
            }
        }
    }
}


android {
    namespace = "calebxzhou.rdi.client"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "calebxzhou.rdi.client"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }

    sourceSets["main"].assets.srcDir("src/commonMain/resources")

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(javaVersionInt)
        targetCompatibility = JavaVersion.toVersion(javaVersionInt)
    }

   /* signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "Release signing is not configured. " +
                        "Provide client/ui/keystore.properties or rdi.signing.* Gradle properties."
                )
            }
        }
    }*/

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/license/**",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "META-INF/io.netty.versions.properties",
                "META-INF/MANIFEST.MF"
            )
        }
    }
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

tasks.matching { it.name == "hotRunDesktop" || it.name == "hotDevDesktop" }.configureEach {
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    if (this is JavaExec) {
        systemProperties = System.getProperties().filter { it.key.toString().startsWith("rdi.") } as MutableMap<String, Any?>
        jvmArgs(hotRunBaseJvmArgs)
    }
}
tasks.register<Sync>("desktopInstallLibs") {
    dependsOn("desktopJar")
    from(configurations.getByName("desktopRuntimeClasspath"))
    from(tasks.named<Jar>("desktopJar"))
    into(layout.buildDirectory.dir("install/ui/lib"))
}

tasks.named<Jar>("desktopJar") {
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
    val filesNeed = listOf("lib", "启动.exe", "fonts")
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
            "启动.exe"
        ).directory(shipDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("7z打包失败，退出码: $exitCode")
        }
    }
}

/*tasks.register("assembleSignedRelease") {
    group = "build"
    description = "Build a signed release APK. Requires signing config values."
    dependsOn("assembleRelease")
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing not configured. Set storeFile/storePassword/keyAlias/keyPassword " +
                    "in client/ui/keystore.properties or rdi.signing.* properties."
            )
        }
    }
}*/
