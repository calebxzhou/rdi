package calebxzhou.rdi.client.service

import calebxzau.rdi.mediaproc.MediaProcRuntimeClasspath
import java.io.File

internal object MediaProcGameClasspath {
    fun resolve(classpath: String = System.getProperty("java.class.path")): Result<List<File>> =
        MediaProcRuntimeClasspath.resolve(classpath)
}
