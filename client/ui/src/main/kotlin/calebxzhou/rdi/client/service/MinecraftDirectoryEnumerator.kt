package calebxzhou.rdi.client.service

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.isDirectory

data class MinecraftDirectoryEntry(
    val path: Path,
    val isReparsePoint: Boolean
)

fun interface MinecraftDirectoryEnumerator {
    fun enumerate(directory: Path): Result<List<MinecraftDirectoryEntry>>
}

class NioMinecraftDirectoryEnumerator : MinecraftDirectoryEnumerator {
    override fun enumerate(directory: Path): Result<List<MinecraftDirectoryEntry>> = runCatching {
        Files.newDirectoryStream(directory).use { entries ->
            buildList {
                entries.forEach { entry ->
                    val attributes = runCatching {
                        Files.readAttributes(
                            entry,
                            java.nio.file.attribute.BasicFileAttributes::class.java,
                            NOFOLLOW_LINKS
                        )
                    }.getOrNull() ?: return@forEach
                    val isReparsePoint = attributes.isSymbolicLink || attributes.isOther
                    if (attributes.isDirectory || isReparsePoint && entry.isDirectory()) {
                        add(MinecraftDirectoryEntry(entry, isReparsePoint))
                    }
                }
            }
        }
    }
}

class WindowsMinecraftDirectoryEnumerator(
    private val native: MinecraftDirectoryEnumerator = WindowsNativeMinecraftDirectoryEnumerator(),
    private val fallback: MinecraftDirectoryEnumerator = NioMinecraftDirectoryEnumerator()
) : MinecraftDirectoryEnumerator {
    override fun enumerate(directory: Path): Result<List<MinecraftDirectoryEntry>> {
        if (!isWindows()) return fallback.enumerate(directory)
        return native.enumerate(directory).fold(
            onSuccess = { Result.success(it) },
            onFailure = { nativeFailure ->
                fallback.enumerate(directory).onFailure { fallbackFailure ->
                    fallbackFailure.addSuppressed(nativeFailure)
                }
            }
        )
    }
}

class WindowsNativeMinecraftDirectoryEnumerator : MinecraftDirectoryEnumerator {
    private val kernel32: Kernel32 by lazy { Kernel32.INSTANCE }

    override fun enumerate(directory: Path): Result<List<MinecraftDirectoryEntry>> = runCatching {
        check(isWindows()) { "Windows native directory enumeration is only available on Windows" }
        val searchPath = directory.toString().trimEnd('\\', '/') + "\\*"
        val data = WinBase.WIN32_FIND_DATA()
        val handle = kernel32.FindFirstFileEx(
            searchPath,
            WinBase.FindExInfoBasic,
            data.pointer,
            WinBase.FindExSearchLimitToDirectories,
            null,
            WinDef.DWORD(FIND_FIRST_EX_LARGE_FETCH.toLong())
        )
        if (isInvalidHandle(handle)) {
            val error = kernel32.GetLastError()
            if (error == ERROR_FILE_NOT_FOUND) return@runCatching emptyList()
            throw IOException("FindFirstFileEx failed for $directory, Windows error $error")
        }

        try {
            buildList {
                do {
                    data.read()
                    val name = data.getFileName()
                    if (
                        name != "." &&
                        name != ".." &&
                        data.dwFileAttributes and WinNT.FILE_ATTRIBUTE_DIRECTORY != 0
                    ) {
                        add(
                            MinecraftDirectoryEntry(
                                directory.resolve(name),
                                data.dwFileAttributes and WinNT.FILE_ATTRIBUTE_REPARSE_POINT != 0
                            )
                        )
                    }
                } while (kernel32.FindNextFile(handle, data.pointer))

                val error = kernel32.GetLastError()
                if (error != ERROR_NO_MORE_FILES) {
                    throw IOException("FindNextFile failed for $directory, Windows error $error")
                }
            }
        } finally {
            kernel32.FindClose(handle)
        }
    }

    private fun isInvalidHandle(handle: WinNT.HANDLE?): Boolean =
        handle == null || Pointer.nativeValue(handle.pointer) == -1L

    private companion object {
        const val FIND_FIRST_EX_LARGE_FETCH = 0x02
        const val ERROR_FILE_NOT_FOUND = 2
        const val ERROR_NO_MORE_FILES = 18
    }
}

private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
