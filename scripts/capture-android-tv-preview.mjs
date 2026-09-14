import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const exec = promisify(execFile);
const args = process.argv.slice(2);
const value = name => { const index = args.indexOf(name); return index < 0 ? "" : args[index + 1] || ""; };
const serial = value("--serial"), name = value("--name"), page = value("--page");
const current = args.includes("--current");
const pages = new Set(["home", "movies", "series", "collections", "my-list", "search", "details", "player", "settings", "profiles", "server", "pairing", "profile-pin", "update"]);
if (!serial || !/^[\w.:-]+$/.test(serial) || !/^[a-z0-9][a-z0-9-]{0,79}$/.test(name)) throw new Error("Use --serial <dispositivo> --name <nome-ascii-do-print> e --page <rota> ou --current.");
if (current === Boolean(page) || (page && !pages.has(page))) throw new Error("Escolha uma rota de preview válida OU --current.");
if (!current && !serial.startsWith("emulator-")) throw new Error("A abertura automática do modo demonstrativo é restrita a emuladores. Em TV física, navegue e use --current.");
const waitMs = Number(value("--wait-ms") || 8000);
if (!Number.isFinite(waitMs) || waitMs < 0 || waitMs > 60000) throw new Error("--wait-ms deve estar entre 0 e 60000.");
const keys = value("--keys").split(",").filter(Boolean);
const allowedKeys = new Set(["DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "BACK", "MEDIA_PLAY_PAUSE", "MEDIA_PAUSE"]);
if (keys.some(key => !allowedKeys.has(key))) throw new Error("Tecla de navegação inválida.");
const adb = value("--adb") || path.join(root, "apps/android-tv/.toolchain/android-sdk/platform-tools/adb.exe");
const run = async parameters => (await exec(adb, ["-s", serial, ...parameters], { windowsHide: true, timeout: 120000, maxBuffer: 20 * 1024 * 1024, encoding: "buffer" })).stdout;
const packageDump = (await run(["shell", "dumpsys", "package", "com.brasa.tv"])).toString("utf8");
const versionName = packageDump.match(/versionName=([^\s]+)/)?.[1];
const versionCode = Number(packageDump.match(/versionCode=(\d+)/)?.[1]);
if (!versionName || !Number.isSafeInteger(versionCode) || !/^\d+\.\d+\.\d+$/.test(versionName)) throw new Error("APK BRasa não encontrado ou versão inválida.");
const destination = path.join(root, "preview", "android-tv", versionName);
const pngPath = path.join(destination, `${name}.png`), manifestPath = path.join(destination, `${name}.json`);
for (const file of [pngPath, manifestPath]) {
    if (!args.includes("--replace") && await fs.stat(file).then(() => true, () => false)) throw new Error(`Captura já existe: ${file}. Use outro nome ou --replace.`);
}
if (page) {
    if (!/DEBUGGABLE/.test(packageDump)) throw new Error("As rotas demonstrativas exigem APK debug. Para release use --current.");
    await run(["shell", "am", "force-stop", "com.brasa.tv"]);
    await run(["shell", "am", "start", "-W", "-n", "com.brasa.tv/.app.MainActivity", "--ez", "preview", "true", "--es", "previewPage", page]);
}
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const assertForeground = async () => {
    const activity = (await run(["shell", "dumpsys", "activity", "activities"])).toString("utf8");
    const resumed = activity.split(/\r?\n/).find(line => /mResumedActivity|topResumedActivity/.test(line)) || "";
    if (!resumed.includes("com.brasa.tv/")) throw new Error("BRasa não está em primeiro plano; operação recusada.");
};
await wait(waitMs);
for (const key of keys) {
    await assertForeground();
    await run(["shell", "input", "keyevent", `KEYCODE_${key}`]);
    await wait(1000);
}
if (keys.length) await wait(2000);
await assertForeground();
const png = await run(["exec-out", "screencap", "-p"]);
if (png.length < 24 || !png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) throw new Error("A captura não é PNG.");
const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
const density = (await run(["shell", "wm", "density"])).toString("utf8").trim();
const androidApi = (await run(["shell", "getprop", "ro.build.version.sdk"])).toString("utf8").trim();
const manifest = {
    schemaVersion: 1, file: path.basename(pngPath), capturedAt: new Date().toISOString(),
    packageName: "com.brasa.tv", versionName, versionCode, androidApi: Number(androidApi), width, height, density,
    source: serial.startsWith("emulator-") ? "android-emulator-apk" : "physical-device-apk",
    mode: page ? "debug-demo-data" : "current-app-state", initialRoute: page || null,
    navigationKeys: keys, uiScale: "app-setting-not-overridden", imageModified: false,
    note: page === "player" ? "PreviewPlayerScreen demonstrativo; não representa o PlayerContent de produção nem comprova reprodução." : "PNG direto do APK. Nenhuma reconstrução HTML nem edição da imagem.",
    sha256: crypto.createHash("sha256").update(png).digest("hex"),
};
await fs.mkdir(destination, { recursive: true });
await fs.writeFile(pngPath, png);
await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
console.log(JSON.stringify({ file: path.relative(root, pngPath), width, height, versionName, mode: manifest.mode }));
