package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.service.DockerService
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.service.player
import io.ktor.server.request.receive
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.host2Routes() {
    val hostService by inject<Host2Service>()
    val setupService by inject<Host2SetupService>()
    val contentsService by inject<Host2ContentsService>()
    val clientPackService by inject<Host2ClientPackService>()
    val fileService by inject<Host2FileService>()

    route("/host2") {
        post {
            val player = call.player()
            val host = hostService.create(player, call.receive())
            setupService.startInstall(player, host.id)
            response(data = host)
        }
        get("/my") {
            response(data = hostService.list(call.player(), true, 0))
        }
        get("/list") {
            response(data = hostService.list(call.player(), false, call.page()))
        }
        route("/{id}") {
            get {
                response(data = hostService.detail(call.player(), call.host2Id()))
            }
            delete {
                val id = call.host2Id()
                hostService.requireOwner(call.player(), id)
                setupService.cancel(id)
                hostService.delete(call.player(), id)
                ok()
            }
            put("/options") {
                hostService.options(call.player(), call.host2Id(), call.receive())
                ok()
            }
            post("/install") {
                setupService.startInstall(call.player(), call.host2Id())
                ok("房间整合包正在安装")
            }
            put("/pack-source") {
                val dto = call.receive<Host2.PackSourceDto>()
                setupService.switchPack(call.player(), call.host2Id(), dto)
                ok("房间整合包来源已更新")
            }
            put("/ownership") {
                val dto = call.receive<Host2.TransferOwnershipDto>()
                hostService.transferOwnership(call.player(), call.host2Id(), dto.playerId)
                ok()
            }
            post("/quit") {
                hostService.quit(call.player(), call.host2Id())
                ok()
            }
            post("/start") {
                val id = call.host2Id()
                hostService.withMutation(id) {
                    var context = hostService.requireCanStart(call.player(), id)
                    contentsService.applyPendingForStart(context.host)
                    context = hostService.requireCanStart(call.player(), id)
                    Host2RuntimeService.start(context)
                }
                ok()
            }
            post("/stop") {
                val id = call.host2Id()
                hostService.withMutation(id) {
                    val host = hostService.requireAdmin(call.player(), id)
                    Host2RuntimeService.stop(host.id)
                    contentsService.applyPendingForStart(host)
                }
                ok()
            }
            post("/restart") {
                val id = call.host2Id()
                hostService.withMutation(id) {
                    var context = hostService.requireCanStart(call.player(), id)
                    Host2RuntimeService.stop(id)
                    contentsService.applyPendingForStart(context.host)
                    context = hostService.requireCanStart(call.player(), id)
                    Host2RuntimeService.restart(context)
                }
                ok()
            }
            post("/command") {
                val host = hostService.requireAdmin(call.player(), call.host2Id())
                val dto = call.receive<Host2.CommandDto>()
                response(data = Host2RuntimeService.command(host.id, dto.command))
            }
            route("/members") {
                post {
                    val dto = call.receive<Host2.InviteMemberDto>()
                    hostService.invite(call.player(), call.host2Id(), dto.qq)
                    ok()
                }
                put("/{playerId}/role") {
                    val dto = call.receive<Host2.SetMemberRoleDto>()
                    hostService.setMemberRole(call.player(), call.host2Id(), call.playerId(), dto.role)
                    ok()
                }
                delete("/{playerId}") {
                    hostService.removeMember(call.player(), call.host2Id(), call.playerId())
                    ok()
                }
            }
            route("/contents") {
                get {
                    response(data = contentsService.list(call.player(), call.host2Id()))
                }
                post {
                    response(data = contentsService.add(call.player(), call.host2Id(), call.receive()))
                }
                delete {
                    response(data = contentsService.delete(call.player(), call.host2Id(), call.receive()))
                }
                put("/enabled") {
                    response(data = contentsService.setEnabled(call.player(), call.host2Id(), call.receive()))
                }
            }
            post("/contents/apply") {
                response(data = contentsService.apply(call.player(), call.host2Id(), call.receive()))
            }
            get("/client-manifest") {
                response(data = clientPackService.manifest(call.player(), call.host2Id()))
            }
            get("/client-pack/hash") {
                call.respondText(clientPackService.clientPackSha1(call.player(), call.host2Id()))
            }
            get("/client-pack") {
                call.respondFile(clientPackService.clientPack(call.player(), call.host2Id()))
            }
            route("/log") {
                get {
                    val id = call.host2Id()
                    hostService.requireAdmin(call.player(), id)
                    response(data = host2RecentLog(id))
                }
                sse("/stream") {
                    val id = call.host2Id()
                    hostService.requireAdmin(call.player(), id)
                    host2RecentLog(id).takeIf(String::isNotBlank)?.let { send(ServerSentEvent(data = it)) }
                    if (Host2RuntimeService.status(id) == calebxzhou.rdi.common.model.HostStatus.STOPPED) return@sse
                    callbackFlow {
                        val listener = DockerService.listenLog(
                            id.toString(),
                            onLine = { trySend(it) },
                            onError = { close(it) },
                            onFinished = { close() }
                        )
                        awaitClose { listener.close() }
                    }.collect { line -> send(ServerSentEvent(data = line)) }
                }
            }
            route("/files") {
                get {
                    response(data = fileService.list(call.player(), call.host2Id(), call.request.queryParameters["path"].orEmpty()))
                }
                get("/search") {
                    response(data = fileService.search(call.player(), call.host2Id(), call.request.queryParameters["query"].orEmpty()))
                }
                post("/file") {
                    response(data = fileService.upload(call.player(), call.host2Id(), call))
                }
                get("/file") {
                    response(data = fileService.read(call.player(), call.host2Id(), call.request.queryParameters["path"].orEmpty()))
                }
                put("/file") {
                    response(data = fileService.write(call.player(), call.host2Id(), call.receive<Host.FileWriteDto>()))
                }
                post("/create") {
                    response(data = fileService.create(call.player(), call.host2Id(), call.receive<Host.FileCreateDto>()))
                }
                put("/rename") {
                    response(data = fileService.rename(call.player(), call.host2Id(), call.receive<Host.FileRenameDto>()))
                }
                delete("/file") {
                    fileService.delete(call.player(), call.host2Id(), call.receive<Host.FileDeleteDto>())
                    ok()
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.host2Id(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw RequestError("新版房间ID格式错误")

private fun io.ktor.server.application.ApplicationCall.playerId(): UUID =
    parameters["playerId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw RequestError("玩家ID格式错误")

private fun io.ktor.server.application.ApplicationCall.page(): Int =
    request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

private fun host2RecentLog(id: UUID): String {
    if (Host2RuntimeService.status(id) != calebxzhou.rdi.common.model.HostStatus.STOPPED) {
        return DockerService.getLog(id.toString(), 0, 200)
    }
    val latest = HOST2_DIR.resolve(id.toString()).resolve("logs/latest.log")
    if (!latest.isFile) return ""
    val lines = ArrayDeque<String>()
    latest.bufferedReader().useLines { sequence ->
        sequence.forEach { line ->
            if (lines.size == 200) lines.removeFirst()
            lines.addLast(line)
        }
    }
    return lines.joinToString("\n")
}
