package calebxzhou.rdi.client.auth

import calebxzhou.rdi.client.model.LoginInfo
import kotlinx.serialization.Serializable

@Serializable
data class LastPlayHostInfo(
    val id: String,
    val name: String
)

expect class LocalCredentials() {
    var loginInfos: MutableMap<String, LoginInfo>
    var lastPlayHost: LastPlayHostInfo?
    val lastLogged: LoginInfo?
    fun save()

    companion object {
        fun read(): LocalCredentials
    }
}

fun LocalCredentials.updateLastPlayHost(id: String, name: String) {
    lastPlayHost = LastPlayHostInfo(id = id, name = name)
    save()
}
