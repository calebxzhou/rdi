using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;

namespace installer;

internal static partial class Win32InstallerApp
{
    private const string WindowClassName = "RdiInstallerWindow";
    private const uint WindowStyle = 0x00C80000;
    private const uint ChildVisible = 0x50000000;
    private const uint TabStop = 0x00010000;
    private const uint Border = 0x00800000;
    private const uint AutoCheckBox = 0x00000003;
    private const uint EditAutoHorizontalScroll = 0x00000080;
    private const uint ProgressMarquee = 0x00000008;
    private const uint WmCreate = 0x0001;
    private const uint WmDestroy = 0x0002;
    private const uint WmSize = 0x0005;
    private const uint WmClose = 0x0010;
    private const uint WmSetFont = 0x0030;
    private const uint WmCommand = 0x0111;
    private const uint WmUser = 0x0400;
    private const uint WmStatusChanged = 0x8001;
    private const uint WmInstallCompleted = 0x8002;
    private const uint ButtonGetCheck = 0x00F0;
    private const uint ButtonSetCheck = 0x00F1;
    private const uint ProgressSetMarquee = WmUser + 10;
    private const uint BrowseFolderInitialized = 1;
    private const uint BrowseFolderSetSelection = WmUser + 103;
    private const int Checked = 1;
    private const int ShowNormal = 1;
    private const int Hide = 0;
    private const int DefaultGuiFont = 17;
    private const int IdPath = 1001;
    private const int IdBrowse = 1002;
    private const int IdDesktopShortcut = 1003;
    private const int IdStartMenuShortcut = 1004;
    private const int IdInstall = 1005;
    private const uint MessageYesNo = 0x00000004;
    private const uint MessageIconError = 0x00000010;
    private const uint MessageIconWarning = 0x00000030;
    private const uint MessageDefaultButton2 = 0x00000100;

    private static readonly object StateLock = new();
    private static nint _window;
    private static nint _titleLabel;
    private static nint _pathLabel;
    private static nint _pathEdit;
    private static nint _browseButton;
    private static nint _desktopShortcutCheckBox;
    private static nint _startMenuShortcutCheckBox;
    private static nint _progressBar;
    private static nint _statusLabel;
    private static nint _installButton;
    private static string _pendingStatus = "准备安装";
    private static InstallCompletion? _pendingCompletion;
    private static bool _installing;
    private static bool _installationComplete;

    public static unsafe int Run()
    {
        SetProcessDpiAwarenessContext(-4);
        var comInitialized = CoInitializeEx(0, 2) >= 0;
        try
        {
            var controls = new InitCommonControls
            {
                Size = (uint)sizeof(InitCommonControls),
                Classes = 0x00000020,
            };
            InitCommonControlsEx(in controls);

            ExtractIconEx(Environment.ProcessPath ?? string.Empty, 0, out var largeIcon, out var smallIcon, 1);
            var windowClass = new WindowClassEx
            {
                Size = (uint)sizeof(WindowClassEx),
                WindowProcedure = (nint)(delegate* unmanaged[Stdcall]<nint, uint, nuint, nint, nint>)&WindowProcedure,
                Instance = GetModuleHandle(null),
                Icon = largeIcon,
                Cursor = LoadCursor(0, 32512),
                Background = 6,
                SmallIcon = smallIcon,
            };

            fixed (char* className = WindowClassName)
            {
                windowClass.ClassName = (nint)className;
                if (RegisterClassEx(in windowClass) == 0)
                {
                    throw new InvalidOperationException($"无法注册installer window:{Marshal.GetLastPInvokeError()}");
                }
            }

            var bounds = new NativeRect { Right = 800, Bottom = 300 };
            AdjustWindowRectEx(ref bounds, WindowStyle, false, 0);
            var width = bounds.Right - bounds.Left;
            var height = bounds.Bottom - bounds.Top;
            var x = (GetSystemMetrics(0) - width) / 2;
            var y = (GetSystemMetrics(1) - height) / 2;
            _window = CreateWindowEx(
                0,
                WindowClassName,
                "rdi安装程序",
                WindowStyle,
                x,
                y,
                width,
                height,
                0,
                0,
                windowClass.Instance,
                0
            );
            if (_window == 0)
            {
                throw new InvalidOperationException($"无法创建installer window:{Marshal.GetLastPInvokeError()}");
            }

            ShowWindow(_window, ShowNormal);
            UpdateWindow(_window);
            NativeMessage message;
            while (GetMessage(out message, 0, 0, 0) > 0)
            {
                TranslateMessage(in message);
                DispatchMessage(in message);
            }
            return (int)message.WParam;
        }
        catch (Exception exception)
        {
            MessageBox(0, exception.Message, "rdi安装程序", MessageIconError);
            return 1;
        }
        finally
        {
            if (comInitialized) CoUninitialize();
        }
    }

