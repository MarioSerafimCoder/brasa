import { getMovies } from "../../data/movies.js";
import { applyPreferences } from "../utils/preferences.js";
import { filterContentByProfile, getActiveProfile, initializeProfiles } from "../utils/profiles.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";
import { installTmdbImageFallbacks, movieImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";
import { bindMovieGridNavigation, renderMovieGrid } from "./library-utils.js?v=streaming-20260709a";
import {
    createCollection, createId, deleteCollection, duplicateCollection, filterCollectionsForProfile,
    getCollectionMovies, loadCollections, updateCollection
} from "../services/collections-service.js";
import { getAllMediaStatus } from "../services/media-service.js";

applyPreferences();

const elements = {
    overview: document.getElementById("collectionsOverview"), detail: document.getElementById("collectionDetail"),
    grid: document.getElementById("collectionsGrid"), search: document.getElementById("collectionSearch"),
    total: document.getElementById("collectionsTotal"), showEmpty: document.getElementById("showEmptyCollections"),
    tabs: document.getElementById("collectionTabs"), sort: document.getElementById("collectionsSort"),
    newButton: document.getElementById("newCollectionButton"), back: document.getElementById("backToCollections"),
    hero: document.getElementById("collectionDetailHero"), title: document.getElementById("collectionTitle"),
    description: document.getElementById("collectionDescription"), kicker: document.getElementById("collectionKicker"),
    count: document.getElementById("collectionCount"), updated: document.getElementById("collectionUpdated"),
    actions: document.getElementById("collectionDetailActions"), movieSort: document.getElementById("collectionMovieSort"),
    movies: document.getElementById("collectionMovies")
};

let movies = [];
let collections = [];
let activeCollection = null;
let activeFilter = "all";
let openMenuId = "";

init();

async function init() {
    installPageTransitions();
    installTmdbImageFallbacks();
    installPageSidebar("collection");
    const profile = await initializeProfiles();
    movies = filterContentByProfile(getMovies(), profile);
    try { const mediaState = await getAllMediaStatus(); movies = movies.map((movie) => { const item = mediaState.items?.[`movie:${movie.id}`] || {}; return { ...movie, mediaStatus:item.status||"pending", mediaStrategy:item.strategy||"pending", videoCodec:item.probe?.video?.codec||"", audioCodec:item.probe?.audioTracks?.[0]?.codec||"", resolution:item.probe?.video?.height||0, hdr:Boolean(item.probe?.video?.hdr), directPlay:item.strategy==="direct-play", prepared:Boolean(item.preparedPath) }; }); } catch {}
    await reloadCollections();
    bindEvents();
    const requestedId = new URLSearchParams(location.search).get("id");
    requestedId ? openCollection(requestedId, false) : renderOverview();
    refreshIcons();
}

async function reloadCollections() {
    collections = filterCollectionsForProfile(await loadCollections(), getActiveProfile());
}

function bindEvents() {
    elements.search.addEventListener("input", renderOverview);
    elements.showEmpty.addEventListener("change", renderOverview);
    elements.sort.addEventListener("change", renderOverview);
    elements.newButton.addEventListener("click", () => openEditor());
    elements.back.addEventListener("click", () => showOverview());
    elements.movieSort.addEventListener("change", renderDetailMovies);
    elements.tabs.addEventListener("click", (event) => {
        const button = event.target.closest("[data-filter]");
        if (!button) return;
        activeFilter = button.dataset.filter;
        elements.tabs.querySelectorAll("[data-filter]").forEach((tab) => {
            const selected = tab === button;
            tab.classList.toggle("is-active", selected);
            tab.setAttribute("aria-selected", String(selected));
        });
        renderOverview();
    });
    elements.grid.addEventListener("click", handleGridClick);
    elements.grid.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches("[data-collection-id]")) {
            event.preventDefault();
            openCollection(event.target.dataset.collectionId);
        }
    });
    elements.actions.addEventListener("click", handleActionClick);
    bindMovieGridNavigation(elements.movies);
    window.addEventListener("popstate", () => {
        const id = new URLSearchParams(location.search).get("id");
        id ? openCollection(id, false) : showOverview(false);
    });
    document.addEventListener("click", (event) => {
        if (!event.target.closest(".collection-card__menu")) closeMenus();
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") closeMenus();
    });
}

