import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

group = "calebxzhou.rdi.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting
        val desktopMain by getting
        val androidMain by getting

        val jvmCommonMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.lionsoul:ip2region:3.3.7")
            }
        }

        desktopMain.dependsOn(jvmCommonMain)
        androidMain.dependsOn(jvmCommonMain)
    }
}

android {
    namespace = "calebxzhou.rdi.common.ip2region"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets["main"].resources.srcDir("src/commonMain/resources")
}
