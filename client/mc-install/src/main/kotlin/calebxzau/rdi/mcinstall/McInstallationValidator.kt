package calebxzau.rdi.mcinstall

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

sealed interface McInstallationValidation {
    data class Valid(val realPath: Path) : McInstallationValidation
    data object Missing : McInstallationValidation
    data object Invalid : McInstallationValidation
    data object Inaccessible : McInstallationValidation
}

interface McInstallationValidator {
    fun validateDiscoveredCandidate(candidate: Path, fixedDriveRoots: List<Path>): McInstallationValidation

    fun validateStoredRealPath(realPath: Path, fixedDriveRoots: List<Path>): McInstallationValidation
}

fun interface McFixedVolumeChecker {
    fun isFixed(path: Path): Boolean
}

class DefaultMcInstallationValidator(
    private val fixedVolumeChecker: McFixedVolumeChecker = WindowsMcFixedVolumeChecker
) : McInstallationValidator {
    override fun validateDiscoveredCandidate(
        candidate: Path,
        fixedDriveRoots: List<Path>
    ): McInstallationValidation {
        if (!candidate.fileName?.toString().equals(MINECRAFT_DIRECTORY_NAME, ignoreCase = true)) {
            return McInstallationValidation.Invalid
        }
        return validateRealRoot(candidate, fixedDriveRoots)
    }

    override fun validateStoredRealPath(
        realPath: Path,
        fixedDriveRoots: List<Path>
    ): McInstallationValidation = validateRealRoot(realPath, fixedDriveRoots)

    private fun validateRealRoot(
        candidate: Path,
        fixedDriveRoots: List<Path>
    ): McInstallationValidation {
        val root = resolveDirectory(candidate) ?: return resolutionFailure(candidate)
        if (!isOnFixedDrive(root, fixedDriveRoots)) return McInstallationValidation.Invalid

        for (requiredDirectory in REQUIRED_DIRECTORIES) {
            val required = resolveDirectory(root.resolve(requiredDirectory))
                ?: return resolutionFailure(root.resolve(requiredDirectory))
            if (!isOnFixedDrive(required, fixedDriveRoots)) return McInstallationValidation.Invalid
        }
        return McInstallationValidation.Valid(root)
    }

    private fun resolveDirectory(path: Path): Path? {
        return try {
            val realPath = path.toRealPath()
            val attributes = Files.readAttributes(realPath, BasicFileAttributes::class.java)
            if (!attributes.isDirectory) return null
            Files.newDirectoryStream(realPath).use { }
            realPath.toAbsolutePath().normalize()
        } catch (_: NoSuchFileException) {
            null
        } catch (_: AccessDeniedException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun resolutionFailure(path: Path): McInstallationValidation {
        return try {
            if (Files.notExists(path, NOFOLLOW_LINKS)) {
                McInstallationValidation.Missing
            } else {
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther) {
                    McInstallationValidation.Invalid
                } else {
                    McInstallationValidation.Inaccessible
                }
            }
        } catch (_: NoSuchFileException) {
            McInstallationValidation.Missing
        } catch (_: SecurityException) {
            McInstallationValidation.Inaccessible
        } catch (_: IOException) {
            McInstallationValidation.Inaccessible
        }
    }

    private fun isOnFixedDrive(path: Path, fixedDriveRoots: List<Path>): Boolean {
        if (!fixedVolumeChecker.isFixed(path)) return false
        if (fixedDriveRoots.isEmpty()) return true

        val normalizedPath = path.toAbsolutePath().normalize()
        return fixedDriveRoots.any { root ->
            val normalizedRoot = runCatching { root.toRealPath() }
                .getOrElse { root.toAbsolutePath().normalize() }
            normalizedPath == normalizedRoot || normalizedPath.startsWith(normalizedRoot)
        }
    }

    private companion object {
        const val MINECRAFT_DIRECTORY_NAME = ".minecraft"
        val REQUIRED_DIRECTORIES = listOf("assets", "libraries", "versions")
    }
}

object WindowsMcFixedVolumeChecker : McFixedVolumeChecker {
    override fun isFixed(path: Path): Boolean {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return false
        val root = path.root ?: return false
        return Kernel32.INSTANCE.GetDriveType(root.toString()) == WinBase.DRIVE_FIXED
    }
}
