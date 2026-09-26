import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";
import { performance } from "node:perf_hooks";
import readline from "node:readline";
import { parseDevices, chooseDevice, nextSlot, validPng, confirmsUsb } from "./core.mjs";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const LOCAL = path.join(ROOT, "local-only");
const ADB = path.join(ROOT, "tools", "platform-tools", "adb.exe");
const ACTIVE = path.join(LOCAL, "active.json");
fs.mkdirSync(LOCAL, { recursive: true });
const argv = process.argv.slice(2);
const command = argv.shift() || "doctor";
const option = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i < 0 ? fallback : argv[i + 1];
};
const iso = () => new Date().toISOString();
const stamp = () => iso().replace(/[:.]/g, "-");
const json = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2), "utf8");
const append = (file, value) => fs.appendFileSync(file, JSON.stringify(value) + "\n", "utf8");
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const children = new Set();
let serial;
let stopping = false;

function run(args, timeout = 15000, bound = true) {
  if (bound && !serial) throw new Error("未绑定目标设备");
  return new Promise((resolve) => {
    const started = iso();
    const begin = performance.now();
    const child = spawn(ADB, bound ? ["-s", serial, ...args] : args, { windowsHide: true });
    children.add(child);
    const output = [];
    const errors = [];
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
    child.stdout.on("data", (bytes) => output.push(bytes));
    child.stderr.on("data", (bytes) => errors.push(bytes));
    child.on("error", (error) => errors.push(Buffer.from(error.message)));
    child.on("close", (code) => {
      clearTimeout(timer);
      children.delete(child);
      resolve({ started, ended: iso(), elapsedMs: Math.round(performance.now() - begin), code, timedOut,
        stdout: Buffer.concat(output), stderr: Buffer.concat(errors).toString("utf8") });
    });
  });
}

async function bind() {
  const result = await run(["devices", "-l"], 15000, false);
  if (result.code !== 0) throw new Error(result.stderr || "ADB 设备检查失败");
  const devices = parseDevices(result.stdout.toString("utf8"));
  const device = chooseDevice(devices, option("serial"));
  serial = device.serial;
  const state = await run(["get-devpath"]);
  // Windows libadbusb may report 'unknown'. USB-only discovery is read-only.
  const usb = await run(["-d", "get-serialno"], 15000, false);
  if (!confirmsUsb(state.stdout.toString(), serial, usb.code === 0 ? usb.stdout.toString() : "")) {
    throw new Error("未确认 USB 传输路径；拒绝把无线/模拟器连接算作本次 USB 真机采集");
  }
  return device;
}

async function saveCommand(folder, name, args, timeout) {
  const result = await run(args, timeout);
  fs.writeFileSync(path.join(folder, name + ".txt"), result.stdout);
  const { stdout, ...metadata } = result;
  json(path.join(folder, name + ".meta.json"), { ...metadata, args });
  return result;
}

async function environment(folder, packages = []) {
  fs.mkdirSync(folder, { recursive: true });
  // Android output alone describes the handset network. No PC IP probe is used.
  const commands = {
    properties: ["shell", "getprop"],
    clock: ["shell", "date", "+%s.%N"],
    packages: ["shell", "pm", "list", "packages"],
    launchers: ["shell", "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"],
    accessibility: ["shell", "dumpsys", "accessibility"],
    enabled_accessibility: ["shell", "settings", "get", "secure", "enabled_accessibility_services"],
    connectivity: ["shell", "dumpsys", "connectivity"],
    wifi: ["shell", "cmd", "wifi", "status"],
    proxy: ["shell", "settings", "get", "global", "http_proxy"],
    vpn: ["shell", "settings", "get", "secure", "always_on_vpn_app"],
    region: ["shell", "settings", "get", "system", "system_locales"],
  };
  for (const pkg of new Set(["cn.returnguard", ...packages])) commands[`package-${pkg}`] = ["shell", "dumpsys", "package", pkg];
  // Small batches keep environment inspection from overwhelming the handset.
  const entries = Object.entries(commands);
  for (let i = 0; i < entries.length; i += 3) {
    await Promise.all(entries.slice(i, i + 3).map(([name, args]) => saveCommand(folder, name, args)));
  }
  json(path.join(folder, "interpretation.json"), {
    capturedAt: iso(), serial,
    network: "以 connectivity 中当前默认网络、VPN transports、LinkProperties 及手机设置核对；仅配置 VPN 应用不等于 VPN 正在连接",
    publicIp: "未捕获；没有把电脑出口 IP 当成手机出口 IP",
    protection: "无障碍服务启用不等于应用保护开关开启；实际保护和待测应用勾选必须另行记录",
    privateData: "不使用 root、run-as 或读取应用私有目录；返回日志由应用页面截图或用户主动导出",
  });
}

