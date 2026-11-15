# OmniRoute Android Compatibility Audit

**Audited:** 2026-11-15  
**Stable source pin:** `cheaper-inference/OmniRoute` tag `v3.8.53`, commit `191dce8d534ceb56c28a08e71381284a9a87838f`.  
**Unreleased development line observed:** `release/v3.8.54`, commit `23b8c4209eaac95b41b6c8923951405bccb66d3e`. Do not treat that branch as a published release.

This is a source-level audit, not a successful Android port. Every item marked as a compatibility gate still requires a native runtime test.

## 1. Server entrypoint

The normal OmniRoute server runner is `scripts/dev/run-next.mjs`. It imports Next.js programmatically, constructs the custom HTTP server, installs the WebSocket upgrade bridges and starts listening in the same Node process. Its mode is `start` when argv[2] is `start`; otherwise it selects development mode.

The separate `scripts/dev/run-standalone.mjs` launches Next's standalone `server-ws.mjs` / `server.js` using `child_process.spawn`. The Node mobile FAQ explicitly warns that `child_process.spawn()` and `fork()` are not supported as on desktop. Therefore, the Android gateway must not use the standalone launcher unchanged.

**Candidate runtime command:** invoke `scripts/dev/run-next.mjs` with argv `["node", "scripts/dev/run-next.mjs", "start"]` directly through embedded Node's `node::Start()`. This is a candidate, not yet a proven Android start path. The Next production build must already exist, and all runtime imports must be verified under the mobile library.

Required environment before Node starts:

- `NODE_ENV=production`
- `HOST=127.0.0.1` (the runner defaults to `0.0.0.0`; never accept that default on the phone)
- `PORT=20128`
- `HOME=<app-private persistent config directory>`
- `TMPDIR=<app cache directory>` (Android apps do not have a normal `/tmp`)
- `DATA_DIR=<app-private no-backup data directory>`
- `NODE_COMPILE_CACHE=<app cache directory>/node-compile-cache`
- set the working directory to the extracted mobile bundle root before Node initializes

The server process must confirm a loopback bind and authenticated readiness before the UI reports Running.

## 2. Backend-only build

OmniRoute's `scripts/build/backendOnlyPages.mjs` supports `OMNIROUTE_BUILD_BACKEND_ONLY=1`. It stubs App Router UI files (page/layout/template/loading/error/not-found/default) while retaining API `route.ts` handlers, middleware and metadata routes. Its purpose is explicitly documented for embedders that consume `/api/*`, `/v1/*` and `/v1beta/*` without needing the dashboard.

7xAI should use this mode to build a compact backend-only server and supply its own native Compose UI. The build helper restores changed source files in a `finally` path; CI must verify the clean checkout remains unmodified after the build, including on failure or cancellation.

Do not copy the full Electron distribution or the complete dashboard static assets into the APK. Assemble only the server output, runtime dependencies, required public assets, migrations and license notices that are proven necessary.

## 3. SQLite

The published SQLite runtime documentation describes this fallback order:

1. bundled `better-sqlite3`;
2. runtime-installed `better-sqlite3`;
3. Node's built-in `node:sqlite` (Node >=22.5);
4. WASM-backed `sql.js`.

The lazy runtime installer invokes `execSync("npm install ... better-sqlite3")` before trying `node:sqlite`. Mobile Node documents process-spawning restrictions. Add an explicit mobile branch (for example, `process.versions.mobile`) that skips all npm/child-process installation and goes directly to `node:sqlite`, then `sql.js` if needed. Never attempt to install native modules at runtime on the handset.

The full Node mobile package includes `node:sqlite`; its lite build omits SQLite/Web Storage. Therefore, the POC and production target must use the full feature profile unless the selected SQLite backend is demonstrably unnecessary.

Test migrations, read/write transactions, data persistence, close/reopen, low storage, interrupted writes and app process death. OmniRoute documents its default SQLite topology as single-writer; only one gateway instance may own the app database.

## 4. Native and desktop-only dependencies

