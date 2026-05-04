package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.AiConfig
import calebxzhou.rdi.client.AiProvider
import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.client.ui.getPlatformTotalPhysicalMemoryMb
import calebxzhou.rdi.client.ui.validatePlatformJavaPath
import calebxzhou.rdi.CONF
import calebxzhou.rdi.common.ProxyConfig

object SettingsService {

    data class ValidationResult(
        val success: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Get total physical memory in MB
     */
    fun getTotalPhysicalMemoryMb(): Int = getPlatformTotalPhysicalMemoryMb()

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
     * Validate Java path — delegates to platform-specific implementation.
     */
    fun validateJavaPath(rawPath: String, expectedMajor: Int): Result<Unit> =
        validatePlatformJavaPath(rawPath, expectedMajor)

    fun validateAiSettings(provider: AiProvider, baseUrl: String, model: String): ValidationResult {
        val normalizedBaseUrl = normalizeAiBaseUrl(provider, baseUrl)
        if (!normalizedBaseUrl.startsWith("http://") && !normalizedBaseUrl.startsWith("https://")) {
            return ValidationResult(false, "AI接口地址必须以http://或https://开头")
        }
        if (model.isBlank()) {
            return ValidationResult(false, "AI模型不能为空")
        }
        return ValidationResult(true)
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
        jre8Path: String,
        proxyEnabled: Boolean,
        proxySystem: Boolean,
        proxyHost: String,
        proxyPortText: String,
        proxyUsr: String,
        proxyPwd: String,
        aiProvider: AiProvider,
        aiBaseUrl: String,
        aiApiKey: String,
        aiModel: String
    ): Result<Unit> = runCatching {
        val memoryValue = maxMemoryText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val jre25 = jre25Path.trim().takeIf { it.isNotEmpty() }
        val jre21 = jre21Path.trim().takeIf { it.isNotEmpty() }
        val jre8 = jre8Path.trim().takeIf { it.isNotEmpty() }
        val proxyPort = proxyPortText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val normalizedAiBaseUrl = normalizeAiBaseUrl(aiProvider, aiBaseUrl)
        val normalizedAiModel = aiModel.trim().ifBlank { aiProvider.defaultModel }
        val aiValidation = validateAiSettings(aiProvider, normalizedAiBaseUrl, normalizedAiModel)
        require(aiValidation.success) { aiValidation.errorMessage ?: "AI设置无效" }

        val config = AppConfig(
            preferModMirror = preferModMirror,
            preferMcMirror = preferMcMirror,
            maxMemory = memoryValue ?: 0,
            jre25Path = jre25,
            jre21Path = jre21,
            jre8Path = jre8,
            proxyConfig = ProxyConfig(
                enabled = proxyEnabled,
                systemProxy = proxySystem,
                host = proxyHost,
                port = proxyPort ?: 10808,
                usr = proxyUsr.takeIf { it.isNotBlank() },
                pwd = proxyPwd.takeIf { it.isNotBlank() }
            ),
            aiConfig = AiConfig(
                provider = aiProvider,
                baseUrl = normalizedAiBaseUrl,
                apiKey = aiApiKey.trim(),
                model = normalizedAiModel
            ),
            pinyinName = CONF.pinyinName
        )
        AppConfig.save(config)
    }

    fun normalizeAiBaseUrl(provider: AiProvider, baseUrl: String): String =
        if (provider == AiProvider.DEEPSEEK) {
            AiProvider.DEEPSEEK.defaultBaseUrl
        } else {
            baseUrl.trim().ifBlank { provider.defaultBaseUrl }
        }

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
