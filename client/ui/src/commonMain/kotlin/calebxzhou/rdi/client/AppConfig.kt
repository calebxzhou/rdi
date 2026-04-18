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
data class AppConfig(
    val preferModMirror: Boolean = true,
    val preferMcMirror: Boolean = true,
    //不限制
    val maxMemory: Int=0,
    val jre21Path: String?=null,
    val jre8Path: String?=null,
    val proxyConfig: ProxyConfig?=null,
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

