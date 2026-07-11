import { ForbiddenError } from "./app-errors.mjs";

export function validateLocalWriteRequest(request, { port } = {}) {
    if (!["POST", "PUT", "PATCH", "DELETE"].includes(String(request.method || "").toUpperCase())) return true;
    const host = String(request.headers?.host || "").toLowerCase();
    const allowedHosts = new Set([`127.0.0.1:${port}`, `localhost:${port}`, `[::1]:${port}`]);
    if (!allowedHosts.has(host)) throw new ForbiddenError("Origem local inválida.");
    if (String(request.headers?.["x-brasa-request"] || "") !== "1") throw new ForbiddenError("Requisição local não reconhecida.");
    const source = String(request.headers?.origin || request.headers?.referer || "");
    if (source) {
        let sourceHost = ""; try { sourceHost = new URL(source).host.toLowerCase(); } catch { throw new ForbiddenError("Origem inválida."); }
        if (!allowedHosts.has(sourceHost)) throw new ForbiddenError("Origem externa não permitida.");
    }
    return true;
}
