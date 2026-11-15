# Runtime and upstream provenance

## Production Node baseline

- Node.js: `v24.22.3` LTS
- Released: 2026-11-11
- Release type: security release
- Official release notes: https://nodejs.org/en/blog/release/v24.22.3/

## Android Node mobile proof-of-concept

- Project: https://github.com/fogtape/nodejs-mobile
- Tag: `v24.22.1-0`
- Asset: `nodejs-mobile-android-24.22.1-0.zip` (full; do not use lite for OmniRoute because lite excludes `node:sqlite`)
- Archive SHA-256: `d6fd9f610fe5d81fede8f0d400c5a62723247725a19831835f15abf4f0604250`
- Class: `poc-only`
- Rule: never package this artifact in a user alpha/release APK. It predates the Node 24.22.3 production security baseline.

## OmniRoute stable source pin

- Repository: https://github.com/cheaper-inference/OmniRoute
- Stable tag: `v3.8.53`
- Commit: `191dce8d534ceb56c28a08e71381284a9a87838f`
- Release date: 2026-11-06
- Development branch `release/v3.8.54` was observed at `23b8c4209eaac95b41b6c8923951405bccb66d3e`, but is not the stable release pin. Reconsider only after a published release and renewed audit.

## Production promotion requirements

A Node runtime can be marked `production-approved` only when its manifest includes:
- exact upstream Node source tag and commit;
- exact Android mobile patch repository/tag/commit and source-tree checksum;
- reviewed patch list and patch hashes;
- NDK, Android API, Python and host build toolchain pins;
- ABI and 16 KB ELF page alignment checks;
- SHA-256 of each packaged native library;
- Node smoke/regression evidence for version, crypto, TLS, DNS, HTTP, fetch, filesystem, SQLite and process lifecycle;
- upstream mobile curated test run;
- real ARM64 Android device start/stop/restart evidence;
- full license and NOTICE inventory.

Never resolve a failed production build by silently switching to the POC artifact, to Node 18, or to an unreviewed newer patch branch. If the mobile patch stack cannot be rebased to Node 24.22.3, alpha release remains blocked.
