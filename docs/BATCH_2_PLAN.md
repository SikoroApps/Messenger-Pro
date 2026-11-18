# Batch 2 plan — Identity, Backend & Core Security

**Status: PLANNING — implementation not started.**
**Catalogue basis: `features/feature-catalogue.json` is labelled `RECONSTRUCTED — NOT ORIGINAL`. No feature is COMPLETE.**
**Phone number + OTP only. No email, no password, no mandatory Google account.**

---

## 0. Batch 1 verification (prerequisite)

Before this plan was written, Batch 1 was re-verified against the actual current HEAD:

| Check | Result |
| --- | --- |
| Repository / branch | `SikoroApps/Messenger-Pro`, `main` |
| HEAD verified | `e68344b5f76e08f7f18d8cea176cc62b785b8a8c` |
| CI on that exact HEAD | Run 13, https://github.com/SikoroApps/Messenger-Pro/actions/runs/44206655226 — `success` |
| Unit tests | 3 passed, 0 failures/errors/skips |
| Lint | `lintDebug` passed |
| Device test | `AppLaunchTest.appLaunchesToStableShell` passed on API 35 emulator |
| APK package | `com.sikorokoro44.messengerpro.debug` verified from built APK |
| Secret scan | No credentials/signing material in tracked files or history |

Batch 1 is not being repeated. A new regression will be repaired before Batch 2 work is marked complete.

## 0.1 Catalogue recovery report (required before mapping IDs)

Searches actually performed:

- Full working tree and all 27 commits reachable from `refs/heads/main`, `refs/remotes/origin/main` and `refs/remotes/origin/HEAD` for `MP-[0-9]{3}`, `600 features`, `catalogue`, `registry`, `status.json`.
- `git log --all --name-only` for any file ever named like a catalogue/registry/status file.
- GitHub code search `MP-001 org:SikoroApps` — only 3 hits: `README.md`, `features/README.md`, `docs/BUILD_ORDER.md` (the policy prose already known).
- GitHub Issues (open + closed), Pull Requests (open + closed), org repository list, authenticated user gists — no catalogue, no handover attachment.
- Discussions disabled; wiki endpoint returns "Repository not found".
- The accessible prior project conversation record for this session contains no 600-feature list and explicitly records that none was supplied.

**Finding: the original authoritative catalogue could not be recovered.** The only evidenced feature identifiers are the range endpoints `MP-001` and `MP-600` in the three policy files above. Their names and descriptions were never present.

**Action taken:** a reconstruction proposal of exactly 600 features was created at `features/feature-catalogue.json` and `features/feature-catalogue.md`, explicitly labelled **RECONSTRUCTED — NOT ORIGINAL**. `MP-001` and `MP-600` are preserved as the range endpoints; all other IDs and all descriptions are newly reconstructed and marked as such. Every feature starts at `NOT_STARTED`; none is `COMPLETE`. This proposal requires review before it is used as an authoritative implementation ledger.

---

## 1. Batch 2 scope

Build the dependency-complete identity and core-security system:

- Backend foundation (Supabase project, migrations, Edge Functions, environments, secrets model).
- International phone number normalisation and validation (default country from device locale, user-overridable).
- OTP request, server-side generation/hashing/storage/expiry, SMS hand-off, resend cooldown.
- OTP verification, replay prevention, attempt limiting, invalid/expired/error states.
- Account creation/login on successful verification only.
- Session establishment, validation, refresh, expiration, logout, revocation; secure Android token storage.
- Rate limits, abuse protection, network-failure retry semantics.
- Phone-only registration is preserved end to end. Opening the OTP screen never authenticates a user.

This batch does **not** build conversations, messages, chat UI, media, groups or release signing.

## 2. Exact catalogue feature IDs

Allocation from the reconstructed catalogue (80 features, `MP-041`–`MP-120`). Internal groups are for execution only; the mandatory seven-batch order is unchanged.

Group boundaries follow the actual reconstructed catalogue names in `features/feature-catalogue.json`:

