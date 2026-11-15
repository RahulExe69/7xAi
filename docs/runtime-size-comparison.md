# Node Android runtime size comparison

**Date:** 2026-11-15  
**CI run:** https://github.com/RahulExe69/7xAi/actions/runs/43746516084  
**Artifact:** `node-android-runtime-profile-comparison`  
**Classification:** Experimental Node `v24.22.1-0` POC only; no profile is approved for distribution.

## Measured shared-library sizes

| Profile | ABI | Unstripped bytes | Stripped bytes | Notes |
|---|---|---:|---:|---|
| Full | arm64-v8a | 117,282,800 | 114,350,992 | Debug symbols removed; dynamic exports preserved |
| Lite | arm64-v8a | 66,270,352 | 66,270,352 | Release-like archive already stripped |
| Full | x86_64 | 115,709,840 | 113,683,984 | Emulator test ABI only |
| Lite | x86_64 | 68,334,888 | 68,334,888 | Emulator test ABI only |

For the ARM64 shipping ABI, lite saves **48,080,640 bytes (approximately 42.04%)** versus the stripped full runtime. The comparison used the same upstream tag/build recipe and NDK. Both profiles and both ABIs passed architecture and 16 KB PT_LOAD alignment checks.

## Common dynamic dependencies

All tested libraries declare only:
- `libm.so`
- `libdl.so`
- `liblog.so`
- `libc++_shared.so`
- `libc.so`

The APK must package the matching `libc++_shared.so` for each ABI used by that build.

## Decision

Use lite for the first embedded Node POC because the OmniRoute source audit found a `sql.js` WASM fallback. Lite omits `node:sqlite`, so this is conditional: the chosen profile survives only if a real OmniRoute database test proves schema migration, transactional writes, read-after-write, restart persistence, interrupted-write recovery, and normal single-writer operation. If `sql.js` fails those tests or is incompatible with the server's DB access layer, switch the POC to full and reassess the size trade-off.

Do not claim the final APK will be 66 MB. This is the runtime shared library alone. The final APK also contains the Kotlin/Compose app, native bridge, matching C++ shared runtime, OmniRoute backend-only server files, JavaScript dependencies/assets, migrations and license notices. Final APK and installed data size must be measured after integration.

## Verification artifacts

- Full ARM64 stripped SHA-256: `fc184ad1934c4b5da0109153a4324531e2b217e18c6f3827b84c747d393b46f4`
- Lite ARM64 SHA-256: `3b01535cafbd572d3af92c5514143b8f78a69e94a49ab5c1374f78effb235313`
- Full x86_64 stripped SHA-256: `28cd0111c9bd3954b9d9b3e1c96f3d8a0115d8078233a83d30a2dd1ccef698a0`
- Lite x86_64 SHA-256: `083dcd5a72f38dc06827c444f41acaac61fc44940f6fca5108b72f8f2d3ebc2c`
