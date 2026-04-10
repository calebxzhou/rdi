import groovy.lang.Closure
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
evaluationDependsOn(":c-mc-common")
val commonProject = project(":c-mc-common")
val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer

base {
    archivesName = "rdi"
}

version = "5-mc-client-1.7.10-forge"
group = "calebxzhou.rdi"

sourceSets.named("main") {
    java.srcDir(commonProject.file("src/main/java"))
    resources.srcDir(commonProject.file("src/main/resources"))
}

tasks.named<RunMinecraftTask>("runClient25") {
    systemProperty("rdi.ihq.url", "http://127.0.0.1:65231")
    systemProperty("rdi.game.ip", "127.0.0.1:65230")
    systemProperty("rdi.host.name", "测试测试12123主机")
    systemProperty("rdi.host.port", "25565")
    systemProperty("mixin.hotSwap", "true")

    extraArgs.addAll(
        "--mixin",
        "mixins.rdi.json",
        "--width",
        "2560",
        "--height",
        "1440",
    )
}

@Suppress("UNCHECKED_CAST")
val registerClientCopyTask = commonProject.extensions.extraProperties["registerClientCopyTask"] as Closure<*>
registerClientCopyTask.call(project)
