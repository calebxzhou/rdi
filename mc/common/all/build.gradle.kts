import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("jvm") version "2.4.0"
    java
}

group = "calebxzhou.rdi.mc.common"
version = "0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("com.google.code.gson:gson:2.10")
    implementation("com.neovisionaries:nv-websocket-client:2.14")
}

fun registerServerCopyTask(taskName: String, project: Project, release: Boolean) {
    project.tasks.register(taskName) {
        dependsOn(project.tasks.named("build"))

        val mcVersionSlug = project.version.toString().replace("5-mc-server-", "")
        val artifact = project.layout.buildDirectory.file("libs/rdi-${project.version}.jar")
        val destinationDirs = buildList {
            add(project.layout.projectDirectory.dir("../../master/run/game-libs/$mcVersionSlug/mods"))
            if (release) {
                add(project.layout.projectDirectory.dir("\\\\rdi\\rdi55\\ihq\\game-libs\\$mcVersionSlug\\mods"))
            }
        }

        doLast {
            val jarFile = artifact.get().asFile
            if (!jarFile.exists()) {
                throw GradleException("未找到构建产物: $jarFile")
            }

            destinationDirs.forEach { target ->
                val targetDir = target.asFile
                targetDir.mkdirs()
                Files.copy(
                    jarFile.toPath(),
                    targetDir.resolve(jarFile.name).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }
}

extra["registerServerCopyTask"] = { targetProject: Project ->
    registerServerCopyTask("出core-local", targetProject, false)
    registerServerCopyTask("出core-release", targetProject, true)
}
