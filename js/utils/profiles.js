const ACTIVE_PROFILE_KEY = "brasa:active-profile";
const LAURA_EXIT_PIN = "1234";
let transientProfileId = "";

const profiles = Object.freeze([
    { id: "mario", name: "Mário", initials: "M", kind: "adult" },
    { id: "isabele", name: "Isabele", initials: "I", kind: "adult" },
    { id: "laura", name: "Laura", initials: "L", kind: "kids" }
]);

export function getProfiles() {
    return profiles.map((profile) => ({ ...profile }));
}

export function getActiveProfile() {
    try {
        const id = localStorage.getItem(ACTIVE_PROFILE_KEY) || transientProfileId;
        return profiles.find((profile) => profile.id === id) || null;
    } catch {
        return profiles.find((profile) => profile.id === transientProfileId) || null;
    }
}

export function setActiveProfile(profileId) {
    const profile = profiles.find((item) => item.id === profileId);

    if (!profile) {
        throw new Error("Perfil inválido.");
    }

    transientProfileId = profile.id;

    try {
        localStorage.setItem(ACTIVE_PROFILE_KEY, profile.id);
    } catch {
        // O perfil permanece ativo apenas nesta sessão quando o armazenamento não está disponível.
    }

    document.dispatchEvent(new CustomEvent("brasa:profile-change", { detail: { profile } }));
    return { ...profile };
}

export function isKidsProfile(profile = getActiveProfile()) {
    return profile?.kind === "kids";
}

export function filterContentByProfile(content, profile = getActiveProfile()) {
    const items = Array.isArray(content) ? content : [];

    return isKidsProfile(profile)
        ? items.filter((item) => item?.kids === true)
        : [...items];
}

export async function initializeProfiles(root = document) {
    bindProfileControls(root);
    updateProfileControls(root);

    const activeProfile = getActiveProfile();

    if (activeProfile) {
        return activeProfile;
    }

    return openProfileSelector({ initial: true });
}

export function bindProfileControls(root = document) {
    if (root.__brasaProfileControlsBound) return;

    root.__brasaProfileControlsBound = true;
    root.addEventListener("click", (event) => {
        if (!event.target.closest("[data-profile-switcher]")) return;

        openProfileSelector();
    });
}

export function updateProfileControls(root = document) {
    const profile = getActiveProfile();

    root.querySelectorAll("[data-profile-switcher]").forEach((control) => {
        const name = control.querySelector("[data-profile-name]");
        const initials = control.querySelector("[data-profile-initials]");

        control.dataset.profileId = profile?.id || "";
        control.setAttribute("aria-label", profile ? `Trocar perfil, atual: ${profile.name}` : "Escolher perfil");

        if (name) name.textContent = profile?.name || "Perfis";
        if (initials) initials.textContent = profile?.initials || "?";
    });

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function openProfileSelector({ initial = false } = {}) {
    const existing = document.querySelector(".profile-selector");

    if (existing) {
        return existing.__brasaProfilePromise || Promise.resolve(getActiveProfile());
    }

    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        const activeProfile = getActiveProfile();

        overlay.className = "profile-selector";
        overlay.innerHTML = `
            <section class="profile-selector__dialog" role="dialog" aria-modal="true" aria-label="${initial ? "Escolher perfil" : "Trocar perfil"}">
                <p class="profile-selector__eyebrow">BRasa</p>
                <h1>${initial ? "Quem está assistindo?" : "Trocar perfil"}</h1>
                <p>${initial ? "Escolha um perfil para continuar." : "Escolha quem vai usar o BRasa agora."}</p>
                <div class="profile-selector__list">
                    ${profiles.map((profile) => `
                        <button class="profile-selector__card ${profile.id === activeProfile?.id ? "is-active" : ""}" type="button" data-profile-id="${profile.id}">
                            <span class="profile-selector__avatar" data-profile-avatar="${profile.id}">${profile.initials}</span>
                            <strong>${profile.name}</strong>
                            <small>${profile.kind === "kids" ? "Perfil infantil" : "Perfil adulto"}</small>
                        </button>
                    `).join("")}
                </div>
                ${initial ? "" : '<button class="profile-selector__cancel" type="button" data-profile-cancel>Cancelar</button>'}
            </section>
        `;

        const close = (value) => {
            overlay.remove();
            resolve(value);
        };

        const chooseProfile = async (profileId) => {
            const nextProfile = profiles.find((profile) => profile.id === profileId);
            const currentProfile = getActiveProfile();

            if (!nextProfile) return;

            if (isKidsProfile(currentProfile) && !isKidsProfile(nextProfile)) {
                const allowed = await requestLauraExitPin();

                if (!allowed) return;
            }

            setActiveProfile(nextProfile.id);
            updateProfileControls(document);
            close(nextProfile);

            if (!initial) {
                window.location.reload();
            }
        };

        overlay.querySelectorAll("[data-profile-id]").forEach((button) => {
            button.addEventListener("click", () => chooseProfile(button.dataset.profileId));
        });

        overlay.querySelector("[data-profile-cancel]")?.addEventListener("click", () => close(null));
        overlay.__brasaProfilePromise = Promise.resolve();
        document.body.appendChild(overlay);
        overlay.querySelector("[data-profile-id]")?.focus();
    });
}

function requestLauraExitPin() {
    return new Promise((resolve) => {
        const overlay = document.createElement("div");

        overlay.className = "profile-pin";
        overlay.innerHTML = `
            <form class="profile-pin__dialog" aria-label="PIN do perfil Laura">
                <p class="profile-selector__eyebrow">Perfil Laura</p>
                <h2>Digite o PIN para sair</h2>
                <label>
                    <span>PIN de 4 dígitos</span>
                    <input type="password" inputmode="numeric" maxlength="4" pattern="\\d{4}" autocomplete="off">
                </label>
                <p class="profile-pin__error" hidden>PIN incorreto. Tente novamente.</p>
                <div>
                    <button type="button" data-pin-cancel>Cancelar</button>
                    <button type="submit">Continuar</button>
                </div>
            </form>
        `;

        const form = overlay.querySelector("form");
        const input = overlay.querySelector("input");
        const error = overlay.querySelector(".profile-pin__error");
        const close = (allowed) => {
            overlay.remove();
            resolve(allowed);
        };

        form.addEventListener("submit", (event) => {
            event.preventDefault();

            if (input.value === LAURA_EXIT_PIN) {
                close(true);
                return;
            }

            error.hidden = false;
            input.select();
        });

        overlay.querySelector("[data-pin-cancel]").addEventListener("click", () => close(false));
        document.body.appendChild(overlay);
        input.focus();
    });
}
