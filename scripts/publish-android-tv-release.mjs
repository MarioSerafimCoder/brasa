import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { loadPublishedUpdate, publishAtomic, UPDATE_FILE_NAME } from "../server/android-tv-release.mjs";

const exec = promisify(execFile);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const androidRoot = path.join(root, "apps", "android-tv");
const updatesRoot = path.join(root, "data", "android-tv-updates");
const args = process.argv.slice(2);

if (args.includes("--status")) {
    const current = await loadPublishedUpdate(updatesRoot);
    console.log(current ? JSON.stringify(current.manifest, null, 2) : "Nenhuma atualização Android TV publicada.");
    process.exit(0);
}

const apk = path.join(androidRoot, "app", "build", "outputs", "apk", "release", "app-release.apk");
const notesFile = value("--notes-file");
if (!await exists(apk)) throw new Error("app-release.apk não encontrado. Compile a versão release primeiro.");
const sdkRoot = process.env.ANDROID_HOME || path.join(androidRoot, ".toolchain", "android-sdk");
const outputMetadata = JSON.parse(await fs.readFile(path.join(path.dirname(apk), "output-metadata.json"), "utf8"));
const artifact = outputMetadata.elements?.find((item) => item.outputFile === path.basename(apk)) || outputMetadata.elements?.[0];
const packageName = outputMetadata.applicationId, versionCode = Number(artifact?.versionCode), versionName = String(artifact?.versionName || "");
if (packageName !== "com.brasa.tv") throw new Error("O APK não pertence ao pacote com.brasa.tv.");
if (!Number.isSafeInteger(versionCode) || versionCode < 1 || !/^\d+\.\d+\.\d+/.test(versionName)) throw new Error("Os metadados de versão do APK são inválidos.");
const apksigner = await findTool(sdkRoot, "apksigner.bat"), apksignerJar = await findApksignerJar(apksigner);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java";
const signer = await run(java, ["-jar", apksignerJar, "verify", "--verbose", "--print-certs", apk]);
if (/CN=Android Debug/i.test(signer)) throw new Error("APK debug não pode ser publicado.");
const signingCertificateSha256 = match(signer, /certificate SHA-256 digest:\s*([a-fA-F0-9]{64})/i, "fingerprint do APK").toUpperCase();
const expected = (await fs.readFile(path.join(androidRoot, "release-certificate.sha256"), "utf8")).trim().replace(/:/g, "").toUpperCase();
if (!/^[A-F0-9]{64}$/.test(expected) || signingCertificateSha256 !== expected) throw new Error("O certificado do APK não corresponde ao fingerprint release registrado.");
const stat = await fs.stat(apk), sha256 = await hash(apk), releaseNotes = notesFile ? JSON.parse(await fs.readFile(path.resolve(notesFile), "utf8")) : [];
const manifest = { schemaVersion: 1, packageName, versionCode, versionName, minimumServerApi: 1, minimumAndroidApi: 23, fileName: UPDATE_FILE_NAME, sizeBytes: stat.size, sha256, signingCertificateSha256, mandatory: false, publishedAt: new Date().toISOString(), releaseNotes };
await publishAtomic({ updatesRoot, apkSource: apk, manifest });
console.log(`BRasa TV ${versionName} (${versionCode}) publicado localmente.`);
console.log(`SHA-256: ${sha256}`);
console.log(`Tamanho: ${stat.size} bytes`);

function value(flag) { const index = args.indexOf(flag); return index >= 0 ? args[index + 1] : ""; }
async function exists(file) { return fs.stat(file).then((item) => item.isFile()).catch(() => false); }
async function findTool(sdk, name) {
    for (const candidate of [path.join(sdk, "cmdline-tools", "latest", "bin", name), path.join(sdk, "tools", "bin", name)]) {
        if (await exists(candidate)) return candidate;
    }
    const roots = await fs.readdir(path.join(sdk, "build-tools"), { withFileTypes: true }).catch(() => []);
    for (const item of roots.filter((entry) => entry.isDirectory()).sort((a, b) => b.name.localeCompare(a.name, undefined, { numeric: true }))) {
        const candidate = path.join(sdk, "build-tools", item.name, name);
        if (await exists(candidate)) return candidate;
    }
    return name;
}
async function findApksignerJar(script) {
    const directory = path.dirname(script), candidates = [path.join(directory, "apksigner.jar"), path.join(directory, "lib", "apksigner.jar")];
    for (const candidate of candidates) if (await exists(candidate)) return candidate;
    throw new Error("apksigner.jar não encontrado no Android SDK.");
}
async function run(command, values) {
    let executable = command, parameters = values;
    if (process.platform === "win32" && /\.(?:bat|cmd)$/i.test(command)) {
        executable = process.env.ComSpec || "cmd.exe";
        const commandLine = [`"${command}"`, ...values.map((item) => `"${String(item).replaceAll('"', '""')}"`)].join(" ");
        parameters = ["/d", "/s", "/c", `"${commandLine}"`];
    }
    const { stdout, stderr } = await exec(executable, parameters, { windowsHide: true, maxBuffer: 4 * 1024 * 1024 });
    return `${stdout}\n${stderr}`;
}
function match(text, pattern, label) { const result = text.match(pattern); if (!result) throw new Error(`Não foi possível extrair ${label}.`); return result[1]; }
async function hash(file) { const digest = crypto.createHash("sha256"), data = await fs.readFile(file); return digest.update(data).digest("hex").toUpperCase(); }
