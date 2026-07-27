using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;

internal static class Download
{
    public static async Task FileAsync(
        string url,
        string targetPath,
        int maxParallelParts = 8,
        Action<FileDownloadProgress>? reportProgress = null,
        Action<string>? reportStatus = null,
        CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfLessThan(maxParallelParts, 1);

        using var handler = new SocketsHttpHandler
        {
            MaxConnectionsPerServer = maxParallelParts,
            EnableMultipleHttp2Connections = true
        };
        using var client = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };

        using var probeRequest = new HttpRequestMessage(HttpMethod.Get, url);
        probeRequest.Headers.Range = new(0, 0);
        using var probeResponse = await client.SendAsync(
            probeRequest,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        if (probeResponse.StatusCode != HttpStatusCode.PartialContent)
        {
            probeResponse.EnsureSuccessStatusCode();
            await DownloadResponse(probeResponse, targetPath, reportProgress, cancellationToken);
            return;
        }
        if (probeResponse.Content.Headers.ContentRange?.Length is not { } totalBytes)
        {
            await DownloadSingle(client, url, targetPath, reportProgress, cancellationToken);
            return;
        }

        try
        {
            await DownloadInParallel(
                client,
                url,
                targetPath,
                totalBytes,
                maxParallelParts,
                reportProgress,
                reportStatus,
                cancellationToken);
        }
        catch (Exception exception) when (exception is HttpRequestException or InvalidDataException or IOException)
        {
            reportStatus?.Invoke($"并行下载失败，切换为单流下载: {exception.Message}");
            await DownloadSingle(client, url, targetPath, reportProgress, cancellationToken);
        }
    }

    private static async Task DownloadSingle(
        HttpClient client,
        string url,
        string targetPath,
        Action<FileDownloadProgress>? reportProgress,
        CancellationToken cancellationToken)
    {
        using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();
        await DownloadResponse(response, targetPath, reportProgress, cancellationToken);
    }

    private static async Task DownloadResponse(
        HttpResponseMessage response,
        string targetPath,
        Action<FileDownloadProgress>? reportProgress,
        CancellationToken cancellationToken)
    {
        var progress = new ProgressTracker(response.Content.Headers.ContentLength, reportProgress);
        try
        {
            await using var source = await response.Content.ReadAsStreamAsync(cancellationToken);
            await using var target = File.Create(targetPath);
            var downloadedBytes = await CopyStream(source, target, progress, cancellationToken);
            if (response.Content.Headers.ContentLength is { } expectedBytes && downloadedBytes != expectedBytes)
                throw new InvalidDataException($"下载长度不正确，应为{expectedBytes}，实际为{downloadedBytes}");
        }
        finally
        {
            progress.Finish();
        }
    }

    private static async Task DownloadInParallel(
        HttpClient client,
        string url,
        string targetPath,
        long totalBytes,
        int maxParallelParts,
        Action<FileDownloadProgress>? reportProgress,
        Action<string>? reportStatus,
        CancellationToken cancellationToken)
    {
        var partCount = (int)Math.Min(maxParallelParts, totalBytes);
        var partSize = (totalBytes + partCount - 1) / partCount;
        reportStatus?.Invoke($"启动{partCount}线程高速下载");
        var partPaths = Enumerable.Range(0, partCount)
            .Select(index => $"{targetPath}.part-{index}")
            .ToArray();
        var progress = new ProgressTracker(totalBytes, reportProgress);

        try
        {
            await Task.WhenAll(partPaths.Index().Select(part =>
            {
                var start = part.Index * partSize;
                var end = Math.Min(totalBytes - 1, start + partSize - 1);
                return DownloadRange(client, url, part.Item, start, end, progress, cancellationToken);
            }));

            await using var target = File.Create(targetPath);
            foreach (var partPath in partPaths)
            {
                await using var part = File.OpenRead(partPath);
                await part.CopyToAsync(target, cancellationToken);
            }
        }
        finally
        {
            progress.Finish();
            foreach (var partPath in partPaths)
                File.Delete(partPath);
        }
    }

    private static async Task DownloadRange(
        HttpClient client,
        string url,
        string partPath,
        long start,
        long end,
        ProgressTracker progress,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Range = new RangeHeaderValue(start, end);
        using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (response.StatusCode != HttpStatusCode.PartialContent)
            throw new HttpRequestException($"服务器未返回Range数据: {(int)response.StatusCode}");
        if (response.Content.Headers.ContentRange is not { From: var actualStart, To: var actualEnd } ||
            actualStart != start || actualEnd != end)
        {
            throw new InvalidDataException($"服务器返回了错误的Range: {response.Content.Headers.ContentRange}");
        }

        await using var source = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using var target = File.Create(partPath);
        var downloadedBytes = await CopyStream(source, target, progress, cancellationToken);
        if (downloadedBytes != end - start + 1)
            throw new InvalidDataException($"分片长度不正确，应为{end - start + 1}，实际为{downloadedBytes}");
    }

    private static async Task<long> CopyStream(
        Stream source,
        Stream target,
        ProgressTracker progress,
        CancellationToken cancellationToken)
    {
        var buffer = new byte[128 * 1024];
        long downloadedBytes = 0;
        int bytesRead;
        while ((bytesRead = await source.ReadAsync(buffer, cancellationToken)) > 0)
        {
            await target.WriteAsync(buffer.AsMemory(0, bytesRead), cancellationToken);
            progress.Report(bytesRead);
            downloadedBytes += bytesRead;
        }
        return downloadedBytes;
    }

    private sealed class ProgressTracker(long? totalBytes, Action<FileDownloadProgress>? reportProgress)
    {
        private readonly Stopwatch stopwatch = Stopwatch.StartNew();
        private readonly object reportLock = new();
        private long downloadedBytes;
        private long lastReportMilliseconds = -100;

        public void Report(int bytes)
        {
            Interlocked.Add(ref downloadedBytes, bytes);
            Publish(false);
        }

        public void Finish() => Publish(true);

        private void Publish(bool completed)
        {
            lock (reportLock)
            {
                var elapsedMilliseconds = stopwatch.ElapsedMilliseconds;
                if (!completed && elapsedMilliseconds - lastReportMilliseconds < 100)
                    return;

                lastReportMilliseconds = elapsedMilliseconds;
                var downloaded = Interlocked.Read(ref downloadedBytes);
                reportProgress?.Invoke(new(
                    downloaded,
                    totalBytes,
                    downloaded / Math.Max(stopwatch.Elapsed.TotalSeconds, 0.001),
                    completed));
            }
        }
    }
}

internal readonly record struct FileDownloadProgress(
    long DownloadedBytes,
    long? TotalBytes,
    double BytesPerSecond,
    bool Completed);
