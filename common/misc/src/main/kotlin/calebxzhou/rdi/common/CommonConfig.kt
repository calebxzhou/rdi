package calebxzhou.rdi.common

import kotlinx.serialization.Serializable
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * calebxzhou @ 2026-02-08 0:13
 */
@Serializable
data class ProxyConfig(
    val enabled: Boolean = false,
    val systemProxy: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 10808,
    val usr: String? = null,
    val pwd: String? = null
)

object CommonConfig {
    @Volatile
    var proxyConfig: ProxyConfig = ProxyConfig()
        private set

    fun updateProxyConfig(proxy: ProxyConfig?) {
        proxyConfig = proxy ?: ProxyConfig()
        applyProxy(proxyConfig)
    }

    private fun applyProxy(cfg: ProxyConfig) {
        if (!cfg.enabled) {
            System.setProperty("java.net.useSystemProxies", "false")
            clearProxyProperties()
            Authenticator.setDefault(null)
            return
        }
        if (cfg.systemProxy) {
            System.setProperty("java.net.useSystemProxies", "true")
            clearProxyProperties()
            Authenticator.setDefault(null)
            return
        }
        System.setProperty("java.net.useSystemProxies", "false")
        if (cfg.host.isNotBlank() && cfg.port > 0) {
            System.setProperty("http.proxyHost", cfg.host)
            System.setProperty("http.proxyPort", cfg.port.toString())
            System.setProperty("https.proxyHost", cfg.host)
            System.setProperty("https.proxyPort", cfg.port.toString())
        } else {
            clearProxyProperties()
        }
        val usr = cfg.usr?.trim().orEmpty()
        val pwd = cfg.pwd?.trim().orEmpty()
        if (usr.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(usr, pwd.toCharArray())
                }
            })
        } else {
            Authenticator.setDefault(null)
        }
    }

    private fun clearProxyProperties() {
        listOf(
            "http.proxyHost",
            "http.proxyPort",
            "https.proxyHost",
            "https.proxyPort"
        ).forEach { System.clearProperty(it) }
    }
}
