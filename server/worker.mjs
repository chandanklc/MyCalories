// MyCalories key server: a tiny Cloudflare Worker that adds your Anthropic API key to the app's
// requests, so no secret key ships inside the app. The app holds only APP_TOKEN, which you can
// change at any time. Only the one endpoint and model the app uses are allowed.

export const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
export const ALLOWED_MODELS = ["claude-opus-5-5"];
export const MAX_TOKENS = 4000;
// Plate photos are shrunk to 1568 px by the app, which fits well under this.
export const MAX_BODY_BYTES = 5 * 1024 * 1024;

const json = (status, message) =>
  new Response(JSON.stringify({ type: "error", error: { type: "proxy_error", message } }), {
    status,
    headers: { "content-type": "application/json" },
  });

/** Constant-time comparison so the token can't be guessed from response timing. */
function sameSecret(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export async function handle(request, env, fetchImpl = fetch) {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/v1/messages") return json(404, "Not found");

  const token = request.headers.get("x-api-key") ?? request.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!sameSecret(token, env.APP_TOKEN)) return json(401, "Bad app token");
  if (!env.ANTHROPIC_API_KEY) return json(500, "Server is missing ANTHROPIC_API_KEY");

  const text = await request.text();
  if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) return json(413, "Request too large");

  let body;
  try {
    body = JSON.parse(text);
  } catch {
    return json(400, "Body must be JSON");
  }
  if (!ALLOWED_MODELS.includes(body?.model)) return json(400, "Model not allowed");
  body.max_tokens = Math.min(Number(body.max_tokens) || MAX_TOKENS, MAX_TOKENS);

  const headers = {
    "content-type": "application/json",
    "x-api-key": env.ANTHROPIC_API_KEY,
    "anthropic-version": request.headers.get("anthropic-version") ?? "2023-06-01",
  };
  const beta = request.headers.get("anthropic-beta");
  if (beta) headers["anthropic-beta"] = beta;

  let upstream;
  try {
    upstream = await fetchImpl(ANTHROPIC_URL, { method: "POST", headers, body: JSON.stringify(body) });
  } catch {
    return json(502, "Couldn't reach Anthropic");
  }
  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
  });
}

export default {
  fetch: (request, env) => handle(request, env),
};
