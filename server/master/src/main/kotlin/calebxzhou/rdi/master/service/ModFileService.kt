package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.master.DL_MODS_CLIENT_DIR
import calebxzhou.rdi.master.net.param
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.modFileRoutes() = route("/mod") {
    get("/download/{filename}") {
        val player = call.player()

        val filename = param("filename").trim()
        if (
            filename.isBlank() ||
            filename.contains("..") ||
            filename.contains("/") ||
            filename.contains("\\") ||
            !filename.endsWith(".jar", ignoreCase = true)
        ) {
            throw RequestError("非法文件名")
        }

        val file = ModStorage.findDownloadFile(filename, DL_MOD_DIR, DL_MODS_CLIENT_DIR).getOrThrow()
        if (file == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        DownloadQuotaService.reserve(player._id, file.length())
        call.respondFile(file)
    }
}
