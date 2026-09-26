import fs from "node:fs";
import path from "node:path";
import { urlsFromLine, resumedComponent } from "./evidence.mjs";

const folder = path.resolve(process.argv[2] || "");
if (!fs.existsSync(path.join(folder, "round.json"))) throw new Error("请指定完成采集的 round 目录");
const read = (name) => fs.readFileSync(path.join(folder, name), "utf8");
const load = (name) => JSON.parse(read(name));
const lines = (name) => fs.existsSync(path.join(folder, name)) ? read(name).trim().split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line)) : [];
const round = load("round.json");
if (!round.endedAt) throw new Error("采集尚未结束，请先停止再分析");
const out = path.join(folder, "analysis");
fs.mkdirSync(out, { recursive: true });
const save = (name, value) => fs.writeFileSync(path.join(out, name), JSON.stringify(value, null, 2));
const frames = lines("screenshots/index.jsonl");
const samples = lines("sampling.jsonl");
const events = lines("events.jsonl");
const urls = [];
const transitions = [];
const relevantLogs = [];

function inspect(text, source, hostTime = null) {
  text.split(/\r?\n/).forEach((line, index) => {
    const epoch = source === "logcat.txt" ? line.match(/^\s*(\d{10}\.\d+)\s/)?.[1] : null;
    const time = epoch ? { deviceTime: new Date(Number(epoch) * 1000).toISOString() } : { hostTime };
    const component = line.match(/\bcmp=([A-Za-z0-9_.$]+\/[A-Za-z0-9_.$]+)/)?.[1] || null;
    for (const value of urlsFromLine(line)) {
      urls.push({ observed: value, ...time, source, line: index + 1, component, context: line,
        classification: /\bSTART\b.*\bdat=/.test(line) ? "Activity 启动请求中的链接，仍需确认是否实际展示" : "文本中出现的 URL，不单独证明页面访问或广告跳转",
        completeness: /\.\.\.|…/.test(value) ? "可能截断；完整 URL 未捕获" : "仅保留原文暴露的字符串，不保证系统没有隐藏后续内容" });
    }
    if (source === "logcat.txt" && /ActivityTaskManager.*START|wm_(?:create|resume|pause|stop|finish)_activity|am_(?:create|resume|pause|stop|finish)_activity|returnguard|ERR_UNKNOWN_URL_SCHEME|返回尝试|达到返回上限|GuardService/.test(line)) {
      relevantLogs.push({ ...time, source, line: index + 1, text: line });
    }
  });
}
inspect(read("logcat.txt"), "logcat.txt");
for (const name of fs.readdirSync(path.join(folder, "foreground")).filter((name) => name.endsWith(".txt")).sort()) {
  const meta = load("foreground/" + name.replace(/\.txt$/, ".meta.json"));
  const text = read("foreground/" + name);
  transitions.push({ hostStart: meta.started, hostEnd: meta.ended, component: resumedComponent(text), source: "foreground/" + name, code: meta.code });
  inspect(text, "foreground/" + name, meta.started);
}
const pages = [];
for (const name of fs.readdirSync(path.join(folder, "pages")).filter((name) => /^\d+\.meta\.json$/.test(name)).sort()) {
  const meta = load("pages/" + name);
  pages.push({ ...meta, source: "pages/" + name });
  inspect(read("pages/" + name.replace(/\.meta.json$/, ".txt")), "pages/" + name.replace(/\.meta.json$/, ".txt"), meta.started);
}
save("url-evidence.local.json", urls);
save("foreground-timeline.json", transitions);
save("relevant-log-evidence.local.json", relevantLogs);
save("operation-timeline.json", events);
const summary = {
  sourcePackage: round.sourcePackage, actualProtection: round.actualProtection, targetSelected: round.targetSelected,
  requestedSeconds: round.requestedSeconds, actualElapsedMs: round.actualElapsedMs,
  screenshotAttempts: frames.length, validScreenshots: frames.filter((frame) => frame.ok).length,
  screenshotSkippedSlots: samples.filter((sample) => sample.stream === "screenshot").reduce((sum, sample) => sum + sample.skippedSlots, 0),
  screenshotDurationMs: frames.map((frame) => frame.elapsedMs),
  uiAttempts: pages.length, validUi: pages.filter((page) => page.xmlCaptured).length,
  uiWarnings: pages.filter((page) => page.stderr || page.code !== 0).length,
  observedComponents: [...new Set(transitions.map((entry) => entry.component).filter(Boolean))],
  urlEvidenceCount: urls.length,
  conclusion: "待审阅截图、用户操作和返回日志；未捕获广告不得判为通过；不同广告不得作为同场景对照",
};
save("summary.json", summary);
fs.writeFileSync(path.join(out, "采集质量与证据索引.md"), `# 本轮采集质量与证据索引（本地原始分析）\n\n此文是自动索引，不是广告效果结论，也未经过分享脱敏。\n\n- 源应用：${round.sourcePackage}\n- 实际保护状态：${round.actualProtection}；计划状态：${round.requestedProtection}\n- 开始：${round.startedAt}；结束：${round.endedAt}\n- 截图：${summary.validScreenshots}/${summary.screenshotAttempts} 次有效，漏过 ${summary.screenshotSkippedSlots} 个采样槽；每帧时刻和耗时见 screenshots/index.jsonl。\n- UI：${summary.validUi}/${summary.uiAttempts} 次得到层级，${summary.uiWarnings} 次有错误或警告。\n- 系统日志：logcat.txt；提取线索：relevant-log-evidence.local.json。\n- 前台采样：foreground-timeline.json。两次采样之间的短暂切换可能遗漏，须结合系统日志。\n- URL：url-evidence.local.json 中 ${urls.length} 条原文证据；${urls.length ? "出现 URL 不等于访问过该页面，不自动判为广告。" : "HTTP(S) URL 与深度链接未捕获。"}\n- 操作：operation-timeline.json，人工标记记录接收时间，可能晚于实际动作。\n- logcat 使用手机时钟；截图、操作标记使用电脑时钟。偏差依据 environment/clock.txt 及 meta.json 起止区间估计。\n\n同应用内网页须比对广告文案、页面文字、URL、Activity 和返回行为；不能只凭包名未变或 WebView 判定。返回尝试次数不能直接等同于有效返回次数。无广告场景写“未复现”。\n\n分享前须审阅所有准备发送的文件，遮盖账户、验证码、令牌等，原始数据不上传。\n`);
console.log(JSON.stringify(summary, null, 2));