| Internal group | ID range | Scope |
| --- | --- | --- |
| 2A | `MP-041`–`MP-050` | Backend provider decision, schema/RLS foundation, Edge Function contract, secret boundary, Android API client, TLS/network base |
| 2B | `MP-051`–`MP-056` | E.164 normalization, structural validation, country picker, input masking, server re-check, phone identity state machine |
| 2C | `MP-057`–`MP-072` | OTP function contracts, server code generation and hashed verifier, expiry, single-use consumption, autofill/manual entry, SMS adapter/provider/ DLReceipt/failover/template |
| 2D | `MP-073`–`MP-080` | Client + server resend cooldown, per-number/per-IP/global request limits, attempt lockout, replay and reuse rejection |
| 2E | `MP-081`–`MP-084` | Account creation after verification, account lifecycle, anti-enumeration, idempotent duplicate registration |
| 2F | `MP-085`–`MP-095` | Token issuance/rotation, Keystore storage, no plaintext logging, restore/expiry/interceptor/refresh, logout, revocation, device session record |
| 2G | `MP-096`–`MP-114` | Retry/backoff/idempotency/offline handling, negative tests, end-to-end registration/sign-in tests, session tests, phone-only regression, diagnostics redaction, RLS authorization test, abuse logging |
| 2H | `MP-115`–`MP-120` | Risk challenge gate, plaintext-free audit, runtime secret injection, contract documentation, exit evidence, known-limitations note |

The per-ID names, descriptions and dependency arrays are in `features/feature-catalogue.json`. No ID is renumbered or deleted.

## 3. Dependency graph

```text
Batch 1 (COMPLETE, MP-001..MP-040)
  └── 2A Backend foundation (MP-041..MP-050)
        └── 2B Phone entry/validation (MP-051..MP-056)
              └── 2C OTP request, code lifecycle + SMS (MP-057..MP-072)
                    └── 2D Cooldown/limits/replay (MP-073..MP-080)
                          └── 2E Account creation (MP-081..MP-084)
                                └── 2F Sessions + secure storage (MP-085..MP-095)
                                      └── 2G Retry/tests/redaction/RLS test (MP-096..MP-114)
                                            └── 2H Challenge/audit/docs/gate (MP-115..MP-120)
```

Hard rule: no group starts until every listed prerequisite has passing test evidence. In particular:

- No SMS integration code until the backend project, schema and secret storage are deployed and verified.
- No verification logic until request/code state is server-owned and hashed.
- No session issuance until verification has passed server-side.
- No feature is marked `COMPLETE` without the evidence in section 14.

## 4. Backend architecture

**Candidate evaluated: Supabase (free tier).** Evidence: https://supabase.com/pricing, https://supabase.com/docs/guides/platform/free-plan.md, https://supabase.com/docs/guides/functions/limits.md, https://supabase.com/docs/guides/auth/phone-login.md.

| Aspect | Finding |
| --- | --- |
| Postgres | 500 MB/project on Free; RLS supported; migrations via Supabase CLI |
| Edge Functions | 500,000 invocations/month Free; 150 s wall clock, 2 s CPU; 256 MB memory; up to 100 secrets/project |
| Free projects | 2 free projects per user across organisations; inactive projects are paused (paid plans are not) |
| Native phone OTP | Supported, but only with MessageBird, Twilio, Vonage or TextLocal (community) |
| eSMS Africa | **Not** a native Supabase Auth SMS provider |
| Secrets | Function secrets (`supabase secrets set`); service-role key never shipped to Android |

**Architecture decision (proposed, needs approval):** use Supabase as the backend (Postgres + RLS + Edge Functions) with a **custom server-side OTP flow** implemented in Edge Functions, because the preferred SMS provider is not supported by Supabase Auth's built-in phone login. Android receives only a publishable/anon key; all OTP state, verification, account creation and session minting stay server-side.

**Alternative if approval is withheld:** use Supabase Auth's native phone OTP with a supported provider, but Twilio/MessageBird/Vonage do not document `+268` coverage on their free tiers and require funding — this conflicts with the free-tier and Eswatini priorities. A self-hosted Supabase gateway is technically possible but adds infrastructure outside the current GitHub-only build policy.

Separate `dev` and `prod` Supabase projects; no production data changes without a reviewed migration.

## 5. Authentication architecture

1. Android normalises the phone number to E.164 and sends it to `POST /auth/otp/request`.
2. Edge Function validates input, applies per-phone/IP rate limits and cooldown, generates a 6-digit code with a CSPRNG, stores only `SHA-256(code + per-record salt)` with expiry and attempt counters, and calls the SMS provider server-side. The plaintext code is never logged, returned to the client or stored.
3. Android opens the OTP screen. **No authentication state is granted here.**
4. Android sends phone + code + request id to `POST /auth/otp/verify`.
5. Edge Function checks expiry, attempt count, hash (constant-time comparison), and marks the code consumed atomically before any session is issued. Replay is rejected.
6. On success only: upsert the phone identity/account (created on first verification), mint a short-lived access token and a rotating refresh token, return both to Android.
7. Android stores tokens in `AndroidKeyStore`-backed encrypted storage (`Security-crypto`/Keystore + EncryptedSharedPreferences or DataStore with a Keystore-backed key).
8. Refresh through `POST /auth/session/refresh` with rotation and reuse detection; logout and revocation through `POST /auth/session/logout` and a server-side session table.
9. RLS policies key off the authenticated phone subject; service-role key is used only inside Edge Functions.

