package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.common.model.Task2
import java.io.File

/**
 * Android stubs for desktop-only McVersionScreen helpers.
 * These are only called behind `isDesktop` guard, so they should never actually execute.
 */

actual fun selectRdiPackFiles(): List<File>? = null

actual fun buildImportPackTask2(zipFile: File): Task2 {
    error("Import pack not supported on Android")
}

actual suspend fun importRdiModpackTask2(onProgress: (String) -> Unit): Task2 {
    error("Import modpack not supported on Android")
}

actual suspend fun exportRdiModpack(
    packdir: ModpackLocalDir,
    onProgress: (String) -> Unit
): Result<Unit> = Result.failure(UnsupportedOperationException("Not supported on Android"))

actual suspend fun exportLogsPack(packdir: ModpackLocalDir): Result<Unit> =
    Result.failure(UnsupportedOperationException("Not supported on Android"))
