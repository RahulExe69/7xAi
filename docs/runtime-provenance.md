# Runtime provenance and release rules

## Production target

- Node.js v24.22.3 LTS
- Release date: 2026-11-11
- Release type: security release
- Official release: https://nodejs.org/en/blog/release/v24.22.3/

## Throwaway Android feasibility artifact

- Project: https://github.com/digidem/nodejs-mobile
- Tag: v24.20.0-0
- Asset: nodejs-mobile-android-24.20.0-0.zip
- SHA-256: f5ffbaf4f2679fa9180b0758c637c2f8fc8828300f95129badf213a028fb37bb
- Class: poc-only
- Rule: do not place this artifact in a production or alpha APK. It is older than the production security baseline.

## Production promotion requirements

A runtime can be marked production-approved only when the artifact manifest contains:
- exact Node source tag and commit,
- exact Android mobile patch repository and commit,
- reviewed patch list and hashes,
- NDK and API-level/toolchain pins,
- ABI and ELF/native-library verification,
- SHA-256 of every packaged native runtime library,
- Node core smoke/regression evidence for crypto, TLS, DNS, HTTP, fs and SQLite integration,
- real ARM64 Android device lifecycle results.

Never resolve a failed production build by silently switching to the POC artifact or Node 18. If the mobile patch stack cannot be rebased to Node 24.22.3, the alpha remains blocked.
