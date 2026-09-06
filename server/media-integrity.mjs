import path from "node:path";

const DEFINITIVE_PATTERNS = [
    /invalid as first byte of an EBML number/i,
    /EBML header parsing failed/i,
    /moov atom not found/i,
    /invalid data found when processing input/i,
    /error reading header/i,
    /end of file/i,
    /packet corrupt/i,
    /corrupt decoded frame/i,
    /error while decoding/i,
    /bytestream -\d+/i,
];

export function mediaDiagnostics(stderr = "", exitCode = 0) {
    const lines = String(stderr).replace(/\r/g, "").split("\n").map((line) => line.trim()).filter(Boolean);
    const evidence = lines.filter((line) => DEFINITIVE_PATTERNS.some((pattern) => pattern.test(line)));
    return { suspicious: exitCode !== 0 || evidence.length > 0, exitCode, evidence: [...new Set(evidence)].slice(-20), stderr: lines.slice(-40).join("\n") };
}

export function quarantinePath(realLibraryRoot, rootId, relativeFile, suffix = "") {
    const base = path.resolve(path.dirname(realLibraryRoot), "Quarentena BRasa", rootId);
    const relative = String(relativeFile || "");
    if (!relative || path.isAbsolute(relative) || relative.split(/[\\/]+/).includes("..")) throw new Error("Caminho de quarentena inválido.");
    const parsed = path.parse(relative);
    const target = path.resolve(base, parsed.dir, `${parsed.name}${suffix}${parsed.ext}`);
    if (target !== base && !target.startsWith(`${base}${path.sep}`)) throw new Error("Destino fora da quarentena.");
    return target;
}

export function scanFingerprint(file, stat) { return `${file}|${stat.size}|${Math.round(stat.mtimeMs)}`; }
