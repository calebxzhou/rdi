namespace installer;

public partial class Form1 : Form
{
    private bool _installing;
    private bool _installationComplete;

    public Form1()
    {
        InitializeComponent();
        installPathTextBox.Text = InstallPathSelector.SelectDefaultPath();
    }

    private void BrowseButton_Click(object? sender, EventArgs eventArgs)
    {
        using var dialog = new FolderBrowserDialog
        {
            Description = "选择rdi安装目录",
            SelectedPath = installPathTextBox.Text,
            ShowNewFolderButton = true,
            UseDescriptionForTitle = true,
        };
        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            installPathTextBox.Text = dialog.SelectedPath;
        }
    }

    private async void InstallButton_Click(object? sender, EventArgs eventArgs)
    {
        if (_installationComplete)
        {
            Close();
            return;
        }

        string installPath;
        try
        {
            if (string.IsNullOrWhiteSpace(installPathTextBox.Text))
            {
                throw new ArgumentException("请选择安装路径");
            }
            installPath = Path.GetFullPath(installPathTextBox.Text.Trim());
            if (Directory.Exists(installPath) && Directory.EnumerateFileSystemEntries(installPath).Any())
            {
                var answer = MessageBox.Show(
                    this,
                    "目标目录已包含文件。继续安装会覆盖同名文件，但保留其他文件。是否继续？",
                    "确认覆盖",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Warning,
                    MessageBoxDefaultButton.Button2
                );
                if (answer != DialogResult.Yes) return;
            }
        }
        catch (Exception exception)
        {
            ShowError("安装路径无效", exception);
            return;
        }

        SetInstalling(true);
        try
        {
            var progress = new Progress<string>(message => statusLabel.Text = message);
            var result = await InstallerService.InstallAsync(
                installPath,
                desktopShortcutCheckBox.Checked,
                startMenuShortcutCheckBox.Checked,
                progress
            );

            _installationComplete = true;
            _installing = false;
            statusLabel.Text = "安装完成";
            installProgressBar.Visible = false;
            installButton.Enabled = true;
            installButton.Text = "关闭";
            if (result.Warnings.Count > 0)
            {
                MessageBox.Show(
                    this,
                    string.Join(Environment.NewLine, result.Warnings),
                    "安装完成，但存在warning",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning
                );
            }
        }
        catch (Exception exception)
        {
            statusLabel.Text = "安装失败";
            ShowError("安装失败", exception);
            SetInstalling(false);
        }
    }

    private void SetInstalling(bool installing)
    {
        _installing = installing;
        installPathTextBox.Enabled = !installing;
        browseButton.Enabled = !installing;
        desktopShortcutCheckBox.Enabled = !installing;
        startMenuShortcutCheckBox.Enabled = !installing;
        installButton.Enabled = !installing;
        installProgressBar.Visible = installing;
        if (installing) statusLabel.Text = "正在准备安装";
    }

    private void Form1_FormClosing(object? sender, FormClosingEventArgs eventArgs)
    {
        if (!_installing) return;
        eventArgs.Cancel = true;
        System.Media.SystemSounds.Exclamation.Play();
    }

    private void ShowError(string title, Exception exception)
    {
        MessageBox.Show(
            this,
            $"{title}:{Environment.NewLine}{exception.Message}",
            title,
            MessageBoxButtons.OK,
            MessageBoxIcon.Error
        );
    }
}
