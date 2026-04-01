import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val ktorVersion = "3.4.0"
val version = "5.11.8"
project.version = version

plugins {
    kotlin("multiplatform") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.compose") version "1.10.1"
    id("com.android.application") version "8.12.0"
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
    jvm("desktop")

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                val composeVersion = "1.10.1"
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                implementation("org.jetbrains.compose.material:material:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui:$composeVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation(project(":common"))
                // Source: https://mvnrepository.com/artifact/org.joml/joml
                implementation("org.joml:joml:1.10.8")
                implementation("net.raphimc:MinecraftAuth:5.0.0")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-client-encoding:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.github.oshai:kotlin-logging-jvm:8.0.01")
                implementation("calebxzhou.mykotutils:std:0.1")
                implementation("calebxzhou.mykotutils:log:0.1")
                implementation("io.netty:netty-all:4.2.7.Final")
                implementation("org.mongodb:bson:5.6.4")
                implementation("org.mongodb:bson-kotlinx:5.6.4")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
                implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha05")
                implementation("net.peanuuutz.tomlkt:tomlkt:0.5.0")
                implementation("com.mikepenz:multiplatform-markdown-renderer:0.39.2")
                implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.39.2")
                implementation("com.github.oshi:oshi-core:6.9.3") {
                    exclude(group = "net.java.dev.jna")
                }
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
                implementation("org.jetbrains.compose.material3:material3-desktop:1.10.0-alpha05")
    
                // JNA/Oshi dependencies (JNA excluded from commonMain)
                implementation("net.java.dev.jna:jna:5.18.1")
                implementation("net.java.dev.jna:jna-platform:5.18.1")


                val lwjglVersion = "3.3.3"
                val components = listOf("", "glfw", "opengl")
                components.forEach { component ->
                    val suffix = if (component.isNotEmpty()) "-$component" else ""
                    implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion")
                    implementation("org.lwjgl:lwjgl${suffix}:$lwjglVersion:natives-windows")
                }

                implementation("io.netty:netty-all:4.2.7.Final")
                implementation(project(":common"))


                runtimeOnly("org.hotswapagent:hotswap-agent-core:2.0.1")
                implementation("com.github.oshi:oshi-core:6.10.0")
                implementation("ch.qos.logback:logback-classic:1.5.32")
                implementation("io.github.oshai:kotlin-logging-jvm:8.0.01")
                implementation("org.yaml:snakeyaml:2.6")


                implementation("calebxzhou.mykotutils:std:0.1")
                implementation("calebxzhou.mykotutils:log:0.1")

                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-auth:$ktorVersion")
                implementation("io.ktor:ktor-client-encoding:$ktorVersion")
                implementation("org.jsoup:jsoup:1.22.1")
                implementation("org.mongodb:bson:5.6.4")
                implementation("org.mongodb:bson-kotlinx:5.6.4")
                implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-websockets:$ktorVersion")
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("org.apache.maven:maven-artifact:3.9.11")
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation("io.netty:netty-all:4.2.7.Final")
                implementation("io.ktor:ktor-client-android:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("androidx.activity:activity-compose:1.12.4")
                // Use slf4j-simple on Android instead of logback (logback uses Class.getModule() which doesn't exist on Android)
                implementation("org.slf4j:slf4j-simple:2.0.17")
                implementation("org.yaml:snakeyaml:2.6")
                // JNA AAR includes Android native .so files (regular JAR only has desktop natives)
                implementation("net.java.dev.jna:jna:5.18.1@aar")
                // JNA Platform JAR (interfaces only, safe for Android if excluded JNA JAR)
                implementation("net.java.dev.jna:jna-platform:5.18.1") {
                    exclude(module = "jna")
                }
                // oshi with JNA JAR excluded (AAR above replaces it)
                implementation("com.github.oshi:oshi-core:6.9.3") {
                    exclude(group = "net.java.dev.jna")
                }
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
    compileSdk = 36

    defaultConfig {
        applicationId = "calebxzhou.rdi.client"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = version
    }

    sourceSets["main"].assets.srcDir("src/commonMain/resources")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
            force("org.slf4j:slf4j-api:2.0.16")
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("com.ibm.icu:icu4j:76.1")
        force("it.unimi.dsi:fastutil:8.5.15")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
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
    "-Drdi.account=eyJfaWQiOiI2OGIzMTRiYmFkYWY1MmRkYWI5NmI1ZWQiLCJuYW1lIjoiMTIzMTIzIiwicHdkIjoiMTIzQEBAIiwicXEiOiIxMjMxMjMifQ=="
)

tasks.withType<JavaExec>().configureEach {
    workingDir = runDir
    doFirst {
        runDir.mkdirs()
    }
}

tasks.matching { it.name == "hotRunDesktop" }.configureEach {
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
    tasks.register(name) {
        notCompatibleWithConfigurationCache("uses project file operations at execution time")

        dependsOn("desktopInstallLibs")

        val baseDestinations = listOf(
            file("../../server/master/run/client-libs/lib"),
       //     File(System.getProperty("user.home"), "Documents/rdi5ship/lib")
        )
        val destinationDirs = baseDestinations + extraDestinations.map { file(it) }

        doLast {
            val srcDir = layout.buildDirectory.dir("install/ui/lib").get().asFile
            if (!srcDir.exists()) {
                throw GradleException("未找到目录: $srcDir")
            }
            val srcFileNames = srcDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

            destinationDirs.forEach { targetDir ->
                targetDir.mkdirs()
                copy {
                    from(srcDir)
                    into(targetDir)
                }
                targetDir.listFiles()?.forEach { file ->
                    if (file.name !in srcFileNames) {
                        val deleted = file.deleteRecursively()
                        if (deleted) {
                            println("Deleted unused library: ${file.name}")
                        } else {
                            println("Failed to delete: ${file.name}")
                        }
                    }
                }
            }
        }
    }
}

registerCopyTask("出core2-local")
registerCopyTask("出core2-release", listOf("\\\\rdi\\rdi55\\ihq\\client-libs\\lib"))

tasks.register<Zip>("makeShipPack") {
    notCompatibleWithConfigurationCache("uses project file operations at execution time")
    val shipDir = File(System.getProperty("user.home"), "Documents/rdi5ship")
    val filesNeed = listOf("lib", "双击启动.cmd", "fonts", "jre","mcb")

    group = "distribution"
    description = "Create shipping zip in Documents/rdi5ship."
    //dependsOn("出core2-local")

    destinationDirectory.set(shipDir)
    archiveFileName.set("rdi5ship-${version}.zip")

    from(File(shipDir, "lib")) { into("lib") }
    from(File(shipDir, "fonts")) { into("fonts") }
    from(File(shipDir, "jre")) { into("jre") }
    from(File(shipDir, "mcb")) { into("mc") }
    from(shipDir) { include("双击启动.cmd") }

    doFirst {
        if (!shipDir.exists()) throw GradleException("未找到 ship 目录: $shipDir")
        filesNeed.forEach { name ->
            val target = File(shipDir, name)
            if (!target.exists()) throw GradleException("缺少文件或目录: $target")
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
