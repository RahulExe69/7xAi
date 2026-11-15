# 7xAI Embedded OmniRoute — Implementation Plan

> **For agentic workers:** Execute task-by-task with TDD and a fresh review gate after every independently testable milestone. Work only on `feat/android-embedded-omniroute`; do not merge to `main` until a reviewed PR and all alpha gates pass.

**Goal:** Build a standalone ARM64 Android APK that hosts an authenticated, loopback-only OmniRoute server inside an embedded Node.js runtime, with no PC, VPS or Termux requirement.

**Architecture:** Kotlin/Compose UI + dedicated Android `:gateway` service process + JNI-embedded Node.js + backend-only OmniRoute bundle. The server runs inside the Node process using an in-process entrypoint; external providers perform model inference.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle, Android NDK/CMake, JNI/C++, Node.js mobile build recipe, OmniRoute Next.js backend-only build, GitHub Actions.

**Spec:** `docs/architecture.md`; source-level constraints in `docs/omniroute-mobile-audit.md`.

## Global constraints

- Repository `RahulExe69/7xAi`; work on `feat/android-embedded-omniroute`; no changes to `main`.
- Android minimum API 29; production ABI `arm64-v8a`; x86_64 is permitted only for emulator test builds.
- Production Node baseline `v24.22.3` LTS; `fogtape/nodejs-mobile v24.22.1-0` full archive is POC-only.
- OmniRoute stable tag `v3.8.53`, commit `191dce8d534ceb56c28a08e71381284a9a87838f`.
- Backend-only build uses `OMNIROUTE_BUILD_BACKEND_ONLY=1`; server runner for mobile is an in-process entrypoint, not the child-process standalone launcher.
- Never execute a downloaded ELF from writable app storage; native libraries must be inside the APK ABI library area.
- Node `node::Start()` is one-shot and runs in a dedicated `:gateway` process; stop, exit process, then restart fresh process.
- Runtime must set `HOST=127.0.0.1`, `PORT=20128`, app-private `HOME`, Android `TMPDIR`, `DATA_DIR`, and compile cache before starting Node.
- Mobile runtime must not assume `child_process.spawn/fork/execSync`, runtime npm installs, desktop keychain, desktop TPROXY or host-built native addons.
- SQLite fallback is `node:sqlite`, then `sql.js`; skip runtime `npm install better-sqlite3` on mobile.
- API key and local gateway credential handling must be authenticated, encrypted and redacted; no secrets in assets, logs, crash reports or backups.
- Do not use Android `dataSync` as a perpetual service category; validate `specialUse` / distribution rules and keep a visible-session fallback.
- Never call the 24.22.1 POC runtime production-ready; no release APK without production Node 24.22.3 and real-device evidence.

## Environment / CI

The authoring environment has no Android SDK/NDK, Gradle, emulator or connected phone. Build verification must run in GitHub Actions. Emulator tests are useful but cannot replace ARM64 hardware verification. The first UI scaffold's Gradle assembly and unit-test tasks passed in CI; emulator smoke test completion is a separate check.

## Task 0 — Project foundation

**Files:** Android Gradle project, `app/src/main`, `.github/workflows/android-ci.yml`.

- [x] Add stable Android API 36 build configuration and pinned plugins/dependencies.
- [x] Add the 7xAI Compose home/status scaffold.
- [x] Add instrumentation test that launches the main activity and asserts the status label.
- [x] Add CI for debug APK assembly, unit tests and emulator UI smoke tests.
- [ ] Confirm latest CI run has completed and preserve the build artifact as UI-only until runtime integration.

**Gate:** clean CI build and instrumentation smoke test pass. No native runtime is implied by this task.

## Task 1 — Android Node runtime proof-of-concept

**Files:** `runtime/manifest.json`, `scripts/fetch-libnode.sh`, `gateway-runtime/src/main/cpp/CMakeLists.txt`, `native-lib.cpp`, `NativeNode.kt`, POC-only Android service/test module, CI runtime workflow.

