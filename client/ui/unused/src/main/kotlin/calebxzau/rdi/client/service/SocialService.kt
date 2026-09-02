package calebxzau.rdi.client.service

import calebxzhou.rdi.client.net.rdiResponse
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.common.model.MailAction
import calebxzhou.rdi.common.model.Response
import calebxzau.rdi.common.model.Friend
import calebxzau.rdi.common.model.FriendLookupDto
import calebxzau.rdi.common.model.FriendRequestDto
import calebxzau.rdi.common.model.FriendTag
import calebxzau.rdi.common.model.FriendTagCreateDto
import calebxzau.rdi.common.model.FriendTagRenameDto
import calebxzau.rdi.common.model.FriendTagReplaceDto
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import org.bson.types.ObjectId
import java.util.UUID

/** Client boundary for mailbox and friend operations. */
object SocialService {
    suspend fun loadMails(page: Int): Result<List<Mail.Vo>> = request {
        requireOk(server.makeRequest<List<Mail.Vo>>("mail", params = mapOf("page" to page))).data.orEmpty()
    }

    suspend fun loadMail(id: UUID): Result<Mail.Dto> = request {
        requireData(server.makeRequest("mail/$id"), "邮件内容为空")
    }

    suspend fun deleteMail(id: UUID): Result<Unit> = request {
        val response = server.makeRequest<Unit>("mail/$id", HttpMethod.Delete)
        requireOk(response)
    }

    suspend fun deleteMails(ids: Collection<UUID>): Result<Unit> = request {
        val response = server.createRequest("mail", HttpMethod.Delete) {
            json()
            setBody(ids.distinct().toList().json)
        }.let { it.rdiResponse<Unit>() }
        requireOk(response)
    }

    suspend fun actOnMail(id: UUID, action: MailAction): Result<Unit> = request {
        val response = server.makeRequest<Unit>("mail/$id/action/${action.name}", HttpMethod.Post)
        requireOk(response)
    }

    suspend fun loadFriends(): Result<List<Friend>> = request {
        requireOk(server.makeRequest<List<Friend>>("friend")).data.orEmpty()
    }

    suspend fun lookupFriend(qq: String): Result<calebxzhou.rdi.common.model.RAccount.Dto> = request {
        val response = server.createRequest("friend/lookup", HttpMethod.Post) {
            json()
            setBody(FriendLookupDto(qq.trim()).json)
        }.rdiResponse<calebxzhou.rdi.common.model.RAccount.Dto>()
        requireData(response, "玩家信息为空")
    }

    suspend fun requestFriend(qq: String): Result<Unit> = request {
        val response = server.createRequest("friend/request", HttpMethod.Post) {
            json()
            setBody(FriendRequestDto(qq.trim()).json)
        }.rdiResponse<Unit>()
        requireOk(response)
    }

    suspend fun removeFriend(id: ObjectId): Result<Unit> = request {
        val response = server.makeRequest<Unit>("friend/$id", HttpMethod.Delete)
        requireOk(response)
    }

    suspend fun loadTags(): Result<List<FriendTag>> = request {
        requireOk(server.makeRequest<List<FriendTag>>("friend/tags")).data.orEmpty()
    }

    suspend fun createTag(name: String): Result<FriendTag> = request {
        val response = server.createRequest("friend/tags", HttpMethod.Post) {
            json()
            setBody(FriendTagCreateDto(name).json)
        }.rdiResponse<FriendTag>()
        requireData(response, "标签信息为空")
    }

    suspend fun renameTag(id: UUID, name: String): Result<FriendTag> = request {
        val response = server.createRequest("friend/tags/$id", HttpMethod.Put) {
            json()
            setBody(FriendTagRenameDto(name).json)
        }.rdiResponse<FriendTag>()
        requireData(response, "标签信息为空")
    }

    suspend fun deleteTag(id: UUID): Result<Unit> = request {
        val response = server.makeRequest<Unit>("friend/tags/$id", HttpMethod.Delete)
        requireOk(response)
    }

    suspend fun replaceFriendTags(friendId: ObjectId, tagIds: Collection<UUID>): Result<Unit> = request {
        val response = server.createRequest("friend/$friendId/tags", HttpMethod.Put) {
            json()
            setBody(FriendTagReplaceDto(tagIds.distinct().map(UUID::toString)).json)
        }.rdiResponse<Unit>()
        requireOk(response)
    }

    private fun <T> requireOk(response: Response<T>): Response<T> =
        response.takeIf { it.ok } ?: error(response.msg.ifBlank { "服务器请求失败" })

    private fun <T> requireData(response: Response<T>, emptyMessage: String): T =
        requireOk(response).data ?: error(emptyMessage)

    private suspend inline fun <T> request(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cause: Throwable) {
        Result.failure(cause)
    }
}
