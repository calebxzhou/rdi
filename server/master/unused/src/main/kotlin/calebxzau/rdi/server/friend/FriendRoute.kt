package calebxzau.rdi.server.friend

import calebxzau.rdi.common.model.FriendRequestDto
import calebxzau.rdi.common.model.FriendLookupDto
import calebxzau.rdi.common.model.FriendTagCreateDto
import calebxzau.rdi.common.model.FriendTagRenameDto
import calebxzau.rdi.common.model.FriendTagReplaceDto
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.ok
import calebxzhou.rdi.master.net.response
import calebxzhou.rdi.master.service.player
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.bson.types.ObjectId
import java.util.UUID

fun Route.friendRoutes() {
    route("/friend") {
        get { response(data = FriendService.list(call.player())) }
        post("/lookup") { response(data = FriendService.lookup(call.player(), call.receive<FriendLookupDto>().qq)) }
        post("/request") { FriendService.request(call.player(), call.receive<FriendRequestDto>().qq); ok() }
        delete("/{playerId}") {
            val id = call.parameters["playerId"]?.let { runCatching { ObjectId(it) }.getOrNull() }
                ?: throw ParamError("玩家ID格式错误")
            FriendService.remove(call.player(), id)
            ok()
        }
        route("/tags") {
            get { response(data = FriendService.tags(call.player())) }
            post { response(data = FriendService.createTag(call.player(), call.receive<FriendTagCreateDto>().name)) }
            route("/{tagId}") {
                put { response(data = FriendService.renameTag(call.player(), call.tagUuid(), call.receive<FriendTagRenameDto>().name)) }
                delete { FriendService.deleteTag(call.player(), call.tagUuid()); ok() }
            }
        }
        put("/{playerId}/tags") {
            val playerId = call.parameters["playerId"]?.let { runCatching { ObjectId(it) }.getOrNull() }
                ?: throw ParamError("玩家ID格式错误")
            val tagIds = call.receive<FriendTagReplaceDto>().tagIds.map {
                runCatching { UUID.fromString(it) }.getOrNull() ?: throw ParamError("标签ID格式错误")
            }
            FriendService.replaceTags(call.player(), playerId, tagIds)
            ok()
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.tagUuid(): UUID =
    parameters["tagId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ParamError("标签ID格式错误")
