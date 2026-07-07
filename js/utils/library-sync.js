export function bindLibrarySync() {
    document.querySelectorAll("[data-library-sync]").forEach((button) => {
        if (button.dataset.bound === "true") return;

        button.dataset.bound = "true";
        button.addEventListener("click", () => syncLibrary(button));
    });
}

async function syncLibrary(button) {
    const originalLabel = button.querySelector("span")?.textContent || "Atualizar";

    setButtonState(button, "Atualizando...", true);

    try {
        const response = await fetch("/api/sync", {
            method: "POST"
        });

        const data = await response.json().catch(() => ({}));

        if (!response.ok || !data.ok) {
            throw new Error(data.message || "Nao foi possivel atualizar.");
        }

        setButtonState(button, "Atualizado", true);

        window.setTimeout(() => {
            window.location.reload();
        }, 900);
    } catch (error) {
        console.error(error);
        setButtonState(button, "Abra pelo servidor", false);
        window.setTimeout(() => {
            setButtonState(button, originalLabel, false);
        }, 3500);
    }
}

function setButtonState(button, label, disabled) {
    button.disabled = disabled;

    const text = button.querySelector("span");
    if (text) {
        text.textContent = label;
    }
}
