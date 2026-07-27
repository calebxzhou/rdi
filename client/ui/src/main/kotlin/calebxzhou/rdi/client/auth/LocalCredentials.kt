package calebxzhou.rdi.client.auth

import calebxzau.rdi.client.RDIClient
import calebxzhou.rdi.client.model.LoginInfo
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class LastPlayHostInfo(
    val id: String,
    val name: String,
)

@Serializable
private data class CredentialsData(
    var loginInfos: MutableMap<String, LoginInfo> = hashMapOf(),
    var lastPlayHost: LastPlayHostInfo? = null,
)

class LocalCredentials {
    var loginInfos: MutableMap<String, LoginInfo> = hashMapOf()
    var lastPlayHost: LastPlayHostInfo? = null

    val lastLogged: LoginInfo?
        get() = loginInfos.values.maxByOrNull { it.lastLoggedTime }

    fun save() {
        val data = CredentialsData(
            loginInfos = loginInfos,
            lastPlayHost = lastPlayHost
        )
        file.writeText(serdesJson.encodeToString(data))
    }

    companion object {
        private val file = File(RDIClient.DIR, "local_credentials.json")

        fun read(): LocalCredentials = try {
            if (!file.exists()) {
                file.createNewFile()
                LocalCredentials().save()
            }
            val data = serdesJson.decodeFromString<CredentialsData>(file.readText())
            LocalCredentials().apply {
                loginInfos = data.loginInfos
                lastPlayHost = data.lastPlayHost
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LocalCredentials()
        }
    }
}

fun LocalCredentials.updateLastPlayHost(id: String, name: String) {
    lastPlayHost = LastPlayHostInfo(id, name)
    save()
}
