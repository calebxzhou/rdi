package calebxzhou.rdi.client.service

import calebxzau.rdi.client.CONF
import calebxzhou.rdi.client.AiConfig
import calebxzhou.rdi.client.AiProvider
import calebxzhou.rdi.client.AiProviderProfile
import calebxzhou.rdi.client.AiTokenPrice
import calebxzhou.rdi.client.AppConfig
import calebxzau.rdi.client.ui.normalizeJavaExecutablePath
import calebxzau.rdi.client.ui.readTotalPhysicalMemoryMb
import calebxzau.rdi.client.ui.validateJavaExecutablePath
import calebxzhou.rdi.common.ProxyConfig
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

object SettingsService {

    data class ValidationResult(
        val success: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Get total physical memory in MB
     */
    fun getTotalPhysicalMemoryMb(): Int = readTotalPhysicalMemoryMb()

    /**
     * Validate memory settings
     */
    fun validateMemory(maxMemoryText: String, totalMemoryMb: Int): ValidationResult {
        if (maxMemoryText.isBlank()) {
            return ValidationResult(true)
        }

        val memoryValue = maxMemoryText.trim().toIntOrNull()
            ?: return ValidationResult(false, "最大内存格式不正确")

        if (memoryValue == 0) {
            return ValidationResult(true)
        }

        if (memoryValue <= 4096) {
            return ValidationResult(false, "最大内存必须大于4096MB")
        }

        if (totalMemoryMb in 1..memoryValue) {
            return ValidationResult(false, "最大内存必须小于总内存 ${totalMemoryMb}MB")
        }

        return ValidationResult(true)
    }

    /**
     * Validate proxy port
     */
    fun validateProxyPort(proxyPortText: String): ValidationResult {
        if (proxyPortText.isBlank()) {
            return ValidationResult(true)
        }

        val port = proxyPortText.trim().toIntOrNull()
            ?: return ValidationResult(false, "代理端口格式不正确")

        if (port !in 1..65535) {
            return ValidationResult(false, "代理端口必须在1-65535之间")
        }

        return ValidationResult(true)
    }

    /**
     * Validate a configured Java path.
     */
    fun validateJavaPath(rawPath: String, expectedMajor: Int): Result<Unit> =
        validateJavaExecutablePath(rawPath, expectedMajor)

    fun validateAiSettings(provider: AiProvider, baseUrl: String): ValidationResult {
        val normalizedBaseUrl = normalizeAiBaseUrl(provider, baseUrl)
        if (!normalizedBaseUrl.startsWith("http://") && !normalizedBaseUrl.startsWith("https://")) {
            return ValidationResult(false, "AI接口地址必须以http://或https://开头")
        }
        return ValidationResult(true)
    }

    fun validateAiProfile(profile: AiProviderProfile, active: Boolean): ValidationResult {
        val normalizedProfile = profile.normalized()
        validateAiSettings(
            normalizedProfile.provider,
            normalizedProfile.baseUrl
        ).takeIf { !it.success }?.let { return it }
        if (active) {
            if (normalizedProfile.apiKey.isBlank()) {
                return ValidationResult(false, "当前AI配置的API Key不能为空")
            }
            if (normalizedProfile.model.isBlank()) {
                return ValidationResult(false, "当前AI配置的模型不能为空")
            }
        }
        validateAiTokenPrice(normalizedProfile.tokenPrice).takeIf { !it.success }?.let { return it }
        return validateAiContextLimit(normalizedProfile.contextLimitTokens.toString())
    }

    private fun validateAiTokenPrice(price: AiTokenPrice): ValidationResult {
        val prices = listOf(price.inputCacheMiss1M, price.inputCacheHit1M, price.output1M)
        if (prices.any { it.isNaN() || it.isInfinite() || it < 0.0 }) {
            return ValidationResult(false, "AI价格必须是不小于0的数字")
        }
        return ValidationResult(true)
    }

    fun validateAiConfig(aiConfig: AiConfig, requireActiveProfile: Boolean = true): ValidationResult {
        val normalizedConfig = aiConfig.normalized()
        val activeProfile = normalizedConfig.activeProfile()
        return validateAiProfile(activeProfile, active = requireActiveProfile)
    }

    fun validateAiContextLimit(contextLimitText: String): ValidationResult {
        val contextLimit = contextLimitText.trim().toIntOrNull()
            ?: return ValidationResult(false, "AI上下文上限必须是数字")
        if (contextLimit !in 64_000..1_000_000) {
            return ValidationResult(false, "AI上下文上限必须在64000-1000000之间")
        }
        return ValidationResult(true)
    }

    suspend fun fetchAiModels(provider: AiProvider, baseUrl: String, apiKey: String): Result<List<String>> {
        val result = runCatching {
            val key = apiKey.trim()
            require(key.isNotBlank()) { "请输入API Key" }
            val modelsUrl = "${normalizeAiBaseUrl(provider, baseUrl).trimEnd('/')}/models"
            val response = ktorClient.request {
                url(modelsUrl)
                method = HttpMethod.Get
                header(HttpHeaders.Authorization, "Bearer $key")
            }
            val responseText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(response.toAiModelFetchError(responseText))
            }

            serdesJson.decodeFromString<AiModelsResponse>(responseText)
                .data
                .mapNotNull { it.id.trim().takeIf(String::isNotBlank) }
                .distinct()
        }
        return result.recoverCatching { error ->
            if (error is IOException) {
                throw IllegalStateException("无法连接AI服务，请检查网络或API Base URL")
            }
            throw error
        }
    }

