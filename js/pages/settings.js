import { applyPreferences, getPreferences, savePreferences } from "../utils/preferences.js";
import { getActiveProfile, initializeProfiles } from "../utils/profiles.js";
import { createProfile, listProfiles, removeProfile, setProfilePin, updateProfile } from "../services/profile-service.js";
import { getMediaQueue, getMediaSettings, getMediaToolsStatus, pauseMediaQueue, resumeMediaQueue, saveMediaSettings } from "../services/media-service.js";
import { installPageTransitions } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";

let preferences = applyPreferences();

init();

async function init() {
    installPageTransitions();
    installPageSidebar("settings");
    await initializeProfiles();
    if (getActiveProfile()?.kind === "kids") {
        location.replace("../index.html");
        return;
    }
    preferences = applyPreferences();
    const profileName = document.getElementById("settingsProfileName");
    if (profileName) profileName.textContent = getActiveProfile()?.name || "perfil atual";
    hideUnsupportedSettings();
    hydrateControls(preferences);
    installColorSwatches();
    bindControls();
    installProfileManager();
    await installMediaCenter();

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

async function installMediaCenter(){const badge=document.getElementById("mediaToolsBadge"),summary=document.getElementById("mediaToolsSummary"),queueText=document.getElementById("mediaQueueStatus"),queueDetails=document.getElementById("mediaQueueDetails"),toggle=document.getElementById("toggleMediaQueue");if(!badge)return;try{const [tools,settings,queue]=await Promise.all([getMediaToolsStatus(),getMediaSettings(),getMediaQueue()]);badge.textContent=tools.ffmpegAvailable&&tools.ffprobeAvailable?"Ferramentas disponíveis":"Configuração necessária";badge.classList.toggle("is-ready",tools.ffmpegAvailable&&tools.ffprobeAvailable);summary.innerHTML=`<span>FFmpeg: <strong>${tools.ffmpegAvailable?"disponível":"não encontrado"}</strong></span><span>FFprobe: <strong>${tools.ffprobeAvailable?"disponível":"não encontrado"}</strong></span><span>GPU: <strong>${[tools.hardwareAcceleration.nvenc&&"NVIDIA",tools.hardwareAcceleration.qsv&&"Intel",tools.hardwareAcceleration.amf&&"AMD"].filter(Boolean).join(", ")||"CPU"}</strong></span><small>${tools.version||"Defina FFMPEG_PATH e FFPROBE_PATH no arquivo .env."}</small>`;document.querySelectorAll("[data-media-setting]").forEach((control)=>{const value=settings[control.dataset.mediaSetting];control.type==="checkbox"?control.checked=Boolean(value):control.value=value;control.addEventListener("change",async()=>{const next=control.type==="checkbox"?control.checked:control.type==="number"?Number(control.value):control.value;await saveMediaSettings({[control.dataset.mediaSetting]:next});});});queueText.textContent=queue.active.length?"Processando mídia":queue.queued.length?`${queue.queued.length} na fila`:"Fila vazia";queueDetails.textContent=queue.active[0]||"";toggle.textContent=queue.paused?"Retomar fila":"Pausar fila";toggle.onclick=async()=>{queue.paused?await resumeMediaQueue():await pauseMediaQueue();location.reload();};}catch(error){badge.textContent="Central indisponível";summary.textContent=error.message;}}

function installProfileManager(){const root=document.getElementById("profilesManager"),add=document.getElementById("addProfileButton");if(!root||!add)return;const render=()=>{const active=getActiveProfile();root.innerHTML=listProfiles().map((p)=>`<article class="profile-manager-card"><span class="profile-manager-avatar is-${p.avatar?.color||"blue"}">${p.initials}</span><div><strong>${p.name}</strong><small>${p.kind==="kids"?"Infantil":"Adulto"}${p.hasPin?" • PIN configurado":""}</small></div><span><button type="button" data-edit-profile="${p.id}">Editar</button><button type="button" data-pin-profile="${p.id}">${p.hasPin?"Trocar PIN":"Definir PIN"}</button><button type="button" data-delete-profile="${p.id}" ${p.id===active?.id?"disabled title=\"Troque de perfil antes de excluir\"":""}>Excluir</button></span></article>`).join("");};add.addEventListener("click",()=>openProfileEditor(null,render));root.addEventListener("click",async(event)=>{const edit=event.target.closest("[data-edit-profile]");if(edit)openProfileEditor(listProfiles().find((p)=>p.id===edit.dataset.editProfile),render);const pin=event.target.closest("[data-pin-profile]");if(pin){const profile=listProfiles().find((p)=>p.id===pin.dataset.pinProfile),value=await requestNewPin(profile?.hasPin);if(value!==null){try{await setProfilePin(pin.dataset.pinProfile,value.pin,value.currentPin);render();alert("PIN atualizado.");}catch(error){alert(error.message);}}}const del=event.target.closest("[data-delete-profile]");if(del&&confirm("Excluir este perfil? Favoritos, progresso e histórico serão removidos permanentemente.")){try{await removeProfile(del.dataset.deleteProfile);render();}catch(error){alert(error.message);}}});render();}
function requestNewPin(hasPin=false){return new Promise((resolve)=>{const overlay=document.createElement("div");overlay.className="profile-manager-modal";overlay.innerHTML=`<form role="dialog" aria-modal="true" aria-labelledby="pinSettingsTitle"><h2 id="pinSettingsTitle">Configurar PIN</h2>${hasPin?'<label>PIN atual<input name="currentPin" type="password" inputmode="numeric" pattern="\\d{4}" maxlength="4" required autocomplete="current-password"></label>':""}<label>Novo PIN de 4 dígitos<input name="pin" type="password" inputmode="numeric" pattern="\\d{4}" maxlength="4" autocomplete="new-password"></label><label>Confirmar novo PIN<input name="confirmation" type="password" inputmode="numeric" pattern="\\d{4}" maxlength="4" autocomplete="new-password"></label><p class="settings-note" aria-live="polite">Deixe os campos de novo PIN vazios para remover o PIN atual.</p><div><button type="button" data-cancel>Cancelar</button><button class="settings-action" type="submit">Salvar PIN</button></div></form>`;const close=(value)=>{overlay.remove();resolve(value)};overlay.querySelector("[data-cancel]").addEventListener("click",()=>close(null));overlay.addEventListener("keydown",(e)=>{if(e.key==="Escape")close(null)});overlay.querySelector("form").addEventListener("submit",(e)=>{e.preventDefault();const fields=e.currentTarget.elements;if(fields.pin.value!==fields.confirmation.value){overlay.querySelector(".settings-note").textContent="Os novos PINs não coincidem.";return;}close({pin:fields.pin.value,currentPin:fields.currentPin?.value||""})});document.body.appendChild(overlay);overlay.querySelector("input").focus();});}
function openProfileEditor(profile,onSaved){const overlay=document.createElement("div");overlay.className="profile-manager-modal";overlay.innerHTML=`<form role="dialog" aria-modal="true" aria-label="${profile?"Editar":"Criar"} perfil"><h2>${profile?"Editar perfil":"Novo perfil"}</h2><label>Nome<input name="name" maxlength="40" required value="${profile?.name||""}"></label><label>Iniciais<input name="initials" maxlength="2" required value="${profile?.initials||""}"></label><label>Tipo<select name="kind"><option value="adult">Adulto</option><option value="kids" ${profile?.kind==="kids"?"selected":""}>Infantil</option></select></label><label>Cor<select name="color"><option value="blue">Azul</option><option value="purple">Roxo</option><option value="pink">Rosa</option><option value="orange">Laranja</option><option value="green">Verde</option></select></label><div><button type="button" data-cancel>Cancelar</button><button class="settings-action" type="submit">Salvar</button></div></form>`;const close=()=>overlay.remove();overlay.querySelector("[data-cancel]").addEventListener("click",close);overlay.addEventListener("keydown",(e)=>{if(e.key==="Escape")close();});overlay.querySelector("form").addEventListener("submit",async(event)=>{event.preventDefault();const e=event.currentTarget.elements;const input={name:e.name.value,initials:e.initials.value,kind:e.kind.value,avatar:{color:e.color.value}};try{profile?await updateProfile(profile.id,input):await createProfile(input);close();onSaved();}catch(error){alert(error.message);}});document.body.appendChild(overlay);overlay.querySelector('[name="color"]').value=profile?.avatar?.color||"blue";overlay.querySelector("input").focus();}

function hydrateControls(values) {
    document.querySelectorAll("[data-setting]").forEach((control) => {
        const key = control.dataset.setting;

        if (control.dataset.type === "radio") {
            control.querySelectorAll("input").forEach((input) => {
                input.checked = input.value === values[key];
            });
            return;
        }

        if (control.type === "checkbox") {
            control.checked = Boolean(values[key]);
            return;
        }

        control.value = values[key];
    });
}

function bindControls() {
    document.addEventListener("click", (event) => {
        const swatch = event.target.closest("[data-color-swatch]");

        if (!swatch) return;

        const control = document.querySelector(`[data-setting="${swatch.dataset.colorTarget}"]`);

        if (!control) return;

        control.value = swatch.dataset.colorSwatch;
        savePreferences({ [control.dataset.setting]: control.value });
        hydrateControls(getPreferences());
        updateColorSwatches();
    });

    document.addEventListener("change", (event) => {
        const control = event.target.closest("[data-setting]");

        if (!control) return;

        const key = control.dataset.setting;
        const value = readControlValue(control);

        control.setCustomValidity?.("");
        savePreferences({ [key]: value });
        hydrateControls(getPreferences());
        updateColorSwatches();
    });
}

function installColorSwatches() {
    const presets = {
        subtitleColor: ["#FFFFFF", "#F8FAFC", "#FDE68A", "#BFDBFE"],
        subtitleBackground: ["#000000", "#111827", "#1F2937", "#7F1D1D"]
    };

    Object.entries(presets).forEach(([setting, colors]) => {
        const control = document.querySelector(`[data-setting="${setting}"]`);

        if (!control || control.closest(".setting-field")?.querySelector(".color-swatches")) return;

        const swatches = document.createElement("div");
        swatches.className = "color-swatches";
        swatches.setAttribute("aria-label", "Cores rápidas");
        swatches.innerHTML = colors.map((color) => `
            <button class="color-swatch" type="button" data-color-target="${setting}" data-color-swatch="${color}" style="--swatch-color:${color}" aria-label="Usar ${color}"></button>
        `).join("");

        control.insertAdjacentElement("afterend", swatches);
    });

    updateColorSwatches();
}

function updateColorSwatches() {
    document.querySelectorAll("[data-color-swatch]").forEach((swatch) => {
        const control = document.querySelector(`[data-setting="${swatch.dataset.colorTarget}"]`);
        const active = control?.value?.toLowerCase() === swatch.dataset.colorSwatch.toLowerCase();
        swatch.classList.toggle("is-active", Boolean(active));
    });
}

function hideUnsupportedSettings() {
    document.querySelectorAll('[data-setting="normalizeVolume"]').forEach((control) => {
        const wrapper = control.closest("label, .setting-field");

        if (wrapper) {
            wrapper.hidden = true;
        }
    });
}

function readControlValue(control) {
    if (control.dataset.type === "radio") {
        return control.querySelector("input:checked")?.value;
    }

    if (control.type === "checkbox") {
        return control.checked;
    }

    if (control.type === "range" || control.type === "number") {
        return Number(control.value);
    }

    return control.value;
}
