using System.Diagnostics;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;

internal static partial class Program
{
    private const string Jdk25DownloadUrl = "https://mirrors.huaweicloud.com/eclipse/temurin-compliance/temurin/25/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_windows_hotspot_25.0.3_9.msi";
    private static readonly string LauncherRoot = Path.GetFullPath(AppContext.BaseDirectory).TrimEnd(Path.DirectorySeparatorChar);
    private static readonly string JdkCacheFile = Path.Combine(LauncherRoot, "available_jdks.txt");
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
        Directory.SetCurrentDirectory(LauncherRoot);

        try
        {
            var bestJava = ResolveBestJdk25();
            var javawExe = Path.Combine(bestJava.JavaHome, "bin", "javaw.exe");
            if (!File.Exists(javawExe))
                return Fail($"找到的Java25缺少javaw.exe：\r\n{bestJava.JavaHome}");

            var mainJar = Path.Combine(LauncherRoot, "lib", "rdi-5-ui.jar");
            if (!File.Exists(mainJar))
                return Fail("缺少lib文件夹或rdi-5-ui.jar。\r\n请确认客户端已完整解压。");

            return StartClient(javawExe, args);
        }
        catch (Exception exception)
        {
            return Fail($"启动失败。\r\n错误: {exception.Message}");
        }
    }

    private static void WriteInfo(string message) =>
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] {message}");

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
                case ConsoleKey.R:
                    WriteInfo("用户选择重新搜索Java25");
                    break;
                case ConsoleKey.Enter:
                    if (SelectManualJdk25() is { } manualJava)
                        return manualJava;
                    WriteInfo("手动选择未得到可用Java25，返回选择菜单");
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
        Console.WriteLine("按空格键下载并安装Java25，按R重新搜索，按回车键手动选择JDK安装目录。");
        Console.ResetColor();
    }

    private static bool InstallJdk25()
    {
        var installerPath = Path.Combine(Path.GetTempPath(), "OpenJDK25U-jdk_x64_windows_hotspot_25.0.3_9.msi");

        try
        {
            WriteInfo("正在下载Java25安装程序");
            using var client = new HttpClient { Timeout = Timeout.InfiniteTimeSpan };
            using var response = client.GetAsync(Jdk25DownloadUrl, HttpCompletionOption.ResponseHeadersRead).GetAwaiter().GetResult();
            response.EnsureSuccessStatusCode();
            using (var source = response.Content.ReadAsStream())
            using (var target = File.Create(installerPath))
                source.CopyTo(target);

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
        try
        {
            var shellType = Type.GetTypeFromProgID("Shell.Application")
                ?? throw new InvalidOperationException("无法使用文件夹选择器");
            dynamic shell = Activator.CreateInstance(shellType)!;
            dynamic? folder = shell.BrowseForFolder(0, title, 0, 0);
            return folder?.Self?.Path as string;
        }
        catch (Exception exception)
        {
            WriteInfo($"打开文件夹选择框失败: {exception.Message}");
            return null;
        }
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

        var priorityCandidates = SearchJdk25Candidates(GetInitialCandidateRoots(), "优先目录");
        WriteInfo($"优先目录候选Java数量: {priorityCandidates.Count}");
        var priorityValidCandidates = FindValidJdk25Candidates(priorityCandidates, "优先目录");
        if (priorityValidCandidates is not [])
        {
            WriteCachedJdkList(priorityValidCandidates);
            PrintAvailableJdkList(priorityValidCandidates, "优先目录");
            return SelectBestJdk25Candidate(priorityValidCandidates);
        }

        var driveRoots = GetDriveRoots();
        WriteInfo($"优先目录未找到可用Java25，开始搜索Java，共{driveRoots.Count}个磁盘");
        var allCandidates = SearchJdk25Candidates(driveRoots, "搜索Java");
        allCandidates.UnionWith(priorityCandidates);
        var validCandidates = FindValidJdk25Candidates(allCandidates, "最终校验");
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

    private static List<string> GetInitialCandidateRoots()
    {
        var userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        List<string?> roots =
        [
            Environment.GetEnvironmentVariable("JAVA_HOME"),
            userProfile,
            Environment.GetEnvironmentVariable("PUBLIC"),
            Path.Combine(userProfile, ".jdks"),
            Path.Combine(userProfile, ".sdkman", "candidates", "java"),
            LauncherRoot,
            .. (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';')
        ];
        AddChildRoot(roots, "LOCALAPPDATA", "Programs");
        AddChildRoot(roots, "LOCALAPPDATA", "Microsoft");
        AddChildRoot(roots, "ProgramFiles", "Java");
        AddChildRoot(roots, "ProgramFiles", "Microsoft");
        AddChildRoot(roots, "ProgramFiles", "Eclipse Adoptium");
        AddChildRoot(roots, "ProgramFiles", "BellSoft");

        return roots
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .Select(path => path!)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    private static void AddChildRoot(List<string?> roots, string environmentVariable, string child)
    {
        if (Environment.GetEnvironmentVariable(environmentVariable) is { Length: > 0 } root)
            roots.Add(Path.Combine(root, child));
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
        var candidates = candidateSet.ToList();
        var validCandidates = new List<JdkCandidate>();
        foreach (var (index, javaExe) in candidates.Index())
        {
            WriteInfo($"校验阶段[{stageName}] {index + 1}/{candidates.Count}: {javaExe}");
            if (TestJavaCandidate(javaExe) is { } candidate)
            {
                validCandidates.Add(candidate);
                WriteInfo($"发现可用Java25: {candidate.JavaHome}");
            }
        }
        return validCandidates;
    }

    private static JdkCandidate? FindFirstValidJdk25Candidate(IEnumerable<string> candidateSet, string stageName)
    {
        var candidates = candidateSet
            .OrderByDescending(GetPathScore)
            .ThenBy(path => path.Length)
            .ThenBy(path => path)
            .ToList();

        foreach (var (index, javaExe) in candidates.Index())
        {
            WriteInfo($"校验阶段[{stageName}] {index + 1}/{candidates.Count}: {javaExe}");
            if (TestJavaCandidate(javaExe) is { } candidate)
            {
                WriteInfo($"缓存中发现可用Java25，直接使用: {candidate.JavaHome}");
                return candidate;
            }
        }
        return null;
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

    private static int StartClient(string javawExe, string[] additionalJvmParameters)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = javawExe,
            WorkingDirectory = LauncherRoot,
            UseShellExecute = false
        };
        string[] arguments =
        [
            "-Dfile.encoding=UTF-8",
            .. additionalJvmParameters,
            "-cp", "lib/*",
            "--enable-native-access=ALL-UNNAMED",
            "calebxzhou.rdi.client.MainKt"
        ];
        foreach (var argument in arguments)
            startInfo.ArgumentList.Add(argument);

        WriteInfo($"将使用Java25启动: {Path.GetDirectoryName(Path.GetDirectoryName(javawExe))}");
        using var process = Process.Start(startInfo) ?? throw new InvalidOperationException("无法启动RDI客户端");
        WriteInfo($"已启动进程PID: {process.Id}，正在观察5秒确认是否稳定启动");
        if (process.WaitForExit(5000))
            return Fail($"程序在启动后5秒内退出，可能触发自动更新，也有可能启动失败\r\n退出码: {process.ExitCode}\r\nJava: {javawExe}");

        WriteInfo("启动成功，5秒后关闭本窗口");
        Thread.Sleep(5000);
        return 0;
    }

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
    private sealed record SearchDirectory(string Path, bool ForceDeep, int Depth);
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
