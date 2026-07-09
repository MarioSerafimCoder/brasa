import { applyPreferences, getPreferences, savePreferences } from "../utils/preferences.js";
import { bindKidsModeToggle } from "../utils/kids-mode.js?v=streaming-20260709a";
import { installPageTransitions } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";

const preferences = applyPreferences();

init();

function init() {
    installPageTransitions();
    installPageSidebar("settings");
    hideUnsupportedSettings();
    hydrateControls(preferences);
    installColorSwatches();
    bindControls();
    bindKidsModeToggle();

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

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

        if (key === "kidsPin" && value && !/^\d{4}$/.test(value)) {
            control.setCustomValidity("Use exatamente 4 números.");
            control.reportValidity();
            hydrateControls(getPreferences());
            return;
        }

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

