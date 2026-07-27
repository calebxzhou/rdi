using System.Formats.Tar;
using ZstdSharp;

namespace installer;

internal sealed record InstallResult(IReadOnlyList<string> Warnings);

internal static class InstallerService
{
    public static Task<InstallResult> InstallAsync(
        string installPath,
        bool createDesktopShortcut,
        bool createStartMenuShortcut,
        IProgress<string> progress
    ) => Task.Run(() => Install(
        Path.GetFullPath(installPath),
        createDesktopShortcut,
        createStartMenuShortcut,
        progress
    ));

    private static InstallResult Install(
        string installPath,
        bool createDesktopShortcut,
        bool createStartMenuShortcut,
        IProgress<string> progress
    )
    {
        var parentDirectory = Directory.GetParent(installPath)?.FullName
            ?? Path.GetPathRoot(installPath)
            ?? throw new IOException($"无法确定安装目录:{installPath}");
        Directory.CreateDirectory(parentDirectory);
        var stagingDirectory = Path.Combine(parentDirectory, $".rdi-installing-{Guid.CreateVersion7():N}");

        try
        {
            ExtractTo(stagingDirectory, progress);
            if (!File.Exists(Path.Combine(stagingDirectory, "start.exe")))
            {
                throw new InvalidDataException("安装包中缺少start.exe");
            }

            progress.Report("正在写入安装目录");
            MergeIntoInstallDirectory(stagingDirectory, installPath);
        }
        finally
        {
            if (Directory.Exists(stagingDirectory)) Directory.Delete(stagingDirectory, true);
        }

        var warnings = new List<string>();
        var startExe = Path.Combine(installPath, "start.exe");
        if (createDesktopShortcut)
        {
            TryCreateShortcut(
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "rdi.lnk"),
                startExe,
                installPath,
                "Desktop",
                warnings
            );
        }
        if (createStartMenuShortcut)
        {
            TryCreateShortcut(
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "rdi", "rdi.lnk"),
                startExe,
                installPath,
                "Start Menu",
                warnings
            );
        }
        return new InstallResult(warnings);
    }

    private static void ExtractTo(string stagingDirectory, IProgress<string> progress)
    {
        Directory.CreateDirectory(stagingDirectory);
        using var archive = typeof(InstallerService).Assembly.GetManifestResourceStream("client.tar.zst")
            ?? throw new InvalidDataException("未找到内置client.tar.zst");
        using var zstd = new DecompressionStream(archive);
        using var tar = new TarReader(zstd);

        while (tar.GetNextEntry() is { } entry)
        {
            progress.Report($"正在安装:{entry.Name}");
            var destination = ResolveArchivePath(stagingDirectory, entry.Name);
            if (entry.EntryType is TarEntryType.Directory or TarEntryType.DirectoryList)
            {
                Directory.CreateDirectory(destination);
                continue;
            }
            if (entry.EntryType is not (TarEntryType.RegularFile or TarEntryType.V7RegularFile or TarEntryType.ContiguousFile))
            {
                throw new InvalidDataException($"安装包包含不支持的entry:{entry.Name}");
            }

            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            using (var output = new FileStream(destination, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                (entry.DataStream ?? throw new InvalidDataException($"无法读取entry:{entry.Name}"))
                    .CopyTo(output);
            }
            File.SetLastWriteTimeUtc(destination, entry.ModificationTime.UtcDateTime);
        }
    }

    private static string ResolveArchivePath(string rootDirectory, string entryName)
    {
        var relativePath = entryName.Replace('/', Path.DirectorySeparatorChar);
        if (Path.IsPathRooted(relativePath)) throw new InvalidDataException($"非法entry路径:{entryName}");

        var root = Path.GetFullPath(rootDirectory).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var destination = Path.GetFullPath(Path.Combine(rootDirectory, relativePath));
        if (!destination.StartsWith(root, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"非法entry路径:{entryName}");
        }
        return destination;
    }

    private static void MergeIntoInstallDirectory(string stagingDirectory, string installPath)
    {
        if (!Directory.Exists(installPath))
        {
            Directory.Move(stagingDirectory, installPath);
            return;
        }

        EnsureDestinationFilesAreWritable(stagingDirectory, installPath);
        foreach (var sourceDirectory in Directory.EnumerateDirectories(stagingDirectory, "*", SearchOption.AllDirectories))
        {
            Directory.CreateDirectory(Path.Combine(installPath, Path.GetRelativePath(stagingDirectory, sourceDirectory)));
        }
        foreach (var sourceFile in Directory.EnumerateFiles(stagingDirectory, "*", SearchOption.AllDirectories))
        {
            var destination = Path.Combine(installPath, Path.GetRelativePath(stagingDirectory, sourceFile));
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.Move(sourceFile, destination, true);
        }
    }

    private static void EnsureDestinationFilesAreWritable(string stagingDirectory, string installPath)
    {
        foreach (var sourceFile in Directory.EnumerateFiles(stagingDirectory, "*", SearchOption.AllDirectories))
        {
            var destination = Path.Combine(installPath, Path.GetRelativePath(stagingDirectory, sourceFile));
            if (!File.Exists(destination)) continue;
            using var _ = new FileStream(destination, FileMode.Open, FileAccess.ReadWrite, FileShare.None);
        }
    }

    private static void TryCreateShortcut(
        string shortcutPath,
        string targetPath,
        string workingDirectory,
        string shortcutName,
        ICollection<string> warnings
    )
    {
        try
        {
            ShortcutService.Create(shortcutPath, targetPath, workingDirectory);
        }
        catch (Exception exception)
        {
            warnings.Add($"{shortcutName} shortcut创建失败:{exception.Message}");
        }
    }
}
