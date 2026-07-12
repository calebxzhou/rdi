package calebxzhou.rdi.client.service

import java.io.File

/**
 * Platform-specific client directories.
 * Desktop: uses RDIClient.DIR (user.dir)
 * Android: uses context.getExternalFilesDir(name)
 */
expect object ClientDirs {
    /** Directory for downloaded modpacks (zip archives) */
    val dlPacksDir: File

    /** Directory for installed game versions / modpack versions */
    val versionsDir: File

    /** Directory for downloaded mod files */
    val dlModsDir: File

    /** Temporary processing directory for modpack operations */
    val packProcDir: File

    /** Root MC directory */
    val mcDir: File

    /** Libraries directory */
    val librariesDir: File

    /** Assets directory */
    val assetsDir: File

    /** Asset indexes directory */
    val assetIndexesDir: File

    /** Asset objects directory */
    val assetObjectsDir: File
    val toolsDir: File
}
