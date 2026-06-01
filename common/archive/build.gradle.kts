plugins {
    `java-library`
    kotlin("jvm")
}

group = "calebxzhou.rdi.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.mykotutils.std)
    implementation(libs.commons.compress)
    compileOnly(libs.zstd.jni)
}
