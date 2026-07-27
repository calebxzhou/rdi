using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;

internal static partial class Program
{
    private const string MftSearchArgument = "--mft-search";
    private const string MftLogPrefix = "LOG\t";
    private const string MftPathPrefix = "PATH\t";
    private const int MaxParallelJdkChecks = 8;
    private const uint BrowseForFileSystemDirectories = 0x00000001;
    private const uint BrowseWithEditBox = 0x00000010;
    private const uint BrowseWithNewDialogStyle = 0x00000040;
    private const uint ApartmentThreaded = 0x00000002;
    private const int LeftShiftVirtualKey = 0xA0;
    private const int MaximumWindowsPathLength = 32768;
    private const string DebugRServerUrl = "http://127.0.0.1:65231";
    private const string OfficialRServerUrl = "https://rdi.calebxzhou.cn:65331";
    private const string Jdk25DownloadUrl = "https://mirrors.huaweicloud.com/eclipse/temurin-compliance/temurin/25/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_windows_hotspot_25.0.3_9.msi";
    private static readonly string LauncherRoot = Path.GetFullPath(AppContext.BaseDirectory).TrimEnd(Path.DirectorySeparatorChar);
    private static readonly string JdkCacheFile = Path.Combine(LauncherRoot, "available_jdks.txt");
    private static string RServerUrl { get; set; } = OfficialRServerUrl;
    private static readonly string[] SearchKeywords =
    [
        "java", "jdk", "jre", "runtime", "jbr", "temurin", "zulu", "oracle",
        "microsoft", "corretto", "graal", "graalvm", "openjdk", "sdk", "bin",
        "program", "cache", "software", "local", "packages", "appdata", "users",
        "public", "25"
    ];

    [STAThread]
    private static int Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        if (args is [MftSearchArgument, var resultFile])
            return RunElevatedMftSearch(resultFile);

        Directory.SetCurrentDirectory(LauncherRoot);

