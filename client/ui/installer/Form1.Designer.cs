#nullable enable

namespace installer;

partial class Form1
{
    private Label titleLabel = null!;
    private Label pathLabel = null!;
    private TextBox installPathTextBox = null!;
    private Button browseButton = null!;
    private CheckBox desktopShortcutCheckBox = null!;
    private CheckBox startMenuShortcutCheckBox = null!;
    private ProgressBar installProgressBar = null!;
    private Label statusLabel = null!;
    private Button installButton = null!;

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
    }

    private void InitializeComponent()
    {
        var rootPanel = new FlowLayoutPanel();
        var pathPanel = new FlowLayoutPanel();
        var shortcutPanel = new FlowLayoutPanel();
        var footerPanel = new FlowLayoutPanel();
        titleLabel = new Label();
        pathLabel = new Label();
        installPathTextBox = new TextBox();
        browseButton = new Button();
        desktopShortcutCheckBox = new CheckBox();
        startMenuShortcutCheckBox = new CheckBox();
        installProgressBar = new ProgressBar();
        statusLabel = new Label();
        installButton = new Button();
        rootPanel.SuspendLayout();
        pathPanel.SuspendLayout();
        shortcutPanel.SuspendLayout();
        footerPanel.SuspendLayout();
        SuspendLayout();

        rootPanel.AutoScroll = true;
        rootPanel.Dock = DockStyle.Fill;
        rootPanel.FlowDirection = FlowDirection.TopDown;
        rootPanel.Padding = new Padding(28, 24, 28, 20);
        rootPanel.WrapContents = false;

        titleLabel.AutoSize = true;
        titleLabel.Font = new Font("Microsoft YaHei UI", 15F, FontStyle.Bold);
        titleLabel.Margin = new Padding(0, 0, 0, 4);
        titleLabel.Text = "安装rdi客户端";

        pathLabel.AutoSize = true;
        pathLabel.Margin = new Padding(3, 0, 0, 8);
        pathLabel.Text = "装到哪？";

        pathPanel.AutoSize = true;
        pathPanel.AutoSizeMode = AutoSizeMode.GrowAndShrink;
        pathPanel.FlowDirection = FlowDirection.LeftToRight;
        pathPanel.Margin = new Padding(0, 0, 0, 16);
        pathPanel.WrapContents = false;

        installPathTextBox.Margin = new Padding(3, 1, 9, 1);
        installPathTextBox.Size = new Size(620, 27);
        installPathTextBox.TabIndex = 0;

        browseButton.Margin = new Padding(0);
        browseButton.Size = new Size(100, 35);
        browseButton.TabIndex = 1;
        browseButton.Text = "浏览…";
        browseButton.UseVisualStyleBackColor = true;
        browseButton.Click += BrowseButton_Click;

        shortcutPanel.AutoSize = true;
        shortcutPanel.AutoSizeMode = AutoSizeMode.GrowAndShrink;
        shortcutPanel.FlowDirection = FlowDirection.LeftToRight;
        shortcutPanel.Margin = new Padding(0, 0, 0, 18);
        shortcutPanel.WrapContents = false;

        desktopShortcutCheckBox.AutoSize = true;
        desktopShortcutCheckBox.Checked = true;
        desktopShortcutCheckBox.CheckState = CheckState.Checked;
        desktopShortcutCheckBox.Margin = new Padding(3, 0, 28, 0);
        desktopShortcutCheckBox.TabIndex = 2;
        desktopShortcutCheckBox.Text = "创建桌面图标";
        desktopShortcutCheckBox.UseVisualStyleBackColor = true;

        startMenuShortcutCheckBox.AutoSize = true;
        startMenuShortcutCheckBox.Checked = true;
        startMenuShortcutCheckBox.CheckState = CheckState.Checked;
        startMenuShortcutCheckBox.Margin = new Padding(0);
        startMenuShortcutCheckBox.TabIndex = 3;
        startMenuShortcutCheckBox.Text = "创建开始菜单图标";
        startMenuShortcutCheckBox.UseVisualStyleBackColor = true;

        installProgressBar.Margin = new Padding(3, 0, 0, 9);
        installProgressBar.MarqueeAnimationSpeed = 28;
        installProgressBar.Size = new Size(710, 7);
        installProgressBar.Style = ProgressBarStyle.Marquee;
        installProgressBar.Visible = false;

        statusLabel.AutoEllipsis = true;
        statusLabel.ForeColor = SystemColors.GrayText;
        statusLabel.Margin = new Padding(0, 6, 12, 0);
        statusLabel.Size = new Size(600, 30);
        statusLabel.Text = "准备安装";
        statusLabel.TextAlign = ContentAlignment.MiddleLeft;

        footerPanel.FlowDirection = FlowDirection.RightToLeft;
        footerPanel.Margin = new Padding(0);
        footerPanel.Size = new Size(716, 42);
        footerPanel.WrapContents = false;

        installButton.Margin = new Padding(0);
        installButton.Size = new Size(120, 36);
        installButton.TabIndex = 4;
        installButton.Text = "开始安装";
        installButton.UseVisualStyleBackColor = true;
        installButton.Click += InstallButton_Click;

        pathPanel.Controls.Add(installPathTextBox);
        pathPanel.Controls.Add(browseButton);
        shortcutPanel.Controls.Add(desktopShortcutCheckBox);
        shortcutPanel.Controls.Add(startMenuShortcutCheckBox);
        footerPanel.Controls.Add(installButton);
        footerPanel.Controls.Add(statusLabel);
        rootPanel.Controls.Add(titleLabel);
        rootPanel.Controls.Add(pathLabel);
        rootPanel.Controls.Add(pathPanel);
        rootPanel.Controls.Add(shortcutPanel);
        rootPanel.Controls.Add(installProgressBar);
        rootPanel.Controls.Add(footerPanel);

        AcceptButton = installButton;
        AutoScaleMode = AutoScaleMode.Font;
        ClientSize = new Size(800, 300);
        Controls.Add(rootPanel);
        Font = new Font("Microsoft YaHei UI", 9F);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = false;
        Name = "InstallerForm";
        StartPosition = FormStartPosition.CenterScreen;
        Text = "rdi安装程序";
        FormClosing += Form1_FormClosing;
        footerPanel.ResumeLayout(false);
        shortcutPanel.ResumeLayout(false);
        shortcutPanel.PerformLayout();
        pathPanel.ResumeLayout(false);
        pathPanel.PerformLayout();
        rootPanel.ResumeLayout(false);
        rootPanel.PerformLayout();
        ResumeLayout(false);
    }
}
