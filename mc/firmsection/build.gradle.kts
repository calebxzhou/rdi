plugins {
    kotlin("jvm") version "2.4.0"
}

group = "calebxzhou.rdi.mc.firmsection"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    // jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
