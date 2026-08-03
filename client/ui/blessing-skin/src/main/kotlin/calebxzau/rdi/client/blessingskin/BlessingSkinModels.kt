package calebxzau.rdi.client.blessingskin

enum class BlessingTextureFilter(val wireValue: String) {
    SKIN("skin"),
    CAPE("cape")
}

enum class BlessingTextureSort(val wireValue: String) {
    LIKES("likes"),
    TIME("time")
}

enum class BlessingTextureType(val wireValue: String) {
    STEVE("steve"),
    ALEX("alex"),
    CAPE("cape");

    val isCape: Boolean
        get() = this == CAPE

    val isSlim: Boolean
        get() = this == ALEX

    companion object {
        fun fromWireValue(value: String): BlessingTextureType? =
            entries.firstOrNull { it.wireValue == value.lowercase() }
    }
}

data class BlessingTextureSearch(
    val keyword: String = "",
    val filter: BlessingTextureFilter = BlessingTextureFilter.SKIN,
    val sort: BlessingTextureSort = BlessingTextureSort.LIKES
)

data class BlessingTextureSummary(
    val id: Int,
    val name: String,
    val type: BlessingTextureType,
    val uploaderId: Int,
    val isPublic: Boolean,
    val likes: Int,
    val previewUrl: String
)

data class BlessingTexturePage(
    val items: List<BlessingTextureSummary>,
    val nextPage: Int?
)

data class ResolvedBlessingTexture(
    val id: Int,
    val name: String,
    val type: BlessingTextureType,
    val uploaderId: Int,
    val isPublic: Boolean,
    val likes: Int,
    val hash: String,
    val textureUrl: String,
    val previewUrl: String
)