## 6. SMS architecture and provider evaluation

| Provider | Evidence | +268 / Eswatini | Sandbox / free test | Cost to go live | Notes |
| --- | --- | --- | --- | --- | --- |
| eSMS Africa (`esmsafrica.io`, Eira Digital Co. Ltd) | https://esmsafrica.io/sms-api/eswatini, https://esmsafrica.io/verify, https://esmsafrica.io/pricing | Yes — MTN & Eswatini Mobile, direct routes, `+268`, production status | Free sandbox with test key; eSMS Verify hashes codes and rate-limits server-side | Prepaid wallet minimum **USD 20**; Eswatini sender ID registration appears to be **USD 100 one-time** under the published "all African countries except KE/UG" fee | Preferred candidate. Two-call Verify API (`/verify/start`, `/verify/check`), DLR webhooks, credentials only in Edge Function secrets. **Payment requires user approval.** |
| eSMS Africa (`esmsafrica.com`) | https://esmsafrica.com/, https://esmsafrica.com/developers | Site is Nigeria-focused (MTN/Airtel/Glo/9mobile); no Eswatini claim found | Not documented | Unknown | Appears to be a **different operator** from the `.io` site. Do not assume shared accounts, pricing or API. Needs clarification before use. |
| Lunyazi | https://www.lunyazi.com/, https://www.lunyazi.com/otp/ | Yes — "SMS and OTP for Eswatini and Southern Africa", `+268` example | Free credits on signup, no card required (homepage says 10, OTP page says 5 — confirm at signup) | Pay per verification, no monthly minimum | Strong free-tier alternative; two-call OTP API, 5-minute expiry, 5-attempt lockout. Needs terms/privacy review. |
| Twilio / MessageBird / Vonage (Supabase-native) | https://supabase.com/docs/guides/auth/phone-login.md | No `+268` coverage confirmed on free tier | Trial credits, usually funding required | Paid | Would avoid custom auth code but conflicts with Eswatini + free-tier priority. |

**Recommendation:** integrate against an **SMS gateway interface** with two adapters, so the OTP state machine is provider-independent: first `sandbox` (no real SMS, deterministic test codes in dev only), then evaluate eSMS Africa Verify (`+268`, sandbox) and Lunyazi (free credits). Real end-to-end SMS delivery stays **BLOCKED** until credentials/funding are supplied; no feature is marked COMPLETE on sandbox evidence alone.

**Never:** provider keys in Kotlin, Compose, repository files, docs, Git history or CI output. GitHub Actions secrets and Supabase Function secrets only, injected at runtime.

## 7. OTP lifecycle

States: `REQUESTED → CODE_SENT → AWAITING_VERIFY → CONSUMED` with terminal `EXPIRED`, `FAILED`, `BLOCKED`, `REVOKED`.

| Rule | Implementation |
| --- | --- |
| Format | E.164 only, default country from locale, user-changeable |
| Code | 6 digits, CSPRNG, server-generated |
| Storage | Salted SHA-256 hash only; no plaintext, no logs |
| Expiry | 5 minutes (configurable, server-enforced) |
| Resend cooldown | 60 seconds per phone; request id rotated; previous code invalidated |
| Attempt limit | 5 wrong codes → request blocked |
| Replay | Atomic consume before session issue; second use rejected even with correct code |
| Abuse | Per-phone, per-IP and global limits; CAPTCHA consideration per Supabase guidance |
| Network failure | Idempotency key on request; safe retry with cooldown; no duplicate account/session |
| Logging | Phone numbers redacted; OTP values never logged; no production debug endpoints |

## 8. Database design (migrations, not manual edits)

