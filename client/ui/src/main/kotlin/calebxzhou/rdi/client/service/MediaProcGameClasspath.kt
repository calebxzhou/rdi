package calebxzhou.rdi.client.service

import calebxzau.rdi.mediaproc.MediaProcRuntimeClasspath
import calebxzau.rdi.mediaproc.MediaProcNativeRuntime
import java.io.File

internal data class MediaProcGameRuntime(
    val classpath: List<File>,
    val nativeLibraryDir: File
)

internal object MediaProcGameClasspath {
    fun resolve(
        classpath: String = System.getProperty("java.class.path"),
        nativeRoot: File = ClientDirs.toolsDir.resolve("mediaproc-natives")
    ): Result<MediaProcGameRuntime> = runCatching {
        val files = MediaProcRuntimeClasspath.resolve(classpath).getOrThrow()
            .filterNot { it.name.startsWith("kotlinx-coroutines-core") }
            .distinctBy { it.absolutePath }
        val nativeJar = MediaProcRuntimeClasspath.nativeJar(files)
        val nativeLibraryDir = MediaProcNativeRuntime.prepare(nativeJar, nativeRoot).getOrThrow()
        MediaProcGameRuntime(files, nativeLibraryDir)
    }
}
