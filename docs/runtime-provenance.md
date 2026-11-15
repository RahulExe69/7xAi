# Runtime provenance and release rules

## Production target

- Node.js v24.22.3 LTS
- Release date: 2026-11-11
- Release type: security release
- Official release: https://nodejs.org/en/blog/release/v24.22.3/

## Throwaway Android feasibility artifact

- Project: https://github.com/fogtape/nodejs-mobile
- Tag: v24.22.1-0
- Asset: nodejs-mobile-android-24.22.1-0.zip
- SHA-256: d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250
- Class: poc-only
- Rule: do not place this artifact in a production or alpha APK. It is older than the production security baseline; use the full archive because the lite build removes node:sqlite.

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
