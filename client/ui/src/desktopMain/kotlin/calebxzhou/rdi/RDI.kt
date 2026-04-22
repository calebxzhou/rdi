package calebxzhou.rdi

import java.io.File

object RDIClient {
    val DIR: File = File(System.getProperty("user.dir")).absoluteFile

    init {
        lgr.info { "RDI启动中" }
        DIR.mkdir()
        lgr.info { (javaClass.protectionDomain.codeSource.location.toURI().toString()) }

    }

}

fun main() {
    //redirect new ui
    calebxzhou.rdi.client.main()
}