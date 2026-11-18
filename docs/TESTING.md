# Testing and evidence policy

All builds and tests run on GitHub Actions (`.github/workflows/android-ci.yml`). There is no
local Android SDK requirement. A passing status is evidence only for what actually ran.

## Jobs and suites

| Job / task | Suite | Evidence artifact |
| --- | --- | --- |
| `verify` → `:app:testDebugUnitTest` | Android JVM unit tests (normalisation, state machine, HTTP/TLS contract, sessions, retry, redaction) | `messenger-pro-unit-test-results` |
| `verify` → `:backend:test` | Server-authoritative OTP/session core and shared domain primitives | `messenger-pro-backend-test-results` |
| `verify` → `:app:lintDebug` | Static analysis | `messenger-pro-lint` |
| `verify` → `:app:assembleDebug` | Debug APK + package-name assertion | `messenger-pro-debug` |
| `device-smoke` → `connectedDebugAndroidTest` | API 35 x86_64 emulator with KVM | `messenger-pro-device-test-results` |

## What a green run does and does not prove

- It proves the tracked code compiles, the unit/instrumentation suites pass, lint is clean and a
  debug APK with the expected package name is produced on the exact commit SHA.
- It does **not** prove backend deployment, real SMS delivery, permission/revocation behaviour,
  notification delivery, physical-device app lock, signed release, or Play Console rollout.
- Sandbox or mocked-provider tests are never counted as real `+268` delivery.
- Device-only features remain `IN_PROGRESS` until verified on a physical device or a documented
  emulator with the required capability.

## Status rules for the feature catalogue

`TESTED` requires passing automated tests for the implemented slice. `COMPLETE` additionally
requires the full prerequisite chain and the runtime/device evidence named in the feature's
`test_requirement`. No status is advanced to imply deployment that has not happened.
