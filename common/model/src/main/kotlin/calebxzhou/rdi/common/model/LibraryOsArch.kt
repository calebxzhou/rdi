package calebxzhou.rdi.common.model

import java.util.Locale
import kotlin.text.contains
import kotlin.text.lowercase

enum class LibraryOsArch(val archSuffix: String,val ruleOsName: String) {
    WIN_X64("-windows","windows"),
    WIN_ARM64("-windows-arm64","windows"),
    MAC_X64("-macos","osx"),
    MAC_ARM64("-macos-arm64","osx"),
    LINUX_X64("-linux","linux"),
    LINUX_ARM64("-linux-aarch_64","linux"),
    ;
    val isWindows get()= this == WIN_X64 || this == WIN_ARM64
    val isUnixLike get() = !isWindows
    companion object{

        fun detectHostOs(): LibraryOsArch {
            val osName = System.getProperty("os.name")?.lowercase(Locale.ROOT) ?: ""
            val normalizedName = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("os x") -> "osx"
                else -> "linux"
            }
            val rawArch = System.getProperty("os.arch")?.lowercase(Locale.ROOT) ?: ""
            val normalizedArch = when {
                rawArch.contains("aarch64") || (rawArch.contains("arm") && rawArch.contains("64")) -> "arm64"
                rawArch.contains("arm") -> "arm"
                rawArch.contains("64") || rawArch.contains("x86_64") || rawArch.contains("amd64") -> "x64"
                else -> "x86"
            }

            return when (normalizedName) {
                "windows" -> if (normalizedArch == "arm64") WIN_ARM64 else WIN_X64
                "osx" -> if (normalizedArch == "arm64") MAC_ARM64 else MAC_X64
                else -> if (normalizedArch == "arm64") LINUX_ARM64 else LINUX_X64
            }
        }

    }
}