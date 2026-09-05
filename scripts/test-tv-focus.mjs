import assert from "node:assert/strict";
import { nextFocusIndex } from "../tv/focus-manager.js";

const items = [{ row: 0, column: 0 }, { row: 0, column: 1 }, { row: 0, column: 2 }, { row: 1, column: 0 }, { row: 1, column: 1 }, { row: 2, column: 0 }];
assert.equal(nextFocusIndex(items, 0, "right"), 1);
assert.equal(nextFocusIndex(items, 1, "left"), 0);
assert.equal(nextFocusIndex(items, 2, "down"), 4);
assert.equal(nextFocusIndex(items, 4, "up"), 1);
assert.equal(nextFocusIndex(items, 4, "down"), 5);
assert.equal(nextFocusIndex(items, 0, "left"), 0);
assert.equal(nextFocusIndex([], 0, "right"), -1);
console.log("Foco da TV: 7 cenários aprovados.");
