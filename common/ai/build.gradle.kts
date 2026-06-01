plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "calebxzhou.rdi.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}



dependencies {
    api(project(":misc"))
    api(project(":net"))
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
