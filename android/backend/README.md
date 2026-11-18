# Server-authoritative reference core (`:backend`)

This pure-JVM module contains the **rules** for phone-OTP identity, sessions and shared domain
primitives, plus credential-aware adapters behind injectable HTTP transports. It has no Android
SDK dependency, so the security-critical behaviour can be exercised in GitHub Actions without a
local emulator or provider account.

## What is implemented and tested here

- `server/Core.kt` — injectable clock, CSPRNG ids, stable generic error codes, E.164 server-side
  re-check, redaction and keyed hashing helpers.
- `server/Otp.kt` — six-digit CSPRNG codes, HMAC-SHA256 verifiers with per-request salt,
  constant-time comparison, and an atomic single-use `consume` contract.
- `server/RateLimits.kt` — 60-second resend cooldown and sliding-window per-phone/per-IP/global
  limits over pre-hashed subjects.
- `server/Sms.kt` — provider-independent gateway plus a **development-only** sandbox adapter.
  The sandbox does not send a real message and is forbidden in production.
- `server/Sessions.kt` — token issuance, refresh rotation, consumed-token reuse detection and
  family-wide revocation; only token hashes are persisted.
- `server/AuthService.kt` — orchestration: no account or session exists before verification, the
  code is never returned to the caller, and idempotency keys prevent duplicate sends.
- `server/AuthHttp.kt` — a loopback-only HTTP adapter for the JSON contract. Integration tests use
  real sockets to verify status codes, redaction, idempotency, replay rejection and token rotation.
  It is explicitly not a hosted TLS deployment.
- `server/Audit.kt` — keyed-hash audit records and generic abuse signals for auth transitions;
  no phone number, OTP, token, request id or provider message is written to the logs.
- `server/ESmsVerify.kt` — pinned HTTPS eSMS Verify adapter, mandatory `esms_test_` sandbox key,
  stable status mapping, `Idempotency-Key`, injected transport and generic errors that never echo
  provider prose, codes or keys.
- `server/ProviderOtp.kt` — provider-managed OTP state machine. The plaintext code goes from the
  client to the provider check call only; no code or hash is stored locally, and tokens are issued
  only after an approved check and an atomic consume.
- `server/Supabase.kt` — server/CI-only PostgREST repositories for accounts, provider OTP records
  and sessions, with host pinning, `apikey` header handling and generic status-derived failures.
- `domain/` — consent-gated, hash-only contact matching, message idempotency/pagination, media
  validation and account-control policy models used by later batches.

## What this module does NOT prove

This is a reference implementation and CI test harness, **not a deployed service**. It does not
provide:

- a hosted Postgres/RLS project or deployed Edge Functions;
- a real SMS provider integration or real `+268` delivery;
- physical-device verification of permission, notification, autofill or app-lock behaviour;
- release signing or Play Console rollout.

Consequently, no catalogue feature backed by this module is marked `COMPLETE` on this evidence
alone. See `docs/BUILD_ORDER.md` and `docs/BATCH_2_PLAN.md` for the outstanding exit gates.

## Deployment adapters

The repository interfaces (`OtpRepository`, `AccountRepository`, `SessionRepository`,
`RateLimiter` subjects) are the contract a deployed adapter must satisfy. In particular,
`OtpRepository.consume` must be a single atomic conditional update (for example
`UPDATE ... WHERE consumed_at IS NULL RETURNING ...`) and refresh rotation must atomically mark
the old hash consumed before issuing a new session.
