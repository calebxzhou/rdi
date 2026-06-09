plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    `java-library`
}

group = "calebxzhou.mykotutils"
version = "0.1"


repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
}
