export function nextFocusIndex(items, currentIndex, direction) {
    if (!items.length) return -1;
    const current = items[Math.max(0, currentIndex)] || items[0], row = current.row, column = current.column;
    if (direction === "left" || direction === "right") { const delta = direction === "left" ? -1 : 1, target = items.findIndex((item) => item.row === row && item.column === column + delta); return target >= 0 ? target : currentIndex; }
    const targetRow = direction === "up" ? row - 1 : row + 1, candidates = items.map((item, index) => ({ ...item, index })).filter((item) => item.row === targetRow); if (!candidates.length) return currentIndex; candidates.sort((a, b) => Math.abs(a.column - column) - Math.abs(b.column - column)); return candidates[0].index;
}

export function installFocusManager(root = document) {
    let lastFocused = null;
    root.addEventListener("focusin", (event) => { if (event.target.matches("[data-tv-focus]")) lastFocused = event.target; });
    root.addEventListener("keydown", (event) => {
        const keys = { ArrowLeft: "left", ArrowRight: "right", ArrowUp: "up", ArrowDown: "down" }; if (!keys[event.key]) return;
        const nodes = [...root.querySelectorAll("[data-tv-focus]:not([disabled])")].filter((node) => node.offsetParent !== null); if (!nodes.length) return;
        const items = nodes.map((node) => ({ row: Number(node.dataset.focusRow || 0), column: Number(node.dataset.focusColumn || 0) })), current = Math.max(0, nodes.indexOf(document.activeElement)), next = nextFocusIndex(items, current, keys[event.key]);
        if (next >= 0 && nodes[next] !== document.activeElement) { event.preventDefault(); nodes[next].focus(); nodes[next].scrollIntoView({ block: "nearest", inline: "center", behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" }); }
    });
    return { focusFirst() { const target = lastFocused?.isConnected ? lastFocused : root.querySelector("[data-tv-focus]:not([disabled])"); target?.focus(); }, remember: () => lastFocused };
}
