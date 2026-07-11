import { spawn } from "node:child_process";
const suites = ["test-env-setup.mjs", "test-provider-recovery.mjs", "test-automatic-launcher.mjs", "test-library-watcher.mjs", "test-sync-coordinator.mjs", "test-profiles.mjs", "test-migrations.mjs", "test-local-security.mjs", "test-library-health.mjs", "test-media-compatibility.mjs", "test-profiles-collections.mjs", "test-admin-auth.mjs", "test-admin-api.mjs", "test-admin-library.mjs", "test-admin-profiles.mjs", "test-admin-logs.mjs"];
let passed = 0;
for (const suite of suites) { const code = await run(suite); if (code !== 0) { console.error(`\nFALHOU: ${suite}`); process.exitCode = 1; } else passed++; }
console.log(`\nResumo: ${passed}/${suites.length} suítes aprovadas.`);
function run(suite) { return new Promise((resolve) => { console.log(`\n→ ${suite}`); const child = spawn(process.execPath, [`scripts/${suite}`], { stdio: "inherit", windowsHide: true }); child.on("error", () => resolve(1)); child.on("close", resolve); }); }
