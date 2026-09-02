package calebxzau.rdi.server.modpack

import calebxzhou.rdi.common.model.ModpackUploadSessionCreateDto
import calebxzau.rdi.common.model.Modpack2AppendPreflightDto
import calebxzau.rdi.common.model.Modpack2VersionCreateFromUploadDto
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.net.uid
import calebxzhou.rdi.master.service.ModpackService
import calebxzhou.rdi.master.service.modpack2ParallelUploadService
import calebxzhou.rdi.master.service.player
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.UUID

/** JWT-protected Modpack2 API and its shared parallel-upload session routes. */
fun Route.modpack2Routes() {
    val service by inject<Modpack2Service>()

    route("/modpack2") {
        post {
            response(data = service.create(call.player(), call.receive()))
        }
        get("/list") {
            response(data = service.listPublic(parseModpack2Page(call.request.queryParameters["page"])))
        }
        get("/owned") {
            response(
                data = service.listOwned(
                    call.player(),
                    parseModpack2Offset(call.request.queryParameters["offset"]),
                    parseModpack2Limit(call.request.queryParameters["limit"]),
                )
            )
        }
        route("/{modpackId}/versions") {
            post("/preflight") {
                response(
                    data = service.appendPreflight(
                        call.player(),
                        call.modpack2Id("modpackId"),
                        call.receive<Modpack2AppendPreflightDto>(),
                    )
                )
            }
            post {
                response(
                    data = service.createVersion(
                        call.player(),
                        call.modpack2Id("modpackId"),
                        call.receive<Modpack2VersionCreateFromUploadDto>(),
                    )
                )
            }
            get {
                response(data = service.listVersions(call.player(), call.modpack2Id("modpackId")))
            }
            route("/{versionId}") {
                post("/retry") {
                    response(
                        data = service.retryVersion(
                            call.player(),
                            call.modpack2Id("modpackId"),
                            call.modpack2Id("versionId"),
                        )
                    )
                }
                get("/client-pack/hash") {
                    call.respondText(
                        service.clientPackHash(
                            call.player(),
                            call.modpack2Id("modpackId"),
                            call.modpack2Id("versionId"),
                        )
                    )
                }
                get("/client-pack") {
                    call.respondFile(
                        service.clientPack(
                            call.player(),
                            call.modpack2Id("modpackId"),
                            call.modpack2Id("versionId"),
                        )
                    )
                }
                get("/client-manifest") {
                    response(
                        data = service.clientManifest(
                            call.player(),
                            call.modpack2Id("modpackId"),
                            call.modpack2Id("versionId"),
                        )
                    )
                }
            }
        }
        delete("/version/{versionId}") {
            service.deleteVersion(call.player(), call.modpack2Id("versionId"))
            ok()
        }
        delete("/{modpackId}") {
            service.deleteModpack(call.player(), call.modpack2Id("modpackId"))
            ok()
        }

        route("/upload-sessions") {
            post {
                response(
                    data = ModpackService.modpack2ParallelUploadService
                        .create(call.uid, call.receive<ModpackUploadSessionCreateDto>())
                        .getOrThrow()
                )
            }
            route("/{uploadId}") {
                get {
                    response(
                        data = ModpackService.modpack2ParallelUploadService
                            .status(call.uid, call.modpack2UploadId())
                            .getOrThrow()
                    )
                }
                delete {
                    ModpackService.modpack2ParallelUploadService
                        .cancel(call.uid, call.modpack2UploadId())
                        .getOrThrow()
                    ok()
                }
                put("/parts/{index}") {
                    val expectedSha1 = call.request.headers[PART_SHA1_HEADER]
                        ?: throw ParamError("缺少分片SHA-1")
                    val expectedLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    ModpackService.modpack2ParallelUploadService.uploadPart(
                        ownerId = call.uid,
                        id = call.modpack2UploadId(),
                        index = call.parameters["index"]?.toIntOrNull()
                            ?: throw ParamError("分片序号无效"),
                        declaredLength = expectedLength,
                        expectedSha1 = expectedSha1,
                        source = call.receiveChannel()
                    ).getOrThrow()
                    ok()
                }
                post("/complete") {
                    response(
                        data = ModpackService.modpack2ParallelUploadService
                            .complete(call.uid, call.modpack2UploadId())
                            .getOrThrow()
                    )
                }
            }
        }
    }
}

private const val PART_SHA1_HEADER = "X-Part-SHA1"

internal fun parseModpack2Page(value: String?): Int =
    value?.toIntOrNull()?.takeIf { it >= 0 }
        ?: if (value == null) 0 else throw ParamError("page格式错误")

internal fun parseModpack2Offset(value: String?): Long =
    value?.toLongOrNull()?.takeIf { it >= 0 }
        ?: if (value == null) 0L else throw ParamError("offset格式错误")

internal fun parseModpack2Limit(value: String?): Int =
    value?.toIntOrNull()?.takeIf { it in 1..100 }
        ?: if (value == null) 50 else throw ParamError("limit格式错误")

private suspend fun io.ktor.server.application.ApplicationCall.modpack2Id(name: String): UUID =
    parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ParamError("${name}格式错误")

private suspend fun io.ktor.server.application.ApplicationCall.modpack2UploadId(): UUID =
    modpack2Id("uploadId")
