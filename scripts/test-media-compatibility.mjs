import assert from "node:assert/strict";import {decideMediaStrategy,isProbeStillValid} from "../server/media-compatibility.mjs";
const probe=(container,video,audio,extra={})=>({container,duration:7200,video:{codec:video,bitDepth:8,...extra},audioTracks:[{codec:audio}]});
const cases=[[probe("mov,mp4","h264","aac"),"direct-play"],[probe("matroska","h264","aac"),"remux"],[probe("matroska","h264","dts"),"audio-transcode"],[probe("matroska","hevc","dts"),"video-transcode"],[probe("webm","vp9","opus"),"direct-play"],[{container:"matroska",duration:0,video:null,audioTracks:[]},"corrupted"],[probe("matroska","unknown","aac"),"unsupported"],[probe("matroska","h264","aac",{bitDepth:10}),"video-transcode"]];
for(const [input,expected] of cases)assert.equal(decideMediaStrategy(input).strategy,expected);
assert.equal(isProbeStillValid({fingerprint:{size:10,mtimeMs:20}},{size:10,mtimeMs:20}),true);assert.equal(isProbeStillValid({fingerprint:{size:10,mtimeMs:20}},{size:11,mtimeMs:20}),false);
console.log(`Matriz de compatibilidade: ${cases.length+2} testes aprovados.`);
