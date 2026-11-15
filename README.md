# 7xAI

7xAI is an Android-first AI client designed to run a locally hosted OmniRoute gateway inside a standalone ARM64 APK.

## Project status

Early architecture and feasibility planning. No production APK or working embedded OmniRoute runtime is available yet.

## Direction

- One Android APK; no Termux installation, PC, or VPS required for the local gateway.
- Kotlin/Jetpack Compose application with an embedded Android Node.js runtime.
- OmniRoute headless server hosted locally on loopback.
- External AI providers perform model inference; this app does not bundle model weights.
- Security, Android lifecycle behavior, and real-device compatibility are release gates.

Implementation is isolated to the `feat/android-embedded-omniroute` branch. The default branch is kept as a clean project entry point until a reviewed implementation is ready.

## Important

The embedded Node.js runtime and OmniRoute server are experimental on Android. Node 24.22.3 LTS is the production security baseline; older mobile runtime binaries may only be used for throwaway feasibility tests and must not ship.
