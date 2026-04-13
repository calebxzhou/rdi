plugins {
    kotlin("jvm")
}

group = "calebxzhou.rdi.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.lionsoul:ip2region:3.3.7")
}

kotlin {
    jvmToolchain(21)
}
