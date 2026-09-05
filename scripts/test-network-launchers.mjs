import assert from "node:assert/strict";
import fs from "node:fs/promises";

const startShortcut = await fs.readFile("Ativar Servidor BRasa.vbs", "utf8");
const stopShortcut = await fs.readFile("Encerrar Servidor BRasa.vbs", "utf8");
const starter = await fs.readFile("scripts/start-brasa-network.ps1", "utf8");
const stopper = await fs.readFile("scripts/stop-brasa.ps1", "utf8");

assert.match(startShortcut, /start-brasa-network\.ps1/i);
assert.match(stopShortcut, /stop-brasa\.ps1/i);
assert.match(starter, /lanAccessEnabled\s*=\s*\$true/);
assert.match(starter, /BRASA_SKIP_STARTUP_SYNC/);
assert.match(starter, /ProcessName -eq "node"/);
assert.match(stopper, /ProcessName -ne "node"/);
assert.match(stopper, /4173/);
console.log("Atalhos de servidor em rede: 7 cenarios aprovados.");
