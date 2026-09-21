export function markerEditorHtml() {
    return `<section class="wide"><h3>Pular abertura e créditos</h3><p>Defina o início e o fim de cada trecho. O botão na TV pula somente até o fim indicado, preservando cenas pós-créditos.</p><div id="markerEditor" role="status">Carregando marcações…</div></section>`;
}

export function formatMarkerTime(ms) {
    const value = Math.max(0, Math.round(ms));
    return `${Math.floor(value / 60000)}:${String(Math.floor(value / 1000) % 60).padStart(2, "0")}.${String(value % 1000).padStart(3, "0")}`;
}

export function parseMarkerTime(value) {
    const match = /^(\d+):([0-5]\d)(?:[.,](\d{1,3}))?$/.exec(value.trim());
    if (!match) throw new Error("Use minutos:segundos.milissegundos, por exemplo 1:30.000.");
    return Number(match[1]) * 60000 + Number(match[2]) * 1000 + Number((match[3] || "").padEnd(3, "0"));
}

export async function bindMarkerEditor(key, api, toast) {
    const root = document.querySelector("#markerEditor");
    if (!root) return;
    const endpoint = `/api/admin/library/${encodeURIComponent(key)}/markers`;
    try {
        const data = await api(endpoint);
        if (!root.isConnected) return;
        if (!data.durationMs) { root.textContent = "Use Analisar mídia e reabra este conteúdo para editar os tempos."; return; }
        root.innerHTML = `<p>${data.mode === "manual" ? "Marcações personalizadas" : "Capítulos detectados automaticamente"} • Duração: ${formatMarkerTime(data.durationMs)}${data.stale ? " • O arquivo mudou; revise as marcações." : ""}</p>
            <form id="markerForm" class="form-grid">${[["intro", "Abertura"], ["credits", "Créditos"]].map(([kind, label]) => {
                const marker = data.markers.find(item => item.kind === kind);
                return `<fieldset class="wide"><legend>${label}</legend><label><input type="checkbox" name="${kind}Enabled" ${marker ? "checked" : ""}> Exibir botão de pular</label>
                    <div class="form-grid"><label class="field"><span>Início (min:seg.ms)</span><input name="${kind}Start" inputmode="decimal" value="${formatMarkerTime(marker?.startMs || 0)}"></label>
                    <label class="field"><span>Fim (min:seg.ms)</span><input name="${kind}End" inputmode="decimal" value="${formatMarkerTime(marker?.endMs || 0)}"></label></div></fieldset>`;
            }).join("")}<div class="actions wide"><button class="button primary" type="submit">Salvar marcações</button><button class="button" type="button" data-automatic>Usar capítulos automáticos</button></div><small class="wide">Para desativar os botões, desmarque os dois trechos e salve. As alterações valem ao abrir o vídeo novamente.</small></form>`;
        const form = root.querySelector("form");
        async function update(method, body) {
            form.querySelectorAll("button").forEach(button => { button.disabled = true; });
            try { await api(endpoint, { method, body }); toast("Marcações salvas. Reabra o vídeo na TV para atualizar."); await bindMarkerEditor(key, api, toast); }
            catch (error) { toast(error.message, true); form.querySelectorAll("button").forEach(button => { button.disabled = false; }); }
        }
        form.onsubmit = event => {
            event.preventDefault();
            try {
                const fields = new FormData(form);
                const markers = ["intro", "credits"].filter(kind => fields.has(`${kind}Enabled`)).map(kind => ({ kind, startMs: parseMarkerTime(fields.get(`${kind}Start`)), endMs: parseMarkerTime(fields.get(`${kind}End`)) }));
                update("PUT", { markers });
            } catch (error) { toast(error.message, true); }
        };
        root.querySelector("[data-automatic]").onclick = () => update("DELETE");
    } catch (error) { root.textContent = error.message; }
}