function getVisibleCollections() {
    const query = normalize(elements.search.value);
    const items = collections.map((collection) => ({ collection, items: getCollectionMovies(collection, movies) }))
        .filter(({ collection, items }) => {
            const matchesQuery = !query || normalize(`${collection.title} ${collection.description}`).includes(query);
            const matchesType = activeFilter === "all" || (activeFilter === "system" ? collection.source === "system" : collection.type === activeFilter && collection.source !== "system");
            return matchesQuery && matchesType && (elements.showEmpty.checked || items.length > 0);
        });
    const sort = elements.sort.value;
    return items.sort((a, b) => {
        if (sort === "alpha") return a.collection.title.localeCompare(b.collection.title, "pt-BR");
        if (sort === "count-desc") return b.items.length - a.items.length;
        if (sort === "count-asc") return a.items.length - b.items.length;
        return (Date.parse(b.collection.updatedAt) || 0) - (Date.parse(a.collection.updatedAt) || 0);
    });
}

function renderOverview() {
    if (elements.overview.hidden) return;
    const visible = getVisibleCollections();
    elements.total.textContent = `${visible.length} ${visible.length === 1 ? "coleção" : "coleções"}`;
    if (!visible.length) {
        elements.grid.innerHTML = `<div class="collections-empty"><i data-lucide="library"></i><h2>Nenhuma coleção encontrada</h2><p>${elements.showEmpty.checked ? "Tente ajustar a busca ou os filtros." : "Ative “Mostrar coleções vazias” para ver todas as opções."}</p></div>`;
        refreshIcons();
        return;
    }
    elements.grid.innerHTML = visible.map(({ collection, items }) => collectionCard(collection, items)).join("");
    refreshIcons();
}

function collectionCard(collection, items) {
    const empty = items.length === 0;
    return `<article class="collection-card ${empty ? "is-empty" : ""}" data-collection-id="${escapeAttribute(collection.id)}" role="button" tabindex="0" aria-label="Abrir coleção ${escapeAttribute(collection.title)}">
        ${collectionArtwork(collection, items)}
        <div class="collection-card__shade"></div>
        <div class="collection-card__top"><span class="collection-type collection-type--${collection.source === "system" ? "system" : collection.type}">${typeLabel(collection)}</span>
            <div class="collection-card__menu"><button type="button" data-menu="${escapeAttribute(collection.id)}" aria-label="Ações de ${escapeAttribute(collection.title)}" aria-expanded="${openMenuId === collection.id}"><i data-lucide="ellipsis"></i></button>
            <div class="collection-menu" ${openMenuId === collection.id ? "" : "hidden"} role="menu"><button role="menuitem" data-action="open" data-id="${escapeAttribute(collection.id)}">Abrir</button>${collection.source === "user" ? `<button role="menuitem" data-action="edit" data-id="${escapeAttribute(collection.id)}">Editar</button>` : ""}<button role="menuitem" data-action="duplicate" data-id="${escapeAttribute(collection.id)}">Duplicar</button>${collection.source === "user" ? `<button class="is-danger" role="menuitem" data-action="delete" data-id="${escapeAttribute(collection.id)}">Excluir</button>` : ""}</div></div>
        </div>
        <div class="collection-card__content"><h2>${escapeHtml(collection.title)}</h2><p>${escapeHtml(collection.description)}</p><div class="collection-card__meta"><span>${items.length} ${items.length === 1 ? "filme" : "filmes"}</span><span class="collection-card__action">Abrir <i data-lucide="arrow-up-right"></i></span></div></div>
    </article>`;
}

function collectionArtwork(collection, items) {
    if (collection.banner) return `<div class="collection-card__art"><img src="${escapeAttribute(collection.banner)}" alt="" loading="lazy"></div>`;
    const posters = items.slice(0, 4);
    if (!posters.length) return `<div class="collection-card__art collection-card__art--placeholder"><i data-lucide="clapperboard"></i></div>`;
    return `<div class="collection-card__art collection-card__art--collage">${posters.map((movie) => `<img src="${escapeAttribute(posterUrl(movie))}" alt="" loading="lazy"${tmdbImageFallbackAttributes(`../${movie.poster}`)}>`).join("")}</div>`;
}

function handleGridClick(event) {
    const menuButton = event.target.closest("[data-menu]");
    if (menuButton) {
        event.stopPropagation();
        openMenuId = openMenuId === menuButton.dataset.menu ? "" : menuButton.dataset.menu;
        renderOverview();
        elements.grid.querySelector(`[data-menu="${CSS.escape(menuButton.dataset.menu)}"]`)?.focus();
        return;
    }
    const action = event.target.closest("[data-action]");
    if (action) {
        event.stopPropagation();
        runAction(action.dataset.action, action.dataset.id);
        return;
    }
    const card = event.target.closest("[data-collection-id]");
    if (card) openCollection(card.dataset.collectionId);
}

