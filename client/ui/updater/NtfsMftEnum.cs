using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

internal static class Program
{
    private const uint GENERIC_READ  = 0x80000000;
    private const uint GENERIC_WRITE = 0x40000000;

    private const uint FILE_SHARE_READ   = 0x00000001;
    private const uint FILE_SHARE_WRITE  = 0x00000002;
    private const uint FILE_SHARE_DELETE = 0x00000004;

    private const uint OPEN_EXISTING = 3;

    // CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 44, METHOD_NEITHER, FILE_ANY_ACCESS)
    private const uint FSCTL_ENUM_USN_DATA = 0x000900B3;

    private const int ERROR_HANDLE_EOF = 38;
    private const uint FILE_ATTRIBUTE_DIRECTORY = 0x00000010;

    // USN_RECORD_V2 fixed portion is 60 bytes.
    private const int USN_RECORD_V2_MIN_SIZE = 60;

    [StructLayout(LayoutKind.Sequential)]
    private struct MftEnumData
    {
        public ulong StartFileReferenceNumber;
        public long LowUsn;
        public long HighUsn;
    }

    private readonly record struct DirectoryEntry(
        ulong ParentReference,
        string Name
    );

    private readonly record struct FileMatch(
        ulong ParentReference,
        string Name
    );

    [DllImport(
        "kernel32.dll",
        EntryPoint = "CreateFileW",
        CharSet = CharSet.Unicode,
        SetLastError = true
    )]
    private static extern SafeFileHandle CreateFile(
        string fileName,
        uint desiredAccess,
        uint shareMode,
        IntPtr securityAttributes,
        uint creationDisposition,
        uint flagsAndAttributes,
        IntPtr templateFile
    );

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DeviceIoControl(
        SafeFileHandle device,
        uint ioControlCode,
        ref MftEnumData inputBuffer,
        int inputBufferSize,
        byte[] outputBuffer,
        int outputBufferSize,
        out int bytesReturned,
        IntPtr overlapped
    );

