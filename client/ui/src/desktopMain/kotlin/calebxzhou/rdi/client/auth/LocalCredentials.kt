package calebxzhou.rdi.client.auth

import calebxzhou.rdi.RDIClient
import calebxzhou.rdi.client.model.LoginInfo
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
private data class DesktopCredentialsData(
    var loginInfos: MutableMap<String, LoginInfo> = hashMapOf(),
    var lastPlayHost: LastPlayHostInfo? = null,
)

actual class LocalCredentials actual constructor() {
    actual var loginInfos: MutableMap<String, LoginInfo> = hashMapOf()
    actual var lastPlayHost: LastPlayHostInfo? = null

    actual val lastLogged: LoginInfo?
        get() = loginInfos.values.maxByOrNull { it.lastLoggedTime }

    actual fun save() {
        val data = DesktopCredentialsData(
            loginInfos = loginInfos,
            lastPlayHost = lastPlayHost
        )
        file.writeText(serdesJson.encodeToString(data))
    }

    actual companion object {
        private val file = File(RDIClient.DIR, "local_credentials.json")

        actual fun read(): LocalCredentials = try {
            if (!file.exists()) {
                file.createNewFile()
                LocalCredentials().save()
            }
            val data = serdesJson.decodeFromString<DesktopCredentialsData>(file.readText())
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
