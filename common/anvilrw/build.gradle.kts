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
    api(libs.knbt)
}