function handleActionClick(event) {
    const button = event.target.closest("[data-action]");
    if (button) runAction(button.dataset.action, button.dataset.id);
}

async function runAction(action, id) {
    closeMenus();
    const collection = collections.find((item) => item.id === id);
    if (!collection) return;
    if (action === "open") return openCollection(id);
    if (action === "edit") return openEditor(collection);
    if (action === "duplicate") {
        try {
            await duplicateCollection(collection, getActiveProfile()?.id || "mario");
            await reloadCollections(); renderOverview(); showToast("Coleção duplicada com sucesso.");
        } catch (error) { showToast(error.message, "error"); }
    }
    if (action === "delete" && confirm(`Excluir a coleção “${collection.title}”? Esta ação não pode ser desfeita.`)) {
        try { await deleteCollection(id); await reloadCollections(); showOverview(); showToast("Coleção excluída."); }
        catch (error) { showToast(error.message, "error"); }
    }
}

function openCollection(id, updateUrl = true) {
    const collection = collections.find((item) => item.id === id);
    if (!collection) { showOverview(updateUrl); showToast("Coleção não encontrada.", "error"); return; }
    activeCollection = collection;
    elements.overview.hidden = true;
    elements.detail.hidden = false;
    if (updateUrl) history.pushState({}, "", `collection.html?id=${encodeURIComponent(id)}`);
    const items = getCollectionMovies(collection, movies);
    elements.hero.innerHTML = collectionArtwork(collection, items);
    elements.title.textContent = collection.title;
    elements.description.textContent = collection.description || "Sem descrição.";
    elements.kicker.innerHTML = `<span class="collection-type collection-type--${collection.source === "system" ? "system" : collection.type}">${typeLabel(collection)}</span>`;
    elements.count.textContent = `${items.length} ${items.length === 1 ? "item" : "itens"}`;
    elements.updated.textContent = `Atualizada em ${formatDate(collection.updatedAt)}`;
    elements.actions.innerHTML = `${collection.source === "user" ? `<button type="button" data-action="edit" data-id="${escapeAttribute(id)}"><i data-lucide="pencil"></i>Editar</button>` : ""}${collection.type === "manual" && collection.source === "user" ? `<button class="is-primary" type="button" data-action="edit" data-id="${escapeAttribute(id)}"><i data-lucide="plus"></i>Adicionar filmes</button>` : ""}<button type="button" data-action="duplicate" data-id="${escapeAttribute(id)}"><i data-lucide="copy"></i>Duplicar</button>`;
    elements.movieSort.value = collection.type === "manual" ? `${collection.sort?.field || "manual"}-${collection.sort?.direction || "asc"}` : `${collection.sort?.field || "title"}-${collection.sort?.direction || "asc"}`;
    if (!elements.movieSort.value) elements.movieSort.value = collection.type === "manual" ? "manual-asc" : "title-asc";
    renderDetailMovies(); refreshIcons(); window.scrollTo({ top: 0, behavior: "smooth" });
}

function renderDetailMovies() {
    if (!activeCollection) return;
    const [field, direction] = elements.movieSort.value.split("-");
    const collection = { ...activeCollection, sort: { field, direction } };
    renderMovieGrid(elements.movies, getCollectionMovies(collection, movies), {
        emptyMessage: collection.type === "manual" ? "Esta coleção ainda não tem filmes." : "Nenhum filme da biblioteca atende às regras desta coleção.",
        emptyActionLabel: collection.type === "manual" && collection.source === "user" ? "Adicionar filmes" : "",
        emptyAction: "edit-collection"
    });
    elements.movies.querySelector("[data-empty-action]")?.addEventListener("click", () => openEditor(collection));
}

function showOverview(updateUrl = true) {
    activeCollection = null;
    elements.detail.hidden = true;
    elements.overview.hidden = false;
    if (updateUrl) history.pushState({}, "", "collection.html");
    renderOverview(); window.scrollTo({ top: 0, behavior: "smooth" });
}

