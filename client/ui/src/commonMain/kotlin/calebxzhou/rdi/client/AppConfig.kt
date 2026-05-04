package calebxzhou.rdi.client

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.CONF
import calebxzhou.rdi.common.CommonConfig
import calebxzhou.rdi.common.ProxyConfig
import calebxzhou.rdi.common.serdesToml
import calebxzhou.rdi.common.service.ModService
import kotlinx.serialization.Serializable
import java.io.File

/**
 * calebxzhou @ 2025-12-11 10:23
 */
@Serializable
enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val modelCandidates: List<String>
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        modelCandidates = listOf("gpt-5.5", "gpt-5.4")
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        modelCandidates = listOf("deepseek-v4-flash[1m]", "deepseek-v4-pro[1m]")
    )
    ;
    val defaultModel get()  = modelCandidates.first()
}

@Serializable
data class AiConfig(
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = AiProvider.OPENAI.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = AiProvider.OPENAI.defaultModel
)

@Serializable
data class AppConfig(
    val preferModMirror: Boolean = true,
    val preferMcMirror: Boolean = true,
    //不限制
    val maxMemory: Int=0,
    val jre25Path: String?=null,
    val jre21Path: String?=null,
    val jre8Path: String?=null,
    val proxyConfig: ProxyConfig?=null,
    val aiConfig: AiConfig = AiConfig(),
    val pinyinName: Boolean = false,
){
    companion object {
        private val lgr by Loggers
        private val configFile: File
            get() = platformAppConfigFile()
        fun load(): AppConfig {
            return if (configFile.exists()) {
                try {
                    serdesToml.decodeFromString(serializer(), configFile.readText())

                } catch (e: Exception) {
                    lgr.warn { "read config failed, use default and save" }
                    e.printStackTrace()
                    AppConfig().also { save(it) }
                }
            } else {
                AppConfig().also { save(it) }
            }.also {
                ModService.preferMirror = it.preferModMirror
                CommonConfig.updateProxyConfig(it.proxyConfig)
            }
        }

        fun save(config: AppConfig) {
            try {
                CONF = config
                ModService.preferMirror = config.preferModMirror
                CommonConfig.updateProxyConfig(config.proxyConfig)
                configFile.parentFile?.mkdirs()
                configFile.writeText(serdesToml.encodeToString(serializer(), config))
            } catch (e: Exception) {
                lgr.warn (e){ "save config failed\n" }
            }
        }
    }
}

