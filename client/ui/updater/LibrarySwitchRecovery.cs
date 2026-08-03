using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

internal enum UiLibraryUpdateResult
{
    UpToDate,
    Updated,
    LocalVersionAvailable,
    LaunchAborted
}

internal enum LibrarySwitchResult
{
    Switched,
    LaunchAborted
}

internal enum LibraryRecoveryPromptKind
{
    ForceCloseOccupiers,
    ElevateSwitch
}

internal sealed record LibraryRecoveryPrompt(
    LibraryRecoveryPromptKind Kind,
    IReadOnlyList<LibraryOccupier> Occupiers);

internal sealed record LibraryOccupier(
    int ProcessId,
    string Name,
    DateTime StartTimeUtc,
    bool IsCritical);

internal static class LibrarySwitchRecovery
{
    internal const string ElevatedKillArgument = "--elevated-kill-occupiers";
    internal const string ElevatedSwitchArgument = "--elevated-switch-lib";
    private const int ErrorAccessDenied = 5;
    private const int ErrorSharingViolation = 32;
    private const int ErrorLockViolation = 33;
    private const int UserCancelledElevation = 1223;

    internal static Task<LibrarySwitchResult> SwitchAsync(
        string libDirectory,
        string stagingDirectory,
        string backupDirectory,
        Action<string> writeInfo,
        Action<string> writeWarning,
        Func<LibraryRecoveryPrompt, bool>? prompt)
    {
        try
        {
            SwitchLibraryDirectory(libDirectory, stagingDirectory, backupDirectory);
            return Task.FromResult(LibrarySwitchResult.Switched);
        }
        catch (Exception exception) when (IsAccessDenied(exception))
        {
            return Task.FromResult(RecoverAccessDenied(
                libDirectory,
                stagingDirectory,
                backupDirectory,
                writeInfo,
                writeWarning,
                prompt));
        }
    }

    internal static void SwitchLibraryDirectory(
        string libDirectory,
        string stagingDirectory,
        string backupDirectory)
    {
        if (Directory.Exists(libDirectory))
            Directory.Move(libDirectory, backupDirectory);
        try
        {
            Directory.Move(stagingDirectory, libDirectory);
        }
        catch
        {
            if (Directory.Exists(backupDirectory) && !Directory.Exists(libDirectory))
                Directory.Move(backupDirectory, libDirectory);
            throw;
        }
    }

    internal static int RunElevatedKill(
        string launcherRoot,
        IReadOnlyList<string> targetArguments)
    {
        if (targetArguments.Count == 0 || targetArguments.Count % 2 != 0)
            return 2;

        List<ProcessIdentity> targets = [];
        for (var index = 0; index < targetArguments.Count; index += 2)
        {
            if (!int.TryParse(targetArguments[index], out var processId) ||
                !long.TryParse(targetArguments[index + 1], out var startTimeTicks) ||
                processId <= 4 || startTimeTicks <= 0)
                return 2;
            targets.Add(new(processId, startTimeTicks));
        }

        var libDirectory = Path.Combine(launcherRoot, "lib");
        if (!TryFindOccupiers(libDirectory, out var occupiers, out _))
            return 1;

        foreach (var target in targets)
        {
            var occupier = occupiers.FirstOrDefault(it =>
                it.ProcessId == target.ProcessId &&
                it.StartTimeUtc.Ticks == target.StartTimeTicks);
            if (occupier is null)
                continue;
            if (!CanTerminate(occupier) || !TryTerminateProcess(occupier))
                return 1;
        }

        if (!TryFindOccupiers(libDirectory, out occupiers, out _))
            return 1;
        return targets.Any(target => occupiers.Any(it =>
            it.ProcessId == target.ProcessId &&
            it.StartTimeUtc.Ticks == target.StartTimeTicks))
            ? 1
            : 0;
    }

    internal static int RunElevatedSwitch(string launcherRoot, string updateId)
    {
        if (!Guid.TryParseExact(updateId, "N", out _))
            return 2;

        var libDirectory = Path.Combine(launcherRoot, "lib");
        var stagingDirectory = Path.Combine(launcherRoot, $"lib.updating-{updateId}");
        var backupDirectory = Path.Combine(launcherRoot, $"lib.previous-{updateId}");
        if (!Directory.Exists(stagingDirectory))
            return 2;

        try
        {
            SwitchLibraryDirectory(libDirectory, stagingDirectory, backupDirectory);
            return 0;
        }
        catch
        {
            return 1;
        }
    }

