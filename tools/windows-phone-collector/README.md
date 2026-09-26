# Windows 手机日志采集器

通过 USB 记录 Android 真机测试，由测试者在手机上打开应用、点击广告或摇动，在电脑窗口中自行开始、停止和标记事件。主要用于“反摇一摇广告”的真机问题分析；采集器本身不判断广告是否拦截成功。

本目录同时保存源码和 **[PhoneLogCollector.exe](PhoneLogCollector.exe)**。这是 Windows 采集程序，不是 Android APK。EXE 随此分支保存，不是 GitHub Release。

## 下载和首次准备

1. 在 GitHub 选择 `tools/windows-phone-log-collector` 分支，用 **Code → Download ZIP** 下载并解压仓库，进入 `tools/windows-phone-collector`。也可克隆该分支。
2. 安装 [Node.js](https://nodejs.org/) 的受支持版本（建议 Node.js 22 或更新；本机已用 24 验证）。不需要安装 npm 依赖。
3. 准备 [Android 官方 Platform Tools](https://developer.android.com/tools/releases/platform-tools)。可在本目录打开 PowerShell，运行下面的下载脚本；或自行解压官方 Windows 包，让 ADB 位于 `tools/platform-tools/adb.exe`。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup-adb.ps1
```

该脚本从 `dl.google.com` 下载到本目录，不全局安装 ADB。Google Platform Tools 按其官方条款提供，不随本分支提交。

4. USB 连接并解锁手机，开启开发者选项及 USB 调试，在手机上允许电脑授权。
5. 双击 **PhoneLogCollector.exe** 或 **Start-Collector.cmd**。请保留 EXE 周围的脚本与工具目录，**只下载一个 EXE 无法采集**。程序使用 Windows 的 .NET Framework/Windows Forms；Windows 10/11 常规环境适用。EXE 未进行代码签名。

不需要 Android Studio，不编译或安装 Android 应用，不需要 Root、Bootloader 解锁或清除应用数据。

## 自己开始一轮测试

1. 点击“刷新连接”，选择手机。存在多台设备时必须选择；后续采集命令绑定所选设备，并核实 USB 连接。
2. 填写手机**实际**保护状态。这个选项只做记录，不会切换手机上的保护开关；无障碍服务启用也不能单独证明保护开启。
3. 默认每轮 **90 秒**，可设置 5–600 秒。默认勾选“同时采截图和页面信息”，本轮应用可选携程旅行或包子漫画；取消勾选则只采日志，可手动测试其他应用。
4. 点击“开始采集”，**等待显示“采集中”后**再打开待测应用、点击实际广告或摇动。工具不会代替你点击手机，不会操作登录、下单、支付或系统授权。
5. 可点击“广告出现、点击、摇动、自动返回、手动返回、再次跳转”记录事件。按钮记录的是电脑收到标记的时间，可能晚于手机实际动作。
6. 点击“停止并保存”，或等待到时自动结束。填写本轮测试应用和经过，尤其说明哪些返回是自己操作的，再点“保存测试说明”。请检查应用名称是否仍沿用上一轮。退出窗口会先请求结束该窗口启动的采集。
7. 点“打开日志文件夹”查看本地数据；点“复制分析请求”，粘贴到能访问本机文件的助手任务中，请助手分析并制作脱敏报告。**复制请求不会自动上传，也不会自动调用任何 AI 服务。**

保护关闭和开启须分别采集，记录每轮实际状态。未出现广告写“未复现”；两轮广告不同，不能直接视为同一场景对照。

完整模式当前内置的包名是 `ctrip.android.view` 与 `com.taoymall.taohuatan.bzmh`，启动前会检查是否安装。其他来源或同名应用可能使用不同包名，请先通过手机安装列表确认，不能凭显示名称套用。

## 采集内容与限制

| 内容 | 完整模式 | 仅日志模式 |
| --- | --- | --- |
| main/system/events/crash 连续 logcat | 是，不按源应用 PID 过滤、不清空日志 | 是 |
| 原始 PNG 截图 | 每秒尝试一次，保存实际命令起止时间 | 否 |
| 前台包名和 Activity | 约每 1.5 秒被动采样 | 仅依赖系统日志实际暴露内容 |
| 页面信息 | 约每 10 秒被动采样 | 否 |
| 手机环境 | 机型、系统、地区、网络配置、应用版本等 | 启动时保存时钟与无障碍状态 |
| 人工事件、测试说明 | 是 | 是 |

截图、日志、前台和页面采样分别进行。慢帧会跳过已错过的时间槽，并在元数据中记录，不补造截图，不保证严格每秒一张。结束时等待正在执行的有限时长命令收尾，因此实际结束时间可能略晚于所选时长。

本机曾发现 `uiautomator dump` 干扰被测无障碍服务，已不用该方法；页面采样采用 `dumpsys activity top` 等被动查询，不保证得到完整层级或文字。WebView 的出现不是广告证据；同包名也可能发生网页切换。

手机网络依据手机输出判断，不用电脑出口 IP 代替。日志使用手机时钟，截图和人工事件使用电脑时钟，须按 `environment/clock.txt` 与对应元数据估计偏差。

不能保证捕获完整 URL、广告点击目标、网页加载成功或应用内部返回日志。系统未暴露的地址写“未捕获”。不读取应用私有数据，不安装抓包证书，不绕过 HTTPS；反广告应用内部记录由用户主动导出或截图补充。系统接受返回请求也不等于已经成功回到原页面。

## 本地文件

每轮保存在 `local-only/round-*`，互不覆盖。

```text
local-only/
  desktop-latest.json          # 最近一次桌面窗口采集目录
  round-*/
    round.json                 # 模式、时间、设备、采集结束状态
    logcat.txt                 # 原始连续日志
    events.jsonl               # 操作标记和采集事件
    manual-notes.json           # 手工填写的测试说明
    sampling.jsonl             # 完整模式各采样耗时和漏槽
    screenshots/               # PNG 与每帧时间索引
    foreground/                # 前台任务被动采样
    pages/                     # 被动页面信息和限制说明
    environment/               # 手机环境与时钟
    manual-exports/            # 用户主动导出的应用内部记录
```

**原始日志和截图可能含账号、验证码、令牌及其他应用内容。** `.gitignore` 已排除采集目录、工具下载目录、分享暂存和 ZIP，但这不等于自动脱敏。分享前应人工审阅并生成单独副本；不要强制提交被忽略的数据。本分支不包含任何真实手机采集数据、个人路径、设备序列号或测试报告。

## 命令行与本地分析

在本目录执行（将 `DEVICE_SERIAL` 换成 `doctor` 输出的实际设备标识）：

```powershell
node capture.mjs doctor
node capture.mjs start --mode full --serial DEVICE_SERIAL --package ctrip.android.view --protection off --seconds 90
node capture.mjs start --mode logs --serial DEVICE_SERIAL --protection on --seconds 90
node capture.mjs mark 手动返回 第一次
node capture.mjs stop
node analyze.mjs "本轮目录"
```

命令行省略 `--mode` 时仍默认 `logs`。`analyze.mjs` 只生成本地采集质量和证据索引，**不是最终效果报告，也没有自动脱敏**。URL 仅表示文本中出现，历史任务、配置和接口 URL 不能直接当成广告落地地址。

## 从源码构建与验证

先关闭本目录的采集窗口。使用 Windows PowerShell 5.1 编译桌面 EXE（不是 Android 工程）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-window.ps1
npm test
```

本目录没有第三方 npm 依赖。可选真机集成验证：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\window.integration.ps1
```

该集成脚本需要已授权的 USB 手机，会实际创建短时日志采集轮次以验证事件、提前停止、自动停止和说明保存；不会点击手机或触发广告。不要与正式测试同时运行。基础测试和采集器自检不代表反广告功能通过真机广告测试。

EXE 的 SHA-256 见 `SHA256SUMS.txt`，可用 `Get-FileHash .\PhoneLogCollector.exe -Algorithm SHA256` 核对。编译输出不承诺逐字节可复现。项目授权沿用仓库根目录 MIT License。
