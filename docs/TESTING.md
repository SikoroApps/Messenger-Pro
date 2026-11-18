# Testing and evidence policy

All builds and tests run on GitHub Actions (`.github/workflows/android-ci.yml`). There is no
local Android SDK requirement. A passing status is evidence only for what actually ran.

## Jobs and suites

| Job / task | Suite | Evidence artifact |
| --- | --- | --- |
| `verify` → `:app:testDebugUnitTest` | Android JVM unit tests (normalisation, state machine, HTTP/TLS contract, bearer-token scoping, sessions, jittered retry scheduling, localized OTP extraction, redaction) | `messenger-pro-unit-test-results` |
| `verify` → `:backend:test` | Server-authoritative OTP/session core, bounded device-session records, loopback HTTP contract adapter, SMS receipt rules, keyed-hash audit/abuse logging and shared domain primitives | `messenger-pro-backend-test-results` |
| `verify` → `:app:lintDebug` | Static analysis | `messenger-pro-lint` |
| `verify` → `:app:assembleDebug` | Debug APK + package-name assertion | `messenger-pro-debug` |
| `device-smoke` → `connectedDebugAndroidTest` | API 35 x86_64 emulator with KVM | `messenger-pro-device-test-results` |

## What a green run does and does not prove

- It proves the tracked code compiles, the unit/instrumentation suites pass, lint is clean and a
  debug APK with the expected package name is produced on the exact commit SHA.
- Latest exact code evidence: run 60 `44272939936` on `dc98268b7213d83aa8f83c831e953224c3ea5923` —
  57 app JVM tests (including 8 `SmsOtpExtractorTest` cases), 40 backend JVM tests (including 5
  `SmsFailoverTest` cases and a bounded device-session record case), 11 API 35 emulator tests,
  0 failures/errors/skips, lint 0 errors/11 warnings, debug APK SHA-256
  `02f0d1e1e8f50e08a8a2e8578e71dd662a75bd5781a7d86faa2d36f23ea3dc76`.
- It does **not** prove backend deployment, real SMS delivery, permission/revocation behaviour,
  notification delivery, physical-device app lock, signed release, or Play Console rollout.
- Sandbox or mocked-provider tests are never counted as real `+268` delivery.
- Device-only features remain `IN_PROGRESS` until verified on a physical device or a documented
  emulator with the required capability.

## Status rules for the feature catalogue

`TESTED` requires passing automated tests for the implemented slice. `COMPLETE` additionally
requires the full prerequisite chain and the runtime/device evidence named in the feature's
`test_requirement`. No status is advanced to imply deployment that has not happened. A
CI-tested reference-core capability does not by itself advance a catalogue row whose prerequisite
chain is still open; the audit/abuse work is recorded in `docs/PROGRESS.md` while `MP-114` and
`MP-116` remain `NOT_STARTED`. Bearer-token scoping is covered by `AuthorizationHeaderInterceptorTest`
and `HttpAuthApiTest` redirect/HTTPS regressions; that is client/network-layer evidence only, not
proof of a deployed authorization runtime. Likewise, `RetryPolicy` is covered by
`TransportAndInputTest` seeded-jitter, bounded-exponential-window, transient/idempotent gate and
hard-cap cases; this does not imply a runtime retry executor, hosted backend or real SMS delivery.
`SecurityConfigurationTest` asserts the manifest-level cleartext and broad-SMS-permission policy for
`MP-048`, and replay rejection for `MP-103` rests on `AuthServiceTest` plus the client `CODE_CONSUMED`
contract case; neither is evidence of deployed enforcement. `SmsFailoverTest` covers the
`FailoverSmsGateway` decision policy for `MP-071`; a mocked provider and loopback core are never real
`+268` delivery evidence. `VerificationMessageTemplate` and the extended `SmsOtpExtractor` cover
locale-tagged template round trips for `MP-072`, but no delivered SMS, OS retriever consent flow or
human-reviewed siSwati copy is evidenced. `MP-095` is covered by a loopback test of sanitized,
bounded device-label creation and rotation continuity; it is not proof of Supabase/RLS persistence
or a device-control UI.

Latest code evidence: CI run 60 `44272939936`,
https://github.com/SikoroApps/Messenger-Pro/actions/runs/44272939936, on
`dc98268b7213d83aa8f83c831e953224c3ea5923` — 57 app JVM tests, 40 backend JVM tests, 11 API 35
emulator tests, lint clean and debug APK SHA-256
`02f0d1e1e8f50e08a8a2e8578e71dd662a75bd5781a7d86faa2d36f23ea3dc76`.