    private static LibrarySwitchResult RecoverAccessDenied(
        string libDirectory,
        string stagingDirectory,
        string backupDirectory,
        Action<string> writeInfo,
        Action<string> writeWarning,
        Func<LibraryRecoveryPrompt, bool>? prompt)
    {
        if (!TryFindOccupiers(libDirectory, out var occupiers, out var inspectionError))
        {
            writeWarning($"无法确定占用UI库的程序: {inspectionError}");
            return LibrarySwitchResult.LaunchAborted;
        }

        while (occupiers.Count > 0)
        {
            var protectedOccupiers = occupiers.Where(it => !CanTerminate(it)).ToArray();
            if (protectedOccupiers.Length > 0)
            {
                writeWarning($"无法安全终止占用UI库的系统进程: {FormatOccupiers(protectedOccupiers)}");
                return LibrarySwitchResult.LaunchAborted;
            }

            writeWarning($"以下程序正在使用UI库: {FormatOccupiers(occupiers)}");
            if (!Confirm(prompt, LibraryRecoveryPromptKind.ForceCloseOccupiers, occupiers))
                return LibrarySwitchResult.LaunchAborted;

            writeInfo("正在请求管理员权限关闭占用程序");
            if (!TryRunElevatedKill(occupiers))
            {
                writeWarning("占用UI库的程序无法关闭，本次不再启动客户端");
                return LibrarySwitchResult.LaunchAborted;
            }

            if (TrySwitch(libDirectory, stagingDirectory, backupDirectory))
                return LibrarySwitchResult.Switched;

            if (!TryFindOccupiers(libDirectory, out occupiers, out inspectionError))
            {
                writeWarning($"关闭占用程序后仍无法检查UI库占用: {inspectionError}");
                return LibrarySwitchResult.LaunchAborted;
            }
        }

        writeWarning("没有找到文件占用进程，可能是RDI目录权限不足");
        if (!Confirm(prompt, LibraryRecoveryPromptKind.ElevateSwitch, []))
            return LibrarySwitchResult.LaunchAborted;

        writeInfo("正在请求管理员权限完成UI库切换");
        return TryRunElevatedSwitch(stagingDirectory)
            ? LibrarySwitchResult.Switched
            : LibrarySwitchResult.LaunchAborted;
    }

    private static bool TrySwitch(
        string libDirectory,
        string stagingDirectory,
        string backupDirectory)
    {
        try
        {
            SwitchLibraryDirectory(libDirectory, stagingDirectory, backupDirectory);
            return true;
        }
        catch (Exception exception) when (IsAccessDenied(exception))
        {
            return false;
        }
    }

    private static bool Confirm(
        Func<LibraryRecoveryPrompt, bool>? prompt,
        LibraryRecoveryPromptKind kind,
        IReadOnlyList<LibraryOccupier> occupiers) =>
        prompt?.Invoke(new(kind, occupiers)) == true;

    private static bool CanTerminate(LibraryOccupier occupier) =>
        occupier.ProcessId > 4 &&
        occupier.ProcessId != Environment.ProcessId &&
        occupier.StartTimeUtc != DateTime.MinValue &&
        !occupier.IsCritical;

    private static bool TryTerminateProcess(LibraryOccupier occupier)
    {
        try
        {
            using var process = Process.GetProcessById(occupier.ProcessId);
            if (process.HasExited)
                return true;
            if (process.StartTime.ToUniversalTime().Ticks != occupier.StartTimeUtc.Ticks)
                return false;
            process.Kill();
            return process.WaitForExit(10_000) && process.HasExited;
        }
        catch
        {
            return false;
        }
    }

    private static bool TryRunElevatedKill(IReadOnlyList<LibraryOccupier> occupiers)
    {
        List<string> arguments = [ElevatedKillArgument];
        foreach (var occupier in occupiers)
        {
            arguments.Add(occupier.ProcessId.ToString());
            arguments.Add(occupier.StartTimeUtc.Ticks.ToString());
        }
        return TryRunElevated(arguments);
    }

    private static bool TryRunElevatedSwitch(string stagingDirectory)
    {
        var updateId = Path.GetFileName(stagingDirectory)["lib.updating-".Length..];
        return Guid.TryParseExact(updateId, "N", out _) &&
               TryRunElevated([ElevatedSwitchArgument, updateId]);
    }

    private static bool TryRunElevated(IReadOnlyList<string> arguments)
    {
        var executable = Environment.ProcessPath;
        if (string.IsNullOrWhiteSpace(executable))
            return false;

        try
        {
            var startInfo = new ProcessStartInfo
            {
                FileName = executable,
                Verb = "runas",
                UseShellExecute = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            foreach (var argument in arguments)
                startInfo.ArgumentList.Add(argument);
            using var process = Process.Start(startInfo);
            if (process is null)
                return false;
            process.WaitForExit();
            return process.ExitCode == 0;
        }
        catch (Win32Exception exception) when (exception.NativeErrorCode == UserCancelledElevation)
        {
            return false;
        }
        catch
        {
            return false;
        }
    }

    private static bool IsAccessDenied(Exception exception) =>
        exception is UnauthorizedAccessException ||
        exception is IOException &&
        (exception.HResult & 0xFFFF) is ErrorAccessDenied or ErrorSharingViolation or ErrorLockViolation;

    private static bool TryFindOccupiers(
        string libDirectory,
        out IReadOnlyList<LibraryOccupier> occupiers,
        out string? error)
    {
        occupiers = [];
        error = null;
        try
        {
            var files = Directory.Exists(libDirectory)
                ? Directory.EnumerateFiles(libDirectory, "*", SearchOption.AllDirectories).ToArray()
                : [];
            return WindowsFileLocks.TryFind(files, out occupiers, out error);
        }
        catch (Exception exception)
        {
            error = exception.Message;
            return false;
        }
    }

    private static string FormatOccupiers(IEnumerable<LibraryOccupier> occupiers) =>
        string.Join("、", occupiers.Select(it => $"{it.Name}(PID{it.ProcessId})"));

    private readonly record struct ProcessIdentity(int ProcessId, long StartTimeTicks);
}

internal static class WindowsFileLocks
{
    private const int ErrorMoreData = 234;

