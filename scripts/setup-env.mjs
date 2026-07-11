import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const REQUIRED = Object.freeze({
    OMDB_API_KEY: "",
    OPENSUBTITLES_API_KEY: "",
    TMDB_API_KEY: "",
    TMDB_READ_TOKEN: "",
    SUBTITLE_LANGUAGES: "pt-br,en"
});

export async function ensureEnvFile({ rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), ".."), logger = console, quiet = false } = {}) {
    const envPath = path.join(rootDir, ".env"), examplePath = path.join(rootDir, ".env.example");
    let content = await fs.readFile(envPath, "utf8").catch((error) => error.code === "ENOENT" ? null : Promise.reject(error));
    let created = false, updated = false;
    if (content === null) {
        content = await fs.readFile(examplePath, "utf8").catch(() => serialize(REQUIRED));
        created = true;
        if (!quiet) logger.log("BRasa: .env não encontrado; arquivo criado a partir de .env.example.");
    }
    const parsed = parseEnv(content), missing = Object.keys(REQUIRED).filter((key) => !(key in parsed));
    if (missing.length) {
        const suffix = missing.map((key) => `${key}=${REQUIRED[key]}`).join("\n");
        content = `${content.replace(/\s*$/, "")}\n${suffix}\n`;
        updated = true;
    }
    if (created || updated) await atomicWrite(envPath, content);
    const finalValues = parseEnv(content), empty = Object.keys(REQUIRED).filter((key) => key !== "SUBTITLE_LANGUAGES" && !String(finalValues[key] || "").trim());
    if (!quiet) {
        if (!created && !updated) logger.log("BRasa: .env existente preservado.");
        if (missing.length) logger.log(`BRasa: variáveis ausentes adicionadas: ${missing.join(", ")}.`);
        if (empty.length) logger.warn(`BRasa: chaves ainda vazias: ${empty.join(", ")}. Metadados, imagens ou legendas podem ficar incompletos até o preenchimento.`);
        else logger.log("BRasa: configuração de APIs preenchida.");
    }
    return { created, updated, missing, empty, path: envPath };
}

export function parseEnv(content) {
    const values = {};
    String(content || "").split(/\r?\n/).forEach((line) => {
        const value = line.trim(); if (!value || value.startsWith("#") || !value.includes("=")) return;
        const separator = value.indexOf("="), key = value.slice(0, separator).trim();
        if (key) values[key] = value.slice(separator + 1).trim().replace(/^(["'])(.*)\1$/, "$2");
    });
    return values;
}

function serialize(values) { return `${Object.entries(values).map(([key, value]) => `${key}=${value}`).join("\n")}\n`; }
async function atomicWrite(file, content) { const temporary = `${file}.${process.pid}.tmp`; await fs.writeFile(temporary, content, { encoding: "utf8", mode: 0o600 }); await fs.rename(temporary, file); }

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
    await ensureEnvFile();
}
