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
    val defaultBaseUrl: String
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
    )
    ;
}

@Serializable
enum class AiReasoningEffort(
    val displayName: String,
    val apiValue: String?
) {
    AUTO("自动", null),
    LOW("低", "low"),
    MEDIUM("中", "medium"),
    HIGH("高", "high"),
    MAX("极", "xhigh");

    fun supportedBy(provider: AiProvider): Boolean =
        this == AUTO || when (provider) {
            AiProvider.OPENAI -> this in setOf(LOW, MEDIUM, HIGH, MAX)
            AiProvider.DEEPSEEK -> this in setOf(LOW, MEDIUM,HIGH, MAX)
        }

    fun normalizedFor(provider: AiProvider): AiReasoningEffort =
        takeIf { it.supportedBy(provider) } ?: AUTO

    companion object {
        fun optionsFor(provider: AiProvider): List<AiReasoningEffort> =
            entries.filter { it.supportedBy(provider) }
    }
}

@Serializable
data class AiConfig(
    val activeProfileId: String = "",
    val profiles: List<AiProviderProfile> = emptyList(),
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = AiProvider.OPENAI.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = "",
    val contextLimitTokens: Int = 1_000_000,
    val reasoningEffort: AiReasoningEffort = AiReasoningEffort.AUTO
) {
    fun normalized(): AiConfig {
        val normalizedProfiles = profiles
            .map { it.normalized() }
            .ifEmpty { listOf(legacyProfile()) }
        val activeId = activeProfileId
            .takeIf { id -> normalizedProfiles.any { it.id == id } }
            ?: normalizedProfiles.first().id
        return copy(
            activeProfileId = activeId,
            profiles = normalizedProfiles
        )
    }

    fun activeProfile(): AiProviderProfile =
        normalized().let { config ->
            config.profiles.firstOrNull { it.id == config.activeProfileId }
                ?: config.profiles.first()
        }

    private fun legacyProfile(): AiProviderProfile =
        AiProviderProfile(
            id = DEFAULT_AI_PROFILE_ID,
            name = provider.displayName,
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            contextLimitTokens = contextLimitTokens,
            reasoningEffort = reasoningEffort
        ).normalized()
}

@Serializable
data class AiProviderProfile(
    val id: String = DEFAULT_AI_PROFILE_ID,
    val name: String = "",
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = AiProvider.OPENAI.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = "",
    val contextLimitTokens: Int = 1_000_000,
    val reasoningEffort: AiReasoningEffort = AiReasoningEffort.AUTO
) {
    fun normalized(): AiProviderProfile =
        copy(
            id = id.trim().ifBlank { DEFAULT_AI_PROFILE_ID },
            name = name.trim().ifBlank { provider.displayName },
            baseUrl = if (provider == AiProvider.DEEPSEEK) {
                AiProvider.DEEPSEEK.defaultBaseUrl
            } else {
                baseUrl.trim().ifBlank { provider.defaultBaseUrl }
            },
            apiKey = apiKey.trim(),
            model = model.trim(),
            contextLimitTokens = contextLimitTokens,
            reasoningEffort = reasoningEffort.normalizedFor(provider)
        )
}

const val DEFAULT_AI_PROFILE_ID = "default"

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
