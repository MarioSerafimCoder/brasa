import { applyPreferences, getPreferences, savePreferences } from "../utils/preferences.js";
import { bindLibrarySync } from "../utils/library-sync.js";

const preferences = applyPreferences();

init();

function init() {
    hydrateControls(preferences);
    bindControls();
    bindLibrarySync();

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
    document.addEventListener("change", (event) => {
        const control = event.target.closest("[data-setting]");

        if (!control) return;

        const key = control.dataset.setting;
        const value = readControlValue(control);

        savePreferences({ [key]: value });
        hydrateControls(getPreferences());
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
