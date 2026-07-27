using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

internal static class NtfsMftEnum
{
    private const uint GenericRead = 0x80000000;
    private const uint FileShareRead = 0x00000001;
    private const uint FileShareWrite = 0x00000002;
    private const uint FileShareDelete = 0x00000004;
    private const uint OpenExisting = 3;
    private const uint FileFlagBackupSemantics = 0x02000000;
    private const uint FsctlEnumUsnData = 0x000900B3;
    private const int ErrorHandleEof = 38;
    private const int UsnRecordV2MinSize = 60;
    private const int MaximumPathLength = 32768;

    [StructLayout(LayoutKind.Sequential)]
    private struct MftEnumDataV0
    {
        public ulong StartFileReferenceNumber;
        public long LowUsn;
        public long HighUsn;
    }

    private enum FileIdType
    {
        FileId
    }

    [StructLayout(LayoutKind.Explicit, Size = 24)]
    private struct FileIdDescriptor
    {
        [FieldOffset(0)] public uint Size;
        [FieldOffset(4)] public FileIdType Type;
        [FieldOffset(8)] public long FileId;
    }

    private sealed record DriveScanResult(HashSet<string> Paths, long Records, int Matches, int UnresolvedPaths);

    [DllImport("kernel32.dll", EntryPoint = "CreateFileW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFile(
        string fileName,
        uint desiredAccess,
        uint shareMode,
        nint securityAttributes,
        uint creationDisposition,
        uint flagsAndAttributes,
        nint templateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DeviceIoControl(
        SafeFileHandle device,
        uint ioControlCode,
        ref MftEnumDataV0 inputBuffer,
        int inputBufferSize,
        byte[] outputBuffer,
        int outputBufferSize,
        out int bytesReturned,
        nint overlapped);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern SafeFileHandle OpenFileById(
        SafeFileHandle volumeHint,
        ref FileIdDescriptor fileId,
        uint desiredAccess,
        uint shareMode,
        nint securityAttributes,
        uint flagsAndAttributes);

    [DllImport("kernel32.dll", EntryPoint = "GetFinalPathNameByHandleW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern uint GetFinalPathNameByHandle(
        SafeFileHandle file,
        StringBuilder filePath,
        int filePathLength,
        uint flags);

    public static MftEnumerationResult FindJavaExecutables(Action<string>? onPathFound = null)
    {
        HashSet<string> paths = new(StringComparer.OrdinalIgnoreCase);
        List<string> diagnostics = [];
        if (!OperatingSystem.IsWindows())
            return new(paths, ["MFT搜索仅支持Windows"]);

        foreach (var drive in DriveInfo.GetDrives())
        {
            var stopwatch = Stopwatch.StartNew();
            try
            {
                if (!drive.IsReady || !drive.DriveFormat.Equals("NTFS", StringComparison.OrdinalIgnoreCase))
                    continue;
                if (drive.Name is not [var driveLetter, ':', ..])
                    continue;

                var result = FindJavaExecutablesOnDrive(char.ToUpperInvariant(driveLetter), onPathFound);
                paths.UnionWith(result.Paths);
                diagnostics.Add(
                    $"MFT[{drive.Name}]耗时{stopwatch.Elapsed.TotalSeconds:F2}秒，V2={result.Records}，java.exe={result.Matches}，路径成功={result.Paths.Count}，路径失败={result.UnresolvedPaths}");
            }
            catch (Win32Exception exception)
            {
                diagnostics.Add($"MFT[{drive.Name}]失败，Win32={exception.NativeErrorCode}: {exception.Message}");
            }
            catch (Exception exception)
            {
                diagnostics.Add($"MFT[{drive.Name}]失败: {exception.Message}");
            }
        }

        return new(paths, diagnostics);
    }

    private static DriveScanResult FindJavaExecutablesOnDrive(char driveLetter, Action<string>? onPathFound)
    {
        var volumePath = $@"\\.\{driveLetter}:";
        using var volume = CreateFile(
            volumePath,
            GenericRead,
            FileShareRead | FileShareWrite | FileShareDelete,
            nint.Zero,
            OpenExisting,
            0,
            nint.Zero);
        if (volume.IsInvalid)
            throw new Win32Exception(Marshal.GetLastWin32Error(), $"无法以只读方式打开{volumePath}");

        HashSet<string> paths = new(StringComparer.OrdinalIgnoreCase);
        var enumData = new MftEnumDataV0
        {
            LowUsn = 0,
            HighUsn = long.MaxValue
        };
        var buffer = new byte[1024 * 1024];
        var inputSize = Marshal.SizeOf<MftEnumDataV0>();
        long records = 0;
        var matches = 0;
        var unresolvedPaths = 0;

        while (true)
        {
            var success = DeviceIoControl(
                volume,
                FsctlEnumUsnData,
                ref enumData,
                inputSize,
                buffer,
                buffer.Length,
                out var bytesReturned,
                nint.Zero);
            if (!success)
            {
                var error = Marshal.GetLastWin32Error();
                if (error == ErrorHandleEof)
                    break;
                throw new Win32Exception(error, $"FSCTL_ENUM_USN_DATA读取{driveLetter}:失败");
            }
            if (bytesReturned < sizeof(ulong))
                break;

            var nextReference = BitConverter.ToUInt64(buffer, 0);
            var offset = sizeof(ulong);
            while (offset + UsnRecordV2MinSize <= bytesReturned)
            {
                var recordLength = BitConverter.ToUInt32(buffer, offset);
                if (recordLength < UsnRecordV2MinSize || offset + (long)recordLength > bytesReturned)
                    break;

                if (BitConverter.ToUInt16(buffer, offset + 4) == 2)
                {
                    records++;
                    if (GetJavaFileReference(buffer, offset, recordLength) is { } fileReference)
                    {
                        matches++;
                        if (ResolvePath(volume, fileReference) is not { } path)
                            unresolvedPaths++;
                        else if (paths.Add(path))
                            onPathFound?.Invoke(path);
                    }
                }
                offset += checked((int)recordLength);
            }

            if (nextReference <= enumData.StartFileReferenceNumber)
                break;
            enumData.StartFileReferenceNumber = nextReference;
        }

        return new(paths, records, matches, unresolvedPaths);
    }

    private static ulong? GetJavaFileReference(byte[] buffer, int offset, uint recordLength)
    {
        var fileNameLength = BitConverter.ToUInt16(buffer, offset + 56);
        var fileNameOffset = BitConverter.ToUInt16(buffer, offset + 58);
        if ((uint)fileNameOffset + fileNameLength > recordLength || (fileNameLength & 1) != 0)
            return null;

        var name = Encoding.Unicode.GetString(buffer, offset + fileNameOffset, fileNameLength);
        return name.Equals("java.exe", StringComparison.OrdinalIgnoreCase)
            ? BitConverter.ToUInt64(buffer, offset + 8)
            : null;
    }

    private static string? ResolvePath(SafeFileHandle volume, ulong fileReference)
    {
        var fileId = new FileIdDescriptor
        {
            Size = (uint)Marshal.SizeOf<FileIdDescriptor>(),
            Type = FileIdType.FileId,
            FileId = unchecked((long)fileReference)
        };
        using var file = OpenFileById(
            volume,
            ref fileId,
            0,
            FileShareRead | FileShareWrite | FileShareDelete,
            nint.Zero,
            FileFlagBackupSemantics);
        if (file.IsInvalid)
            return null;

        var path = new StringBuilder(MaximumPathLength);
        var length = GetFinalPathNameByHandle(file, path, path.Capacity, 0);
        if (length is 0 || length >= path.Capacity)
            return null;

        const string extendedPathPrefix = @"\\?\";
        return path.ToString() is var resolvedPath && resolvedPath.StartsWith(extendedPathPrefix, StringComparison.Ordinal)
            ? resolvedPath[extendedPathPrefix.Length..]
            : resolvedPath;
    }
}

internal sealed record MftEnumerationResult(HashSet<string> Paths, List<string> Diagnostics);
