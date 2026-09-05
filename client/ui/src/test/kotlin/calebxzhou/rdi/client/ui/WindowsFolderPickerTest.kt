package calebxzhou.rdi.client.ui

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsFolderPickerTest {
    @Test
    fun `modern folder options preserve existing flags and add folder flags`() {
        val existing = 0x1000

        val result = modernFolderDialogOptions(existing)

        assertEquals(existing or 0x20 or 0x40 or 0x800, result)
    }

    @Test
    fun `only the Windows cancelled HRESULT is treated as cancellation`() {
        assertEquals(true, isModernFolderDialogCancelled(0x800704C7.toInt()))
        assertEquals(false, isModernFolderDialogCancelled(0x80004005.toInt()))
        assertEquals(false, isModernFolderDialogCancelled(0))
    }

    @Test
    fun `existing directory is accepted`() {
        val directory = Files.createTempDirectory("modern-folder-picker-test").toFile()
        try {
            assertEquals(directory, validatedSelectedDirectory(directory.absolutePath))
        } finally {
            directory.delete()
        }
    }

    @Test
    fun `file blank and missing paths are rejected`() {
        val directory = Files.createTempDirectory("modern-folder-picker-test").toFile()
        val file = directory.resolve("file.txt").apply { writeText("test") }
        try {
            assertNull(validatedSelectedDirectory(file.absolutePath))
            assertNull(validatedSelectedDirectory(" "))
            assertNull(validatedSelectedDirectory(directory.resolve("missing").absolutePath))
            assertNull(validatedSelectedDirectory(null))
        } finally {
            file.delete()
            directory.delete()
        }
    }

    @Test
    fun `requested directory takes precedence over Downloads and home`() {
        val home = Files.createTempDirectory("modern-folder-picker-home").toFile()
        val requested = Files.createTempDirectory("modern-folder-picker-requested").toFile()
        home.resolve("Downloads").mkdirs()
        try {
            assertEquals(requested, modernInitialDirectory(requested, home))
        } finally {
            requested.delete()
            home.resolve("Downloads").delete()
            home.delete()
        }
    }

    @Test
    fun `Downloads is used when requested directory is invalid`() {
        val home = Files.createTempDirectory("modern-folder-picker-home").toFile()
        val downloads = home.resolve("Downloads").apply { mkdirs() }
        try {
            assertEquals(downloads, modernInitialDirectory(home.resolve("missing"), home))
        } finally {
            downloads.delete()
            home.delete()
        }
    }

    @Test
    fun `regular file at Downloads is rejected in favor of home`() {
        val home = Files.createTempDirectory("modern-folder-picker-home").toFile()
        val downloads = home.resolve("Downloads").apply { writeText("not a directory") }
        try {
            assertEquals(home, modernInitialDirectory(home.resolve("missing"), home))
        } finally {
            downloads.delete()
            home.delete()
        }
    }

    @Test
    fun `no valid initial directory returns null`() {
        val home = Files.createTempDirectory("modern-folder-picker-home").toFile()
        val invalidHome = home.resolve("missing-home")
        try {
            assertNull(modernInitialDirectory(home.resolve("missing"), invalidHome))
        } finally {
            home.delete()
        }
    }
}
