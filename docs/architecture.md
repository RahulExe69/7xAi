# 7xAI Embedded OmniRoute — Architecture

**Status:** Approved design direction; implementation is gated on runtime feasibility.
**Date:** 2026-11-15
**Application ID:** com.sevenx.ai
**Initial ABI:** arm64-v8a
**Initial distribution:** privately sideloaded APK. Play distribution is a separate policy review.

## Product intent

7xAI is a standalone Android application that hosts an OmniRoute gateway on the same phone. It must not require a PC, VPS, Termux installation, shell commands, or user-managed runtime downloads. The phone hosts the gateway and user interface; model inference remains with configured external AI providers. Internet access and valid provider credentials are still required.

## Selected architecture

1. Kotlin + Jetpack Compose owns the user interface.
2. A dedicated Android service process named :gateway hosts the embedded Node runtime, isolated from the UI process.
3. Android loads Node native shared libraries from the APK's lib/arm64-v8a section. Do not download an executable ELF to writable app storage and attempt to run it.
4. JNI invokes Node's node::Start() exactly once in the gateway process. It is a one-shot blocking entry point: stop means graceful server shutdown and gateway-process exit; restart means creating a fresh service process.
5. The OmniRoute JavaScript/server bundle and mutable data are extracted to app-private storage. No broad storage permission is required.
6. OmniRoute binds to 127.0.0.1 only. Local requests still require a random gateway credential.
7. Provider credentials are encrypted using a data-encryption key protected by Android Keystore. Secrets, prompts and authorization headers are redacted from logs.
8. The UI uses the authenticated OpenAI-compatible API for model listing and streamed chat. OmniRoute's management UI may be embedded only after its routes and OAuth callbacks are audited.

## Node runtime policy

- Production security baseline: Node.js v24.22.3 LTS, released 2026-11-11 as a security release.
- OmniRoute's current supported engines include Node 24.x (>=24.0.0 <27).
- Throwaway proof-of-concept only: fogtape/nodejs-mobile v24.22.1-0 full Android archive, SHA-256 d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250. It is behind the production security baseline and must never ship. Use full, not lite: the lite profile omits node:sqlite.
- Production requires a reviewed Android/mobile patch stack rebased to Node v24.22.3, with patch hashes and native regression evidence. If that build cannot be produced, the release is blocked; do not silently ship the older POC runtime or downgrade to Node 18.
- fogtape/nodejs-mobile is an external mobile build recipe, not an official Node.js Android support guarantee. Audit its patches and licenses before adopting the artifact.

## OmniRoute packaging policy

The Android target is the headless server, not Electron. Audit the actual upstream production dependency graph and standalone server output. Include only required server/runtime files. Treat SQLite drivers and every native addon as explicit compatibility gates; test the selected SQLite backend, migrations, persistence and restart. Exclude Electron, desktop keychain/system-tray integrations, and optional local ONNX inference assets unless a demonstrated core server dependency requires otherwise.

Pin the upstream OmniRoute tag/commit, Node engine range, package lock, bundle hash and complete license/notice inventory before each build. v3.8.54 was inspected as a candidate only; re-check the selected upstream release immediately before implementation.

## Lifecycle and Android policy

The gateway starts only after an explicit user action in the first alpha. While active it displays a persistent notification with Open and Stop actions. Android/OEM battery management may still kill processes; 7xAI must detect a dead gateway and never show a false Running state.

Do not use dataSync as a generic permanent-server label: Android 15 applies a six-hour-per-24-hour timeout to that FGS type. Investigate specialUse, its required subtype property, Android 14+ permissions and Play policy. If the intended distribution channel cannot justify a long-running service type, fall back to visible-session-only gateway mode. Boot auto-start is out of scope for the first alpha.

## Release gates

- Node 24 embedding smoke test passes in a minimal APK.
- Production mobile Node runtime is rebased to Node 24.22.3 and passes version, ABI, crypto, TLS, DNS, HTTP and process-lifecycle tests.
- OmniRoute starts headlessly, persists SQLite state, returns authenticated GET /v1/models, and completes streamed mock-provider chat.
- Start → graceful stop → fresh-process restart is reliable; no second node::Start() call occurs within one process.
- Loopback binding and authentication are verified; no secret leakage or backup exposure.
- CI produces a reproducible ARM64 APK with runtime hashes and license notices.
- Real-device tests replace all RAM, APK-size, storage and battery estimates.

## Provisional device budgets (not measured)

Android 10/API 29 minimum target; ARM64 only; 4 GB RAM minimum test candidate and 6–8 GB recommended. Reserve at least 300 MB free storage initially. APK planning envelope: 60–120 MB, 130 MB provisional alpha ceiling. Measure actual APK, expanded data, idle/active RAM and screen-off power before making compatibility claims.

## References

- OmniRoute Termux guide: https://github.com/cheaper-inference/OmniRoute/wiki/Termux-Guide
- OmniRoute source: https://github.com/cheaper-inference/OmniRoute
- Node 24.22.3 security release: https://nodejs.org/en/blog/release/v24.22.3/
- Node 24 Android mobile build reference: https://github.com/fogtape/nodejs-mobile/releases/tag/v24.22.1-0
- Android Node embedding reference project: https://github.com/narraeon/narraeon
- Android W^X behavior: https://developer.android.com/about/versions/10/behavior-changes-all
- Android special-use FGS: https://developer.android.com/reference/android/content/pm/ServiceInfo#FOREGROUND_SERVICE_TYPE_SPECIAL_USE
- Android 15 FGS timeout: https://developer.android.com/develop/background-work/services/fgs/timeout
