package calebxzhou.rdi

import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.client.AppConfig
import java.io.File

fun main() {
    //redirect new ui
    calebxzhou.rdi.client.main()
}

object RDIClient {
    val DIR: File = File(System.getProperty("user.dir")).absoluteFile
    val JRE21 = javaExePath
    init {
        lgr.info { "RDI启动中" }
        DIR.mkdir()
        lgr.info { (javaClass.protectionDomain.codeSource.location.toURI().toString()) }

    }

}