1. Add a runtime manifest schema: artifact class (`poc-only` or `production-approved`), Node version, source repo/tag/commit, ABI, archive SHA-256, NDK version, API level, patch recipe commit and binary SHA-256.
2. Add a unit test that rejects missing hash, wrong ABI, mismatched Node version, unknown artifact class and any POC artifact in release variants.
3. Fetch `fogtape/nodejs-mobile v24.22.1-0` full archive over HTTPS; verify SHA-256 `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250`. Extract only required ABI library and headers; never commit native binaries.
4. Implement the JNI bridge and load `libnode.so` from APK native libraries. Do not use downloaded executables.
5. Start Node only once in a dedicated `:gateway` process. Set environment and working directory before Node initialization.
6. Test `process.version`, mobile marker, `fs`, `crypto`, `fetch`, TLS, DNS, loopback HTTP, environment access, temp directory and graceful listener shutdown.
7. Test gateway process death and fresh-process restart. Assert the same process never re-enters `node::Start()`.
8. Rebuild the mobile patch recipe against Node `v24.22.3`; review all patch changes, run upstream curated Android tests and produce a runtime provenance manifest. Keep the 24.22.1 artifact POC-only.

**Gate:** real ARM64 Android device passes smoke and lifecycle tests, and a separately built, reviewed Node 24.22.3 Android runtime passes native regression checks. Failure to produce the production runtime blocks alpha release.

## Task 2 — OmniRoute server bundle and mobile compatibility

**Files:** `scripts/build-omniroute-mobile.sh`, `scripts/patches/omniroute-mobile/`, `runtime/omniroute-manifest.json`, `gateway-runtime/src/main/assets` packaging.

1. Clone stable OmniRoute tag `v3.8.53` at exact commit `191dce8d534ceb56c28a08e71381284a9a87838f`; verify source identity and lockfile before installing dependencies.
2. Run the backend-only production build with `OMNIROUTE_BUILD_BACKEND_ONLY=1`. Assert all changed App Router pages are restored and `git status --porcelain` is clean after build, even when the build fails.
3. Inspect the resulting server bundle, static assets, API route files, migrations, package imports and all native or dynamically imported modules.
4. Add a mobile SQLite fallback patch: when `process.versions.mobile` is present, do not attempt runtime npm install via `child_process.execSync`; load `node:sqlite`, then `sql.js`.
5. Audit `better-sqlite3`, `keytar`, `onnxruntime-node`, `sqlite-vec`, `wreq-js`, `sharp`, TPROXY, SWC and desktop helpers. Build an explicit mobile allowlist. If `wreq-js` is required by routing, build a compatible Android N-API addon or provide a tested fetch/Undici fallback.
6. The Android runtime must not invoke `scripts/dev/run-standalone.mjs` unchanged because it spawns a child process. Test candidate argv `["node", "scripts/dev/run-next.mjs", "start"]` directly through `node::Start()`.
7. Package only verified server bundle/runtime files, migrations and all required third-party license notices.

**Gate:** actual OmniRoute production bundle boots under embedded Node on Android, authenticated `GET /v1/models` works, DB initializes and persists, and mock-provider SSE chat completes without unsupported child-process/native imports.

## Task 3 — Android gateway service and process lifecycle

**Files:** `GatewayProcessService.kt`, Binder/Messenger IPC, notification actions, process-state repository, service tests.

1. Main UI process controls the isolated gateway process using explicit Binder or Messenger IPC.
2. Report states `Stopped`, `Starting`, `Ready`, `Stopping`, `Failed`; derive `Ready` only from authenticated health and `/v1/models` probe.
3. Implement graceful shutdown of server, DB, worker/timer resources and SSE sessions before gateway process termination.
4. Restart by terminating the dedicated process and creating a new process. Do not call Node start twice.
5. Test repeated start/stop, concurrent start requests, port collision, timeout, binder disconnect, low-memory kill, stale DB lock and interrupted shutdown.
6. Investigate Android 14+ foreground service permission/type and Android 15 timeout behavior. Evaluate `specialUse` only if the use case and distribution policy permit it. Keep a foreground-session-only fallback.

