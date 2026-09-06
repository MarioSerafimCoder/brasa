import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { absoluteLibraryRoots, isProcessableVideo } from "../server/library-config.mjs";
import { getMediaToolsStatus } from "../server/media-tools.mjs";
import { mediaDiagnostics, quarantinePath, scanFingerprint } from "../server/media-integrity.mjs";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const applyQuarantine = process.argv.includes("--quarantine");
const matchText = argumentValue("--match").toLocaleLowerCase("pt-BR");
const stateFile = path.join(rootDir, "data", "library-integrity-scan.json");
const reportJson = path.join(rootDir, "data", "library-integrity-report.json");
const reportTxt = path.join(rootDir, "data", "library-integrity-report.txt");
const tools = await getMediaToolsStatus(rootDir);
if (!tools.ffmpegAvailable) throw new Error("FFmpeg não está disponível para a auditoria.");

const prior = await readJson(stateFile, { version: 1, entries: {} });
const entries = prior.entries || {};
const roots = [];
for (const libraryRoot of absoluteLibraryRoots(rootDir)) {
    const realRoot = await fs.realpath(libraryRoot.absolutePath).catch(() => null);
    if (realRoot) roots.push({ ...libraryRoot, realRoot });
}
const files = [];
for (const libraryRoot of roots) await walk(libraryRoot.realRoot, libraryRoot, files);
if (matchText) files.splice(0, files.length, ...files.filter((item) => item.file.toLocaleLowerCase("pt-BR").includes(matchText)));
files.sort((a, b) => a.file.localeCompare(b.file, "pt-BR"));
const totalBytes = files.reduce((sum, item) => sum + item.stat.size, 0);
let completedBytes = 0, checked = 0, quarantined = 0, defective = 0;

for (const item of files) {
    const fingerprint = scanFingerprint(item.file, item.stat);
    const previous = entries[fingerprint];
    if (previous?.status === "ok" || previous?.status === "quarantined") {
        completedBytes += item.stat.size; checked++;
        if (previous.status === "quarantined") quarantined++;
        continue;
    }
    console.log(`[${checked + 1}/${files.length}] ${percentage(completedBytes, totalBytes)}% — ${item.relativeFile}`);
    const demux = await ffmpeg(["-nostdin", "-hide_banner", "-v", "error", "-i", item.file, "-map", "0", "-c", "copy", "-f", "null", "NUL"]);
    const initial = mediaDiagnostics(demux.stderr, demux.code);
    let confirmation = null, status = "ok", destination = "";
    if (initial.suspicious) {
        const decoded = await ffmpeg(["-nostdin", "-hide_banner", "-v", "error", "-xerror", "-i", item.file, "-map", "0:v:0", "-map", "0:a?", "-threads", "0", "-f", "null", "NUL"]);
        confirmation = mediaDiagnostics(decoded.stderr, decoded.code);
        if (confirmation.suspicious || initial.evidence.length) {
            defective++;
            status = "defective";
            if (applyQuarantine) {
                destination = await availableDestination(item);
                await fs.mkdir(path.dirname(destination), { recursive: true });
                await fs.rename(item.file, destination);
                status = "quarantined";
                quarantined++;
                console.log(`  QUARENTENA: ${destination}`);
            } else console.log("  DEFEITUOSO confirmado.");
        }
    }
    entries[fingerprint] = {
        status, source: item.file, destination, rootId: item.id, relativeFile: item.relativeFile,
        size: item.stat.size, mtimeMs: Math.round(item.stat.mtimeMs), checkedAt: new Date().toISOString(),
        evidence: [...new Set([...(initial.evidence || []), ...(confirmation?.evidence || [])])].slice(-30),
        diagnostic: confirmation?.stderr || initial.stderr,
    };
    checked++; completedBytes += item.stat.size;
    await saveState();
}

await saveState(true);
console.log(`Auditoria concluída: ${checked} arquivo(s), ${defective} defeituoso(s), ${quarantined} em quarentena.`);

async function walk(directory, libraryRoot, output) {
    for (const entry of await fs.readdir(directory, { withFileTypes: true }).catch(() => [])) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) await walk(file, libraryRoot, output);
        else if (entry.isFile()) {
            const stat = await fs.stat(file);
            if (isProcessableVideo(entry.name, stat.size)) output.push({ ...libraryRoot, file, stat, relativeFile: path.relative(libraryRoot.realRoot, file) });
        }
    }
}

function ffmpeg(args) {
    return new Promise((resolve) => {
        let stderr = "", settled = false;
        const child = spawn(tools.ffmpegPath, args, { windowsHide: true, stdio: ["ignore", "ignore", "pipe"] });
        child.stderr.on("data", (chunk) => { stderr = (stderr + chunk).slice(-128_000); });
        child.on("error", (error) => { if (!settled) { settled = true; resolve({ code: -1, stderr: `${stderr}\n${error.message}` }); } });
        child.on("close", (code) => { if (!settled) { settled = true; resolve({ code: Number(code ?? -1), stderr }); } });
    });
}

async function availableDestination(item) {
    let suffix = "", attempt = 0;
    while (true) {
        const candidate = quarantinePath(item.realRoot, item.id, item.relativeFile, suffix);
        if (!await exists(candidate)) return candidate;
        suffix = `.defeituoso-${++attempt}`;
    }
}

async function saveState(final = false) {
    const values = Object.values(entries), bad = values.filter((item) => ["defective", "quarantined"].includes(item.status));
    const healthy = values.filter((item) => item.status === "ok");
    const state = { version: 1, startedAt: prior.startedAt || new Date().toISOString(), updatedAt: new Date().toISOString(), completedAt: final ? new Date().toISOString() : "", applyQuarantine, totalFiles: files.length, totalBytes, checked, entries };
    await atomicJson(stateFile, state);
    const report = { generatedAt: state.updatedAt, completed: final, totals: { files: files.length, auditedFiles: values.length, healthy: healthy.length, bytes: totalBytes, checked, defective: bad.length, quarantined: bad.filter((item) => item.status === "quarantined").length }, items: bad };
    await atomicJson(reportJson, report);
    const lines = ["AUDITORIA DE INTEGRIDADE DA BIBLIOTECA BRASA", `Gerado: ${report.generatedAt}`, `Resultado acumulado: ${report.totals.auditedFiles} | Íntegros: ${report.totals.healthy} | Defeituosos: ${report.totals.defective} | Quarentena: ${report.totals.quarantined}`, `Arquivos atualmente disponíveis: ${files.length} | Verificados nesta execução: ${checked}`, ""];
    for (const item of bad) lines.push(`[${item.status.toUpperCase()}] ${item.relativeFile}`, `Origem: ${item.source}`, `Destino: ${item.destination || "não movido"}`, ...(item.evidence || []).map((line) => `Erro: ${line}`), "");
    await fs.writeFile(`${reportTxt}.tmp`, `${lines.join("\n")}\n`, "utf8");
    await fs.rename(`${reportTxt}.tmp`, reportTxt);
}

async function atomicJson(file, value) { await fs.writeFile(`${file}.tmp`, `${JSON.stringify(value, null, 2)}\n`); await fs.rename(`${file}.tmp`, file); }
async function readJson(file, fallback) { return fs.readFile(file, "utf8").then(JSON.parse).catch(() => fallback); }
async function exists(file) { return fs.stat(file).then(() => true).catch(() => false); }
function percentage(value, total) { return total ? (value / total * 100).toFixed(1) : "100.0"; }
function argumentValue(name) { const index = process.argv.indexOf(name); return index >= 0 ? String(process.argv[index + 1] || "") : ""; }
