# 7xAI Embedded OmniRoute — Architecture

**Status:** Approved direction; implementation remains gated on the Android Node runtime and OmniRoute compatibility proofs.  
**Date:** 2026-11-15  
**Application ID:** `com.sevenx.ai`  
**Initial ABI:** `arm64-v8a` (x86_64 is allowed only for emulator test builds)  
**Initial distribution:** privately sideloaded APK. Google Play policy review is a separate gate.

## Product intent

7xAI is a standalone Android app that runs an OmniRoute gateway on the same phone. It must not require a PC, VPS, Termux installation, shell commands or user-managed runtime downloads. The phone hosts the gateway and user interface; inference remains with configured external AI providers. Internet access and valid provider credentials are required.

## Selected architecture

1. Kotlin + Jetpack Compose owns the UI.
2. A dedicated Android service process named `:gateway` hosts embedded Node, isolated from the UI process.
3. Node native shared libraries are packaged in the APK ABI library directory and loaded by Android's native loader. Never download an executable ELF to writable app storage and attempt to run it.
4. JNI calls `node::Start()` exactly once in the gateway process. Stop means graceful server/database shutdown followed by gateway-process exit; restart means a fresh service process.
5. A pinned, backend-only OmniRoute server bundle and required runtime assets are extracted into app-private storage. The dashboard UI is not shipped; 7xAI supplies its own Compose interface.
6. OmniRoute binds only to `127.0.0.1:20128`, requires a random local gateway credential and is not exposed to the LAN.
7. Provider credentials are encrypted using a random data-encryption key protected by Android Keystore. Device-bound keys are excluded from backup. Logs redact all secrets and prompt data.
8. The UI uses authenticated OpenAI-compatible APIs for model listing and streamed chat. Provider administration is exposed only through reviewed OmniRoute routes or native configuration screens.

## Runtime pins

- **Production security baseline:** Node.js `v24.22.3` LTS, released 2026-11-11 as a security release.
- **Android POC only:** `fogtape/nodejs-mobile` `v24.22.1-0` full Android archive, SHA-256 `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250`. Never ship this older build.
- Production requires a reviewed mobile patch stack rebased to Node `v24.22.3`, with hashes and native regression evidence. If unavailable, release remains blocked; do not downgrade the Node baseline.
- **OmniRoute stable pin:** tag `v3.8.53`, commit `191dce8d534ceb56c28a08e71381284a9a87838f`. The `release/v3.8.54` branch is an unreleased development line and is not the stable production pin.

## OmniRoute packaging and startup

Use `OMNIROUTE_BUILD_BACKEND_ONLY=1` to remove the large dashboard page/client graph while retaining API route handlers and middleware. Build on Linux CI using a clean pinned upstream checkout; the build helper temporarily stubs tracked pages and must leave the worktree clean even on failure/cancellation.

The normal standalone launcher `scripts/dev/run-standalone.mjs` spawns another process and is not suitable unchanged for mobile Node. The candidate in-process entrypoint is `scripts/dev/run-next.mjs start`, called directly through `node::Start()`. This must pass a proof-of-concept with the actual production bundle before it is accepted.

Set the environment before Node starts:
- `NODE_ENV=production`
- `HOST=127.0.0.1`
- `PORT=20128`
- `HOME=<app-private persistent configuration directory>`
- `TMPDIR=<Android cache directory>`
- `DATA_DIR=<app-private no-backup data directory>`
- `NODE_COMPILE_CACHE=<cache directory>/node-compile-cache`
- working directory = extracted bundle root

Node mobile does not provide a normal desktop process environment: Android has no conventional `/tmp`, no useful default writable `HOME`, and child-process spawning is not supported like desktop. Do not assume npm runtime installers, CLI child workers or desktop helpers work on the handset.

## Database and native dependencies

OmniRoute has a SQLite fallback chain ending with Node `node:sqlite` and `sql.js`. Its runtime installer currently attempts `npm install better-sqlite3` through `child_process.execSync`; add a mobile-specific branch to skip that installation and use `node:sqlite` first, with `sql.js` fallback. Use the full Node mobile profile because the lite profile removes SQLite.

Create an explicit Android module allowlist. Do not package host-built `.node` or `.so` assets for Linux/macOS/Windows. Audit `better-sqlite3`, `keytar`, `onnxruntime-node`, `sqlite-vec`, `wreq-js`, `sharp`, TPROXY and all dynamic imports. A feature is either supported by an Android ABI-compatible build and verified, disabled with a visible reason, or replaced by a tested mobile-compatible implementation. Do not remove a transport or DB component blindly.

OmniRoute documents its default SQLite database as single-writer. One gateway process owns the database at a time.

## Android service and lifecycle policy

Start only after explicit user action in the first alpha. While active, show a persistent notification with Open and Stop controls. The UI must reconcile actual process health and authenticated API readiness; never display Running because a start command was merely issued.

Node's `node::Start()` is one-shot, so process restart is required. Test graceful stop, forced stop, process death, DB recovery, port collision and low-memory conditions.

Do not use `dataSync` as a generic permanent-server foreground-service type: Android 15 applies a six-hour-per-24-hour timeout to that type. Evaluate `specialUse`, required subtype metadata, Android 14+ permissions and private sideload / Play distribution rules. Keep a visible-session-only fallback if a defensible long-running service type is unavailable. Boot auto-start is out of scope for the first alpha.

## Release gates

- Node 24 Android embedding smoke test passes on an ARM64 device.
- Production mobile Node runtime is rebased to `v24.22.3` and passes version, ABI, crypto, TLS, DNS, HTTP and lifecycle tests.
- OmniRoute's backend-only bundle boots inside embedded Node with no child-process dependency.
- Authenticated `GET /v1/models`, mock-provider chat and SSE streaming work.
- SQLite migrations and persistent reads/writes work across graceful and forced restarts.
- Loopback binding/authentication and credential storage pass negative security tests.
- CI builds a reproducible ARM64 APK with runtime hashes and license notices.
- Real-device measurements replace provisional APK size, expanded storage, RAM, thermal and battery estimates.
- Foreground-service policy is defensible for the actual distribution channel.

## Provisional device budgets (not measured)

Android 10/API29 minimum test candidate; ARM64 only for production; 4 GB RAM minimum test candidate, 6–8 GB preferred; reserve at least 300 MB free storage. APK planning envelope is **not yet reliable**: the full multi-ABI Node archive is over 100 MB compressed, and only the ARM64 native library plus server bundle will be packaged. Measure the actual single-ABI APK and extracted data before setting a user-facing size promise.

## References

- OmniRoute compatibility audit: [omniroute-mobile-audit.md](omniroute-mobile-audit.md)
- OmniRoute stable release: https://github.com/cheaper-inference/OmniRoute/releases/tag/v3.8.53
- Node 24.22.3 security release: https://nodejs.org/en/blog/release/v24.22.3/
- Fogtape Node mobile recipe: https://github.com/fogtape/nodejs-mobile/tree/recipe
- Android W^X: https://developer.android.com/about/versions/10/behavior-changes-all
- Android foreground service timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
