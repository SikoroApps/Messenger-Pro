# Session handover — Messenger Pro

## Repository state
- Repository: `SikoroApps/Messenger-Pro` (public), branch `main`.
- Current HEAD: `41dbe93ec7a41e2ceaf79389e5f07d4467881cac` — `test: cover account boundaries and add gated migration workflow`; CI run `44376666862` (success, both jobs).
- Earlier code HEAD: `a172562947c47a77164017bbd69ed4c200c74ff7` — `ci: probe optional Supabase deployment credentials`; Android CI run `44374425974` and credentials probe `44374465003` (success).
- Earlier verified code HEAD: `ca8cb73ecee58c48ddc40ae0b2d26f179ab6b30a` — `ci: print debug APK checksum in verification logs`; CI run `44356132108` (success, both jobs).
- `.github/workflows/android-release.yml` remains untracked local Batch 7 scaffolding and must not be added accidentally.
- No local Android SDK or Gradle builds were run. All compile/test evidence comes from GitHub Actions.

## Work completed
- Batch 1 foundation COMPLETE (CI runs 12 and 13).
- Batch 2 client boundary implemented and CI-tested through run 19, then a server-authoritative reference core added in `android/backend/` (commits `c7a528c`, `2a76f5b`, `eb9d991`).
- Metadata-only SMS delivery receipt core added and verified in run 31 (`d295336`); loopback HTTP adapter, scoped idempotency, client error mapping and host allowlist added and verified in run 32 (`6c25411`); SMS OTP message extractor added and verified in run 35 (`cda3bc0`); keyed-hash auth audit and generic abuse signals added and verified in run 38 (`763b13a`, `25ba0e5`).
- Access tokens are now stripped from unrelated requests and attached only to allowlisted HTTPS hosts, including redirects; `MP-091` is `TESTED` from run 47. Runs 41–45 failed on emulator-job test/compile regressions in the new Compose and redirect tests and were resolved through `a34d48f`, `466d112`, `f450c87`, `04ee2ad`, `31917b6` and `2699b59`.
- Bounded jittered exponential backoff and an explicit transient/idempotent retry gate were added to `RetryPolicy`; `MP-097` is `TESTED` from run 50. An automated manifest assertion now covers disabled cleartext and absence of broad SMS permissions; `MP-048` and `MP-103` are `TESTED` from run 52. A provider-independent `FailoverSmsGateway` now retries only clean pre-handoff failures and refuses ambiguous ones; `MP-071` is `TESTED` from run 54. Locale-tagged English and siSwati short verification templates and Unicode-safe keyword boundaries are tested through `SmsOtpExtractor`; `MP-072` is `TESTED` from run 58, with no claim of real delivery or human-reviewed siSwati copy. Optional 64-character sanitized device labels are now recorded on session records and preserved across refresh, with a loopback HTTP test; `MP-095` is `TESTED` from run 60, with no durable RLS storage or device-control UI. Consumed-code replay tests now cover the atomic single-use contract and replay after the original session is revoked; `MP-079` is `TESTED` from run 63, with no deployed Edge Function or durable SQL consume. A reference-core ownership guard now rejects cross-account reads/writes from session-derived callers with non-enumerating failures; `MP-113` is `TESTED` from run 66, with no deployed RLS policy. Cross-session code reuse is now covered by reference-core tests proving a code from one request or previous session cannot open a different/newer session, and cooldown/rate-limit abuse events are covered as generic keyed-hash metadata only; `MP-080` and `MP-114` are `TESTED` from run 69, with no deployed backend, Edge Function, durable SQL, telemetry sink or RLS audit storage. Runs 48–49 and runs 51, 55 were documentation-only runs cancelled by the branch concurrency rule; runs 62, 65 and 68 failed on incorrect test expectations (corrected in `d1181d8`, `9de1861` and `a48a8d9`); runs 50, 52–54, 56–61, 63, 64, 66, 67 and 69 are green.
- CI baseline repaired after the TLS/HTTP transport commit: `f43667f`, `2a3e7f6`, `b06de54`, `01db464`, `2552566`.
- Shared domain primitives for later batches (privacy, messaging, media, account controls) implemented with JVM tests.
- Runtime credential probe verified all eight organization secrets, keystore/alias, both Supabase keys and eSMS sandbox start/check without printing values (run `44336159672`). Secret visibility restriction still needs `admin:org`.
- Added eSMS Verify adapter (`ESmsVerify.kt`), provider-managed OTP service (`ProviderOtp.kt`), Supabase REST adapters (`Supabase.kt`) and deny-by-default RLS migration (`supabase/migrations/0001_identity_foundation.sql`), with 17 new backend tests passing in run `44337788301`. No real SMS delivery or hosted deployment is claimed.
- Wired the documented HTTP endpoints to the provider/Supabase adapters in `ProviderAuthHttp.kt`. Provider OTP attempts and terminal failures are now persisted, so retries cannot reset the counter or revive a failed request. Five `ProviderAuthHttpTest` cases verify request/verify/refresh/logout, idempotency, wrong-code, replay, durable attempt limits and generic provider failures over real loopback sockets with a fake transport for both pinned hosts. CI now prints the debug APK SHA-256 in the job log. Run `44356132108` is green with 72 backend tests; no real provider or database was contacted.
- Added three provider-HTTP account/anti-enumeration tests and two client secret-boundary tests. Run `44376666862` is green with 59 app JVM and 75 backend JVM tests, lint clean, API 35 emulator smoke green, APK SHA-256 `2a2581344c051e8103f2bb800dd7e0c1c3d635c794425c4f969c6763026506fa`, and the new client artifact secret-marker scan passed. Added `.github/workflows/supabase-migration.yml` gated on `DEPLOY-IDENTITY-0001`; optional deployment credentials were re-probed in run `44374465003` and are absent, so no migration was applied.
- Contract/evidence docs: `docs/API.md`, `docs/DATABASE.md`, `docs/TESTING.md`, `docs/CREDENTIALS.md`, `android/backend/README.md`.
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
| 51 / `44263375793` | `a719ca4` | cancelled | documentation-only push superseded by the branch concurrency rule |
| 52 / `44263462812` | `5679a9b` | success | 53 app JVM tests (new manifest assertion), 34 backend JVM tests, 11 emulator tests, lint, debug APK |
| 53 / `44265679115` | `089b490` | success | documentation-only commit re-verified; code evidence stays on run 52 |
| 54 / `44266144588` | `1d3f4d2` | success | 53 app JVM tests, 39 backend JVM tests (5 failover cases), 11 emulator tests, lint, debug APK |
| 55 / `44266379756` | `a1fe240` | cancelled | documentation-only push superseded by the branch concurrency rule |
| 56 / `44268324348` | `03d014a` | success | documentation-only commit re-verified; code evidence stays on run 54 |
| 57 / `44269499530` | `bd934a5` | success | documentation-only commit re-verified; code evidence stays on run 54 |
| 58 / `44269961154` | `887e6be` | success | 57 app JVM tests (8 extractor cases), 39 backend JVM tests, 11 emulator tests, lint, debug APK |
| 59 / `44272119380` | `c9491a6` | success | documentation-only commit re-verified; code evidence stays on run 58 |
| 60 / `44272939936` | `dc98268` | success | 57 app JVM tests, 40 backend JVM tests (new device-session record case), 11 emulator tests, lint, debug APK |
| 61 / `44274736746` | `6ce1dea` | success | documentation-only commit re-verified; code evidence stays on run 60 |
| 62 / `44275214421` | `4fb1ceb` | failure | one new replay test wrongly expected session records to be deleted on logout; corrected in `d1181d8`, and the backend artifact upload hit a transient ECONNRESET |
| 63 / `44275896011` | `d1181d8` | success | 57 app JVM tests, 42 backend JVM tests (new atomic consumed-code cases), 11 emulator tests, lint, debug APK |
| 64 / `44277681139` | `3ee6736` | success | documentation-only commit re-verified; code evidence stays on run 63 |
| 65 / `44278393973` | `97bb511` | failure | one authorization test wrongly expected NOT_FOUND through the service path where ownership is checked first; corrected in `9de1861` |
| 66 / `44279051934` | `9de1861` | success | 57 app JVM tests, 48 backend JVM tests (6 cross-account authorization cases), 11 emulator tests, lint, debug APK |
| 67 / `44280903554` | `d48557d` | success | documentation-only commit re-verified; code evidence stays on run 66 |
| 68 / `44281465713` | `e1dafdb` | failure | one new abuse-redaction test wrongly expected OTP_REQUEST_BURST when the cooldown was disabled (actual generic signal is RATE_LIMIT_HIT); corrected in `a48a8d9` |
| 69 / `44282658517` | `a48a8d9` | success | 57 app JVM tests, 50 backend JVM tests (cross-session rejection and metadata-only cooldown/rate-limit abuse cases), 11 emulator tests, lint, debug APK |
| `44290754295` | `152c3f5` | success | documentation-only commit; code evidence stays on run 69 |
| `44335097442` | `ea9e9f3` | failure | eSMS/provider code failed to compile on a Kotlin `Charset` overload; fixed in `59a30ae` |
| `44336015644` | `b5d54f9` | failure | provider test fixture code `000000` matched a timestamp string; fixed in `89ef801` |
| `44337067781` | `89ef801` | failure | Supabase adapter test used URL encoding as a refresh hash; fixed in `df30bff` |
| `44337788301` | `df30bff` | success | 57 app JVM tests, 67 backend JVM tests (eSMS adapter, provider-managed OTP and Supabase REST cases), API 35 emulator smoke job green, lint clean, debug APK |
| `44346976216` | `ea05d66` | success | documentation-only commit re-verified; code evidence stays on run `44337788301` |
| `44350395975` | `bcc8ac2` | success | 57 app JVM tests, 72 backend JVM tests (5 new `ProviderAuthHttpTest` cases), API 35 emulator smoke job green, lint clean, debug APK |
| `44356132108` | `ca8cb73` | success | same code tests as `bcc8ac2` plus CI checksum logging; 57 app JVM tests, 72 backend JVM tests, API 35 emulator smoke job green, lint clean, debug APK |
| `44374425974` | `a172562` | success | optional deployment-credential probe added; 57 app JVM tests, 72 backend JVM tests, API 35 emulator smoke green, lint clean, debug APK |
| `44376666862` | `41dbe93` | success | 59 app JVM tests (two secret-boundary cases), 75 backend JVM tests (three account/anti-enumeration cases), API 35 emulator smoke job green, lint clean, APK secret-marker scan passed, debug APK |

