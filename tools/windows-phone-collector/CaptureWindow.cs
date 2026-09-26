using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

public sealed class CaptureWindow : Form
{
    readonly string root = Path.GetDirectoryName(typeof(CaptureWindow).Assembly.Location);
    readonly JavaScriptSerializer serializer = new JavaScriptSerializer();
    readonly ComboBox devices = new ComboBox();
    readonly ComboBox protection = new ComboBox();
    readonly NumericUpDown seconds = new NumericUpDown();
    readonly CheckBox fullMode = new CheckBox();
    readonly ComboBox targetApp = new ComboBox();
    readonly TextBox apps = new TextBox();
    readonly TextBox notes = new TextBox();
    readonly TextBox folderBox = new TextBox();
    readonly TextBox messages = new TextBox();
    readonly Label status = new Label();
    readonly Label metrics = new Label();
    readonly Button start = new Button();
    readonly Button stop = new Button();
    readonly Button refresh = new Button();
    readonly FlowLayoutPanel marks = new FlowLayoutPanel();
    readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
    readonly Stopwatch elapsed = new Stopwatch();
    Process collector;
    Task<string> stdout;
    Task<string> stderr;
    string folder;
    string selectedProtection = "unknown";
    string savedObservation = "";
    bool stopping;
    bool closing;
    bool announced;
    bool refreshing;

    sealed class Phone
    {
        public string Serial;
        public string State;
        public string Model;
        public override string ToString() { return Model + "  ·  " + Serial + "  ·  " + State; }
    }

