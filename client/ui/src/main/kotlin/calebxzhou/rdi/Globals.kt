package calebxzhou.rdi

import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.common.service.ModService
import io.github.oshai.kotlinlogging.KotlinLogging

/** Global configuration and logger initialized by the launcher entry point. */
var CONF = AppConfig.load()
val lgr = KotlinLogging.logger("RDI")
