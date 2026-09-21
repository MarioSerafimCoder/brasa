// Optional integration check with the bundled FFmpeg. Uses only synthetic media.
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { findRemuxKeyframe } from "../server/remux-seek.mjs";
import { createHlsSessionManager } from "../server/hls-session.mjs";
import { probeMedia } from "../server/media-probe.mjs";
const run=promisify(execFile);
const ffmpeg=path.resolve("tools/ffmpeg/ffmpeg.exe"), ffprobe=path.resolve("tools/ffmpeg/ffprobe.exe");
const root=await fs.mkdtemp(path.join(os.tmpdir(),"brasa-remux-real-"));
const manager=createHlsSessionManager({rootDir:root,store:{update:async()=>{}},getTools:async()=>({ffmpegAvailable:true,ffmpegPath:ffmpeg,ffprobePath:ffprobe})});
try {
    const input=path.join(root,"source.mp4");
    await run(ffmpeg,["-hide_banner","-loglevel","error","-f","lavfi","-i","testsrc2=size=160x90:rate=24","-f","lavfi","-i","sine=frequency=440:sample_rate=48000","-t","45","-c:v","libx264","-preset","ultrafast","-g","72","-keyint_min","72","-sc_threshold","0","-c:a","aac",input],{windowsHide:true,timeout:30000});
    const probe=await probeMedia(input,ffprobe);
    const keyframe=await findRemuxKeyframe(ffprobe,input,20750);
    assert.equal(keyframe,18000);
    const plan={mode:"remux",audioAction:"copy",startPositionMs:20750};
    let session=await manager.ensure("movie:synthetic",input,probe,{},plan);
    const deadline=Date.now()+20000;
    while(session.state!=="ready") {
        assert.notEqual(session.state,"failed");
        if(Date.now()>deadline)throw new Error("Tempo excedido preparando remux real.");
        await new Promise(resolve=>setTimeout(resolve,100));
        session=await manager.ensure("movie:synthetic",input,probe,{},plan);
    }
    assert.equal(session.videoCopied,true);
    assert.equal(session.startPositionMs,18000);
    const segment=await manager.resolve(session.id,"original/seg-000000.ts");
    assert.ok(segment);
    const {stdout}=await run(ffprobe,["-v","error","-select_streams","v:0","-read_intervals","%+#1","-show_frames","-show_entries","frame=key_frame,width,height","-of","json",segment.file],{windowsHide:true,timeout:10000});
    const frame=JSON.parse(stdout).frames[0];
    assert.equal(frame.key_frame,1);
    assert.equal(frame.width,160);
    assert.equal(frame.height,90);
    console.log("FFmpeg real: retomada de 20,75s usa keyframe de 18s; primeiro segmento decodifica sem conversão de vídeo.");
} finally { for(const session of manager.snapshot())await manager.remove(session.id);await fs.rm(root,{recursive:true,force:true}); }