```
phone_identities        id uuid pk, phone_e164 text unique, created_at, updated_at
accounts                id uuid pk, phone_identity_id uuid fk, state text, created_at
otp_requests            id uuid pk, phone_e164 text, code_hash text, salt text,
                        expires_at, attempts int, consumed_at, created_at, request_context jsonb
sessions                id uuid pk, account_id uuid fk, refresh_token_hash text unique,
                        expires_at, revoked_at, created_at, user_agent_hash text
rate_limit_events       id bigserial pk, scope text, subject_hash text, occurred_at
sms_delivery_events     id uuid pk, otp_request_id uuid fk, provider text,
                        provider_message_id text, status text, occurred_at
```

- RLS enabled on every table. `otp_requests`, `sms_delivery_events` and `rate_limit_events` have **no** client policy (service role only). `accounts` and `sessions` expose only owner-scoped `SELECT` via `auth.uid()`-equivalent phone subject.
- All changes via numbered SQL migrations under `supabase/migrations/`; no ad-hoc production data edits.
- Dev/prod separation: distinct projects and distinct secrets.

## 9. API contracts

### `POST /auth/otp/request`
Input: `{ "phone": "+268...", "request_context": { "locale": "ss-ZA", "client": "android" } }`
Output: `{ "request_id": "uuid", "retry_after_seconds": 60, "expires_in_seconds": 300, "status": "sent" }`
Never returns the OTP. Errors: `INVALID_PHONE`, `RATE_LIMITED`, `COOLDOWN_ACTIVE`, `PROVIDER_UNAVAILABLE`.

### `POST /auth/otp/verify`
Input: `{ "phone": "+268...", "request_id": "uuid", "code": "123456" }`
Output: `{ "verified": true, "account_state": "active", "access_token": "...", "refresh_token": "...", "expires_in": 900 }`
Errors: `INVALID_CODE`, `CODE_EXPIRED`, `TOO_MANY_ATTEMPTS`, `CODE_CONSUMED`, `REQUEST_NOT_FOUND`.

### Session
`POST /auth/session/refresh` → rotated tokens or `401 SESSION_REVOKED`.
`POST /auth/session/logout` → revokes refresh token.
`GET /auth/session/validate` → subject/expiry for authenticated clients.

Internal only (server-to-provider): eSMS Verify `/verify/start`, `/verify/check` or Lunyazi `/otp/send`, `/otp/verify`. Provider keys never cross into the APK.

## 10. Security threat review

| Threat | Mitigation |
| --- | --- |
| OTP guessed / brute force | 5 attempts, exponential cooldown, per-phone/IP limits, CAPTCHA consideration |
| OTP replay | Atomic consume, one-time request id, reused code rejected |
| OTP leakage in logs | No code logging, redacted phone fields, log-event threshold awareness |
| Client-side fake success | Verification and session minting only in Edge Functions |
| Service-role key exposure | Function secrets only; no key in APK, repo, docs or CI output |
| Account enumeration | Uniform responses and timing for request/verify errors |
| Token theft | TLS, short-lived access tokens, rotation + reuse detection, Keystore storage |
| SQL injection | Parameterised queries/PostgREST, no string-built SQL |
| Broken authorisation | RLS on all tables; owner-scoped policies; service role only server-side |
| SMS cost abuse | Global caps, cooldown, per-phone/IP quotas, provider spend limits |
| SIM/device assumption | Auto-read SMS is an optimisation only; server OTP remains authoritative; no SIM-ownership claim |

## 11. Error handling

Typed error codes with stable names; generic user messages that never reveal whether a phone is registered. Retryable network errors carry `Retry-After`; non-retryable validation errors do not. Android maps every error to a specific UI state (invalid phone, invalid code, expired code, cooldown, rate limit, network failure, unknown). No crash, no silent success, no authentication on screen navigation.

## 12. Test strategy

**Unit (Android, JVM):** E.164 normalisation for `+268`, invalid/empty/short numbers, country default/override; OTP state transitions; expiry boundary; attempt counter; resend cooldown; auth state; error mapping; token storage interface.

**Backend (Edge Functions, run in CI):** valid request; invalid phone; rate limit; cooldown; expiry; incorrect code; correct code; reused code; repeated requests; session create/refresh/revoke; unauthorised RLS access; log redaction; provider sandbox adapter with a mocked HTTP transport.

**Android instrumentation:** registration screen opens; phone entry and validation; OTP screen navigation without auth; incorrect code; loading state; network failure; retry; successful verification flow against a stub server; token persistence.

**Emulator:** existing `AppLaunchTest` must stay green; new registration/OTP smoke tests run on the GitHub-hosted API 35 emulator.

## 13. CI requirements

