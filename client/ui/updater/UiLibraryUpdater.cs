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

    public static async Task TryUpdateAsync(
        string primaryUrl,
        string launcherRoot,
        Action<string> writeInfo,
        Action<string> writeWarning,
        Action<FileDownloadProgress> reportProgress,
        bool useBackupApi,
        string? playerIpv4)
    {
        var backupUrl = useBackupApi ? await ResolveBackupUrl(primaryUrl, playerIpv4, writeInfo) : null;
        var apiUrls = new[] { backupUrl, primaryUrl }
            .Where(url => !string.IsNullOrWhiteSpace(url))
            .Select(url => url!.TrimEnd('/'))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();

        var manifest = await GetManifest(apiUrls, writeInfo);
        if (manifest is null)
        {
            writeWarning("无法获取UI库信息，将使用本地版本启动");
            return;
        }
        if (!IsValidManifest(manifest))
        {
            writeWarning("UI库信息为空或无效，将使用本地版本启动");
            return;
        }

        var libDirectory = Path.Combine(launcherRoot, "lib");
        if (IsCurrent(libDirectory, manifest))
        {
            writeInfo("UI库已是最新版本");
            return;
        }

        var updateId = Guid.NewGuid().ToString("N");
        var stagingDirectory = Path.Combine(launcherRoot, $"lib.updating-{updateId}");
        var backupDirectory = Path.Combine(launcherRoot, $"lib.previous-{updateId}");

        try
        {
            Directory.CreateDirectory(stagingDirectory);
            foreach (var (name, expectedSha1) in manifest)
            {
                var currentFile = Path.Combine(libDirectory, name);
                var stagingFile = Path.Combine(stagingDirectory, name);
                if (MatchesSha1(currentFile, expectedSha1))
                {
                    File.Copy(currentFile, stagingFile);
                    continue;
                }

                writeInfo($"正在更新{name}");
                if (!await DownloadLibrary(apiUrls, name, expectedSha1, stagingFile, writeInfo, reportProgress))
                    throw new InvalidDataException($"{name}下载或校验失败");
            }

            SwitchLibraryDirectory(libDirectory, stagingDirectory, backupDirectory);
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
        }
        catch (Exception exception)
        {
            writeWarning($"UI库更新失败，将使用本地版本启动: {exception.Message}");
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

    private static bool IsCurrent(string libDirectory, Dictionary<string, string> manifest)
    {
        try
        {
            if (!Directory.Exists(libDirectory))
                return false;
            var entries = Directory.EnumerateFileSystemEntries(libDirectory).ToArray();
            return entries.Length == manifest.Count && manifest.All(entry =>
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
                await Download.FileAsync(
                    $"{apiUrl}/update/ui/lib/{Uri.EscapeDataString(name)}",
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

    private static void SwitchLibraryDirectory(string libDirectory, string stagingDirectory, string backupDirectory)
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

    private static string Sha1(string path)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexString(SHA1.HashData(stream)).ToLowerInvariant();
    }

    [GeneratedRegex("^[0-9a-fA-F]{40}$")]
    private static partial Regex Sha1Regex();

}

internal sealed record ApiResponse<T>(int Code, string Msg, T? Data);
internal sealed record ServerEntry(string? Api);

[JsonSourceGenerationOptions(JsonSerializerDefaults.Web)]
[JsonSerializable(typeof(ApiResponse<ServerEntry>))]
[JsonSerializable(typeof(ApiResponse<Dictionary<string, string>>))]
internal partial class UpdaterJsonContext : JsonSerializerContext
{
}
