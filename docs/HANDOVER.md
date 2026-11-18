# Session handover — Messenger Pro

## Repository state
- Repository: `SikoroApps/Messenger-Pro` (public), branch `main`.
- HEAD at handover: `daaf9431d21a10dc9cb53fff522500a036e3e63b` — `test: cover expired, consumed, throttled and cold-start session cases`.
- `.github/workflows/android-release.yml` is untracked local Batch 7 work and must not be added accidentally.
- No local Android SDK or Gradle builds were run. All compile/test evidence comes from GitHub Actions.

## Work completed
- Batch 1 foundation COMPLETE, verified on `e68344b5f76e08f7f18d8cea176cc62b785b8a8c` (CI runs 12 and 13).
- Reconstructed, explicitly labelled `RECONSTRUCTED — NOT ORIGINAL` 600-feature catalogue and Batch 2 dependency plan committed on `4c9732bd0143143cca24b67ee3cfc481b1467a19`.
- Client identity foundation on `d9ad485cd12153de268a15b5cbe1ac7a0d6ed4d3` (E.164 normalization/validation, typed `AuthApi`/`AuthError`, `AuthStateMachine`, retry-after parser, redaction, Compose screens).
- Encrypted session lifecycle on `1e9b07f331af8cfc262168d0657c1f604f557a7e` (Keystore-backed `EncryptedSharedPreferences`, refresh/expiry, logout purge, encryption-at-rest device test).
- Checksum-pinned Gradle 8.9 wrapper generated remotely on workflow run `44238658814` and committed as `3444c6f0493d394abcdf48ad5560b2a306f4725f`; CI switched to `./gradlew` on `4c3eb3e5aeb157881f66b31cf4555f15617579ff`.
- Extra expired/consumed/throttled and cold-start coverage on `daaf9431d21a10dc9cb53fff522500a036e3e63b`.
- 19 client-boundary catalogue rows advanced from NOT_STARTED to TESTED with precise evidence; no COMPLETE status added.

## Tests and CI evidence
| Run | Commit | Result | Notes |
| --- | --- | --- | --- |
| 13 / `44206655226` | `e68344b` | success | Batch 1 re-verification |
| 15 / `44237043927` | `d9ad485` | success | 16 JVM tests, 3 device tests |
| 16 / `44237995045` | `1e9b07f` | success | 20 JVM tests, 4 device tests |
| 18 / `44239055899` | `4c3eb3e` | success | checksum-pinned wrapper build |
| 19 / `44239591272` | `daaf943` | success | 21 JVM tests, 5 device tests, lint, APK |

Latest artifact IDs (run 19): debug APK `14398863320`, unit results `14398808021`, device results `14398504406`, lint `14398199786`.
Latest debug APK SHA-256: `9cc3a4f937c64679c6824c87de3baa7e3b3286c91f4cef73f898ace7015e66d7`.
APK download: `https://github.com/SikoroApps/Messenger-Pro/actions/runs/44239591272/artifacts/14398863320` (requires GitHub sign-in).

## Exact blockers
1. No Supabase project or credentials → backend deployment, server-owned OTP generation/hashing/expiry, account creation, server-enforced rate limits and RLS cannot be implemented or verified.
2. No SMS provider account/funding; eSMS Africa `.io` vs `.com` identity unresolved → real `+268` SMS delivery and end-to-end delivered-code tests cannot run.
3. Original 600-feature catalogue unavailable → reconstruction remains labelled and awaiting review.
4. Paid services, production deployment, credential rotation and signing material require explicit user authorization.

## Next specific task
Implement the next pure-client prerequisites in dependency order: TLS-only transport policy and cleartext rejection (`MP-048`), HTTP client timeout/connection policy (`MP-050`), phone input formatting/masking (`MP-054`), manual verification-code interaction (`MP-065`, `MP-066`), client resend cooldown countdown (`MP-073` client portion), authenticated request/refresh wrapper (`MP-091`, `MP-092`), and bounded retry/backoff plus offline handling (`MP-097`–`MP-099`). Add JVM/emulator tests, run GitHub Actions, inspect results, then update statuses and this handover before selecting the following task.
