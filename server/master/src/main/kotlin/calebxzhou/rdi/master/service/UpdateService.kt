package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.master.net.param
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.service.UpdateService.mcCoreModFile
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

private val CLIENT_LIBS_DIR = File("client-libs").also { it.mkdirs() }
private val updatersDir get() = CLIENT_LIBS_DIR.resolve("updaters").also { it.mkdirs() }
private val updaterWin get() = updatersDir.resolve("updater.exe.zst")
private val updaterWinHash get() = updatersDir.resolve("updater.exe.sha1")
private val uiLibsDir get() = CLIENT_LIBS_DIR.resolve("lib")

private val mcCoreFilePrefix = "rdi-5-mc-client"

fun Route.updateRoutes() = route("/update") {
    get("/ui/libs") {
        if (!uiLibsDir.exists()) uiLibsDir.mkdirs()
        val entries = uiLibsDir.listFiles()
            ?.filter { it.isFile }
            ?.associate { it.name to it.sha1 }
            ?: emptyMap()
        response(data = entries)
    }
    get("/ui/lib/{name}") {
        val name = param("name")
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw RequestError("非法文件名")
        }
        val file = uiLibsDir.resolve(name)
        if (!file.exists() || !file.isFile) throw RequestError("无此文件")
        call.respondFile(file)
    }
    get("/ui/ver"){
        val coreFile = uiLibsDir.resolve("rdi-5-ui.jar")
        if (!coreFile.exists() || !coreFile.isFile) throw RequestError("找不到UI核心文件")
        val version = java.util.jar.JarFile(coreFile).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Implementation-Version")
        } ?: throw RequestError("无法读取版本号")
        response(data = version)
    }
    /*get("/ui/hash") {
        response(data = uiFile.sha1)
    }
    get("/ui") {
        if (!uiFile.exists()) throw RequestError("客户端rdi核心维护中")
        call.respondFile(uiFile)
    }*/
    get("/mc/{ver}/hash"){
        val jarFile = mcCoreModFile()
        response(data = jarFile.sha1)
    }

    get("/mc/{ver}"){
        val jarFile = mcCoreModFile()
        call.respondFile(jarFile)
    }
    route("/updater"){
        get {
            call.respondFile(updaterWin)
        }
        get("/hash"){
            val hash = updaterWinHash.readText().trim()
            if (hash.isBlank()) throw RequestError("updater hash为空")
            response(data = hash)
        }
    }
}



object UpdateService {
    suspend fun RoutingContext.mcCoreModFile(): File {
        val jarFile = CLIENT_LIBS_DIR.resolve("$mcCoreFilePrefix-${param("ver")}.jar")
        if (!jarFile.exists()) throw RequestError("无此版本的MC核心库")
        return jarFile
    }
}
