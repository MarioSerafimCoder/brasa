import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { createHlsSessionManager } from "../server/hls-session.mjs";
import { precedingKeyframe } from "../server/remux-seek.mjs";
import { hlsTimeline } from "../server/playback-timeline.mjs";

assert.equal(precedingKeyframe({frames:[{best_effort_timestamp_time:"2"},{best_effort_timestamp_time:"8.5"},{best_effort_timestamp_time:"14"}]},10000),8500);
assert.equal(precedingKeyframe({format:{start_time:"1.4"},frames:[{best_effort_timestamp_time:"9.9"}]},10000),8500);
assert.equal(precedingKeyframe({frames:[]},10000),null);
assert.deepEqual(hlsTimeline(10500,8500),{playbackOffset:8500,resumePosition:2000});
const root=await fs.mkdtemp(path.join(os.tmpdir(),"brasa-remux-seek-"));
const children=[];let probes=0;
const manager=createHlsSessionManager({rootDir:root,store:{update:async()=>{}},
    getTools:async()=>({ffmpegAvailable:true,ffmpegPath:"ffmpeg",ffprobePath:"ffprobe"}),
    findKeyframe:async(_command,input)=>{probes++;return input==="missing-keyframes"?null:8500;},
    spawnProcess:(_command,args)=>{const child=new EventEmitter();child.stdout=new PassThrough();child.stderr=new PassThrough();child.kill=()=>queueMicrotask(()=>child.emit("close",1));children.push({child,args});return child;}});
const probe={duration:120,fingerprint:{size:1000,mtimeMs:1},video:{codec:"h264",width:1920,height:1080},audioTracks:[{codec:"aac"}]};
try {
    const plan={mode:"remux",startPositionMs:10500};
    const first=await manager.ensure("movie:copy","source",probe,{},plan);
    assert.equal(first.videoCopied,true);
    assert.equal(first.startPositionMs,8500);
    await waitFor(()=>children.length===1);
    assert.equal(children[0].args[children[0].args.indexOf("-c:v")+1],"copy");
    await manager.ensure("movie:copy","source",probe,{},plan);
    assert.equal(probes,1,"polling não repete leitura de keyframes");
    children[0].child.emit("close",1);
    await waitFor(()=>manager.snapshot().length===0);
    const fallback=await manager.ensure("movie:copy","source",probe,{},plan);
    assert.equal(fallback.videoCopied,false,"falha do remux mantém conversão automática");
    await waitFor(()=>children.length===2);
    for(let index=0;index<3;index++) assert.equal((await manager.ensure("movie:copy","source",probe,{},plan)).id,fallback.id);
    assert.equal(children.length,2,"polling do plano original não cancela a conversão de recuperação");
    const unknown=await manager.ensure("movie:unknown","missing-keyframes",probe,{},plan);
    assert.equal(unknown.videoCopied,false,"sem ponto seguro recorre à conversão");
    console.log("Remux: retomada em keyframe, timeline, cache da sondagem e recuperação sem reiniciar encoder aprovados.");
} finally { for(const session of manager.snapshot()) await manager.remove(session.id);await fs.rm(root,{recursive:true,force:true}); }
async function waitFor(predicate){const deadline=Date.now()+3000;while(!predicate()){if(Date.now()>deadline)throw new Error("timeout");await new Promise(resolve=>setTimeout(resolve,10));}}
