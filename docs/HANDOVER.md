# Session handover — Messenger Pro

## Repository state
- Repository: `SikoroApps/Messenger-Pro` (public), branch `main`.
- Verified HEAD: `cda3bc096d9aa1a87f5b03f7a259539b21029348` — `fix: place SMS OTP extractor in the package directory`.
- `.github/workflows/android-release.yml` remains untracked local Batch 7 scaffolding and must not be added accidentally.
- No local Android SDK or Gradle builds were run. All compile/test evidence comes from GitHub Actions.

## Work completed
- Batch 1 foundation COMPLETE (CI runs 12 and 13).
- Batch 2 client boundary implemented and CI-tested through run 19, then a server-authoritative reference core added in `android/backend/` (commits `c7a528c`, `2a76f5b`, `eb9d991`).
- Metadata-only SMS delivery receipt core added and verified in run 30 (`d295336`); loopback HTTP adapter, scoped idempotency, client error mapping and host allowlist added and verified in run 32 (`6c25411`); SMS OTP message extractor added and verified in run 35 (`cda3bc0`).
- CI baseline repaired after the TLS/HTTP transport commit: `f43667f`, `2a3e7f6`, `b06de54`, `01db464`, `2552566`.
- Shared domain primitives for later batches (privacy, messaging, media, account controls) implemented with JVM tests.
- Contract/evidence docs: `docs/API.md`, `docs/DATABASE.md`, `docs/TESTING.md`, `android/backend/README.md`.
- Batch records in `docs/BUILD_ORDER.md` and `docs/PROGRESS.md` updated honestly; no batch marked complete.

## Tests and CI evidence
| Run | Commit | Result | Notes |
| --- | --- | --- | --- |
| 29 / `44246930798` | `eb9d991` | success | 37 app JVM tests, 17 backend JVM tests, 8 emulator tests, lint, debug APK |
| 30 / `44249004280` | `d295336` | success | 37 app JVM tests, 22 backend JVM tests, 8 emulator tests, lint, debug APK |
| 32 / `44250745206` | `6c25411` | success | 40 app JVM tests, 28 backend JVM tests, 9 emulator tests, lint, debug APK |
| 35 / `44252590103` | `cda3bc0` | success | 44 app JVM tests, 28 backend JVM tests, 9 emulator tests, lint, debug APK |

Latest evidence is run 35 on the exact HEAD. Artifacts are published under that run as `messenger-pro-backend-test-results`, `messenger-pro-unit-test-results`, `messenger-pro-debug`, `messenger-pro-lint` and `messenger-pro-device-test-results`.
Latest debug APK SHA-256: `d2b25cc6dd12acbc9b47e4d441049077bf00519b1a2d9a27f2db1c813398fbf8`.

## Exact blockers
1. No Supabase project or credentials → no deployed schema/RLS/Edge Functions; server-authorised behaviour cannot be verified at runtime.
2. No SMS provider account/funding; eSMS Africa `.io` vs `.com` identity unresolved → real `+268` delivery and end-to-end delivered-code tests remain BLOCKED.
3. Original 600-feature catalogue unavailable → reconstruction stays labelled and awaiting review.
4. Device-only behaviours need a physical device or documented emulator with the required capability.
5. Release signing material, protected secrets and Play Console access require explicit authorisation.

## Next specific task
Port the reference-core contracts and loopback HTTP adapter into hosted Edge Functions with durable repositories, TLS, provider credentials and a real `+268` delivered-code test, then add server-authorisation negative tests. Do not mark any feature `COMPLETE` or any batch closed until the exit gates in `docs/BUILD_ORDER.md` are evidenced.