    [STAThread]
    public static void Main()
    {
        bool owns;
        using (var mutex = new Mutex(true, "Local\\AntiShakeCaptureWindowV2", out owns))
        {
            if (!owns) { MessageBox.Show("采集窗口已经打开，请从任务栏切换到它。", "手机日志采集"); return; }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new CaptureWindow());
        }
    }

    public CaptureWindow()
    {
        Text = "手机日志采集 · 反摇一摇广告";
        Name = "CaptureWindow";
        Font = new Font("Microsoft YaHei UI", 10F);
        ClientSize = new Size(840, 777);
        MinimumSize = new Size(820, 720);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(246, 248, 250);
        AutoScaleMode = AutoScaleMode.Dpi;
        Icon = SystemIcons.Application;

        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(22), ColumnCount = 1, RowCount = 14 };
        foreach (int height in new[] { 42, 32, 45, 45, 42, 48, 34, 28, 46, 32, 88, 40, 37 }) layout.RowStyles.Add(new RowStyle(SizeType.Absolute, height));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        Controls.Add(layout);
        layout.Controls.Add(new Label { Text = "自己开始测试，日志自动留在本机", Font = new Font(Font, FontStyle.Bold), AutoSize = true, Padding = new Padding(0, 6, 0, 0) });
        layout.Controls.Add(new Label { Text = "看到“采集中”后，再在手机上打开应用、点击广告或摇动。", AutoSize = true });

        var deviceRow = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        deviceRow.Controls.Add(new Label { Text = "USB 手机", AutoSize = true, Padding = new Padding(0, 8, 8, 0) });
        devices.Name = "Devices"; devices.DropDownStyle = ComboBoxStyle.DropDownList; devices.Width = 510;
        deviceRow.Controls.Add(devices);
        refresh.Text = "刷新连接"; refresh.Name = "Refresh"; refresh.Width = 105; refresh.Height = 32;
        refresh.Click += async (s, e) => await RefreshDevices();
        deviceRow.Controls.Add(refresh); layout.Controls.Add(deviceRow);

        var settings = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        settings.Controls.Add(new Label { Text = "时长（秒）", AutoSize = true, Padding = new Padding(0, 7, 0, 0) });
        seconds.Name = "Seconds"; seconds.Minimum = 5; seconds.Maximum = 600; seconds.Value = 90; seconds.Width = 80;
        settings.Controls.Add(seconds);
        settings.Controls.Add(new Label { Text = "  手机实际保护状态", AutoSize = true, Padding = new Padding(8, 7, 0, 0) });
        protection.Name = "Protection"; protection.DropDownStyle = ComboBoxStyle.DropDownList; protection.Width = 175;
        protection.Items.AddRange(new object[] { "不确定 / 未记录", "已开启", "已关闭" }); protection.SelectedIndex = 0;
        settings.Controls.Add(protection);
        settings.Controls.Add(new Label { Text = "仅作记录，不切换手机开关", AutoSize = true, Padding = new Padding(8, 7, 0, 0) });
        layout.Controls.Add(settings);
        var modeRow = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        fullMode.Name = "FullMode"; fullMode.Text = "同时采截图和页面信息"; fullMode.Checked = true; fullMode.AutoSize = true;
        modeRow.Controls.Add(fullMode);
        modeRow.Controls.Add(new Label { Text = "本轮应用", AutoSize = true, Padding = new Padding(12, 5, 5, 0) });
        targetApp.Name = "TargetApp"; targetApp.DropDownStyle = ComboBoxStyle.DropDownList; targetApp.Width = 170;
        targetApp.Items.AddRange(new object[] { "携程旅行", "包子漫画" }); targetApp.SelectedIndex = 0;
        fullMode.CheckedChanged += (s, e) => targetApp.Enabled = fullMode.Checked && collector == null;
        modeRow.Controls.Add(targetApp);
        modeRow.Controls.Add(new Label { Text = "不勾选则只采日志", AutoSize = true, Padding = new Padding(8, 5, 0, 0) });
        layout.Controls.Add(modeRow);

        var controls = new FlowLayoutPanel { Dock = DockStyle.Fill };
        start.Text = "开始采集"; start.Name = "Start"; start.Size = new Size(160, 39); start.BackColor = Color.FromArgb(26, 100, 82); start.ForeColor = Color.White; start.FlatStyle = FlatStyle.Flat;
        start.Click += (s, e) => StartCapture();
        stop.Text = "停止并保存"; stop.Name = "Stop"; stop.Size = new Size(160, 39); stop.Enabled = false;
        stop.Click += (s, e) => RequestStop();
        controls.Controls.Add(start); controls.Controls.Add(stop); layout.Controls.Add(controls);
        status.Name = "Status"; status.Text = "等待连接"; status.AutoSize = true; status.Font = new Font(Font, FontStyle.Bold); status.Padding = new Padding(0, 6, 0, 0); layout.Controls.Add(status);
        metrics.Name = "Metrics"; metrics.Text = "每轮单独保存；时间到自动停止，也可提前结束。"; metrics.AutoSize = true; layout.Controls.Add(metrics);

        marks.Dock = DockStyle.Fill; marks.WrapContents = false; marks.Enabled = false;
        foreach (string label in new[] { "广告出现", "点击", "摇动", "自动返回", "手动返回", "再次跳转" })
        {
            string eventName = label;
            var button = new Button { Text = label, Name = "Mark" + label, Size = new Size(113, 34) };
            button.Click += (s, e) => SendMark(eventName);
            marks.Controls.Add(button);
        }
        layout.Controls.Add(marks);
        var appsRow = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        appsRow.Controls.Add(new Label { Text = "测试应用", AutoSize = true, Padding = new Padding(0, 3, 8, 0) });
        apps.Name = "Apps"; apps.Width = 590; appsRow.Controls.Add(apps); layout.Controls.Add(appsRow);
        notes.Name = "Notes"; notes.Multiline = true; notes.ScrollBars = ScrollBars.Vertical; notes.Dock = DockStyle.Fill;
        notes.AccessibleName = "测试经过：说明是否有广告，哪些返回是手动操作";
        var notesGroup = new GroupBox { Text = "测试经过（可结束后补写；请说明哪些返回是手动操作）", Dock = DockStyle.Fill };
        notesGroup.Controls.Add(notes); layout.Controls.Add(notesGroup);

        var actions = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        AddAction(actions, "保存测试说明", "SaveNotes", () => { SaveNotes(); Log("测试说明已保存。"); });
        AddAction(actions, "打开日志文件夹", "OpenFolder", () => Process.Start(new ProcessStartInfo { FileName = folder ?? Path.Combine(root, "local-only"), UseShellExecute = true }));
        AddAction(actions, "复制分析请求", "CopyRequest", () => { SaveNotes(); Clipboard.SetText(AnalysisRequest()); Log("已复制。回到本任务粘贴即可请求分析；没有上传文件。"); });
        layout.Controls.Add(actions);
        folderBox.Name = "Folder"; folderBox.ReadOnly = true; folderBox.Dock = DockStyle.Fill; folderBox.Text = Path.Combine(root, "local-only"); layout.Controls.Add(folderBox);
        messages.Name = "Messages"; messages.Multiline = true; messages.ReadOnly = true; messages.ScrollBars = ScrollBars.Vertical; messages.Dock = DockStyle.Fill; messages.BackColor = Color.White; layout.Controls.Add(messages);
        timer.Interval = 250; timer.Tick += (s, e) => TickCapture(); timer.Start();
        Shown += async (s, e) => { LoadLatest(); await RefreshDevices(); };
        FormClosing += (s, e) => {
            if (collector != null) { e.Cancel = true; closing = true; RequestStop(); }
            else { try { if (folder != null) SaveNotes(); } catch { } timer.Stop(); }
        };
    }

    void AddAction(FlowLayoutPanel parent, string text, string name, Action action)
    {
        var button = new Button { Text = text, Name = name, Size = new Size(150, 32) };
        button.Click += (s, e) => { try { action(); } catch (Exception error) { Log(error.Message); } };
        parent.Controls.Add(button);
    }
    void Log(string text) { messages.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + text + Environment.NewLine); }
    string Quote(string value) { return "\"" + value.Replace("\"", "\\\"") + "\""; }
    string NodePath()
    {
        foreach (string entry in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
        {
            string candidate = Path.Combine(entry.Trim('"'), "node.exe");
            if (File.Exists(candidate)) return candidate;
        }
        string standard = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "nodejs", "node.exe");
        if (File.Exists(standard)) return standard;
        throw new Exception("未找到 Node.js。请保留本机已安装的 Node.js 后重新打开窗口。");
    }
    Process NewProcess(string executable, string arguments)
    {
        return new Process { StartInfo = new ProcessStartInfo { FileName = executable, Arguments = arguments, WorkingDirectory = root,
            UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8 } };
    }
    Dictionary<string, object> ReadJson(string file) { return serializer.Deserialize<Dictionary<string, object>>(File.ReadAllText(file, Encoding.UTF8)); }
    void WriteJson(string file, object value) { File.WriteAllText(file, serializer.Serialize(value), new UTF8Encoding(false)); }

    async Task RefreshDevices()
    {
        if (collector != null || refreshing) return;
        refreshing = true; refresh.Enabled = false; start.Enabled = false; status.Text = "正在检查 USB 连接…";
        var previous = devices.SelectedItem as Phone;
        try
        {
            string output = await Task.Run(() => {
                using (var process = NewProcess(Path.Combine(root, "tools", "platform-tools", "adb.exe"), "devices -l"))
                {
                    process.Start(); var outputTask = process.StandardOutput.ReadToEndAsync(); var errorTask = process.StandardError.ReadToEndAsync();
                    if (!process.WaitForExit(12000)) { process.Kill(); throw new Exception("连接检查超时，请检查 USB 线后重试。"); }
                    if (process.ExitCode != 0) throw new Exception(errorTask.Result);
                    return outputTask.Result;
                }
            });
            devices.Items.Clear();
            foreach (string line in output.Split('\n'))
            {
                var m = Regex.Match(line.Trim(), @"^(\S+)\s+(device|unauthorized|offline|authorizing)\b(.*)");
                if (!m.Success) continue;
                string model = Regex.Match(m.Groups[3].Value, @"model:(\S+)").Groups[1].Value.Replace('_', ' ');
                devices.Items.Add(new Phone { Serial = m.Groups[1].Value, State = m.Groups[2].Value, Model = model.Length > 0 ? model : "Android 设备" });
            }
            if (devices.Items.Count == 1) devices.SelectedIndex = 0;
            else if (previous != null) for (int i = 0; i < devices.Items.Count; i++) if (((Phone)devices.Items[i]).Serial == previous.Serial) devices.SelectedIndex = i;
            if (devices.Items.Count == 0) status.Text = "未检测到手机：连接 USB、解锁手机并允许 USB 调试，然后刷新。";
            else if (devices.Items.Count > 1 && devices.SelectedIndex < 0) status.Text = "检测到多台设备，请选择要测试的手机。";
            else if (((Phone)devices.SelectedItem).State != "device") status.Text = "手机尚未授权或离线，请在手机上处理后刷新连接。";
            else status.Text = "准备就绪，点击“开始采集”。";
        }
        catch (Exception error) { status.Text = "连接检查失败"; Log(error.Message); }
        finally { refreshing = false; refresh.Enabled = true; start.Enabled = true; }
    }

    void StartCapture()
    {
        if (collector != null) return;
        var phone = devices.SelectedItem as Phone;
        if (phone == null) { status.Text = "请先选择手机；没有设备时点击刷新连接。"; return; }
        if (phone.State != "device") { status.Text = "手机尚未授权或离线，请在手机上处理后刷新连接。"; return; }
        if (!Regex.IsMatch(phone.Serial, @"^[A-Za-z0-9_.:\-]+$")) { Log("设备标识格式异常，未启动。"); return; }
        try
        {
            selectedProtection = protection.SelectedIndex == 1 ? "on" : protection.SelectedIndex == 2 ? "off" : "unknown";
            string modeArguments = fullMode.Checked ? "full --package " + (targetApp.SelectedIndex == 1 ? "com.taoymall.taohuatan.bzmh" : "ctrip.android.view") : "logs";
            collector = NewProcess(NodePath(), Quote(Path.Combine(root, "capture.mjs")) + " start --mode " + modeArguments + " --serial " + Quote(phone.Serial) + " --seconds " + seconds.Value + " --protection " + selectedProtection + " --evidence user-selected-in-desktop-window");
            collector.Start(); stdout = collector.StandardOutput.ReadToEndAsync(); stderr = collector.StandardError.ReadToEndAsync();
            if (folder != null && notes.Text == savedObservation) notes.Clear();
            folder = null; stopping = false; announced = false; elapsed.Restart();
            start.Enabled = false; stop.Enabled = true; refresh.Enabled = false; devices.Enabled = false; seconds.Enabled = false; protection.Enabled = false;
            fullMode.Enabled = false; targetApp.Enabled = false;
            status.ForeColor = Color.FromArgb(26, 100, 82); status.Text = "正在准备，请稍候…";
            folderBox.Text = "正在建立本轮日志目录…";
            Log("开始准备新一轮；当前保护状态仅按你的选择记录。");
        }
        catch (Exception error) { collector = null; Log(error.Message); status.Text = "启动失败"; }
    }
    void ResolveFolder()
    {
        string activeFile = Path.Combine(root, "local-only", "active.json");
        if (folder != null || !File.Exists(activeFile) || collector == null) return;
        var active = ReadJson(activeFile);
        if (Convert.ToInt32(active["pid"]) != collector.Id) return;
        folder = Convert.ToString(active["folder"]); folderBox.Text = folder; SaveNotes();
    }
    void SendMark(string label)
    {
        try
        {
            ResolveFolder();
            if (collector == null || collector.HasExited || folder == null) { Log("尚未采集，事件未记录。"); return; }
            string file = Path.Combine(folder, "inbox", Guid.NewGuid().ToString("N") + ".json");
            WriteJson(file + ".tmp", new { hostTime = DateTime.UtcNow.ToString("o"), type = label, note = "桌面按钮标记", timing = "用户按按钮时间，可能晚于手机动作" });
            File.Move(file + ".tmp", file); Log("已标记：" + label);
        }
        catch (Exception error) { Log("标记未保存：" + error.Message); }
    }
    void RequestStop()
    {
        if (collector == null) return;
        stopping = true; stop.Enabled = false; marks.Enabled = false; status.Text = "正在停止并保存，请稍候…";
    }
    void TickCapture()
    {
        if (collector == null) return;
        try
        {
            ResolveFolder();
            if (folder != null)
            {
                string logFile = Path.Combine(folder, "logcat.txt");
                long size = File.Exists(logFile) ? new FileInfo(logFile).Length : 0;
                metrics.Text = "已运行 " + elapsed.Elapsed.TotalSeconds.ToString("0") + " 秒 / " + seconds.Value + " 秒    ·    日志 " + (size / 1024.0).ToString("0.0") + " KB";
                if (!announced && size > 0 && !stopping) { announced = true; marks.Enabled = true; status.Text = "采集中 · 现在可以在手机上操作"; Log("日志已写入。请手动打开应用并触发广告。"); }
                if (stopping && !collector.HasExited) {
                    string flag = Path.Combine(folder, "desktop-stop-sent.txt");
                    if (!File.Exists(flag)) { SendMark("stop"); File.WriteAllText(flag, DateTime.UtcNow.ToString("o")); }
                }
            }
            if (!collector.HasExited) return;
            string output = stdout.GetAwaiter().GetResult(); string errors = stderr.GetAwaiter().GetResult(); int exitCode = collector.ExitCode;
            if (folder == null) {
                var match = Regex.Match(output, @"(?:输出：|原始数据仅在：)([^\r\n]+)");
                if (match.Success && Directory.Exists(match.Groups[1].Value)) folder = match.Groups[1].Value;
            }
            collector.Dispose(); collector = null; elapsed.Stop();
            start.Enabled = true; stop.Enabled = false; refresh.Enabled = true; devices.Enabled = true; seconds.Enabled = true; protection.Enabled = true; marks.Enabled = false;
            fullMode.Enabled = true; targetApp.Enabled = fullMode.Checked;
            bool abnormal = exitCode != 0 || folder == null;
            if (folder != null) {
                string eventsFile = Path.Combine(folder, "events.jsonl");
                abnormal = abnormal || !File.Exists(Path.Combine(folder, "round.json")) || (File.Exists(eventsFile) && File.ReadAllText(eventsFile).Contains("logcat 意外退出"));
                SaveNotes(); WriteJson(Path.Combine(root, "local-only", "desktop-latest.json"), new { folder = folder, finishedAt = DateTime.UtcNow.ToString("o"), abnormal = abnormal });
                File.WriteAllText(Path.Combine(folder, "请助手分析.txt"), AnalysisRequest(), new UTF8Encoding(false)); folderBox.Text = folder;
            }
            status.Text = abnormal ? "采集中断 / 启动失败 · 已有日志保留，请检查连接后重试" : "已保存 · 可以填写测试经过，或开始下一轮";
            status.ForeColor = abnormal ? Color.DarkRed : Color.FromArgb(26, 100, 82);
            if (errors.Trim().Length > 0) Log(errors.Trim());
            Log(abnormal ? "未将异常结束记为完成90秒或测试通过。" : "本轮已结束；点击“复制分析请求”后发给助手即可。");
            if (closing) Close();
        }
        catch (IOException) { /* atomic files may be in flight; retry on next UI tick */ }
        catch (Exception error) { Log("状态读取失败：" + error.Message); }
    }
    void SaveNotes()
    {
        if (folder == null) throw new Exception("还没有本轮记录，请先开始采集。");
        WriteJson(Path.Combine(folder, "manual-notes.json"), new { savedAt = DateTime.UtcNow.ToString("o"), applications = apps.Text, reportedProtection = selectedProtection, observation = notes.Text, source = "用户在桌面窗口填写；不是自动识别结果", eventTiming = "事件按钮记录主机点击时间，不等于手机动作精确时刻" });
        savedObservation = notes.Text;
    }
    string AnalysisRequest()
    {
        if (folder == null) throw new Exception("还没有本轮记录。");
        return "请分析这轮手机手动测试数据：" + folder + "。结合日志、已有截图和页面信息、manual-notes.json 及 events.jsonl，区分手动/自动返回、应用内页面与跨应用跳转，核对采集是否中断。输出中文报告和脱敏ZIP，原始数据不上传。不要把未复现当成通过。";
    }
    void LoadLatest()
    {
        string file = Path.Combine(root, "local-only", "desktop-latest.json");
        if (!File.Exists(file)) return;
        try {
            string previousFolder = Convert.ToString(ReadJson(file)["folder"]);
            if (!Directory.Exists(previousFolder)) return;
            folder = previousFolder; folderBox.Text = folder;
            string notesFile = Path.Combine(folder, "manual-notes.json");
            if (File.Exists(notesFile)) {
                var data = ReadJson(notesFile); apps.Text = Convert.ToString(data["applications"]); notes.Text = Convert.ToString(data["observation"]); selectedProtection = Convert.ToString(data["reportedProtection"]);
                savedObservation = notes.Text;
                protection.SelectedIndex = selectedProtection == "on" ? 1 : selectedProtection == "off" ? 2 : 0;
            }
            Log("已载入上一次窗口采集记录，可补写说明或复制分析请求。");
        } catch (Exception error) { Log("未能载入上轮记录：" + error.Message); }
    }
}