    internal static bool TryFind(
        string[] files,
        out IReadOnlyList<LibraryOccupier> occupiers,
        out string? error)
    {
        occupiers = [];
        error = null;
        if (files.Length == 0)
            return true;

        var sessionKey = new StringBuilder(64);
        var status = RmStartSession(out var sessionHandle, 0, sessionKey);
        if (status != 0)
        {
            error = $"Restart Manager启动失败，错误码{status}";
            return false;
        }

        try
        {
            status = RmRegisterResources(
                sessionHandle,
                (uint)files.Length,
                files,
                0,
                nint.Zero,
                0,
                nint.Zero);
            if (status != 0)
            {
                error = $"注册被占用文件失败，错误码{status}";
                return false;
            }

            uint processCount = 0;
            uint processNeeded;
            status = RmGetList(sessionHandle, out processNeeded, ref processCount, nint.Zero, out _);
            if (status != 0 && status != ErrorMoreData)
            {
                error = $"读取文件占用进程失败，错误码{status}";
                return false;
            }
            if (processNeeded == 0)
                return true;

            var processSize = Marshal.SizeOf<RM_PROCESS_INFO>();
            var processBuffer = Marshal.AllocHGlobal(processSize * (int)processNeeded);
            try
            {
                processCount = processNeeded;
                status = RmGetList(sessionHandle, out _, ref processCount, processBuffer, out _);
                if (status != 0)
                {
                    error = $"读取文件占用进程失败，错误码{status}";
                    return false;
                }

                occupiers = Enumerable.Range(0, (int)processCount)
                    .Select(index => Marshal.PtrToStructure<RM_PROCESS_INFO>(IntPtr.Add(processBuffer, index * processSize)))
                    .Select(info => new LibraryOccupier(
                        info.Process.dwProcessId,
                        info.strAppName,
                        ToDateTimeUtc(info.Process.ProcessStartTime),
                        info.ApplicationType == RM_APP_TYPE.Critical))
                    .DistinctBy(it => it.ProcessId)
                    .ToArray();
                return true;
            }
            finally
            {
                Marshal.FreeHGlobal(processBuffer);
            }
        }
        finally
        {
            RmEndSession(sessionHandle);
        }
    }

    private static DateTime ToDateTimeUtc(System.Runtime.InteropServices.ComTypes.FILETIME fileTime)
    {
        var ticks = ((long)fileTime.dwHighDateTime << 32) | (uint)fileTime.dwLowDateTime;
        return ticks > 0
            ? DateTime.FromFileTimeUtc(ticks)
            : DateTime.MinValue;
    }

    [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
    private static extern int RmStartSession(
        out uint sessionHandle,
        int sessionFlags,
        StringBuilder sessionKey);

    [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
    private static extern int RmRegisterResources(
        uint sessionHandle,
        uint fileCount,
        [In, MarshalAs(UnmanagedType.LPArray, ArraySubType = UnmanagedType.LPWStr)] string[] files,
        uint applicationCount,
        nint applications,
        uint serviceCount,
        nint services);

    [DllImport("rstrtmgr.dll")]
    private static extern int RmGetList(
        uint sessionHandle,
        out uint processInfoNeeded,
        ref uint processInfoCount,
        nint processInfo,
        out uint rebootReasons);

    [DllImport("rstrtmgr.dll")]
    private static extern int RmEndSession(uint sessionHandle);

    [StructLayout(LayoutKind.Sequential)]
    private struct RM_UNIQUE_PROCESS
    {
        internal int dwProcessId;
        internal System.Runtime.InteropServices.ComTypes.FILETIME ProcessStartTime;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct RM_PROCESS_INFO
    {
        internal RM_UNIQUE_PROCESS Process;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 257)]
        internal string strAppName;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 65)]
        internal string strServiceShortName;
        internal RM_APP_TYPE ApplicationType;
        internal uint AppStatus;
        internal uint TSSessionId;
        internal int bRestartable;
    }

    private enum RM_APP_TYPE
    {
        Unknown = 0,
        MainWindow = 1,
        OtherWindow = 2,
        Service = 3,
        Explorer = 4,
        Console = 5,
        Critical = 1000
    }
}