async function doctor() {
  const folder = path.join(LOCAL, "connection-" + stamp());
  fs.mkdirSync(folder, { recursive: true });
  const version = await run(["version"], 15000, false);
  const devices = await run(["devices", "-l"], 15000, false);
  fs.writeFileSync(path.join(folder, "adb-version.txt"), version.stdout);
  fs.writeFileSync(path.join(folder, "adb-devices.txt"), devices.stdout);
  json(path.join(folder, "status.json"), { checkedAt: iso(), devices: parseDevices(devices.stdout.toString()), stderr: devices.stderr, exitCode: devices.code });
  console.log(devices.stdout.toString().trim());
  console.log("连接检查已保存：" + folder);
  if (!parseDevices(devices.stdout.toString()).some((device) => device.state === "device")) process.exitCode = 2;
}

function active() {
  if (!fs.existsSync(ACTIVE)) throw new Error("当前没有采集轮次");
  const state = JSON.parse(fs.readFileSync(ACTIVE, "utf8"));
  try { process.kill(state.pid, 0); } catch { throw new Error("采集进程已退出；active.json 是残留状态"); }
  return state;
}

function signal(type, note = "") {
  const state = active();
  const file = path.join(state.folder, "inbox", `${Date.now()}-${process.pid}-${Math.random().toString(16).slice(2)}.json`);
  json(file + ".tmp", { hostTime: iso(), type, note, timing: "人工标记接收时间；实际动作可能稍早，不能当作自动检测时间" });
  fs.renameSync(file + ".tmp", file);
  console.log("已记录：" + type);
}