function openEditor(collection = null) {
    if (collection?.source === "system") return;
    const editing = Boolean(collection);
    const draft = collection ? structuredClone(collection) : newDraft();
    const overlay = document.createElement("div");
    overlay.className = "collection-editor";
    overlay.innerHTML = editorMarkup(draft, editing);
    document.body.appendChild(overlay);
    const dialog = overlay.querySelector("[role=dialog]");
    const form = overlay.querySelector("form");
    const typeInputs = [...form.querySelectorAll("[name=type]")];
    const close = () => { overlay.remove(); elements.newButton.focus(); };
    const refreshBuilder = () => {
        const type = form.elements.type.value;
        overlay.querySelector("[data-manual-builder]").hidden = type !== "manual";
        overlay.querySelector("[data-smart-builder]").hidden = type !== "smart";
        renderManualPicker(overlay, draft);
        renderRuleBuilder(overlay, draft);
    };
    overlay.addEventListener("click", (event) => {
        if (event.target === overlay || event.target.closest("[data-close-editor]")) close();
        const movieToggle = event.target.closest("[data-toggle-movie]");
        if (movieToggle) { toggleDraftMovie(draft, movieToggle.dataset.toggleMovie); renderManualPicker(overlay, draft); }
        const move = event.target.closest("[data-move-movie]");
        if (move) { moveDraftMovie(draft, move.dataset.moveMovie, Number(move.dataset.direction)); renderManualPicker(overlay, draft); }
        if (event.target.closest("[data-add-rule]")) { draft.rules.items.push({ field: "genres", operator: "contains", value: "" }); renderRuleBuilder(overlay, draft); }
        const removeRule = event.target.closest("[data-remove-rule]");
        if (removeRule && draft.rules.items.length > 1) { draft.rules.items.splice(Number(removeRule.dataset.removeRule), 1); renderRuleBuilder(overlay, draft); }
    });
    overlay.addEventListener("input", (event) => {
        if (event.target.matches("[data-movie-search]")) renderManualPicker(overlay, draft);
        if (event.target.closest("[data-rule-row]") || event.target.name === "match") { syncRulesFromForm(overlay, draft); renderRulePreview(overlay, draft); }
    });
    typeInputs.forEach((input) => input.addEventListener("change", refreshBuilder));
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        syncRulesFromForm(overlay, draft);
        const now = new Date().toISOString();
        const payload = { ...draft, title: form.elements.title.value.trim(), description: form.elements.description.value.trim(), type: form.elements.type.value, scope: form.elements.scope.value, profileId: form.elements.scope.value === "profile" ? getActiveProfile()?.id : null, rules: form.elements.type.value === "smart" ? draft.rules : null, movieIds: form.elements.type.value === "manual" ? draft.movieIds : [], updatedAt: now };
        if (!payload.title) return form.elements.title.focus();
        setEditorBusy(overlay, true);
        try {
            editing ? await updateCollection(collection.id, payload) : await createCollection(payload);
            await reloadCollections(); close(); showToast(editing ? "Coleção atualizada." : "Coleção criada.");
            editing ? openCollection(collection.id, false) : showOverview(false);
        } catch (error) { setEditorBusy(overlay, false); showToast(error.message, "error"); }
    });
    overlay.addEventListener("keydown", (event) => {
        if (event.key === "Escape") close();
        if (event.key === "Tab") trapFocus(event, dialog);
    });
    refreshBuilder(); refreshIcons(); form.elements.title.focus();
}

