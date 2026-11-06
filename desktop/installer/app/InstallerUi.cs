using System.Drawing;
using System.Windows.Forms;

namespace PocketXiaoli.Setup;

/// <summary>
/// 安装进度窗口。
///
/// 为什么要有它：安装程序是 WinExe（没有控制台），双击后如果只是静默解压 100+ MB，
/// 用户会以为「点了没反应」。窗口只做三件事：品牌标题、当前阶段文案、进度条。
/// </summary>
internal sealed class ProgressForm : Form
{
    private readonly Label _status = new();
    private readonly ProgressBar _bar = new();
    private readonly Label _hint = new();

    public ProgressForm()
    {
        Text = "口袋小鲤 · 安装程序";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        ShowInTaskbar = true;
        ClientSize = new Size(460, 170);
        BackColor = Color.FromArgb(247, 248, 250);
        Font = new Font("Microsoft YaHei UI", 9F);

        var title = new Label
        {
            Text = "口袋小鲤 · 桌面版",
            Font = new Font("Microsoft YaHei UI", 14F, FontStyle.Bold),
            ForeColor = Color.FromArgb(29, 34, 45),
            AutoSize = true,
            Location = new Point(24, 20),
        };

        _status.Text = "准备安装…";
        _status.ForeColor = Color.FromArgb(90, 99, 115);
        _status.AutoSize = false;
        _status.Size = new Size(412, 22);
        _status.Location = new Point(24, 60);

        _bar.Location = new Point(24, 92);
        _bar.Size = new Size(412, 10);
        _bar.Style = ProgressBarStyle.Continuous;
        _bar.Minimum = 0;
        _bar.Maximum = 1000;

        _hint.Text = "安装到当前用户目录，不需要管理员权限";
        _hint.ForeColor = Color.FromArgb(140, 149, 168);
        _hint.AutoSize = false;
        _hint.Size = new Size(412, 20);
        _hint.Location = new Point(24, 116);

        Controls.AddRange(new Control[] { title, _status, _bar, _hint });

        try
        {
            // 用安装程序自身的图标当窗口图标（嵌入后可从 exe 取）
            var icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            if (icon != null) Icon = icon;
        }
        catch
        {
            // 取不到图标不影响安装
        }
    }

    /// <summary>线程安全地更新进度（fraction 0..1）。</summary>
    public void SetProgress(double fraction, string? text = null)
    {
        void Apply()
        {
            var clamped = Math.Clamp(fraction, 0, 1);
            _bar.Value = (int)Math.Round(clamped * 1000);
            if (!string.IsNullOrWhiteSpace(text)) _status.Text = text;
        }

        if (InvokeRequired) Invoke(new Action(Apply));
        else Apply();
    }

    public void SetStatus(string text)
    {
        void Apply() => _status.Text = text;
        if (InvokeRequired) Invoke(new Action(Apply));
        else Apply();
    }
}
