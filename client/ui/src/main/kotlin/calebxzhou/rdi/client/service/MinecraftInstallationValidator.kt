package calebxzhou.rdi.client.service

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

sealed interface MinecraftInstallationValidation {
    data class Valid(val realPath: Path) : MinecraftInstallationValidation
    data object Missing : MinecraftInstallationValidation
    data object Invalid : MinecraftInstallationValidation
    data object Inaccessible : MinecraftInstallationValidation
}

interface MinecraftInstallationValidator {
    fun validateDiscoveredCandidate(candidate: Path, fixedDriveRoots: List<Path>): MinecraftInstallationValidation

    fun validateStoredRealPath(realPath: Path, fixedDriveRoots: List<Path>): MinecraftInstallationValidation
}

fun interface MinecraftFixedVolumeChecker {
    fun isFixed(path: Path): Boolean
}

class DefaultMinecraftInstallationValidator(
    private val fixedVolumeChecker: MinecraftFixedVolumeChecker = WindowsMinecraftFixedVolumeChecker
) : MinecraftInstallationValidator {
    override fun validateDiscoveredCandidate(
        candidate: Path,
        fixedDriveRoots: List<Path>
    ): MinecraftInstallationValidation {
        if (!candidate.fileName?.toString().equals(MINECRAFT_DIRECTORY_NAME, ignoreCase = true)) {
            return MinecraftInstallationValidation.Invalid
        }
        return validateRealRoot(candidate, fixedDriveRoots)
    }

    override fun validateStoredRealPath(
        realPath: Path,
        fixedDriveRoots: List<Path>
    ): MinecraftInstallationValidation = validateRealRoot(realPath, fixedDriveRoots)

    private fun validateRealRoot(
        candidate: Path,
        fixedDriveRoots: List<Path>
    ): MinecraftInstallationValidation {
        val root = resolveDirectory(candidate) ?: return resolutionFailure(candidate)
        if (!isOnFixedDrive(root, fixedDriveRoots)) return MinecraftInstallationValidation.Invalid

        for (requiredDirectory in REQUIRED_DIRECTORIES) {
            val required = resolveDirectory(root.resolve(requiredDirectory))
                ?: return resolutionFailure(root.resolve(requiredDirectory))
            if (!isOnFixedDrive(required, fixedDriveRoots)) return MinecraftInstallationValidation.Invalid
        }
        return MinecraftInstallationValidation.Valid(root)
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

    private fun resolutionFailure(path: Path): MinecraftInstallationValidation {
        return try {
            if (Files.notExists(path, NOFOLLOW_LINKS)) {
                MinecraftInstallationValidation.Missing
            } else {
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther) {
                    MinecraftInstallationValidation.Invalid
                } else {
                    MinecraftInstallationValidation.Inaccessible
                }
            }
        } catch (_: NoSuchFileException) {
            MinecraftInstallationValidation.Missing
        } catch (_: SecurityException) {
            MinecraftInstallationValidation.Inaccessible
        } catch (_: IOException) {
            MinecraftInstallationValidation.Inaccessible
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

object WindowsMinecraftFixedVolumeChecker : MinecraftFixedVolumeChecker {
    override fun isFixed(path: Path): Boolean {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return false
        val root = path.root ?: return false
        return Kernel32.INSTANCE.GetDriveType(root.toString()) == WinBase.DRIVE_FIXED
    }
}
