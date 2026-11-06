using System.Diagnostics;
using System.Drawing;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;
using Microsoft.Win32;

namespace PocketXiaoli.Setup;

/// <summary>
/// 口袋小鲤 · 桌面版 安装程序。
///
/// 为什么自己写而不用 jpackage --type exe：那条路依赖 WiX（本机没有，装它要管理员 + NetFx3）。
/// 这里做成单文件 EXE：内嵌 payload.zip（就是 jpackage 产出的 app-image），运行时解压到
/// %LOCALAPPDATA%\Programs\PocketXiaoli，建快捷方式、写卸载项，不需要管理员、不需要装 Java。
///
/// 参数：
///   --quiet            静默安装（不弹任何 UI，用于脚本验证）
///   --keep-data        卸载时保留 %APPDATA%\PocketXiaoli
///   --uninstall        卸载
/// </summary>
internal static class Program
{
    private const string AppName = "口袋小鲤";
    // 来自 csproj 的 <Version>（由 tools/installer.ps1 从仓库根 gradle.properties 传入）
    private static readonly string Version =
        typeof(Program).Assembly.GetName().Version is { } v ? $"{v.Major}.{v.Minor}.{v.Build}" : "0.0.0";

    private static string TargetDir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "PocketXiaoli");

    private static string DataDir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PocketXiaoli");

    private static bool _quiet;
    private static string LogPath => Path.Combine(Path.GetTempPath(), "pocketxiaoli-setup.log");

    private static void Log(string message)
    {
        if (!_quiet) Console.WriteLine(message);
        try { File.AppendAllText(LogPath, message + Environment.NewLine); } catch { /* 日志写不了不算失败 */ }
    }

    [STAThread]
    private static int Main(string[] args)
    {
        _quiet = args.Any(a => a.Equals("--quiet", StringComparison.OrdinalIgnoreCase));
        try
        {
            try { File.Delete(LogPath); } catch { }
            Log($"{AppName} 安装程序启动：{string.Join(' ', args)}");

            if (args.Any(a => a.Equals("--uninstall", StringComparison.OrdinalIgnoreCase)))
            {
                var keepData = args.Any(a => a.Equals("--keep-data", StringComparison.OrdinalIgnoreCase));
                if (!_quiet)
                {
                    var answer = MessageBox.Show(
                        $"要卸载「{AppName}」吗？\n\n程序本体和快捷方式会被删除；课表/待办等数据默认保留。",
                        $"{AppName} 卸载",
                        MessageBoxButtons.OKCancel,
                        MessageBoxIcon.Question);
                    if (answer != DialogResult.OK) return 0;
                }
                return Uninstall(keepData, quiet: _quiet);
            }

            if (args.Any(a => a.Equals("--diagnose", StringComparison.OrdinalIgnoreCase)))
                return Diagnose();

            if (_quiet) return Install(quiet: true);
            return RunInteractiveInstall();
        }
        catch (Exception ex)
        {
            Log("安装失败：" + ex);
            if (!_quiet) MessageBox.Show(ex.Message, $"{AppName} 安装失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    /// <summary>安装前体检：把「当前进程是谁、哪里能写」记进日志，排查权限问题时用。</summary>
    private static int Diagnose()
    {
        Log($"diagnose: user={Environment.UserName} domain={Environment.UserDomainName} " +
            $"interactive={Environment.UserInteractive} os={Environment.OSVersion.VersionString}");
        Log($"diagnose: process={Environment.ProcessPath}");
        Log($"diagnose: is64={Environment.Is64BitProcess} clr={Environment.Version}");
        var candidates = new (string Label, string Path)[]
        {
            ("LocalAppData", Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData)),
            ("Programs", TargetDir),
            ("AppData", Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData)),
            ("Temp", Path.GetTempPath()),
            ("Desktop", Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory)),
        };
        foreach (var (label, path) in candidates)
        {
            var probe = Path.Combine(path, "_probe_" + Guid.NewGuid().ToString("N")[..6]);
            try
            {
                Directory.CreateDirectory(probe);
                File.WriteAllText(Path.Combine(probe, "x.txt"), "ok");
                Directory.Delete(probe, recursive: true);
                Log($"diagnose: WRITE-OK   {label} = {path}");
            }
            catch (Exception ex)
            {
                Log($"diagnose: WRITE-FAIL {label} = {path} → {ex.GetType().Name}: {ex.Message}");
            }
        }
        return 0;
    }

    private static int Install(bool quiet, Action<double, string?>? progress = null)
    {
        void Report(double fraction, string text)
        {
            if (!quiet) Console.WriteLine(text);
            progress?.Invoke(fraction, text);
        }

        StopRunningApp(quiet);
        Report(0.03, "→ 安装到 " + TargetDir);
        DeleteDirectoryWithRetry(TargetDir);
        Directory.CreateDirectory(TargetDir);

        var resourceName = Assembly.GetExecutingAssembly().GetManifestResourceNames()
            .FirstOrDefault(n => n.EndsWith("payload.zip", StringComparison.OrdinalIgnoreCase))
            ?? throw new InvalidOperationException($"安装包内缺少 payload.zip（现有资源：{string.Join(", ", Assembly.GetExecutingAssembly().GetManifestResourceNames())}）");

        using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName)
                            ?? throw new InvalidOperationException($"读不到嵌入资源 {resourceName}"))
        using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
        {
            var total = zip.Entries.Count;
            var done = 0;
            Report(0.08, "正在解压程序文件（约 100 MB）…");
            foreach (var entry in zip.Entries)
            {
                var dest = Path.GetFullPath(Path.Combine(TargetDir, entry.FullName));
                if (!dest.StartsWith(TargetDir, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException($"压缩包里有越界路径：{entry.FullName}");

                if (entry.FullName.EndsWith('/'))
                {
                    Directory.CreateDirectory(dest);
                    continue;
                }
                Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
                entry.ExtractToFile(dest, overwrite: true);
                done++;
                if (done % 200 == 0)
                    Report(0.08 + 0.78 * done / Math.Max(1, total), $"正在解压程序文件 {done}/{total}");
                if (!quiet && done % 500 == 0) Console.WriteLine($"   解压 {done}/{total}");
            }
        }

        var exe = Path.Combine(TargetDir, "PocketXiaoli.exe");
        if (!File.Exists(exe)) throw new InvalidOperationException($"解压后找不到 PocketXiaoli.exe");

        // 卸载脚本随安装目录保留（注册表里指向它）
        var uninstaller = Path.Combine(TargetDir, "uninstall.ps1");
        File.WriteAllText(uninstaller, UninstallScript, new UTF8Encoding(true));

        Report(0.9, "正在创建快捷方式…");
        var linkName = $"{AppName}.lnk";
        foreach (var folder in new[]
                 {
                     Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                     Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
                 })
        {
            CreateShortcut(Path.Combine(folder, linkName), exe, TargetDir);
        }

        WriteUninstallKey(exe, uninstaller);

        Report(0.97, "正在启动应用…");
        Process.Start(new ProcessStartInfo(exe) { WorkingDirectory = TargetDir, UseShellExecute = true });
        Report(1.0, "安装完成");
        if (!quiet) Console.WriteLine("✓ 安装完成：开始菜单 / 桌面快捷方式已就绪");
        return 0;
    }

    /// <summary>双击安装：进度窗 + 后台解压；成功后自动启动应用。</summary>
    private static int RunInteractiveInstall()
    {
        using var form = new ProgressForm();
        var exitCode = 0;
        var started = false;
        form.Shown += (_, _) =>
        {
            if (started) return;
            started = true;
            Task.Run(() =>
            {
                try
                {
                    exitCode = Install(quiet: false, progress: (fraction, text) => form.SetProgress(fraction, text));
                    form.Invoke(new Action(() => form.Close()));
                }
                catch (Exception ex)
                {
                    Log("安装失败：" + ex);
                    exitCode = 1;
                    form.Invoke(new Action(() =>
                    {
                        form.Close();
                        MessageBox.Show(ex.Message, $"{AppName} 安装失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }));
                }
            });
        };
        Application.Run(form);
        return exitCode;
    }
    /// <summary>删目录：旧进程/杀软可能短暂占着文件，重试几次再放弃。</summary>
    private static void DeleteDirectoryWithRetry(string dir)
    {
        if (!Directory.Exists(dir)) return;
        for (var attempt = 1; ; attempt++)
        {
            try
            {
                Directory.Delete(dir, recursive: true);
                return;
            }
            catch (IOException) when (attempt < 6)
            {
                Thread.Sleep(500);
            }
            catch (UnauthorizedAccessException) when (attempt < 6)
            {
                Thread.Sleep(500);
            }
        }
    }

    /// <summary>安装/卸载前先关掉正在运行的旧实例，否则它占着 jar，删目录会失败。</summary>
    private static void StopRunningApp(bool quiet)
    {
        foreach (var process in Process.GetProcessesByName("PocketXiaoli"))
        {
            try
            {
                if (!quiet) Console.WriteLine($"→ 关闭正在运行的 {AppName}");
                process.CloseMainWindow();
                if (!process.WaitForExit(4000)) process.Kill(entireProcessTree: true);
            }
            catch (Exception ex)
            {
                Log("关闭旧实例失败（继续安装）：" + ex.Message);
            }
        }
        // 给 Windows 一点时间释放文件句柄
        Thread.Sleep(400);
    }

    private static int Uninstall(bool keepData, bool quiet = false)
    {
        StopRunningApp(quiet);
        foreach (var folder in new[]
                 {
                     Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                     Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
                 })
        {
            var link = Path.Combine(folder, $"{AppName}.lnk");
            if (File.Exists(link)) File.Delete(link);
        }

        Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketXiaoli", throwOnMissingSubKey: false);

        if (!keepData && !quiet && Directory.Exists(DataDir))
        {
            var answer = MessageBox.Show(
                $"是否同时删除本地数据？\r\n\r\n{DataDir}\r\n\r\n（课表 / 待办 / 会话都在这里，选「否」以后重装还能接着用）",
                $"{AppName} 卸载", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (answer == DialogResult.Yes) Directory.Delete(DataDir, recursive: true);
        }

        // 自己删不掉自己：拷一段 cmd 到临时目录，等进程退出后再删安装目录
        var cmd = Path.Combine(Path.GetTempPath(), "pocketxiaoli-uninstall.cmd");
        File.WriteAllText(cmd,
            "@echo off\r\ntimeout /t 2 /nobreak >nul\r\nrmdir /s /q \"" + TargetDir + "\"\r\ndel \"%~f0\"\r\n",
            Encoding.ASCII);
        Process.Start(new ProcessStartInfo("cmd.exe", $"/c \"{cmd}\"")
        {
            WindowStyle = ProcessWindowStyle.Hidden,
            UseShellExecute = false,
        });
        return 0;
    }

    private static void WriteUninstallKey(string exe, string uninstaller)
    {
        using var key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketXiaoli");
        key.SetValue("DisplayName", $"{AppName} 桌面版");
        key.SetValue("DisplayVersion", Version);
        key.SetValue("Publisher", "Pocket Xiaoli");
        key.SetValue("InstallLocation", TargetDir);
        key.SetValue("DisplayIcon", $"{exe},0");
        key.SetValue("NoModify", 1, RegistryValueKind.DWord);
        key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
        key.SetValue("EstimatedSize", DirectorySizeKb(TargetDir), RegistryValueKind.DWord);
        key.SetValue("UninstallString",
            $"powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"{uninstaller}\"");
    }

    private static int DirectorySizeKb(string dir) =>
        (int)Math.Min(int.MaxValue, new DirectoryInfo(dir).EnumerateFiles("*", SearchOption.AllDirectories).Sum(f => f.Length) / 1024);

    private static void CreateShortcut(string linkPath, string targetExe, string workingDir)
    {
        var shellType = Type.GetTypeFromProgID("WScript.Shell") ?? throw new InvalidOperationException("找不到 WScript.Shell");
        dynamic shell = Activator.CreateInstance(shellType)!;
        dynamic link = shell.CreateShortcut(linkPath);
        link.TargetPath = targetExe;
        link.WorkingDirectory = workingDir;
        link.IconLocation = targetExe + ",0";
        link.Description = "口袋小鲤 · 桌面版（烟台理工学院校园助手）";
        link.Save();
        Marshal.FinalReleaseComObject(link);
        Marshal.FinalReleaseComObject(shell);
    }

    private const string UninstallScript = """
        # 口袋小鲤 · 桌面版 卸载入口（「设置 → 应用」指向这里）
        param([switch]$KeepData, [switch]$RemoveData)
        $ErrorActionPreference = 'SilentlyContinue'
        $appName = '口袋小鲤'
        $data = Join-Path $env:APPDATA 'PocketXiaoli'
        foreach ($lnk in @(
                (Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk"),
                (Join-Path ([Environment]::GetFolderPath('Desktop')) "$appName.lnk"))) {
            Remove-Item $lnk -Force
        }
        Remove-Item 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketXiaoli' -Recurse -Force
        if ((Test-Path $data) -and -not $KeepData) {
            if ($RemoveData) { Remove-Item $data -Recurse -Force }
            else {
                Add-Type -AssemblyName System.Windows.Forms
                $answer = [System.Windows.Forms.MessageBox]::Show(
                    "是否同时删除本地数据？`n`n$data", "$appName 卸载", 'YesNo', 'Question')
                if ($answer -eq 'Yes') { Remove-Item $data -Recurse -Force }
            }
        }
        $target = Split-Path -Parent $MyInvocation.MyCommand.Path
        $cmd = Join-Path $env:TEMP 'pocketxiaoli-uninstall.cmd'
        @"
        @echo off
        timeout /t 2 /nobreak >nul
        rmdir /s /q "$target"
        del "%~f0"
        "@ | Set-Content -Path $cmd -Encoding ASCII
        Start-Process cmd.exe -ArgumentList "/c `"$cmd`"" -WindowStyle Hidden
        """;
}
