package calebxzhou.rdi.common.anvilrw

class ChunkTooLargeException : RuntimeException {
    constructor(dataSize: Int) : super(
        "Chunks written to MCA files cannot exceed the size of 1MiB (${calebxzhou.rdi.common.anvilrw.util.AnvilConstants.MAX_CHUNK_SIZE_FORMATTED})! Tried to write $dataSize bytes."
    )
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
