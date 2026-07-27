using Microsoft.Win32.SafeHandles;
using System.Runtime.InteropServices;

namespace installer;

internal static partial class InstallPathSelector
{
    private const uint IoctlVolumeGetVolumeDiskExtents = 0x00560000;
    private const uint IoctlStorageQueryProperty = 0x002D1400;
    private const uint FileShareRead = 0x00000001;
    private const uint FileShareWrite = 0x00000002;
    private const uint OpenExisting = 3;
    private const int StorageDeviceSeekPenaltyProperty = 7;

    public static string SelectDefaultPath()
    {
        var drive = DriveInfo.GetDrives()
            .Where(it => it.IsReady && it.DriveType == DriveType.Fixed && !it.Name.StartsWith("C:", StringComparison.OrdinalIgnoreCase))
            .Where(IsSsd)
            .OrderByDescending(it => it.AvailableFreeSpace)
            .ThenBy(it => it.Name, StringComparer.OrdinalIgnoreCase)
            .FirstOrDefault();

        return drive is not null
            ? Path.Combine(drive.RootDirectory.FullName, "mc", "rdi")
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "rdi");
    }

    private static bool IsSsd(DriveInfo drive)
    {
        using var volume = CreateFile(
            $@"\\.\{drive.Name[..2]}",
            0,
            FileShareRead | FileShareWrite,
            0,
            OpenExisting,
            0,
            0
        );
        if (volume.IsInvalid || !GetVolumeDiskExtents(
                volume,
                IoctlVolumeGetVolumeDiskExtents,
                0,
                0,
                out var extents,
                (uint)Marshal.SizeOf<VolumeDiskExtents>(),
                out _,
                0
            ) || extents.NumberOfDiskExtents != 1)
        {
            return false;
        }

        using var physicalDrive = CreateFile(
            $@"\\.\PhysicalDrive{extents.FirstExtent.DiskNumber}",
            0,
            FileShareRead | FileShareWrite,
            0,
            OpenExisting,
            0,
            0
        );
        if (physicalDrive.IsInvalid) return false;

        var query = new StoragePropertyQuery
        {
            PropertyId = StorageDeviceSeekPenaltyProperty,
            QueryType = 0,
        };
        return QueryStorageProperty(
            physicalDrive,
            IoctlStorageQueryProperty,
            in query,
            (uint)Marshal.SizeOf<StoragePropertyQuery>(),
            out var descriptor,
            (uint)Marshal.SizeOf<DeviceSeekPenaltyDescriptor>(),
            out _,
            0
        ) && descriptor.IncursSeekPenalty == 0;
    }

    [LibraryImport("kernel32.dll", EntryPoint = "CreateFileW", SetLastError = true, StringMarshalling = StringMarshalling.Utf16)]
    private static partial SafeFileHandle CreateFile(
        string fileName,
        uint desiredAccess,
        uint shareMode,
        nint securityAttributes,
        uint creationDisposition,
        uint flagsAndAttributes,
        nint templateFile
    );

    [LibraryImport("kernel32.dll", EntryPoint = "DeviceIoControl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool GetVolumeDiskExtents(
        SafeFileHandle device,
        uint controlCode,
        nint inputBuffer,
        uint inputBufferSize,
        out VolumeDiskExtents outputBuffer,
        uint outputBufferSize,
        out uint bytesReturned,
        nint overlapped
    );

    [LibraryImport("kernel32.dll", EntryPoint = "DeviceIoControl", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool QueryStorageProperty(
        SafeFileHandle device,
        uint controlCode,
        in StoragePropertyQuery inputBuffer,
        uint inputBufferSize,
        out DeviceSeekPenaltyDescriptor outputBuffer,
        uint outputBufferSize,
        out uint bytesReturned,
        nint overlapped
    );

    [StructLayout(LayoutKind.Sequential)]
    private struct DiskExtent
    {
        public uint DiskNumber;
        public long StartingOffset;
        public long ExtentLength;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct VolumeDiskExtents
    {
        public uint NumberOfDiskExtents;
        public DiskExtent FirstExtent;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct StoragePropertyQuery
    {
        public int PropertyId;
        public int QueryType;
        public byte AdditionalParameters;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct DeviceSeekPenaltyDescriptor
    {
        public uint Version;
        public uint Size;
        public byte IncursSeekPenalty;
    }
}
