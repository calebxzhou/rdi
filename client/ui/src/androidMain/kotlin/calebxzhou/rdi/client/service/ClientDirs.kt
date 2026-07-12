package calebxzhou.rdi.client.service

import android.content.Context
import java.io.File

actual object ClientDirs {
    /**
     * Must be initialized from an Android entrypoint before use.
     * Uses context.getExternalFilesDir(dirName).
     */
    private lateinit var baseDir: File

    fun ensureInit(context: Context) {
        if (!::baseDir.isInitialized) {
            init(context.getExternalFilesDir(null) ?: context.filesDir)
        }
    }

    fun init(externalFilesDir: File) {
        baseDir = externalFilesDir
        // Ensure all dirs exist
        dlPacksDir.mkdirs()
        versionsDir.mkdirs()
        dlModsDir.mkdirs()
        packProcDir.mkdirs()
        mcDir.mkdirs()
        librariesDir.mkdirs()
        assetsDir.mkdirs()
        assetIndexesDir.mkdirs()
        assetObjectsDir.mkdirs()
    }

    actual val dlPacksDir: File get() = baseDir.resolve("dl-packs")
    actual val versionsDir: File get() = mcDir.resolve("versions")
    actual val dlModsDir: File get() = baseDir.resolve("dl-mods")
    actual val packProcDir: File get() = baseDir.resolve("pack-proc")
    actual val mcDir: File get() = baseDir.resolve("mc")
    actual val librariesDir: File get() = mcDir.resolve("libraries")
    actual val assetsDir: File get() = mcDir.resolve("assets")
    actual val assetIndexesDir: File get() = assetsDir.resolve("indexes")
    actual val assetObjectsDir: File get() = assetsDir.resolve("objects")
    actual val toolsDir: File
        get() = throw UnsupportedOperationException("不支持tools")
}
