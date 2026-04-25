import groovy.lang.Closure
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import org.gradle.api.tasks.SourceSetContainer
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

val defaultPlayArgRaw = """
http://127.0.0.1:65231
127.0.0.1:65230
测试测试12123大世界
25565
68b314bb-adaf-52dd-ab96-b5ed00000000
dev1
""".trimIndent()

val anotherPlayArgRaw = """
http://127.0.0.1:65231
127.0.0.1:65230
另一个测试世界
25565
68b314bb-adaf-52dd-ab96-b5ee00000000
dev2
""".trimIndent()

fun encodePlayArg(raw: String) = Base64.encode(raw.encodeToByteArray())

val requestedTasks = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }.toSet()
val selectedPlayArg = when {
    "runClient25Another" in requestedTasks -> anotherPlayArgRaw
    else -> defaultPlayArgRaw
}

tasks.named<RunMinecraftTask>("runClient25") {
    systemProperty("rdi.play", encodePlayArg(selectedPlayArg))
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

tasks.register("runClient25Another") {
    group = "minecraft"
    description = "Run Minecraft client with another RDI play arg"
    dependsOn("runClient25")
}

@Suppress("UNCHECKED_CAST")
val registerClientCopyTask = commonProject.extensions.extraProperties["registerClientCopyTask"] as Closure<*>
registerClientCopyTask.call(project)
