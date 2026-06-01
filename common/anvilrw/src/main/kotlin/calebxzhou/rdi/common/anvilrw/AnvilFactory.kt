package calebxzhou.rdi.common.anvilrw

import calebxzhou.rdi.common.anvilrw.format.AnvilReader
import calebxzhou.rdi.common.anvilrw.format.AnvilWriter
import calebxzhou.rdi.common.anvilrw.format.FileFormat
import java.io.File

object AnvilFactory {
    fun createReader(anvilFile: File): AnvilReader {
        require(isValidAnvilFile(anvilFile)) {
            "Invalid or unsupported file format: ${anvilFile.name}"
        }
        return AnvilReader(anvilFile)
    }

    fun createReader(filePath: String): AnvilReader {
        require(filePath.isNotBlank()) { "File path cannot be null or empty" }
        return createReader(File(filePath))
    }

    fun createWriter(anvilFile: File): AnvilWriter {
        val format = FileFormat.detectFormat(anvilFile)
        require(format == FileFormat.ANVIL) {
            "Unsupported file format for writing: ${format.description}"
        }
        return AnvilWriter(anvilFile)
    }

    fun createWriter(filePath: String): AnvilWriter {
        require(filePath.isNotBlank()) { "File path cannot be null or empty" }
        return createWriter(File(filePath))
    }

    fun isValidAnvilFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return FileFormat.detectFormat(file) == FileFormat.ANVIL
    }

    fun isValidAnvilFile(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        return isValidAnvilFile(File(filePath))
    }
}
