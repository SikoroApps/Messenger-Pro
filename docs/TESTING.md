# Testing and evidence policy

All builds and tests run on GitHub Actions (`.github/workflows/android-ci.yml`). There is no
local Android SDK requirement. A passing status is evidence only for what actually ran.

## Jobs and suites

| Job / task | Suite | Evidence artifact |
| --- | --- | --- |
| `verify` → `:app:testDebugUnitTest` | Android JVM unit tests (normalisation, state machine, HTTP/TLS contract, bearer-token scoping, sessions, jittered retry scheduling, localized OTP extraction, redaction) | `messenger-pro-unit-test-results` |
| `verify` → `:backend:test` | Server-authoritative OTP/session core, bounded device-session records, loopback HTTP contract adapter, SMS receipt rules, keyed-hash audit/abuse logging, eSMS sandbox adapter, provider-managed OTP, Supabase REST adapters and shared domain primitives | `messenger-pro-backend-test-results` |
| `credentials-probe` | Presence/shape probes for organization secrets, keystore alias, Supabase keys and eSMS sandbox start/check; no values or provider payloads printed | workflow run logs only |
| `verify` → `:app:lintDebug` | Static analysis | `messenger-pro-lint` |
| `verify` → `:app:assembleDebug` | Debug APK + package-name assertion | `messenger-pro-debug` |
| `verify` → APK secret-marker scan | Dex/resource string scan for service-role, eSMS and Supabase deployment markers | `verify` job log |
| `device-smoke` → `connectedDebugAndroidTest` | API 35 x86_64 emulator with KVM | `messenger-pro-device-test-results` |

## What a green run does and does not prove

- It proves the tracked code compiles, the unit/instrumentation suites pass, lint is clean and a
  debug APK with the expected package name is produced on the exact commit SHA.
- Latest exact code evidence: run `44376666862` on `41dbe93ec7a41e2ceaf79389e5f07d4467881cac` —
  59 app JVM tests, 75 backend JVM tests (including the eSMS, provider-OTP, Supabase and
  provider-HTTP suites plus the new account-lifecycle/anti-enumeration cases), API 35 emulator
  smoke job green, 0 failures/errors/skips in the JVM result XML, lint clean, client artifact
  secret-marker scan passed, debug APK SHA-256
  `2a2581344c051e8103f2bb800dd7e0c1c3d635c794425c4f969c6763026506fa`.
- Credential availability evidence: probe run `44336159672` on `b5d54f9` succeeded. It proves only
  that secrets are present and usable in sandbox/CI; it does not prove hosted deployment, real
  SMS delivery or secret visibility restriction. See `docs/CREDENTIALS.md`.
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
or a device-control UI. `MP-079` is covered by atomic `OtpRepository.consume` tests and a replay
after session revocation; it is not proof of a deployed Edge Function or durable SQL consume.
`MP-080` is covered by reference-core tests proving a code issued for one request or a previous
completed session cannot open a different/newer session for the same phone; it is not proof of a
deployed Edge Function or durable request binding. `MP-113` is covered by cross-account negative
authorization tests against a session-derived caller; it is not proof of a deployed Supabase RLS
policy or SQL migration. `MP-114` is covered by in-memory audit/abuse tests showing generic
signals and keyed hashes only (no raw phone, IP, code or message body); it is not proof of a
deployed telemetry sink or RLS-protected audit endpoint. `MP-045` and `MP-046` are covered by
`SecretBoundaryTest` and the APK scan; `MP-081`–`MP-084` are covered by loopback provider-HTTP
account tests with fake transports. These are static/loopback evidence only, not deployed account
or authorization behaviour. The gated Supabase migration workflow has not been exercised because
the three deployment credentials are absent.

Latest code evidence: CI run `44376666862`,
https://github.com/SikoroApps/Messenger-Pro/actions/runs/44376666862, on
`41dbe93ec7a41e2ceaf79389e5f07d4467881cac` — 59 app JVM tests, 75 backend JVM tests, API 35
emulator smoke job green, lint clean, APK secret-marker scan passed and debug APK SHA-256
`2a2581344c051e8103f2bb800dd7e0c1c3d635c794425c4f969c6763026506fa`. Credential probes:
`44336159672` (https://github.com/SikoroApps/Messenger-Pro/actions/runs/44336159672) and
`44374465003` (https://github.com/SikoroApps/Messenger-Pro/actions/runs/44374465003).
