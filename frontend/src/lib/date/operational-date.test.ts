import assert from "node:assert/strict";
import test from "node:test";

import { formatOperationalDate } from "./operational-date.ts";

test("operational date is rendered in Asia Jakarta timezone", () => {
  assert.equal(formatOperationalDate(new Date("2026-07-11T18:00:00Z")), "12 Jul 2026");
});
