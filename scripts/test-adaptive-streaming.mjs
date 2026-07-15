import assert from "node:assert/strict";
import path from "node:path";
import { shouldUseAdaptiveHls, createQualityLadder, createStartupLadder, estimateHlsCacheBytes, selectTvPlaybackPlan } from "../server/transcoding-profiles.mjs";
import { buildHlsArgs, buildRemuxHlsArgs } from "../server/hls-session.mjs";

const probe = (overrides = {}) => ({ duration: 7200, size: 2 * 1024 ** 3, bitrate: 8_000_000, container: "mov", video: { codec: "h264", width: 1920, height: 1080, bitDepth: 8 }, audioTracks: [{ codec: "aac" }], ...overrides });
assert.equal(shouldUseAdaptiveHls(probe()).useHls, false, "MP4 H.264 leve deve usar direct play");
assert.equal(shouldUseAdaptiveHls(probe({ size: 21 * 1024 ** 3 })).required, true, "arquivo acima de 20 GB exige HLS para clientes antigos");
assert.equal(shouldUseAdaptiveHls(probe({ bitrate: 21_000_000 })).useHls, true, "bitrate alto exige HLS para clientes antigos");
assert.equal(shouldUseAdaptiveHls(probe({ video: { codec: "hevc", width: 1920, height: 1080 } })).useHls, true, "HEVC exige HLS para clientes antigos");
assert.equal(shouldUseAdaptiveHls(probe({ audioTracks: [{ codec: "dts" }] })).useHls, true, "DTS exige HLS para clientes antigos");

const ladder1080 = createQualityLadder(probe());
assert.deepEqual(ladder1080.map((item) => item.id), ["720p", "1080p"]);
const ladder4k = createQualityLadder(probe({ video: { codec: "hevc", width: 3840, height: 2160 } }));
assert.deepEqual(ladder4k.map((item) => item.id), ["720p", "1080p", "2160p"]);
assert.deepEqual(createQualityLadder(probe({ video: { codec: "hevc", width: 3840, height: 2016 } })).map((item) => item.id), ["720p", "1080p", "2160p"]);
assert.equal(createQualityLadder(probe({ video: { codec: "h264", width: 1280, height: 720 } })).some((item) => item.height > 720), false, "não deve fazer upscale");
assert.deepEqual(createStartupLadder(probe({ video: { codec: "hevc", width: 3840, height: 2160, hdr: true } })).map((item) => item.id), ["720p"], "HDR deve iniciar em uma única variante leve");
assert.deepEqual(createStartupLadder(probe({ video: { codec: "h264", width: 3840, height: 2160 } })).map((item) => item.id), ["1080p"], "SDR deve iniciar em uma única variante 1080p");
assert.ok(estimateHlsCacheBytes(7200, ladder1080) > 0);

const args = buildHlsArgs("movie.mkv", path.resolve("cache"), ladder1080);
assert.equal(args[args.indexOf("-hls_time") + 1], "2", "segmentos devem ter dois segundos");
assert.ok(args.includes("independent_segments+temp_file"), "segmentos devem usar escrita temporária atômica");
assert.ok(args.includes("master.m3u8"), "manifesto principal obrigatório");
assert.ok(args.some((value) => value.includes("%v") && value.includes("seg-%06d.ts")), "segmentos variantes obrigatórios");
assert.ok(args.includes("event"), "playlist deve permanecer disponível durante o processamento");
const cudaArgs = buildHlsArgs("movie.mkv", path.resolve("cache"), ladder1080, "h264_nvenc", probe());
assert.deepEqual(cudaArgs.slice(0, 5), ["-y", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda"]);
assert.ok(cudaArgs.some((value) => value.includes("scale_cuda")), "NVENC deve manter redimensionamento na GPU");
assert.ok(cudaArgs.includes("p1") && cudaArgs.includes("vbr"), "NVENC deve usar preset rápido e controle de taxa apropriados");
assert.ok(cudaArgs.includes("48") && cudaArgs.includes("1"), "HLS deve forçar GOP e IDR periódicos");
const hdrArgs = buildHlsArgs("movie.mkv", path.resolve("cache"), ladder1080, "h264_nvenc", probe({ video: { codec: "hevc", width: 1920, height: 1080, hdr: true } }));
assert.ok(hdrArgs.some((value) => value.includes("scale_cuda") && value.includes("hwdownload") && value.includes("tonemap")), "HDR deve reduzir na GPU antes do tone mapping compatível");
assert.ok(hdrArgs.includes("hevc_cuvid"), "HEVC pesado deve usar o decoder CUVID que produz quadros válidos para o Superman");

const googleTv = { containers: ["matroska", "mp4", "hls"], videoCodecs: ["h264", "hevc", "hevc-main10", "dolby-vision"], audioCodecs: ["aac", "eac3"], hdrTypes: ["hdr10", "dolby-vision"], maxWidth: 3840, maxHeight: 2160 };
const superman = probe({ size: 25_119_571_112, container: "matroska", video: { codec: "hevc", width: 3840, height: 2160, bitDepth: 10, hdr: true, hdrType: "dolby-vision", dolbyVision: true }, audioTracks: [{ codec: "eac3" }] });
assert.equal(selectTvPlaybackPlan(superman, googleTv).mode, "direct", "Google TV compatível deve receber o Superman original mesmo acima de 20 GB");
const remuxPlan = selectTvPlaybackPlan({ ...superman, audioTracks: [{ codec: "dts" }] }, googleTv);
assert.equal(remuxPlan.mode, "remux", "áudio incompatível deve usar remux");
assert.equal(remuxPlan.audioAction, "aac", "remux deve converter somente o áudio");
assert.equal(selectTvPlaybackPlan(superman, { ...googleTv, videoCodecs: ["h264"], hdrTypes: [] }).mode, "transcode", "TV sem HEVC/HDR deve receber transcodificação");
const remuxArgs = buildRemuxHlsArgs("movie.mkv", path.resolve("cache"), superman, { audioAction: "aac" });
assert.equal(remuxArgs[remuxArgs.indexOf("-c:v") + 1], "copy");
assert.equal(remuxArgs[remuxArgs.indexOf("-c:a") + 1], "aac");
assert.equal(remuxArgs[remuxArgs.indexOf("-hls_time") + 1], "2");
console.log("Streaming adaptativo: capacidades da TV, direct play, remux e HLS aprovados.");
