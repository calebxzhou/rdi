package calebxzau.rdi.mclaunch

import calebxzau.rdi.mediaproc.MediaProcNativeRuntime
import calebxzau.rdi.mediaproc.MediaProcRuntimeClasspath
import java.io.File

data class MediaProcGameRuntime(
    val classpath: List<File>,
    val nativeLibraryDir: File,
)

object MediaProcGameClasspath {
    fun resolve(
        classpath: String = System.getProperty("java.class.path"),
        nativeRoot: File,
    ): Result<MediaProcGameRuntime> = runCatching {
        val files = MediaProcRuntimeClasspath.resolve(classpath).getOrThrow()
            .filterNot { it.name.startsWith("kotlinx-coroutines-core") }
            .distinctBy(File::getAbsolutePath)
        val nativeJar = MediaProcRuntimeClasspath.nativeJar(files)
        val nativeLibraryDir = MediaProcNativeRuntime.prepare(nativeJar, nativeRoot).getOrThrow()
        MediaProcGameRuntime(files, nativeLibraryDir)
    }
}