    [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
    private static nint WindowProcedure(nint window, uint message, nuint wParam, nint lParam)
    {
        try
        {
            return message switch
            {
                WmCreate => CreateControls(window),
                WmSize => LayoutControls(window),
                WmCommand => HandleCommand(window, (int)(wParam & 0xFFFF)),
                WmStatusChanged => ApplyPendingStatus(),
                WmInstallCompleted => ApplyInstallCompletion(window),
                WmClose => CloseWindow(window),
                WmDestroy => Quit(),
                _ => DefWindowProc(window, message, wParam, lParam),
            };
        }
        catch (Exception exception)
        {
            MessageBox(window, exception.Message, "rdi安装程序", MessageIconError);
            return 0;
        }
    }

    private static nint CreateControls(nint window)
    {
        var instance = GetModuleHandle(null);
        var font = GetStockObject(DefaultGuiFont);
        CreateLabel(window, "安装rdi客户端", instance, font, out _titleLabel);
        CreateLabel(window, "安装路径", instance, font, out _pathLabel);
        _pathEdit = CreateWindowEx(Border, "EDIT", "", ChildVisible | TabStop | EditAutoHorizontalScroll, 0, 0, 0, 0, window, IdPath, instance, 0);
        _browseButton = CreateWindowEx(0, "BUTTON", "浏览…", ChildVisible | TabStop, 0, 0, 0, 0, window, IdBrowse, instance, 0);
        _desktopShortcutCheckBox = CreateWindowEx(0, "BUTTON", "创建桌面图标", ChildVisible | TabStop | AutoCheckBox, 0, 0, 0, 0, window, IdDesktopShortcut, instance, 0);
        _startMenuShortcutCheckBox = CreateWindowEx(0, "BUTTON", "创建开始菜单图标", ChildVisible | TabStop | AutoCheckBox, 0, 0, 0, 0, window, IdStartMenuShortcut, instance, 0);
        _progressBar = CreateWindowEx(0, "msctls_progress32", "", ChildVisible | ProgressMarquee, 0, 0, 0, 0, window, 0, instance, 0);
        _statusLabel = CreateWindowEx(0, "STATIC", "准备安装", ChildVisible | 0x00000200, 0, 0, 0, 0, window, 0, instance, 0);
        _installButton = CreateWindowEx(0, "BUTTON", "开始安装", ChildVisible | TabStop, 0, 0, 0, 0, window, IdInstall, instance, 0);

        foreach (var control in new[]
                 {
                     _titleLabel,
                     _pathLabel,
                     _pathEdit,
                     _browseButton,
                     _desktopShortcutCheckBox,
                     _startMenuShortcutCheckBox,
                     _statusLabel,
                     _installButton,
                 })
        {
            SendMessage(control, WmSetFont, (nuint)font, 1);
        }
        SendMessage(_desktopShortcutCheckBox, ButtonSetCheck, Checked, 0);
        SendMessage(_startMenuShortcutCheckBox, ButtonSetCheck, Checked, 0);
        SendMessage(_progressBar, ProgressSetMarquee, 1, 28);
        ShowWindow(_progressBar, Hide);
        SetWindowText(_pathEdit, InstallPathSelector.SelectDefaultPath());
        return 0;
    }

    private static void CreateLabel(nint parent, string text, nint instance, nint font, out nint label)
    {
        label = CreateWindowEx(0, "STATIC", text, ChildVisible, 0, 0, 0, 0, parent, 0, instance, 0);
        SendMessage(label, WmSetFont, (nuint)font, 1);
    }

    private static nint LayoutControls(nint window)
    {
        GetClientRect(window, out var client);
        var contentWidth = Math.Max(300, client.Right - 56);
        MoveWindow(_titleLabel, 28, 20, contentWidth, 30, true);
        MoveWindow(_pathLabel, 28, 61, contentWidth, 22, true);
        MoveWindow(_pathEdit, 28, 87, contentWidth - 100, 27, true);
        MoveWindow(_browseButton, 28 + contentWidth - 88, 86, 88, 29, true);
        MoveWindow(_desktopShortcutCheckBox, 28, 132, 170, 24, true);
        MoveWindow(_startMenuShortcutCheckBox, 220, 132, 190, 24, true);
        MoveWindow(_progressBar, 28, 174, contentWidth, 7, true);
        MoveWindow(_statusLabel, 28, 201, contentWidth - 112, 36, true);
        MoveWindow(_installButton, 28 + contentWidth - 100, 201, 100, 36, true);
        return 0;
    }

    private static nint HandleCommand(nint window, int controlId)
    {
        switch (controlId)
        {
            case IdBrowse:
                BrowseForInstallPath(window);
                break;
            case IdInstall:
                if (_installationComplete) DestroyWindow(window);
                else if (!_installing) StartInstall(window);
                break;
        }
        return 0;
    }

    private static unsafe void BrowseForInstallPath(nint window)
    {
        var initialPath = Marshal.StringToHGlobalUni(GetWindowText(_pathEdit));
        var title = Marshal.StringToHGlobalUni("选择rdi安装目录");
        try
        {
            var displayName = stackalloc char[260];
            var browseInfo = new BrowseInfo
            {
                Owner = window,
                DisplayName = (nint)displayName,
                Title = title,
                Flags = 0x00000041,
                Callback = (nint)(delegate* unmanaged[Stdcall]<nint, uint, nint, nint, int>)&BrowseCallback,
                Parameter = initialPath,
            };
            var itemIdList = SHBrowseForFolder(in browseInfo);
            if (itemIdList == 0) return;
            try
            {
                var path = stackalloc char[32768];
                if (SHGetPathFromIDListEx(itemIdList, path, 32768, 0))
                {
                    SetWindowText(_pathEdit, new string(path));
                }
            }
            finally
            {
                CoTaskMemFree(itemIdList);
            }
        }
        finally
        {
            Marshal.FreeHGlobal(initialPath);
            Marshal.FreeHGlobal(title);
        }
    }

    [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
    private static int BrowseCallback(nint dialog, uint message, nint lParam, nint data)
    {
        if (message == BrowseFolderInitialized) SendMessage(dialog, BrowseFolderSetSelection, 1, data);
        return 0;
    }

    private static void StartInstall(nint window)
    {
        string installPath;
        try
        {
            var input = GetWindowText(_pathEdit).Trim();
            if (input.Length == 0) throw new ArgumentException("请选择安装路径");
            installPath = Path.GetFullPath(input);
            if (Directory.Exists(installPath) && Directory.EnumerateFileSystemEntries(installPath).Any())
            {
                var answer = MessageBox(
                    window,
                    "目标目录已包含文件。继续安装会覆盖同名文件，但保留其他文件。是否继续？",
                    "确认覆盖",
                    MessageYesNo | MessageIconWarning | MessageDefaultButton2
                );
                if (answer != 6) return;
            }
        }
        catch (Exception exception)
        {
            ShowError(window, "安装路径无效", exception);
            return;
        }

        SetInstalling(true);
        var desktopShortcut = SendMessage(_desktopShortcutCheckBox, ButtonGetCheck, 0, 0) == Checked;
        var startMenuShortcut = SendMessage(_startMenuShortcutCheckBox, ButtonGetCheck, 0, 0) == Checked;
        _ = CompleteInstallAsync(installPath, desktopShortcut, startMenuShortcut);
    }

    private static async Task CompleteInstallAsync(string installPath, bool desktopShortcut, bool startMenuShortcut)
    {
        InstallCompletion completion;
        try
        {
            var progress = new NativeProgress();
            var result = await InstallerService.InstallAsync(installPath, desktopShortcut, startMenuShortcut, progress);
            completion = new InstallCompletion(result, null);
        }
        catch (Exception exception)
        {
            completion = new InstallCompletion(null, exception);
        }

        lock (StateLock) _pendingCompletion = completion;
        PostMessage(_window, WmInstallCompleted, 0, 0);
    }

    private static void SetInstalling(bool installing)
    {
        _installing = installing;
        EnableWindow(_pathEdit, !installing);
        EnableWindow(_browseButton, !installing);
        EnableWindow(_desktopShortcutCheckBox, !installing);
        EnableWindow(_startMenuShortcutCheckBox, !installing);
        EnableWindow(_installButton, !installing);
        ShowWindow(_progressBar, installing ? ShowNormal : Hide);
        SetStatus(installing ? "正在准备安装" : "准备安装");
    }

    private static nint ApplyPendingStatus()
    {
        lock (StateLock) SetWindowText(_statusLabel, _pendingStatus);
        return 0;
    }

    private static nint ApplyInstallCompletion(nint window)
    {
        InstallCompletion? completion;
        lock (StateLock)
        {
            completion = _pendingCompletion;
            _pendingCompletion = null;
        }
        if (completion is null) return 0;

        _installing = false;
        ShowWindow(_progressBar, Hide);
        if (completion.Error is not null)
        {
            SetInstalling(false);
            SetStatus("安装失败");
            ShowError(window, "安装失败", completion.Error);
            return 0;
        }

        _installationComplete = true;
        SetStatus("安装完成");
        EnableWindow(_installButton, true);
        SetWindowText(_installButton, "关闭");
        if (completion.Result!.Warnings.Count > 0)
        {
            MessageBox(window, string.Join(Environment.NewLine, completion.Result.Warnings), "安装完成，但存在warning", MessageIconWarning);
        }
        return 0;
    }

    private static void SetStatus(string status)
    {
        lock (StateLock) _pendingStatus = status;
        PostMessage(_window, WmStatusChanged, 0, 0);
    }

    private static nint CloseWindow(nint window)
    {
        if (_installing)
        {
            MessageBeep(MessageIconWarning);
            return 0;
        }
        DestroyWindow(window);
        return 0;
    }

    private static nint Quit()
    {
        PostQuitMessage(0);
        return 0;
    }

    private static void ShowError(nint window, string title, Exception exception) =>
        MessageBox(window, $"{title}:{Environment.NewLine}{exception.Message}", title, MessageIconError);

    private static unsafe string GetWindowText(nint window)
    {
        var length = GetWindowTextLength(window);
        var buffer = stackalloc char[length + 1];
        var copied = GetWindowText(window, buffer, length + 1);
        return new string(buffer, 0, copied);
    }

    private sealed class NativeProgress : IProgress<string>
    {
        public void Report(string value) => SetStatus(value);
    }

    private sealed record InstallCompletion(InstallResult? Result, Exception? Error);

    [StructLayout(LayoutKind.Sequential)]
    private struct WindowClassEx
    {
        public uint Size;
        public uint Style;
        public nint WindowProcedure;
        public int ClassExtra;
        public int WindowExtra;
        public nint Instance;
        public nint Icon;
        public nint Cursor;
        public nint Background;
        public nint MenuName;
        public nint ClassName;
        public nint SmallIcon;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeMessage
    {
        public nint Window;
        public uint Message;
        public nuint WParam;
        public nint LParam;
        public uint Time;
        public int X;
        public int Y;
        public uint Private;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeRect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct InitCommonControls
    {
        public uint Size;
        public uint Classes;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct BrowseInfo
    {
        public nint Owner;
        public nint Root;
        public nint DisplayName;
        public nint Title;
        public uint Flags;
        public nint Callback;
        public nint Parameter;
        public int Image;
    }

    [LibraryImport("user32.dll", EntryPoint = "RegisterClassExW", SetLastError = true)]
    private static partial ushort RegisterClassEx(in WindowClassEx windowClass);

    [LibraryImport("user32.dll", EntryPoint = "CreateWindowExW", SetLastError = true, StringMarshalling = StringMarshalling.Utf16)]
    private static partial nint CreateWindowEx(uint extendedStyle, string className, string windowName, uint style, int x, int y, int width, int height, nint parent, nint menu, nint instance, nint parameter);

    [LibraryImport("user32.dll", EntryPoint = "DefWindowProcW")]
    private static partial nint DefWindowProc(nint window, uint message, nuint wParam, nint lParam);

    [LibraryImport("user32.dll", EntryPoint = "GetMessageW", SetLastError = true)]
    private static partial int GetMessage(out NativeMessage message, nint window, uint minimumMessage, uint maximumMessage);

    [LibraryImport("user32.dll", EntryPoint = "TranslateMessage")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool TranslateMessage(in NativeMessage message);

    [LibraryImport("user32.dll", EntryPoint = "DispatchMessageW")]
    private static partial nint DispatchMessage(in NativeMessage message);

    [LibraryImport("user32.dll", EntryPoint = "ShowWindow")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool ShowWindow(nint window, int command);

    [LibraryImport("user32.dll", EntryPoint = "UpdateWindow")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool UpdateWindow(nint window);

    [LibraryImport("user32.dll", EntryPoint = "DestroyWindow")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool DestroyWindow(nint window);

    [LibraryImport("user32.dll", EntryPoint = "PostMessageW")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool PostMessage(nint window, uint message, nuint wParam, nint lParam);

    [LibraryImport("user32.dll", EntryPoint = "PostQuitMessage")]
    private static partial void PostQuitMessage(int exitCode);

    [LibraryImport("user32.dll", EntryPoint = "SendMessageW")]
    private static partial nint SendMessage(nint window, uint message, nuint wParam, nint lParam);

    [LibraryImport("user32.dll", EntryPoint = "SetWindowTextW", StringMarshalling = StringMarshalling.Utf16)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetWindowText(nint window, string text);

    [LibraryImport("user32.dll", EntryPoint = "GetWindowTextLengthW")]
    private static partial int GetWindowTextLength(nint window);

    [LibraryImport("user32.dll", EntryPoint = "GetWindowTextW")]
    private static unsafe partial int GetWindowText(nint window, char* text, int maxCount);

    [LibraryImport("user32.dll", EntryPoint = "EnableWindow")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool EnableWindow(nint window, [MarshalAs(UnmanagedType.Bool)] bool enable);

    [LibraryImport("user32.dll", EntryPoint = "MoveWindow")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool MoveWindow(nint window, int x, int y, int width, int height, [MarshalAs(UnmanagedType.Bool)] bool repaint);

    [LibraryImport("user32.dll", EntryPoint = "GetClientRect")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool GetClientRect(nint window, out NativeRect rectangle);

    [LibraryImport("user32.dll", EntryPoint = "AdjustWindowRectEx")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool AdjustWindowRectEx(ref NativeRect rectangle, uint style, [MarshalAs(UnmanagedType.Bool)] bool menu, uint extendedStyle);

    [LibraryImport("user32.dll", EntryPoint = "MessageBoxW", StringMarshalling = StringMarshalling.Utf16)]
    private static partial int MessageBox(nint window, string text, string caption, uint type);

    [LibraryImport("user32.dll", EntryPoint = "MessageBeep")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool MessageBeep(uint type);

    [LibraryImport("user32.dll", EntryPoint = "LoadCursorW")]
    private static partial nint LoadCursor(nint instance, nint cursorName);

    [LibraryImport("user32.dll", EntryPoint = "GetSystemMetrics")]
    private static partial int GetSystemMetrics(int index);

    [LibraryImport("user32.dll", EntryPoint = "SetProcessDpiAwarenessContext")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetProcessDpiAwarenessContext(nint context);

    [LibraryImport("kernel32.dll", EntryPoint = "GetModuleHandleW", StringMarshalling = StringMarshalling.Utf16)]
    private static partial nint GetModuleHandle(string? moduleName);

    [LibraryImport("gdi32.dll", EntryPoint = "GetStockObject")]
    private static partial nint GetStockObject(int objectId);

    [LibraryImport("comctl32.dll", EntryPoint = "InitCommonControlsEx")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool InitCommonControlsEx(in InitCommonControls controls);

    [LibraryImport("shell32.dll", EntryPoint = "ExtractIconExW", StringMarshalling = StringMarshalling.Utf16)]
    private static partial uint ExtractIconEx(string file, int iconIndex, out nint largeIcon, out nint smallIcon, uint iconCount);

    [LibraryImport("shell32.dll", EntryPoint = "SHBrowseForFolderW")]
    private static partial nint SHBrowseForFolder(in BrowseInfo browseInfo);

    [LibraryImport("shell32.dll", EntryPoint = "SHGetPathFromIDListEx")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static unsafe partial bool SHGetPathFromIDListEx(nint itemIdList, char* path, uint pathLength, uint flags);

    [LibraryImport("ole32.dll", EntryPoint = "CoTaskMemFree")]
    private static partial void CoTaskMemFree(nint memory);

    [LibraryImport("ole32.dll", EntryPoint = "CoInitializeEx")]
    private static partial int CoInitializeEx(nint reserved, uint coInit);

    [LibraryImport("ole32.dll", EntryPoint = "CoUninitialize")]
    private static partial void CoUninitialize();
}
