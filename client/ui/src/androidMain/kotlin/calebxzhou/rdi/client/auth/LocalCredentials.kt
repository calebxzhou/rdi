package calebxzhou.rdi.client.auth

import android.annotation.SuppressLint
import android.content.Context
import calebxzhou.rdi.client.model.LoginInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AndroidCredentialsData(
    var loginInfos: MutableMap<String, LoginInfo> = hashMapOf(),
    var lastPlayHost: LastPlayHostInfo? = null,
)

actual class LocalCredentials actual constructor() {
    actual var loginInfos: MutableMap<String, LoginInfo> = hashMapOf()
    actual var lastPlayHost: LastPlayHostInfo? = null

    actual val lastLogged: LoginInfo?
        get() = loginInfos.values.maxByOrNull { it.lastLoggedTime }

    @SuppressLint("ApplySharedPref")
    actual fun save() {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("LocalCredentials not initialized. Call LocalCredentials.init(context) first.")
        val data = AndroidCredentialsData(
            loginInfos = loginInfos,
            lastPlayHost = lastPlayHost
        )
        prefs.edit().putString(KEY_CREDENTIALS, json.encodeToString(data)).commit()
    }

    actual companion object {
        private const val PREFS_NAME = "rdi_accounts"
        private const val KEY_CREDENTIALS = "credentials_data"
        private val json = Json { ignoreUnknownKeys = true }

        @SuppressLint("StaticFieldLeak")
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
        }

        actual fun read(): LocalCredentials = try {
            val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: return LocalCredentials()
            val raw = prefs.getString(KEY_CREDENTIALS, null)
            if (raw != null) {
                val data = json.decodeFromString<AndroidCredentialsData>(raw)
                LocalCredentials().apply {
                    loginInfos = data.loginInfos
                    lastPlayHost = data.lastPlayHost
                }
            } else {
                LocalCredentials()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LocalCredentials()
        }
    }
}
