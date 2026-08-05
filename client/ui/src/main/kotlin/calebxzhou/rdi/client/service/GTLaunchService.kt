package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.McVersion
import java.io.File

/**
 * Compatibility entry kept for callers that still request GTNH preparation directly.
 * The launch implementation lives in client/mclaunch.
 */
internal suspend fun GameService.ensureGtnhRuntime(
    versionDir: File,
    onProgress: (String) -> Unit,
): Result<Unit> = createMinecraftLauncher().prepare(
    request = minecraftLaunchRequest(
        mcVersion = McVersion.V071,
        versionId = versionDir.name,
        versionDir = versionDir,
    ),
    onProgress = onProgress,
)
