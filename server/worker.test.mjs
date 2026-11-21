import { test } from "node:test";
import assert from "node:assert/strict";
import { handle, MAX_TOKENS, MAX_BODY_BYTES } from "./worker.mjs";

const env = { ANTHROPIC_API_KEY: "sk-ant-real", APP_TOKEN: "app-secret" };

function req(body, { token = "app-secret", path = "/v1/messages", method = "POST", headers = {} } = {}) {
  return new Request(`https://proxy.example${path}`, {
    method,
    headers: { "content-type": "application/json", "x-api-key": token, "anthropic-version": "2023-06-01", ...headers },
    body: method === "POST" ? (typeof body === "string" ? body : JSON.stringify(body)) : undefined,
  });
}

function fakeUpstream() {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init, body: JSON.parse(init.body) });
    return new Response(JSON.stringify({ id: "msg_1", stop_reason: "end_turn" }), { status: 200, headers: { "content-type": "application/json" } });
  };
  return { fn, calls };
}

const ok = { model: "claude-opus-5-5", max_tokens: 4000, messages: [{ role: "user", content: "two roti" }] };

test("forwards a valid request with the real key, never the app token", async () => {
  const up = fakeUpstream();
  const res = await handle(req(ok), env, up.fn);
  assert.equal(res.status, 200);
  assert.equal(up.calls.length, 1);
  assert.equal(up.calls[0].url, "https://api.anthropic.com/v1/messages");
  assert.equal(up.calls[0].init.headers["x-api-key"], "sk-ant-real");
  assert.equal(up.calls[0].init.headers["anthropic-version"], "2023-06-01");
  assert.deepEqual(up.calls[0].body, ok);
  assert.equal((await res.json()).id, "msg_1");
});

test("rejects a wrong or missing app token", async () => {
  const up = fakeUpstream();
  assert.equal((await handle(req(ok, { token: "nope" }), env, up.fn)).status, 401);
  assert.equal((await handle(req(ok, { token: "" }), env, up.fn)).status, 401);
  assert.equal(up.calls.length, 0);
});

test("refuses to run without both secrets set", async () => {
  const up = fakeUpstream();
  assert.equal((await handle(req(ok), { APP_TOKEN: "app-secret" }, up.fn)).status, 500);
  assert.equal((await handle(req(ok, { token: "" }), { ANTHROPIC_API_KEY: "k" }, up.fn)).status, 401);
});

test("only POST /v1/messages is allowed", async () => {
  const up = fakeUpstream();
  assert.equal((await handle(req(ok, { path: "/v1/models" }), env, up.fn)).status, 404);
  assert.equal((await handle(req(null, { method: "GET" }), env, up.fn)).status, 404);
  assert.equal(up.calls.length, 0);
});

test("only the app's model is allowed", async () => {
  const up = fakeUpstream();
  const res = await handle(req({ ...ok, model: "some-other-model" }), env, up.fn);
  assert.equal(res.status, 400);
  assert.equal(up.calls.length, 0);
});

test("caps max_tokens", async () => {
  const up = fakeUpstream();
  await handle(req({ ...ok, max_tokens: 100000 }), env, up.fn);
  assert.equal(up.calls[0].body.max_tokens, MAX_TOKENS);
});

test("bad JSON and oversized bodies are rejected", async () => {
  const up = fakeUpstream();
  assert.equal((await handle(req("{not json"), env, up.fn)).status, 400);
  const big = "x".repeat(MAX_BODY_BYTES + 1);
  assert.equal((await handle(req({ ...ok, messages: [{ role: "user", content: big }] }), env, up.fn)).status, 413);
  assert.equal(up.calls.length, 0);
});

test("passes Anthropic errors back with their status", async () => {
  const failing = async () => new Response(JSON.stringify({ type: "error" }), { status: 429, headers: { "content-type": "application/json" } });
  const res = await handle(req(ok), env, failing);
  assert.equal(res.status, 429);
});

test("network failure upstream becomes 502", async () => {
  const res = await handle(req(ok), env, async () => { throw new Error("down"); });
  assert.equal(res.status, 502);
});
