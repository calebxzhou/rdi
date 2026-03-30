package calebxzhou.rdi

import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.common.service.ModService
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Global configuration and logger accessible from commonMain.
 * Desktop: initialized by RDI.kt main().
 * Android: initialized by MainActivity.
 */
var CONF = AppConfig.load()
val lgr = KotlinLogging.logger("RDI")
