# Progress tracker

## Current state
Branch `main`, verified HEAD `eb9d991d36dc608ba1887cc600867260a629d9b4`.
Android CI run 29, https://github.com/SikoroApps/Messenger-Pro/actions/runs/44246930798 — **success on the exact HEAD**, both jobs green.

- JVM unit tests (`:app:testDebugUnitTest`): 37 passed, 0 failures/errors/skips (artifact `messenger-pro-unit-test-results`).
- Server-authoritative core tests (`:backend:test`): 17 passed, 0 failures/errors/skips (artifact `messenger-pro-backend-test-results`).
- Emulator tests (API 35, KVM x86_64): 8 passed, 0 failures (artifact `messenger-pro-device-test-results`).
- Lint: `lintDebug` passed (artifact `messenger-pro-lint`).
- Debug APK: artifact `messenger-pro-debug`, SHA-256 `78c4e9a1277eaef4aa5620595a08e7c4452a3d392062e0a0cf134059e3b58b40`.

## Work completed in this segment
- Repaired a broken CI baseline at HEAD: TLS helper invocation, Compose test-tag/editable-text assertions, HTTPS base-url policy for local TLS test servers, and a real `org.json` implementation for JVM unit tests. Commits `f43667f`, `2a3e7f6`, `b06de54`, `01db464`, `2552566`.
- Added `android/backend/`, a pure-JVM server-authoritative reference core with 11 identity tests: server-owned CSPRNG codes, HMAC-SHA256 verifiers with per-request salt and constant-time comparison, atomic single-use consume, expiry, bounded attempts, 60-second cooldown, per-phone/per-IP/global limits, idempotent request replay, token rotation with reuse detection, family revocation, logout and redaction. Commit `c7a528c`.
- Added shared domain foundations with 6 tests: consent-gated hash-only contact matching, block/report models, message idempotency/pagination/delivery state, media validation, privacy/account-control/app-lock policy. Commits `2a76f5b`, `eb9d991`.
- Added contract/evidence documentation: `docs/API.md`, `docs/DATABASE.md`, `docs/TESTING.md`, `android/backend/README.md`, and honest batch records in `docs/BUILD_ORDER.md`.

## Dependency note
The reference core implements the *rules* and is CI-testable without credentials, but it is not a deployed backend. Real end-to-end registration and sign-in with a delivered SMS code remains BLOCKED on a hosted database/Edge Functions, an SMS provider with `+268` delivery and provider credentials. Sandbox/mocked tests are never counted as real delivery. Consequently every affected catalogue row stays `TESTED` or `BLOCKED`; no row is `COMPLETE`.

## Blockers (unchanged)
- No hosted Supabase project/credentials: backend deployment, RLS verification and server-enforced limits cannot be evidenced at runtime.
- No SMS provider account/funding and unresolved eSMS Africa `.io` vs `.com` identity: real `+268` delivery cannot be tested.
- Original 600-feature catalogue not recovered after repository/history/GitHub/web searches: reconstruction remains explicitly labelled.
- Device-only behaviour (permissions, notifications, autofill, app lock) needs a physical device or documented capable emulator.
- Release signing keys, checksum publication credentials and Play Console rollout require protected secrets and authorisation; the untracked release workflow is scaffolding only.

## Next unblocked work
Wire the reference-core rules into a deployable Edge Function adapter and the Android client, then add negative authorization and runtime tests once credentials exist. Until then, continue only with independently verifiable, credential-free work and keep statuses honest.
