plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
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
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}
