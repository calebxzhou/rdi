package calebxzhou.rdi.common

import java.io.File
import java.nio.file.Paths

val DIR = File(System.getProperty("user.dir")).absoluteFile
var DL_MOD_DIR =
    System.getProperty("rdi.modDir")
        ?.let { File(it) }
        ?: DIR.resolve("dl-mods").also { it.mkdirs() }


object RDI {

}