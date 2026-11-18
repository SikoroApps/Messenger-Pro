# Progress tracker

## Current state
Branch `main`, HEAD `daaf9431d21a10dc9cb53fff522500a036e3e63b`.
Android CI run 19, https://github.com/SikoroApps/Messenger-Pro/actions/runs/44239591272 — **success on the exact HEAD**, both jobs green.

- JVM unit tests: 21 passed, 0 failures/errors/skips (`AuthStateMachineTest` 5, `BuildConfigurationTest` 3, `PhoneNormalizerTest` 4, `SecurityAndNetworkTest` 5, `SessionManagerTest` 4).
- Emulator tests (API 35): 5 passed (`AppLaunchTest` 1, `IdentityScreenTest` 2, `EncryptedTokenStoreTest` 2).
- Lint: `lintDebug` passed. Debug APK artifact `messenger-pro-debug` id `14398863320`, SHA-256 `9cc3a4f937c64679c6824c87de3baa7e3b3286c91f4cef73f898ace7015e66d7`.
- Catalogue: 19 client-boundary features are `TESTED`, 0 are `COMPLETE`; the remainder are `NOT_STARTED` or recorded as blocked in the plan. No status was inflated.

## Work completed in this segment
- Client identity foundation (commit `d9ad485cd12153de268a15b5cbe1ac7a0d6ed4d3`, CI run 15 success): E.164 normalization and validation, typed `AuthApi`/`AuthError` contract with no fabricated success, `AuthStateMachine` that cannot authenticate on OTP-screen open, retry-after parsing, redaction helpers, Compose phone/code screens, JVM and device tests.
- Encrypted session lifecycle (commit `1e9b07f331af8cfc262168d0657c1f604f557a7e`, CI run 16 success): Keystore-backed `EncryptedSharedPreferences` token store, near-expiry server rotation, logout purge, JVM and emulator tests including on-disc encryption assertion.
- Remote wrapper generation (commit `47588abaa9958baecb5db66f9e02037f078a950b`, workflow run `44238658814` success) and wrapper commit `3444c6f0493d394abcdf48ad5560b2a306f4725f`, distribution SHA-256 `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`.
- CI switched to the checksum-pinned wrapper (commit `4c3eb3e5aeb157881f66b31cf4555f15617579ff`, CI run 18 success).
- Extra expired/consumed/throttled and cold-start coverage (commit `daaf9431d21a10dc9cb53fff522500a036e3e63b`, CI run 19 success).

## Dependency note
The reconstructed catalogue encodes a strictly linear dependency chain through `MP-120`, including backend and SMS features that are genuinely blocked. The work above is **client-boundary only** and was selected because those prerequisites cannot be satisfied without credentials. It is recorded as `TESTED`, never `COMPLETE`; final `COMPLETE` status for each ID still requires its full prerequisite chain and the evidence in section 14 of `docs/BATCH_2_PLAN.md`. No dependent backend, account or messaging feature was started.

## Next unblocked task
Implement the remaining pure-client prerequisites in dependency order: TLS-only transport policy and HTTP timeouts (`MP-048`, `MP-050`), phone input formatting/masking (`MP-054`), manual code-entry interaction (`MP-065`, `MP-066`), cooldown countdown (`MP-073` client portion), authenticated request/refresh wrapper (`MP-091`, `MP-092`), bounded retry/backoff and offline handling (`MP-097`–`MP-099`), with JVM/emulator tests and a fresh GitHub Actions run.

## Blockers (unchanged)
- No Supabase project or credentials → backend deployment, server-owned OTP state, account creation and server-enforced limits BLOCKED.
- No SMS provider account/funding and unresolved eSMS Africa `.io` vs `.com` identity → real `+268` delivery BLOCKED.
- Original 600-feature catalogue not recovered → reconstruction remains explicitly labelled and awaiting review.
- Paid services, production deployment, credential rotation and signing material require explicit authorization.
