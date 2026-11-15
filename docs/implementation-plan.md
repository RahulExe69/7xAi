# 7xAI Embedded OmniRoute — Implementation Plan

> Approved execution direction: native, stage-gated implementation. Keep work on feat/android-embedded-omniroute; do not merge into main until a reviewed PR and every release gate passes.

## Global constraints

- Repository: RahulExe69/7xAi; it was empty when this project began. The only main-branch commit is a README bootstrap.
- Minimum Android target: API 29; ABI: arm64-v8a.
- Production Node baseline: v24.22.3 LTS. Node 24.20.0-0 mobile binary is POC-only.
- OmniRoute's accepted Node range includes Node 24; pin exact upstream tag, commit, lockfile and package integrity before packaging.
- Native libraries must be packaged in APK ABI libraries; never execute downloaded ELF files from writable app storage.
- The Node runtime lives in a dedicated :gateway process. node::Start() is one-shot; stop gracefully and exit the process, then restart in a new process.
- Local gateway binds to 127.0.0.1, requires authentication, and stores provider credentials using Keystore-backed encryption.
- Do not mislabel a permanent server as dataSync. Assess specialUse and distribution-policy requirements; keep a visible-session fallback.
- No POC artifact, mock test, emulator-only pass or stale runtime may be described as a production-ready release.

## Environment note

The current authoring environment has no Android SDK/NDK, Gradle installation, emulator or connected Android handset. Android builds should run in GitHub Actions on pinned runners. Real-device checks remain a separate release gate and cannot be claimed from CI alone.

## Phase 0 — Repository and project foundation

1. Keep the main branch as a clean README-only entry point.
2. Create the Android app skeleton on this feature branch: Gradle wrapper, version catalog, Kotlin/Compose app, manifest, app ID com.sevenx.ai, install smoke test.
3. Set up CI with Android SDK/NDK and ARM64 emulator tests.
4. Verify clean build and install before native runtime integration.

Gate: a clean CI runner builds and installs a minimal 7xAI APK.

## Phase 1 — Node Android runtime proof-of-concept (highest risk)

1. Add a versioned runtime manifest containing artifact class (poc-only or production-approved), Node version, ABI, source repository/tag/commit, NDK version, patch-stack revision and SHA-256.
2. Add tests rejecting an unapproved patch level, wrong hash, wrong ABI or POC artifact in a production build.
3. For feasibility only, use the pinned digidem/nodejs-mobile v24.20.0-0 ARM64 library; verify its archive hash before extraction.
4. Load libnode.so through Android's native library loader. Start node::Start() exactly once inside GatewayProcessService declared with android:process=":gateway".
5. Test core runtime behavior: version, fs, crypto, fetch, TLS, DNS, loopback HTTP and native library loading.
6. Gracefully close the HTTP listener and durable file/database handles, exit the gateway process, then test a fresh service process restart.
7. Rebase/rebuild the Android mobile patch stack to Node v24.22.3. Review every patch and rerun the native test subset. Never ship the older POC binary.

Gate: real ARM64 Android device passes the POC lifecycle tests AND a production-approved Node 24.22.3 runtime is built and regression-tested. If production Node 24.22.3 cannot be produced, stop release work and reassess.

## Phase 2 — OmniRoute source and dependency audit

1. Pin the exact OmniRoute release, source commit, Node engines, package lock and integrity hash.
2. Audit production server dependencies, build output, native addons, Electron imports, desktop keychain dependencies, child_process, watcher APIs and optional ONNX components.
3. Determine whether Node's built-in SQLite is sufficient or an Android-built better-sqlite3 addon is needed. Test migrations, read/write, clean close and restart.
4. Produce a deterministic minimal headless server bundle and full transitive license/NOTICE inventory.
5. Verify the real server under embedded Node: start, health, authenticated GET /v1/models, mock-provider chat and streaming SSE.

Gate: no unreviewed native dependency, missing runtime file or unsupported server feature.

## Phase 3 — Android gateway process and service lifecycle

1. Main UI process controls GatewayProcessService using explicit Binder/Messenger IPC.
2. Gateway process starts Node once, reports lifecycle state and server readiness to the UI process.
3. Implement an authenticated shutdown path that flushes storage and terminates Node cleanly; process death is required before restart.
4. Implement state reconciliation after process death, start timeout, port conflict, DB-lock/corruption simulation and notification Stop.
5. Validate service category, Android 14+ requirements and Android 15 timeout behavior. Never show Running unless authenticated health check and /v1/models pass.

Gate: repeat start/stop across new gateway processes with no leaked sockets, duplicate instances or corrupted data.

## Phase 4 — Security and secrets

1. Generate a random local gateway credential.
2. Store provider credentials with a random data-encryption key protected by Android Keystore; exclude secrets from Android backup.
3. Require auth on loopback for every app-owned API call.
4. Redact API keys, authorization headers, OAuth codes and prompts from logs/diagnostics.
5. Disable WebView file/universal access and persistent WebView storage for secrets; allow only the local gateway origin.
6. Verify non-loopback binding is rejected and exported Android components are minimized.

Gate: negative tests prove unauthenticated calls fail and secret material never appears in app files/logs/backups.

## Phase 5 — 7xAI product UI

1. Home/dashboard: server lifecycle, model readiness, current model and error state.
2. Chat: conversation history, model selector, streaming replies, cancel, retry and recoverable errors.
3. Provider management: use supported OmniRoute management routes; restrict local WebView or build native setup only from documented APIs.
4. Diagnostics: pinned component versions, database status, bind address, bundle hash and redacted logs.
5. Ensure the UI process remains responsive when the gateway process crashes or restarts.

Gate: automated mock-provider chat test streams, cancels, retries, switches models and handles server shutdown truthfully.

## Phase 6 — Reproducible CI and release pipeline

1. Build Node Android runtime from pinned source and patches; verify source and artifact hashes.
2. Build the OmniRoute server bundle from a clean lockfile with reproducible output and license inventory.
3. Assemble unsigned PR APKs; sign release APKs only with protected CI secrets.
4. Run Gradle unit/instrumentation tests, runtime tests, dependency audit, secret scan and APK analyzer.
5. Upload APK, SHA-256, SBOM/license notices and component manifest as release artifacts.

Gate: a clean CI run produces the same versioned component manifest and a verified installable ARM64 APK.

## Phase 7 — Real-device alpha validation

Test Android 10, 13, 14 and 15+ where available, including one real OEM handset. Cover first install, start/stop, screen-off, notification Stop, app process death, Android low-memory pressure, offline/online recovery, provider timeout, database recovery and reboot behavior. Measure APK size, installed size, extracted server data, idle/active RAM, CPU, thermal behavior and battery draw. Replace all provisional estimates with measured results.

Alpha release is blocked until: Node 24.22.3 Android runtime is production-approved; OmniRoute core APIs and persistence pass; local auth and secret protection pass; lifecycle/FGS policy is defensible; device tests pass; CI emits a signed APK with hash and notices.

## Review focus

- Node ABI/native addon compatibility, especially SQLite.
- One-shot node::Start() lifecycle and new-process restart.
- Foreground-service eligibility and Android 15 time limits.
- Loopback exposure and credentials stored on device.
- OEM process kills, network interruptions and streaming cancellation.

## Decisions recorded

- Use native Android + embedded Node, not Termux or a remote VPS.
- Use a dedicated :gateway process; never re-enter Node's one-shot start routine.
- Treat Node 24.20.0-0 as a throwaway POC; Node 24.22.3 is the production baseline.
- No merging to main or claiming release readiness before all gates pass.
