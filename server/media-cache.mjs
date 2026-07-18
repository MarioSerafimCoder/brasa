import fs from "node:fs/promises";
import path from "node:path";

export function createMediaCache({ rootDir, store }) {
    const preparedRoot = path.join(rootDir, "data", "prepared-media");
    async function inspect() {
        const files = await walk(preparedRoot);
        const sizeBytes = files.reduce((sum, item) => sum + item.size, 0);
        return { root: preparedRoot, sizeBytes, files: files.length, partialFiles: files.filter((item) => /\.(?:part|tmp)$/i.test(item.path)).length };
    }
    async function cleanup({ maxBytes, minimumFreeBytes = 0 } = {}) {
        const settings = await store.settings();
        const limit = Number(maxBytes || settings.maxHlsGb * 1024 ** 3 || 160 * 1024 ** 3);
        const files = await walk(preparedRoot);
        const removed = [];
        for (const item of files.filter((entry) => /\.(?:part|tmp)$/i.test(entry.path))) { await fs.rm(item.path, { force: true });removed.push(item.path); }
        const sessionDirs = await hlsDirectories(preparedRoot);
        let used = sessionDirs.reduce((sum, item) => sum + item.size, 0);
        const free = await freeBytes(preparedRoot);
        for (const session of sessionDirs.sort((a, b) => a.lastUsed - b.lastUsed)) {
            if (used <= limit && free + (sessionDirs.reduce((sum, item) => item.removed ? item.size : 0)) >= minimumFreeBytes) break;
            await fs.rm(session.path, { recursive: true, force: true });session.removed = true;used -= session.size;removed.push(session.path);
        }
        return { removed, sizeBytes: used };
    }
    async function clearHls() { const target=path.join(preparedRoot,"hls");await fs.rm(target,{recursive:true,force:true});await fs.mkdir(target,{recursive:true});return inspect(); }
    return { inspect, cleanup, clearHls };
}

async function walk(directory) { const output=[];for(const entry of await fs.readdir(directory,{withFileTypes:true}).catch(()=>[])){const item=path.join(directory,entry.name);if(entry.isDirectory())output.push(...await walk(item));else{const stat=await fs.stat(item).catch(()=>null);if(stat)output.push({path:item,size:stat.size,mtimeMs:stat.mtimeMs});}}return output; }
async function hlsDirectories(preparedRoot) { const root=path.join(preparedRoot,"hls"),result=[];for(const entry of await fs.readdir(root,{withFileTypes:true}).catch(()=>[])){if(!entry.isDirectory())continue;const directory=path.join(root,entry.name),files=await walk(directory),state=await fs.readFile(path.join(directory,"session.json"),"utf8").then(JSON.parse).catch(()=>({}));result.push({path:directory,size:files.reduce((sum,item)=>sum+item.size,0),lastUsed:Date.parse(state.lastPlayedAt||state.completedAt||state.startedAt)||Math.max(0,...files.map((item)=>item.mtimeMs)),removed:false});}return result; }
async function freeBytes(directory) { if(!fs.statfs)return Number.MAX_SAFE_INTEGER;await fs.mkdir(directory,{recursive:true});const stat=await fs.statfs(directory);return Number(stat.bavail)*Number(stat.bsize); }
