using System.Text;

namespace installer;

internal static class Program
{
    [STAThread]
    private static int Main() => RunAsync().GetAwaiter().GetResult();

    private static async Task<int> RunAsync()
    {
        Console.OutputEncoding = Encoding.UTF8;

        try
        {
            var defaultPath = InstallPathSelector.SelectDefaultPath();
            var installPath = SelectInstallPath(defaultPath);
            var createDesktopShortcut = AskYesNo("要创建桌面图标吗？", "不创建");
            var createStartMenuShortcut = AskYesNo("要创建开始菜单图标吗？", "不创建");

            Console.WriteLine();
            Console.WriteLine($"开始安装到{installPath}");
            var result = await InstallerService.InstallAsync(
                installPath,
                createDesktopShortcut,
                createStartMenuShortcut,
                new ConsoleProgress()
            );

            foreach (var warning in result.Warnings) Console.WriteLine($"warning:{warning}");
            Console.WriteLine("安装完成");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"安装失败:{exception.Message}");
            return 1;
        }
    }

    private static bool AskYesNo(string question, string noAction)
    {
        while (true)
        {
            Console.WriteLine(question);
            Console.WriteLine($"按Y同意 按N{noAction}");
            switch (Console.ReadKey(true).Key)
            {
                case ConsoleKey.Y:
                    Console.WriteLine("Y");
                    return true;
                case ConsoleKey.N:
                    Console.WriteLine("N");
                    return false;
                default:
                    Console.WriteLine("请按Y或N。");
                    break;
            }
        }
    }

    private static string SelectInstallPath(string defaultPath)
    {
        while (true)
        {
            if (AskYesNo($"要安装到{defaultPath}吗？", "选择其他文件夹")) return defaultPath;

            var selectedPath = FolderPicker.Select(defaultPath);
            if (selectedPath is not null)
            {
                Console.WriteLine($"已选择：{selectedPath}");
                return selectedPath;
            }
            Console.WriteLine("未选择安装文件夹。");
        }
    }

    private sealed class ConsoleProgress : IProgress<string>
    {
        public void Report(string value) => Console.WriteLine(value);
    }
}