        try
        {
            var launchOptions = ParseLaunchOptions(args);
            RServerUrl = launchOptions.Debug ? DebugRServerUrl : OfficialRServerUrl;
            if (launchOptions.NoUpdate)
                WriteInfo("已关闭自动更新");
            else
                UiLibraryUpdater.TryUpdateAsync(
                        RServerUrl,
                        LauncherRoot,
                        WriteInfo,
                        WriteWarning,
                        RenderDownloadProgress,
                        useBackupApi: !launchOptions.Debug)
                    .GetAwaiter()
                    .GetResult();

            var libDirectory = Path.Combine(LauncherRoot, "lib");
            if (!Directory.Exists(libDirectory) || !Directory.EnumerateFiles(libDirectory, "*.jar").Any())
                return Fail("缺少UI库文件。\r\n请确认客户端已完整解压，或检查网络后重试。");

            var bestJava = IsLeftShiftPressed()
                ? ShowStartupOptions() ?? ResolveBestJdk25()
                : ResolveBestJdk25();
            var javaExe = Path.Combine(bestJava.JavaHome, "bin", launchOptions.AppLogs ? "java.exe" : "javaw.exe");
            if (!File.Exists(javaExe))
                return Fail($"找到的Java25缺少{Path.GetFileName(javaExe)}：\r\n{bestJava.JavaHome}");

            return StartClient(javaExe, launchOptions);
        }
        catch (Exception exception)
        {
            return Fail($"启动失败。\r\n错误: {exception.Message}");
        }
    }

    private static void WriteInfo(string message) =>
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] {message}");

    private static void WriteWarning(string message)
    {
        Console.ForegroundColor = ConsoleColor.Yellow;
        WriteInfo($"警告: {message}");
        Console.ResetColor();
    }

    private static int Fail(string message)
    {
        ShowStartError(message);
        Console.WriteLine();
        Console.Write("按回车键关闭窗口");
        Console.ReadLine();
        return 1;
    }

    private static void ShowStartError(string message)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine(message);
        Console.ResetColor();
        MessageBox(nint.Zero, message, "RDI启动失败", 0x10);
    }

    private static LaunchOptions ParseLaunchOptions(string[] args)
    {
        List<string> jvmArguments = [];
        var debug = false;
        var appLogs = false;
        var noUpdate = false;

        foreach (var argument in args)
        {
            if (argument.Equals("--debug", StringComparison.OrdinalIgnoreCase))
                debug = true;
            else if (argument.Equals("--app-logs", StringComparison.OrdinalIgnoreCase))
                appLogs = true;
            else if (argument.Equals("--no-update", StringComparison.OrdinalIgnoreCase))
                noUpdate = true;
            else if (argument.StartsWith("--jvmArg=", StringComparison.OrdinalIgnoreCase))
                jvmArguments.AddRange(ParseJvmArguments(argument[(argument.IndexOf('=') + 1)..]));
            else
                throw new ArgumentException($"未知启动参数: {argument}");
        }

        return new(debug, appLogs, noUpdate, jvmArguments);
    }

    private static bool IsLeftShiftPressed() =>
        (GetAsyncKeyState(LeftShiftVirtualKey) & 0x8000) != 0;

    private static JdkCandidate? ShowStartupOptions()
    {
        string[] options = ["重新手动选择JDK25", "TODO"];
        var selectedIndex = 0;

        while (true)
        {
            Console.Clear();
            Console.WriteLine("启动选项（使用↑/↓选择，按Enter确认）");
            Console.WriteLine();
            foreach (var (index, option) in options.Index())
            {
                Console.ForegroundColor = index == selectedIndex
                    ? ConsoleColor.Yellow
                    : ConsoleColor.Gray;
                Console.WriteLine($"{(index == selectedIndex ? '>' : ' ')} {index + 1}.{option}");
            }
            Console.ResetColor();

            switch (Console.ReadKey(true).Key)
            {
                case ConsoleKey.UpArrow:
                    selectedIndex = (selectedIndex - 1 + options.Length) % options.Length;
                    break;
                case ConsoleKey.DownArrow:
                    selectedIndex = (selectedIndex + 1) % options.Length;
                    break;
                case ConsoleKey.Enter:
                    Console.Clear();
                    return selectedIndex == 0 ? SelectManualJdk25() : null;
            }
        }
    }

    private static string[] ParseJvmArguments(string arguments)
    {
        if (string.IsNullOrWhiteSpace(arguments))
            return [];

        var argumentList = CommandLineToArgvW($"updater {arguments}", out var argumentCount);
        if (argumentList == nint.Zero)
            throw new Win32Exception(Marshal.GetLastWin32Error(), "无法解析JVM参数");

        try
        {
            return
            [
                .. Enumerable.Range(1, argumentCount - 1)
                    .Select(index => Marshal.PtrToStringUni(Marshal.ReadIntPtr(argumentList, index * IntPtr.Size))!)
            ];
        }
        finally
        {
            LocalFree(argumentList);
        }
    }

    private static JdkCandidate ResolveBestJdk25()
    {
        while (true)
        {
            if (FindBestJdk25() is { } bestJava)
                return bestJava;

            ShowNoJavaOptions();
            var choice = Console.ReadKey(true).Key;
            Console.WriteLine();

            switch (choice)
            {
                case ConsoleKey.Spacebar:
                    if (InstallJdk25())
                        WriteInfo("将重新搜索Java25");
                    break;
                case ConsoleKey.Enter:
                    if (SelectManualJdk25() is { } manualJava)
                        return manualJava;
                    WriteInfo("手动选择未得到可用Java25，返回选择菜单");
                    break;
                default:
                    WriteInfo("用户选择重新授权并搜索Java25");
                    break;
            }
        }
    }

    private static void ShowNoJavaOptions()
    {
        Console.WriteLine();
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine("未找到可用的64位Java25。");
        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine("按空格键下载并安装Java25，按回车键手动选择JDK安装目录，按任意其他键重新授权并搜索。");
        Console.ResetColor();
    }

    private static bool InstallJdk25()
    {
        var installerPath = Path.Combine(Path.GetTempPath(), "OpenJDK25U-jdk_x64_windows_hotspot_25.0.3_9.msi");

        try
        {
            WriteInfo("正在下载Java25安装程序");
            Download.FileAsync(
                    Jdk25DownloadUrl,
                    installerPath,
                    reportProgress: RenderDownloadProgress,
                    reportStatus: WriteInfo)
                .GetAwaiter()
                .GetResult();

            WriteInfo("下载完成，正在启动Java25安装程序");
            using var installer = Process.Start(new ProcessStartInfo
            {
                FileName = "msiexec.exe",
                UseShellExecute = true,
                Arguments = $"/i \"{installerPath}\" /norestart"
            }) ?? throw new InvalidOperationException("无法启动Java25安装程序");
            installer.WaitForExit();
            if (installer.ExitCode is not (0 or 3010))
                throw new InvalidOperationException($"安装程序退出码: {installer.ExitCode}");

            WriteInfo("Java25安装完成");
            return true;
        }
        catch (Exception exception)
        {
            ShowStartError($"Java25下载安装失败。\r\n错误: {exception.Message}");
            return false;
        }
    }

    private static void RenderDownloadProgress(FileDownloadProgress progress)
    {
        var progressText = progress.TotalBytes is { } total
            ? $"下载进度: {progress.DownloadedBytes * 100d / total,6:F2}%  {FormatBytes(progress.DownloadedBytes)}/{FormatBytes(total)}  {FormatBytes(progress.BytesPerSecond)}/s"
            : $"下载进度: {FormatBytes(progress.DownloadedBytes)}  {FormatBytes(progress.BytesPerSecond)}/s";
        Console.Write($"\r{progressText,-80}");
        if (progress.Completed)
            Console.WriteLine();
    }

    private static string FormatBytes(double bytes) => bytes switch
    {
        >= 1024 * 1024 * 1024 => $"{bytes / 1024 / 1024 / 1024:F2}GB",
        >= 1024 * 1024 => $"{bytes / 1024 / 1024:F2}MB",
        >= 1024 => $"{bytes / 1024:F2}KB",
        _ => $"{bytes:F0}B"
    };

    private static JdkCandidate? SelectManualJdk25()
    {
        WriteInfo("请手动选择Java25安装目录");
        if (PickFolderPath("选择Java25安装目录") is not { Length: > 0 } selectedPath)
        {
            WriteInfo("未选择目录");
            return null;
        }

        WriteInfo($"已选择目录: {selectedPath}");
        HashSet<string> candidates = new(StringComparer.OrdinalIgnoreCase);
        candidates.AddJavaPath(selectedPath);
        var validCandidates = FindValidJdk25Candidates(candidates, "手动选择");
        if (validCandidates is [])
        {
            ShowStartError($"所选目录不是可用的64位Java25：\r\n{selectedPath}");
            return null;
        }

        WriteCachedJdkList(validCandidates);
        PrintAvailableJdkList(validCandidates, "手动选择");
        return SelectBestJdk25Candidate(validCandidates);
    }

    private static string? PickFolderPath(string title)
    {
        Marshal.ThrowExceptionForHR(CoInitializeEx(nint.Zero, ApartmentThreaded));
        var displayName = Marshal.AllocHGlobal(260 * sizeof(char));
        try
        {
            var browseInfo = new BrowseInfo
            {
                Owner = GetConsoleWindow(),
                DisplayName = displayName,
                Title = title,
                Flags = BrowseForFileSystemDirectories | BrowseWithEditBox | BrowseWithNewDialogStyle
            };
            var itemIdList = SHBrowseForFolder(ref browseInfo);
            if (itemIdList == nint.Zero)
                return null;

            try
            {
                var path = new StringBuilder(MaximumWindowsPathLength);
                return SHGetPathFromIDList(itemIdList, path, path.Capacity, 0)
                    ? path.ToString()
                    : null;
            }
            finally
            {
                Marshal.FreeCoTaskMem(itemIdList);
            }
        }
        catch (Exception exception)
        {
            WriteInfo($"打开文件夹选择框失败: {exception.Message}");
            return null;
        }
        finally
        {
            Marshal.FreeHGlobal(displayName);
            CoUninitialize();
        }
    }

    private static int RunElevatedMftSearch(string resultFile)
    {
        try
        {
            File.WriteAllText(resultFile, string.Empty, Encoding.UTF8);
            var result = NtfsMftEnum.FindJavaExecutables(path =>
                File.AppendAllLines(resultFile, [$"{MftPathPrefix}{path}"], Encoding.UTF8));
            File.AppendAllLines(
                resultFile,
                result.Diagnostics.Select(message => $"{MftLogPrefix}{message}"),
                Encoding.UTF8);
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine(exception.Message);
            return 1;
        }
    }

    private static MftSearchResult FindJavaExecutablesWithAdministratorPrivilege()
    {
        var resultFile = Path.Combine(Path.GetTempPath(), $"rdi-java-{Guid.NewGuid():N}.txt");
        HashSet<string> candidates = new(StringComparer.OrdinalIgnoreCase);
        var succeeded = false;

        try
        {
            WriteInfo("请给予管理员权限以搜索电脑上所有的java");
            var executable = Environment.ProcessPath
                ?? throw new InvalidOperationException("无法确定updater程序路径");
            var startInfo = new ProcessStartInfo
            {
                FileName = executable,
                Verb = "runas",
                UseShellExecute = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            startInfo.ArgumentList.Add(MftSearchArgument);
            startInfo.ArgumentList.Add(resultFile);
            WriteInfo("正在搜索java，请稍等15~60秒左右");
            using var process = Process.Start(startInfo)
                ?? throw new InvalidOperationException("无法启动搜索流程");
            var readCharacterCount = 0;
            while (!process.WaitForExit(100))
                ReadMftSearchUpdates(resultFile, candidates, ref readCharacterCount);
            ReadMftSearchUpdates(resultFile, candidates, ref readCharacterCount);
            if (process.ExitCode != 0)
            {
                WriteInfo($"搜索失败，退出码: {process.ExitCode}");
                return new(false, candidates);
            }

            succeeded = true;
            WriteInfo($"搜索完成，找到{candidates.Count}个java.exe");
        }
        catch (Win32Exception exception) when (exception.NativeErrorCode == 1223)
        {
            WriteInfo("用户取消了权限请求");
        }
        catch (Exception exception)
        {
            WriteInfo($"搜索失败: {exception.Message}");
        }
        finally
        {
            File.Delete(resultFile);
        }

        return new(succeeded, candidates);
    }

    private static void ReadMftSearchUpdates(
        string resultFile,
        HashSet<string> candidates,
        ref int readCharacterCount)
    {
        if (!File.Exists(resultFile))
            return;

        string content;
        try
        {
            content = File.ReadAllText(resultFile, Encoding.UTF8);
        }
        catch (IOException)
        {
            return;
        }

        var completeLength = content.LastIndexOf('\n') + 1;
        if (completeLength <= readCharacterCount)
            return;

        foreach (var line in content[readCharacterCount..completeLength]
                     .Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
        {
            if (line.StartsWith(MftLogPrefix, StringComparison.Ordinal))
                WriteInfo(line[MftLogPrefix.Length..]);
            else if (line.StartsWith(MftPathPrefix, StringComparison.Ordinal) &&
                     candidates.Add(line[MftPathPrefix.Length..]))
            {
                WriteInfo($"发现java.exe: {line[MftPathPrefix.Length..]}");
            }
        }
        readCharacterCount = completeLength;
    }

    private static JdkCandidate? FindBestJdk25()
    {
        WriteInfo("开始搜索Java25");

        if (ReadCachedJdkList() is { Count: > 0 } cachedJavaHomes)
        {
            WriteInfo("优先使用JDK缓存列表");
            var cachedCandidates = ConvertJavaHomesToCandidateSet(cachedJavaHomes);
            if (FindFirstValidJdk25Candidate(cachedCandidates, "缓存校验") is { } cachedJava)
            {
                WriteInfo("缓存中的Java25仍然可用，跳过其它缓存项校验");
                return cachedJava;
            }
            WriteInfo("缓存中的Java25已失效，开始重新搜索");
        }

        var mftSearch = FindJavaExecutablesWithAdministratorPrivilege();
        if (!mftSearch.Succeeded)
            return null;

        if (FindFirstValidJdk25Candidate(mftSearch.Candidates, "MFT搜索") is { } mftJava)
        {
            List<JdkCandidate> candidates = [mftJava];
            WriteCachedJdkList(candidates);
            PrintAvailableJdkList(candidates, "MFT搜索");
            return mftJava;
        }

        var driveRoots = GetDriveRoots();
        WriteInfo($"快速搜索未找到可用Java25，开始目录搜索，共{driveRoots.Count}个磁盘");
        var directoryCandidates = SearchJdk25Candidates(driveRoots, "搜索Java");
        var validCandidates = FindValidJdk25Candidates(directoryCandidates, "最终校验");
        if (validCandidates is [])
        {
            if (File.Exists(JdkCacheFile))
                File.Delete(JdkCacheFile);
            return null;
        }

        WriteCachedJdkList(validCandidates);
        PrintAvailableJdkList(validCandidates, "重新搜索");
        return SelectBestJdk25Candidate(validCandidates);
    }

    private static List<string> ReadCachedJdkList()
    {
        if (!File.Exists(JdkCacheFile))
        {
            WriteInfo("未发现JDK缓存文件avaliable_jdks.txt");
            return [];
        }

        var javaHomes = File.ReadLines(JdkCacheFile)
            .Select(line => line.Trim())
            .Where(line => line.Length > 0)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        WriteInfo($"读取JDK缓存成功，共{javaHomes.Count}条");
        return javaHomes;
    }

    private static void WriteCachedJdkList(List<JdkCandidate> candidates)
    {
        var javaHomes = candidates
            .Select(candidate => candidate.JavaHome)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .Order()
            .ToArray();
        File.WriteAllLines(JdkCacheFile, javaHomes, Encoding.UTF8);
        WriteInfo($"已写入JDK缓存到avaliable_jdks.txt，共{javaHomes.Length}条");
    }

    private static HashSet<string> ConvertJavaHomesToCandidateSet(IEnumerable<string> javaHomes)
    {
        HashSet<string> candidates = new(StringComparer.OrdinalIgnoreCase);
        foreach (var javaHome in javaHomes)
            candidates.AddJavaPath(javaHome);
        return candidates;
    }

    private static List<string> GetDriveRoots() => DriveInfo.GetDrives()
        .Where(drive => drive.IsReady && drive.DriveType != DriveType.Network)
        .Select(drive => drive.RootDirectory.FullName)
        .ToList();

    private static HashSet<string> SearchJdk25Candidates(IEnumerable<string> roots, string stageName)
    {
        HashSet<string> candidates = new(StringComparer.OrdinalIgnoreCase);
        HashSet<string> seenDirectories = new(StringComparer.OrdinalIgnoreCase);
        var rootList = roots.ToList();

        foreach (var (index, candidateRoot) in rootList.Index())
        {
            var root = candidateRoot.NormalizedPath;
            if (root is null || !Directory.Exists(root))
                continue;

            WriteInfo($"搜索阶段[{stageName}] {index + 1}/{rootList.Count}: {root}");
            candidates.AddJavaPath(root);
            Stack<SearchDirectory> stack = new();
            stack.Push(new(root, root.Contains(".jdks", StringComparison.OrdinalIgnoreCase) ||
                                 root.Contains(".sdkman", StringComparison.OrdinalIgnoreCase), 0));

            while (stack.TryPop(out var current))
            {
                var currentPath = current.Path.NormalizedPath;
                if (currentPath is null || !seenDirectories.Add(currentPath) || !Directory.Exists(currentPath))
                    continue;

                string[] directories;
                try
                {
                    directories = Directory.GetDirectories(currentPath);
                }
                catch
                {
                    continue;
                }

                foreach (var directory in directories)
                {
                    FileAttributes attributes;
                    try
                    {
                        attributes = File.GetAttributes(directory);
                    }
                    catch
                    {
                        continue;
                    }

                    if (attributes.HasFlag(FileAttributes.ReparsePoint))
                        continue;

                    var name = Path.GetFileName(directory).ToLowerInvariant();
                    var interesting = current.ForceDeep || name == "bin" ||
                                      NumericDirectoryRegex().IsMatch(name) ||
                                      name.ContainsAny(SearchKeywords);
                    if (!interesting)
                        continue;

                    candidates.AddJavaPath(directory);
                    var nextDepth = current.Depth + 1;
                    if (current.ForceDeep || nextDepth < 4)
                    {
                        var forceDeep = current.ForceDeep || name is "java" or "jdk" or "jre" or "runtime" or "jbr" or "bin";
                        stack.Push(new(directory, forceDeep, nextDepth));
                    }
                }
            }
        }

        return candidates;
    }

    private static List<JdkCandidate> FindValidJdk25Candidates(IEnumerable<string> candidateSet, string stageName)
    {
        var validCandidates = ValidateJdk25CandidatesParallel(candidateSet, stageName);
        foreach (var candidate in validCandidates)
            WriteInfo($"发现可用Java25: {candidate.JavaHome}");
        return validCandidates;
    }

    private static JdkCandidate? FindFirstValidJdk25Candidate(IEnumerable<string> candidateSet, string stageName)
    {
        var validCandidates = ValidateJdk25CandidatesParallel(candidateSet, stageName);
        if (validCandidates is [])
            return null;

        var bestCandidate = SelectBestJdk25Candidate(validCandidates);
        WriteInfo($"阶段[{stageName}]发现可用Java25，直接使用: {bestCandidate.JavaHome}");
        return bestCandidate;
    }

    private static List<JdkCandidate> ValidateJdk25CandidatesParallel(
        IEnumerable<string> candidateSet,
        string stageName)
    {
        var candidates = candidateSet
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
        if (candidates is [])
            return [];

        return candidates
            .Index()
            .AsParallel()
            .WithDegreeOfParallelism(Math.Min(MaxParallelJdkChecks, candidates.Count))
            .Select(indexedCandidate =>
            {
                var (index, javaExe) = indexedCandidate;
                WriteInfo($"并行校验阶段[{stageName}] {index + 1}/{candidates.Count}: {javaExe}");
                return TestJavaCandidate(javaExe);
            })
            .OfType<JdkCandidate>()
            .ToList();
    }

    private static JdkCandidate? TestJavaCandidate(string javaExe)
    {
        if (!File.Exists(javaExe))
            return null;

        string versionOutput;
        try
        {
            versionOutput = InvokeProcessCapture(javaExe, "-version");
        }
        catch
        {
            return null;
        }

        var versionLines = versionOutput.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries);
        var versionLine = versionLines
            .Select(line => line.Trim())
            .FirstOrDefault(VersionLineRegex().IsMatch)
            ?? versionLines.FirstOrDefault()
            ?? "<empty>";
        WriteInfo($"检测到Java版本: {javaExe} -> {WhitespaceRegex().Replace(versionLine, " ")}");

        var match = JavaVersionRegex().Match(versionLine);
        if (!match.Success)
            return null;
        var parts = match.Groups["version"].Value.Split('.');
        var majorVersion = parts is ["1", var legacyMajor, ..] ? int.Parse(legacyMajor) : int.Parse(parts[0]);
        if (majorVersion != 25)
        {
            WriteInfo($"跳过该Java，主版本不是25: {javaExe}");
            return null;
        }
        if (X86Regex().IsMatch(versionOutput))
        {
            WriteInfo($"跳过该Java，不是64位: {javaExe}");
            return null;
        }

        var javaHome = Directory.GetParent(Path.GetDirectoryName(javaExe)!)!.FullName;
        if (!File.Exists(Path.Combine(javaHome, "bin", "javac.exe")))
        {
            WriteInfo($"跳过该Java，不是JDK: {javaHome}");
            return null;
        }

        return new(javaExe, javaHome, versionOutput.Trim(), GetPathScore(javaExe));
    }

    private static string InvokeProcessCapture(string fileName, string arguments)
    {
        using var process = Process.Start(new ProcessStartInfo
        {
            FileName = fileName,
            Arguments = arguments,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        }) ?? throw new InvalidOperationException($"无法启动{fileName}");
        var output = process.StandardOutput.ReadToEnd();
        var error = process.StandardError.ReadToEnd();
        process.WaitForExit();
        string[] outputParts = [error.TrimEnd(), output.TrimEnd()];
        return string.Join(Environment.NewLine, outputParts.Where(text => text.Length > 0));
    }

    private static int GetPathScore(string javaExe)
    {
        var score = 0;
        var path = javaExe.ToLowerInvariant();
        if (path.StartsWith(LauncherRoot.ToLowerInvariant())) score += 5000;
        if (path.Contains("\\.jdks\\")) score += 1000;
        if (path.Contains("\\program files\\")) score += 600;
        if (path.Contains("\\users\\")) score += 300;
        if (path.Contains("jdk-25") || path.Contains("jdk25")) score += 400;
        if (path.Contains("temurin")) score += 120;
        if (path.Contains("microsoft")) score += 100;
        if (path.Contains("oracle")) score += 80;
        return score;
    }

    private static JdkCandidate SelectBestJdk25Candidate(List<JdkCandidate> candidates) => candidates
        .OrderByDescending(candidate => candidate.PathScore)
        .ThenBy(candidate => candidate.JavaHome.Length)
        .ThenBy(candidate => candidate.JavaHome)
        .First();

    private static void PrintAvailableJdkList(List<JdkCandidate> candidates, string sourceName)
    {
        WriteInfo($"可用Java25列表[{sourceName}]，共{candidates.Count}项:");
        var sorted = candidates
            .OrderByDescending(candidate => candidate.PathScore)
            .ThenBy(candidate => candidate.JavaHome.Length)
            .ThenBy(candidate => candidate.JavaHome)
            .ToList();
        foreach (var (index, candidate) in sorted.Index())
        {
            var versionLine = candidate.VersionText
                .Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries)
                .FirstOrDefault(VersionLineRegex().IsMatch)
                ?? "<unknown version>";
            WriteInfo($"  [{index + 1}] {candidate.JavaHome}");
            WriteInfo($"       {versionLine.Trim()}");
        }
    }

    private static int StartClient(string javaExe, LaunchOptions options)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = javaExe,
            WorkingDirectory = LauncherRoot,
            UseShellExecute = false,
            RedirectStandardOutput = options.AppLogs,
            RedirectStandardError = options.AppLogs,
            CreateNoWindow = options.AppLogs
        };
        if (options.AppLogs)
        {
            startInfo.StandardOutputEncoding = Encoding.UTF8;
            startInfo.StandardErrorEncoding = Encoding.UTF8;
        }
        string[] arguments =
        [
            "-Dfile.encoding=UTF-8",
            .. options.JvmArguments,
            $"-Drdi.debug={options.Debug.ToString().ToLowerInvariant()}",
            .. (options.NoUpdate ? new[] { "-Drdi.noUpdate=true" } : Array.Empty<string>()),
            $"-Drdi.updater.pid={Environment.ProcessId}",
            $"-Drdi.updater.islogmode={options.AppLogs.ToString().ToLowerInvariant()}",
            $"-Drserverurl={RServerUrl}",
            "-cp", "lib/*",
            "--enable-native-access=ALL-UNNAMED",
            "calebxzau.rdi.client.MainKt"
        ];
        foreach (var argument in arguments)
            startInfo.ArgumentList.Add(argument);

        WriteInfo($"将使用Java25启动: {Path.GetDirectoryName(Path.GetDirectoryName(javaExe))}");
        using var process = Process.Start(startInfo) ?? throw new InvalidOperationException("无法启动RDI客户端");
        if (options.AppLogs)
        {
            WriteInfo($"已启动进程PID: {process.Id}，正在接收RDI日志");
            var outputTask = ForwardAppLogs(process.StandardOutput);
            var errorTask = ForwardAppLogs(process.StandardError);
            process.WaitForExit();
            Task.WhenAll(outputTask, errorTask).GetAwaiter().GetResult();
            WriteInfo($"RDI已退出，退出码: {process.ExitCode}");
            return process.ExitCode;
        }

        WriteInfo($"已启动，正在观察确认是否稳定启动");
        if (process.WaitForExit(3000))
            return Fail($"程序在启动后3秒内退出\r\n退出码: {process.ExitCode}\r\nJava: {javaExe}");

        WriteInfo("启动成功，2秒后关闭本窗口");
        Thread.Sleep(2000);
        return 0;
    }

    private static async Task ForwardAppLogs(StreamReader reader)
    {
        while (await reader.ReadLineAsync() is { } line)
            Console.WriteLine(line);
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct BrowseInfo
    {
        public nint Owner;
        public nint Root;
        public nint DisplayName;
        [MarshalAs(UnmanagedType.LPWStr)] public string? Title;
        public uint Flags;
        public nint Callback;
        public nint CallbackData;
        public int Image;
    }

    [DllImport("kernel32.dll")]
    private static extern nint GetConsoleWindow();

    [DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int virtualKey);

    [DllImport("ole32.dll")]
    private static extern int CoInitializeEx(nint reserved, uint concurrencyModel);

    [DllImport("ole32.dll")]
    private static extern void CoUninitialize();

    [DllImport("shell32.dll", EntryPoint = "SHBrowseForFolderW", CharSet = CharSet.Unicode)]
    private static extern nint SHBrowseForFolder(ref BrowseInfo browseInfo);

    [DllImport("shell32.dll", EntryPoint = "SHGetPathFromIDListEx", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SHGetPathFromIDList(
        nint itemIdList,
        StringBuilder path,
        int pathLength,
        uint flags);

    [DllImport("shell32.dll", EntryPoint = "CommandLineToArgvW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern nint CommandLineToArgvW(string commandLine, out int argumentCount);

    [DllImport("kernel32.dll")]
    private static extern nint LocalFree(nint memory);

    [DllImport("user32.dll", EntryPoint = "MessageBoxW", CharSet = CharSet.Unicode)]
    private static extern int MessageBox(nint windowHandle, string text, string caption, uint type);

    [GeneratedRegex(@"^\d+(\.\d+)*$")]
    private static partial Regex NumericDirectoryRegex();

    [GeneratedRegex("\\bversion\\b\\s+\"[^\"]+\"")]
    private static partial Regex VersionLineRegex();

    [GeneratedRegex("\"(?<version>\\d+(?:\\.\\d+){0,3})")]
    private static partial Regex JavaVersionRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();

    [GeneratedRegex("32-Bit|x86", RegexOptions.IgnoreCase)]
    private static partial Regex X86Regex();

    private sealed record JdkCandidate(string JavaExe, string JavaHome, string VersionText, int PathScore);
    private sealed record MftSearchResult(bool Succeeded, HashSet<string> Candidates);
    private sealed record SearchDirectory(string Path, bool ForceDeep, int Depth);
    private sealed record LaunchOptions(bool Debug, bool AppLogs, bool NoUpdate, List<string> JvmArguments);

}

file static class LauncherExtensions
{
    extension(string? path)
    {
        public string? NormalizedPath
        {
            get
            {
                if (string.IsNullOrWhiteSpace(path))
                    return null;

                try
                {
                    var normalized = Path.GetFullPath(path.Trim().Trim('"'));
                    return Path.GetPathRoot(normalized) == normalized
                        ? normalized
                        : normalized.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
                }
                catch
                {
                    return null;
                }
            }
        }

        public bool ContainsAny(IEnumerable<string> values) =>
            path is not null && values.Any(path.Contains);
    }

    extension(HashSet<string> candidates)
    {
        public void AddJavaPath(string path)
        {
            if (path.NormalizedPath is not { } normalized)
                return;

            if (Directory.Exists(normalized))
            {
                string[] javaExecutables =
                [
                    Path.Combine(normalized, "java.exe"),
                    Path.Combine(normalized, "bin", "java.exe")
                ];
                foreach (var javaExe in javaExecutables.Where(File.Exists))
                    candidates.Add(Path.GetFullPath(javaExe));
            }
            else if (File.Exists(normalized) &&
                     Path.GetFileName(normalized).Equals("java.exe", StringComparison.OrdinalIgnoreCase))
            {
                candidates.Add(normalized);
            }
        }
    }
}