- Preserve and do not disable any Batch 1 check: JDK 17, SDK setup, Gradle validation, unit tests, lint, debug APK assembly, package-name assertion, artifact uploads, emulator launch test.
- All new backend tests and Android instrumentation tests run in GitHub Actions; compile-only is not completion.
- Secret scanning and dependency scanning on every push; fail on detected secrets.
- No provider credentials in logs; mask all GitHub Actions secrets.
- **Gradle wrapper deviation:** no `gradle/wrapper/` exists; CI pins hosted Gradle 8.9. Proposed secure fix (needs approval because it changes build tooling): generate a wrapper in a GitHub-hosted job with `gradle wrapper --gradle-version 8.9`, commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, and add `distributionSha256Sum` to `gradle-wrapper.properties` after verifying the published checksum. Until then, hosted Gradle remains pinned and deterministic.

## 14. Batch 2 exit criteria

All must be evidenced; none may be inferred from a green compile:

1. Backend deployed and verified (dev + prod separation, migrations applied via reviewed migration).
2. SMS integration configured (sandbox at minimum); real `+268` delivery tested if credentials/funding are available.
3. OTP verification tested end to end (server authoritative, no client trust).
4. Account creation and login/session tested.
5. Security tests (rate limit, replay, expiry, RLS, secret redaction) passing.
6. Android unit + instrumentation tests passing.
7. CI green on the exact final HEAD, including all Batch 1 checks.
8. No secrets exposed in code, history, docs or logs.
9. Documentation updated: `README.md`, `BUILD_ORDER.md`, `ARCHITECTURE.md`, `SECURITY.md`, `BATCH_2_PLAN.md`, `features/README.md`, catalogue statuses and evidence; `API.md`, `DATABASE.md`, `TESTING.md` created as needed.
10. Catalogue statuses reflect reality: sandbox-only or blocked features are `TESTED`/`BLOCKED`, never `COMPLETE`.

## 15. Risks and blockers

| Risk / blocker | Impact | Status |
| --- | --- | --- |
| Original 600-feature catalogue unavailable | ID/description mapping is provisional | Open — reconstruction awaits review |
| No Supabase project or credentials | Backend cannot be deployed | BLOCKED — credentials required |
| SMS provider account/funding | Real `+268` delivery cannot be verified | BLOCKED — approval + credentials required |
| eSMS Africa `.io` vs `.com` ambiguity | Wrong provider/API could be selected | Open — clarification required |
| Two domain eSMS Verify vs Supabase native OTP | Custom auth adds code and review burden | Open — architecture approval required |
| No Gradle wrapper | Build reproducibility relies on hosted pinned Gradle | Open — remediation proposed |
| Batch 1 regression | Blocks all Batch 2 completion | Currently clear; continuously re-verified |

## 16. Decisions that genuinely require your input

1. Approve or replace the **reconstructed catalogue** as the working ledger (it is not the original).
2. Approve **Supabase + custom Edge Function OTP** as the backend/authentication architecture (alternative: native Supabase phone OTP with a supported, likely paid, SMS provider).
3. Choose the SMS provider for real delivery and approve any **payment/funding**: eSMS Africa `.io` (sandbox free, production min USD 20 + sender ID fee) or Lunyazi (free starter credits, confirm terms).
4. Clarify whether the intended eSMS Africa is `esmsafrica.io` or `esmsafrica.com` — they appear to be different operators.
5. Approve generating and committing a **Gradle wrapper** with a verified distribution checksum in a GitHub-hosted job.

No code implementation for Batch 2 has started; these decisions do not block writing this plan, but they do block deployment and real SMS verification.

## 17. References

- Batch 1 evidence: https://github.com/SikoroApps/Messenger-Pro/actions/runs/44205558041 (run 12), https://github.com/SikoroApps/Messenger-Pro/actions/runs/44206655226 (run 13)
- Supabase pricing: https://supabase.com/pricing
- Supabase free plan: https://supabase.com/docs/guides/platform/free-plan.md
- Supabase Edge Function limits: https://supabase.com/docs/guides/functions/limits.md
- Supabase phone sign-in: https://supabase.com/docs/guides/auth/phone-login.md
- eSMS Africa Eswatini SMS API: https://esmsafrica.io/sms-api/eswatini
- eSMS Verify: https://esmsafrica.io/verify
- eSMS Africa pricing: https://esmsafrica.io/pricing
- Lunyazi OTP: https://www.lunyazi.com/otp/
