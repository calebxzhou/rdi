using System.Runtime.InteropServices;

namespace installer;

internal static unsafe partial class FolderPicker
{
    private const uint CoinitApartmentThreaded = 2;
    private const uint ClsctxInprocServer = 1;
    private const uint FosPickFolders = 0x20;
    private const uint FosForceFileSystem = 0x40;
    private const uint FosPathMustExist = 0x800;
    private const uint SigdnFileSystemPath = 0x80058000;
    private const uint WsExTopmost = 0x00000008;
    private const uint WsExToolWindow = 0x00000080;
    private const uint WsPopup = 0x80000000;
    private const int Cancelled = unchecked((int)0x800704C7);
    private static readonly Guid FileOpenDialogClassId = new("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7");
    private static readonly Guid FileOpenDialogInterfaceId = new("D57C7288-D4AD-4768-BE02-9D969532D960");
    private static readonly Guid ShellItemInterfaceId = new("43826D1E-E718-42EE-BC55-A1E261C37BFE");

    public static string? Select(string defaultPath)
    {
        Marshal.ThrowExceptionForHR(CoInitializeEx(0, CoinitApartmentThreaded));
        try
        {
            var classId = FileOpenDialogClassId;
            var interfaceId = FileOpenDialogInterfaceId;
            Marshal.ThrowExceptionForHR(CoCreateInstance(
                in classId,
                0,
                ClsctxInprocServer,
                in interfaceId,
                out var dialog
            ));

            try
            {
                Configure(dialog, defaultPath);
                var owner = CreateTopmostOwner();
                try
                {
                    var showResult = InvokeShow(dialog, owner);
                    if (showResult == Cancelled) return null;
                    Marshal.ThrowExceptionForHR(showResult);
                }
                finally
                {
                    DestroyWindow(owner);
                }

                Marshal.ThrowExceptionForHR(InvokeGetResult(dialog, out var shellItem));
                try
                {
                    Marshal.ThrowExceptionForHR(InvokeGetDisplayName(shellItem, out var path));
                    try
                    {
                        return Marshal.PtrToStringUni(path);
                    }
                    finally
                    {
                        CoTaskMemFree(path);
                    }
                }
                finally
                {
                    Release(shellItem);
                }
            }
            finally
            {
                Release(dialog);
            }
        }
        finally
        {
            CoUninitialize();
        }
    }

    private static void Configure(nint dialog, string defaultPath)
    {
        Marshal.ThrowExceptionForHR(InvokeGetOptions(dialog, out var options));
        Marshal.ThrowExceptionForHR(InvokeSetOptions(
            dialog,
            options | FosPickFolders | FosForceFileSystem | FosPathMustExist
        ));

        fixed (char* title = "选择rdi安装文件夹")
        {
            Marshal.ThrowExceptionForHR(InvokeSetTitle(dialog, title));
        }

        var existingPath = FindExistingDirectory(defaultPath);
        var shellItemInterfaceId = ShellItemInterfaceId;
        Marshal.ThrowExceptionForHR(SHCreateItemFromParsingName(
            existingPath,
            0,
            in shellItemInterfaceId,
            out var defaultFolder
        ));
        try
        {
            Marshal.ThrowExceptionForHR(InvokeSetDefaultFolder(dialog, defaultFolder));
        }
        finally
        {
            Release(defaultFolder);
        }
    }

    private static string FindExistingDirectory(string path)
    {
        var directory = new DirectoryInfo(path);
        while (!directory.Exists && directory.Parent is not null) directory = directory.Parent;
        return directory.FullName;
    }

    private static nint CreateTopmostOwner()
    {
        var owner = CreateWindowEx(
            WsExTopmost | WsExToolWindow,
            "STATIC",
            "rdi installer folder picker",
            WsPopup,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
        if (owner == 0)
        {
            throw new InvalidOperationException($"无法创建folder picker owner:{Marshal.GetLastPInvokeError()}");
        }
        return owner;
    }

    private static nint* Vtable(nint instance) => *(nint**)instance;

    private static int InvokeShow(nint dialog, nint owner) =>
        ((delegate* unmanaged[Stdcall]<nint, nint, int>)Vtable(dialog)[3])(dialog, owner);

    private static int InvokeSetOptions(nint dialog, uint options) =>
        ((delegate* unmanaged[Stdcall]<nint, uint, int>)Vtable(dialog)[9])(dialog, options);

    private static int InvokeGetOptions(nint dialog, out uint options)
    {
        fixed (uint* output = &options)
        {
            return ((delegate* unmanaged[Stdcall]<nint, uint*, int>)Vtable(dialog)[10])(dialog, output);
        }
    }

    private static int InvokeSetDefaultFolder(nint dialog, nint folder) =>
        ((delegate* unmanaged[Stdcall]<nint, nint, int>)Vtable(dialog)[11])(dialog, folder);

    private static int InvokeSetTitle(nint dialog, char* title) =>
        ((delegate* unmanaged[Stdcall]<nint, char*, int>)Vtable(dialog)[17])(dialog, title);

    private static int InvokeGetResult(nint dialog, out nint result)
    {
        fixed (nint* output = &result)
        {
            return ((delegate* unmanaged[Stdcall]<nint, nint*, int>)Vtable(dialog)[20])(dialog, output);
        }
    }

    private static int InvokeGetDisplayName(nint shellItem, out nint path)
    {
        fixed (nint* output = &path)
        {
            return ((delegate* unmanaged[Stdcall]<nint, uint, nint*, int>)Vtable(shellItem)[5])(
                shellItem,
                SigdnFileSystemPath,
                output
            );
        }
    }

    private static void Release(nint instance) =>
        ((delegate* unmanaged[Stdcall]<nint, uint>)Vtable(instance)[2])(instance);

    [LibraryImport("ole32.dll")]
    private static partial int CoInitializeEx(nint reserved, uint coInit);

    [LibraryImport("ole32.dll")]
    private static partial void CoUninitialize();

    [LibraryImport("ole32.dll")]
    private static partial int CoCreateInstance(
        in Guid classId,
        nint outer,
        uint context,
        in Guid interfaceId,
        out nint instance
    );

    [LibraryImport("ole32.dll")]
    private static partial void CoTaskMemFree(nint memory);

    [LibraryImport("shell32.dll", EntryPoint = "SHCreateItemFromParsingName", StringMarshalling = StringMarshalling.Utf16)]
    private static partial int SHCreateItemFromParsingName(
        string path,
        nint bindingContext,
        in Guid interfaceId,
        out nint shellItem
    );

    [LibraryImport("user32.dll", EntryPoint = "CreateWindowExW", SetLastError = true, StringMarshalling = StringMarshalling.Utf16)]
    private static partial nint CreateWindowEx(
        uint extendedStyle,
        string className,
        string windowName,
        uint style,
        int x,
        int y,
        int width,
        int height,
        nint parent,
        nint menu,
        nint instance,
        nint parameter
    );

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool DestroyWindow(nint window);
}
