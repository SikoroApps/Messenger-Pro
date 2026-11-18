# Progress tracker

## Current state
Branch `main`, verified HEAD `5679a9be2ae6b9e221abe11bb6d883544e586e2c`.
Android CI run 51, https://github.com/SikoroApps/Messenger-Pro/actions/runs/44263462812 — **success on the exact code HEAD `5679a9be2ae6b9e221abe11bb6d883544e586e2c`**, both jobs green. The subsequent documentation-only commit `089b4904ed8dd76cbfe4a059bdc25b1bf5db544c` was re-verified by run `44265679115` (success); all test counts and checksums below refer to run 51.

- JVM unit tests (`:app:testDebugUnitTest`): 53 passed, 0 failures/errors/skips (artifact `messenger-pro-unit-test-results`).
- Server-authoritative core tests (`:backend:test`): 34 passed, 0 failures/errors/skips (artifact `messenger-pro-backend-test-results`).
- Emulator tests (API 35, KVM x86_64): 11 passed, 0 failures (artifact `messenger-pro-device-test-results`).
- Lint: `lintDebug` passed (artifact `messenger-pro-lint`).
- Debug APK: artifact `messenger-pro-debug`, SHA-256 `2539d8015ffd08437e4da83945877782c70cda3fe39ad052147194f186da08c0`.

## Work completed in this segment
- Repaired a broken CI baseline at HEAD: TLS helper invocation, Compose test-tag/editable-text assertions, HTTPS base-url policy for local TLS test servers, and a real `org.json` implementation for JVM unit tests. Commits `f43667f`, `2a3e7f6`, `b06de54`, `01db464`, `2552566`.
- Added `android/backend/`, a pure-JVM server-authoritative reference core with 11 identity tests: server-owned CSPRNG codes, HMAC-SHA256 verifiers with per-request salt and constant-time comparison, atomic single-use consume, expiry, bounded attempts, 60-second cooldown, per-phone/per-IP/global limits, idempotent request replay, token rotation with reuse detection, family revocation, logout and redaction. Commit `c7a528c`.
- Added shared domain foundations with 6 tests: consent-gated hash-only contact matching, block/report models, message idempotency/pagination/delivery state, media validation, privacy/account-control/app-lock policy. Commits `2a76f5b`, `eb9d991`.
- Added metadata-only SMS delivery receipt core: provider-independent hand-off, redacted event log, receipt matching/terminal-state rules and generic failure categories with 5 JVM tests. Commit `d295336`; CI run 30 (`44249004280`) was green with 22 backend tests.
- Added a loopback-only HTTP adapter for the JSON auth contract with real-socket tests for code/token redaction, scoped idempotency, stable status codes, cooldown `Retry-After`, replay rejection, rotation reuse, logout and provider failure. Added client idempotency headers and `SESSION_REVOKED` error mapping. Commit `6c25411`; CI run 32 is green.
- Preserved and tested concurrent host-allowlist hardening: optional HTTPS host allowlist from public build config, rejected at `HttpAuthApi` construction and covered by client tests.
- Added a pure-JVM `SmsOtpExtractor` with retriever-hash validation, user-consent parsing and ambiguity rejection; 4 unit tests added in run 35. OS retriever/Play-services wiring and delivered-message evidence remain BLOCKED.
- Added metadata-only auth audit and abuse-signal logging to the reference core: keyed-hash audit records for OTP/session transitions, generic abuse signals for malformed, replayed and reused requests, and logout audit only when a session was actually revoked. Five `AuditTest` cases pass in run 38. Run 36 failed on an incorrect test expectation (first rotation is valid); `25ba0e5` corrected the test and run 38 is green.
- The audit code is reference-core evidence only. `MP-114` and `MP-116` stay `NOT_STARTED` because their reconstructed prerequisite chains (`MP-113`, `MP-115`) and deployed-runtime gates are unmet; no status was inflated.
- Added an OkHttp network interceptor that strips any pre-existing `Authorization` header and attaches the current access token only to allowlisted HTTPS hosts. Because it is a network interceptor, the host check is re-evaluated on redirects. `AuthorizationHeaderInterceptorTest` (4 cases) and `HttpAuthApiTest` TLS/redirect regressions cover the rule. Runs 41–45 failed on emulator-job test/compile regressions while introducing these changes; commits `a34d48f`, `466d112`, `f450c87`, `04ee2ad`, `31917b6` and `2699b59` resolved them, and runs 46 and 47 are green.
- Added full jitter over a bounded exponential window and an explicit transient/idempotent retry gate to `RetryPolicy`. Seeded-jitter, hard-cap and total-budget tests replace the former fixed-delay assertion and pass in run 50; `MP-097` is `TESTED`. This is network-layer policy evidence only, with no runtime retry executor or hosted backend integration.
- Added `SecurityConfigurationTest`, an automated JVM assertion that the manifest disables cleartext traffic and requests no broad SMS permission. Together with the existing `TransportPolicy`, `HttpAuthApi` and interceptor cleartext negatives, this completes the evidence required by `MP-048`; `MP-048` and `MP-103` (replay rejection, already covered by `AuthServiceTest.correctCodeVerifiesExactlyOnceThenReplayIsRejected` and `SecurityAndNetworkTest`) are now `TESTED` from run 51. No deployed backend or real SMS delivery is claimed.
- Advanced `MP-091` and `MP-097` to `TESTED` from runs 47 and 50, and 9 earlier already-evidenced registry rows (client transport, formatting, manual entry, cooldown timer, refresh flow, retry budget, offline detection and OTP extraction) from `NOT_STARTED` to `TESTED`; 50 rows are now `TESTED` and no row was marked `COMPLETE`.
- Added contract/evidence documentation: `docs/API.md`, `docs/DATABASE.md`, `docs/TESTING.md`, `android/backend/README.md`, and honest batch records in `docs/BUILD_ORDER.md`.

## Dependency note
The reference core and loopback adapter implement the *rules and JSON contract* and are CI-testable without credentials, but they are not a deployed backend. Real end-to-end registration and sign-in with a delivered SMS code remains BLOCKED on a hosted database/Edge Functions, an SMS provider with `+268` delivery and provider credentials. Sandbox/mocked tests and loopback sockets are never counted as real delivery or hosted deployment. Consequently every affected catalogue row stays `TESTED` or `BLOCKED`; no row is `COMPLETE`.

## Blockers (unchanged)
- No hosted Supabase project/credentials: backend deployment, RLS verification and server-enforced limits cannot be evidenced at runtime.
- No SMS provider account/funding and unresolved eSMS Africa `.io` vs `.com` identity: real `+268` delivery cannot be tested.
- Original 600-feature catalogue not recovered after repository/history/GitHub/web searches: reconstruction remains explicitly labelled.
- Device-only behaviour (permissions, notifications, autofill, app lock) needs a physical device or documented capable emulator.
- Release signing keys, checksum publication credentials and Play Console rollout require protected secrets and authorisation; the untracked release workflow is scaffolding only.

## Next unblocked work
Port the reference-core rules and loopback HTTP contract into a hosted Edge Function adapter with durable repositories, TLS and provider credentials, then add negative authorization and real delivered-code tests once credentials exist. Until then, continue only with independently verifiable, credential-free work and keep statuses honest.