async function capture() {
  const mode = option("mode", "logs");
  if (!["logs", "full"].includes(mode)) throw new Error("采集模式只接受 logs/full");
  const duration = Number(option("seconds", "90"));
  if (!Number.isFinite(duration) || duration <= 0 || duration > 600) throw new Error("时长应为 1–600 秒");
  const requestedProtection = option("protection", "unknown");
  if (!["on", "off", "unknown"].includes(requestedProtection)) throw new Error("保护状态只接受 on/off/unknown");
  const pkg = option("package", null);
  if ((mode === "full" && !pkg) || (pkg && !/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg))) throw new Error("请通过已安装应用确认后提供合法 --package 包名");
  if (fs.existsSync(ACTIVE)) {
    let existing;
    try { existing = active(); } catch { /* stale session is kept in its own folder */ }
    if (existing) throw new Error("另一轮仍在采集；请先停止");
  }
  const device = await bind();
  if (pkg) {
    const installed = await run(["shell", "pm", "path", pkg]);
    if (!installed.stdout.toString().startsWith("package:")) throw new Error("设备未确认安装目标包，停止");
  }
  const folder = path.join(LOCAL, "round-" + stamp());
  for (const name of ["screenshots", "pages", "foreground", "inbox", "environment", "manual-exports"]) fs.mkdirSync(path.join(folder, name), { recursive: true });
  console.log("正在记录环境，请暂时不要打开待测应用。");
  if (mode === "full") await environment(path.join(folder, "environment"), [pkg]);
  else {
    await saveCommand(path.join(folder, "environment"), "clock", ["shell", "date", "+%s.%N"]);
    await saveCommand(path.join(folder, "environment"), "accessibility", ["shell", "dumpsys", "accessibility"]);
  }
  const round = { mode, device, sourcePackage: pkg, requestedProtection, actualProtection: "待核验", targetSelected: "待核验", protectionEvidence: option("evidence", "未提供"), requestedSeconds: duration, startedAt: iso(), collectorPid: process.pid, result: "待人工审阅；没有广告证据不得认定通过", interaction: "用户手动打开、点击或摇动；采集器不操作手机", limitation: mode === "logs" ? "只采日志，无截图或页面采样；未写入系统日志的页面、点击、URL 和返回结果无法仅凭日志证明" : "见页面采样元数据" };
  json(path.join(folder, "round.json"), round);
  const events = path.join(folder, "events.jsonl");
  const begin = performance.now();
  const epoch = Date.now();
  const event = (type, details = {}) => append(events, { hostTime: iso(), elapsedMs: Math.round(performance.now() - begin), type, ...details });
  const stop = (reason) => { if (!stopping) { stopping = true; event("stop_requested", { reason }); } };
  process.on("SIGINT", () => stop("Ctrl+C"));
  process.on("SIGTERM", () => stop("SIGTERM"));
  const handles = [];
  const logOut = fs.openSync(path.join(folder, "logcat.txt"), "w");
  const logErr = fs.openSync(path.join(folder, "logcat.stderr.txt"), "w");
  const logcat = spawn(ADB, ["-s", serial, "logcat", "-b", "main", "-b", "system", "-b", "events", "-b", "crash", "-v", "epoch", "-T", "1"], { windowsHide: true, stdio: ["ignore", logOut, logErr] });
  children.add(logcat);
  let logClosed = false;
  const logDone = new Promise((resolve) => {
    logcat.on("error", (error) => { event("logcat_error", { message: error.message }); stop("logcat 启动失败"); });
    logcat.on("close", (code) => { logClosed = true; children.delete(logcat); event("logcat_closed", { code }); if (!stopping) stop("logcat 意外退出"); resolve(); });
  });
  json(ACTIVE, { pid: process.pid, folder, serial, startedAt: round.startedAt });
  event("capture_started", { requestedProtection, sourcePackage: pkg });
  console.log("采集已开始。现在可以手动打开待测应用并复现。\n标记后回车：a 广告、s 摇动、c 点击、r 自动返回、b 手动返回、n 未复现、q 停止。\n输出：" + folder);
  let input;
  if (process.stdin.isTTY) {
    input = readline.createInterface({ input: process.stdin, output: process.stdout });
    const labels = { a: "广告出现", s: "摇动", c: "点击", r: "自动返回", b: "手动返回", n: "未复现" };
    input.on("line", (line) => line.trim() === "q" ? stop("用户停止") : event(labels[line.trim()] || "备注", { note: line, timing: "人工标记，非动作精确发生时刻" }));
  }
  const deadline = setTimeout(() => stop("达到采集时长"), duration * 1000);
  const control = setInterval(() => {
    for (const name of fs.readdirSync(path.join(folder, "inbox")).filter((name) => name.endsWith(".json"))) {
      const full = path.join(folder, "inbox", name);
      const mark = JSON.parse(fs.readFileSync(full, "utf8"));
      event(mark.type, { ...mark, elapsedMs: Date.parse(mark.hostTime) - epoch });
      fs.unlinkSync(full);
      if (mark.type === "stop") stop("外部停止按钮");
    }
  }, 150);

  async function loop(name, interval, sample) {
    let slot = 0;
    while (!stopping) {
      const remaining = begin + slot * interval - performance.now();
      if (remaining > 0) { await sleep(Math.min(remaining, 100)); continue; }
      const started = iso();
      const startMono = performance.now();
      try { await sample(slot); } catch (error) { event("sample_error", { stream: name, message: error.message }); }
      const following = Math.max(slot + 1, nextSlot(begin, performance.now(), interval));
      append(path.join(folder, "sampling.jsonl"), { stream: name, slot, plannedElapsedMs: slot * interval, started, ended: iso(), durationMs: Math.round(performance.now() - startMono), skippedSlots: following - slot - 1, stopped: stopping });
      slot = following;
    }
  }
  if (mode === "full") {
  handles.push(loop("screenshot", 1000, async (slot) => {
    const result = await run(["exec-out", "screencap", "-p"], 8000);
    const name = String(slot).padStart(5, "0");
    const ok = result.code === 0 && validPng(result.stdout);
    if (ok) fs.writeFileSync(path.join(folder, "screenshots", name + ".png"), result.stdout);
    const { stdout, ...metadata } = result;
    append(path.join(folder, "screenshots", "index.jsonl"), { ...metadata, slot, file: ok ? name + ".png" : null, bytes: stdout.length, ok, timestampMeaning: "截图命令开始/结束的主机时间；具体成像时刻位于区间内" });
  }));
  handles.push(loop("foreground", 1500, async (slot) => {
    await saveCommand(path.join(folder, "foreground"), String(slot).padStart(5, "0"), ["shell", "dumpsys", "activity", "activities"], 6000);
  }));
  handles.push(loop("page-passive", 10000, async (slot) => {
    // UiAutomator suppressed GuardService on this handset. Only passive dumpsys is safe here.
    const name = String(slot).padStart(5, "0");
    const result = await run(["shell", "dumpsys", "activity", "top"], 6000);
    const text = result.stdout.toString("utf8");
    fs.writeFileSync(path.join(folder, "pages", name + ".txt"), text);
    json(path.join(folder, "pages", name + ".meta.json"), { started: result.started, ended: result.ended, elapsedMs: result.elapsedMs, code: result.code, stderr: result.stderr, timedOut: result.timedOut, method: "dumpsys activity top", xmlCaptured: false, text: [], hasWebViewClass: /(?:android\.webkit\.|\b)WebView/.test(text), limitation: "本机 UIAutomator 会抑制被测无障碍服务，已禁止使用。被动 activity top 的视图信息受应用/系统限制；文字通过原始截图人工审阅，WebView 本身不证明是广告。" });
    if (!stopping) await saveCommand(path.join(folder, "pages"), name + "-accessibility", ["shell", "dumpsys", "accessibility"], 6000);
  }));
  }
  try {
    while (!stopping) await sleep(100);
  } finally {
    clearTimeout(deadline);
    clearInterval(control);
    input?.close();
    // Each in-flight command has a timeout. Let remote UI timeout/cleanup finish.
    await Promise.allSettled(handles);
    if (!logClosed) logcat.kill();
    await logDone;
    fs.closeSync(logOut);
    fs.closeSync(logErr);
    for (const child of children) child.kill();
    round.endedAt = iso();
    round.actualElapsedMs = Math.round(performance.now() - begin);
    round.remainingCollectorChildren = children.size;
    json(path.join(folder, "round.json"), round);
    event("capture_finished", { elapsedMs: round.actualElapsedMs });
    if (fs.existsSync(ACTIVE) && JSON.parse(fs.readFileSync(ACTIVE)).pid === process.pid) fs.unlinkSync(ACTIVE);
    console.log("本轮采集结束，采集子进程已收尾。原始数据仅在：" + folder);
  }
}

