package calebxzhou.rdi.client.service

import calebxzhou.rdi.RDIClient
import java.io.File

actual object ClientDirs {
    actual val dlPacksDir: File = RDIClient.DIR.resolve("dl-packs").also { it.mkdirs() }
    actual val dlModsDir: File = RDIClient.DIR.resolve("dl-mods").also { it.mkdirs() }
    actual val packProcDir: File = RDIClient.DIR.resolve("pack-proc").also { it.mkdirs() }
    actual val mcDir: File = RDIClient.DIR.resolve("mc").also { it.mkdirs() }
    actual val versionsDir: File = mcDir.resolve("versions").also { it.mkdirs() }
    actual val librariesDir: File = mcDir.resolve("libraries").also { it.mkdirs() }
    actual val assetsDir: File = mcDir.resolve("assets").also { it.mkdirs() }
    actual val assetIndexesDir: File = assetsDir.resolve("indexes").also { it.mkdirs() }
    actual val assetObjectsDir: File = assetsDir.resolve("objects").also { it.mkdirs() }
}