    public static int Main()
    {
        if (!OperatingSystem.IsWindows())
        {
            Console.Error.WriteLine("This program only works on Windows.");
            return 1;
        }

        var results = new HashSet<string>(
            StringComparer.OrdinalIgnoreCase
        );

        foreach (DriveInfo drive in DriveInfo.GetDrives())
        {
            try
            {
                if (!drive.IsReady ||
                    !string.Equals(
                        drive.DriveFormat,
                        "NTFS",
                        StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                if (drive.Name.Length < 2 || drive.Name[1] != ':')
                    continue;

                char driveLetter = char.ToUpperInvariant(drive.Name[0]);

                Console.Error.WriteLine(
                    $"Scanning {driveLetter}: MFT..."
                );

                foreach (string path in FindJavaExecutables(driveLetter))
                    results.Add(path);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(
                    $"{drive.Name}: {ex.Message}"
                );
            }
        }

        foreach (string path in results.OrderBy(
                     static path => path,
                     StringComparer.OrdinalIgnoreCase))
        {
            Console.WriteLine(path);
        }

        Console.Error.WriteLine(
            $"Found {results.Count} java.exe file(s)."
        );

        return 0;
    }

    private static IEnumerable<string> FindJavaExecutables(
        char driveLetter)
    {
        string volumePath = $@"\\.\{driveLetter}:";

        using SafeFileHandle volume = CreateFile(
            volumePath,
            GENERIC_READ | GENERIC_WRITE,
            FILE_SHARE_READ |
            FILE_SHARE_WRITE |
            FILE_SHARE_DELETE,
            IntPtr.Zero,
            OPEN_EXISTING,
            0,
            IntPtr.Zero
        );

        if (volume.IsInvalid)
        {
            throw new Win32Exception(
                Marshal.GetLastWin32Error(),
                $"Could not open {volumePath}. Run as administrator."
            );
        }

        var directories =
            new Dictionary<ulong, DirectoryEntry>();

        var matches = new List<FileMatch>();

        var enumData = new MftEnumData
        {
            StartFileReferenceNumber = 0,
            LowUsn = 0,
            HighUsn = long.MaxValue
        };

        // Larger buffers reduce the number of DeviceIoControl calls.
        byte[] buffer = new byte[1024 * 1024];
        int inputSize = Marshal.SizeOf<MftEnumData>();

        while (true)
        {
            bool success = DeviceIoControl(
                volume,
                FSCTL_ENUM_USN_DATA,
                ref enumData,
                inputSize,
                buffer,
                buffer.Length,
                out int bytesReturned,
                IntPtr.Zero
            );

            if (!success)
            {
                int error = Marshal.GetLastWin32Error();

                if (error == ERROR_HANDLE_EOF)
                    break;

                throw new Win32Exception(
                    error,
                    $"FSCTL_ENUM_USN_DATA failed on {driveLetter}:"
                );
            }

            if (bytesReturned < sizeof(ulong))
                break;

            // The first 8 bytes contain the starting FRN
            // for the next DeviceIoControl call.
            ulong nextReference =
                BitConverter.ToUInt64(buffer, 0);

            int offset = sizeof(ulong);

            while (offset + USN_RECORD_V2_MIN_SIZE <= bytesReturned)
            {
                uint recordLength =
                    BitConverter.ToUInt32(buffer, offset);

                if (recordLength < USN_RECORD_V2_MIN_SIZE ||
                    offset + (long)recordLength > bytesReturned)
                {
                    break;
                }

                ushort majorVersion =
                    BitConverter.ToUInt16(buffer, offset + 4);

                if (majorVersion == 2)
                {
                    ProcessUsnRecordV2(
                        buffer,
                        offset,
                        recordLength,
                        directories,
                        matches
                    );
                }

                offset += checked((int)recordLength);
            }

            if (nextReference <= enumData.StartFileReferenceNumber)
                break;

            enumData.StartFileReferenceNumber = nextReference;
        }

        foreach (FileMatch match in matches)
        {
            string? path = BuildPath(
                driveLetter,
                match,
                directories
            );

            if (path is not null)
                yield return path;
        }
    }

    private static void ProcessUsnRecordV2(
        byte[] buffer,
        int offset,
        uint recordLength,
        Dictionary<ulong, DirectoryEntry> directories,
        List<FileMatch> matches)
    {
        // USN_RECORD_V2 layout:
        //  8: FileReferenceNumber
        // 16: ParentFileReferenceNumber
        // 52: FileAttributes
        // 56: FileNameLength
        // 58: FileNameOffset

        ulong fileReference =
            BitConverter.ToUInt64(buffer, offset + 8);

        ulong parentReference =
            BitConverter.ToUInt64(buffer, offset + 16);

        uint attributes =
            BitConverter.ToUInt32(buffer, offset + 52);

        ushort fileNameLength =
            BitConverter.ToUInt16(buffer, offset + 56);

        ushort fileNameOffset =
            BitConverter.ToUInt16(buffer, offset + 58);

        if ((uint)fileNameOffset + fileNameLength > recordLength ||
            (fileNameLength & 1) != 0)
        {
            return;
        }

        string name = Encoding.Unicode.GetString(
            buffer,
            offset + fileNameOffset,
            fileNameLength
        );

        bool isDirectory =
            (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0;

        if (isDirectory)
        {
            directories[fileReference] =
                new DirectoryEntry(parentReference, name);

            return;
        }

        if (string.Equals(
                name,
                "java.exe",
                StringComparison.OrdinalIgnoreCase))
        {
            matches.Add(
                new FileMatch(parentReference, name)
            );
        }
    }

    private static string? BuildPath(
        char driveLetter,
        FileMatch file,
        Dictionary<ulong, DirectoryEntry> directories)
    {
        var parts = new List<string> { file.Name };
        var visited = new HashSet<ulong>();

        ulong current = file.ParentReference;

        while (visited.Add(current))
        {
            if (!directories.TryGetValue(
                    current,
                    out DirectoryEntry directory))
            {
                // Parent record could not be resolved.
                return null;
            }

            // The NTFS root record commonly has "." as its name
            // and references itself as its parent.
            if (!string.IsNullOrEmpty(directory.Name) &&
                directory.Name != ".")
            {
                parts.Add(directory.Name);
            }

            if (directory.ParentReference == current)
                break;

            current = directory.ParentReference;
        }

        parts.Reverse();

        return $@"{driveLetter}:\" +
               string.Join('\\', parts);
    }
}