function editorMarkup(draft, editing) {
    return `<section class="collection-editor__dialog" role="dialog" aria-modal="true" aria-labelledby="editorTitle"><header><div><p>${editing ? "Editar coleção" : "Nova coleção"}</p><h2 id="editorTitle">${editing ? escapeHtml(draft.title) : "Crie do seu jeito"}</h2></div><button type="button" data-close-editor aria-label="Fechar"><i data-lucide="x"></i></button></header>
    <form><div class="collection-editor__body"><label>Nome<input name="title" maxlength="80" required value="${escapeAttribute(draft.title)}" placeholder="Ex.: Ficção científica favorita"></label><label>Descrição<textarea name="description" maxlength="300" rows="3" placeholder="Conte o que reúne esta coleção">${escapeHtml(draft.description)}</textarea></label>
    <fieldset><legend>Tipo</legend><div class="collection-choice"><label><input type="radio" name="type" value="manual" ${draft.type === "manual" ? "checked" : ""}><span><strong>Manual</strong><small>Você escolhe e ordena os filmes.</small></span></label><label><input type="radio" name="type" value="smart" ${draft.type === "smart" ? "checked" : ""}><span><strong>Inteligente</strong><small>Atualizada automaticamente por regras.</small></span></label></div></fieldset>
    <label>Escopo<select name="scope"><option value="profile" ${draft.scope === "profile" ? "selected" : ""}>Somente perfil atual</option>${getActiveProfile()?.kind === "kids" ? "" : `<option value="shared" ${draft.scope === "shared" ? "selected" : ""}>Compartilhada entre perfis adultos</option>`}</select></label>
    <section data-manual-builder class="collection-builder"><div class="collection-builder__heading"><div><h3>Filmes da coleção</h3><p>Pesquise, selecione e ajuste a ordem.</p></div></div><label>Pesquisar filmes<input data-movie-search type="search" placeholder="Digite um título"></label><div data-selected-movies class="selected-movies"></div><div data-movie-picker class="movie-picker"></div></section>
    <section data-smart-builder class="collection-builder"><div class="collection-builder__heading"><div><h3>Regras inteligentes</h3><p>Combine critérios usando os dados da biblioteca.</p></div><button type="button" data-add-rule><i data-lucide="plus"></i>Regra</button></div><label>Correspondência<select name="match"><option value="all" ${draft.rules?.match !== "any" ? "selected" : ""}>Todas as regras</option><option value="any" ${draft.rules?.match === "any" ? "selected" : ""}>Qualquer regra</option></select></label><div data-rule-list></div><div data-rule-preview class="rule-preview"></div></section></div>
    <footer><button type="button" data-close-editor>Cancelar</button><button class="is-primary" type="submit">${editing ? "Salvar alterações" : "Criar coleção"}</button></footer></form></section>`;
}

function renderManualPicker(root, draft) {
    const query = normalize(root.querySelector("[data-movie-search]")?.value || "");
    const selected = draft.movieIds.map((id) => movies.find((movie) => String(movie.id) === String(id))).filter(Boolean);
    root.querySelector("[data-selected-movies]").innerHTML = selected.length ? selected.map((movie, index) => `<div><span>${index + 1}. ${escapeHtml(movie.title)}</span><span><button type="button" data-move-movie="${escapeAttribute(movie.id)}" data-direction="-1" aria-label="Mover ${escapeAttribute(movie.title)} para cima" ${index === 0 ? "disabled" : ""}><i data-lucide="chevron-up"></i></button><button type="button" data-move-movie="${escapeAttribute(movie.id)}" data-direction="1" aria-label="Mover ${escapeAttribute(movie.title)} para baixo" ${index === selected.length - 1 ? "disabled" : ""}><i data-lucide="chevron-down"></i></button><button type="button" data-toggle-movie="${escapeAttribute(movie.id)}" aria-label="Remover ${escapeAttribute(movie.title)}"><i data-lucide="x"></i></button></span></div>`).join("") : `<p>Nenhum filme selecionado.</p>`;
    root.querySelector("[data-movie-picker]").innerHTML = movies.filter((movie) => !query || normalize(`${movie.title} ${(movie.genres || []).join(" ")}`).includes(query)).map((movie) => `<button type="button" class="${draft.movieIds.map(String).includes(String(movie.id)) ? "is-selected" : ""}" data-toggle-movie="${escapeAttribute(movie.id)}"><img src="${escapeAttribute(posterUrl(movie))}" alt=""${tmdbImageFallbackAttributes(`../${movie.poster}`)}><span>${escapeHtml(movie.title)}</span><i data-lucide="${draft.movieIds.map(String).includes(String(movie.id)) ? "check" : "plus"}"></i></button>`).join("");
    refreshIcons();
}

function renderRuleBuilder(root, draft) {
    const list = root.querySelector("[data-rule-list]");
    if (!list) return;
    list.innerHTML = (draft.rules?.items || []).map((rule, index) => `<div class="rule-row" data-rule-row="${index}"><select data-rule-field>${fieldOptions(rule.field)}</select><select data-rule-operator>${operatorOptions(rule.operator)}</select><input data-rule-value value="${escapeAttribute(rule.value)}" placeholder="Valor"><button type="button" data-remove-rule="${index}" aria-label="Remover regra" ${draft.rules.items.length === 1 ? "disabled" : ""}><i data-lucide="trash-2"></i></button></div>`).join("");
    renderRulePreview(root, draft); refreshIcons();
}

function syncRulesFromForm(root, draft) {
    draft.rules = { match: root.querySelector("[name=match]")?.value || "all", items: [...root.querySelectorAll("[data-rule-row]")].map((row) => ({ field: row.querySelector("[data-rule-field]").value, operator: row.querySelector("[data-rule-operator]").value, value: row.querySelector("[data-rule-value]").value })) };
}