try {
  if (command === "doctor") await doctor();
  else if (command === "snapshot") {
    await bind();
    const folder = path.join(LOCAL, "snapshot-" + stamp());
    fs.mkdirSync(folder, { recursive: true });
    await Promise.all([
      (async () => {
        const result = await run(["exec-out", "screencap", "-p"], 10000);
        if (result.code === 0 && validPng(result.stdout)) fs.writeFileSync(path.join(folder, "screen.png"), result.stdout);
        const { stdout, ...meta } = result;
        json(path.join(folder, "screen.meta.json"), meta);
      })(),
      saveCommand(folder, "activity", ["shell", "dumpsys", "activity", "activities"]),
      saveCommand(folder, "page-passive", ["shell", "dumpsys", "activity", "top"], 6000),
      saveCommand(folder, "accessibility", ["shell", "dumpsys", "accessibility"], 6000),
    ]);
    console.log(folder);
  }
  else if (command === "environment") {
    await bind();
    const folder = path.join(LOCAL, "environment-" + stamp());
    await environment(folder, option("package") ? [option("package")] : []);
    console.log(folder);
  } else if (command === "start") await capture();
  else if (command === "stop") signal("stop");
  else if (command === "mark") signal(argv[0] || "备注", argv.slice(1).join(" "));
  else if (command === "status") console.log(active());
  else throw new Error("可用命令：doctor / environment / start / stop / mark / status");
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
