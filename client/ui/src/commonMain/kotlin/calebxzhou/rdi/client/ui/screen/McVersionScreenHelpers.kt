package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.common.model.Task2
import java.io.File

/**
 * Desktop-only helper functions used by McVersionScreen.
 * These are only called behind `isDesktop` guard.
 * Android provides no-op stubs.
 */

expect fun selectRdiPackFiles(): List<File>?

expect fun buildImportPackTask2(packFile: File): Task2

expect suspend fun importRdiModpackTask2(onProgress: (String) -> Unit): Task2

expect suspend fun exportRdiModpack(
    packdir: ModpackLocalDir,
    onProgress: (String) -> Unit
): Result<Unit>

expect suspend fun exportLogsPack(packdir: ModpackLocalDir): Result<Unit>
