using System.IO.Compression;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

internal static partial class UiLibraryUpdater
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        TypeInfoResolver = UpdaterJsonContext.Default
    };

    public static async Task<UiLibraryUpdateResult> TryUpdateAsync(
        string primaryUrl,
        string launcherRoot,
        Action<string> writeInfo,
        Action<string> writeWarning,
        Action<FileDownloadProgress> reportProgress,
        bool useBackupApi,
        string? playerIpv4,
        Func<LibraryRecoveryPrompt, bool>? promptRecovery = null)
    {
        var backupUrl = useBackupApi ? await ResolveBackupUrl(primaryUrl, playerIpv4, writeInfo) : null;
        var apiUrls = new[] { backupUrl, primaryUrl }
            .Where(url => !string.IsNullOrWhiteSpace(url))
            .Select(url => url!.TrimEnd('/'))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();

        var release = await GetRelease(apiUrls, writeInfo);
        if (release is null)
        {
            writeWarning("无法获取UI库信息，将使用本地版本启动");
            return UiLibraryUpdateResult.LocalVersionAvailable;
        }

        var libDirectory = Path.Combine(launcherRoot, "lib");
        var installedVersion = ReadInstalledVersion(libDirectory);
        if (release.Version is { } targetVersion)
        {
            writeInfo(installedVersion is null
                ? $"本地UI版本未知，目标版本为{targetVersion}"
                : $"本地UI版本{installedVersion}，目标版本为{targetVersion}");
        }

        if (IsCurrent(libDirectory, release))
        {
            writeInfo("UI库已是最新版本");
            return UiLibraryUpdateResult.UpToDate;
        }

        var updateId = Guid.NewGuid().ToString("N");
        var stagingDirectory = Path.Combine(launcherRoot, $"lib.updating-{updateId}");
        var backupDirectory = Path.Combine(launcherRoot, $"lib.previous-{updateId}");

        try
        {
            Directory.CreateDirectory(stagingDirectory);
            foreach (var (name, expectedSha1) in release.Manifest)
            {
                var currentFile = Path.Combine(libDirectory, name);
                var stagingFile = Path.Combine(stagingDirectory, name);
                if (MatchesSha1(currentFile, expectedSha1))
                {
                    File.Copy(currentFile, stagingFile);
                    continue;
                }

                writeInfo($"正在更新{name}");
                if (!await DownloadLibrary(
                        apiUrls,
                        release.Version,
                        name,
                        expectedSha1,
                        stagingFile,
                        writeInfo,
                        reportProgress))
                    throw new InvalidDataException($"{name}下载或校验失败");
            }

            ValidateStagingVersion(stagingDirectory, release.Version);

            var switchResult = await LibrarySwitchRecovery.SwitchAsync(
                libDirectory,
                stagingDirectory,
                backupDirectory,
                writeInfo,
                writeWarning,
                promptRecovery);
            if (switchResult == LibrarySwitchResult.LaunchAborted)
            {
                writeInfo("UI库恢复未完成，本次不再启动客户端");
                return UiLibraryUpdateResult.LaunchAborted;
            }
            writeInfo("UI库更新完成");

            if (Directory.Exists(backupDirectory))
            {
                try
                {
                    Directory.Delete(backupDirectory, true);
                }
                catch (Exception exception)
                {
                    writeWarning($"旧UI库清理失败: {exception.Message}");
                }
            }
            return UiLibraryUpdateResult.Updated;
        }
        catch (Exception exception)
        {
            writeWarning($"UI库更新失败，将使用本地版本启动: {exception.Message}");
            return UiLibraryUpdateResult.LocalVersionAvailable;
        }
        finally
        {
            try
            {
                if (Directory.Exists(stagingDirectory))
                    Directory.Delete(stagingDirectory, true);
            }
            catch (Exception exception)
            {
                writeWarning($"更新临时文件清理失败: {exception.Message}");
            }
        }
    }

    private static async Task<string?> ResolveBackupUrl(
        string primaryUrl,
        string? playerIpv4,
        Action<string> writeInfo)
    {
        try
        {
            using var client = CreateMetadataClient();
            var serverEntryUrl = $"{primaryUrl.TrimEnd('/')}/server-entry";
            if (!string.IsNullOrWhiteSpace(playerIpv4))
                serverEntryUrl += $"?myIp={Uri.EscapeDataString(playerIpv4)}";
            var response = await client.GetFromJsonAsync<ApiResponse<ServerEntry>>(
                serverEntryUrl,
                JsonOptions);
            var api = response?.Code == 0 ? response.Data?.Api : null;
            if (string.IsNullOrWhiteSpace(api))
                return null;

            api = api.Trim();
            var backupUrl = api.Contains("://", StringComparison.Ordinal) ? api : $"https://{api}";
            writeInfo($"将优先使用加速API，非常快");
            return backupUrl;
        }
        catch (Exception exception)
        {
            writeInfo($"获取加速API失败，继续使用默认API: {exception.Message}");
            return null;
        }
    }

    private static async Task<UiLibraryRelease?> GetRelease(
        IEnumerable<string> apiUrls,
        Action<string> writeInfo)
    {
        var versionedRelease = await GetVersionedRelease(apiUrls, writeInfo);
        if (versionedRelease is not null)
            return versionedRelease;

        writeInfo("服务端不支持版本化UI库接口，使用兼容接口");
        var legacyManifest = await GetManifest(apiUrls, writeInfo);
        return legacyManifest is not null && IsValidManifest(legacyManifest)
            ? new UiLibraryRelease(null, legacyManifest)
            : null;
    }

    private static async Task<UiLibraryRelease?> GetVersionedRelease(
        IEnumerable<string> apiUrls,
        Action<string> writeInfo)
    {
        foreach (var apiUrl in apiUrls)
        {
            try
            {
                using var client = CreateMetadataClient();
                var versionResponse = await client.GetFromJsonAsync<ApiResponse<string>>(
                    $"{apiUrl}/update/ui/ver",
                    JsonOptions);
                if (versionResponse is not { Code: 0, Data: not null })
                {
                    writeInfo("API未返回UI版本信息，尝试下一个API");
                    continue;
                }

                var version = versionResponse.Data.Trim();
                if (!UiVersionRegex().IsMatch(version))
                {
                    writeInfo("API返回了无效的UI版本，尝试下一个API");
                    continue;
                }

                var manifestResponse = await client.GetFromJsonAsync<ApiResponse<Dictionary<string, string>>>(
                    $"{apiUrl}/update/ui/libs/{Uri.EscapeDataString(version)}",
                    JsonOptions);
                if (manifestResponse is not { Code: 0, Data: not null } ||
                    !IsValidVersionedManifest(manifestResponse.Data))
                {
                    writeInfo($"UI版本{version}的manifest无效，尝试下一个API");
                    continue;
                }

                return new UiLibraryRelease(version, manifestResponse.Data);
            }
            catch (Exception exception)
            {
                writeInfo($"获取版本化UI库信息失败，尝试下一个API: {exception.Message}");
            }
        }

        return null;
    }

    private static async Task<Dictionary<string, string>?> GetManifest(
        IEnumerable<string> apiUrls,
        Action<string> writeInfo)
    {
        foreach (var apiUrl in apiUrls)
        {
            try
            {
                using var client = CreateMetadataClient();
                var response = await client.GetFromJsonAsync<ApiResponse<Dictionary<string, string>>>(
                    $"{apiUrl}/update/ui/libs",
                    JsonOptions);
                if (response is { Code: 0, Data: not null })
                    return response.Data;
                writeInfo($"API未返回UI库信息");
            }
            catch (Exception exception)
            {
                writeInfo($"获取UI库信息失败，尝试下一个API: {exception.Message}");
            }
        }
        return null;
    }

    private static HttpClient CreateMetadataClient() => new()
    {
        Timeout = TimeSpan.FromSeconds(5)
    };

    private static bool IsValidManifest(Dictionary<string, string> manifest) =>
        manifest.Count > 0 &&
        manifest.Keys.Any(name => name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) &&
        manifest.All(entry =>
            !string.IsNullOrWhiteSpace(entry.Key) &&
            entry.Key is not "." and not ".." &&
            Path.GetFileName(entry.Key) == entry.Key &&
            entry.Key.IndexOfAny(Path.GetInvalidFileNameChars()) < 0 &&
            !string.IsNullOrWhiteSpace(entry.Value) &&
            Sha1Regex().IsMatch(entry.Value));

    private static bool IsValidVersionedManifest(Dictionary<string, string> manifest) =>
        IsValidManifest(manifest) && manifest.ContainsKey("rdi-ui.jar");

    private static bool IsCurrent(string libDirectory, UiLibraryRelease release)
    {
        try
        {
            if (!Directory.Exists(libDirectory))
                return false;
            if (release.Version is { } targetVersion &&
                !string.Equals(ReadInstalledVersion(libDirectory), targetVersion, StringComparison.Ordinal))
                return false;

            var entries = Directory.EnumerateFileSystemEntries(libDirectory).ToArray();
            return entries.Length == release.Manifest.Count && release.Manifest.All(entry =>
            {
                var path = Path.Combine(libDirectory, entry.Key);
                return MatchesSha1(path, entry.Value);
            });
        }
        catch
        {
            return false;
        }
    }

    private static string? ReadInstalledVersion(string libDirectory)
    {
        try
        {
            var jarPath = Path.Combine(libDirectory, "rdi-ui.jar");
            using var archive = ZipFile.OpenRead(jarPath);
            var manifestEntry = archive.Entries.FirstOrDefault(entry =>
                entry.FullName.Equals("META-INF/MANIFEST.MF", StringComparison.OrdinalIgnoreCase));
            if (manifestEntry is null)
                return null;

            using var reader = new StreamReader(manifestEntry.Open());
            const string versionPrefix = "Implementation-Version:";
            foreach (var line in reader.ReadToEnd().Split('\n'))
            {
                if (!line.StartsWith(versionPrefix, StringComparison.OrdinalIgnoreCase))
                    continue;

                var version = line[versionPrefix.Length..].Trim();
                return UiVersionRegex().IsMatch(version) ? version : null;
            }
        }
        catch
        {
        }

        return null;
    }

    private static void ValidateStagingVersion(string stagingDirectory, string? expectedVersion)
    {
        if (expectedVersion is null)
            return;

        var actualVersion = ReadInstalledVersion(stagingDirectory);
        if (!string.Equals(actualVersion, expectedVersion, StringComparison.Ordinal))
            throw new InvalidDataException($"暂存UI库版本不匹配，目标为{expectedVersion}，实际为{actualVersion ?? "未知"}");
    }

    private static bool MatchesSha1(string path, string expectedSha1)
    {
        try
        {
            return File.Exists(path) && Sha1(path).Equals(expectedSha1, StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return false;
        }
    }

    private static async Task<bool> DownloadLibrary(
        IEnumerable<string> apiUrls,
        string? version,
        string name,
        string expectedSha1,
        string targetPath,
        Action<string> writeInfo,
        Action<FileDownloadProgress> reportProgress)
    {
        foreach (var apiUrl in apiUrls)
        {
            try
            {
                File.Delete(targetPath);
                var escapedName = Uri.EscapeDataString(name);
                var downloadUrl = version is null
                    ? $"{apiUrl}/update/ui/lib/{escapedName}"
                    : $"{apiUrl}/update/ui/lib/{Uri.EscapeDataString(version)}/{escapedName}";
                await Download.FileAsync(
                    downloadUrl,
                    targetPath,
                    reportProgress: reportProgress,
                    reportStatus: writeInfo);
                if (Sha1(targetPath).Equals(expectedSha1, StringComparison.OrdinalIgnoreCase))
                    return true;
                writeInfo($"{name}校验失败，尝试下一个API");
            }
            catch (Exception exception)
            {
                writeInfo($"{name}下载失败，尝试下一个API: {exception.Message}");
            }
        }
        File.Delete(targetPath);
        return false;
    }

    private static string Sha1(string path)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexString(SHA1.HashData(stream)).ToLowerInvariant();
    }

    [GeneratedRegex("^[A-Za-z0-9][A-Za-z0-9._-]*$")]
    private static partial Regex UiVersionRegex();

    [GeneratedRegex("^[0-9a-fA-F]{40}$")]
    private static partial Regex Sha1Regex();

}

internal sealed record UiLibraryRelease(
    string? Version,
    Dictionary<string, string> Manifest);

internal sealed record ApiResponse<T>(int Code, string Msg, T? Data);
internal sealed record ServerEntry(string? Api);

[JsonSourceGenerationOptions(JsonSerializerDefaults.Web)]
[JsonSerializable(typeof(ApiResponse<ServerEntry>))]
[JsonSerializable(typeof(ApiResponse<string>))]
[JsonSerializable(typeof(ApiResponse<Dictionary<string, string>>))]
internal partial class UpdaterJsonContext : JsonSerializerContext
{
}
