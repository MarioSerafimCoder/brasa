import {
  defaultUserState,
  emptyProfileState,
  publicProfile,
  publicProfiles,
  validateProfile,
  validateProfileState,
  validateProgress,
  validateHistory,
  isValidProfileId,
  isValidMediaId,
  isValidMediaKey,
  hashPin,
  verifyPinHash,
  requiresAdminProfileSession,
} from "./profile-contract.mjs";
import { ValidationError } from "./app-errors.mjs";
import {
  progressWriteTime,
  mergeImportedProfileState,
} from "./user-state-store.mjs";

export function createProfileController({
  readUserState,
  queueUserStateWrite,
  readJsonBody,
  sendJson,
}) {
  const pinAttempts = new Map();
  async function handleProfilesApi(request, response, url) {
    const state = await readUserState();
    const parts = url.pathname.split("/").filter(Boolean);
    const profileId = decodeURIComponent(parts[2] || "");
    const resource = parts[3] || "";
    const resourceId = decodeURIComponent(parts.slice(4).join("/"));

    if (request.method === "GET" && url.pathname === "/api/profiles")
      return sendJson(response, 200, {
        profiles: publicProfiles(state.profiles),
      });
    if (request.method === "POST" && url.pathname === "/api/profiles") {
      const input = await readJsonBody(request);
      const profile = validateProfile(input);
      if (state.profiles.some((item) => item.id === profile.id))
        return sendJson(response, 409, {
          ok: false,
          message: "Já existe um perfil com este identificador.",
        });
      state.profiles.push(profile);
      state.states[profile.id] = emptyProfileState();
      await queueUserStateWrite(state);
      return sendJson(response, 201, { profile: publicProfile(profile) });
    }
    if (
      !isValidProfileId(profileId) ||
      !state.profiles.some((item) => item.id === profileId)
    )
      return sendJson(response, 404, {
        ok: false,
        message: "Perfil não encontrado.",
      });
    const profileIndex = state.profiles.findIndex(
      (item) => item.id === profileId,
    );
    if (request.method === "GET" && resource === "state")
      return sendJson(response, 200, {
        state: state.states[profileId] || emptyProfileState(),
      });
    if (request.method === "PUT" && resource === "preferences") {
      const current = state.states[profileId],
        input = await readJsonBody(request);
      state.states[profileId] = validateProfileState({
        ...current,
        preferences: { ...current.preferences, ...input },
        updatedAt: new Date().toISOString(),
      });
      await queueUserStateWrite(state);
      return sendJson(response, 200, { state: state.states[profileId] });
    }
    if (request.method === "PATCH" && resource === "state") {
      state.states[profileId] = validateProfileState(
        mergeImportedProfileState(
          state.states[profileId],
          validateProfileState(await readJsonBody(request)),
        ),
      );
      await queueUserStateWrite(state);
      return sendJson(response, 200, { state: state.states[profileId] });
    }
    if (request.method === "PUT" && resource === "state") {
      const input = await readJsonBody(request),
        current = state.states[profileId];
      if ((input.updatedAt || "") !== (current.updatedAt || ""))
        return sendJson(response, 409, {
          ok: false,
          message:
            "Este perfil mudou em outro aparelho. Recarregue os dados antes de salvar.",
        });
      state.states[profileId] = validateProfileState({
        ...current,
        ...input,
        updatedAt: new Date().toISOString(),
      });
      await queueUserStateWrite(state);
      return sendJson(response, 200, { state: state.states[profileId] });
    }
    if (request.method === "PUT" && !resource) {
      state.profiles[profileIndex] = validateProfile(
        {
          ...state.profiles[profileIndex],
          ...(await readJsonBody(request)),
          id: profileId,
        },
        profileId,
      );
      await queueUserStateWrite(state);
      return sendJson(response, 200, {
        profile: publicProfile(state.profiles[profileIndex]),
      });
    }
    if (request.method === "DELETE" && !resource) {
      if (
        state.profiles[profileIndex].kind === "adult" &&
        state.profiles.filter((item) => item.kind === "adult").length === 1
      )
        return sendJson(response, 409, {
          ok: false,
          message: "O último perfil adulto não pode ser excluído.",
        });
      state.profiles.splice(profileIndex, 1);
      delete state.states[profileId];
      await queueUserStateWrite(state);
      return sendJson(response, 200, { ok: true });
    }
    if (resource === "favorites" && isValidMediaId(resourceId)) {
      const current = state.states[profileId],
        list = current.favorites;
      const favorites =
        request.method === "PUT"
          ? [...new Set([...list, resourceId])].slice(0, 5000)
          : list.filter((id) => id !== resourceId);
      state.states[profileId] = {
        ...current,
        favorites,
        updatedAt: new Date().toISOString(),
      };
      await queueUserStateWrite(state);
      return sendJson(response, 200, {
        ok: true,
        favorites: state.states[profileId].favorites,
      });
    }
    if (resource === "progress" && isValidMediaKey(resourceId)) {
      const current = state.states[profileId],
        progress = { ...current.progress };
      if (request.method === "PUT") {
        const input = await readJsonBody(request),
          timing = progressWriteTime(progress[resourceId], input);
        if (timing.stale) return sendJson(response, 200, { ok: true });
        progress[resourceId] = validateProgress(
          { ...input, updatedAt: timing.updatedAt },
          resourceId,
        );
      }
      if (request.method === "DELETE") delete progress[resourceId];
      state.states[profileId] = {
        ...current,
        progress,
        updatedAt: new Date().toISOString(),
      };
      await queueUserStateWrite(state);
      return sendJson(response, 200, { ok: true });
    }
    if (resource === "history") {
      if (request.method === "POST") {
        const entry = validateHistory({
          ...(await readJsonBody(request)),
          lastWatchedAt: new Date().toISOString(),
        });
        state.states[profileId].history = [
          entry,
          ...state.states[profileId].history.filter(
            (item) => item.mediaKey !== entry.mediaKey,
          ),
        ].slice(0, 500);
        await queueUserStateWrite(state);
        return sendJson(response, 200, { ok: true });
      }
      if (request.method === "DELETE") {
        state.states[profileId].history = [];
        await queueUserStateWrite(state);
        return sendJson(response, 200, { ok: true });
      }
    }
    if (resource === "pin" && request.method === "PUT") {
      const { pin = "", currentPin = "" } = await readJsonBody(request);
      const profile = state.profiles[profileIndex];
      if (profile.pinHash && !verifyPinHash(profile, currentPin))
        return sendJson(response, 403, {
          ok: false,
          message: "Confirme o PIN atual antes de alterar ou remover.",
        });
      profile.pinHash = pin ? hashPin(pin) : "";
      await queueUserStateWrite(state);
      return sendJson(response, 200, { ok: true });
    }
    if (resource === "verify-pin" && request.method === "POST")
      return verifyProfilePin(
        response,
        state.profiles[profileIndex],
        await readJsonBody(request),
      );
    sendJson(response, 405, { ok: false, message: "Método não permitido." });
  }

  function verifyProfilePin(response, profile, input) {
    const record = pinAttempts.get(profile.id) || { count: 0, blockedUntil: 0 };
    if (Date.now() < record.blockedUntil)
      return sendJson(response, 429, {
        ok: false,
        message:
          "Não foi possível verificar o PIN. Tente novamente em 30 segundos.",
      });
    let valid = verifyPinHash(profile, input.pin);
    if (valid) {
      pinAttempts.delete(profile.id);
      return sendJson(response, 200, { ok: true });
    }
    record.count++;
    if (record.count >= 5) {
      record.blockedUntil = Date.now() + 30000;
      record.count = 0;
    }
    pinAttempts.set(profile.id, record);
    sendJson(response, 401, { ok: false, message: "PIN inválido." });
  }
  async function adminListProfiles() {
    const state = await readUserState();
    return publicProfiles(state.profiles);
  }
  async function adminCreateProfile(input) {
    const state = await readUserState(),
      profile = validateProfile(input);
    if (state.profiles.some((item) => item.id === profile.id))
      throw new ValidationError("Já existe um perfil com este identificador.");
    if (input.pin) profile.pinHash = hashPin(input.pin);
    state.profiles = [...state.profiles, profile];
    state.states = { ...state.states, [profile.id]: emptyProfileState() };
    await queueUserStateWrite(state);
    return publicProfile(profile);
  }
  async function adminUpdateProfile(id, input) {
    if (!isValidProfileId(id)) throw new ValidationError("Perfil inválido.");
    const state = await readUserState(),
      index = state.profiles.findIndex((item) => item.id === id);
    if (index < 0) throw new ValidationError("Perfil não encontrado.");
    const current = state.profiles[index],
      profile = validateProfile({ ...current, ...input, id }, id);
    profile.pinHash =
      input.pin === undefined
        ? current.pinHash
        : input.pin
          ? hashPin(input.pin)
          : "";
    state.profiles = state.profiles.map((item) =>
      item.id === id ? profile : item,
    );
    await queueUserStateWrite(state);
    return publicProfile(profile);
  }
  async function adminRemoveProfile(id) {
    const state = await readUserState(),
      profile = state.profiles.find((item) => item.id === id);
    if (!profile) throw new ValidationError("Perfil não encontrado.");
    if (
      profile.kind === "adult" &&
      state.profiles.filter((item) => item.kind === "adult").length === 1
    )
      throw new ValidationError(
        "O último perfil adulto não pode ser removido.",
      );
    state.profiles = state.profiles.filter((item) => item.id !== id);
    const states = { ...state.states };
    delete states[id];
    state.states = states;
    await queueUserStateWrite(state);
    return { removed: true };
  }
  async function adminClearProfile(id, action) {
    const state = await readUserState();
    if (!state.profiles.some((item) => item.id === id))
      throw new ValidationError("Perfil não encontrado.");
    const current = validateProfileState(state.states[id] || {}),
      next = { ...current };
    if (action === "clear-favorites") next.favorites = [];
    if (action === "clear-progress") {
      next.progress = {};
      next.completed = [];
    }
    if (action === "clear-history") next.history = [];
    if (action === "reset-preferences") next.preferences = {};
    next.updatedAt = new Date().toISOString();
    state.states = { ...state.states, [id]: next };
    await queueUserStateWrite(state);
    return { cleared: true, action };
  }

  return {
    handleProfilesApi,
    adminListProfiles,
    adminCreateProfile,
    adminUpdateProfile,
    adminRemoveProfile,
    adminClearProfile,
  };
}
