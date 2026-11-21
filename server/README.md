# MyCalories key server

A tiny Cloudflare Worker that keeps your Anthropic API key off the phone. The app sends its
requests here with a separate **app token**; the server checks the token, allows only the
Claude endpoint and model the app uses, caps response length, and adds your real key.

## Test

```bash
npm test
```

## Deploy (Cloudflare Workers, free plan is enough to start)

```bash
npx wrangler login
npx wrangler secret put ANTHROPIC_API_KEY   # your sk-ant-... key
npx wrangler secret put APP_TOKEN           # any long random string, e.g. from: openssl rand -hex 24
npx wrangler deploy
```

`wrangler deploy` prints your server address, e.g. `https://mycalories-key-server.<you>.workers.dev`.

## Point the app at it

In `local.properties` (not committed):

```
CLAUDE_PROXY_URL=https://mycalories-key-server.<you>.workers.dev
CLAUDE_PROXY_TOKEN=<the APP_TOKEN you set>
```

Remove `ANTHROPIC_API_KEY` from `local.properties` once the server works; the app uses the server
whenever `CLAUDE_PROXY_URL` is set.

## Good to know

- Anyone who extracts the app token from the app can use this server until you change the token.
  Change it with `wrangler secret put APP_TOKEN` and rebuild the app if it leaks.
- Add a rate limit in the Cloudflare dashboard (Security → WAF → Rate limiting rules) before a public release,
  and set a monthly spend limit in the Anthropic Console.
