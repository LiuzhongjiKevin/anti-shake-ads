import test from "node:test";
import assert from "node:assert/strict";
import { urlsFromLine, resumedComponent } from "../evidence.mjs";

test("retains actual HTTPS and custom scheme strings without inventing absent URLs", () => {
  assert.deepEqual(urlsFromLine('START {dat=imeituan://www.meituan.com/a?x=1 cmp=p.q/.A}'), ["imeituan://www.meituan.com/a?x=1"]);
  assert.deepEqual(urlsFromLine('text="https://example.test/a?x=1&amp;y=2"'), ["https://example.test/a?x=1&amp;y=2"]);
  assert.deepEqual(urlsFromLine("net::ERR_UNKNOWN_URL_SCHEME"), []);
});

test("selects resumed activity without treating historical tasks as foreground", () => {
  assert.equal(resumedComponent("ActivityRecord{foo u0 old.app/.Old}\n topResumedActivity=ActivityRecord{bar u0 ctrip.android.view/.Web} t1}"), "ctrip.android.view/.Web");
  assert.equal(resumedComponent("ActivityRecord{foo u0 old.app/.Old}"), null);
});
