# Testing and evidence policy

All builds and tests run on GitHub Actions (`.github/workflows/android-ci.yml`). There is no
local Android SDK requirement. A passing status is evidence only for what actually ran.

## Jobs and suites

| Job / task | Suite | Evidence artifact |
| --- | --- | --- |
| `verify` → `:app:testDebugUnitTest` | Android JVM unit tests (normalisation, state machine, HTTP/TLS contract, bearer-token scoping, sessions, jittered retry scheduling, redaction) | `messenger-pro-unit-test-results` |
| `verify` → `:backend:test` | Server-authoritative OTP/session core, loopback HTTP contract adapter, SMS receipt rules, keyed-hash audit/abuse logging and shared domain primitives | `messenger-pro-backend-test-results` |
| `verify` → `:app:lintDebug` | Static analysis | `messenger-pro-lint` |
| `verify` → `:app:assembleDebug` | Debug APK + package-name assertion | `messenger-pro-debug` |
| `device-smoke` → `connectedDebugAndroidTest` | API 35 x86_64 emulator with KVM | `messenger-pro-device-test-results` |

## What a green run does and does not prove

- It proves the tracked code compiles, the unit/instrumentation suites pass, lint is clean and a
  debug APK with the expected package name is produced on the exact commit SHA.
- Latest exact evidence: run 50 (`44261505452`) on `9c5146a47c846f9d4a0fe0bdce59ae982d414bd5` —
  52 app JVM tests, 34 backend JVM tests, 11 API 35 emulator tests, 0 failures/errors/skips,
  lint 0 errors/11 warnings, debug APK SHA-256
  `884cc34b0f09ec308c289e12b6435aa343185a0e8bcfcb845042719fbd7db8b3`.
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

Latest evidence: CI run 50, https://github.com/SikoroApps/Messenger-Pro/actions/runs/44261505452,
on `9c5146a47c846f9d4a0fe0bdce59ae982d414bd5` — 52 app JVM tests, 34 backend JVM tests, 11 API 35
emulator tests, lint clean and debug APK SHA-256
`884cc34b0f09ec308c289e12b6435aa343185a0e8bcfcb845042719fbd7db8b3`.