**Gate:** lifecycle tests prove no duplicate server, leaked socket or corrupted database. UI never shows Running on stale state.

## Task 4 — Security and credential storage

**Files:** Keystore key manager, encrypted credential store, gateway auth provider, backup rules, redacted logger, security tests.

1. Generate a random local gateway credential; never use a fixed token.
2. Encrypt provider keys and local secrets using an app data key protected by Android Keystore.
3. Keep the database/config under app-private no-backup storage; provide explicit encrypted export only if later needed.
4. Require auth on app-to-gateway requests even though the gateway binds to loopback.
5. Redact Authorization headers, API keys, OAuth codes, prompts and bodies from logs/diagnostics.
6. Ensure Android components are non-exported unless they are the launcher or a platform-required receiver; restrict WebView origin and file access if a WebView is later used.
7. Verify gateway is unreachable from LAN and unauthenticated localhost requests fail.

**Gate:** security negative tests pass; no secrets are present in backup, logcat, crash bundle, assets or Git history.

## Task 5 — 7xAI product interface

**Files:** Compose dashboard, chat view-model, provider config screens, model picker, conversation store, diagnostics.

1. Replace the placeholder status card with real gateway lifecycle and health.
2. Add API-key/provider configuration using secure storage.
3. Implement model listing, model selector, chat history, SSE streaming, cancel, retry and network interruption recovery.
4. Add provider usage/status and clear handling for rate limits/quota.
5. Add diagnostics showing pinned component versions, active SQLite driver, bind address and redacted logs.
6. Do not ship the OmniRoute dashboard; use the native UI against supported APIs.

**Gate:** mock provider tests cover list, selection, stream, cancellation, retries, offline errors and gateway stop during chat.

## Task 6 — Reproducible CI and release workflow

**Files:** GitHub Actions runtime build, OmniRoute bundle build, artifact assembler, SBOM/license output, release job.

1. Build the mobile Node runtime from pinned source/patches on Linux with pinned NDK and Python toolchain.
2. Verify source tree hash, patch series, NDK version, ABI, ELF page alignment, binary hash and notice inventory.
3. Build OmniRoute backend-only bundle from exact tag/lockfile, apply only reviewed mobile patches and verify clean source tree afterwards.
4. Assemble debug artifacts for PRs; sign release APKs only using protected signing secrets.
5. Run unit, instrumentation, runtime, native, bundle, security, dependency, license, secret-scan and APK-size checks.
6. Publish APK, SHA-256, component manifest, SBOM and notices.

**Gate:** clean CI produces a reproducible ARM64 release candidate and full provenance record.

## Task 7 — Real-device alpha validation

Test Android 10, 13, 14, 15+ and at least one physical ARM64 OEM handset. Cover install/update, first runtime extraction, service start/stop, screen off, notification Stop, app process kill, system low-memory pressure, reboot, offline/online recovery, long SSE streaming, provider timeout, DB recovery and storage pressure.

Measure real APK size, expanded app data, runtime extraction size, idle/active RAM, CPU, thermal behavior and battery impact during 15/30/60-minute runs. Replace all estimates with measured figures.

**Alpha release is blocked until:** Node 24.22.3 production runtime is approved; OmniRoute server bundle and SQLite pass; Android lifecycle/FGS policy is defensible; security tests pass; real device tests pass; signed APK and provenance artifacts are verified.

## Review focus

- Node ABI / native addon compatibility, including Android 16 KB page size.
- One-shot Node process lifecycle and clean server/database stop.
- Android background restrictions, foreground service category and OEM kills.
- Mobile SQLite fallback bypassing runtime package installation.
- Required transports / dynamic optional imports and WebSocket/SSE support.
- Loopback exposure, local authentication, provider key persistence and backup behavior.
- Runtime binary provenance, exact upstream pin and all third-party notices.
