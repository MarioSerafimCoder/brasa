import assert from "node:assert/strict";
import fs from "node:fs/promises";

const root = "apps/android-tv/app/src/main/java/com/brasa/tv";
const theme = await fs.readFile(`${root}/designsystem/BrasaTheme.kt`, "utf8");
const components = await fs.readFile(`${root}/designsystem/Components.kt`, "utf8");
const home = await fs.readFile(`${root}/feature/home/HomeScreen.kt`, "utf8");
const pin = await fs.readFile(`${root}/feature/profiles/ProfilePinScreen.kt`, "utf8");
const player = await fs.readFile(`${root}/feature/player/PlayerScreen.kt`, "utf8");
const http = await fs.readFile(`${root}/core/network/BrasaHttpClient.kt`, "utf8");
const factory = await fs.readFile(`${root}/core/playback/PlaybackFactory.kt`, "utf8");
const settings = await fs.readFile(`${root}/data/storage/AppSettingsStore.kt`, "utf8");

assert.match(theme, /val hero = 48\.sp/);
assert.match(theme, /val body = 18\.sp/);
assert.match(components, /LocalCardDensity\.current/);
assert.match(home, /rememberLazyListState/);
assert.match(home, /scrollToItem\(0\)/);
assert.match(components, /onDispose \{ keyboard\?\.hide\(\) \}/);
assert.doesNotMatch(pin, /BasicTextField|KeyboardType|LocalSoftwareKeyboardController/);
assert.match(pin, /listOf\(listOf\("1","2","3"\)/);
assert.match(settings, /floatPreferencesKey\("ui_scale"\)/);
assert.match(settings, /floatPreferencesKey\("card_density"\)/);
assert.match(theme, /LocalDensity provides scaledDensity/);
assert.match(http, /readTimeout\(60,TimeUnit\.SECONDS\)/);
assert.match(factory, /DefaultLoadErrorHandlingPolicy\(6\)/);
assert.match(player, /Tentar novamente/);
console.log("Regressoes Android TV: escala, topo, teclado e player aprovados.");
