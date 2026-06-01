package calebxzhou.rdi.common.anvilrw.format

import java.io.File

enum class FileFormat(val extension: String, val description: String) {
    ANVIL("mca", "Anvil Format"),
    MCREGION("mcr", "McRegion (Alpha) Format"),
    BEDROCK("mcworld", "Bedrock Format"),
    UNKNOWN("unknown", "Unknown Format");

    companion object {
        fun detectFormat(file: File): FileFormat {
            val fileName = file.name.lowercase()
            return when {
                fileName.endsWith(ANVIL.extension) -> ANVIL
                fileName.endsWith(MCREGION.extension) -> MCREGION
                fileName.endsWith(BEDROCK.extension) -> BEDROCK
                else -> UNKNOWN
            }
        }

        fun detectFormat(fileName: String): FileFormat {
            require(fileName.isNotBlank()) { "File path cannot be null or empty" }
            return detectFormat(File(fileName))
        }
    }
}
