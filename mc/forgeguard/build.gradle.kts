plugins {
    java
}

group = "calebxzau.rdi.mc.forgeguard"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.jar {
    archiveFileName.set("forgeguard.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Premain-Class" to "forgeguard.Agent",
            "Agent-Class" to "forgeguard.Agent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
        )
    }

    from({
        configurations.runtimeClasspath.get().map { zipTree(it) }
    })
}
