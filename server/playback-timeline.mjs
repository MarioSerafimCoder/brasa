export function hlsTimeline(resumePosition, startPosition) {
    const offset = Math.max(0, Math.round(Number(startPosition) || 0));
    const resume = Math.max(0, Math.round(Number(resumePosition) || 0));
    return { playbackOffset: offset, resumePosition: Math.max(0, resume - offset) };
}
