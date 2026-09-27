import fs from "node:fs/promises";
import path from "node:path";
import { ValidationError } from "./app-errors.mjs";

export function createCollectionsController({
  userCollectionsFile,
  readJsonBody,
  sendJson,
}) {
  const systemCollectionIds = new Set([
    "mcu",
    "dc",
    "spider-man",
    "star-wars",
    "lotr",
    "harry-potter",
    "jurassic",
    "mission-impossible",
    "science-fiction",
    "classics",
    "fast-furious",
    "pirates-caribbean",
    "rocky",
    "pixar",
    "disney-classics",
    "dreamworks",
    "ghibli",
    "best-picture",
  ]);
  let userCollectionsWriteQueue = Promise.resolve();
  async function handleCollectionsApi(request, response, url) {
    const id = decodeURIComponent(
      url.pathname.slice("/api/collections/".length),
    );
    const collections = await readUserCollections();

    if (request.method === "GET" && url.pathname === "/api/collections") {
      sendJson(response, 200, { collections });
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/collections") {
      const input = validateCollection(await readJsonBody(request));
      if (
        systemCollectionIds.has(input.id) ||
        collections.some((item) => item.id === input.id)
      ) {
        sendJson(response, 409, {
          ok: false,
          message: "Já existe uma coleção com este identificador.",
        });
        return;
      }
      await writeUserCollections([...collections, input]);
      sendJson(response, 201, { collection: input });
      return;
    }

    if (!isValidCollectionId(id)) {
      sendJson(response, 400, {
        ok: false,
        message: "Identificador de coleção inválido.",
      });
      return;
    }

    if (systemCollectionIds.has(id)) {
      sendJson(response, 403, {
        ok: false,
        message: "Coleções do sistema não podem ser alteradas ou excluídas.",
      });
      return;
    }

    const index = collections.findIndex((item) => item.id === id);
    if (index < 0) {
      sendJson(response, 404, {
        ok: false,
        message: "Coleção não encontrada.",
      });
      return;
    }

    if (request.method === "PUT") {
      const input = validateCollection(await readJsonBody(request), id);
      collections[index] = input;
      await writeUserCollections(collections);
      sendJson(response, 200, { collection: input });
      return;
    }

    if (request.method === "DELETE") {
      collections.splice(index, 1);
      await writeUserCollections(collections);
      sendJson(response, 200, { ok: true });
      return;
    }

    sendJson(response, 405, { ok: false, message: "Método não permitido." });
  }

  async function readUserCollections() {
    await fs.mkdir(path.dirname(userCollectionsFile), { recursive: true });
    const content = await fs
      .readFile(userCollectionsFile, "utf8")
      .catch(async (error) => {
        if (error.code !== "ENOENT") throw error;
        await fs.writeFile(userCollectionsFile, "[]\n", "utf8");
        return "[]";
      });
    try {
      const parsed = JSON.parse(content || "[]");
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      const corrupt = `${userCollectionsFile}.corrupt-${Date.now()}`;
      await fs.rename(userCollectionsFile, corrupt).catch(() => {});
      const backup = await fs
        .readFile(`${userCollectionsFile}.backup.json`, "utf8")
        .then(JSON.parse)
        .catch(() => []);
      console.error(
        `BRasa: coleções corrompidas preservadas em ${corrupt}.`,
        error.message,
      );
      const recovered = Array.isArray(backup) ? backup : [];
      await writeUserCollections(recovered);
      return recovered;
    }
  }

  async function writeUserCollections(collections) {
    userCollectionsWriteQueue = userCollectionsWriteQueue.then(async () => {
      const temporaryFile = `${userCollectionsFile}.${process.pid}.tmp`,
        backup = `${userCollectionsFile}.backup.json`,
        previous = await fs
          .readFile(userCollectionsFile, "utf8")
          .catch(() => "");
      if (previous) await fs.writeFile(backup, previous, "utf8");
      await fs.writeFile(
        temporaryFile,
        `${JSON.stringify(collections, null, 2)}\n`,
        "utf8",
      );
      await fs.rename(temporaryFile, userCollectionsFile);
    });
    return userCollectionsWriteQueue;
  }

  function validateCollection(input, forcedId = "") {
    if (!input || typeof input !== "object" || Array.isArray(input))
      throw new Error("Dados da coleção inválidos.");
    const id = forcedId || String(input.id || "");
    if (!isValidCollectionId(id))
      throw new Error("Identificador de coleção inválido.");
    const title = String(input.title || "")
      .trim()
      .slice(0, 80);
    if (!title) throw new Error("Informe o nome da coleção.");
    const type = input.type === "smart" ? "smart" : "manual";
    const scope = input.scope === "shared" ? "shared" : "profile";
    const movieIds = Array.isArray(input.movieIds)
      ? [...new Set(input.movieIds.map(String))].slice(0, 2000)
      : [];
    const ruleItems = Array.isArray(input.rules?.items)
      ? input.rules.items.slice(0, 20).map((rule) => ({
          field: String(rule.field || "title").slice(0, 32),
          operator: String(rule.operator || "contains").slice(0, 24),
          value:
            typeof rule.value === "boolean"
              ? rule.value
              : String(rule.value ?? "").slice(0, 160),
        }))
      : [];
    return {
      id,
      title,
      description: String(input.description || "")
        .trim()
        .slice(0, 300),
      type,
      source: "user",
      scope,
      profileId:
        scope === "profile" ? String(input.profileId || "").slice(0, 48) : null,
      banner: String(input.banner || "").slice(0, 500),
      movieIds: type === "manual" ? movieIds : [],
      rules:
        type === "smart"
          ? {
              match: input.rules?.match === "any" ? "any" : "all",
              items: ruleItems,
            }
          : null,
      sort: {
        field: String(input.sort?.field || "title").slice(0, 24),
        direction: input.sort?.direction === "desc" ? "desc" : "asc",
      },
      createdAt: String(input.createdAt || new Date().toISOString()),
      updatedAt: new Date().toISOString(),
    };
  }

  function isValidCollectionId(id) {
    return /^[a-z0-9][a-z0-9-]{2,63}$/.test(String(id || ""));
  }

  async function adminCreateCollection(input) {
    const collections = await readUserCollections(),
      collection = validateCollection(input);
    if (
      systemCollectionIds.has(collection.id) ||
      collections.some((item) => item.id === collection.id)
    )
      throw new ValidationError(
        "Já existe uma coleção com este identificador.",
      );
    await writeUserCollections([...collections, collection]);
    return collection;
  }
  async function adminUpdateCollection(id, input) {
    if (systemCollectionIds.has(id))
      throw new ValidationError("Coleções do sistema são somente leitura.");
    const collections = await readUserCollections(),
      index = collections.findIndex((item) => item.id === id);
    if (index < 0) throw new ValidationError("Coleção não encontrada.");
    const collection = validateCollection({ ...input, id }, id),
      next = [...collections];
    next[index] = collection;
    await writeUserCollections(next);
    return collection;
  }
  async function adminRemoveCollection(id) {
    if (systemCollectionIds.has(id))
      throw new ValidationError("Coleções do sistema são somente leitura.");
    const collections = await readUserCollections();
    if (!collections.some((item) => item.id === id))
      throw new ValidationError("Coleção não encontrada.");
    await writeUserCollections(collections.filter((item) => item.id !== id));
    return { removed: true };
  }

  return {
    handleCollectionsApi,
    readUserCollections,
    adminCreateCollection,
    adminUpdateCollection,
    adminRemoveCollection,
  };
}
