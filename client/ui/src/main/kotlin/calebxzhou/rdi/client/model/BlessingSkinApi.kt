package calebxzhou.rdi.client.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-01-12 20:55
 */

@Serializable
data class BSSkinData(
    val tid: Int,
    val name: String,
    val type: String,
    val uploader: Int,
    val public: Boolean,
    val likes: Int
) {
    val isCape: Boolean
        get() = type == "cape"
    val isSlim: Boolean
        get() = type == "steve"
}

@Serializable
data class BSSkin(
    val tid: Int,
    val name: String,
    val type: String,
    val hash: String,
    val size: Int,
    val uploader: Int,
    val public: Boolean,
    val upload_at: String,
    val likes: Int
)