Separate workflows: Credentials probe `44336159672` on `b5d54f9` succeeded; re-probe `44374465003` on `a172562` succeeded and confirmed the three optional Supabase deployment secrets are absent. See `docs/CREDENTIALS.md`.

Latest code evidence is run `44376666862` on the exact code HEAD. APK checksum and the artifact secret-marker scan are printed directly in the `verify` job log.
Latest debug APK SHA-256: `2a2581344c051e8103f2bb800dd7e0c1c3d635c794425c4f969c6763026506fa`.

## Exact blockers
1. Organization secret visibility cannot be restricted/inspected without the `admin:org` scope: `gh auth refresh -h github.com -s admin:org` is the one-time user action needed. Runtime accessibility is already proven.
2. The gated migration workflow exists but no hosted migration/Edge Function deployment has been performed; `SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF` and `SUPABASE_DB_PASSWORD` were re-probed and are absent. Deployed schema, RLS and server-authorised runtime behaviour remain unverified.
3. eSMS is proven in test mode only. Real `+268` delivery requires a production key, wallet credits and an approved sender ID; sandbox codes are never delivery evidence.
4. Original 600-feature catalogue unavailable → reconstruction stays labelled and awaiting review.
5. Device-only behaviours need a physical device or documented emulator with the required capability.
6. Release workflow remains untracked scaffolding until a signed artifact/checksum is CI-evidenced; Play Console rollout needs separate authorization.

## Next specific task
Server-side endpoint wiring and the gated migration workflow are in place and CI-tested. Next: create the three Supabase deployment secrets, dispatch `.github/workflows/supabase-migration.yml` with `DEPLOY-IDENTITY-0001`, verify the post-deploy RLS assertions, add server-function deployment, then obtain a real delivered `+268` sign-in and hosted authorization evidence. Keep Batch 2 gated; do not mark any feature `COMPLETE` or any batch closed until the exit gates in `docs/BUILD_ORDER.md` are evidenced. The one-time user action for secret restriction remains `gh auth refresh -h github.com -s admin:org`.
