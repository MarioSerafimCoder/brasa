export function resolveByteRange(value, size) {
    if (!Number.isSafeInteger(size) || size < 1) return { satisfiable: false };
    if (!value) return { satisfiable: true, partial: false, start: 0, end: size - 1 };
    const match = /^bytes=(\d*)-(\d*)$/.exec(String(value).trim());
    if (!match || (!match[1] && !match[2])) return { satisfiable: false };
    if (!match[1]) {
        const suffixLength = Number(match[2]);
        if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) return { satisfiable: false };
        return { satisfiable: true, partial: true, start: Math.max(size - suffixLength, 0), end: size - 1 };
    }
    const start = Number(match[1]);
    const requestedEnd = match[2] ? Number(match[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(requestedEnd) || start < 0 || start >= size || requestedEnd < start) return { satisfiable: false };
    return { satisfiable: true, partial: true, start, end: Math.min(requestedEnd, size - 1) };
}
