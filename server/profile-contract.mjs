import crypto from "node:crypto";
import { ValidationError } from "./app-errors.mjs";
import { normalizeProfileState } from "./profile-state.mjs";
import { preserveWatchTimestamp } from "./recently-watched.mjs";

export function defaultUserState() {
  const now = new Date().toISOString();
  const profiles = [
    {
      id: "mario",
      name: "Mário",
      initials: "M",
      kind: "adult",
      avatar: { type: "initials", value: "M", color: "blue" },
      pinHash: "",
      createdAt: now,
      updatedAt: now,
    },
    {
      id: "isabele",
      name: "Isabele",
      initials: "I",
      kind: "adult",
      avatar: { type: "initials", value: "I", color: "purple" },
      pinHash: "",
      createdAt: now,
      updatedAt: now,
    },
    {
      id: "laura",
      name: "Laura",
      initials: "L",
      kind: "kids",
      maxContentRating: 10,
      avatar: { type: "initials", value: "L", color: "pink" },
      pinHash: "",
      createdAt: now,
      updatedAt: now,
    },
  ];
  return {
    version: 1,
    profiles,
    states: Object.fromEntries(
      profiles.map((p) => [p.id, emptyProfileState()]),
    ),
  };
}
export function emptyProfileState() {
  return {
    favorites: [],
    progress: {},
    history: [],
    completed: [],
    preferences: {},
    updatedAt: "",
  };
}
export function publicProfile(profile) {
  const { pinHash, ...safe } = profile;
  return { ...safe, hasPin: Boolean(pinHash) };
}
export function publicProfiles(items) {
  return items.map(publicProfile);
}
export function validateProfile(input, forcedId = "") {
  const id = forcedId || String(input.id || "");
  if (!isValidProfileId(id))
    throw new ValidationError("Identificador de perfil inválido.");
  const name = String(input.name || "")
    .trim()
    .slice(0, 40);
  if (!name) throw new ValidationError("Informe o nome do perfil.");
  const now = new Date().toISOString(),
    kind = input.kind === "kids" ? "kids" : "adult";
  return {
    id,
    name,
    initials: String(input.initials || name[0])
      .trim()
      .slice(0, 2)
      .toUpperCase(),
    kind,
    maxContentRating:
      kind === "kids"
        ? Math.min(18, Math.max(0, Number(input.maxContentRating ?? 10)))
        : undefined,
    avatar: {
      type: "initials",
      value: String(input.initials || name[0])
        .slice(0, 2)
        .toUpperCase(),
      color: String(input.avatar?.color || "blue").slice(0, 16),
    },
    pinHash: String(input.pinHash || ""),
    createdAt: String(input.createdAt || now),
    updatedAt: now,
  };
}
export function validateProfileState(input) {
  return normalizeProfileState(input, {
    validateMediaKey: isValidMediaKey,
    validateProgress,
    validateHistory,
  });
}
export function validateProgress(input, key) {
  return {
    mediaType: key.startsWith("episode:") ? "episode" : "movie",
    mediaId: String(input.mediaId || key.split(":").slice(1).join(":")),
    seriesId: String(input.seriesId || ""),
    currentTime: Number(input.currentTime || 0),
    duration: Number(input.duration || 0),
    percentage: Math.min(100, Math.max(0, Number(input.percentage || 0))),
    completed: Boolean(input.completed),
    updatedAt: preserveWatchTimestamp(input.updatedAt),
  };
}
export function validateHistory(input) {
  if (!isValidMediaKey(input.mediaKey)) throw new Error("Conteúdo inválido.");
  return {
    mediaKey: input.mediaKey,
    mediaType: input.mediaKey.startsWith("episode:") ? "episode" : "movie",
    mediaId: String(input.mediaId || ""),
    title: String(input.title || "").slice(0, 160),
    startedAt: preserveWatchTimestamp(input.startedAt),
    lastWatchedAt: preserveWatchTimestamp(input.lastWatchedAt),
    completedAt: String(input.completedAt || ""),
  };
}
export function isValidProfileId(id) {
  return /^[a-z0-9][a-z0-9-]{1,47}$/.test(id);
}
export function isValidMediaId(id) {
  return /^[a-zA-Z0-9._-]{1,120}$/.test(id);
}
export function isValidMediaKey(key) {
  return /^(movie|episode|series):[a-zA-Z0-9._-]{1,120}$/.test(key);
}
export function hashPin(pin) {
  if (!/^\d{4}$/.test(String(pin)))
    throw new Error("O PIN deve ter exatamente quatro dígitos.");
  const salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.scryptSync(String(pin), salt, 32).toString("hex");
  return `scrypt$${salt}$${hash}`;
}
export function verifyPinHash(profile, pin) {
  const parts = String(profile.pinHash || "").split("$");
  if (parts.length !== 3 || !/^\d{4}$/.test(String(pin || ""))) return false;
  try {
    return crypto.timingSafeEqual(
      crypto.scryptSync(String(pin), parts[1], 32),
      Buffer.from(parts[2], "hex"),
    );
  } catch {
    return false;
  }
}
export function requiresAdminProfileSession(request, url) {
  if (request.method === "POST" && url.pathname === "/api/profiles")
    return true;
  const parts = url.pathname.split("/").filter(Boolean),
    resource = parts[3] || "";
  return (
    ((request.method === "PUT" || request.method === "DELETE") && !resource) ||
    (request.method === "PUT" && resource === "pin")
  );
}
