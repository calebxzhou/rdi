package calebxzhou.rdi.common.exception

import calebxzhou.rdi.common.model.CFDownloadMod

open class CFApiException(msg: String?) : Exception(msg)

class CFDownloadModException(
	val failed: List<Pair<CFDownloadMod, Throwable>>
) : Exception(
	"Failed to download mods: " + failed.joinToString(", ") { "${it.first.projectId}.${it.first.fileId}" },
	failed.firstOrNull()?.second
)