function renderRulePreview(root, draft) {
    const preview = root.querySelector("[data-rule-preview]");
    if (!preview) return;
    const collection = { type: "smart", rules: draft.rules, sort: { field: "title", direction: "asc" } };
    const matches = getCollectionMovies(collection, movies);
    preview.innerHTML = `<strong>${matches.length} ${matches.length === 1 ? "filme encontrado" : "filmes encontrados"}</strong>${matches.length ? `<div>${matches.slice(0, 5).map((movie) => `<img src="${escapeAttribute(posterUrl(movie))}" alt="${escapeAttribute(movie.title)}"${tmdbImageFallbackAttributes(`../${movie.poster}`)}>`).join("")}</div>` : `<p>Ajuste as regras para encontrar filmes.</p>`}`;
}

function fieldOptions(selected) {
    return [["title","Título"],["genres","Gênero"],["year","Ano"],["rating","Nota"],["quality","Qualidade"],["contentRating","Classificação"],["kids","Infantil"],["favorite","Favorito"],["progress","Progresso"],["completed","Concluído"],["recentlyWatched","Assistido recentemente"],["available","Arquivo local"],["mediaStatus","Status da mídia"],["mediaStrategy","Estratégia"],["videoCodec","Codec de vídeo"],["audioCodec","Codec de áudio"],["resolution","Resolução vertical"],["hdr","HDR"],["directPlay","Direct play"],["prepared","Preparado"]].map(([value,label]) => `<option value="${value}" ${selected === value ? "selected" : ""}>${label}</option>`).join("");
}

function operatorOptions(selected) {
    return [["contains","contém"],["notContains","não contém"],["equals","igual"],["notEquals","diferente"],["gte","maior ou igual"],["lte","menor ou igual"],["between","entre"],["true","verdadeiro"],["false","falso"]].map(([value,label]) => `<option value="${value}" ${selected === value ? "selected" : ""}>${label}</option>`).join("");
}

function newDraft() {
    const now = new Date().toISOString();
    return { id: createId("nova-colecao"), title: "", description: "", type: "manual", source: "user", scope: "profile", profileId: getActiveProfile()?.id || "mario", banner: "", movieIds: [], rules: { match: "all", items: [{ field: "genres", operator: "contains", value: "" }] }, sort: { field: "manual", direction: "asc" }, createdAt: now, updatedAt: now };
}

function toggleDraftMovie(draft, id) { const index = draft.movieIds.map(String).indexOf(String(id)); index >= 0 ? draft.movieIds.splice(index, 1) : draft.movieIds.push(String(id)); }
function moveDraftMovie(draft, id, direction) { const index = draft.movieIds.map(String).indexOf(String(id)); const target = index + direction; if (index >= 0 && target >= 0 && target < draft.movieIds.length) [draft.movieIds[index], draft.movieIds[target]] = [draft.movieIds[target], draft.movieIds[index]]; }
function closeMenus() { if (!openMenuId) return; openMenuId = ""; if (!elements.overview.hidden) renderOverview(); }
function typeLabel(collection) { return collection.source === "system" ? "Sistema" : collection.type === "manual" ? "Manual" : "Inteligente"; }
function posterUrl(movie) { return movieImageUrl(movie, { type: "poster", size: "w342", fallback: `../${movie.poster}` }); }
function formatDate(value) { const date = new Date(value); return Number.isNaN(date.getTime()) ? "data desconhecida" : date.toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" }); }
function normalize(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim(); }
function refreshIcons() { window.lucide?.createIcons?.(); }
function setEditorBusy(root, busy) { root.querySelectorAll("button,input,textarea,select").forEach((control) => control.disabled = busy); root.classList.toggle("is-busy", busy); }
function trapFocus(event, root) { const focusable = [...root.querySelectorAll("button:not([disabled]),input:not([disabled]),textarea:not([disabled]),select:not([disabled])")]; if (!focusable.length) return; const first = focusable[0], last = focusable.at(-1); if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); } }
function showToast(message, type = "success") { document.querySelector(".collection-toast")?.remove(); const toast = document.createElement("div"); toast.className = `collection-toast is-${type}`; toast.setAttribute("role", type === "error" ? "alert" : "status"); toast.textContent = message; document.body.appendChild(toast); setTimeout(() => toast.remove(), 3200); }
