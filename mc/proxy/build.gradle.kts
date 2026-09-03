plugins {
    `java-library`
    kotlin("jvm")
}

group = "calebxzhou.rdi.mc.proxy"
//version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly(libs.netty.buffer)
    compileOnly(libs.netty.codec)
    compileOnly(libs.netty.common)
    compileOnly(libs.netty.transport)

    testImplementation(kotlin("test"))
    testImplementation(libs.netty.buffer)
    testImplementation(libs.netty.codec)
    testImplementation(libs.netty.common)
    testImplementation(libs.netty.transport)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
base {
    archivesName.set("rdi-mc-proxy")
}