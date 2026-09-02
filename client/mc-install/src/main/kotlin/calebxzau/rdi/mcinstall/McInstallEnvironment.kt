package calebxzau.rdi.mcinstall

import calebxzhou.rdi.common.model.McVersion
import java.io.File
import java.io.InputStream

data class McInstallDirectories(
    val mcDir: File,
    val versionsDir: File,
    val librariesDir: File,
    val assetsDir: File,
    val assetIndexesDir: File,
    val assetObjectsDir: File,
)

class McInstallEnvironment(
    val directories: McInstallDirectories,
    val preferMirror: () -> Boolean,
    val javaPath: () -> String,
    val resourceLoader: (String) -> Result<InputStream>,
    val launchPreparer: McLaunchPreparer,
)

fun interface McLaunchPreparer {
    suspend fun prepare(
        mcVersion: McVersion,
        versionId: String,
        versionDir: File,
        onProgress: (String) -> Unit,
    ): Result<Unit>
}

data class McLaunchPreparationRequest(
    val mcVersion: McVersion,
    val loader: calebxzhou.rdi.common.model.ModLoader,
    val versionId: String,
)
