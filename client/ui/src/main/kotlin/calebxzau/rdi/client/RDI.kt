package calebxzau.rdi.client

import calebxzhou.rdi.client.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
/** Global configuration and logger initialized by the launcher entry point. */
var CONF = AppConfig.load()
val lgr = KotlinLogging.logger("RDI")

object RDIClient {
    val DIR: File = File(System.getProperty("user.dir")).absoluteFile
    var OLD_MAIN = false
    init {
        lgr.info { "RDI启动中" }
        DIR.mkdir()
        lgr.info { (javaClass.protectionDomain.codeSource.location.toURI().toString()) }
        System.setProperty("compose.interop.blending", "true")
    }

}