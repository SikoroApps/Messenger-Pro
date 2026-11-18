# Session handover — Messenger Pro

## Repository state
- Repository: `SikoroApps/Messenger-Pro` (public), branch `main`.
- Verified HEAD: `5679a9be2ae6b9e221abe11bb6d883544e586e2c` — `test: assert manifest cleartext and SMS permission policy`.
- `.github/workflows/android-release.yml` remains untracked local Batch 7 scaffolding and must not be added accidentally.
- No local Android SDK or Gradle builds were run. All compile/test evidence comes from GitHub Actions.

## Work completed
- Batch 1 foundation COMPLETE (CI runs 12 and 13).
- Batch 2 client boundary implemented and CI-tested through run 19, then a server-authoritative reference core added in `android/backend/` (commits `c7a528c`, `2a76f5b`, `eb9d991`).
- Metadata-only SMS delivery receipt core added and verified in run 31 (`d295336`); loopback HTTP adapter, scoped idempotency, client error mapping and host allowlist added and verified in run 32 (`6c25411`); SMS OTP message extractor added and verified in run 35 (`cda3bc0`); keyed-hash auth audit and generic abuse signals added and verified in run 38 (`763b13a`, `25ba0e5`).
- Access tokens are now stripped from unrelated requests and attached only to allowlisted HTTPS hosts, including redirects; `MP-091` is `TESTED` from run 47. Runs 41–45 failed on emulator-job test/compile regressions in the new Compose and redirect tests and were resolved through `a34d48f`, `466d112`, `f450c87`, `04ee2ad`, `31917b6` and `2699b59`.
- Bounded jittered exponential backoff and an explicit transient/idempotent retry gate were added to `RetryPolicy`; `MP-097` is `TESTED` from run 50. An automated manifest assertion now covers disabled cleartext and absence of broad SMS permissions; `MP-048` and `MP-103` are `TESTED` from run 51. Runs 48–49 and the documentation-only run `44263375793` were cancelled by the branch concurrency rule; runs 50 and 51 are green on their exact HEADs.
- CI baseline repaired after the TLS/HTTP transport commit: `f43667f`, `2a3e7f6`, `b06de54`, `01db464`, `2552566`.
- Shared domain primitives for later batches (privacy, messaging, media, account controls) implemented with JVM tests.
- Contract/evidence docs: `docs/API.md`, `docs/DATABASE.md`, `docs/TESTING.md`, `android/backend/README.md`.
- Batch records in `docs/BUILD_ORDER.md` and `docs/PROGRESS.md` updated honestly; no batch marked complete.

## Tests and CI evidence
| Run | Commit | Result | Notes |
| --- | --- | --- | --- |
| 29 / `44246930798` | `eb9d991` | success | 37 app JVM tests, 17 backend JVM tests, 8 emulator tests, lint, debug APK |
| 31 / `44249004280` | `d295336` | success | 37 app JVM tests, 22 backend JVM tests, 8 emulator tests, lint, debug APK |
| 32 / `44250745206` | `6c25411` | success | 40 app JVM tests, 28 backend JVM tests, 9 emulator tests, lint, debug APK |
| 35 / `44252590103` | `cda3bc0` | success | 44 app JVM tests, 28 backend JVM tests, 9 emulator tests, lint, debug APK |
| 36 / `44253390512` | `763b13a` | failure | one audit test assumed a refresh token was consumed before first rotation; corrected in `25ba0e5` |
| 38 / `44253912755` | `25ba0e5` | success | 44 app JVM tests, 33 backend JVM tests, 9 emulator tests, lint, debug APK |
| 41–45 / various | various | failure | emulator-job regressions while adding host-scoped bearer tokens and phone-only UI assertions (Compose imports/API, redirect test wiring, repeated `setContent` and unavailable `printToString`); corrected in `a34d48f`, `466d112`, `f450c87`, `04ee2ad`, `31917b6`, `2699b59` |
| 46 / `44259092033` | `31917b6` | success | 50 app JVM tests, 34 backend JVM tests, 11 emulator tests, lint, debug APK |
| 47 / `44259910664` | `2699b59` | success | 50 app JVM tests, 34 backend JVM tests, 11 emulator tests, lint, debug APK |
| 48–49 / `44261135410`, `44261194479` | `e954378`, `9fe8e2b` | cancelled | documentation-only pushes superseded by the branch concurrency rule; no code evidence from these runs |
| 50 / `44261505452` | `9c5146a` | success | 52 app JVM tests, 34 backend JVM tests, 11 emulator tests, lint, debug APK |
| `44263375793` | `a719ca4` | cancelled | documentation-only push superseded by the branch concurrency rule |
| 51 / `44263462812` | `5679a9b` | success | 53 app JVM tests (new manifest assertion), 34 backend JVM tests, 11 emulator tests, lint, debug APK |

Latest evidence is run 51 on the exact HEAD. Artifacts `messenger-pro-backend-test-results`, `messenger-pro-unit-test-results`, `messenger-pro-debug`, `messenger-pro-lint` and `messenger-pro-device-test-results` are published under that run (IDs `14408883350`, `14408574099`, `14408692985`, `14409566167`, `14409845114` respectively).
Latest debug APK SHA-256: `2539d8015ffd08437e4da83945877782c70cda3fe39ad052147194f186da08c0`.

## Exact blockers
1. No Supabase project or credentials → no deployed schema/RLS/Edge Functions; server-authorised behaviour cannot be verified at runtime.
2. No SMS provider account/funding; eSMS Africa `.io` vs `.com` identity unresolved → real `+268` delivery and end-to-end delivered-code tests remain BLOCKED.
3. Original 600-feature catalogue unavailable → reconstruction stays labelled and awaiting review.
4. Device-only behaviours need a physical device or documented emulator with the required capability.
5. Release signing material, protected secrets and Play Console access require explicit authorisation.

## Next specific task
Port the reference-core contracts and loopback HTTP adapter into hosted Edge Functions with durable repositories, TLS, provider credentials and a real `+268` delivered-code test, then add server-authorisation negative tests. Do not mark any feature `COMPLETE` or any batch closed until the exit gates in `docs/BUILD_ORDER.md` are evidenced.
