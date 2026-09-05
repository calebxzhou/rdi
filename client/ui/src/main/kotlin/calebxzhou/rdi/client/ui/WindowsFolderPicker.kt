package calebxzhou.rdi.client.ui

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

private const val FOS_PICKFOLDERS = 0x20
private const val FOS_FORCEFILESYSTEM = 0x40
private const val FOS_PATHMUSTEXIST = 0x800
private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
private const val ERROR_CANCELLED_HRESULT = 0x800704C7.toInt()

private val CLSID_FILE_OPEN_DIALOG = Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
private val IID_FILE_OPEN_DIALOG = Guid.GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
private val IID_SHELL_ITEM = Guid.GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")

private interface Shell32Modern : StdCallLibrary {
    fun SHCreateItemFromParsingName(
        path: WString,
        bindContext: Pointer?,
        riid: Guid.GUID,
        result: PointerByReference
    ): WinNT.HRESULT
}

private object Shell32ModernLibrary {
    val instance: Shell32Modern by lazy {
        Native.load("shell32", Shell32Modern::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

private class FileOpenDialog(pointer: Pointer) : Unknown(pointer) {
    fun getOptions(options: IntByReference): WinNT.HRESULT =
        _invokeNativeObject(10, arrayOf(pointer, options), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun setOptions(options: Int): WinNT.HRESULT =
        _invokeNativeObject(9, arrayOf(pointer, options), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun setFolder(folder: Pointer): WinNT.HRESULT =
        _invokeNativeObject(12, arrayOf(pointer, folder), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun setTitle(title: WString): WinNT.HRESULT =
        _invokeNativeObject(17, arrayOf(pointer, title), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun show(): WinNT.HRESULT =
        _invokeNativeObject(3, arrayOf(pointer, null), WinNT.HRESULT::class.java) as WinNT.HRESULT

    fun getResult(result: PointerByReference): WinNT.HRESULT =
        _invokeNativeObject(20, arrayOf(pointer, result), WinNT.HRESULT::class.java) as WinNT.HRESULT
}

private class ShellItem(pointer: Pointer) : Unknown(pointer) {
    fun getDisplayName(displayNameType: Int, result: PointerByReference): WinNT.HRESULT =
        _invokeNativeObject(5, arrayOf(pointer, displayNameType, result), WinNT.HRESULT::class.java) as WinNT.HRESULT
}

internal fun modernFolderDialogOptions(existing: Int): Int =
    existing or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST

internal fun isModernFolderDialogCancelled(hresult: Int): Boolean =
    hresult == ERROR_CANCELLED_HRESULT

internal fun validatedSelectedDirectory(path: String?): File? =
    path?.takeUnless(String::isBlank)?.let(::File)?.takeIf { it.exists() && it.isDirectory }

internal fun modernInitialDirectory(requested: File, home: File): File? =
    requested.takeIf { it.exists() && it.isDirectory }
        ?: File(home, "Downloads").takeIf { it.exists() && it.isDirectory }
        ?: home.takeIf { it.exists() && it.isDirectory }

private fun hresultHex(hresult: WinNT.HRESULT): String =
    "0x${hresult.toInt().toUInt().toString(16).padStart(8, '0')}"

private fun checkHresult(operation: String, hresult: WinNT.HRESULT) {
    if (hresult.toInt() < 0) {
        throw IllegalStateException("$operation failed with HRESULT ${hresultHex(hresult)}")
    }
}

internal fun pickModernWindowsDirectory(
    title: String,
    defaultDirectory: File
): File? {
    val ole32 = Ole32.INSTANCE
    val initialized = ole32.CoInitializeEx(
        null,
        Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE
    )
    checkHresult("CoInitializeEx", initialized)
    try {
        val dialogPointer = PointerByReference()
        val createDialogResult = ole32.CoCreateInstance(
            CLSID_FILE_OPEN_DIALOG,
            null,
            WTypes.CLSCTX_INPROC_SERVER,
            IID_FILE_OPEN_DIALOG,
            dialogPointer
        )
        if (createDialogResult.toInt() < 0) {
            dialogPointer.value?.let { Unknown(it).Release() }
        }
        checkHresult("CoCreateInstance", createDialogResult)
        val dialog = dialogPointer.value
            ?: throw IllegalStateException("CoCreateInstance returned an empty dialog pointer")
        val fileOpenDialog = FileOpenDialog(dialog)
        try {
            val existingOptions = IntByReference()
            checkHresult("IFileOpenDialog.GetOptions", fileOpenDialog.getOptions(existingOptions))
            checkHresult(
                "IFileOpenDialog.SetOptions",
                fileOpenDialog.setOptions(modernFolderDialogOptions(existingOptions.value))
            )
            checkHresult("IFileOpenDialog.SetTitle", fileOpenDialog.setTitle(WString(title)))

            val home = File(System.getProperty("user.home"))
            modernInitialDirectory(defaultDirectory, home)?.let { initialDirectory ->
                val initialFolderPointer = PointerByReference()
                val createFolderResult = Shell32ModernLibrary.instance.SHCreateItemFromParsingName(
                    WString(initialDirectory.absolutePath),
                    null,
                    IID_SHELL_ITEM,
                    initialFolderPointer
                )
                if (createFolderResult.toInt() < 0) {
                    initialFolderPointer.value?.let { Unknown(it).Release() }
                }
                checkHresult("SHCreateItemFromParsingName", createFolderResult)
                val initialFolder = initialFolderPointer.value
                    ?: throw IllegalStateException("SHCreateItemFromParsingName returned an empty folder pointer")
                try {
                    checkHresult("IFileOpenDialog.SetFolder", fileOpenDialog.setFolder(initialFolder))
                } finally {
                    Unknown(initialFolder).Release()
                }
            }

            val showResult = fileOpenDialog.show()
            if (isModernFolderDialogCancelled(showResult.toInt())) return null
            checkHresult("IFileOpenDialog.Show", showResult)

            val resultPointer = PointerByReference()
            val getResult = fileOpenDialog.getResult(resultPointer)
            if (getResult.toInt() < 0) {
                resultPointer.value?.let { Unknown(it).Release() }
            }
            checkHresult("IFileOpenDialog.GetResult", getResult)
            val result = resultPointer.value
                ?: throw IllegalStateException("IFileOpenDialog.GetResult returned an empty result pointer")
            val resultItem = ShellItem(result)
            try {
                val pathPointer = PointerByReference()
                val getDisplayName = resultItem.getDisplayName(SIGDN_FILESYSPATH, pathPointer)
                if (getDisplayName.toInt() < 0) {
                    pathPointer.value?.let(ole32::CoTaskMemFree)
                }
                checkHresult("IShellItem.GetDisplayName", getDisplayName)
                val path = pathPointer.value
                    ?: throw IllegalStateException("IShellItem.GetDisplayName returned an empty path")
                try {
                    val selectedDirectory = validatedSelectedDirectory(path.getWideString(0))
                    return selectedDirectory
                        ?: throw IllegalStateException("Selected folder is not an existing directory")
                } finally {
                    ole32.CoTaskMemFree(path)
                }
            } finally {
                resultItem.Release()
            }
        } finally {
            fileOpenDialog.Release()
        }
    } finally {
        ole32.CoUninitialize()
    }
}
