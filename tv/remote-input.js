export function installRemoteInput({ onBack, getPlayer } = {}) {
    document.addEventListener("keydown", (event) => {
        if (["Escape", "Backspace"].includes(event.key)) { event.preventDefault(); onBack?.(); return; }
        if (event.key === " " && getPlayer?.()) { event.preventDefault(); const player = getPlayer(); player.paused ? player.play() : player.pause(); }
        if (["ArrowLeft", "ArrowRight"].includes(event.key) && getPlayer?.() && document.activeElement?.tagName === "VIDEO") { event.preventDefault(); getPlayer().currentTime = Math.max(0, getPlayer().currentTime + (event.key === "ArrowRight" ? 10 : -10)); }
    });
}
