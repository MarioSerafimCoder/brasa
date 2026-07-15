let loading = false;
let signature = "";
const content = document.querySelector("#content");

window.addEventListener("hashchange", schedule);
new MutationObserver(schedule).observe(content, { childList: true, subtree: false });
schedule();

function schedule() { if (location.hash.split("/")[0] === "#settings") setTimeout(render, 0); }

async function render(force = false) {
    if (loading || location.hash.split("/")[0] !== "#settings") return;
    loading = true;
    try {
        const response = await fetch(force ? "/api/admin/media-tools/retest" : "/api/admin/media-tools", {
            method: force ? "POST" : "GET", credentials: "same-origin",
            headers: force ? { "Content-Type": "application/json", "X-BRasa-Request": "1", "X-BRasa-CSRF": await csrf() } : { Accept: "application/json" },
            body: force ? "{}" : undefined,
        });
        if (!response.ok) return;
        const tools = (await response.json()).data;
        const next = JSON.stringify(tools);
        if (!force && next === signature && document.querySelector("#mediaToolsPanel")) return;
        signature = next;
        document.querySelector("#mediaToolsPanel")?.remove();
        content.insertAdjacentHTML("beforeend", panel(tools));
        document.querySelector("#mediaToolsRetest").onclick = async (event) => { event.currentTarget.disabled = true; await render(true); };
    } finally { loading = false; }
}

async function csrf() { const response = await fetch("/api/admin/csrf", { credentials: "same-origin" }); return (await response.json()).data?.csrf || ""; }
function safe(value) { return String(value ?? "").replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]); }
function row(label, value) { return `<div><small>${safe(label)}</small><strong>${safe(value)}</strong></div>`; }
function panel(tools) {
    const nvenc = tools.validation?.nvenc || {};
    return `<section id="mediaToolsPanel" class="panel" style="margin-top:16px"><h2>FFmpeg e aceleração de vídeo</h2><div class="detail-list">
        ${row("FFmpeg", tools.ffmpegAvailable ? "Disponível" : "Ausente")}${row("Caminho FFmpeg", tools.ffmpegPath || "Não encontrado")}
        ${row("FFprobe", tools.ffprobeAvailable ? "Disponível" : "Ausente")}${row("Caminho FFprobe", tools.ffprobePath || "Não encontrado")}
        ${row("NVENC", tools.hardwareAcceleration?.nvenc ? "Ativo" : "Indisponível")}${row("Encoder selecionado", tools.selectedEncoder || "libx264")}
        ${row("Diagnóstico NVENC", nvenc.message || "Não testado")}${row("Último teste", tools.refreshedAt ? new Date(tools.refreshedAt).toLocaleString("pt-BR") : "—")}
    </div><p class="muted">H.264: ${tools.encoders?.nvenc?.h264_nvenc ? "sim" : "não"} · HEVC: ${tools.encoders?.nvenc?.hevc_nvenc ? "sim" : "não"} · AV1: ${tools.encoders?.nvenc?.av1_nvenc ? "sim" : "não"}</p><button id="mediaToolsRetest" class="button" type="button">Testar novamente</button></section>`;
}
