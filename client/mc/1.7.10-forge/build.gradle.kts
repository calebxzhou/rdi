import groovy.lang.Closure
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.classpath.Instrumented.systemProperty
import kotlin.io.encoding.Base64

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
    val playArg = Base64.encode("http://127.0.0.1:65231\n127.0.0.1:65230\n测试测试12123大世界\n25565\n68b314bb-adaf-52dd-ab96-b5ed00000000\n哇塞的哇塞的".encodeToByteArray())
    systemProperty("rdi.play", playArg)
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