The upstream package includes optional or platform-sensitive packages such as `better-sqlite3`, `keytar`, `onnxruntime-node`, `sqlite-vec`, and `wreq-js`, and the standalone assembler copies native assets for several desktop/server platforms. The mobile packaging step must not copy Linux, Windows or macOS `.node` binaries or shared libraries into an Android APK.

Build an explicit Android runtime allowlist and prove that optional integrations are either:
- supported by an Android ABI-compatible N-API build and tested on device; or
- disabled with a clear unsupported status; or
- replaced by a supported Node built-in / pure-JavaScript implementation.

In particular:
- Do not include host-built `better-sqlite3`; prefer `node:sqlite` then `sql.js`.
- Exclude Electron, desktop keychain, system-tray, TPROXY and desktop executable helpers.
- Do not include ONNX/model-inference assets; inference is remote through configured providers.
- Audit `wreq-js` before deciding whether it is optional for core provider routing. If it is required, either build a matching Android N-API binary or provide a tested standard-fetch/Undici fallback. Do not silently remove a transport that routing relies on.
- Check Next/SWC/sharp/image paths for accidental native binding loads even when dashboard pages are stubbed.

## 5. Start/stop semantics

The mobile Node library documents one Node instance per process and a one-shot `node::Start()` entrypoint. Android must run the gateway in a dedicated `:gateway` service process. Stop must first request graceful server and DB shutdown, then terminate that gateway process; restart starts a fresh process. Do not call `node::Start()` again in the same process.

The Node mobile FAQ also notes that spawning child processes is not supported like desktop. Every runtime feature and transitive dependency that invokes `child_process` needs a test or explicit disablement. Build-time tools can use child processes on Linux CI; handset runtime code cannot assume them.

## 6. Security and network

- Bind only to IPv4 loopback `127.0.0.1`; test that the listener is not reachable from LAN interfaces.
- Require a random local API credential even on loopback; do not rely on the phone being single-user.
- Never put provider credentials in query parameters, JS bundles, assets, logs, crash reports or Android backups.
- Keep provider credentials in an encrypted store whose key is protected by Android Keystore; exclude device-bound secret material from backup.
- Ensure server shutdown flushes persistent state and rejects new work before process exit.
- Redact authorization headers, API keys, OAuth codes, prompts and request bodies from exported diagnostics.

## 7. Source and runtime pins

- OmniRoute stable tag: `v3.8.53` → commit `191dce8d534ceb56c28a08e71381284a9a87838f`.
- OmniRoute development branch `release/v3.8.54` is not the current stable tag; reconsider only after it is published and audited.
- Production Node baseline: `v24.22.3` LTS security release.
- Available Android mobile POC candidate: `fogtape/nodejs-mobile` `v24.22.1-0`, full archive SHA-256 `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250`. This is behind the production security baseline and must not ship.
- The POC full Android archive is larger than 100 MB compressed because it contains multiple ABIs and headers; the final ARM64 APK size must be measured after extracting only the required runtime library. Do not use old 35–65 MB estimates.

## References

- OmniRoute stable release: https://github.com/cheaper-inference/OmniRoute/releases/tag/v3.8.53
- OmniRoute source: https://github.com/cheaper-inference/OmniRoute/tree/v3.8.53
- OmniRoute SQLite runtime notes: https://github.com/cheaper-inference/OmniRoute/blob/v3.8.53/docs/ops/SQLITE_RUNTIME.md
- Node 24.22.3 security release: https://nodejs.org/en/blog/release/v24.22.3/
- Android Node mobile recipe: https://github.com/fogtape/nodejs-mobile/tree/recipe
- Android runtime embedding notes: https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/EMBEDDING.md
- Android runtime API limitations: https://github.com/fogtape/nodejs-mobile/blob/recipe/docs/FAQ.md
- Android W^X behavior: https://developer.android.com/about/versions/10/behavior-changes-all
- Android foreground service limits: https://developer.android.com/develop/background-work/services/fgs/timeout