    suspend fun fetchDeepSeekBalance(apiKey: String): Result<String> {
        val result = runCatching {
            val key = apiKey.trim()
            require(key.isNotBlank()) { "请输入API Key" }
            val response = ktorClient.request {
                url("${AiProvider.DEEPSEEK.defaultBaseUrl}/user/balance")
                method = HttpMethod.Get
                header(HttpHeaders.Authorization, "Bearer $key")
            }
            val responseText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(response.toAiModelFetchError(responseText))
            }

            serdesJson.decodeFromString<DeepSeekBalanceResponse>(responseText)
                .balance_infos
                .mapNotNull { info ->
                    val balance = info.total_balance.trim()
                    val currency = info.currency.trim()
                    if (balance.isBlank() || currency.isBlank()) null else "$balance 元($currency)"
                }
                .joinToString("，")
                .ifBlank { "无余额信息" }
        }
        return result.recoverCatching { error ->
            if (error is IOException) {
                throw IllegalStateException("无法连接AI服务，请检查网络或API Base URL")
            }
            throw error
        }
    }

    /**
     * Save settings configuration
     */
    suspend fun saveSettings(
        preferModMirror: Boolean,
        preferMcMirror: Boolean,
        maxMemoryText: String,
        jre25Path: String,
        jre21Path: String,
        proxyEnabled: Boolean,
        proxySystem: Boolean,
        proxyHost: String,
        proxyPortText: String,
        proxyUsr: String,
        proxyPwd: String,
        solidWindow: Boolean,
        aiConfig: AiConfig,
        requireActiveAiProfile: Boolean = false
    ): Result<Unit> = runCatching {
        fun normalizeJavaPath(rawPath: String, label: String): String? =
            rawPath.trim().takeIf { it.isNotEmpty() }?.let { path ->
                normalizeJavaExecutablePath(path) ?: throw IllegalArgumentException("${label}路径无效")
            }

        val memoryValue = maxMemoryText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val jre25 = normalizeJavaPath(jre25Path, "Java25")
        val jre21 = normalizeJavaPath(jre21Path, "Java21")
        val proxyPort = proxyPortText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val normalizedAiConfig = normalizeAiConfigForSave(aiConfig)
        val aiValidation = validateAiConfig(normalizedAiConfig, requireActiveAiProfile)
        require(aiValidation.success) { aiValidation.errorMessage ?: "AI设置无效" }

        val config = AppConfig(
            preferModMirror = preferModMirror,
            preferMcMirror = preferMcMirror,
            maxMemory = memoryValue ?: 0,
            jre25Path = jre25,
            jre21Path = jre21,
            proxyConfig = ProxyConfig(
                enabled = proxyEnabled,
                systemProxy = proxySystem,
                host = proxyHost,
                port = proxyPort ?: 10808,
                usr = proxyUsr.takeIf { it.isNotBlank() },
                pwd = proxyPwd.takeIf { it.isNotBlank() }
            ),
            aiConfig = normalizedAiConfig,
            pinyinName = CONF.pinyinName,
            solidWindow = solidWindow
        )
        AppConfig.save(config)
    }

    fun normalizeAiBaseUrl(provider: AiProvider, baseUrl: String): String =
        if (provider == AiProvider.DEEPSEEK) {
            AiProvider.DEEPSEEK.defaultBaseUrl
        } else {
            baseUrl.trim().ifBlank { provider.defaultBaseUrl }
        }

    fun normalizeAiConfigForSave(aiConfig: AiConfig): AiConfig {
        val normalized = aiConfig.normalized()
        val activeProfile = normalized.activeProfile()
        return normalized.copy(
            provider = activeProfile.provider,
            baseUrl = activeProfile.baseUrl,
            apiKey = activeProfile.apiKey,
            model = activeProfile.model,
            contextLimitTokens = activeProfile.contextLimitTokens,
            reasoningEffort = activeProfile.reasoningEffort
        )
    }

    @Serializable
    private data class AiModelsResponse(
        val data: List<AiModelEntry> = emptyList()
    )

    @Serializable
    private data class AiModelEntry(
        val id: String = ""
    )

    @Serializable
    private data class DeepSeekBalanceResponse(
        val balance_infos: List<DeepSeekBalanceInfo> = emptyList()
    )

    @Serializable
    private data class DeepSeekBalanceInfo(
        val currency: String = "",
        val total_balance: String = ""
    )

    private fun HttpResponse.toAiModelFetchError(body: String): String {
        val detail = body.extractAiErrorMessage()
        val prefix = when (status.value) {
            400 -> "400 请求格式错误"
            401 -> "401 API Key错误，认证失败"
            402 -> "402 账户余额不足"
            422 -> "422 请求参数错误"
            429 -> "429 请求速率达到上限"
            500 -> "500 服务端内部故障"
            503 -> "503 服务端繁忙"
            else -> "${status.value} ${status.description}"
        }
        return detail?.let { "$prefix：$it" } ?: prefix
    }

    private fun String.extractAiErrorMessage(): String? =
        runCatching {
            val root = serdesJson.parseToJsonElement(this).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf(String::isNotBlank)

    /**
     * Validate profile change
     */
    fun validateProfileChange(name: String, pwd: String, currentName: String): ValidationResult {
        val nameBytes = name.toByteArray(Charsets.UTF_8).size
        if (nameBytes !in 3..24) {
            return ValidationResult(false, "昵称须在3~24个字节，当前为${nameBytes}")
        }

        if (pwd.isNotEmpty() && pwd.length !in 6..16) {
            return ValidationResult(false, "密码长度须在6~16个字符")
        }

        if (name == currentName && pwd.isEmpty()) {
            return ValidationResult(false, "没有修改内容")
        }

        return ValidationResult(true)
    }
}
