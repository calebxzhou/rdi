using System.Runtime.InteropServices;
using System.Runtime.InteropServices.Marshalling;

namespace installer;

internal static partial class ShortcutService
{
    private const uint ClsctxInprocServer = 1;
    private const uint CoinitMultithreaded = 0;
    private static readonly Guid ShellLinkClassId = new("00021401-0000-0000-C000-000000000046");
    private static readonly Guid ShellLinkInterfaceId = new("000214F9-0000-0000-C000-000000000046");
    private static readonly ComWrappers ComWrappers = new StrategyBasedComWrappers();

    public static void Create(string shortcutPath, string targetPath, string workingDirectory)
    {
        var initializeResult = CoInitializeEx(0, CoinitMultithreaded);
        Marshal.ThrowExceptionForHR(initializeResult);
        try
        {
            var classId = ShellLinkClassId;
            var interfaceId = ShellLinkInterfaceId;
            Marshal.ThrowExceptionForHR(CoCreateInstance(
                in classId,
                0,
                ClsctxInprocServer,
                in interfaceId,
                out var shellLinkPointer
            ));

            var shellLink = (IShellLinkW)ComWrappers.GetOrCreateObjectForComInstance(
                shellLinkPointer,
                CreateObjectFlags.UniqueInstance
            );
            shellLink.SetPath(targetPath);
            shellLink.SetWorkingDirectory(workingDirectory);
            shellLink.SetDescription("rdi");
            shellLink.SetIconLocation(targetPath, 0);

            if (shellLink is not IPersistFile persistFile)
            {
                throw new InvalidOperationException("Windows Shell不支持保存shortcut");
            }
            Directory.CreateDirectory(Path.GetDirectoryName(shortcutPath)!);
            persistFile.Save(shortcutPath, true);
        }
        finally
        {
            CoUninitialize();
        }
    }

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
}

[GeneratedComInterface(Options = ComInterfaceOptions.ComObjectWrapper)]
[Guid("000214F9-0000-0000-C000-000000000046")]
internal partial interface IShellLinkW
{
    void GetPath(nint file, int maxPath, nint findData, uint flags);
    nint GetIDList();
    void SetIDList(nint idList);
    void GetDescription(nint description, int maxName);
    void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string description);
    void GetWorkingDirectory(nint directory, int maxPath);
    void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string directory);
    void GetArguments(nint arguments, int maxPath);
    void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string arguments);
    ushort GetHotkey();
    void SetHotkey(ushort hotkey);
    int GetShowCommand();
    void SetShowCommand(int showCommand);
    void GetIconLocation(nint iconPath, int pathLength, out int iconIndex);
    void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string iconPath, int iconIndex);
    void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string relativePath, uint reserved);
    void Resolve(nint window, uint flags);
    void SetPath([MarshalAs(UnmanagedType.LPWStr)] string file);
}

[GeneratedComInterface(Options = ComInterfaceOptions.ComObjectWrapper)]
[Guid("0000010C-0000-0000-C000-000000000046")]
internal partial interface IPersist
{
    void GetClassId(out Guid classId);
}

[GeneratedComInterface(Options = ComInterfaceOptions.ComObjectWrapper)]
[Guid("0000010B-0000-0000-C000-000000000046")]
internal partial interface IPersistFile : IPersist
{
    [PreserveSig]
    int IsDirty();

    void Load(nint fileName, uint mode);
    void Save([MarshalAs(UnmanagedType.LPWStr)] string fileName, [MarshalAs(UnmanagedType.Bool)] bool remember);
    void SaveCompleted(nint fileName);
    void GetCurrentFile(out nint fileName);
}
